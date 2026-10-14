package dev.hearth.launcher.data

import android.content.Context
import android.os.Bundle
import java.io.File

/**
 * Moving into Hearth One: everything Glimmer and Clawd keep (their settings, Clawd's pet,
 * water, diary, scores, easter eggs …) comes over into Hearth, so nothing is lost when the
 * separate apps go. Nothing leaves the phone; it goes app to app over the family's bridge.
 */
object AppData {

    /** All of this app's stored preferences, file by file. */
    fun export(context: Context): Bundle {
        val out = Bundle()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.filter { it.name.endsWith(".xml") }?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            val values = Bundle()
            runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).all }.getOrNull()?.forEach { (key, value) ->
                when (value) {
                    is String -> values.putString(key, value)
                    is Int -> values.putInt(key, value)
                    is Long -> values.putLong(key, value)
                    is Float -> values.putFloat(key, value)
                    is Boolean -> values.putBoolean(key, value)
                    is Set<*> -> values.putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
                }
            }
            out.putBundle(name, values)
        }
        return out
    }

    /**
     * Takes another app's data in, without losing any of Hearth's own: what Hearth doesn't
     * have comes over; counts and scores keep the higher one; collections (easter eggs, solved
     * riddles) are joined. From Glimmer, its island settings come over as they were set there.
     */
    @Suppress("DEPRECATION")
    fun import(context: Context, data: Bundle, fromGlimmer: Boolean) {
        for (name in data.keySet()) {
            val values = data.getBundle(name) ?: continue
            val settingsFile = name == SettingsRepository.PREFS_NAME
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val mine = prefs.all
            val edit = prefs.edit()
            for (key in values.keySet()) {
                val theirs = values.get(key) ?: continue
                // In the settings, Hearth's own choices stay – only Glimmer's island settings come over.
                if (settingsFile) {
                    val glimmerKey = key.startsWith("glimmer") || key == "clawdInGlimmer"
                    if (!(fromGlimmer && glimmerKey)) continue
                    put(edit, key, theirs)
                    continue
                }
                val own = mine[key]
                when {
                    own == null -> put(edit, key, theirs)
                    theirs is Int && own is Int -> edit.putInt(key, maxOf(theirs, own))
                    theirs is Long && own is Long -> edit.putLong(key, maxOf(theirs, own))
                    theirs is Float && own is Float -> edit.putFloat(key, maxOf(theirs, own))
                    theirs is ArrayList<*> && own is Set<*> ->
                        edit.putStringSet(key, (own.filterIsInstance<String>() + theirs.filterIsInstance<String>()).toSet())
                    else -> Unit
                }
            }
            edit.apply()
        }
        // The easter eggs are kept in memory too: read them again.
        EasterEggs.reload(context)
    }

    private fun put(edit: android.content.SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is ArrayList<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
