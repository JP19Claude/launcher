package dev.hearth.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sixty-nine easter eggs hidden in Hearth, the ones from 19.6 on not all with Clawd. Each one found is remembered. A new
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
 * 29. Open the list of secret codes (*#0000# in the finder)
 * 30. Unlock OMEGA Labs (seven taps on the build number)
 * 31. Look for "Omega" in the finder and send all of Greek into orbit around the Ω
 * 32. Tap the Ω crystal as often as OMEGA UI's number: the Clawd Illumination opens
 *
 * ZENITH 19.6 added twenty-two more (see the egg map): the codes *#8376# *#6366# *#7625# *#3729#
 * *#6725# *#9277# *#2733#, the words hearth, konami, fluid, zenith, sudo su, hello world and π,
 * and five small ones – the Monitor's title, the Schutzschild's gauge and perfect score, ten rolls
 * of the die in the Core Enforcer Studio, and the clock at 13:37. ZENITH 20 added three: the moon in
 * the Z-Nacht, the weather picture in the Zentrale and twenty taps on its clock; the jubilee hotfix
 * twelve more (the clock at 20:20, the word "Jubiläum", all five auroras, the Z-Nacht at three in the
 * morning or for twenty minutes, twenty percent battery, the Zentrale at dawn, on a 20th, at 20 °C,
 * the greeting's five taps) and two that come by themselves (twenty eggs, forty in gold). Every egg
 * also has a jubilee edition: the first time it happens in ZENITH 20 it sets off a golden firework.
 */
object EasterEggs {

    const val TOTAL = 69

    private const val PREFS = "hearth_eggs"
    private const val KEY = "found"
    private const val JUBILEE_KEY = "jubilee"

    private val _found = MutableStateFlow<Set<String>>(emptySet())
    val found: StateFlow<Set<String>> = _found.asStateFlow()

    /**
     * ZENITH 20, the jubilee edition: every egg, found before or not, shows a golden twist the first
     * time it happens in ZENITH 20 – a firework and a mark on the egg map.
     */
    private val _jubilee = MutableStateFlow<Set<String>>(emptySet())
    val jubilee: StateFlow<Set<String>> = _jubilee.asStateFlow()

    /**
     * What a find shows: [big] only for the very last egg (confetti), else a small hint. [egg] is
     * the egg's id, [repeat] an old egg shown in its jubilee edition for the first time.
     */
    class Party(val text: String, val big: Boolean, val egg: String = "", val repeat: Boolean = false)

    private val _party = MutableSharedFlow<Party>(extraBufferCapacity = 2)

    /** New finds; the home screen shows them (small, unless it was the last one). */
    val party: SharedFlow<Party> = _party.asSharedFlow()

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _found.value = runCatching { prefs.getStringSet(KEY, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())
        _jubilee.value = runCatching { prefs.getStringSet(JUBILEE_KEY, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())
    }

    /** Read again from storage (after another app's eggs were joined in). */
    fun reload(context: Context) {
        loaded = false
        init(context)
    }

    fun find(context: Context, egg: String) {
        init(context)
        val first = egg !in _found.value
        val jubileeFirst = egg !in _jubilee.value
        // Found again, and its jubilee edition seen already: nothing pops up, it just happens.
        if (!first && !jubileeFirst) return
        if (first) _found.value = _found.value + egg
        if (jubileeFirst) _jubilee.value = _jubilee.value + egg
        // Two eggs come by themselves: twenty finds, and forty jubilee editions.
        var club: String? = null
        if (_found.value.size >= 20 && "club20" !in _found.value) {
            _found.value = _found.value + "club20"
            _jubilee.value = _jubilee.value + "club20"
            club = "🏅 Jubiläums-Club: zwanzig Eggs gefunden!"
        }
        if (_jubilee.value.size >= 40 && "jubilee40" !in _found.value) {
            _found.value = _found.value + "jubilee40"
            _jubilee.value = _jubilee.value + "jubilee40"
            club = "🏅 Vierzig Eggs in der Jubiläums-Edition!"
        }
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY, _found.value)
                .putStringSet(JUBILEE_KEY, _jubilee.value)
                .apply()
        }
        val n = _found.value.size.coerceAtMost(TOTAL)
        _party.tryEmit(
            when {
                club != null -> Party(club, big = false, egg = egg)
                first && n == TOTAL -> Party("Alle $TOTAL Easter Eggs gefunden! Du bist offiziell ${dev.hearth.launcher.data.Brand.name}-Profi.", big = true, egg = egg)
                first -> Party("🥚 $n/$TOTAL", big = false, egg = egg)
                else -> Party("Jubiläums-Edition ${_jubilee.value.size.coerceAtMost(TOTAL)}/$TOTAL", big = false, egg = egg, repeat = true)
            },
        )
    }
}
