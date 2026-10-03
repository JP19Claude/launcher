package dev.hearth.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Twenty-eight easter eggs hidden in Hearth, all with Clawd. Each one found is remembered. A new
 * find shows only a small hint; finding one again shows nothing; only the last one throws a
 * real party (confetti, Clawd dancing, a glass message).
 *
 *  1. Settings: tap "Hearth · gebaut mit Claude" at the bottom seven times
 *  2. Home: tap the clock five times quickly
 *  3. Clawd: ask for the meaning of life
 *  4. Search: look for "Regenbogen"
 *  5. Glass clock widget: tap it three times
 *  6. Clawd: tap him in the chat's header five times (he flips)
 *  7. Clawd: tell him you love him
 *  8. Clawd: ask him to dance
 *  9. Search: look for "Clawd"
 * 10. Hold Clawd in a search bar (home or app drawer)
 * 11. Tap Clawd on the home screen six times quickly (he gets dizzy)
 * 12. Drag Clawd on the home screen from one edge to the other
 * 13. Tap Clawd while he sleeps
 * 14. Ask Clawd who's the best
 * 15. Talk to Clawd after midnight
 * 16. Give Clawd the rainbow color
 * 17. Dress Clawd up: an outfit and a hat at once
 * 18. 100 points in Clawd Jump
 * 19. Drink eight glasses of water with Clawd in a day
 * 20. Pet Clawd five times in a row (Tamagotchi)
 * 21. Breathe a whole minute with Clawd
 * 22. Keep Clawd's mood diary seven days in a row
 * 23. Flip Clawd's coin until it lands on its edge (1 in 250)
 * 24. Win five times in a row against Clawd at Schnick-Schnack-Schnuck
 * 25. Look at Clawd's countdown on the big day itself
 * 26. Solve all of Clawd's riddles
 * 27. Tap "Version" in the software update screen five times quickly
 * 28. Tap the ClaudeOS version five times, then spin the Claude star ten whole turns
 */
object EasterEggs {

    const val TOTAL = 28

    private const val PREFS = "hearth_eggs"
    private const val KEY = "found"

    private val _found = MutableStateFlow<Set<String>>(emptySet())
    val found: StateFlow<Set<String>> = _found.asStateFlow()

    /** What a find shows: [big] only for the very last egg (confetti), else a small hint. */
    class Party(val text: String, val big: Boolean)

    private val _party = MutableSharedFlow<Party>(extraBufferCapacity = 2)

    /** New finds; the home screen shows them (small, unless it was the last one). */
    val party: SharedFlow<Party> = _party.asSharedFlow()

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
        // Found again: nothing pops up, it just happens.
        if (!first) return
        val n = _found.value.size.coerceAtMost(TOTAL)
        _party.tryEmit(
            if (n == TOTAL) {
                Party("Alle $TOTAL Easter Eggs gefunden! Du bist offiziell Hearth-Profi.", big = true)
            } else {
                Party("🥚 $n/$TOTAL", big = false)
            },
        )
    }
}
