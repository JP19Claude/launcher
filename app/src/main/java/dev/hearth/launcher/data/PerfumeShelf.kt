package dev.hearth.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    private val _shelf = MutableStateFlow<List<Perfume>>(emptyList())
    val shelf: StateFlow<List<Perfume>> = _shelf.asStateFlow()

    private val _pick = MutableStateFlow<PerfumePick?>(null)
    val pick: StateFlow<PerfumePick?> = _pick.asStateFlow()

    private val _city = MutableStateFlow("")

    /** The city the weather comes from ("" until one is set). */
    val city: StateFlow<String> = _city.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Clawd is working out today's choice. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var lat = Double.NaN
    private var lon = Double.NaN
    @Volatile
    private var lastTry = 0L
    private val picking = Mutex()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        ClaudeAssistant.init(context)
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    fun remove(id: String) {
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

    private val analysisSystem = """
        Du bist Clawd, ein freundlicher Duft-Experte auf dem Handy des Nutzers. Du erkennst Parfüms und beschreibst sie sachkundig.
        Antworte AUSSCHLIESSLICH mit einem JSON-Array, ohne Text davor oder danach und ohne Code-Block. Ein Objekt pro Parfüm:
        {"name": "Name des Parfüms, mit Konzentration wenn bekannt (z. B. Eau de Toilette)", "house": "Marke", "family": "Duftfamilie, z. B. frisch-aromatisch, holzig-würzig, blumig-fruchtig, orientalisch-süß", "notes": ["bis zu 6 typische Duftnoten"], "seasons": ["die passenden aus Frühling, Sommer, Herbst, Winter"], "tempMin": niedrigste passende Außentemperatur in °C, "tempMax": höchste passende Außentemperatur in °C, "occasions": ["die passenden aus Büro, Alltag, Abend, Date, Sport, Festlich"], "strength": 1 (zart) bis 5 (sehr intensiv), "summary": "ein Satz auf Deutsch: wie es riecht und für wen oder wann es passt"}
        Erkennst du ein Parfüm nicht sicher, nimm den Namen so, wie er dasteht, und schätze vorsichtig. Ist gar kein Parfüm zu erkennen, antworte mit [].
    """.trimIndent()

    private fun parse(answer: String): List<Perfume> {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val array = runCatching { JSONArray(answer.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { o -> Perfume.fromJson(o.put("id", UUID.randomUUID().toString()).put("taste", "neutral")) }
        }
    }

    private fun report(added: List<Perfume>, skipped: Int): String = when {
        added.isEmpty() && skipped > 0 -> "Die kenne ich schon – es ist alles drin."
        added.isEmpty() -> "Ich konnte kein Parfüm erkennen. Schreib mir die Namen oder mach ein schärferes Foto."
        added.size == 1 -> "„${added[0].title}“ ist im Schrank. Gefällt es dir? Stell es unten ein."
        else -> "${added.size} Parfüms sind im Schrank. Sag mir unten, welche du magst und welche nicht."
    }

    /** Parfums written down: Clawd looks at each (or, without a key, just keeps the names). */
    suspend fun addFromText(text: String): PerfumeAdded {
        val names = namesIn(text)
        if (names.isEmpty()) return PerfumeAdded(emptyList(), "Schreib mir, welche Parfüms du hast – zum Beispiel „Sauvage, Black Opium“.")
        if (!ClaudeAssistant.settings.value.hasKey) {
            val added = merge(names.map { Perfume(id = UUID.randomUUID().toString(), name = it) })
            return PerfumeAdded(
                added,
                if (added.isEmpty()) report(added, names.size) else "${added.size} Namen gemerkt. Für Analyse und kluge Tipps trag deinen API-Schlüssel bei „Frag Clawd“ ein.",
            )
        }
        return try {
            val answer = ClaudeAssistant.complete(analysisSystem, "Das hat der Nutzer geschrieben:\n" + names.joinToString("\n"))
            val found = parse(answer)
            val added = merge(found)
            PerfumeAdded(added, report(added, found.size))
        } catch (e: ClaudeApiException) {
            PerfumeAdded(emptyList(), e.message ?: "Clawd konnte das nicht lesen.")
        }
    }

    /** Photos of perfume bottles: Clawd recognises them and looks at each one. */
    suspend fun addFromPhotos(images: List<ByteArray>, note: String): PerfumeAdded {
        if (images.isEmpty()) return PerfumeAdded(emptyList(), "Die Fotos konnte ich nicht öffnen.")
        return try {
            val extra = if (note.isBlank()) "" else "\nDer Nutzer schreibt dazu: ${note.trim()}"
            val answer = ClaudeAssistant.complete(
                analysisSystem,
                "Auf den Fotos stehen Parfümflakons. Erkenne jedes einzelne Parfüm (Marke und Name vom Flakon ablesen) und beschreibe es.$extra",
                images,
                maxTokens = 3500,
            )
            val found = parse(answer)
            val added = merge(found)
            PerfumeAdded(added, report(added, found.size))
        } catch (e: ClaudeApiException) {
            val vision = if (ClaudeAssistant.settings.value.isClaude) "" else " Fotos klappen mit Claude oder Gemini."
            PerfumeAdded(emptyList(), (e.message ?: "Clawd konnte die Fotos nicht lesen.") + vision)
        }
    }

    /** Looks at the perfumes that so far are only names. */
    suspend fun analyseNames(): PerfumeAdded {
        val open = _shelf.value.filterNot { it.analysed }
        if (open.isEmpty()) return PerfumeAdded(emptyList(), "Clawd kennt schon alle genau.")
        return try {
            val answer = ClaudeAssistant.complete(analysisSystem, "Das hat der Nutzer geschrieben:\n" + open.joinToString("\n") { it.title })
            val found = parse(answer)
            merge(found)
            PerfumeAdded(emptyList(), if (found.isEmpty()) "Ich konnte sie nicht einordnen." else "Clawd hat sich ${found.size} Parfüm${if (found.size == 1) "" else "s"} genau angesehen.")
        } catch (e: ClaudeApiException) {
            PerfumeAdded(emptyList(), e.message ?: "Clawd konnte das nicht lesen.")
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

    private val adviceSystem = """
        Du bist Clawd und suchst dem Nutzer jeden Morgen das Parfüm aus. Du bekommst das Wetter, den Tag und seine Sammlung mit seinem Geschmack.
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
        val answer = ClaudeAssistant.complete(adviceSystem, context.toString(), maxTokens = 600)
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
