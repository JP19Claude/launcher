package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * ZENITH 21.5 – the real pictures of the wallpaper gallery: the NASA Image and Video Library (images.nasa.gov), whose
 * pictures NASA made itself and, as a rule, doesn't hold a copyright on (public domain). The app asks the library's own
 * search (images-api.nasa.gov, no account, no key) for a topic – the Moon in all its phases, Apollo, craters, Earth,
 * the planets, Hubble and Webb – shows the small pictures, and fetches a large one only when it is wanted as a
 * wallpaper. Nothing but the search words leaves the phone.
 */

internal enum class NasaGroup(val label: String) {
    Moon("Mond"),
    Earth("Erde"),
    Planets("Planeten"),
    Space("Weltraum"),
}

/** A topic of the library: what is searched for. */
internal class NasaTopic(val id: String, val label: String, val query: String, val group: NasaGroup)

/** One picture of the library: its name, its text, a small picture and the list of the files it exists in. */
internal class NasaImage(val id: String, val title: String, val description: String, val thumb: String, val collection: String)

internal class NasaPage(val images: List<NasaImage>, val hasMore: Boolean)

/** The files of one picture, biggest first (any of them may be missing). */
internal class NasaAssets(val orig: String?, val large: String?, val medium: String?, val small: String?)

internal object NasaLibrary {

    private const val SEARCH = "https://images-api.nasa.gov/search"
    private const val PAGE_SIZE = 45
    private const val AGENT = "ZENITH-Launcher (Android)"

    val topics: List<NasaTopic> = listOf(
        NasaTopic("m-voll", "Vollmond", "full moon", NasaGroup.Moon),
        NasaTopic("m-sichel", "Sichel", "crescent moon", NasaGroup.Moon),
        NasaTopic("m-phasen", "Mondphasen", "moon phases", NasaGroup.Moon),
        NasaTopic("m-finsternis", "Mondfinsternis", "lunar eclipse", NasaGroup.Moon),
        NasaTopic("m-blut", "Blutmond", "blood moon", NasaGroup.Moon),
        NasaTopic("m-super", "Supermond", "supermoon", NasaGroup.Moon),
        NasaTopic("m-aufgang", "Mondaufgang", "moonrise", NasaGroup.Moon),
        NasaTopic("m-horizont", "Mond am Horizont", "moon horizon from space", NasaGroup.Moon),
        NasaTopic("m-erdaufgang", "Erdaufgang", "earthrise", NasaGroup.Moon),
        NasaTopic("m-iss", "Mond von der ISS", "moon from the international space station", NasaGroup.Moon),
        NasaTopic("m-apollo", "Apollo", "apollo lunar surface", NasaGroup.Moon),
        NasaTopic("m-apollo8", "Apollo 8", "apollo 8 moon", NasaGroup.Moon),
        NasaTopic("m-apollo11", "Apollo 11", "apollo 11 moon", NasaGroup.Moon),
        NasaTopic("m-apollo17", "Apollo 17", "apollo 17 moon", NasaGroup.Moon),
        NasaTopic("m-krater", "Krater", "moon crater", NasaGroup.Moon),
        NasaTopic("m-lro", "LRO-Aufnahmen", "lunar reconnaissance orbiter", NasaGroup.Moon),
        NasaTopic("m-pol", "Südpol", "moon south pole", NasaGroup.Moon),
        NasaTopic("m-rueck", "Rückseite", "far side of the moon", NasaGroup.Moon),
        NasaTopic("m-artemis", "Artemis", "artemis moon", NasaGroup.Moon),
        NasaTopic("m-landschaft", "Mondlandschaft", "lunar landscape", NasaGroup.Moon),
        NasaTopic("m-mosaik", "Mond-Mosaik", "moon mosaic", NasaGroup.Moon),
        NasaTopic("m-erde-mond", "Erde und Mond", "earth and moon", NasaGroup.Moon),

        NasaTopic("e-erde", "Erde aus dem All", "earth from space", NasaGroup.Earth),
        NasaTopic("e-blue", "Blue Marble", "blue marble", NasaGroup.Earth),
        NasaTopic("e-nacht", "Erde bei Nacht", "earth at night city lights", NasaGroup.Earth),
        NasaTopic("e-aurora", "Polarlicht von oben", "aurora from space station", NasaGroup.Earth),
        NasaTopic("e-iss", "ISS", "international space station earth", NasaGroup.Earth),
        NasaTopic("e-sturm", "Wolken und Stürme", "hurricane from space", NasaGroup.Earth),

        NasaTopic("p-saturn", "Saturn", "saturn cassini", NasaGroup.Planets),
        NasaTopic("p-jupiter", "Jupiter", "jupiter juno", NasaGroup.Planets),
        NasaTopic("p-mars", "Mars", "mars surface perseverance", NasaGroup.Planets),
        NasaTopic("p-venus", "Venus", "venus", NasaGroup.Planets),
        NasaTopic("p-merkur", "Merkur", "mercury messenger", NasaGroup.Planets),
        NasaTopic("p-neptun", "Neptun", "neptune", NasaGroup.Planets),
        NasaTopic("p-uranus", "Uranus", "uranus", NasaGroup.Planets),
        NasaTopic("p-pluto", "Pluto", "pluto new horizons", NasaGroup.Planets),
        NasaTopic("p-monde", "Monde der Planeten", "europa titan enceladus", NasaGroup.Planets),
        NasaTopic("p-sonne", "Sonne", "sun solar dynamics observatory", NasaGroup.Planets),

        NasaTopic("s-hubble", "Hubble", "hubble space telescope nebula", NasaGroup.Space),
        NasaTopic("s-webb", "James Webb", "james webb space telescope", NasaGroup.Space),
        NasaTopic("s-galaxien", "Galaxien", "galaxy", NasaGroup.Space),
        NasaTopic("s-nebel", "Nebel", "nebula", NasaGroup.Space),
        NasaTopic("s-milchstrasse", "Milchstraße", "milky way", NasaGroup.Space),
        NasaTopic("s-saeulen", "Säulen der Schöpfung", "pillars of creation", NasaGroup.Space),
        NasaTopic("s-haufen", "Sternhaufen", "star cluster", NasaGroup.Space),
        NasaTopic("s-komet", "Kometen", "comet", NasaGroup.Space),
        NasaTopic("s-supernova", "Supernova", "supernova remnant", NasaGroup.Space),
    )

    fun of(group: NasaGroup): List<NasaTopic> = topics.filter { it.group == group }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
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

    /** One page of the pictures for [topic] (page 0 is the first). Blocking; throws IOException if the library can't be reached. */
    fun search(topic: NasaTopic, page: Int): NasaPage {
        val url = "$SEARCH?q=${URLEncoder.encode(topic.query, "UTF-8")}&media_type=image&page_size=$PAGE_SIZE&page=${page + 1}"
        val body = text(url) ?: throw IOException("Die NASA-Bibliothek antwortet nicht.")
        val root = try {
            JSONObject(body).getJSONObject("collection")
        } catch (e: Exception) {
            throw IOException("Die Antwort der NASA-Bibliothek war nicht lesbar.")
        }
        val items = root.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<NasaImage>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONArray("data")?.optJSONObject(0) ?: continue
            val id = data.optString("nasa_id")
            val collection = item.optString("href")
            if (id.isEmpty() || collection.isEmpty()) continue
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
            out.add(NasaImage(id, title, description, clean(thumb), clean(collection)))
        }
        val links = root.optJSONArray("links")
        var more = false
        if (links != null) {
            for (k in 0 until links.length()) {
                if (links.optJSONObject(k)?.optString("rel") == "next") more = true
            }
        }
        return NasaPage(out, more)
    }

    /** The addresses of the files of [image]: from its list in the library, or – if that can't be read – the usual names. */
    fun assets(image: NasaImage): NasaAssets {
        val urls = ArrayList<String>()
        val body = text(image.collection)
        if (body != null) {
            try {
                val array = JSONArray(body)
                for (i in 0 until array.length()) urls.add(clean(array.optString(i)))
            } catch (e: Exception) {
                // fall back to the usual names below
            }
        }
        val base = "https://images-assets.nasa.gov/image/${image.id.replace(" ", "%20")}/${image.id.replace(" ", "%20")}"
        fun pick(suffix: String): String? = urls.firstOrNull { it.lowercase().endsWith("~$suffix.jpg") } ?: "$base~$suffix.jpg"
        return NasaAssets(pick("orig"), pick("large"), pick("medium"), pick("small"))
    }

    /** A small picture for looking at (null if it can't be had). Blocking. */
    fun fetchBitmap(url: String, maxSide: Int): Bitmap? {
        return try {
            val connection = open(url)
            try {
                if (connection.responseCode !in 200..299) return null
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 6_000_000) return null
                        out.write(buffer, 0, n)
                    }
                }
                val bytes = out.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                connection.disconnect()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** The file at [url], kept in the cache (so it isn't fetched twice); null if it can't be had or is bigger than [maxBytes]. Blocking. */
    fun fetchFile(context: Context, url: String, maxBytes: Long): File? {
        val dir = File(context.cacheDir, "nasa")
        dir.mkdirs()
        val name = Integer.toHexString(url.hashCode()) + "-" + url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._~-]"), "_")
        val file = File(dir, name)
        if (file.exists() && file.length() > 0L) return file
        trimCache(dir, 160L * 1024 * 1024)
        val part = File(dir, "$name.part")
        return try {
            val connection = open(url)
            try {
                if (connection.responseCode !in 200..299) return null
                if (connection.contentLengthLong > maxBytes) return null
                var total = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw IOException("zu groß")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                if (total > 0L && part.renameTo(file)) file else null
            } finally {
                connection.disconnect()
            }
        } catch (e: Throwable) {
            part.delete()
            null
        }
    }

    private fun trimCache(dir: File, limit: Long) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= limit) return
        for (f in files.sortedBy { it.lastModified() }) {
            total -= f.length()
            f.delete()
            if (total <= limit * 3 / 4) break
        }
    }

    /** A picture from a file for looking at on the screen, at most [maxSide] pixels on its long side (null if it can't be read). */
    fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * The wallpaper from a photo: the piece of it that fills a screen of [w] × [h] pixels, [panX]/[panY] (0 … 1) saying where
     * in the photo that piece sits. Only that piece is decoded, so even a huge photo needs little memory.
     */
    @Suppress("DEPRECATION")
    fun wallpaper(file: File, w: Int, h: Int, panX: Float, panY: Float): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null
            val target = w.toFloat() / h
            val cropW: Int
            val cropH: Int
            if (srcW.toFloat() / srcH > target) {
                cropH = srcH
                cropW = (srcH * target).roundToInt().coerceIn(1, srcW)
            } else {
                cropW = srcW
                cropH = (srcW / target).roundToInt().coerceIn(1, srcH)
            }
            val x0 = ((srcW - cropW) * panX.coerceIn(0f, 1f)).roundToInt().coerceIn(0, srcW - cropW)
            val y0 = ((srcH - cropH) * panY.coerceIn(0f, 1f)).roundToInt().coerceIn(0, srcH - cropH)
            var sample = Integer.highestOneBit(max(1, cropH / h))
            if (sample < 1) sample = 1
            val decoder = BitmapRegionDecoder.newInstance(file.path, false) ?: return null
            val region: Bitmap? = try {
                decoder.decodeRegion(Rect(x0, y0, x0 + cropW, y0 + cropH), BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                decoder.recycle()
            }
            if (region == null) return null
            if (region.width == w && region.height == h) region else Bitmap.createScaledBitmap(region, w, h, true)
        } catch (e: Throwable) {
            null
        }
    }
}
