package dev.hearth.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** What the owner thinks of a perfume in the collection – Clawd never suggests one that's disliked. */
enum class PerfumeTaste(val label: String, val key: String) {
    Love("Mag ich", "love"),
    Neutral("Okay", "neutral"),
    Dislike("Mag ich nicht", "dislike"),
}

/** Who stands beside the advice: Clawd, or the ZENITH mark. */
enum class PerfumeHelper(val label: String, val display: String, val cupboard: String) {
    Clawd("Clawd", "Clawd", "Clawds Duftschrank"),
    Zenith("ZENITH", "ZENITH", "ZENITH-Duftschrank"),
}

/** One perfume of the collection, as Clawd knows it. */
data class Perfume(
    val id: String,
    val name: String,
    val house: String = "",
    /** Scent family, e.g. "frisch-aromatisch". */
    val family: String = "",
    val notes: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    /** The outside temperatures it suits, in °C. */
    val tempMin: Int = 0,
    val tempMax: Int = 35,
    val occasions: List<String> = emptyList(),
    /** 1 (soft) … 5 (very intense). */
    val strength: Int = 3,
    val summary: String = "",
    val taste: PerfumeTaste = PerfumeTaste.Neutral,
    val lastWorn: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    /** Whether there's a picture of it (from a photo); else a drawn bottle stands in. */
    val photo: Boolean = false,
    /** When the picture was set, so a new one shows at once. */
    val photoAt: Long = 0L,
    /** The picture is a product shot from the web (shown whole, on light) – not a crop of a photo. */
    val photoWeb: Boolean = false,
) {
    /** Whether Clawd has looked at it yet (without a key he only keeps the name). */
    val analysed: Boolean get() = family.isNotBlank() || summary.isNotBlank()

    /** House and name, once. */
    val title: String get() = if (house.isNotBlank() && !name.contains(house, ignoreCase = true)) "$house $name" else name

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("house", house).put("family", family)
        .put("notes", JSONArray(notes)).put("seasons", JSONArray(seasons))
        .put("tempMin", tempMin).put("tempMax", tempMax)
        .put("occasions", JSONArray(occasions)).put("strength", strength)
        .put("summary", summary).put("taste", taste.key)
        .put("lastWorn", lastWorn).put("addedAt", addedAt)
        .put("photo", photo).put("photoAt", photoAt).put("photoWeb", photoWeb)

    companion object {
        private fun strings(array: JSONArray?): List<String> =
            if (array == null) emptyList() else (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }

        fun fromJson(o: JSONObject): Perfume? {
            val name = o.optString("name").trim()
            if (name.isEmpty()) return null
            val low = o.optInt("tempMin", 0).coerceIn(-30, 45)
            val high = o.optInt("tempMax", 35).coerceIn(-30, 45)
            return Perfume(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                house = o.optString("house").trim(),
                family = o.optString("family").trim(),
                notes = strings(o.optJSONArray("notes")).take(8),
                seasons = strings(o.optJSONArray("seasons")),
                tempMin = minOf(low, high),
                tempMax = maxOf(low, high),
                occasions = strings(o.optJSONArray("occasions")),
                strength = o.optInt("strength", 3).coerceIn(1, 5),
                summary = o.optString("summary").trim(),
                taste = PerfumeTaste.entries.firstOrNull { it.key == o.optString("taste") } ?: PerfumeTaste.Neutral,
                lastWorn = o.optLong("lastWorn", 0L),
                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                photo = o.optBoolean("photo", false),
                photoAt = o.optLong("photoAt", 0L),
                photoWeb = o.optBoolean("photoWeb", false),
            )
        }
    }
}

/** The day's weather where the owner lives, from Open-Meteo. */
class PerfumeWeather(
    val city: String,
    val now: Double,
    val feels: Double,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val code: Int,
    val wind: Double,
) {
    /** The sky in words, from the WMO weather code. */
    val sky: String
        get() = when (code) {
            0 -> "klar"
            1 -> "überwiegend klar"
            2 -> "wechselhaft bewölkt"
            3 -> "bedeckt"
            45, 48 -> "neblig"
            in 51..57 -> "Nieselregen"
            in 61..67 -> "Regen"
            in 71..77 -> "Schnee"
            in 80..82 -> "Regenschauer"
            85, 86 -> "Schneeschauer"
            in 95..99 -> "Gewitter"
            else -> "wechselhaft"
        }

    /** "Köln · 18 °C (gefühlt 16), bis 21 °C, wechselhaft bewölkt, Regen 20 %". */
    val line: String
        get() = "$city · ${now.toInt()} °C (gefühlt ${feels.toInt()}), bis ${max.toInt()} °C, $sky" +
            if (rainChance >= 20) ", Regen $rainChance %" else ""
}

/** Today's choice: which perfume, and why. */
class PerfumePick(val perfumeId: String, val reason: String, val weather: String, val day: String)

/** What came of adding perfumes. */
class PerfumeAdded(val added: List<Perfume>, val message: String)

/**
 * ZENITH 19.7, Clawd's perfume shelf: the perfumes the owner has – written down or photographed,
 * looked at by Clawd (scent family, notes, season, temperatures) and remembered – with which
 * ones the owner likes and which not, and each day's choice for the weather. The analysis and
 * the daily advice come from the AI (the owner's own key); without one Clawd keeps the names and
 * chooses by temperature and taste on his own.
 */
object PerfumeShelf {

    private const val PREFS = "hearth_perfume"

    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context

    private val _shelf = MutableStateFlow<List<Perfume>>(emptyList())
    val shelf: StateFlow<List<Perfume>> = _shelf.asStateFlow()

    private val _pick = MutableStateFlow<PerfumePick?>(null)
    val pick: StateFlow<PerfumePick?> = _pick.asStateFlow()

    private val _city = MutableStateFlow("")

    /** The city the weather comes from ("" until one is set). */
    val city: StateFlow<String> = _city.asStateFlow()

    private val _helper = MutableStateFlow(PerfumeHelper.Clawd)

    /** Who stands beside the advice (the widget and the shelf show Clawd or the ZENITH mark). */
    val helper: StateFlow<PerfumeHelper> = _helper.asStateFlow()

    private val who: String get() = _helper.value.display

    private val _busy = MutableStateFlow(false)

    /** Clawd is working out today's choice. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var lat = Double.NaN
    private var lon = Double.NaN
    @Volatile
    private var lastTry = 0L
    private val picking = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pictureJob: Job? = null
    private val tried = mutableSetOf<String>()

    private val _fetching = MutableStateFlow(false)

    /** The pictures of the bottles are being looked for on the web. */
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        ClaudeAssistant.init(context)
        app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _helper.value = PerfumeHelper.entries.firstOrNull { it.name == prefs.getString("helper", null) } ?: PerfumeHelper.Clawd
        _shelf.value = runCatching {
            val array = JSONArray(prefs.getString("shelf", "[]"))
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(Perfume::fromJson) }
        }.getOrDefault(emptyList())
        _city.value = prefs.getString("city", "").orEmpty()
        lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: Double.NaN
        lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: Double.NaN
        val day = prefs.getString("pickDay", null)
        val id = prefs.getString("pickId", null)
        if (day != null && id != null) {
            _pick.value = PerfumePick(id, prefs.getString("pickWhy", "").orEmpty(), prefs.getString("pickWeather", "").orEmpty(), day)
        }
    }

    private fun saveShelf() {
        prefs.edit().putString("shelf", JSONArray(_shelf.value.map { it.toJson() }).toString()).apply()
    }

    private fun savePick() {
        val p = _pick.value
        prefs.edit()
            .putString("pickDay", p?.day)
            .putString("pickId", p?.perfumeId)
            .putString("pickWhy", p?.reason)
            .putString("pickWeather", p?.weather)
            .apply()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun same(a: Perfume, b: Perfume): Boolean = norm(a) == norm(b)

    private fun norm(p: Perfume): String = (p.house + p.name).lowercase().filter { it.isLetterOrDigit() }

    // ---- The collection -------------------------------------------------------------------------

    fun setTaste(id: String, taste: PerfumeTaste) {
        _shelf.value = _shelf.value.map { if (it.id == id) it.copy(taste = taste) else it }
        saveShelf()
        // A perfume that's no longer liked can't stay today's choice.
        if (taste == PerfumeTaste.Dislike && _pick.value?.perfumeId == id) {
            _pick.value = null
            savePick()
        }
    }

    fun setHelper(helper: PerfumeHelper) {
        _helper.value = helper
        prefs.edit().putString("helper", helper.name).apply()
    }

    fun remove(id: String) {
        runCatching { photoFile(id).delete() }
        _shelf.value = _shelf.value.filterNot { it.id == id }
        saveShelf()
        if (_pick.value?.perfumeId == id) {
            _pick.value = null
            savePick()
        }
    }

    fun markWorn(id: String) {
        _shelf.value = _shelf.value.map { if (it.id == id) it.copy(lastWorn = System.currentTimeMillis()) else it }
        saveShelf()
    }

    private fun merge(found: List<Perfume>): List<Perfume> {
        val current = _shelf.value.toMutableList()
        val added = mutableListOf<Perfume>()
        found.forEach { p ->
            val index = current.indexOfFirst { same(it, p) }
            when {
                index < 0 -> {
                    current += p
                    added += p
                }
                // Known by name only so far: now Clawd has looked at it.
                !current[index].analysed && p.analysed -> current[index] = p.copy(
                    id = current[index].id,
                    taste = current[index].taste,
                    lastWorn = current[index].lastWorn,
                    addedAt = current[index].addedAt,
                    photo = current[index].photo,
                    photoAt = current[index].photoAt,
                    photoWeb = current[index].photoWeb,
                )
            }
        }
        _shelf.value = current
        saveShelf()
        return added
    }

    /** Names split off a line of text ("Sauvage, Bleu de Chanel; Black Opium"). */
    private fun namesIn(text: String): List<String> =
        text.split(',', ';', '\n').map { it.trim().trim('•', '-', '*', ' ') }.filter { it.length in 2..80 }

    private fun analysisSystem(): String = """
        Du bist $who, ein freundlicher Duft-Experte auf dem Handy des Nutzers. Du erkennst Parfüms und beschreibst sie sachkundig.
        Antworte AUSSCHLIESSLICH mit einem JSON-Array, ohne Text davor oder danach und ohne Code-Block. Ein Objekt pro Parfüm:
        {"name": "Name des Parfüms, mit Konzentration wenn bekannt (z. B. Eau de Toilette)", "house": "Marke", "family": "Duftfamilie, z. B. frisch-aromatisch, holzig-würzig, blumig-fruchtig, orientalisch-süß", "notes": ["bis zu 6 typische Duftnoten"], "seasons": ["die passenden aus Frühling, Sommer, Herbst, Winter"], "tempMin": niedrigste passende Außentemperatur in °C, "tempMax": höchste passende Außentemperatur in °C, "occasions": ["die passenden aus Büro, Alltag, Abend, Date, Sport, Festlich"], "strength": 1 (zart) bis 5 (sehr intensiv), "summary": "ein Satz auf Deutsch: wie es riecht und für wen oder wann es passt"}
        Bei Fotos gib zusätzlich an: "photo": die Nummer des Fotos (1 = das erste) und "box": [links, oben, rechts, unten] – der Rahmen genau um diesen Flakon, als Zahlen von 0 bis 1000 bezogen auf Breite und Höhe dieses Fotos.
        Erkennst du ein Parfüm nicht sicher, nimm den Namen so, wie er dasteht, und schätze vorsichtig. Ist gar kein Parfüm zu erkennen, antworte mit [].
    """.trimIndent()

    /** A perfume the AI described and, from a photo, where on which photo its bottle is. */
    private class Found(val perfume: Perfume, val photo: Int, val box: List<Double>?)

    private fun parse(answer: String): List<Found> {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val array = runCatching { JSONArray(answer.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val index = o.optInt("photo", 1)
            o.remove("photo")
            o.remove("photoAt")
            val perfume = Perfume.fromJson(o.put("id", UUID.randomUUID().toString()).put("taste", "neutral")) ?: return@mapNotNull null
            val raw = o.optJSONArray("box")
            val box = if (raw != null && raw.length() == 4) List(4) { raw.optDouble(it, -1.0) } else null
            val valid = box != null && box.all { it in 0.0..1000.0 } && box[2] - box[0] >= 40.0 && box[3] - box[1] >= 40.0
            Found(perfume, index, if (valid) box else null)
        }
    }

    private fun report(added: List<Perfume>, skipped: Int): String = when {
        added.isEmpty() && skipped > 0 -> "Die kenne ich schon – es ist alles drin."
        added.isEmpty() -> "Ich konnte kein Parfüm erkennen. Schreib mir die Namen oder mach ein schärferes Foto."
        added.size == 1 -> "„${added[0].title}“ ist im Schrank. Gefällt es dir? Stell es unten ein."
        else -> "${added.size} Parfüms sind im Schrank. Sag mir unten, welche du magst und welche nicht."
    }

    /** Parfums written down: the helper looks at each (or, without a key, just keeps the names). */
    suspend fun addFromText(text: String): PerfumeAdded {
        val names = namesIn(text)
        if (names.isEmpty()) return PerfumeAdded(emptyList(), "Schreib mir, welche Parfüms du hast – zum Beispiel „Sauvage, Black Opium“.")
        if (!ClaudeAssistant.settings.value.hasKey) {
            val added = merge(names.map { Perfume(id = UUID.randomUUID().toString(), name = it) })
            searchPictures()
            return PerfumeAdded(
                added,
                if (added.isEmpty()) report(added, names.size) else "${added.size} Namen gemerkt. Für Analyse und kluge Tipps trag deinen API-Schlüssel bei „Frag Clawd“ ein.",
            )
        }
        return try {
            val answer = ClaudeAssistant.complete(analysisSystem(), "Das hat der Nutzer geschrieben:\n" + names.joinToString("\n"))
            val found = parse(answer)
            val added = merge(found.map { it.perfume })
            searchPictures()
            PerfumeAdded(added, report(added, found.size))
        } catch (e: ClaudeApiException) {
            PerfumeAdded(emptyList(), e.message ?: "$who konnte das nicht lesen.")
        }
    }

    /** Photos of perfume bottles: recognised, looked at one by one – and each bottle cut out as its picture. */
    suspend fun addFromPhotos(images: List<ByteArray>, note: String): PerfumeAdded {
        if (images.isEmpty()) return PerfumeAdded(emptyList(), "Die Fotos konnte ich nicht öffnen.")
        return try {
            val extra = if (note.isBlank()) "" else "\nDer Nutzer schreibt dazu: ${note.trim()}"
            val answer = ClaudeAssistant.complete(
                analysisSystem(),
                "Auf den ${images.size} Fotos stehen Parfümflakons. Erkenne jedes einzelne Parfüm (Marke und Name vom Flakon ablesen), beschreibe es und gib an, auf welchem Foto und wo es steht.$extra",
                images,
                maxTokens = 3500,
            )
            val found = parse(answer)
            val added = merge(found.map { it.perfume })
            withContext(Dispatchers.IO) { keepPhotos(images, found) }
            searchPictures()
            PerfumeAdded(added, report(added, found.size))
        } catch (e: ClaudeApiException) {
            val vision = if (ClaudeAssistant.settings.value.isClaude) "" else " Fotos klappen mit Claude oder Gemini."
            PerfumeAdded(emptyList(), (e.message ?: "$who konnte die Fotos nicht lesen.") + vision)
        }
    }

    /** Looks at the perfumes that so far are only names. */
    suspend fun analyseNames(): PerfumeAdded {
        val open = _shelf.value.filterNot { it.analysed }
        if (open.isEmpty()) return PerfumeAdded(emptyList(), "$who kennt schon alle genau.")
        return try {
            val answer = ClaudeAssistant.complete(analysisSystem(), "Das hat der Nutzer geschrieben:\n" + open.joinToString("\n") { it.title })
            val found = parse(answer)
            merge(found.map { it.perfume })
            searchPictures()
            PerfumeAdded(emptyList(), if (found.isEmpty()) "Ich konnte sie nicht einordnen." else "$who hat sich ${found.size} Parfüm${if (found.size == 1) "" else "s"} genau angesehen.")
        } catch (e: ClaudeApiException) {
            PerfumeAdded(emptyList(), e.message ?: "$who konnte das nicht lesen.")
        }
    }

    // ---- Pictures ----------------------------------------------------------------------------------

    fun photoFile(id: String): File = File(File(app.filesDir, "perfume"), "$id.jpg")

    /** The perfume's picture, small (null when it has none). */
    fun loadPhoto(id: String): Bitmap? = runCatching { BitmapFactory.decodeFile(photoFile(id).path) }.getOrNull()

    /** Cuts the bottle out of [bytes] (by [box], 0..1000 each way; the whole photo without) and keeps it, 512 px at most. */
    private fun storePhoto(id: String, bytes: ByteArray, box: List<Double>?): Boolean {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching false
            var region = source
            if (box != null) {
                val w = source.width
                val h = source.height
                val marginX = (box[2] - box[0]) * 0.06
                val marginY = (box[3] - box[1]) * 0.06
                val left = ((box[0] - marginX).coerceAtLeast(0.0) / 1000.0 * w).toInt()
                val top = ((box[1] - marginY).coerceAtLeast(0.0) / 1000.0 * h).toInt()
                val right = ((box[2] + marginX).coerceAtMost(1000.0) / 1000.0 * w).toInt().coerceAtMost(w)
                val bottom = ((box[3] + marginY).coerceAtMost(1000.0) / 1000.0 * h).toInt().coerceAtMost(h)
                if (right - left >= 48 && bottom - top >= 48) region = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            }
            val scale = minOf(1f, 512f / maxOf(region.width, region.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(region, (region.width * scale).toInt().coerceAtLeast(1), (region.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                region
            }
            val dir = File(app.filesDir, "perfume").apply { mkdirs() }
            File(dir, "$id.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }.getOrDefault(false)
    }

    /** Gives each perfume that has no picture yet the bottle cut out of its photo. */
    private fun keepPhotos(images: List<ByteArray>, found: List<Found>) {
        val stored = mutableSetOf<String>()
        found.forEach { f ->
            val target = _shelf.value.firstOrNull { same(it, f.perfume) } ?: return@forEach
            if (target.photo || target.id in stored) return@forEach
            val bytes = images.getOrNull(f.photo - 1) ?: images.firstOrNull() ?: return@forEach
            if (storePhoto(target.id, bytes, f.box)) stored += target.id
        }
        if (stored.isNotEmpty()) {
            val at = System.currentTimeMillis()
            _shelf.update { list -> list.map { if (it.id in stored) it.copy(photo = true, photoAt = at, photoWeb = false) else it } }
            saveShelf()
        }
    }

    /** A picture for a perfume: the owner's own photo, or ([web]) a product shot chosen from the web. */
    suspend fun setPhoto(id: String, bytes: ByteArray, web: Boolean = false): Boolean {
        val ok = withContext(Dispatchers.IO) { storePhoto(id, bytes, null) }
        if (ok) {
            val at = System.currentTimeMillis()
            _shelf.update { list -> list.map { if (it.id == id) it.copy(photo = true, photoAt = at, photoWeb = web) else it } }
            saveShelf()
        }
        return ok
    }

    /** The first picture the web has for this perfume that can be had; false when none could. */
    suspend fun findPicture(id: String): Boolean {
        val perfume = _shelf.value.firstOrNull { it.id == id } ?: return false
        for (hit in PerfumeImages.search(perfume.title).take(6)) {
            val bytes = withContext(Dispatchers.IO) { PerfumeImages.download(hit.full) ?: PerfumeImages.download(hit.thumb) } ?: continue
            if (setPhoto(id, bytes, web = true)) return true
        }
        return false
    }

    /**
     * Looks for the picture of every perfume that has none, one after the other, in the background
     * – each only once while the app runs, unless [again].
     */
    fun searchPictures(again: Boolean = false) {
        if (!::prefs.isInitialized || pictureJob?.isActive == true) return
        if (again) tried.clear()
        pictureJob = scope.launch {
            _fetching.value = true
            try {
                for (perfume in _shelf.value.toList()) {
                    if (perfume.photo || perfume.id in tried) continue
                    tried += perfume.id
                    findPicture(perfume.id)
                    delay(500)
                }
            } finally {
                _fetching.value = false
            }
        }
    }

    /** A photo as JPEG, small enough to send (null when it can't be read). */
    fun readPhoto(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bitmap.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    // ---- Weather and today's choice -------------------------------------------------------------

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Sets the city the weather comes from (null: it worked; else why not). */
    suspend fun setCity(query: String): String? {
        val q = query.trim()
        if (q.length < 2) return "Schreib einen Ort, zum Beispiel „Köln“."
        return try {
            val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=1&language=de&format=json")
            val hit = json.optJSONArray("results")?.optJSONObject(0) ?: return "Den Ort „$q“ kenne ich nicht."
            val name = hit.optString("name").ifBlank { q }
            val country = hit.optString("country")
            lat = hit.getDouble("latitude")
            lon = hit.getDouble("longitude")
            _city.value = if (country.isBlank()) name else "$name, $country"
            prefs.edit().putString("city", _city.value).putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
            null
        } catch (e: Exception) {
            "Das Wetter-Verzeichnis ist gerade nicht erreichbar. Prüf das Internet."
        }
    }

    private suspend fun fetchWeather(): PerfumeWeather? {
        if (lat.isNaN() || lon.isNaN()) return null
        val json = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1",
        )
        val current = json.optJSONObject("current") ?: return null
        val daily = json.optJSONObject("daily") ?: return null
        val now = current.optDouble("temperature_2m", Double.NaN)
        if (now.isNaN()) return null
        return PerfumeWeather(
            city = _city.value.substringBefore(','),
            now = now,
            feels = current.optDouble("apparent_temperature", now),
            max = daily.optJSONArray("temperature_2m_max")?.optDouble(0, now) ?: now,
            min = daily.optJSONArray("temperature_2m_min")?.optDouble(0, now) ?: now,
            rainChance = daily.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0,
            code = current.optInt("weather_code", 3),
            wind = current.optDouble("wind_speed_10m", 0.0),
        )
    }

    /** A typical day's high for the month, when there's no weather to go by. */
    private fun typicalHigh(): Double = doubleArrayOf(3.0, 5.0, 10.0, 15.0, 19.0, 22.0, 25.0, 25.0, 20.0, 14.0, 8.0, 4.0)[Calendar.getInstance().get(Calendar.MONTH)]

    private fun daysSinceWorn(p: Perfume): Int? =
        if (p.lastWorn <= 0L) null else ((System.currentTimeMillis() - p.lastWorn) / 86_400_000L).toInt()

    private fun adviceSystem(): String = """
        Du bist $who und suchst dem Nutzer jeden Morgen das Parfüm aus. Du bekommst das Wetter, den Tag und seine Sammlung mit seinem Geschmack.
        Regeln: Wähle nie ein Parfüm mit taste = "dislike". Bevorzuge taste = "love", wechsle aber ab: nicht dasselbe wie an den letzten Tagen (daysSinceWorn klein heißt: erst kürzlich getragen). Achte auf die Temperatur (tempMin bis tempMax gegen die Höchsttemperatur des Tages), auf Regen, die Jahreszeit und den Wochentag (Arbeitstag: eher dezent, Wochenende oder Abend: gern auffälliger).
        Antworte NUR mit JSON, ohne Code-Block: {"id": "<id aus der Sammlung>", "reason": "ein bis zwei freundliche Sätze auf Deutsch in der Du-Form, mit Bezug aufs Wetter und den Tag"}
    """.trimIndent()

    private suspend fun adviceFromAi(candidates: List<Perfume>, weather: PerfumeWeather?): Pair<Perfume, String>? {
        val calendar = Calendar.getInstance()
        val context = JSONObject()
            .put("datum", SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMAN).format(Date()))
            .put("wetter", weather?.line ?: "unbekannt (nur die Jahreszeit zählt)")
            .put("hoechsttemperatur", weather?.max?.toInt() ?: typicalHigh().toInt())
            .put("regenwahrscheinlichkeit", weather?.rainChance ?: 0)
            .put("wochenende", calendar.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY })
            .put(
                "sammlung",
                JSONArray(
                    candidates.map { p ->
                        JSONObject()
                            .put("id", p.id).put("name", p.title).put("family", p.family)
                            .put("notes", JSONArray(p.notes)).put("tempMin", p.tempMin).put("tempMax", p.tempMax)
                            .put("strength", p.strength).put("occasions", JSONArray(p.occasions))
                            .put("taste", p.taste.key).put("daysSinceWorn", daysSinceWorn(p) ?: JSONObject.NULL)
                    },
                ),
            )
        val answer = ClaudeAssistant.complete(adviceSystem(), context.toString(), maxTokens = 600)
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(answer.substring(start, end + 1)) }.getOrNull() ?: return null
        val chosen = candidates.firstOrNull { it.id == json.optString("id") } ?: return null
        return chosen to json.optString("reason").trim()
    }

    /** Without the AI: the best fit by temperature and taste, not the one worn yesterday. */
    private fun adviceLocal(candidates: List<Perfume>, weather: PerfumeWeather?, day: String): Pair<Perfume, String> {
        val high = weather?.max ?: typicalHigh()
        val best = candidates.maxByOrNull { p ->
            val fit = when {
                high < p.tempMin -> -(p.tempMin - high) / 3.0
                high > p.tempMax -> -(high - p.tempMax) / 3.0
                else -> 0.0
            }
            val taste = if (p.taste == PerfumeTaste.Love) 2.0 else 0.0
            val worn = daysSinceWorn(p)
            val rest = if (worn != null && worn < 3) -3.0 + worn else 0.0
            val jitter = ((p.id + day).hashCode() and 0xFF) / 255.0
            fit + taste + rest + jitter
        } ?: candidates.first()
        val where = if (weather != null) "Bei bis zu ${weather.max.toInt()} °C und ${weather.sky}" else "Für heute"
        val what = best.summary.ifBlank { best.family }.ifBlank { "ein guter Griff aus deinem Schrank" }
        return best to "$where passt ${best.title}: ${what.replaceFirstChar { it.lowercase() }}"
    }

    /**
     * Makes sure today has a choice: the AI's when there's a key, Clawd's own otherwise. [force]
     * picks again, not [exclude] (the one that was shown and not wanted).
     */
    suspend fun refreshToday(force: Boolean = false, exclude: Set<String> = emptySet()) {
        if (!::prefs.isInitialized) return
        val day = today()
        val current = _pick.value
        val candidates = _shelf.value.filter { it.taste != PerfumeTaste.Dislike }
        if (candidates.isEmpty()) {
            if (current != null) {
                _pick.value = null
                savePick()
            }
            return
        }
        if (!force && current != null && current.day == day && candidates.any { it.id == current.perfumeId }) return
        val now = System.currentTimeMillis()
        if (!force && now - lastTry < 5 * 60_000L) return
        if (!picking.tryLock()) return
        try {
            lastTry = now
            _busy.value = true
            val weather = runCatching { fetchWeather() }.getOrNull()
            val pool = candidates.filter { it.id !in exclude }.ifEmpty { candidates }
            var chosen: Pair<Perfume, String>? = null
            if (ClaudeAssistant.settings.value.hasKey) {
                chosen = runCatching { adviceFromAi(pool, weather) }.getOrNull()
            }
            val (perfume, reason) = chosen ?: adviceLocal(pool, weather, day)
            _pick.value = PerfumePick(perfume.id, reason, weather?.line.orEmpty(), day)
            savePick()
        } finally {
            _busy.value = false
            picking.unlock()
        }
    }
}
