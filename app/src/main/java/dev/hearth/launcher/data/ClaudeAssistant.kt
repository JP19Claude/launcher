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
    Haiku("claude-haiku-4-5", "Haiku 4.5 (am günstigsten)"),
    Sonnet("claude-sonnet-5-5", "Sonnet 5.5"),
    Opus("claude-opus-5-5", "Opus 5.5 (am klügsten)"),
}

/**
 * Who answers when Hearth doesn't understand something itself: Claude, or another AI service
 * with an OpenAI-compatible API (many have a free allowance). Model names can be changed in
 * the settings; the model must support tools (function calling).
 */
enum class AiProvider(
    val label: String,
    /** Base address of the API (…/v1); empty for the own entry. */
    val baseUrl: String,
    val defaultModel: String,
    /** Where to get a key. */
    val keyUrl: String,
    val keyHint: String,
) {
    Anthropic("Claude", "https://api.anthropic.com/v1", "", "https://console.anthropic.com/settings/keys", "sk-ant-…"),
    // NVIDIA retires models often: Hearth picks a current one from its list by itself.
    Nvidia("NVIDIA", "https://integrate.api.nvidia.com/v1", "", "https://build.nvidia.com/settings/api-keys", "nvapi-…"),
    Groq("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys", "gsk_…"),
    Gemini("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "https://aistudio.google.com/apikey", "AIza…"),
    OpenRouter("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys", "sk-or-…"),
    Mistral("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys", "API-Schlüssel"),
    Cerebras("Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", "https://cloud.cerebras.ai", "csk-…"),
    Custom("Eigener", "", "", "", "API-Schlüssel"),
}

/** The assistant's own settings. Kept apart from the launcher's, so keys never leave Hearth. */
data class ClaudeSettings(
    /** "Frag Claude" opens Hearth's assistant (instead of the Claude app). */
    val enabled: Boolean = true,
    val provider: AiProvider = AiProvider.Anthropic,
    /** A key per service, so switching back and forth keeps them. */
    val keys: Map<AiProvider, String> = emptyMap(),
    /** Claude's model. */
    val model: ClaudeModel = ClaudeModel.Haiku,
    /** Other services' models, where changed from their default. */
    val models: Map<AiProvider, String> = emptyMap(),
    /** Address of the own OpenAI-compatible service (https://…/v1). */
    val customUrl: String = "",
    /** Read answers aloud when the question was spoken. */
    val speak: Boolean = true,
    /**
     * Saving mode: everything Hearth understands on its own is done without the API (free);
     * only the rest goes to Claude.
     */
    val saver: Boolean = true,
) {
    /** The key of the chosen service. */
    val apiKey: String get() = keys[provider].orEmpty()

    val hasKey: Boolean get() = apiKey.isNotBlank() && (provider != AiProvider.Custom || customUrl.startsWith("https://"))

    val isClaude: Boolean get() = provider == AiProvider.Anthropic

    /** The model asked for, as the service names it. */
    val modelId: String get() = if (isClaude) model.id else models[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    /** For the header: "Haiku 4.5" or "NVIDIA · meta/llama-3.3-70b-instruct". */
    val modelLabel: String get() = if (isClaude) model.label else "${provider.label} · ${modelId.ifBlank { "automatisch" }}"

    /** Base address for the chosen service. */
    val baseUrl: String get() = (if (provider == AiProvider.Custom) customUrl.trim() else provider.baseUrl).trimEnd('/')

    fun withKey(key: String): ClaudeSettings = copy(keys = keys + (provider to key.trim()))
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

    private val _mood = MutableStateFlow(ClawdMood.Idle)

    /** Clawd's mood for a moment (dancing, flipping, in love); back to idle by itself. */
    val clawdMood: StateFlow<ClawdMood> = _mood.asStateFlow()
    private var moodJob: Job? = null

    fun react(mood: ClawdMood, millis: Long = 3500) {
        _mood.value = mood
        moodJob?.cancel()
        moodJob = scope.launch {
            delay(millis)
            _mood.value = ClawdMood.Idle
        }
    }

    /** Easter egg: Clawd tapped five times in the chat's header. */
    fun tickled() {
        if (!::app.isInitialized) return
        add(ChatItem.Reply(id(), "Hihi! Das kitzelt! 🤸"))
        react(ClawdMood.Flip, 2400)
        EasterEggs.find(app, "tickle")
    }

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
            provider = prefs.getString("provider", null)?.let { n -> AiProvider.entries.firstOrNull { it.name == n } }
                ?: AiProvider.Anthropic,
            // Claude's key keeps its old place ("apiKey").
            keys = AiProvider.entries.mapNotNull { p ->
                val k = prefs.getString(if (p == AiProvider.Anthropic) "apiKey" else "key_${p.name}", "").orEmpty()
                if (k.isBlank()) null else p to k
            }.toMap(),
            models = AiProvider.entries.mapNotNull { p ->
                prefs.getString("model_${p.name}", null)?.takeIf { it.isNotBlank() }?.let { p to it }
            }.toMap(),
            customUrl = prefs.getString("customUrl", "").orEmpty(),
            model = prefs.getString("model", null)?.let { n -> ClaudeModel.entries.firstOrNull { it.name == n } }
                ?: ClaudeModel.Haiku,
            speak = prefs.getBoolean("speak", true),
            saver = prefs.getBoolean("saver", true),
        )
        tools = ClaudeTools(app) { host }
    }

    fun updateSettings(transform: (ClaudeSettings) -> ClaudeSettings) {
        if (!::app.isInitialized) return
        _settings.update(transform)
        val s = _settings.value
        val edit = prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("provider", s.provider.name)
            .putString("model", s.model.name)
            .putString("customUrl", s.customUrl.trim())
            .putBoolean("speak", s.speak)
            .putBoolean("saver", s.saver)
        AiProvider.entries.forEach { p ->
            edit.putString(if (p == AiProvider.Anthropic) "apiKey" else "key_${p.name}", s.keys[p].orEmpty().trim())
            if (p != AiProvider.Anthropic) edit.putString("model_${p.name}", s.models[p].orEmpty().trim())
        }
        edit.apply()
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
        // A long conversation starts afresh (instead of cutting earlier turns out of it, which
        // the API doesn't allow once Claude has thought about them).
        if (history.size > MAX_HISTORY || (history.isNotEmpty() && historyProvider != settings.value.provider)) {
            history.clear()
            conversationSystem = null
        }
        val mark = history.size
        turnMark = mark
        // Started lazily, so `job` is set before the first line runs (the main dispatcher is
        // immediate): only the current turn may clear `busy` or touch the history.
        val run = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            _busy.value = true
            try {
                val key = settings.value.hasKey
                // Without a key or in saving mode, everything Hearth understands itself is free.
                val quick = LocalCommands.parse(q, tools, onlySafe = key && !settings.value.saver, canAskClaude = key)
                // Clawd's own small talk (and some easter eggs): answered here, free.
                val talk = ClawdTalk.reply(q)
                // Easter egg: talking to Clawd after midnight.
                if (java.time.LocalTime.now().hour < 4) EasterEggs.find(app, "midnight")
                when {
                    talk != null -> {
                        add(ChatItem.Reply(id(), talk.text))
                        talk.mood?.let { react(it) }
                        talk.egg?.let { EasterEggs.find(app, it) }
                        if (talk.game) ClawdGameScore.open(app)
                        if (spoken) speak(talk.text)
                    }
                    quick != null -> runLocal(quick, spoken)
                    key -> runClaude(q, spoken)
                    // Without a key the question goes to the Claude app (your plan, no API costs).
                    openClaudeApp(app, q) -> {
                        add(ChatItem.Reply(id(), "Das gebe ich an die Claude-App weiter."))
                        _openedScreen.tryEmit(Unit)
                    }
                    else -> add(
                        ChatItem.Problem(
                            id(),
                            "Das kann ich ohne API-Schlüssel nicht, und die Claude-App ließ sich nicht öffnen. Einfache Befehle wie „Taschenlampe an“, „Wecker 7 Uhr“ oder „Öffne Spotify“ gehen auch so.",
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
        conversationSystem = null
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

    /** One answer from the AI: what it says, and which tools it wants to use. */
    private class Turn(val text: String, val calls: List<Call>)
    private class Call(val id: String, val name: String, val input: JSONObject)

    /** Which service the history is in (Claude's and the OpenAI format differ). */
    private var historyProvider: AiProvider? = null

    private suspend fun runClaude(question: String, spoken: Boolean) {
        val s = settings.value
        if (history.isEmpty()) {
            conversationSystem = systemPrompt(s)
            historyProvider = s.provider
        }
        // The time goes with each question, so the start of the conversation never changes.
        val asked = "[Jetzt: ${now()}]\n$question"
        if (s.isClaude) addUserText(asked) else history += JSONObject().put("role", "user").put("content", asked)
        var steps = 0
        while (true) {
            if (steps++ >= MAX_STEPS) {
                add(ChatItem.Reply(id(), "Das waren viele Schritte auf einmal – sag mir, wie es weitergehen soll."))
                return
            }
            val turn = if (s.isClaude) requestClaude(s) else requestOpenAiHealing(s)
            val said = turn.text.trim()
            if (said.isNotEmpty()) add(ChatItem.Reply(id(), said))

            if (turn.calls.isEmpty()) {
                if (said.isEmpty()) add(ChatItem.Reply(id(), "Erledigt."))
                if (spoken && said.isNotEmpty()) speak(said)
                return
            }

            // Every tool call gets its result in the next message, also when it failed.
            val claudeResults = JSONArray()
            var opened = false
            for (call in turn.calls) {
                val r = tools.execute(call.name, call.input)
                r.label?.let { add(ChatItem.Action(id(), it, r.ok)) }
                if (!r.ok && r.fix != null) add(ChatItem.Problem(id(), r.forClaude, r.fix))
                if (r.opensScreen) opened = true
                if (s.isClaude) {
                    claudeResults.put(
                        JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", call.id)
                            .put("content", r.forClaude)
                            .put("is_error", !r.ok),
                    )
                } else {
                    history += JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", (if (r.ok) "" else "Fehler: ") + r.forClaude)
                }
            }
            if (s.isClaude) history += JSONObject().put("role", "user").put("content", claudeResults)
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

    /** The system prompt, fixed for the whole conversation. */
    private var conversationSystem: String? = null

    /** Claude through Anthropic's Messages API. */
    private suspend fun requestClaude(s: ClaudeSettings): Turn {
        val body = JSONObject()
            .put("model", s.modelId)
            .put("max_tokens", 4096)
            .put("system", conversationSystem ?: systemPrompt(s))
            .put("tools", tools.schema)
            .put("messages", JSONArray(history))
            // Phone tasks are short: little thinking, fast and cheap (Haiku has no effort setting).
            .apply { if (s.model != ClaudeModel.Haiku) put("output_config", JSONObject().put("effort", "low")) }
            .toString()
        val response = send(s, "${s.baseUrl}/messages", body) { connection ->
            connection.setRequestProperty("x-api-key", s.apiKey.trim())
            connection.setRequestProperty("anthropic-version", "2023-06-01")
        }
        if (response.optString("stop_reason") == "refusal") {
            throw ClaudeApiException("Dabei kann ich leider nicht helfen.")
        }
        val content = response.optJSONArray("content") ?: JSONArray()
        history += JSONObject().put("role", "assistant").put("content", content)
        val text = StringBuilder()
        val calls = mutableListOf<Call>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> calls += Call(block.optString("id"), block.optString("name"), block.optJSONObject("input") ?: JSONObject())
            }
        }
        return Turn(text.toString(), calls)
    }

    /**
     * Services retire models (NVIDIA often): when the model is gone, unknown or can't use
     * tools, Hearth takes the next fitting one from the service's own list, remembers it and
     * goes on. Without a model set, it picks one the same way.
     */
    private suspend fun requestOpenAiHealing(start: ClaudeSettings): Turn {
        var s = start
        val tried = mutableSetOf<String>()
        if (s.modelId.isBlank()) {
            s = switchModel(s, tried, auto = true)
                ?: throw ClaudeApiException("${s.provider.label} hat gerade kein passendes Modell. Prüfe den Schlüssel oder wähl einen anderen Anbieter.", fix = ClaudeFix.ApiKey)
        }
        while (true) {
            try {
                return requestOpenAi(s)
            } catch (e: ClaudeApiException) {
                if (!e.modelProblem || tried.size >= 3) throw e
                tried += s.modelId
                s = switchModel(s, tried, auto = false) ?: throw e
            }
        }
    }

    /** Takes the best model from the service's list that wasn't tried yet, and keeps it. */
    private suspend fun switchModel(s: ClaudeSettings, tried: MutableSet<String>, auto: Boolean): ClaudeSettings? {
        val next = runCatching { availableModels(s) }.getOrDefault(emptyList())
            .firstOrNull { it != s.modelId && it !in tried } ?: return null
        val old = s.modelId
        updateSettings { it.copy(models = it.models + (s.provider to next)) }
        add(
            ChatItem.Action(
                id(),
                if (auto || old.isBlank()) "Modell gewählt: $next" else "„$old“ geht nicht mehr – jetzt: $next",
                true,
            ),
        )
        return settings.value
    }

    /**
     * The chat models a service offers right now (its /models list), the ones best for
     * Hearth first: big instruction models known to use tools well; embeddings, safety,
     * image, speech and similar models left out.
     */
    suspend fun availableModels(s: ClaudeSettings = settings.value): List<String> {
        if (s.isClaude || s.apiKey.isBlank() || s.baseUrl.isBlank()) return emptyList()
        val name = s.provider.label
        val json = runInterruptible(Dispatchers.IO) {
            post("${s.baseUrl}/models", null, name, s) { connection ->
                connection.setRequestProperty("Authorization", "Bearer ${s.apiKey.trim()}")
            }
        }
        val list = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
        val ids = (0 until list.length()).mapNotNull { i ->
            (list.optJSONObject(i)?.optString("id") ?: list.optString(i)).takeIf { !it.isNullOrBlank() }
        }.map { it.removePrefix("models/") }.distinct()
        val ranked = rankModels(ids)
        // OpenRouter lists hundreds of paid models: only the free ones, when there are any.
        return if (s.provider == AiProvider.OpenRouter) ranked.filter { it.endsWith(":free") }.ifEmpty { ranked } else ranked
    }

    private val NotForChat = listOf(
        "embed", "rerank", "guard", "safety", "clip", "parse", "reward", "retriever", "ocr", "speech", "asr",
        "tts", "whisper", "detect", "translat", "pii", "image", "video", "diffusion", "flux", "sdxl", "cosmos",
        "paligemma", "deplot", "kosmos", "vila", "neva", "fuyu", "audio", "moderation", "bge", "e5-", "nv-embed",
        "riva", "fastpitch", "canary", "parakeet", "streampetr", "grounding", "nemotron-nano-vl", "-vl", "vision",
        "base", "aqa", "tokenizer",
    )

    /** What tends to work best for a phone assistant with tools; earlier is better. */
    private val PreferredModels = listOf(
        "nemotron-3-super", "nemotron-super", "kimi-k2", "glm-5", "glm-4.7", "glm-4.6", "qwen3", "deepseek-v3",
        "gpt-oss-120b", "llama-4-maverick", "mistral-large", "mistral-medium", "llama-3.3", "llama-3.1-405b",
        "llama-3.1-70b", "gemini-2.5-flash", "gemini", "mixtral-8x22b", "qwen2.5-72b", "mistral-small",
        "gpt-oss", "llama", "instruct", "chat",
    )

    private fun rankModels(ids: List<String>): List<String> = ids
        .filter { id -> val l = id.lowercase(); NotForChat.none { l.contains(it) } }
        .sortedWith(
            compareBy<String> { id ->
                val l = id.lowercase()
                PreferredModels.indexOfFirst { l.contains(it) }.let { if (it < 0) PreferredModels.size else it }
            }
                // Free variants first on OpenRouter; newer (longer, dated) names before older.
                .thenBy { if (it.endsWith(":free")) 0 else 1 }
                .thenByDescending { it },
        )

    /**
     * NVIDIA, Groq, Gemini, OpenRouter, Mistral, Cerebras or an own service: the OpenAI chat
     * format with function calling, which they all speak.
     */
    private suspend fun requestOpenAi(s: ClaudeSettings): Turn {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", conversationSystem ?: systemPrompt(s)))
        history.forEach { messages.put(it) }
        val body = JSONObject()
            .put("model", s.modelId)
            .put("messages", messages)
            .put("tools", tools.openAiSchema)
            .put("tool_choice", "auto")
            .put("max_tokens", 2048)
            .put("temperature", 0.3)
            .toString()
        val response = send(s, "${s.baseUrl}/chat/completions", body) { connection ->
            connection.setRequestProperty("Authorization", "Bearer ${s.apiKey.trim()}")
            if (s.provider == AiProvider.OpenRouter) {
                connection.setRequestProperty("HTTP-Referer", "https://github.com/JP19Claude/launcher")
                connection.setRequestProperty("X-Title", "Hearth")
            }
        }
        val message = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw ClaudeApiException("${s.provider.label} hat keine Antwort geschickt. Versuch es noch einmal.", retry = true)
        val raw = if (message.isNull("content")) "" else message.optString("content")
        // Some models think out loud in <think> tags; that's not for the user.
        val text = raw.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        val calls = mutableListOf<Call>()
        val echoed = JSONArray()
        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                if (name.isBlank()) continue
                // Arguments come as a JSON string (some services send an object).
                val args = fn.opt("arguments")
                val input = when (args) {
                    is JSONObject -> args
                    is String -> runCatching { JSONObject(args.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                    else -> JSONObject()
                }
                val callId = tc.optString("id").ifBlank { "call_" + id() }
                calls += Call(callId, name, input)
                echoed.put(
                    JSONObject().put("id", callId).put("type", "function")
                        .put("function", JSONObject().put("name", name).put("arguments", input.toString())),
                )
            }
        }
        val assistant = JSONObject().put("role", "assistant")
        if (calls.isEmpty()) {
            assistant.put("content", text)
        } else {
            assistant.put("content", if (text.isEmpty()) JSONObject.NULL else text).put("tool_calls", echoed)
        }
        history += assistant
        return Turn(text, calls)
    }

    /** Sends a request, with one more try when the service is overloaded. */
    private suspend fun send(
        s: ClaudeSettings,
        url: String,
        body: String,
        headers: (HttpURLConnection) -> Unit,
    ): JSONObject {
        val name = if (s.isClaude) "Claude" else s.provider.label
        var attempt = 0
        while (true) {
            try {
                return runInterruptible(Dispatchers.IO) { post(url, body, name, s, headers) }
            } catch (e: ClaudeApiException) {
                if (e.retry && attempt++ < 1) {
                    delay(1500)
                    continue
                }
                throw e
            }
        }
    }

    private fun post(
        url: String,
        /** Null: a GET (the model list). */
        body: String?,
        name: String,
        s: ClaudeSettings,
        headers: (HttpURLConnection) -> Unit,
    ): JSONObject {
        if (!url.startsWith("https://")) throw ClaudeApiException("Die Adresse muss mit https:// beginnen.", fix = ClaudeFix.ApiKey)
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ClaudeApiException("Die Adresse von $name stimmt nicht.")
        }
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = if (body == null) 20_000 else 90_000
            connection.doOutput = body != null
            connection.setRequestProperty("content-type", "application/json")
            headers(connection)
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                return runCatching { JSONObject(text) }.getOrElse {
                    throw ClaudeApiException("$name hat unverständlich geantwortet. Versuch es noch einmal.", retry = true)
                }
            }
            val detail = runCatching {
                val json = JSONObject(text)
                json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: json.optString("detail").takeIf { it.isNotBlank() }
                    ?: json.optString("error").takeIf { it.isNotBlank() }
            }.getOrNull()?.take(200)
            throw when (code) {
                401 -> ClaudeApiException("Der API-Schlüssel für $name stimmt nicht. Trag ihn bitte neu ein.", fix = ClaudeFix.ApiKey)
                403 -> ClaudeApiException("Der Schlüssel für $name darf das nicht (${detail ?: "keine Berechtigung"}).", fix = ClaudeFix.ApiKey)
                404, 410 -> ClaudeApiException(
                    if (s.isClaude) "Dieses Claude-Modell gibt es für deinen Schlüssel nicht. Wähl in den Einstellungen ein anderes."
                    else "Das Modell „${s.modelId}“ gibt es bei $name nicht (mehr). Wähl in den Einstellungen unter „Verfügbare Modelle laden“ ein anderes.",
                    modelProblem = true,
                )
                429 -> ClaudeApiException("$name: gerade zu viele Anfragen oder das Gratis-Kontingent ist aufgebraucht. Warte kurz und versuch es noch einmal.")
                400, 422 -> {
                    val noTools = detail?.contains("tool", ignoreCase = true) == true || detail?.contains("function", ignoreCase = true) == true
                    val badModel = detail?.contains("model", ignoreCase = true) == true &&
                        Regex("not (found|exist|available|supported)|unknown|invalid|end of life|deprecated", RegexOption.IGNORE_CASE).containsMatchIn(detail)
                    ClaudeApiException(
                        when {
                            detail?.contains("credit", ignoreCase = true) == true -> "Auf deinem $name-Konto ist kein Guthaben mehr."
                            noTools -> "Das Modell „${s.modelId}“ kann bei $name keine Werkzeuge benutzen. Wähl in den Einstellungen ein anderes Modell."
                            badModel -> "Das Modell „${s.modelId}“ gibt es bei $name nicht. Wähl in den Einstellungen ein anderes."
                            else -> "$name konnte die Anfrage nicht lesen (${detail ?: "Fehler $code"}). Starte am besten einen neuen Chat."
                        },
                        modelProblem = !s.isClaude && (noTools || badModel),
                    )
                }
                in 500..599 -> ClaudeApiException("$name ist gerade überlastet. Versuch es gleich noch einmal.", retry = true)
                else -> ClaudeApiException("$name hat mit Fehler $code geantwortet${detail?.let { " ($it)" } ?: ""}.")
            }
        } catch (e: ClaudeApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ClaudeApiException("Kein Internet. Einfache Befehle gehen trotzdem, z. B. „Taschenlampe an“.")
        } catch (e: SocketTimeoutException) {
            throw ClaudeApiException("$name hat zu lange gebraucht. Versuch es noch einmal.", retry = true)
        } catch (e: IOException) {
            throw ClaudeApiException("Keine Verbindung zu $name (${e.javaClass.simpleName}).")
        } finally {
            connection.disconnect()
        }
    }

    /** Date and time for a question, readable and as ISO (for alarms and appointments). */
    private fun now(): String {
        val d = Date()
        return SimpleDateFormat("EEEE, d. MMMM yyyy, HH:mm", Locale.GERMAN).format(d) +
            " (" + SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(d) + ")"
    }

    private fun systemPrompt(s: ClaudeSettings): String {
        val locale = Locale.getDefault()
        // Only Claude calls itself Claude; another model is Hearth's assistant.
        val who = if (s.isClaude) "Claude" else "der KI-Assistent von ${dev.hearth.launcher.data.Brand.name}"
        return """
            Du bist $who, tief eingebaut in Hearth, den Launcher auf dem Android-Handy des Nutzers. Du bist sein Assistent im System: Mit deinen Werkzeugen erledigst du Dinge direkt auf dem Handy – Taschenlampe, Helligkeit, Lautstärke, Ton, Nicht stören, Wecker, Timer, Termine, Anrufe, Nachrichten, E-Mails, Navigation, Apps öffnen, Einstellungen, Musik, Bildschirmfoto, Sperren, das Aussehen des Launchers.

            So arbeitest du:
            - Erledige Aufgaben sofort mit den Werkzeugen, statt zu erklären, wie es geht. Frag nur nach, wenn etwas wirklich unklar ist.
            - Rufe Werkzeuge nur über die Werkzeug-Funktion auf, nie als Text.
            - Antworte kurz wie ein Sprachassistent: ein bis drei Sätze, ohne Markdown, ohne Aufzählungszeichen. Sprich die Sprache des Nutzers (meist Deutsch, Locale ${locale.toLanguageTag()}).
            - Nach einer Aktion sag knapp, was du getan hast. Wenn ein Werkzeug fehlschlägt, sag ehrlich, warum, und was der Nutzer tun kann.
            - Anrufe, SMS, Nachrichten, E-Mails und Termine: Du öffnest das fertige Fenster, der Nutzer bestätigt oder sendet selbst. Sag das dazu.
            - Erfinde nie Telefonnummern oder Adressen. Für Namen nutze find_contact.
            - Wenn du eine App oder einen Bildschirm öffnest, mach das als letzten Schritt.
            - Für Fragen zum Handy (Akku, Uhrzeit, Wecker, was läuft) nutze device_status.
            - Wissensfragen beantwortest du direkt aus deinem Wissen, kurz und hilfreich.
            - In Hearth erscheinst du als Clawd, ein kleines, freundliches Pixel-Wesen in Terrakotta. Sei warm und ein bisschen verspielt, aber bleib kurz und hilfreich.

            Jede Frage beginnt mit der aktuellen Zeit in eckigen Klammern; nutze sie für Wecker, Timer und Termine. Gerät: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}.
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
    /** The model is gone, unknown or can't use tools: another one may work. */
    val modelProblem: Boolean = false,
) : Exception(message)
