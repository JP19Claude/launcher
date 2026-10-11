package dev.hearth.launcher.data

import android.content.Context

/**
 * ZENITH 20, what the jubilee's eggs remember between visits: which of the aurora's five colors
 * have been tried (all five is an egg).
 */
object JubileeTrail {

    private const val PREFS = "zenith_jubilee_trail"

    private var seen: Set<String>? = null

    /** The aurora was on in [look]. Cheap when it's an old one. */
    fun sawAurora(context: Context, look: AuroraLook) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = seen ?: prefs.getStringSet("aurora", emptySet()).orEmpty().toSet().also { seen = it }
        if (look.name in known) return
        val now = known + look.name
        seen = now
        prefs.edit().putStringSet("aurora", now).apply()
        if (now.size >= AuroraLook.entries.size) EasterEggs.find(context, "auroraall")
    }
}
