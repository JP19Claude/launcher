package dev.hearth.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** The Claude model the assistant talks to. */
enum class ClaudeModel(val id: String, val label: String) {
    Sonnet("claude-sonnet-5-5", "Sonnet 5.5"),
    Haiku("claude-haiku-4-5-20251001", "Haiku 4.5 (am schnellsten)"),
    Opus("claude-opus-5-5", "Opus 5.5 (am klügsten)"),
}

/** The assistant's own settings. Kept apart from the launcher's, so the key never leaves Hearth. */
data class ClaudeSettings(
    /** "Frag Claude" opens Hearth's assistant (instead of the Claude app). */
    val enabled: Boolean = true,
    val apiKey: String = "",
    val model: ClaudeModel = ClaudeModel.Sonnet,
    /** Read answers aloud when the question was spoken. */
    val speak: Boolean = true,
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
}

/** Something the user can do to let Claude go further. */
enum class ClaudeFix { ApiKey, Contacts, WriteSettings, DoNotDisturb, Accessibility }

/** One line of the conversation as the assistant shows it. */
sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Reply(override val id: Long, val text: String) : ChatItem

    /** Something Claude did on the phone, as a small checked (or failed) line. */
    data class Action(override val id: Long, val label: String, val ok: Boolean) : ChatItem
    data class Problem(override val id: Long, val text: String, val fix: ClaudeFix? = null) : ChatItem
}

/** What the launcher itself offers Claude while it runs (design, its settings, web search). */
interface AssistantHost {
    fun applyPreset(preset: DesignPreset)
    fun openLauncherSettings()
    fun openWallpaperPicker()
    fun webSearch(query: String)
}

/**
 * Claude, deep in the system: an assistant that doesn't just talk but does things on the phone –
 * flashlight, brightness, volume, alarms, timers, appointments, messages, navigation, apps,
 * settings, music, screenshots, the launcher's look. It runs with Claude's tools through the
 * Anthropic API (the user's own key); simple commands work offline as well, without a key.
 *
 * One instance for the whole app, so the launcher's sheet and the assist screen (long press
 * on home, quick settings tile, Glimmer) share the conversation. Nothing here throws: every
 * failure ends up as a line in the conversation.
 */
object ClaudeAssistant {

    private lateinit var app: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var tools: ClaudeTools

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val ids = AtomicLong(0)

    private val _settings = MutableStateFlow(ClaudeSettings())
    val settings: StateFlow<ClaudeSettings> = _settings.asStateFlow()

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _openedScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Claude opened an app or a system screen: the sheet makes way for it. */
    val openedScreen: SharedFlow<Unit> = _openedScreen.asSharedFlow()

    /** The launcher, while it runs. */
    @Volatile
    var host: AssistantHost? = null

    /** The conversation as the API sees it (with tool calls and their results). */
    private val history = mutableListOf<JSONObject>()
    private var lastQuestion: String? = null

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = ClaudeSettings(
            enabled = prefs.getBoolean("enabled", true),
            apiKey = prefs.getString("apiKey", "").orEmpty(),
            model = prefs.getString("model", null)?.let { n -> ClaudeModel.entries.firstOrNull { it.name == n } }
                ?: ClaudeModel.Sonnet,
            speak = prefs.getBoolean("speak", true),
        )
        tools = ClaudeTools(app) { host }
    }

    fun updateSettings(transform: (ClaudeSettings) -> ClaudeSettings) {
        if (!::app.isInitialized) return
        _settings.update(transform)
        val s = _settings.value
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("apiKey", s.apiKey.trim())
            .putString("model", s.model.name)
            .putBoolean("speak", s.speak)
            .apply()
    }

    // Conversation

    /** Asks Claude (or does it right away, for simple commands). */
    fun ask(question: String, spoken: Boolean = false) {
        if (!::app.isInitialized) return
        val q = question.trim().take(MAX_QUESTION)
        if (q.isEmpty()) return
        stopSpeaking()
        cancelTurn()
        lastQuestion = q
        add(ChatItem.User(id(), q))
        val mark = history.size
        turnMark = mark
        // Started lazily, so `job` is set before the first line runs (the main dispatcher is
        // immediate): only the current turn may clear `busy` or touch the history.
        val run = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            _busy.value = true
            try {
                val key = settings.value.hasKey
                val quick = LocalCommands.parse(q, tools, onlySafe = key)
                when {
                    quick != null -> runLocal(quick, spoken)
                    key -> runClaude(q, spoken)
                    else -> add(
                        ChatItem.Problem(
                            id(),
                            "Das kann ich ohne Internet-Claude nicht. Mit deinem API-Schlüssel erledige ich fast alles auf dem Handy. Einfache Befehle wie „Taschenlampe an“, „Wecker 7 Uhr“ oder „Öffne Spotify“ gehen auch so.",
                            ClaudeFix.ApiKey,
                        ),
                    )
                }
                if (job === self) turnMark = -1
            } catch (e: CancellationException) {
                // cancelTurn() already took the turn back.
                throw e
            } catch (e: ClaudeApiException) {
                if (job === self) {
                    rollBack(mark)
                    turnMark = -1
                    add(ChatItem.Problem(id(), e.message ?: "Claude ist gerade nicht erreichbar.", e.fix))
                }
            } catch (t: Throwable) {
                if (job === self) {
                    rollBack(mark)
                    turnMark = -1
                    add(ChatItem.Problem(id(), "Da ist etwas schiefgelaufen (${t.javaClass.simpleName}). Versuch es bitte noch einmal."))
                }
            } finally {
                if (job === self) {
                    job = null
                    _busy.value = false
                }
            }
        }
        job = run
        run.start()
    }

    /** History length before the running turn; -1 when none runs. */
    private var turnMark = -1

    /** Stops the running turn and takes it out of the history (Claude never saw it through). */
    private fun cancelTurn() {
        job?.cancel()
        job = null
        if (turnMark >= 0) rollBack(turnMark)
        turnMark = -1
    }

    /** Asks the last question again, e.g. after a permission was granted. */
    fun retry() {
        lastQuestion?.let { ask(it) }
    }

    fun stop() {
        cancelTurn()
        _busy.value = false
        stopSpeaking()
    }

    /** A new conversation. */
    fun clear() {
        stop()
        history.clear()
        _items.value = emptyList()
        lastQuestion = null
    }

    /** A note from the assistant itself (e.g. no speech input on this phone). */
    fun notice(text: String) {
        if (::app.isInitialized) add(ChatItem.Problem(id(), text))
    }

    private fun add(item: ChatItem) {
        // The sheet shows the last lines; very long chats drop the oldest.
        _items.update { (it + item).takeLast(MAX_ITEMS) }
    }

    private fun id() = ids.incrementAndGet()

    private fun rollBack(mark: Int) {
        while (history.size > mark) history.removeAt(history.lastIndex)
    }

    private suspend fun runLocal(steps: List<LocalCommands.Step>, spoken: Boolean) {
        val said = mutableListOf<String>()
        for (step in steps) {
            when (step) {
                is LocalCommands.Step.Say -> said += step.text
                is LocalCommands.Step.Tool -> {
                    val r = tools.execute(step.name, step.input)
                    r.label?.let { add(ChatItem.Action(id(), it, r.ok)) }
                    if (!r.ok) {
                        add(ChatItem.Problem(id(), r.forClaude, r.fix))
                    } else {
                        r.say?.let { said += it }
                    }
                    if (r.opensScreen) _openedScreen.tryEmit(Unit)
                }
            }
        }
        val text = said.joinToString(" ").ifBlank { if (steps.isEmpty()) "" else "Erledigt." }
        if (text.isNotBlank()) {
            add(ChatItem.Reply(id(), text))
            if (spoken) speak(text)
        }
    }

    private suspend fun runClaude(question: String, spoken: Boolean) {
        addUserText(question)
        trimHistory()
        var steps = 0
        while (true) {
            if (steps++ >= MAX_STEPS) {
                add(ChatItem.Reply(id(), "Das waren viele Schritte auf einmal – sag mir, wie es weitergehen soll."))
                return
            }
            val response = request()
            val content = response.optJSONArray("content") ?: JSONArray()
            history += JSONObject().put("role", "assistant").put("content", content)

            val text = StringBuilder()
            val uses = mutableListOf<JSONObject>()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> text.append(block.optString("text"))
                    "tool_use" -> uses += block
                }
            }
            val said = text.toString().trim()
            if (said.isNotEmpty()) add(ChatItem.Reply(id(), said))

            if (uses.isEmpty()) {
                if (said.isEmpty()) add(ChatItem.Reply(id(), "Erledigt."))
                if (spoken && said.isNotEmpty()) speak(said)
                return
            }

            // Every tool call gets its result in the next message, also when it failed.
            val results = JSONArray()
            var opened = false
            for (use in uses) {
                val name = use.optString("name")
                val input = use.optJSONObject("input") ?: JSONObject()
                val r = tools.execute(name, input)
                r.label?.let { add(ChatItem.Action(id(), it, r.ok)) }
                if (!r.ok && r.fix != null) add(ChatItem.Problem(id(), r.forClaude, r.fix))
                if (r.opensScreen) opened = true
                results.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", use.optString("id"))
                        .put("content", r.forClaude)
                        .put("is_error", !r.ok),
                )
            }
            history += JSONObject().put("role", "user").put("content", results)
            if (opened) _openedScreen.tryEmit(Unit)
        }
    }

    /** A new question; joined to the last message if that was ours too (tool results). */
    private fun addUserText(text: String) {
        val block = JSONObject().put("type", "text").put("text", text)
        val last = history.lastOrNull()
        if (last != null && last.optString("role") == "user") {
            last.optJSONArray("content")?.put(block)
        } else {
            history += JSONObject().put("role", "user").put("content", JSONArray().put(block))
        }
    }

    /** Keeps the conversation short; it may only start at a question (not at a tool result). */
    private fun trimHistory() {
        while (history.size > MAX_HISTORY) {
            val start = (1 until history.size).firstOrNull { i ->
                val m = history[i]
                m.optString("role") == "user" &&
                    m.optJSONArray("content")?.optJSONObject(0)?.optString("type") == "text"
            } ?: break
            repeat(start) { history.removeAt(0) }
        }
    }

    private suspend fun request(): JSONObject {
        val s = settings.value
        val body = JSONObject()
            .put("model", s.model.id)
            .put("max_tokens", 1024)
            .put("system", systemPrompt())
            .put("tools", tools.schema)
            .put("messages", JSONArray(history))
            .toString()
        var attempt = 0
        while (true) {
            try {
                return runInterruptible(Dispatchers.IO) { post(s.apiKey.trim(), body) }
            } catch (e: ClaudeApiException) {
                // Overloaded or a hiccup on the server: once more, after a moment.
                if (e.retry && attempt++ < 1) {
                    delay(1500)
                    continue
                }
                throw e
            }
        }
    }

    private fun post(key: String, body: String): JSONObject {
        val connection = try {
            URL(API_URL).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ClaudeApiException("Keine Verbindung zu Claude. Bist du online?")
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("x-api-key", key)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                return runCatching { JSONObject(text) }.getOrElse {
                    throw ClaudeApiException("Claude hat unverständlich geantwortet. Versuch es noch einmal.", retry = true)
                }
            }
            val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            throw when (code) {
                401 -> ClaudeApiException("Der API-Schlüssel stimmt nicht. Trag ihn bitte neu ein.", fix = ClaudeFix.ApiKey)
                403 -> ClaudeApiException("Der API-Schlüssel darf das nicht (${detail ?: "keine Berechtigung"}).", fix = ClaudeFix.ApiKey)
                404 -> ClaudeApiException("Dieses Claude-Modell gibt es für deinen Schlüssel nicht. Wähl in den Einstellungen ein anderes.")
                429 -> ClaudeApiException("Gerade zu viele Anfragen. Warte kurz und versuch es noch einmal.")
                400 -> ClaudeApiException(
                    if (detail?.contains("credit", ignoreCase = true) == true) {
                        "Auf deinem Anthropic-Konto ist kein Guthaben mehr."
                    } else {
                        "Claude konnte die Anfrage nicht lesen (${detail ?: "Fehler 400"}). Starte am besten einen neuen Chat."
                    },
                )
                in 500..599 -> ClaudeApiException("Claude ist gerade überlastet. Versuch es gleich noch einmal.", retry = true)
                else -> ClaudeApiException("Claude hat mit Fehler $code geantwortet.")
            }
        } catch (e: ClaudeApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ClaudeApiException("Kein Internet. Einfache Befehle gehen trotzdem, z. B. „Taschenlampe an“.")
        } catch (e: SocketTimeoutException) {
            throw ClaudeApiException("Claude hat zu lange gebraucht. Versuch es noch einmal.", retry = true)
        } catch (e: IOException) {
            throw ClaudeApiException("Keine Verbindung zu Claude (${e.javaClass.simpleName}).")
        } finally {
            connection.disconnect()
        }
    }

    private fun systemPrompt(): String {
        val now = Date()
        val locale = Locale.getDefault()
        val date = SimpleDateFormat("EEEE, d. MMMM yyyy, HH:mm", Locale.GERMAN).format(now)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(now)
        return """
            Du bist Claude, tief eingebaut in Hearth, den Launcher auf dem Android-Handy des Nutzers. Du bist sein Assistent im System: Mit deinen Werkzeugen erledigst du Dinge direkt auf dem Handy – Taschenlampe, Helligkeit, Lautstärke, Ton, Nicht stören, Wecker, Timer, Termine, Anrufe, Nachrichten, E-Mails, Navigation, Apps öffnen, Einstellungen, Musik, Bildschirmfoto, Sperren, das Aussehen des Launchers.

            So arbeitest du:
            - Erledige Aufgaben sofort mit den Werkzeugen, statt zu erklären, wie es geht. Frag nur nach, wenn etwas wirklich unklar ist.
            - Antworte kurz wie ein Sprachassistent: ein bis drei Sätze, ohne Markdown, ohne Aufzählungszeichen. Sprich die Sprache des Nutzers (meist Deutsch, Locale ${locale.toLanguageTag()}).
            - Nach einer Aktion sag knapp, was du getan hast. Wenn ein Werkzeug fehlschlägt, sag ehrlich, warum, und was der Nutzer tun kann.
            - Anrufe, SMS, Nachrichten, E-Mails und Termine: Du öffnest das fertige Fenster, der Nutzer bestätigt oder sendet selbst. Sag das dazu.
            - Erfinde nie Telefonnummern oder Adressen. Für Namen nutze find_contact.
            - Wenn du eine App oder einen Bildschirm öffnest, mach das als letzten Schritt.
            - Für Fragen zum Handy (Akku, Uhrzeit, Wecker, was läuft) nutze device_status.
            - Wissensfragen beantwortest du direkt aus deinem Wissen, kurz und hilfreich.

            Jetzt ist $date (ISO: $iso). Gerät: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}.
        """.trimIndent()
    }

    // Speaking

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private fun speak(text: String) {
        if (!settings.value.speak) return
        runCatching {
            val engine = tts
            if (engine == null) {
                pendingSpeech = text
                tts = TextToSpeech(app) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        runCatching { tts?.language = Locale.getDefault() }
                        pendingSpeech?.let { say(it) }
                    }
                    pendingSpeech = null
                }
            } else if (ttsReady) {
                say(text)
            } else {
                pendingSpeech = text
            }
        }
    }

    private fun say(text: String) {
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "claude-${id()}") }
    }

    fun stopSpeaking() {
        pendingSpeech = null
        runCatching { if (ttsReady) tts?.stop() }
    }

    // Opening the assistant from anywhere

    /**
     * Opens Hearth's assistant screen (from Glimmer, the control center or Hearth itself).
     * With a question only from the family's apps: the screen that takes questions is
     * protected by the bridge permission, so no other app can make Claude do things.
     */
    fun openAssistant(context: Context, question: String? = null): Boolean {
        val component = ComponentName(
            HEARTH_PACKAGE,
            if (question.isNullOrBlank()) ASSIST_ACTIVITY else ASK_ALIAS,
        )
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!question.isNullOrBlank()) intent.putExtra(EXTRA_PROMPT, question)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** The Claude app (with the question, if given), or claude.ai without the app. */
    fun openClaudeApp(context: Context, question: String? = null): Boolean {
        fun start(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        val installed = context.packageManager.getLaunchIntentForPackage(SystemControls.CLAUDE_PACKAGE)
        if (installed != null) {
            if (!question.isNullOrBlank()) {
                val share = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, question)
                    .setPackage(SystemControls.CLAUDE_PACKAGE)
                if (start(share)) return true
            }
            return start(installed)
        }
        val url = if (question.isNullOrBlank()) "https://claude.ai/new" else "https://claude.ai/new?q=" + android.net.Uri.encode(question)
        return start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    /** The last question, handed to the Claude app (for things only a chat can do). */
    fun handOverToApp(context: Context) {
        if (openClaudeApp(context, lastQuestion)) _openedScreen.tryEmit(Unit)
    }

    const val HEARTH_PACKAGE = "dev.hearth.launcher"
    const val ASSIST_ACTIVITY = "dev.hearth.launcher.ClaudeAssistActivity"
    const val ASK_ALIAS = "dev.hearth.launcher.ClaudeAskActivity"
    const val EXTRA_PROMPT = "dev.hearth.launcher.CLAUDE_PROMPT"

    private const val PREFS = "hearth_claude"
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MAX_STEPS = 8
    private const val MAX_HISTORY = 40
    private const val MAX_ITEMS = 80
    private const val MAX_QUESTION = 4000
}

/** A failed call to Claude, with a sentence for the user. */
class ClaudeApiException(
    message: String,
    val retry: Boolean = false,
    val fix: ClaudeFix? = null,
) : Exception(message)
