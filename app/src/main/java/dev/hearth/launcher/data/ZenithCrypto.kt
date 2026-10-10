package dev.hearth.launcher.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ZENITH 19's own encryption, for the ZENITH-Tresor and for settings backups with a password:
 * AES-256-GCM (encrypted and sealed, so any change to a file is noticed) with a key derived from
 * the password by PBKDF2-HMAC-SHA256. Nothing here ever leaves the phone; without the password
 * nothing can be read again, not even by ZENITH.
 */
object ZenithCrypto {
    /** PBKDF2 rounds: slow enough to make guessing passwords expensive, quick enough on a phone. */
    const val ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    /** Below this, a password is too easy to guess. */
    const val MIN_PASSWORD = 6

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    /** The key for [password] with [salt] (takes a moment: run it off the main thread). */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES").also { raw.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun keyOf(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")

    /** Encrypts and seals [plain]: a fresh IV in front, then the ciphertext with its tag. */
    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        if (aad != null) cipher.updateAAD(aad)
        val body = cipher.doFinal(plain)
        return cipher.iv + body
    }

    /** Opens what [seal] made; throws if the key is wrong or anything was changed. */
    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        require(sealed.size > IV_BYTES) { "too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    // ---- Settings backups with a password ----

    private val BACKUP_MAGIC = "ZSEC1".toByteArray(Charsets.US_ASCII)

    /** Whether [bytes] is a backup sealed with a password (else it's the plain JSON). */
    fun isSealedBackup(bytes: ByteArray): Boolean =
        bytes.size > BACKUP_MAGIC.size && bytes.copyOfRange(0, BACKUP_MAGIC.size).contentEquals(BACKUP_MAGIC)

    /** A settings backup only [password] opens: "ZSEC1", salt, rounds, then the sealed JSON. */
    fun sealBackup(text: String, password: CharArray): ByteArray {
        val salt = randomBytes(SALT_BYTES)
        val header = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.write(BACKUP_MAGIC)
                it.write(salt)
                it.writeInt(ITERATIONS)
            }
        }.toByteArray()
        val key = deriveKey(password, salt)
        return header + seal(key, text.toByteArray(Charsets.UTF_8), header)
    }

    /** The JSON inside a backup sealed with [password]; null if the password is wrong. */
    fun openBackup(bytes: ByteArray, password: CharArray): String? = runCatching {
        val input = DataInputStream(bytes.inputStream())
        val magic = ByteArray(BACKUP_MAGIC.size).also { input.readFully(it) }
        require(magic.contentEquals(BACKUP_MAGIC))
        val salt = ByteArray(SALT_BYTES).also { input.readFully(it) }
        val rounds = input.readInt()
        require(rounds in 10_000..5_000_000)
        val headerSize = BACKUP_MAGIC.size + SALT_BYTES + 4
        val key = deriveKey(password, salt, rounds)
        open(key, bytes.copyOfRange(headerSize, bytes.size), bytes.copyOfRange(0, headerSize)).toString(Charsets.UTF_8)
    }.getOrNull()

    /** A [Long] as 8 bytes (for associated data). */
    fun longBytes(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v).array()
}
