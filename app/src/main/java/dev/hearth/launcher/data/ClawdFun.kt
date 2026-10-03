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

    class State(val food: Int, val joy: Int, val level: Int = 1) {
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
        State(decayed(context, "food", FOOD_PER_HOUR), decayed(context, "joy", JOY_PER_HOUR), level(context))

    /** Every bit of care counts: a new level every twelve. */
    private fun level(context: Context): Int = 1 + care(context) / 12

    /** How often he's been fed, played with or petted, ever. */
    fun care(context: Context): Int = prefs(context).getInt("care", 0)

    private fun cared(context: Context) {
        val p = prefs(context)
        p.edit().putInt("care", p.getInt("care", 0) + 1).apply()
    }

    private fun set(context: Context, food: Int, joy: Int) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putInt("food", food.coerceIn(0, 100)).putLong("foodAt", now)
            .putInt("joy", joy.coerceIn(0, 100)).putLong("joyAt", now)
            .apply()
    }

    fun feed(context: Context): State {
        val s = state(context)
        cared(context)
        set(context, s.food + 35, s.joy + 5)
        return state(context)
    }

    fun play(context: Context): State {
        val s = state(context)
        cared(context)
        set(context, s.food - 5, s.joy + 30)
        return state(context)
    }

    fun pet(context: Context): State {
        val s = state(context)
        cared(context)
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

/** Drinking water with Clawd: glasses today, towards a goal; starts fresh each day. */
object ClawdWater {
    const val GOAL = 8

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_water", Context.MODE_PRIVATE)

    private fun today(): String = java.time.LocalDate.now().toString()

    fun glasses(context: Context): Int {
        val p = prefs(context)
        return if (p.getString("day", null) == today()) p.getInt("count", 0) else 0
    }

    /** One more glass (or one less with [delta] = -1); returns today's count. */
    fun add(context: Context, delta: Int = 1): Int {
        val count = (glasses(context) + delta).coerceIn(0, 30)
        val edit = prefs(context).edit().putString("day", today()).putInt("count", count)
        // Remembered for Clawd's badges.
        if (count >= GOAL) edit.putBoolean("goalReached", true)
        edit.apply()
        return count
    }

    fun goalEverReached(context: Context): Boolean = prefs(context).getBoolean("goalReached", false)

    fun line(count: Int): String = when {
        count == 0 -> "Noch kein Glas heute – los geht's! 💧"
        count < GOAL / 2 -> "Guter Anfang, weiter so!"
        count < GOAL -> "Fast geschafft, noch ${GOAL - count}!"
        count == GOAL -> "Ziel erreicht! 🎉"
        else -> "Wasser-Profi! 🌊"
    }
}

/** Clawd rolls a die. */
object ClawdDice {
    fun roll(): Int = (1..6).random()
}

/** A kind word for each day. */
object ClawdMotivation {
    private val Lines = listOf(
        "Kleine Schritte sind auch Schritte.",
        "Du musst nicht perfekt sein – nur dabei bleiben.",
        "Heute ist ein guter Tag für etwas Neues.",
        "Pausen gehören zum Weg dazu.",
        "Du hast schon so viel geschafft. Ich bin stolz auf dich!",
        "Eins nach dem anderen. Pixel für Pixel.",
        "Fehler sind nur Updates in Arbeit.",
        "Atme tief durch – du kriegst das hin.",
        "Sei heute nett zu dir selbst.",
        "Mut ist, es trotzdem zu versuchen.",
        "Lächeln steht dir! 🙂",
        "Was heute schwer ist, ist morgen Erfahrung.",
        "Glaub an dich – ich tu's auch.",
        "Ein bisschen Fortschritt ist besser als gar keiner.",
    )

    fun of(day: Int, extra: Int = 0): String = Lines[Math.floorMod(day + extra, Lines.size)]
}

/** How long until the weekend, the way Clawd says it. */
fun clawdWeekend(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Pair<String, String> {
    val day = now.dayOfWeek.value // 1 = Monday … 7 = Sunday
    return when (day) {
        6, 7 -> "🎉" to "Wochenende! Genieß es."
        5 -> if (now.hour >= 17) "🎉" to "Feierabend – Wochenende!" else "1" to "Morgen ist Wochenende!"
        else -> "${6 - day}" to "Tage bis zum Wochenende"
    }
}

/** Clawd Jump: the best score on this phone. */
object ClawdGameScore {
    private fun prefs(context: Context) = context.getSharedPreferences("clawd_game", Context.MODE_PRIVATE)

    fun best(context: Context): Int = prefs(context).getInt("best", 0)

    /** Saves [score] if it beats the record; true when it did. */
    fun submit(context: Context, score: Int): Boolean {
        if (score <= best(context)) return false
        prefs(context).edit().putInt("best", score).apply()
        // The Clawd app's game widget shows the new record right away.
        runCatching {
            context.sendBroadcast(android.content.Intent("dev.hearth.clawd.REFRESH").setPackage(context.packageName))
        }
        return true
    }

    /** Opens the game (from widgets, the chat, the settings). */
    fun open(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent()
                    .setClassName(context.packageName, "dev.hearth.launcher.ClawdGameActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Clawd's badges: little goals across everything he does, collected over time. */
object ClawdBadges {

    class Badge(val emoji: String, val title: String, val hint: String, val earned: Boolean)

    fun all(context: Context, settings: LauncherSettings): List<Badge> {
        EasterEggs.init(context)
        val care = ClawdPet.care(context)
        val level = ClawdPet.state(context).level
        val best = ClawdGameScore.best(context)
        val focus = ClawdFocus.done(context)
        val eggs = EasterEggs.found.value.size
        return listOf(
            Badge("🍕", "Erste Mahlzeit", "Füttere Clawd im Tamagotchi", care >= 1),
            Badge("🧡", "Bester Freund", "Tamagotchi Level 3", level >= 3),
            Badge("👑", "Pixel-Profi", "Tamagotchi Level 10", level >= 10),
            Badge("🦘", "Hüpfer", "25 Punkte in Clawd Jump", best >= 25),
            Badge("🐞", "Bug-Jäger", "100 Punkte in Clawd Jump", best >= 100),
            Badge("🏆", "Legende", "300 Punkte in Clawd Jump", best >= 300),
            Badge("💧", "Gut gewässert", "Wasserziel an einem Tag erreicht", ClawdWater.goalEverReached(context)),
            Badge("🎯", "Fokussiert", "Eine Fokus-Runde geschafft", focus >= 1),
            Badge("📚", "Fleißig", "10 Fokus-Runden geschafft", focus >= 10),
            Badge("🎩", "Modebewusst", "Clawd ein Outfit anziehen", settings.clawdOutfit != ClawdOutfit.None),
            Badge("🥚", "Eiersucher", "5 Easter Eggs gefunden", eggs >= 5),
            Badge("🌟", "Alle Eier!", "Alle ${EasterEggs.TOTAL} Easter Eggs gefunden", eggs >= EasterEggs.TOTAL),
        )
    }
}
