package dev.hearth.launcher.data

import android.content.Context

/** Clawd's greeting for the time of day. */
fun clawdGreeting(hour: Int): String = when (hour) {
    in 5..10 -> "Guten Morgen!"
    in 11..13 -> "Mahlzeit!"
    in 14..17 -> "Schönen Nachmittag!"
    in 18..21 -> "Guten Abend!"
    else -> "Gute Nacht … zzz"
}

/** Clawd sleeps late at night. */
fun clawdAsleep(hour: Int): Boolean = hour >= 23 || hour < 6

/**
 * Clawd as a pet, like a Tamagotchi: he gets hungry and bored over time; feeding, playing
 * and petting him makes him happy. Kept on this phone, per app.
 */
object ClawdPet {

    class State(val food: Int, val joy: Int) {
        val mood: ClawdMood
            get() = when {
                food < 25 -> ClawdMood.Sleep
                joy >= 80 && food >= 60 -> ClawdMood.Love
                joy >= 50 -> ClawdMood.Wave
                else -> ClawdMood.Idle
            }

        val status: String
            get() = when {
                food < 25 -> "Hunger … 🍕?"
                joy < 30 -> "Mir ist langweilig"
                joy >= 80 && food >= 60 -> "Rundum glücklich!"
                food < 50 -> "Ein Snack wär nett"
                else -> "Alles gut!"
            }
    }

    private const val FOOD_PER_HOUR = 6f
    private const val JOY_PER_HOUR = 4f

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_pet", Context.MODE_PRIVATE)

    private fun decayed(context: Context, key: String, perHour: Float): Int {
        val p = prefs(context)
        val value = p.getInt(key, 80)
        val since = p.getLong("${key}At", System.currentTimeMillis())
        val hours = (System.currentTimeMillis() - since).coerceAtLeast(0) / 3_600_000f
        return (value - hours * perHour).toInt().coerceIn(0, 100)
    }

    fun state(context: Context): State =
        State(decayed(context, "food", FOOD_PER_HOUR), decayed(context, "joy", JOY_PER_HOUR))

    private fun set(context: Context, food: Int, joy: Int) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putInt("food", food.coerceIn(0, 100)).putLong("foodAt", now)
            .putInt("joy", joy.coerceIn(0, 100)).putLong("joyAt", now)
            .apply()
    }

    fun feed(context: Context): State {
        val s = state(context)
        set(context, s.food + 35, s.joy + 5)
        return state(context)
    }

    fun play(context: Context): State {
        val s = state(context)
        set(context, s.food - 5, s.joy + 30)
        return state(context)
    }

    fun pet(context: Context): State {
        val s = state(context)
        set(context, s.food, s.joy + 12)
        return state(context)
    }
}

/** Clawd as a magic 8-ball: ask a yes/no question, tap him, he answers. */
object ClawdOracle {
    private val Answers = listOf(
        "Ja, auf jeden Fall!",
        "Meine Pixel sagen: ja.",
        "Sieht gut aus!",
        "Ohne Zweifel.",
        "Frag mich später nochmal.",
        "Hmm … schwer zu sagen.",
        "Konzentrier dich und frag nochmal.",
        "Eher nicht.",
        "Meine Antwort ist nein.",
        "Darauf würde ich nicht wetten.",
        "Absolut!",
        "Vielleicht – wenn du ganz fest dran glaubst.",
        "Die Zeichen stehen auf ja.",
        "Lieber nicht heute.",
        "Ja – aber erst nach einem Snack.",
        "42. Äh, ich meine: ja.",
    )

    fun ask(): String = Answers.random()
}

/** A focus timer with Clawd: 25 minutes of work, he works along. */
object ClawdFocus {
    const val MINUTES = 25

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_focus", Context.MODE_PRIVATE)

    /** When the running session ends (ms), 0 when none runs. */
    fun endsAt(context: Context): Long = prefs(context).getLong("end", 0L)

    fun start(context: Context) {
        prefs(context).edit().putLong("end", System.currentTimeMillis() + MINUTES * 60_000L).apply()
    }

    fun stop(context: Context) {
        prefs(context).edit().putLong("end", 0L).apply()
    }

    /** Finished sessions, for a little pride. */
    fun done(context: Context): Int = prefs(context).getInt("done", 0)

    fun finished(context: Context) {
        val p = prefs(context)
        p.edit().putLong("end", 0L).putInt("done", p.getInt("done", 0) + 1).apply()
    }
}

/** What Clawd says when you tap him: greetings, tips, jokes – a little different each time. */
object ClawdLines {
    private val Tips = listOf(
        "Halt mich gedrückt, dann reden wir.",
        "Ich wohne auch in jeder Suchleiste – tipp mich dort an.",
        "Schon gewusst? In Hearth sind 10 Easter Eggs versteckt.",
        "Trink mal ein Glas Wasser 💧",
        "Kurz strecken? Ich mach mit! 🙆",
        "Ich kann Wecker stellen, Apps öffnen und mehr – frag einfach.",
        "In den Einstellungen kannst du mir Hüte aufsetzen. 👑",
        "Es gibt viele Widgets mit mir – halt den Startbildschirm gedrückt.",
        "Du machst das super heute!",
        "Pixel-High-Five! ✋",
    )

    private var n = 0

    fun next(hour: Int, battery: Int = -1, charging: Boolean = false): String {
        n++
        return when {
            n == 1 -> clawdGreeting(hour)
            charging && n % 5 == 0 -> "Mmh, Strom! Ich tanke mit. ⚡"
            battery in 0..15 && n % 3 == 0 -> "Der Akku ist fast leer – ich werd schon müde …"
            n % 4 == 0 -> ClawdTalk.joke(n / 4)
            else -> Tips[(n * 7) % Tips.size]
        }
    }
}
