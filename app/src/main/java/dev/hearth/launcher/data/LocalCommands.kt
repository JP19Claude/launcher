package dev.hearth.launcher.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple commands Hearth understands on its own: instantly, offline and without an API key
 * ("Taschenlampe an", "Wecker 6:30", "Timer 10 Minuten", "Öffne Spotify", "Wie viel Akku?").
 * Only short, unambiguous sentences match; anything else goes to Claude. With a key, only
 * commands that can't be misread are taken here ([onlySafe]).
 */
object LocalCommands {

    sealed interface Step {
        class Tool(val name: String, val input: JSONObject) : Step
        class Say(val text: String) : Step
    }

    private val Numbers = mapOf(
        "null" to 0, "ein" to 1, "eine" to 1, "einen" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4,
        "fuenf" to 5, "funf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwoelf" to 12, "zwolf" to 12, "fuenfzehn" to 15, "funfzehn" to 15, "zwanzig" to 20,
        "dreissig" to 30, "vierzig" to 40, "fuenfundvierzig" to 45, "funfundvierzig" to 45, "fuenfzig" to 50,
        "funfzig" to 50, "sechzig" to 60, "neunzig" to 90, "hundert" to 100,
    )

    /** "Taschenlampe an und Wecker 7 Uhr": each part on its own; all must be understood. */
    fun parse(text: String, tools: ClaudeTools, onlySafe: Boolean): List<Step>? {
        val parts = text.split(Regex("\\s+(und|and|dann|danach)\\s+|[,;]\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 4) return null
        val steps = mutableListOf<Step>()
        for (part in parts) steps += parseOne(part, tools, onlySafe) ?: return null
        return steps
    }

    private fun parseOne(text: String, tools: ClaudeTools, onlySafe: Boolean): List<Step>? {
        // Umlauts folded (ö → o), punctuation gone; then number words as digits.
        val raw = ClaudeTools.fold(text)
        val t = raw.split(' ').joinToString(" ") { w -> Numbers[w]?.toString() ?: w }
        if (t.isEmpty()) return null
        val words = t.split(' ')
        // Long sentences are Claude's: they usually mean more than one simple thing.
        if (words.size > 8) return null

        fun tool(name: String, vararg pairs: Pair<String, Any>) =
            listOf(Step.Tool(name, JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }))

        // From the text before "ein" became "1".
        val on = Regex("\\b(an|ein|einschalten|anschalten|anmachen|aktivieren|on)\\b").containsMatchIn(raw)
        val off = Regex("\\b(aus|ausschalten|ausmachen|deaktivieren|off)\\b").containsMatchIn(raw)
        val number = Regex("\\b(\\d{1,3})\\b").find(t)?.groupValues?.get(1)?.toIntOrNull()

        // Flashlight
        if (Regex("\\b(taschenlampe|lampe|flashlight|torch|blitzlicht)\\b").containsMatchIn(t) && (on || off)) {
            return tool("flashlight", "on" to !off)
        }

        // Do not disturb
        if (t.contains("nicht storen") || t.contains("do not disturb")) {
            if (on || off) return tool("do_not_disturb", "on" to !off)
        }

        // Ringer
        if (Regex("^(handy |telefon )?(auf )?(lautlos|stumm)( schalten| stellen| machen)?$").matches(t)) return tool("sound_mode", "mode" to "silent")
        if (Regex("^(handy |telefon )?(auf )?(vibration|vibrieren|vibrationsmodus)( an| schalten| stellen)?$").matches(t)) return tool("sound_mode", "mode" to "vibrate")
        if (Regex("^(ton an|klingelton an|ton einschalten|lautlos aus|stumm aus)$").matches(raw)) return tool("sound_mode", "mode" to "normal")

        // Brightness
        if (t.contains("helligkeit") && number != null) return tool("brightness", "percent" to number.coerceIn(0, 100))
        if (Regex("^(bildschirm |display )?(heller|dunkler)( machen| bitte)?$").matches(t) ||
            Regex("^mach (den bildschirm |das display )?(heller|dunkler)$").matches(t)
        ) {
            val now = runCatching { (tools.currentBrightness() * 100).toInt() }.getOrDefault(50)
            val next = if (t.contains("heller")) now + 25 else now - 25
            return tool("brightness", "percent" to next.coerceIn(3, 100))
        }
        if (t.contains("auto helligkeit") || t.contains("automatische helligkeit")) {
            if (on || off) return tool("brightness", "auto" to !off)
        }

        // Volume
        if (t.contains("lautstarke") && number != null) {
            val stream = when {
                t.contains("klingel") -> "ring"
                t.contains("wecker") -> "alarm"
                else -> "media"
            }
            return tool("volume", "percent" to number.coerceIn(0, 100), "stream" to stream)
        }
        if (Regex("^(mach |musik |ton )?(lauter|leiser)( machen| bitte)?$").matches(t)) {
            val now = runCatching { (tools.currentVolume() * 100).toInt() }.getOrDefault(50)
            val next = if (t.contains("lauter")) now + 15 else now - 15
            return tool("volume", "percent" to next.coerceIn(0, 100), "stream" to "media")
        }

        // Alarm: "Wecker 6:30", "weck mich um 7 Uhr", "Wecker auf 6 Uhr 45"
        Regex("^(stell |stelle )?(einen |den )?(wecker|alarm|weck mich)\\b.*?\\b(\\d{1,2})(?: uhr)?(?:[ :.](\\d{1,2}))?(?: uhr)?(?: morgens| fruh| abends| nachmittags| am morgen| am abend| am nachmittag)?$").find(t)?.let { m ->
            // "Viertel vor" and the like: better left to Claude than guessed.
            if (t.contains("viertel") || t.contains(" vor ") || t.contains(" nach ")) return@let
            var hour = m.groupValues[4].toIntOrNull() ?: return@let
            var minute = m.groupValues[5].toIntOrNull() ?: 0
            // "halb 7" is 6:30; "7 Uhr abends" is 19:00.
            if (Regex("\\bhalb \\d").containsMatchIn(t) && m.groupValues[5].isEmpty()) {
                hour = (hour + 23) % 24
                minute = 30
            }
            if (hour in 1..11 && Regex("\\b(abends|abend|nachmittags|nachmittag|pm)\\b").containsMatchIn(t)) hour += 12
            if (hour in 0..23 && minute in 0..59 && !t.contains("minute") && !t.contains("stunde")) {
                return tool("set_alarm", "hour" to hour, "minute" to minute)
            }
        }

        // Timer: "Timer 10 Minuten", "Timer für 30 Sekunden", "5 Minuten Timer"
        if (t.contains("timer") || t.contains("countdown")) {
            Regex("\\b(\\d{1,3}) ?(sekunden|sekunde|sek|s|minuten|minute|min|m|stunden|stunde|std|h)\\b").find(t)?.let { m ->
                val n = m.groupValues[1].toInt()
                val unit = m.groupValues[2]
                val seconds = when {
                    unit.startsWith("st") || unit == "h" -> n * 3600
                    unit.startsWith("s") -> n
                    else -> n * 60
                }
                if (seconds > 0) return tool("set_timer", "seconds" to seconds)
            }
        }

        // Media
        when (t) {
            "pause", "musik pause", "musik stoppen", "musik stopp", "stopp", "stop", "pausieren", "musik anhalten" ->
                return tool("media", "action" to "pause")
            "play", "weiter", "musik weiter", "musik an", "musik abspielen", "abspielen", "fortsetzen", "musik fortsetzen" ->
                return tool("media", "action" to "play")
            "nachster song", "nachstes lied", "nachster titel", "skip", "uberspringen", "nachstes" ->
                return tool("media", "action" to "next")
            "vorheriger song", "vorheriges lied", "letztes lied", "vorheriger titel", "zuruck spulen" ->
                return tool("media", "action" to "previous")
        }

        // System
        if (Regex("^(mach |mache )?(ein )?(screenshot|bildschirmfoto)( machen)?$").matches(t)) return tool("system_action", "action" to "screenshot")
        if (Regex("^(handy |bildschirm |telefon )?(sperren|sperre)( bitte)?$").matches(t) || t == "sperr das handy") {
            return tool("system_action", "action" to "lock_screen")
        }
        if (t == "auto drehen an" || t == "automatisch drehen an" || t == "bildschirm drehen an") return tool("auto_rotate", "on" to true)
        if (t == "auto drehen aus" || t == "automatisch drehen aus" || t == "bildschirm drehen aus") return tool("auto_rotate", "on" to false)
        SettingsWords[t]?.let { return tool("open_settings", "page" to it) }

        // Status
        // Only the level ("Wie viel Akku?"); "Warum ist der Akku so schnell leer?" is Claude's.
        if (Regex("\\b(akku|batterie|akkustand)\\b").containsMatchIn(t) && words.size <= 6 &&
            (words.size <= 2 || Regex("\\b(wie viel|wieviel|wie voll|akkustand|prozent|stand|ladung)\\b").containsMatchIn(t))
        ) {
            return listOf(Step.Say(batterySentence(tools)))
        }
        if (Regex("^(wie spat ist es|wie viel uhr ist es|uhrzeit|wie spat)$").matches(t)) {
            return listOf(Step.Say("Es ist " + SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date()) + " Uhr."))
        }
        if (Regex("^(welcher tag ist heute|welches datum|datum|was ist heute fur ein tag)$").matches(t)) {
            return listOf(Step.Say("Heute ist " + SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMAN).format(Date()) + "."))
        }

        // Apps: "Öffne Spotify", "Starte die Kamera", "Spotify öffnen"
        val appName = Regex("^(offne|offnen|starte|starten|open|launch|geh zu|gehe zu|zeig mir) (die |den |das )?(app )?(.+)$").find(t)?.groupValues?.get(4)
            ?: Regex("^(.+?) (offnen|starten|aufmachen)$").find(t)?.groupValues?.get(1)
        if (appName != null) {
            val app = tools.findApp(appName, strict = true)
            if (app != null) return tool("open_app", "name" to app.label.toString())
            if (!onlySafe) return listOf(Step.Say("Ich finde keine App namens „$appName“."))
            return null
        }

        // Without a key, a few more (that Claude would otherwise do better).
        if (onlySafe) return null

        Regex("^(ruf|rufe|anrufen) ([+\\d][\\d ]{3,}) ?(an)?$").find(t)?.let { m ->
            return tool("call", "number" to m.groupValues[2].replace(" ", ""))
        }
        // Destinations and searches keep the user's own spelling (München, not munchen).
        Regex("^(navigiere|navigation|route|fahr mich|bring mich|wegbeschreibung)\\s+(zu |nach |zum |zur |in die )?(.+)$", RegexOption.IGNORE_CASE)
            .find(text.trim())?.let { m -> return tool("navigate", "destination" to m.groupValues[3].trim()) }
        Regex("^(suche nach|such nach|google|suche|such|search)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text.trim())?.let { m -> return tool("web_search", "query" to m.groupValues[2].trim()) }
        if (Regex("^(danke|dankeschon|vielen dank|thx|thanks)$").matches(t)) return listOf(Step.Say("Gern geschehen!"))
        if (Regex("^(hallo|hi|hey|hey claude|hallo claude|moin|servus)$").matches(t)) {
            return listOf(Step.Say("Hallo! Sag mir, was ich auf dem Handy erledigen soll."))
        }
        return null
    }

    private val SettingsWords = mapOf(
        "einstellungen" to "main", "einstellungen offnen" to "main",
        "wlan" to "wifi", "wlan einstellungen" to "wifi", "wifi" to "wifi",
        "bluetooth" to "bluetooth", "bluetooth einstellungen" to "bluetooth",
        "flugmodus" to "airplane", "standort" to "location", "hotspot" to "hotspot",
        "akku einstellungen" to "battery", "energiesparmodus" to "battery_saver",
        "display einstellungen" to "display", "ton einstellungen" to "sound",
    )

    private fun batterySentence(tools: ClaudeTools): String {
        val status = tools.deviceStatus().lines()
        val battery = status.firstOrNull { it.startsWith("Akku:") }?.removePrefix("Akku: ")
        return if (battery != null) "Dein Akku ist bei $battery." else "Den Akkustand konnte ich nicht lesen."
    }
}
