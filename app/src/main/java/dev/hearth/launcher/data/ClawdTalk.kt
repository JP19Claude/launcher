package dev.hearth.launcher.data

/** How Clawd, Claude's little pixel creature, is feeling right now (drives his animation). */
enum class ClawdMood { Idle, Thinking, Dance, Flip, Love, Wave, Sleep }

/**
 * Clawd's own small talk: greetings, who he is, jokes – and a few easter eggs. Answered
 * right away on the phone (free, offline); everything else goes to the AI as usual.
 */
object ClawdTalk {

    class Answer(val text: String, val egg: String? = null, val mood: ClawdMood? = null, val game: Boolean = false)

    private val Facts = listOf(
        "Ich bestehe aus genau 104 Pixeln – na gut, plus Hut.",
        "Oktopusse haben drei Herzen. Ich hab eins, aber ein großes. 🧡",
        "Honig wird nie schlecht – in Pyramiden fand man 3000 Jahre alten.",
        "Ein Tag auf der Venus dauert länger als ein Jahr auf der Venus.",
        "Bananen sind Beeren, Erdbeeren aber nicht.",
        "Der Eiffelturm ist im Sommer bis zu 15 cm höher – Wärme dehnt Metall.",
        "Kühe haben beste Freundinnen und sind gestresst, wenn man sie trennt.",
        "Das erste Computer-„Bug“ war eine echte Motte in einem Relais (1947).",
    )
    private var fact = 0

    private val Jokes = listOf(
        "Warum können Pixel so gut tanzen? Weil sie immer im Raster bleiben.",
        "Was macht ein Launcher in der Pause? Er startet durch.",
        "Wie nennt man ein kleines Wesen aus Pixeln, das Witze erzählt? Clawd. Hallo!",
        "Warum war das Handy so müde? Es hatte zu viele Tabs offen.",
        "Was sagt der Akku zum Ladekabel? Ohne dich bin ich nur halb so voll.",
        "Warum ist Glimmer nie einsam? Er hat immer die Kamera in der Mitte.",
        "Warum hat Clawd vier Beine? Damit er auch bei Updates auf dem Boden bleibt.",
        "Was ist Clawds Lieblingsessen? Pixel-Pizza – in 8 Bit geschnitten.",
        "Wie kommt Clawd auf den Startbildschirm? Ganz einfach: mit einem Widget-Sprung.",
        "Warum zählt Clawd nie Schafe? Er zählt Pixel. Bei 104 schläft er ein.",
        "Was sagt Clawd, wenn das WLAN weg ist? Kein Problem, ich bin offline auch lustig.",
    )

    /** How many jokes Clawd knows; [joke] picks one (any number wraps around). */
    val jokeCount: Int get() = Jokes.size

    fun joke(n: Int): String = Jokes[Math.floorMod(n, Jokes.size)]

    private var joke = 0

    fun reply(question: String): Answer? {
        val t = ClaudeTools.fold(question).removeSuffix(" clawd").removePrefix("clawd ").trim()
        fun m(pattern: String) = Regex(pattern).matches(t)
        return when {
            t.contains("sinn des lebens") -> Answer("42. Die passende Frage dazu sucht Deep Thought noch.", egg = "42", mood = ClawdMood.Wave)
            m("(ich )?(hab|habe) dich (so )?lieb|ich liebe dich|love you|i love you") ->
                Answer("Aww! Ich dich auch – mit allen 104 Pixeln.", egg = "love", mood = ClawdMood.Love)
            m("(tanz|tanze|tanz mal|tanz fur mich|dance|lass uns tanzen)( bitte)?") ->
                Answer("Achtung, jetzt wird's pixelig! ♪", egg = "dance", mood = ClawdMood.Dance)
            m("hallo|hi|hey|moin|servus|guten (morgen|tag|abend)|na") ->
                Answer("Hey! Ich bin Clawd. Sag mir, was ich auf deinem Handy erledigen soll.", mood = ClawdMood.Wave)
            m("wer bist du|wie heisst du|was bist du|bist du clawd") ->
                Answer("Ich bin Clawd, Claudes kleines Pixel-Wesen. Ich wohne jetzt in deinem Launcher und helfe dir, wo ich kann.", mood = ClawdMood.Wave)
            m("wie gehts|wie geht es dir|wie gehts dir|alles gut") ->
                Answer("Mir geht's pixelig gut, danke! Und dir?", mood = ClawdMood.Wave)
            m("(erzahl|erzahle|sag)( mir)? (einen|nen) witz|witz|noch ein witz|noch einen") -> {
                val text = Jokes[joke % Jokes.size]
                joke++
                Answer(text, mood = ClawdMood.Dance)
            }
            m("wer hat dich (gebaut|gemacht|erfunden|programmiert)") ->
                Answer("Erfunden hat mich Anthropic – und du hast mich in deinen Launcher geholt.", mood = ClawdMood.Wave)
            m("(danke|dankeschon|vielen dank|merci)") -> Answer("Gern geschehen! 🧡", mood = ClawdMood.Love)
            m("(wie spat ist es|wie viel uhr ist es|uhrzeit|wie spat)") -> {
                val now = java.time.LocalTime.now()
                Answer("Es ist %d:%02d Uhr.".format(now.hour, now.minute), mood = ClawdMood.Wave)
            }
            m("(welcher tag ist heute|was ist heute fur ein tag|datum|welches datum ist heute)") -> {
                val today = java.time.LocalDate.now()
                Answer(
                    "Heute ist " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", java.util.Locale.GERMAN)) + ".",
                    mood = ClawdMood.Wave,
                )
            }
            m("(was kannst du|was kannst du alles|hilfe|was machst du)") -> Answer(
                "Ich erledige Sachen auf deinem Handy (Wecker, Apps, Einstellungen …), erzähle Witze und Fakten, " +
                    "bin dein Tamagotchi, lebe in Glimmer und in vielen Widgets – und mit „Lass uns spielen“ startet Clawd Jump!",
                mood = ClawdMood.Wave,
            )
            m("(gute nacht|nacht|ich geh schlafen|schlaf gut)") -> Answer("Schlaf gut! 🌙 Ich pass auf dein Handy auf.", mood = ClawdMood.Sleep)
            m("(schlaf|schlaf mal|geh schlafen)") -> Answer("Zzz …", mood = ClawdMood.Sleep)
            m("(mir ist langweilig|langweilig|lass uns spielen|spielen|spiel|spiel was|clawd jump)") ->
                Answer("Los geht's: Clawd Jump! Tippen zum Springen. 🐞", mood = ClawdMood.Dance, game = true)
            m("(erzahl (mir )?was|erzahl (mir )?etwas|fakt|fun fact|wusstest du|noch ein fakt)") -> {
                val text = Facts[fact % Facts.size]
                fact++
                Answer("Wusstest du? $text", mood = ClawdMood.Thinking)
            }
            m("(wie alt bist du)") -> Answer("In Pixel-Jahren? Etwa 104. In echt: noch ganz frisch.", mood = ClawdMood.Wave)
            m("(magst du mich|hast du mich lieb)") -> Answer("Na klar! Du bist mein Lieblingsmensch. 🧡", egg = "love", mood = ClawdMood.Love)
            m("(mach einen salto|salto|flip|mach nen salto)") -> Answer("Hui! 🤸", mood = ClawdMood.Flip)
            m("(wink|winke|wink mal|winke mal)") -> Answer("👋 Hallihallo!", mood = ClawdMood.Wave)
            else -> null
        }
    }
}
