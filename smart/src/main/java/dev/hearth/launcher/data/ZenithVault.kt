package dev.hearth.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Size
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The ZENITH-Tresor: photos, videos and files, encrypted on the phone with AES-256-GCM.
 *
 * - A random vault key encrypts everything; the password (PBKDF2) only locks that key, so a new
 *   password doesn't have to encrypt everything again. Optionally the fingerprint unlocks it
 *   too, through a key in Android's hardware keystore that only opens after the fingerprint.
 * - Each file is encrypted in chunks of 1 MiB (big videos never have to fit in memory); every
 *   chunk is sealed together with the file's id and its place, so nothing can be swapped,
 *   reordered or changed unnoticed.
 * - The list of what's inside (names, sizes, thumbnails) is encrypted as well.
 * - Before an original may be deleted, the encrypted copy is read back and compared.
 *
 * Without the password nothing can be read again – not by ZENITH, not by anyone.
 */
object ZenithVault {

    enum class Kind { Photo, Video, Note, File }

    data class Entry(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val added: Long,
        val sha: String,
        val thumb: Boolean,
    ) {
        val kind: Kind
            get() = when {
                mime.startsWith("image/") -> Kind.Photo
                mime.startsWith("video/") -> Kind.Video
                mime.startsWith("text/plain") -> Kind.Note
                else -> Kind.File
            }
    }

    private const val CHUNK = 1 shl 20
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val THUMB = 360
    /** Photos up to this size open inside the vault; bigger files are opened or exported. */
    const val VIEW_LIMIT = 80L shl 20
    private const val BIO_ALIAS = "zenith_vault_fingerprint"

    private val FILE_MAGIC = "ZTF1".toByteArray(Charsets.US_ASCII)
    private val BACKUP_MAGIC = "ZTRB1".toByteArray(Charsets.US_ASCII)
    private val KEY_AAD = "ZENITH-Tresor/key".toByteArray(Charsets.US_ASCII)
    private val INDEX_AAD = "ZENITH-Tresor/index".toByteArray(Charsets.US_ASCII)
    private val ID = Regex("^[0-9a-f]{32}$")
    private val STORED = Regex("^([0-9a-f]{32}\\.(dat|thumb)|index\\.bin)$")

    private val guard = Any()
    @Volatile private var vaultKey: ByteArray? = null

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    // ---- Where it lives ----

    private fun dir(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }
    private fun metaFile(context: Context) = File(dir(context), "vault.json")
    private fun indexFile(context: Context) = File(dir(context), "index.bin")
    private fun dataFile(context: Context, id: String) = File(dir(context), "$id.dat")
    private fun thumbFile(context: Context, id: String) = File(dir(context), "$id.thumb")

    /** Where a file opened in another app is put for the moment (wiped when the vault locks). */
    fun openedDir(context: Context): File = File(context.cacheDir, "vault-open")

    fun exists(context: Context): Boolean = metaFile(context).isFile

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun readMeta(context: Context): JSONObject? =
        runCatching { JSONObject(metaFile(context).readText()) }.getOrNull()

    /** Written whole or not at all: first into a spare file, then swapped in. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val spare = File(target.parentFile, target.name + ".new")
        FileOutputStream(spare).use {
            it.write(bytes)
            it.fd.sync()
        }
        // A rename replaces the old file in one step: there's always a whole one.
        if (!spare.renameTo(target)) throw IOException("could not save ${target.name}")
    }

    private fun currentKey(): SecretKey = ZenithCrypto.keyOf(vaultKey ?: throw IllegalStateException("locked"))

    // ---- Setting up, unlocking, locking ----

    /** A new, empty vault locked with [password] – and open right away. */
    fun create(context: Context, password: CharArray) {
        synchronized(guard) {
            check(!exists(context)) { "vault exists" }
            val raw = ZenithCrypto.randomBytes(ZenithCrypto.KEY_BYTES)
            writeMeta(context, raw, password, null)
            vaultKey = raw
            _entries.value = emptyList()
            saveIndex(context)
            _unlocked.value = true
        }
    }

    /** Locks the vault key with [password] (a fresh salt each time); keeps the fingerprint's copy from [keep]. */
    private fun writeMeta(context: Context, raw: ByteArray, password: CharArray, keep: JSONObject?) {
        val salt = ZenithCrypto.randomBytes(ZenithCrypto.SALT_BYTES)
        val wrapped = ZenithCrypto.seal(ZenithCrypto.deriveKey(password, salt), raw, KEY_AAD)
        val meta = JSONObject()
            .put("v", 1)
            .put("salt", b64(salt))
            .put("iter", ZenithCrypto.ITERATIONS)
            .put("key", b64(wrapped))
        if (keep != null && keep.has("bio") && keep.has("bioIv")) {
            meta.put("bio", keep.getString("bio")).put("bioIv", keep.getString("bioIv"))
        }
        writeAtomically(metaFile(context), meta.toString().toByteArray(Charsets.UTF_8))
    }

    /** The vault key behind [password], or null if it's the wrong one. */
    private fun unwrap(context: Context, password: CharArray): ByteArray? {
        val meta = readMeta(context) ?: return null
        return runCatching {
            val salt = unb64(meta.getString("salt"))
            val rounds = meta.optInt("iter", ZenithCrypto.ITERATIONS).coerceIn(10_000, 5_000_000)
            ZenithCrypto.open(ZenithCrypto.deriveKey(password, salt, rounds), unb64(meta.getString("key")), KEY_AAD)
        }.getOrNull()
    }

    enum class Unlock { Ok, WrongPassword, Damaged }

    /** Whether [password] is the vault's (asked again before deleting it all). */
    fun verify(context: Context, password: CharArray): Boolean = unwrap(context, password) != null

    /** Opens the vault with [password] (takes a moment: off the main thread). */
    fun unlock(context: Context, password: CharArray): Unlock {
        val raw = unwrap(context, password) ?: return Unlock.WrongPassword
        return if (openWith(context, raw)) Unlock.Ok else Unlock.Damaged
    }

    private fun openWith(context: Context, raw: ByteArray): Boolean = synchronized(guard) {
        val list = loadIndex(context, raw) ?: return false
        vaultKey = raw
        _entries.value = list
        _unlocked.value = true
        if (indexFile(context).isFile) sweep(context, list)
        true
    }

    /** Locks: the key is forgotten, and whatever was opened in other apps is wiped. */
    fun lock(context: Context) {
        synchronized(guard) {
            vaultKey?.fill(0)
            vaultKey = null
            _entries.value = emptyList()
            _unlocked.value = false
        }
        wipeOpened(context)
    }

    fun wipeOpened(context: Context) {
        runCatching { openedDir(context).deleteRecursively() }
        runCatching { cameraDir(context).deleteRecursively() }
    }

    /** Leftovers of an add that never finished (the phone switched off halfway). */
    private fun sweep(context: Context, list: List<Entry>) {
        val known = list.map { it.id }.toHashSet()
        dir(context).listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.')
            if (ID.matches(id) && id !in known && (f.name.endsWith(".dat") || f.name.endsWith(".thumb"))) f.delete()
            if (f.name.endsWith(".new")) f.delete()
        }
    }

    fun changePassword(context: Context, old: CharArray, new: CharArray): Boolean = synchronized(guard) {
        val raw = unwrap(context, old) ?: return false
        writeMeta(context, raw, new, readMeta(context))
        true
    }

    /** Deletes the vault and everything in it, for good. */
    fun destroy(context: Context) {
        lock(context)
        synchronized(guard) {
            dir(context).deleteRecursively()
            removeFingerprintKey()
        }
    }

    // ---- The list of what's inside (encrypted) ----

    private fun loadIndex(context: Context, raw: ByteArray): List<Entry>? {
        val file = indexFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            val json = JSONArray(ZenithCrypto.open(ZenithCrypto.keyOf(raw), file.readBytes(), INDEX_AAD).toString(Charsets.UTF_8))
            List(json.length()) { i ->
                val o = json.getJSONObject(i)
                Entry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    mime = o.optString("mime", "application/octet-stream"),
                    size = o.getLong("size"),
                    added = o.optLong("added"),
                    sha = o.optString("sha"),
                    thumb = o.optBoolean("thumb"),
                )
            }.filter { ID.matches(it.id) }
        }.getOrNull()
    }

    private fun saveIndex(context: Context) {
        val json = JSONArray()
        _entries.value.forEach { e ->
            json.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("mime", e.mime)
                    .put("size", e.size)
                    .put("added", e.added)
                    .put("sha", e.sha)
                    .put("thumb", e.thumb),
            )
        }
        writeAtomically(indexFile(context), ZenithCrypto.seal(currentKey(), json.toString().toByteArray(Charsets.UTF_8), INDEX_AAD))
    }

    // ---- Files in, files out ----

    private fun chunkAad(id: String, index: Long): ByteArray = id.toByteArray(Charsets.US_ASCII) + ZenithCrypto.longBytes(index)

    /** Reads until [buf] is full or the stream ends. */
    private fun fill(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun encryptInto(key: SecretKey, id: String, input: InputStream, out: OutputStream, digest: MessageDigest, progress: (Long) -> Unit): Long {
        val data = DataOutputStream(out)
        data.write(FILE_MAGIC)
        val buf = ByteArray(CHUNK)
        var index = 0L
        var total = 0L
        while (true) {
            val n = fill(input, buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(chunkAad(id, index))
            val sealed = cipher.doFinal(buf, 0, n)
            data.writeInt(sealed.size)
            data.write(cipher.iv)
            data.write(sealed)
            index++
            total += n
            progress(total)
            if (n < buf.size) break
        }
        data.flush()
        return total
    }

    /** Decrypts a stored file chunk by chunk into [sink]; throws if anything was changed. */
    private fun decryptFrom(key: SecretKey, id: String, file: File, sink: (ByteArray, Int) -> Unit): Long {
        DataInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)).use { input ->
            val magic = ByteArray(FILE_MAGIC.size).also { input.readFully(it) }
            if (!magic.contentEquals(FILE_MAGIC)) throw IOException("not a vault file")
            val iv = ByteArray(IV_BYTES)
            var index = 0L
            var total = 0L
            while (true) {
                val len = try {
                    input.readInt()
                } catch (e: EOFException) {
                    break
                }
                if (len < 16 || len > CHUNK + 16) throw IOException("damaged")
                input.readFully(iv)
                val sealed = ByteArray(len).also { input.readFully(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(chunkAad(id, index))
                val plain = cipher.doFinal(sealed)
                sink(plain, plain.size)
                index++
                total += plain.size
            }
            return total
        }
    }

    private class Source(val name: String, val size: Long, val mime: String)

    private fun describe(context: Context, uri: Uri): Source {
        var name: String? = null
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val title = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Datei"
        val mime = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(title.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        return Source(title, size, mime)
    }

    /** What happened to one file on its way into the vault. */
    class Added(val uri: Uri, val entry: Entry?, val error: String? = null)

    /**
     * Encrypts the file at [uri] into the vault, reads the encrypted copy back and compares it
     * with the original (only then is it [Added.entry] and may the original go). [progress]
     * gets the bytes done and the file's size (-1 if unknown).
     */
    fun add(context: Context, uri: Uri, progress: (Long, Long) -> Unit = { _, _ -> }): Added {
        val key = runCatching { currentKey() }.getOrElse { return Added(uri, null, "Tresor ist gesperrt") }
        val source = describe(context, uri)
        val id = hex(ZenithCrypto.randomBytes(16))
        val data = dataFile(context, id)
        return try {
            val inDigest = MessageDigest.getInstance("SHA-256")
            val written = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(data), 1 shl 16).use { out ->
                    encryptInto(key, id, input, out, inDigest) { done -> progress(done, source.size) }
                }
            } ?: throw IOException("cannot read")
            val sha = inDigest.digest()
            // Read back and compare, before anyone deletes the original.
            val outDigest = MessageDigest.getInstance("SHA-256")
            val read = decryptFrom(key, id, data) { b, n -> outDigest.update(b, 0, n) }
            if (read != written || !outDigest.digest().contentEquals(sha)) throw IOException("check failed")
            val thumb = thumbnailOf(context, uri, source.mime)?.let { jpeg ->
                runCatching { thumbFile(context, id).writeBytes(ZenithCrypto.seal(key, jpeg, chunkAad(id, -1))) }.isSuccess
            } ?: false
            val entry = Entry(id, source.name, source.mime, written, System.currentTimeMillis(), hex(sha), thumb)
            synchronized(guard) {
                if (vaultKey == null) throw IOException("locked meanwhile")
                _entries.value = listOf(entry) + _entries.value
                saveIndex(context)
            }
            Added(uri, entry)
        } catch (e: Throwable) {
            data.delete()
            thumbFile(context, id).delete()
            Added(uri, null, source.name)
        }
    }

    /**
     * Puts [bytes] (a note) into the vault as [name] – encrypted and read back like any file.
     * [replacing] an older version, that one goes once the new one is safe.
     */
    fun putBytes(context: Context, name: String, mime: String, bytes: ByteArray, replacing: Entry? = null): Entry? {
        val key = runCatching { currentKey() }.getOrNull() ?: return null
        val id = hex(ZenithCrypto.randomBytes(16))
        val data = dataFile(context, id)
        return try {
            val inDigest = MessageDigest.getInstance("SHA-256")
            val written = BufferedOutputStream(FileOutputStream(data), 1 shl 16).use { out ->
                encryptInto(key, id, bytes.inputStream(), out, inDigest) { }
            }
            val sha = inDigest.digest()
            val outDigest = MessageDigest.getInstance("SHA-256")
            val read = decryptFrom(key, id, data) { b, n -> outDigest.update(b, 0, n) }
            if (read != written || !outDigest.digest().contentEquals(sha)) throw IOException("check failed")
            val entry = Entry(id, name, mime, written, System.currentTimeMillis(), hex(sha), false)
            synchronized(guard) {
                if (vaultKey == null) throw IOException("locked meanwhile")
                _entries.value = listOf(entry) + _entries.value.filterNot { it.id == replacing?.id }
                saveIndex(context)
            }
            if (replacing != null) {
                dataFile(context, replacing.id).delete()
                thumbFile(context, replacing.id).delete()
            }
            entry
        } catch (e: Throwable) {
            data.delete()
            null
        }
    }

    /** A note's text, decrypted. */
    fun readText(context: Context, entry: Entry): String? = readAll(context, entry)?.toString(Charsets.UTF_8)

    /** Where a photo just taken goes for the moment, before it's encrypted (wiped right after). */
    fun cameraDir(context: Context): File = File(context.cacheDir, "vault-cam")

    /** Deletes the original of a file that's now safely in the vault (if its app allows it). */
    fun deleteOriginal(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)

    /** The thumbnail of [entry] as JPEG bytes, decrypted. */
    fun thumbnail(context: Context, entry: Entry): ByteArray? {
        if (!entry.thumb) return null
        return runCatching { ZenithCrypto.open(currentKey(), thumbFile(context, entry.id).readBytes(), chunkAad(entry.id, -1)) }.getOrNull()
    }

    /** A whole file, decrypted into memory (photos to look at). */
    fun readAll(context: Context, entry: Entry): ByteArray? = runCatching {
        require(entry.size <= VIEW_LIMIT)
        val out = ByteArrayOutputStream(entry.size.toInt().coerceAtLeast(32))
        val total = decryptFrom(currentKey(), entry.id, dataFile(context, entry.id)) { b, n -> out.write(b, 0, n) }
        if (total != entry.size) throw IOException("incomplete")
        out.toByteArray()
    }.getOrNull()

    /** Decrypts [entry] into the file the user chose. */
    fun export(context: Context, entry: Entry, target: Uri): Boolean = runCatching {
        val total = context.contentResolver.openOutputStream(target, "w")?.use { out ->
            decryptFrom(currentKey(), entry.id, dataFile(context, entry.id)) { b, n -> out.write(b, 0, n) }
        } ?: throw IOException("cannot write")
        if (total != entry.size) throw IOException("incomplete")
    }.isSuccess

    /** Decrypts [entry] for a moment into the app's own cache, to be opened by another app. */
    fun openCopy(context: Context, entry: Entry): File? = runCatching {
        val folder = File(openedDir(context), entry.id).apply { mkdirs() }
        val safe = entry.name.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifBlank { "datei" }.take(80)
        val file = File(folder, safe)
        val total = FileOutputStream(file).use { out ->
            decryptFrom(currentKey(), entry.id, dataFile(context, entry.id)) { b, n -> out.write(b, 0, n) }
        }
        if (total != entry.size) throw IOException("incomplete")
        file
    }.getOrNull()

    fun remove(context: Context, entry: Entry) {
        synchronized(guard) {
            _entries.value = _entries.value.filterNot { it.id == entry.id }
            saveIndex(context)
            dataFile(context, entry.id).delete()
            thumbFile(context, entry.id).delete()
            File(openedDir(context), entry.id).deleteRecursively()
        }
    }

    // ---- Pictures ----

    private fun sampleFor(size: Size, max: Int): Int {
        var s = 1
        val side = maxOf(size.width, size.height)
        while (side / (s * 2) >= max) s *= 2
        return s
    }

    /** A photo from its decrypted bytes, at most [max] pixels on its long side (turned upright). */
    fun decode(bytes: ByteArray, max: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleFor(info.size, max))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    private fun thumbnailOf(context: Context, uri: Uri, mime: String): ByteArray? = runCatching {
        val resolver = context.contentResolver
        var bitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = runCatching { resolver.loadThumbnail(uri, Size(THUMB, THUMB), null) }.getOrNull()
        }
        if (bitmap == null && mime.startsWith("image/")) {
            bitmap = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(sampleFor(info.size, THUMB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
        if (bitmap == null && mime.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            bitmap = try {
                retriever.setDataSource(context, uri)
                retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB, THUMB)
            } catch (e: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        val picture = bitmap ?: return@runCatching null
        val soft = if (picture.config == Bitmap.Config.HARDWARE) picture.copy(Bitmap.Config.ARGB_8888, false) else picture
        val scale = THUMB.toFloat() / maxOf(soft.width, soft.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt().coerceAtLeast(1), (soft.height * scale).toInt().coerceAtLeast(1), true) else soft
        ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }.getOrNull()

    // ---- Fingerprint ----

    fun fingerprintOn(context: Context): Boolean = readMeta(context)?.has("bio") == true

    private fun fingerprintKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(BIO_ALIAS, null) as? SecretKey)?.let { if (!create) return it }
        if (!create) return null
        runCatching { store.deleteEntry(BIO_ALIAS) }
        val spec = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    private fun removeFingerprintKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIO_ALIAS) }
    }

    /** A cipher for switching the fingerprint on; it works once the fingerprint was given. */
    fun fingerprintEnableCipher(): Cipher? = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, fingerprintKey(create = true)) }
    }.getOrNull()

    /** After the fingerprint: locks the vault key with it too. */
    fun finishFingerprintEnable(context: Context, cipher: Cipher): Boolean = runCatching {
        synchronized(guard) {
            val raw = vaultKey ?: throw IllegalStateException("locked")
            val wrapped = cipher.doFinal(raw)
            val meta = readMeta(context) ?: throw IOException("no vault")
            meta.put("bio", b64(wrapped)).put("bioIv", b64(cipher.iv))
            writeAtomically(metaFile(context), meta.toString().toByteArray(Charsets.UTF_8))
        }
    }.isSuccess

    fun disableFingerprint(context: Context) {
        synchronized(guard) {
            readMeta(context)?.let { meta ->
                meta.remove("bio")
                meta.remove("bioIv")
                runCatching { writeAtomically(metaFile(context), meta.toString().toByteArray(Charsets.UTF_8)) }
            }
            removeFingerprintKey()
        }
    }

    /**
     * A cipher for unlocking with the fingerprint; null if that's off or no longer valid (a new
     * fingerprint was added to the phone – then it's switched off and the password is needed).
     */
    fun fingerprintUnlockCipher(context: Context): Cipher? {
        val meta = readMeta(context) ?: return null
        if (!meta.has("bio") || !meta.has("bioIv")) return null
        return try {
            val key = fingerprintKey(create = false) ?: throw IllegalStateException("no key")
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, unb64(meta.getString("bioIv"))))
            }
        } catch (e: Exception) {
            disableFingerprint(context)
            null
        }
    }

    /** After the fingerprint: opens the vault. */
    fun finishFingerprintUnlock(context: Context, cipher: Cipher): Unlock {
        val meta = readMeta(context) ?: return Unlock.Damaged
        val raw = runCatching { cipher.doFinal(unb64(meta.getString("bio"))) }.getOrNull() ?: return Unlock.WrongPassword
        return if (openWith(context, raw)) Unlock.Ok else Unlock.Damaged
    }

    // ---- The whole vault as one file (still encrypted) ----

    /**
     * Saves the whole vault – still encrypted, locked by the same password – into one file, to
     * keep it safe elsewhere or to bring it onto a new phone. (The fingerprint stays behind: it
     * belongs to this phone.)
     */
    fun exportVault(context: Context, target: Uri, progress: (Long, Long) -> Unit = { _, _ -> }): Boolean = runCatching {
        val meta = readMeta(context) ?: throw IOException("no vault")
        meta.remove("bio")
        meta.remove("bioIv")
        val files = dir(context).listFiles()?.filter { it.isFile && STORED.matches(it.name) }.orEmpty()
        val whole = files.sumOf { it.length() }
        var done = 0L
        context.contentResolver.openOutputStream(target, "w")?.use { raw ->
            val out = DataOutputStream(BufferedOutputStream(raw, 1 shl 16))
            out.write(BACKUP_MAGIC)
            val metaBytes = meta.toString().toByteArray(Charsets.UTF_8)
            out.writeInt(metaBytes.size)
            out.write(metaBytes)
            out.writeInt(files.size)
            val buf = ByteArray(1 shl 16)
            files.forEach { f ->
                out.writeUTF(f.name)
                out.writeLong(f.length())
                FileInputStream(f).use { input ->
                    var left = f.length()
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) throw IOException("short read")
                        out.write(buf, 0, n)
                        left -= n
                        done += n
                    }
                }
                progress(done, whole)
            }
            out.flush()
        } ?: throw IOException("cannot write")
    }.isSuccess

    enum class Import { Ok, NotAVault, Failed }

    /** Brings back a vault saved with [exportVault] (only while there's none on this phone). */
    fun importVault(context: Context, source: Uri): Import {
        if (exists(context)) return Import.Failed
        val staging = File(context.filesDir, "vault-import")
        staging.deleteRecursively()
        staging.mkdirs()
        val result = runCatching {
            context.contentResolver.openInputStream(source)?.use { raw ->
                val input = DataInputStream(BufferedInputStream(raw, 1 shl 16))
                val magic = ByteArray(BACKUP_MAGIC.size)
                if (fill(input, magic) != magic.size || !magic.contentEquals(BACKUP_MAGIC)) return@runCatching Import.NotAVault
                val metaSize = input.readInt()
                if (metaSize !in 2..65_536) return@runCatching Import.NotAVault
                val metaBytes = ByteArray(metaSize).also { input.readFully(it) }
                val meta = JSONObject(metaBytes.toString(Charsets.UTF_8))
                if (!meta.has("salt") || !meta.has("key")) return@runCatching Import.NotAVault
                val count = input.readInt()
                if (count !in 0..1_000_000) throw IOException("damaged")
                val buf = ByteArray(1 shl 16)
                repeat(count) {
                    val name = input.readUTF()
                    val length = input.readLong()
                    if (!STORED.matches(name) || length < 0) throw IOException("damaged")
                    FileOutputStream(File(staging, name)).use { out ->
                        var left = length
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw IOException("short")
                            out.write(buf, 0, n)
                            left -= n
                        }
                    }
                }
                File(staging, "vault.json").writeBytes(metaBytes)
                Import.Ok
            } ?: Import.Failed
        }.getOrDefault(Import.Failed)
        if (result != Import.Ok) {
            staging.deleteRecursively()
            return result
        }
        synchronized(guard) {
            val target = File(context.filesDir, "vault")
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.deleteRecursively()
                return Import.Failed
            }
        }
        return Import.Ok
    }

    /** All of the vault's files together, as stored. */
    fun storedSize(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
