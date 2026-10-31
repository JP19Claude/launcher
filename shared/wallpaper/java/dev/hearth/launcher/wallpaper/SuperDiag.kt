package dev.hearth.launcher.wallpaper

import android.content.Context

/*
 * What went wrong with a wallpaper, kept in a line of text the app can show (and the person can pass on): the graphics chip that won't
 * take a shader, a video the phone can't play. Without it such trouble is only a black screen.
 */
object SuperDiag {
    private const val PREFS = "super_diag"

    fun note(context: Context, what: String?) {
        if (what.isNullOrBlank()) return
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last", what.take(800))
                .putLong("at", System.currentTimeMillis())
                .apply()
        }
    }

    /** The last note with its time, or null. */
    fun last(context: Context): String? = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = p.getString("last", null) ?: return@runCatching null
        val at = p.getLong("at", 0L)
        val time = if (at > 0L) java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(java.util.Date(at)) else ""
        if (time.isEmpty()) text else "$time – $text"
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    }
}
