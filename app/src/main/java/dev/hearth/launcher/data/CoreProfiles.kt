package dev.hearth.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** A look saved in the Core Enforcer Studio: what it's called, and the saved values. */
class CoreProfile(val id: String, val name: String, val values: String, val saved: Long)

/**
 * ZENITH 19.3, Core Enforcer Studio: looks saved under a name and brought back with one tap.
 * Only what makes the look is kept – icons, clock, dock, colors, glass, the Z-Angriff – never
 * app locks, Glimmer, the Claude key or Projekt Zenith's secret.
 */
object CoreProfiles {

    /** The settings that make up a look (by their saved names). */
    private val LOOK_KEYS = setOf(
        "iconStyle", "iconShape", "iconSize", "showLabels", "labelSize",
        "clockStyle", "clockFont", "dockStyle", "pageTransition",
        "accent", "glassLook", "glassTint", "glassTintStrength", "glassBlur", "glassRefraction",
        "glassDispersion", "glassSpecular", "glassQuality", "omegaGlass", "omegaColor",
        "fluidDesign", "zenithWallpaper", "zenithDaylight", "smoothMode",
        "showGreeting", "showSearchPill", "galaxyClaude", "ccStyle",
        "strikeColor", "strikeSpeed", "zenithStrike", "colorOsClock", "colorOsDock",
    )

    private fun prefs(context: Context) = context.getSharedPreferences("core_enforcer", Context.MODE_PRIVATE)

    /** The current look as JSON: [SettingsRepository.exportJson] cut down to the look's settings. */
    fun snapshot(repo: SettingsRepository): String {
        val all = JSONObject(repo.exportJson()).optJSONObject("settings") ?: JSONObject()
        val look = JSONObject()
        all.keys().forEach { key -> if (key in LOOK_KEYS || key.startsWith("cc")) look.put(key, all.get(key)) }
        return JSONObject().put("hearth", 1).put("settings", look).toString()
    }

    /** Puts a [snapshot] back. */
    fun restore(repo: SettingsRepository, values: String): Boolean = repo.importJson(values)

    fun list(context: Context): List<CoreProfile> = runCatching {
        val json = JSONArray(prefs(context).getString("profiles", "[]"))
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            CoreProfile(o.getString("id"), o.getString("name"), o.getString("values"), o.optLong("saved"))
        }
    }.getOrDefault(emptyList())

    private fun store(context: Context, profiles: List<CoreProfile>) {
        val json = JSONArray()
        profiles.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name).put("values", it.values).put("saved", it.saved)) }
        prefs(context).edit().putString("profiles", json.toString()).apply()
    }

    /** Saves the current look as [name]; one with the same name is replaced. */
    fun save(context: Context, repo: SettingsRepository, name: String): CoreProfile {
        val clean = name.trim().ifEmpty { "Look" }.take(30)
        val profile = CoreProfile(System.currentTimeMillis().toString(36), clean, snapshot(repo), System.currentTimeMillis())
        store(context, listOf(profile) + list(context).filterNot { it.name.equals(clean, ignoreCase = true) })
        return profile
    }

    fun delete(context: Context, id: String) = store(context, list(context).filterNot { it.id == id })

    /** A look rolled at random – bold, but always something that works. */
    fun rolled(s: LauncherSettings, r: Random = Random.Default): LauncherSettings {
        fun step(from: Float, to: Float) = (Math.round((from + r.nextFloat() * (to - from)) * 20f) / 20f)
        val clock = ClockStyle.entries.filter { it != ClockStyle.Hidden }.random(r)
        val dock = DockStyle.entries.random(r)
        return s.copy(
            accent = AccentColor.entries.random(r),
            iconShape = IconShape.entries.random(r),
            iconStyle = IconStyle.entries.random(r),
            pageTransition = PageTransition.entries.random(r),
            glassTint = GlassTint.entries.random(r),
            glassRefraction = step(0.4f, 2.2f),
            glassBlur = step(0.5f, 2f),
            glassDispersion = step(0.1f, 1.3f),
            glassSpecular = step(0.6f, 1.8f),
            glassTintStrength = step(0.5f, 2f),
            strikeColor = StrikeColor.entries.random(r),
        ).withClock(clock).withDock(dock)
    }
}
