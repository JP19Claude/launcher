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
        "Schon gewusst? In ${dev.hearth.launcher.data.Brand.name} sind 10 Easter Eggs versteckt.",
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
        // Remembered for Clawd's badges (and an easter egg).
        if (count >= GOAL) edit.putBoolean("goalReached", true)
        edit.apply()
        if (count == GOAL) EasterEggs.find(context, "water")
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
        if (score >= 100) EasterEggs.find(context, "jump100")
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
        val breaths = ClawdBreath.sessions(context)
        val diary = ClawdDiary.streak(context)
        val riddles = ClawdRiddles.solved(context)
        val rps = ClawdRps.best(context)
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
            Badge("🧘", "Tief durchgeatmet", "Eine Minute mit Clawd atmen", breaths >= 1),
            Badge("📔", "Tagebuch-Woche", "7 Tage in Folge im Clawd-Tagebuch", diary >= 7),
            Badge("🧠", "Rätselfuchs", "10 von Clawds Rätseln gelöst", riddles >= 10),
            Badge("✌️", "Glückspilz", "3× in Folge gegen Clawd gewonnen (Schnick-Schnack-Schnuck)", rps >= 3),
            Badge("🥚", "Eiersucher", "5 Easter Eggs gefunden", eggs >= 5),
            Badge("🌟", "Alle Eier!", "Alle ${EasterEggs.TOTAL} Easter Eggs gefunden", eggs >= EasterEggs.TOTAL),
        )
    }
}

/**
 * How Clawd reacts when you tap him: something different each time, dizzy when you tap too
 * fast, grumpy when you wake him up.
 */
object ClawdReactions {
    class Reaction(val mood: ClawdMood, val line: String)

    private val Taps = listOf(
        Reaction(ClawdMood.Wave, "Hey! 👋"),
        Reaction(ClawdMood.Jump, "Boing!"),
        Reaction(ClawdMood.Blush, "Oh, hallo du 🙈"),
        Reaction(ClawdMood.Love, "Hab dich lieb! 🧡"),
        Reaction(ClawdMood.Dance, "Party! ♪"),
        Reaction(ClawdMood.Surprised, "Huch!"),
        Reaction(ClawdMood.Flip, "Salto! 🤸"),
        Reaction(ClawdMood.Blush, "Hihi, das kitzelt!"),
        Reaction(ClawdMood.Wave, "Boop! 👉"),
        Reaction(ClawdMood.Jump, "Hopp hopp!"),
        Reaction(ClawdMood.Surprised, "Oh! Du schon wieder? 😄"),
        Reaction(ClawdMood.Dance, "Lass uns tanzen!"),
        Reaction(ClawdMood.Love, "Du bist toll!"),
        Reaction(ClawdMood.Thinking, "Hmm … ich überlege, was ich sagen wollte."),
    )

    private var last = -1

    /** A tap: never the same reaction twice in a row. */
    fun tap(): Reaction {
        var i = Taps.indices.random()
        if (i == last) i = (i + 1) % Taps.size
        last = i
        return Taps[i]
    }

    /** Tapped many times in a row, quickly. */
    val dizzy = Reaction(ClawdMood.Dizzy, "Mir wird ganz schwindelig … 😵")

    /** Woken up. */
    val wokenUp = Reaction(ClawdMood.Surprised, "Hmpf! Ich hab grad so schön geschlafen … 😴")
}

/** Counts quick taps in a row (within [windowMs] of each other). */
class ClawdTapCounter(private val windowMs: Long = 700) {
    private var lastAt = 0L
    var count = 0
        private set

    fun tap(): Int {
        val now = System.currentTimeMillis()
        count = if (now - lastAt <= windowMs) count + 1 else 1
        lastAt = now
        return count
    }
}

/** What Clawd says when you unlock the phone. */
object ClawdHello {
    private val Lines = listOf(
        "Hallo! 👋", "Da bist du ja!", "Willkommen zurück!", "Schön, dich zu sehen!",
        "Hey du!", "Na, was machen wir?", "Ich hab auf dich gewartet!", "Hallöchen!",
    )
    private val Late = listOf("Psst … ist schon spät 🌙", "Noch wach? Ich auch!", "Hallo, Nachteule! 🦉")
    private var last = -1

    /** The first unlock of the day gets the time-of-day greeting, the others a varied hello. */
    fun line(hour: Int, firstToday: Boolean): String {
        if (firstToday) return clawdGreeting(hour)
        val pool = if (hour >= 23 || hour < 5) Late else Lines
        var i = pool.indices.random()
        if (i == last && pool.size > 1) i = (i + 1) % pool.size
        last = i
        return pool[i]
    }
}

/** Clawd's mood diary: one face a day, the last week at a glance. Kept on this phone. */
object ClawdDiary {
    /** From sad to happy. */
    val Faces = listOf("😢", "😕", "😐", "🙂", "😄")

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_diary", Context.MODE_PRIVATE)

    /** The face noted on [day], or null. */
    fun of(context: Context, day: java.time.LocalDate = java.time.LocalDate.now()): Int? =
        prefs(context).getInt(day.toString(), -1).takeIf { it in Faces.indices }

    /** Today's face: the first tap notes 🙂, each further one the next face; returns it. */
    fun next(context: Context): Int {
        val now = of(context)
        val face = if (now == null) 3 else (now + 1) % Faces.size
        prefs(context).edit().putInt(java.time.LocalDate.now().toString(), face).apply()
        if (streak(context) >= 7) EasterEggs.find(context, "diary")
        return face
    }

    /** The last seven days, oldest first (null where nothing was noted). */
    fun week(context: Context): List<Int?> {
        val today = java.time.LocalDate.now()
        return (6 downTo 0).map { of(context, today.minusDays(it.toLong())) }
    }

    /** Days in a row with a face, up to today. */
    fun streak(context: Context): Int {
        var day = java.time.LocalDate.now()
        var n = 0
        while (n < 400 && of(context, day) != null) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    /** How Clawd takes it: comforting when it's a bad day, dancing on a great one. */
    fun moodFor(face: Int?): ClawdMood = when (face) {
        null -> ClawdMood.Thinking
        0, 1 -> ClawdMood.Love
        2 -> ClawdMood.Idle
        3 -> ClawdMood.Wave
        else -> ClawdMood.Dance
    }

    fun line(face: Int?): String = when (face) {
        null -> "Wie geht's dir heute? Tipp mich an."
        0 -> "Ich bin für dich da. 🧡 Morgen wird besser."
        1 -> "Nicht so dein Tag? Eine kleine Pause hilft."
        2 -> "Ganz okay ist auch okay."
        3 -> "Schön! Ich freu mich mit dir."
        else -> "Super Tag! Lass uns tanzen! ♪"
    }

    /** The week as a row of faces (· for days without one). */
    fun weekLine(context: Context): String = week(context).joinToString(" ") { f -> f?.let { Faces[it] } ?: "·" }
}

/** Heads or tails – and once in a long while, the coin lands on its edge (an easter egg). */
object ClawdCoin {
    const val HEADS = 0
    const val TAILS = 1
    const val EDGE = 2

    fun flip(context: Context?): Int {
        if ((1..250).random() == 1) {
            context?.let { EasterEggs.find(it, "coin") }
            return EDGE
        }
        return (HEADS..TAILS).random()
    }

    fun label(side: Int): String = when (side) {
        HEADS -> "Kopf"
        TAILS -> "Zahl"
        else -> "Auf der Kante!"
    }
}

/** Breathing with Clawd: four seconds in, six out, six rounds – one calm minute. */
object ClawdBreath {
    const val IN_MS = 4000L
    const val OUT_MS = 6000L
    const val ROUNDS = 6

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_breath", Context.MODE_PRIVATE)

    fun sessions(context: Context): Int = prefs(context).getInt("sessions", 0)

    /** A whole minute breathed through. */
    fun finished(context: Context) {
        prefs(context).edit().putInt("sessions", sessions(context) + 1).apply()
        EasterEggs.find(context, "breathe")
    }
}

/** Clawd counts down to the next big days of the year. */
object ClawdCountdown {
    class Day(val name: String, val emoji: String, val date: java.time.LocalDate)

    /** Easter Sunday (the Gregorian computus). */
    private fun easter(year: Int): java.time.LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return java.time.LocalDate.of(year, month, day)
    }

    /** The coming big days, soonest first (today's still counts). */
    fun upcoming(today: java.time.LocalDate = java.time.LocalDate.now()): List<Day> {
        fun next(make: (Int) -> java.time.LocalDate): java.time.LocalDate {
            val d = make(today.year)
            return if (d.isBefore(today)) make(today.year + 1) else d
        }
        return listOf(
            Day("Weihnachten", "🎄", next { java.time.LocalDate.of(it, 12, 24) }),
            Day("Silvester", "🎆", next { java.time.LocalDate.of(it, 12, 31) }),
            Day("Ostern", "🐣", next { easter(it) }),
            Day("Halloween", "🎃", next { java.time.LocalDate.of(it, 10, 31) }),
            Day("Valentinstag", "💝", next { java.time.LocalDate.of(it, 2, 14) }),
            Day("Sommeranfang", "☀️", next { java.time.LocalDate.of(it, 6, 21) }),
            Day("Nikolaus", "🎅", next { java.time.LocalDate.of(it, 12, 6) }),
        ).sortedBy { it.date }
    }

    fun daysUntil(day: Day, today: java.time.LocalDate = java.time.LocalDate.now()): Long =
        java.time.temporal.ChronoUnit.DAYS.between(today, day.date)

    fun line(day: Day, today: java.time.LocalDate = java.time.LocalDate.now()): String = when (val n = daysUntil(day, today)) {
        0L -> "Heute ist ${day.name}! ${day.emoji}"
        1L -> "Morgen ist ${day.name}!"
        else -> "Noch $n Tage bis ${day.name}"
    }

    /** "Wie lange noch bis Weihnachten?" – the day by its (folded) name. */
    fun byName(folded: String): Day? = upcoming().firstOrNull { folded.contains(ClaudeTools.fold(it.name)) }
}

/** Clawd's riddles: the question first, a tap shows the answer. */
object ClawdRiddles {
    class Riddle(val question: String, val answer: String)

    val All = listOf(
        Riddle("Was hat Zähne, kann aber nicht beißen?", "Ein Kamm."),
        Riddle("Was wird nasser, je mehr es trocknet?", "Ein Handtuch."),
        Riddle("Was hat einen Hals, aber keinen Kopf?", "Eine Flasche."),
        Riddle("Je mehr man wegnimmt, desto größer wird es. Was ist es?", "Ein Loch."),
        Riddle("Was hat Tasten, aber kein Schloss?", "Eine Tastatur – oder ein Klavier."),
        Riddle("Was gehört dir, wird aber von anderen viel öfter benutzt?", "Dein Name."),
        Riddle("Was kann man fangen, aber nicht werfen?", "Eine Erkältung."),
        Riddle("Was hat ein Auge, kann aber nicht sehen?", "Eine Nadel."),
        Riddle("Was steht mitten in Paris?", "Das „r“."),
        Riddle("Was ist leichter als eine Feder, aber niemand kann es lange halten?", "Den Atem."),
        Riddle("Was geht durchs Glas, ohne es zu zerbrechen?", "Das Licht."),
        Riddle("Was hat Hände, kann aber nicht klatschen?", "Eine Uhr."),
        Riddle("Was läuft, hat aber keine Beine?", "Wasser – oder die Zeit."),
        Riddle("Welches Wesen hat 104 Pixel und wohnt in deinem Handy?", "Ich! Clawd. 👋"),
        Riddle("Was hat Blätter, ist aber kein Baum?", "Ein Buch."),
    )

    fun of(n: Int): Riddle = All[Math.floorMod(n, All.size)]

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_riddles", Context.MODE_PRIVATE)

    /** How many different riddles were solved (answers shown). */
    fun solved(context: Context): Int = prefs(context).getStringSet("solved", emptySet()).orEmpty().size

    /** The answer to riddle [n] was shown; all of them makes an easter egg. */
    fun solve(context: Context, n: Int) {
        val now = prefs(context).getStringSet("solved", emptySet()).orEmpty() + Math.floorMod(n, All.size).toString()
        prefs(context).edit().putStringSet("solved", now).apply()
        if (now.size >= All.size) EasterEggs.find(context, "riddles")
    }
}

/** Schnick, Schnack, Schnuck with Clawd: rock, paper, scissors. */
object ClawdRps {
    val Hands = listOf("✊", "✋", "✌️")
    val Names = listOf("Stein", "Papier", "Schere")

    /** [result]: 1 you won, 0 a draw, -1 Clawd won. */
    class Round(val you: Int, val clawd: Int, val result: Int, val wins: Int, val losses: Int, val streak: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("clawd_rps", Context.MODE_PRIVATE)

    /** Who wins with these two hands: 1 the first, 0 nobody, -1 the second. */
    fun judge(a: Int, b: Int): Int = when (Math.floorMod(a - b, 3)) {
        0 -> 0
        1 -> 1
        else -> -1
    }

    fun play(context: Context?, you: Int): Round {
        val clawd = (0..2).random()
        val result = judge(you, clawd)
        if (context == null) return Round(you, clawd, result, 0, 0, 0)
        val p = prefs(context)
        val wins = p.getInt("wins", 0) + if (result == 1) 1 else 0
        val losses = p.getInt("losses", 0) + if (result == -1) 1 else 0
        // A draw doesn't break a winning run.
        val streak = when (result) {
            1 -> p.getInt("streak", 0) + 1
            0 -> p.getInt("streak", 0)
            else -> 0
        }
        val best = maxOf(p.getInt("best", 0), streak)
        p.edit().putInt("wins", wins).putInt("losses", losses).putInt("streak", streak).putInt("best", best)
            .putInt("you", you).putInt("clawd", clawd).putInt("result", result).apply()
        if (streak >= 5) EasterEggs.find(context, "rps")
        return Round(you, clawd, result, wins, losses, streak)
    }

    /** The last round played (or null). */
    fun last(context: Context): Round? {
        val p = prefs(context)
        if (!p.contains("result")) return null
        return Round(p.getInt("you", 0), p.getInt("clawd", 0), p.getInt("result", 0), p.getInt("wins", 0), p.getInt("losses", 0), p.getInt("streak", 0))
    }

    fun best(context: Context): Int = prefs(context).getInt("best", 0)

    fun line(r: Round): String = when (r.result) {
        1 -> if (r.streak >= 3) "Du gewinnst – schon ${r.streak}× in Folge! 🔥" else "Du gewinnst! 🎉"
        0 -> "Unentschieden!"
        else -> "Clawd gewinnt! 😄"
    }

    fun moodFor(r: Round?): ClawdMood = when (r?.result) {
        null -> ClawdMood.Wave
        1 -> ClawdMood.Dizzy
        0 -> ClawdMood.Surprised
        else -> ClawdMood.Dance
    }
}
