package dev.hearth.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Five easter eggs hidden in Hearth. Each one found is remembered; finding one throws a
 * little party (confetti and a glass message) on the home screen.
 *
 *  1. Settings: tap "Hearth · gebaut mit Claude" at the bottom seven times
 *  2. Home: tap the clock five times quickly
 *  3. Claude: ask for the meaning of life
 *  4. Search: look for "Regenbogen"
 *  5. Glass clock widget: tap it three times
 */
object EasterEggs {

    const val TOTAL = 5

    private const val PREFS = "hearth_eggs"
    private const val KEY = "found"

    private val _found = MutableStateFlow<Set<String>>(emptySet())
    val found: StateFlow<Set<String>> = _found.asStateFlow()

    private val _party = MutableSharedFlow<String>(extraBufferCapacity = 2)

    /** A message for each find; the home screen celebrates it. */
    val party: SharedFlow<String> = _party.asSharedFlow()

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _found.value = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()
        }.getOrDefault(emptySet())
    }

    fun find(context: Context, egg: String) {
        init(context)
        val first = egg !in _found.value
        if (first) {
            _found.value = _found.value + egg
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, _found.value).apply()
            }
        }
        val n = _found.value.size.coerceAtMost(TOTAL)
        _party.tryEmit(
            when {
                first && n == TOTAL -> "Alle $TOTAL Easter Eggs gefunden! Du bist offiziell Hearth-Profi."
                first -> "Easter Egg $n/$TOTAL gefunden!"
                else -> "Kennst du schon ($n/$TOTAL) – macht trotzdem Spaß."
            },
        )
    }
}
