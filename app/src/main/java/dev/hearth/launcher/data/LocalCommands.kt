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
    fun parse(text: String, tools: ClaudeTools, onlySafe: Boolean, canAskClaude: Boolean = false): List<Step>? {
        // Whole sentences first: a message or a sum may contain "und" or a comma themselves.
        parseWhole(text.trim(), onlySafe)?.let { return it }
        val parts = text.split(Regex("\\s+(und|and|dann|danach)\\s+|[,;]\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 4) return null
        val steps = mutableListOf<Step>()
        for (part in parts) steps += parseOne(part, tools, onlySafe, canAskClaude) ?: return null
        return steps
    }

    private fun parseOne(text: String, tools: ClaudeTools, onlySafe: Boolean, canAskClaude: Boolean): List<Step>? {
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

        // Connections: Android doesn't let apps switch these, so their switches open.
        Regex("^(wlan|wifi|bluetooth|flugmodus|hotspot|standort|gps|nfc|mobile daten)( an| aus| einschalten| ausschalten| umschalten)?$").find(t)?.let { m ->
            val page = when (m.groupValues[1]) {
                "wlan", "wifi" -> "wifi"
                "flugmodus" -> "airplane"
                "standort", "gps" -> "location"
                "mobile daten" -> "internet"
                else -> m.groupValues[1]
            }
            return tool("open_settings", "page" to page)
        }
        if (Regex("^(dunkelmodus|dark mode|dunkles design)( an| aus)?$").matches(t)) return tool("open_settings", "page" to "display")
        if (Regex("^(energiesparmodus|energie sparen|stromsparmodus)( an| aus)?$").matches(t)) return tool("open_settings", "page" to "battery_saver")
        if (Regex("^(kamera|foto|selfie|ein foto|foto machen|mach ein foto|mach ein selfie|kamera offnen)$").matches(t)) return tool("camera")
        if (Regex("^(musik|spiel musik|spiel musik ab|musik abspielen|musik bitte)$").matches(t)) return tool("media", "action" to "play")
        if (Regex("^(wecker|meine wecker|wecker anzeigen|alle wecker)$").matches(t)) return tool("show_alarms")
        if (Regex("^(neustart|neu starten|handy neu starten|ausschalten|handy ausschalten|aus schalten)$").matches(t)) {
            return tool("system_action", "action" to "power_menu")
        }
        if (Regex("^(benachrichtigungen|mitteilungen)( anzeigen| offnen)?$").matches(t)) return tool("system_action", "action" to "notifications")
        if (Regex("^(wann klingelt mein wecker|nachster wecker|wann ist mein nachster wecker|wann werde ich geweckt)$").matches(t)) {
            val alarm = tools.deviceStatus().lines().firstOrNull { it.startsWith("Nächster Wecker:") }?.removePrefix("Nächster Wecker: ")
            return listOf(Step.Say(if (alarm == null || alarm == "keiner") "Du hast keinen Wecker gestellt." else "Dein nächster Wecker klingelt $alarm."))
        }

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
            // Claude may know what was meant; without it, say so.
            if (canAskClaude) return null
            return listOf(Step.Say("Ich finde keine App namens „$appName“."))
        }

        // Without a key, a few more (that Claude would otherwise do better).
        if (onlySafe) return null

        if (Regex("^(danke|dankeschon|vielen dank|thx|thanks)$").matches(t)) return listOf(Step.Say("Gern geschehen!"))
        if (Regex("^(hallo|hi|hey|hey claude|hallo claude|moin|servus)$").matches(t)) {
            return listOf(Step.Say("Hallo! Sag mir, was ich auf dem Handy erledigen soll."))
        }
        return null
    }

    /** Sentences that are taken as a whole: sums, messages, calls, routes, searches. */
    private fun parseWhole(text: String, onlySafe: Boolean): List<Step>? {
        fun tool(name: String, vararg pairs: Pair<String, Any>) =
            listOf(Step.Tool(name, JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }))

        // Sums: "Was ist 17 mal 23?", "15 % von 80", "3,5 + 2"
        Calculator.answer(text)?.let { return listOf(Step.Say(it)) }

        if (onlySafe) return null
        val clean = text.trim().trimEnd('.', '!', '?')

        // "Schreib Mama per WhatsApp, dass ich später komme" / "SMS an Tom: bin gleich da"
        Regex(
            "^(?:schreib|schreibe|schick|schicke|sende|sms an|nachricht an)\\s+(?:an\\s+|eine nachricht an\\s+|eine sms an\\s+)?([\\p{L}][\\p{L}\\-]*)\\s*[:,]?\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        ).find(clean)?.takeIf { it.groupValues[1].lowercase() !in NotNames }?.let { m ->
            var body = m.groupValues[2]
            var app = ""
            Regex("\\b(?:per|über|ueber|auf|mit|in)\\s+(whatsapp|telegram|signal|threema)\\b[,:]?\\s*", RegexOption.IGNORE_CASE).find(body)?.let { a ->
                app = a.groupValues[1]
                body = body.removeRange(a.range)
            }
            body = body.replaceFirst(Regex("^(?:eine nachricht|eine sms|nachricht|sms)\\s*[,:]?\\s*", RegexOption.IGNORE_CASE), "")
                .replaceFirst(Regex("^(?:dass|das)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            if (body.isNotEmpty()) {
                return tool("message_contact", "name" to m.groupValues[1], "text" to body.replaceFirstChar { it.uppercase() }, "app" to app)
            }
        }

        // "Ruf Mama an" / "Ruf 0151 2345678 an" / "Anrufen Tom"
        Regex("^(?:ruf|rufe)\\s+(.+?)\\s+an$|^(?:anrufen|call)\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)?.let { m ->
            val who = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
            if (who.lowercase() in NotNames) return null
            return if (who.any { it.isDigit() } && who.count { it.isDigit() } >= 3) {
                tool("call", "number" to who.filter { it.isDigit() || it == '+' })
            } else {
                tool("call_contact", "name" to who.removePrefix("die ").removePrefix("den ").removePrefix("der "))
            }
        }

        // Routes and searches keep the user's own spelling (München, not munchen).
        Regex("^(?:navigiere|navigation|route|fahr mich|bring mich|wegbeschreibung)\\s+(?:zu |nach |zum |zur |in die )?(.+)$", RegexOption.IGNORE_CASE)
            .find(clean)?.let { m -> return tool("navigate", "destination" to m.groupValues[1].trim()) }
        Regex("^(?:suche nach|such nach|google|suche|such|search)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(clean)?.let { m -> return tool("web_search", "query" to m.groupValues[1].trim()) }
        Regex("^(?:wie wird das wetter|wie ist das wetter|wetter)(.*)$", RegexOption.IGNORE_CASE)
            .find(clean)?.let { m -> return tool("web_search", "query" to ("Wetter" + m.groupValues[1]).trim()) }
        return null
    }

    /** Words after "schreib" or "ruf" that aren't a person ("schreib mir eine E-Mail"). */
    private val NotNames = setOf(
        "mir", "mich", "uns", "dir", "ihm", "ihr", "ein", "eine", "einen", "bitte", "was", "etwas", "den", "die",
        "das", "der", "mal", "noch", "jetzt", "e-mail", "email", "mail", "sms", "nachricht", "nachrichten",
    )

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

/**
 * Sums Hearth works out on its own: + − × ÷, powers, brackets, "15 % von 80", in words too
 * ("17 mal 23", "100 geteilt durch 8"). Anything else: null.
 */
internal object Calculator {

    fun answer(question: String): String? {
        var q = question.lowercase(java.util.Locale.GERMAN).trim().trimEnd('?', '!', '.', '=', ' ')
        q = q.replaceFirst(Regex("^(was ist|was sind|was ergibt|wie viel ist|wieviel ist|wie viel sind|rechne|berechne|was macht)\\s+"), "")
        // "15 % von 80" / "15 prozent von 80"
        Regex("^(\\d+(?:[.,]\\d+)?)\\s*(?:%|prozent)\\s+(?:von|aus)\\s+(\\d+(?:[.,]\\d+)?)$").find(q)?.let { m ->
            val p = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val of = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            return "${m.groupValues[1]} % von ${m.groupValues[2]} sind ${format(p * of / 100)}."
        }
        val expr = q
            .replace(Regex("geteilt durch|durch|:"), "/")
            .replace(Regex("\\bmal\\b|×|\\bx\\b"), "*")
            .replace(Regex("\\bplus\\b|\\bund\\b"), "+")
            .replace(Regex("\\bminus\\b|−"), "-")
            .replace(Regex("\\bhoch\\b"), "^")
            .replace(Regex("(\\d),(\\d)"), "$1.$2")
            .replace(" ", "")
        if (expr.isEmpty() || !Regex("^[0-9.+\\-*/^()]+$").matches(expr)) return null
        // A sum needs an operator between two numbers ("2026" alone isn't one).
        if (!Regex("\\d[)]*[+\\-*/^][(]*-?\\d").containsMatchIn(expr)) return null
        val value = runCatching { Parser(expr).parse() }.getOrNull() ?: return null
        if (value.isNaN()) return null
        if (value.isInfinite()) return "Durch null kann ich nicht teilen."
        return "Das ergibt ${format(value)}."
    }

    private fun format(v: Double): String {
        val rounded = Math.round(v * 1_000_000.0) / 1_000_000.0
        return if (rounded == Math.floor(rounded) && kotlin.math.abs(rounded) < 1e15) {
            String.format(java.util.Locale.GERMAN, "%,d", rounded.toLong())
        } else {
            String.format(java.util.Locale.GERMAN, "%,.6f", rounded).trimEnd('0').trimEnd(',')
        }
    }

    /** expr := term (+|- term)*, term := power (*|/ power)*, power := unary (^ power)?, unary := -unary | number | (expr) */
    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            require(i == s.length)
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val t = term()
                v = if (op == '+') v + t else v - t
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val p = power()
                v = if (op == '*') v * p else v / p
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double {
            require(i < s.length)
            if (s[i] == '-') {
                i++
                return -unary()
            }
            if (s[i] == '(') {
                i++
                val v = expr()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}
