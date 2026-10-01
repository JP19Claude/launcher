package dev.hearth.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How often and when an app was last opened from the launcher. */
data class AppUsage(val count: Int, val lastLaunch: Long)

/** Remembers app launches, for the "Vorschläge" folder of the App Library. */
class UsageRepository(context: Context) {

    private val prefs = context.getSharedPreferences("hearth_usage", Context.MODE_PRIVATE)

    private val _usage = MutableStateFlow(read())
    val usage: StateFlow<Map<String, AppUsage>> = _usage.asStateFlow()

    fun recordLaunch(key: String) {
        val now = System.currentTimeMillis()
        val old = _usage.value[key]
        val updated = AppUsage(count = (old?.count ?: 0) + 1, lastLaunch = now)
        _usage.value = _usage.value + (key to updated)
        prefs.edit().putString(key, "${updated.count}|${updated.lastLaunch}").apply()
    }

    private fun read(): Map<String, AppUsage> = prefs.all.mapNotNull { (key, value) ->
        val parts = (value as? String)?.split('|') ?: return@mapNotNull null
        val count = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val last = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        key to AppUsage(count, last)
    }.toMap()

    companion object {
        /** Frequently and recently used apps score highest. */
        fun score(usage: AppUsage, now: Long): Double {
            val days = (now - usage.lastLaunch).coerceAtLeast(0L) / 86_400_000.0
            return usage.count / (1.0 + days)
        }
    }
}
