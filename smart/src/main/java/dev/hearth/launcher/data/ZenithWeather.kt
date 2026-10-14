package dev.hearth.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One hour of the forecast. */
class WeatherHour(val hour: Int, val temp: Int, val code: Int, val rain: Int)

/** One day of the forecast ([name] is "Heute", "Mo." …). */
class WeatherDay(val name: String, val max: Int, val min: Int, val code: Int, val rain: Int)

/** The weather where the owner lives: now, the next hours and the next days. */
class WeatherReport(
    val city: String,
    val now: Double,
    val feels: Double,
    val code: Int,
    /** It's night there now. */
    val night: Boolean,
    val wind: Double,
    val humidity: Int,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
    val fetchedAt: Long,
) {
    val sky: String get() = ZenithWeather.skyOf(code)

    /** Today's high, low and chance of rain. */
    val max: Int get() = days.firstOrNull()?.max ?: now.roundToInt()
    val min: Int get() = days.firstOrNull()?.min ?: now.roundToInt()
    val rain: Int get() = days.firstOrNull()?.rain ?: 0

    /** "18 °C, wechselhaft bewölkt" */
    val summary: String get() = "${now.roundToInt()} °C, $sky"
}

/**
 * ZENITH 20, the weather: where the owner lives (typed once – the Duftschrank uses it too), and
 * the forecast from Open-Meteo, which needs no sign-up. Only the coordinates of the city are sent.
 */
object ZenithWeather {

    private const val PREFS = "zenith_weather"

    private lateinit var prefs: SharedPreferences

    private val _city = MutableStateFlow("")

    /** The city ("Köln, Deutschland"; "" until one is set). */
    val city: StateFlow<String> = _city.asStateFlow()

    private val _report = MutableStateFlow<WeatherReport?>(null)
    val report: StateFlow<WeatherReport?> = _report.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var lat = Double.NaN
    private var lon = Double.NaN
    @Volatile
    private var lastTry = 0L
    private val busy = Mutex()

    val hasCity: Boolean get() = !lat.isNaN() && !lon.isNaN()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var name = prefs.getString("city", "").orEmpty()
        var la = prefs.getString("lat", null)?.toDoubleOrNull()
        var lo = prefs.getString("lon", null)?.toDoubleOrNull()
        if (name.isBlank() || la == null || lo == null) {
            // The perfume shelf asked for a city before there was a weather of its own.
            val old = app.getSharedPreferences("hearth_perfume", Context.MODE_PRIVATE)
            name = old.getString("city", "").orEmpty()
            la = old.getString("lat", null)?.toDoubleOrNull()
            lo = old.getString("lon", null)?.toDoubleOrNull()
            if (name.isNotBlank() && la != null && lo != null) {
                prefs.edit().putString("city", name).putString("lat", la.toString()).putString("lon", lo.toString()).apply()
            }
        }
        _city.value = name
        lat = la ?: Double.NaN
        lon = lo ?: Double.NaN
    }

    /** The sky in words, from the WMO weather code. */
    fun skyOf(code: Int): String = when (code) {
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
            _report.value = null
            null
        } catch (e: Exception) {
            "Das Wetter-Verzeichnis ist gerade nicht erreichbar. Prüf das Internet."
        }
    }

    /** Fetches the forecast – at most every 20 minutes, unless [force]. */
    suspend fun refresh(force: Boolean = false) {
        if (!::prefs.isInitialized || !hasCity) return
        val now = System.currentTimeMillis()
        val current = _report.value
        if (!force && current != null && now - current.fetchedAt < 20 * 60_000L) return
        if (!force && now - lastTry < 60_000L) return
        // A forced refresh (a new city) waits for one that's running; an ordinary one gives way to it.
        if (force) busy.lock() else if (!busy.tryLock()) return
        try {
            lastTry = now
            _loading.value = true
            val report = runCatching { fetch() }.getOrNull()
            if (report != null) _report.value = report
        } finally {
            _loading.value = false
            busy.unlock()
        }
    }

    private suspend fun fetch(): WeatherReport? {
        val json = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=6",
        )
        val current = json.optJSONObject("current") ?: return null
        val now = current.optDouble("temperature_2m", Double.NaN)
        if (now.isNaN()) return null
        // The next twelve hours, starting with this one.
        val hours = mutableListOf<WeatherHour>()
        val hourly = json.optJSONObject("hourly")
        val times = hourly?.optJSONArray("time")
        if (hourly != null && times != null) {
            val thisHour = current.optString("time").take(13)
            val temps = hourly.optJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val rains = hourly.optJSONArray("precipitation_probability")
            for (i in 0 until times.length()) {
                val t = times.optString(i)
                if (t.length < 13 || t.take(13) < thisHour) continue
                hours += WeatherHour(
                    hour = t.substring(11, 13).toIntOrNull() ?: 0,
                    temp = (temps?.optDouble(i, now) ?: now).roundToInt(),
                    code = codes?.optInt(i, 3) ?: 3,
                    rain = rains?.optInt(i, 0) ?: 0,
                )
                if (hours.size >= 12) break
            }
        }
        // The next days.
        val days = mutableListOf<WeatherDay>()
        val daily = json.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        if (daily != null && dates != null) {
            val highs = daily.optJSONArray("temperature_2m_max")
            val lows = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            val rains = daily.optJSONArray("precipitation_probability_max")
            for (i in 0 until dates.length()) {
                val name = if (i == 0) {
                    "Heute"
                } else {
                    runCatching { LocalDate.parse(dates.optString(i)).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN) }.getOrDefault("")
                }
                days += WeatherDay(
                    name = name,
                    max = (highs?.optDouble(i, now) ?: now).roundToInt(),
                    min = (lows?.optDouble(i, now) ?: now).roundToInt(),
                    code = codes?.optInt(i, 3) ?: 3,
                    rain = rains?.optInt(i, 0) ?: 0,
                )
            }
        }
        return WeatherReport(
            city = _city.value.substringBefore(','),
            now = now,
            feels = current.optDouble("apparent_temperature", now),
            code = current.optInt("weather_code", 3),
            night = current.optInt("is_day", 1) == 0,
            wind = current.optDouble("wind_speed_10m", 0.0),
            humidity = current.optInt("relative_humidity_2m", 0),
            hours = hours,
            days = days,
            fetchedAt = System.currentTimeMillis(),
        )
    }
}
