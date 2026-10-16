package dev.hearth.launcher.wallpaper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/*
 * Ten more pictures of the planet (or moon) of a wallpaper app, to keep: the NASA Image and Video Library (images.nasa.gov),
 * whose pictures NASA made itself and, as a rule, holds no copyright on. The app asks the library's own search (images-api.nasa.gov,
 * no account, no key) for the planet, shows ten small pictures and, for the one that is chosen, downloads the size that was chosen
 * (the library has every picture in several: small, medium, large and the original) into the phone's gallery (Pictures/ZENITH).
 * Nothing but the search words leaves the phone. Every function here blocks – call it from a thread of its own.
 */

/** One picture of the library: its number, its name, its text, a small picture and the list of the files it exists in. */
class PlanetPicture(val id: String, val title: String, val description: String, val thumb: String, val collection: String)

/** The sizes a picture exists in. [url] is where the file is, [size] its bytes (-1 if the library didn't say). */
class PictureSize(val label: String, val url: String, var size: Long = -1L)

object PlanetPictures {

    private const val SEARCH = "https://images-api.nasa.gov/search"
    private const val AGENT = "ZENITH-Wallpaper (Android)"

    /** How many pictures a wallpaper app offers. */
    const val COUNT = 10

    /** What the library is asked for the body of [scene] (English, as the library has it). */
    fun query(scene: SuperScene): String = when (scene) {
        SuperScene.Moon -> "moon surface apollo lunar"
        SuperScene.Earth -> "earth from space blue marble"
        SuperScene.Mars -> "mars surface"
        SuperScene.Venus -> "venus planet"
        SuperScene.Jupiter -> "jupiter juno"
        SuperScene.Saturn -> "saturn cassini"
        SuperScene.Neptune -> "neptune voyager"
        SuperScene.Pluto -> "pluto new horizons"
        SuperScene.Io -> "io jupiter moon volcano"
        SuperScene.Europa -> "europa jupiter moon"
        SuperScene.Ganymede -> "ganymede jupiter moon"
        SuperScene.Flag -> "american flag moon apollo"
        else -> "nebula hubble"
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", AGENT)
        connection.setRequestProperty("Accept", "application/json, image/*;q=0.9, */*;q=0.5")
        return connection
    }

    /** The library gives some addresses as http:// and some with spaces; the phone wants https:// and %20. */
    private fun clean(url: String): String = url.trim().replace("http://", "https://").replace(" ", "%20")

    private fun text(url: String): String? = try {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** The first [COUNT] pictures of the library for [scene]. Throws IOException if the library can't be reached. */
    fun search(scene: SuperScene): List<PlanetPicture> {
        val url = "$SEARCH?q=${URLEncoder.encode(query(scene), "UTF-8")}&media_type=image&page_size=40&page=1"
        val body = text(url) ?: throw IOException("Die NASA-Bibliothek antwortet nicht.")
        val root = try {
            JSONObject(body).getJSONObject("collection")
        } catch (e: Exception) {
            throw IOException("Die Antwort der NASA-Bibliothek war nicht lesbar.")
        }
        val items = root.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<PlanetPicture>()
        val seen = HashSet<String>()
        for (i in 0 until items.length()) {
            if (out.size >= COUNT) break
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONArray("data")?.optJSONObject(0) ?: continue
            val id = data.optString("nasa_id")
            val collection = item.optString("href")
            if (id.isEmpty() || collection.isEmpty() || !seen.add(id)) continue
            var thumb = ""
            val links = item.optJSONArray("links")
            if (links != null) {
                for (k in 0 until links.length()) {
                    val link = links.optJSONObject(k) ?: continue
                    if (link.optString("render") == "image" || link.optString("rel") == "preview") {
                        thumb = link.optString("href")
                        break
                    }
                }
            }
            if (thumb.isEmpty()) continue
            val title = data.optString("title").ifBlank { id }
            val description = data.optString("description").replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            out.add(PlanetPicture(id, title, description, clean(thumb), clean(collection)))
        }
        return out
    }

    /**
     * The sizes of [picture]: from its list in the library, or – if that can't be read – the usual names; the bytes of each are asked
     * (a size the library doesn't have is left out).
     */
    fun sizes(picture: PlanetPicture): List<PictureSize> {
        val urls = ArrayList<String>()
        val body = text(picture.collection)
        if (body != null) {
            try {
                val array = JSONArray(body)
                for (i in 0 until array.length()) urls.add(clean(array.optString(i)))
            } catch (e: Exception) {
                // the usual names below
            }
        }
        val base = "https://images-assets.nasa.gov/image/${picture.id.replace(" ", "%20")}/${picture.id.replace(" ", "%20")}"
        fun pick(suffix: String): String = urls.firstOrNull { it.lowercase().endsWith("~$suffix.jpg") } ?: "$base~$suffix.jpg"
        val wanted = listOf(
            PictureSize("Klein (etwa 640 Pixel)", pick("small")),
            PictureSize("Mittel (etwa 1000 Pixel)", pick("medium")),
            PictureSize("Groß (etwa 2000 Pixel)", pick("large")),
            PictureSize("Original (volle Größe)", pick("orig")),
        )
        return wanted.filter { s ->
            s.size = bytesOf(s.url)
            s.size != 0L
        }
    }

    /** The size of the file at [url] in bytes: -1 if the library doesn't say, 0 if there is no such file. */
    private fun bytesOf(url: String): Long = try {
        val connection = open(url)
        try {
            connection.requestMethod = "HEAD"
            when (connection.responseCode) {
                in 200..299 -> connection.contentLengthLong
                404, 403 -> 0L
                else -> -1L
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        -1L
    }

    /** "1,4 MB" – or "" if the size isn't known. */
    fun megabytes(bytes: Long): String = if (bytes <= 0L) "" else "%.1f MB".format(bytes / 1_048_576.0).replace('.', ',')

    /** A small picture for the grid: at most [maxSide] pixels on the long side, in a light format. Null if it can't be had. */
    fun thumbnail(url: String, maxSide: Int): Bitmap? = try {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 6_000_000) return@use
                        out.write(buffer, 0, n)
                    }
                }
                val bytes = out.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth > 0 && max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Throwable) {
        null
    }

    /** What a download gave: where the picture is now and how big it is. */
    class Saved(val where: String, val width: Int, val height: Int, val bytes: Long)

    /**
     * Downloads [size] of [picture] into the phone's gallery (Pictures/ZENITH; on Android 9 into the app's own picture folder);
     * [progress] goes from 0 to 1. Null if it didn't work. The file is checked to be a picture before it is kept.
     */
    fun download(context: Context, scene: SuperScene, picture: PlanetPicture, size: PictureSize, progress: (Float) -> Unit): Saved? {
        val tag = size.label.substringBefore(' ').lowercase().replace("ß", "ss")
        val safe = picture.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
        val name = "ZENITH-${scene.name}-$safe-$tag.jpg"
        val cache = File(context.cacheDir, "pictures").apply { mkdirs() }
        cache.listFiles()?.forEach { it.delete() }
        val file = File(cache, name)
        return try {
            val connection = open(size.url)
            try {
                if (connection.responseCode !in 200..299) return null
                val total = connection.contentLengthLong
                var got = 0L
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            got += n
                            if (total > 0) progress((got.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && got != total) return null
            } finally {
                connection.disconnect()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            val where = keep(context, file, name) ?: return null
            Saved(where, bounds.outWidth, bounds.outHeight, file.length())
        } catch (e: Throwable) {
            null
        } finally {
            file.delete()
        }
    }

    /** Puts [file] into the gallery; the place it is in now (for the user), or null. */
    private fun keep(context: Context, file: File, name: String): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZENITH")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Bilder/ZENITH"
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        dir.mkdirs()
        val target = File(dir, name)
        file.copyTo(target, overwrite = true)
        return target.parent
    }
}
