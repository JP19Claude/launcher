package dev.hearth.launcher.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One picture a search found: a small one to look at, the full one to keep, and where it came from. */
class PerfumeImageHit(val thumb: String, val full: String, val source: String)

/**
 * ZENITH 19.7, the pictures of the perfume bottles: found on the web by the perfume's name – first
 * in the image search (DuckDuckGo), then at Open Beauty Facts (photos of cosmetics) and Wikipedia.
 * Only the name of the perfume is sent. Not every hit is the right bottle, which is why the
 * shelf lets you pick another one or use a photo of your own.
 */
object PerfumeImages {

    private const val AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun open(url: String, referer: String? = null): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", AGENT)
        connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
        if (referer != null) connection.setRequestProperty("Referer", referer)
        return connection
    }

    private fun text(url: String, referer: String? = null): String? = runCatching {
        val connection = open(url, referer)
        try {
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** The image behind [url] (null if it can't be had, isn't a picture or is huge). Blocking. */
    fun download(url: String): ByteArray? {
        return runCatching {
            if (!url.startsWith("https://")) return@runCatching null
            val connection = open(url)
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val type = connection.contentType
                if (type != null && !type.startsWith("image/")) return@runCatching null
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 8_000_000) return@runCatching null
                        out.write(buffer, 0, n)
                    }
                }
                out.toByteArray()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** A small bitmap of the image behind [url], for looking at (null if it can't be had). Blocking. */
    fun bitmap(url: String): Bitmap? {
        val bytes = download(url) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 480) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Pictures of the perfume called [title], best guesses first. */
    suspend fun search(title: String, max: Int = 10): List<PerfumeImageHit> = withContext(Dispatchers.IO) {
        val hits = mutableListOf<PerfumeImageHit>()
        hits += runCatching { duck("$title perfume bottle") }.getOrDefault(emptyList())
        if (hits.size < max) hits += runCatching { openBeauty(title) }.getOrDefault(emptyList())
        if (hits.size < max) hits += runCatching { wikipedia("$title perfume") }.getOrDefault(emptyList())
        hits.distinctBy { it.full }.take(max)
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** The web image search: a token from its page, then the results as JSON. */
    private fun duck(query: String): List<PerfumeImageHit> {
        val q = encode(query)
        val page = text("https://duckduckgo.com/?q=$q&iax=images&ia=images") ?: return emptyList()
        val token = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1) ?: return emptyList()
        val body = text("https://duckduckgo.com/i.js?l=de-de&o=json&q=$q&vqd=$token&f=,,,,,&p=1", referer = "https://duckduckgo.com/") ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val hits = mutableListOf<PerfumeImageHit>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val full = o.optString("image")
            val thumb = o.optString("thumbnail").ifBlank { full }
            val w = o.optInt("width", 0)
            val h = o.optInt("height", 0)
            // A bottle's picture: big enough, and not a wide banner.
            if (!full.startsWith("https://") || w < 250 || h < 250) continue
            val ratio = w.toFloat() / h
            if (ratio < 0.45f || ratio > 1.6f) continue
            hits += PerfumeImageHit(thumb, full, "Websuche")
            if (hits.size >= 10) break
        }
        return hits
    }

    /** Open Beauty Facts: people's photos of cosmetics and perfumes, found by name. */
    private fun openBeauty(query: String): List<PerfumeImageHit> {
        val body = text(
            "https://world.openbeautyfacts.org/cgi/search.pl?search_terms=${encode(query)}&search_simple=1&action=process&json=1&page_size=8" +
                "&fields=product_name,brands,image_front_url,image_front_small_url",
        ) ?: return emptyList()
        val products = JSONObject(body).optJSONArray("products") ?: return emptyList()
        val hits = mutableListOf<PerfumeImageHit>()
        for (i in 0 until products.length()) {
            val o = products.optJSONObject(i) ?: continue
            val full = o.optString("image_front_url")
            if (!full.startsWith("https://")) continue
            hits += PerfumeImageHit(o.optString("image_front_small_url").ifBlank { full }, full, "Open Beauty Facts")
        }
        return hits
    }

    /** Wikipedia's picture of the article about it, when there is one (famous perfumes). */
    private fun wikipedia(query: String): List<PerfumeImageHit> {
        val body = text(
            "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${encode(query)}&gsrlimit=3" +
                "&prop=pageimages&piprop=thumbnail&pithumbsize=500&format=json",
        ) ?: return emptyList()
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val hits = mutableListOf<PerfumeImageHit>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val source = pages.optJSONObject(keys.next())?.optJSONObject("thumbnail")?.optString("source").orEmpty()
            if (source.startsWith("https://")) hits += PerfumeImageHit(source, source, "Wikipedia")
        }
        return hits
    }
}
