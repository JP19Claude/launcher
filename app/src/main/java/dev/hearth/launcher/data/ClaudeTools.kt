package dev.hearth.launcher.data

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import dev.hearth.launcher.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** What a tool did: a line for the user, and what Claude is told. */
class ToolResult(
    val ok: Boolean,
    /** What Claude reads back. */
    val forClaude: String,
    /** The small "✓ …" line in the conversation; null for read-only tools. */
    val label: String? = null,
    /** A short sentence for offline commands (Claude writes its own otherwise). */
    val say: String? = null,
    /** An app or a system screen opened: the assistant makes way. */
    val opensScreen: Boolean = false,
    val fix: ClaudeFix? = null,
)

/**
 * Everything Claude can do on the phone, as tools for the Anthropic API. Each one is a plain
 * Android route a launcher may take (no root, no hidden APIs except the ones Hearth already
 * uses); none of them throws. Calls and messages only open the ready-made screen: the user
 * sends them.
 */
class ClaudeTools(private val context: Context, private val host: () -> AssistantHost?) {

    private val controls by lazy { SystemControls(context) }
    private val audio get() = context.getSystemService(AudioManager::class.java)

    // The tools as Claude sees them

    val schema: JSONArray by lazy {
        JSONArray().apply {
            put(tool("open_app", "Öffnet eine installierte App über ihren Namen (unscharf, z. B. „Spotify“, „Kamera“, „WhatsApp“).",
                props("name" to str("Name der App")), "name"))
            put(tool("list_apps", "Listet installierte Apps, optional gefiltert. Nutze es, wenn du den genauen Namen nicht kennst.",
                props("query" to str("Optionaler Filter"))))
            put(tool("app_settings", "Öffnet die Einstellungen einer App: Infos (Berechtigungen, Speicher, Deinstallieren) oder ihre Benachrichtigungen.",
                props("name" to str("Name der App"), "page" to str("Welche Seite", listOf("info", "notifications"))), "name"))
            put(tool("flashlight", "Schaltet die Taschenlampe an oder aus.", props("on" to bool("an (true) oder aus (false)")), "on"))
            put(tool("brightness", "Setzt die Bildschirmhelligkeit in Prozent und/oder die automatische Helligkeit.",
                props("percent" to int("0–100"), "auto" to bool("automatische Helligkeit"))))
            put(tool("volume", "Setzt eine Lautstärke in Prozent.",
                props("percent" to int("0–100"), "stream" to str("Welche Lautstärke (Standard: media)", listOf("media", "ring", "notification", "alarm", "call"))), "percent"))
            put(tool("sound_mode", "Klingelmodus: Ton, Vibration oder lautlos.",
                props("mode" to str("Modus", listOf("normal", "vibrate", "silent"))), "mode"))
            put(tool("do_not_disturb", "Schaltet „Nicht stören“ an oder aus.", props("on" to bool("an oder aus")), "on"))
            put(tool("auto_rotate", "Schaltet das automatische Drehen des Bildschirms an oder aus.", props("on" to bool("an oder aus")), "on"))
            put(tool("set_alarm", "Stellt einen Wecker in der Uhr-App, ohne Nachfrage.",
                props(
                    "hour" to int("Stunde 0–23"),
                    "minute" to int("Minute 0–59"),
                    "label" to str("Optionaler Name"),
                    "days" to JSONObject().put("type", "array").put("description", "Optional: Wochentage zum Wiederholen, 1 = Montag … 7 = Sonntag")
                        .put("items", JSONObject().put("type", "integer")),
                ), "hour", "minute"))
            put(tool("set_timer", "Startet einen Timer in der Uhr-App.",
                props("seconds" to int("Dauer in Sekunden"), "label" to str("Optionaler Name")), "seconds"))
            put(tool("show_alarms", "Öffnet die Wecker-Liste der Uhr-App.", props()))
            put(tool("calendar_event", "Öffnet einen fertig ausgefüllten neuen Kalendertermin; der Nutzer speichert ihn.",
                props(
                    "title" to str("Titel"),
                    "start" to str("Beginn, lokale Zeit, ISO 8601 (z. B. 2026-10-03T14:00) oder nur Datum für ganztägig"),
                    "end" to str("Optionales Ende, ISO 8601"),
                    "location" to str("Optionaler Ort"),
                    "description" to str("Optionale Notiz"),
                ), "title", "start"))
            put(tool("find_contact", "Sucht Telefonnummern und E-Mails in den Kontakten über einen Namen.",
                props("name" to str("Name oder Teil davon")), "name"))
            put(tool("call", "Öffnet das Telefon mit der Nummer; der Nutzer tippt auf Anrufen.",
                props("number" to str("Telefonnummer")), "number"))
            put(tool("send_message", "Öffnet eine fertige Nachricht (SMS, oder in einer App wie WhatsApp, Telegram, Signal); der Nutzer sendet sie.",
                props("text" to str("Text"), "number" to str("Optionale Telefonnummer"), "app" to str("Optionale App, z. B. WhatsApp")), "text"))
            put(tool("email", "Öffnet eine fertige E-Mail; der Nutzer sendet sie.",
                props("to" to str("Optionale Adresse"), "subject" to str("Betreff"), "body" to str("Text"))))
            put(tool("navigate", "Startet die Navigation zu einem Ziel.",
                props("destination" to str("Ziel (Adresse oder Ort)"), "mode" to str("Optional", listOf("driving", "walking", "bicycling", "transit"))), "destination"))
            put(tool("show_map", "Zeigt einen Ort oder eine Suche (z. B. „Pizza in der Nähe“) auf der Karte.",
                props("query" to str("Ort oder Suche")), "query"))
            put(tool("web_search", "Sucht im Web mit der Suchmaschine des Nutzers.", props("query" to str("Suchbegriff")), "query"))
            put(tool("open_url", "Öffnet eine Webseite (nur http/https).", props("url" to str("Adresse")), "url"))
            put(tool("media", "Steuert die Musik oder das Video, das gerade läuft oder zuletzt lief.",
                props("action" to str("Aktion", listOf("play_pause", "play", "pause", "next", "previous"))), "action"))
            put(tool("system_action", "Systemaktion: Bildschirm sperren, Bildschirmfoto, Benachrichtigungen, Schnelleinstellungen, Ausschaltmenü, Zurück, Startbildschirm, Letzte Apps, Geteilter Bildschirm.",
                props("action" to str("Aktion", listOf("lock_screen", "screenshot", "notifications", "quick_settings", "power_menu", "back", "home", "recents", "split_screen"))), "action"))
            put(tool("open_settings", "Öffnet eine Seite der Systemeinstellungen.",
                props("page" to str("Seite", SettingsPages.keys.toList())), "page"))
            put(tool("copy_text", "Kopiert Text in die Zwischenablage.", props("text" to str("Text")), "text"))
            put(tool("device_status", "Liest den Zustand des Handys: Uhrzeit, Akku, Laden, nächster Wecker, Ton, Nicht stören, WLAN, Bluetooth, Helligkeit, Lautstärke, Speicher.", props()))
            put(tool("launcher", "Hearth (der Launcher): Design-Vorlage anwenden, Launcher-Einstellungen oder Hintergrundbild öffnen.",
                props(
                    "action" to str("Aktion", listOf("design", "settings", "wallpaper")),
                    "design" to str("Für action=design", DesignPreset.entries.map { it.name }),
                ), "action"))
            // The tools rarely change: cached on Anthropic's side, cheaper and faster.
            optJSONObject(length() - 1)?.put("cache_control", JSONObject().put("type", "ephemeral"))
        }
    }

    // Running a tool

    suspend fun execute(name: String, input: JSONObject): ToolResult = try {
        dispatch(name, input)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        ToolResult(false, "Fehler: ${t.javaClass.simpleName} ${t.message.orEmpty()}".trim(), label = "$name fehlgeschlagen")
    }

    private suspend fun dispatch(name: String, a: JSONObject): ToolResult = when (name) {
        "open_app" -> openApp(a.optString("name"))
        "list_apps" -> listApps(a.optString("query"))
        "app_settings" -> appSettings(a.optString("name"), a.optString("page", "info"))
        "flashlight" -> flashlight(a.optBoolean("on", true))
        "brightness" -> brightness(a.optIntOrNull("percent"), a.optBooleanOrNull("auto"))
        "volume" -> volume(a.optInt("percent", 50), a.optString("stream", "media"))
        "sound_mode" -> soundMode(a.optString("mode"))
        "do_not_disturb" -> doNotDisturb(a.optBoolean("on", true))
        "auto_rotate" -> autoRotate(a.optBoolean("on", true))
        "set_alarm" -> setAlarm(a.optInt("hour", -1), a.optInt("minute", 0), a.optString("label"), a.optJSONArray("days"))
        "set_timer" -> setTimer(a.optInt("seconds", 0), a.optString("label"))
        "show_alarms" -> openScreen("Wecker geöffnet", Intent(AlarmClock.ACTION_SHOW_ALARMS))
        "calendar_event" -> calendarEvent(a)
        "find_contact" -> findContact(a.optString("name"))
        "call" -> call(a.optString("number"))
        "send_message" -> sendMessage(a.optString("text"), a.optString("number"), a.optString("app"))
        "email" -> email(a.optString("to"), a.optString("subject"), a.optString("body"))
        "navigate" -> navigate(a.optString("destination"), a.optString("mode"))
        "show_map" -> openScreen("Karte: ${a.optString("query")}", Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.optString("query")))))
        "web_search" -> webSearch(a.optString("query"))
        "open_url" -> openUrl(a.optString("url"))
        "media" -> media(a.optString("action"))
        "system_action" -> systemAction(a.optString("action"))
        "open_settings" -> openSettings(a.optString("page"))
        "copy_text" -> copyText(a.optString("text"))
        "device_status" -> ToolResult(true, deviceStatus())
        "launcher" -> launcher(a.optString("action"), a.optString("design"))
        else -> ToolResult(false, "Unbekanntes Werkzeug $name")
    }

    // Apps

    private var appCache: List<LauncherActivityInfo> = emptyList()
    private var appCacheTime = 0L

    private fun apps(): List<LauncherActivityInfo> {
        val now = System.currentTimeMillis()
        if (appCache.isNotEmpty() && now - appCacheTime < 30_000) return appCache
        appCache = runCatching {
            val launcher = context.getSystemService(LauncherApps::class.java)
            val users = context.getSystemService(UserManager::class.java).userProfiles
            users.flatMap { launcher.getActivityList(null, it) }
                .filter { it.componentName.packageName != context.packageName }
        }.getOrDefault(emptyList())
        appCacheTime = now
        return appCache
    }

    /** The best app for a spoken or typed name; null when nothing fits well enough. */
    fun findApp(name: String, strict: Boolean = false): LauncherActivityInfo? {
        val q = fold(name)
        if (q.isEmpty()) return null
        val scored = apps().map { it to score(fold(it.label.toString()), q, fold(it.componentName.packageName)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        return best.first.takeIf { best.second >= if (strict) 80 else 50 }
    }

    private fun score(label: String, q: String, pkg: String): Int = when {
        label == q -> 100
        label.replace(" ", "") == q.replace(" ", "") -> 95
        label.startsWith(q) -> 85
        label.split(' ').any { it == q } -> 80
        label.contains(q) -> 65
        q.contains(label) && label.length >= 4 -> 55
        pkg.contains(q.replace(" ", "")) && q.length >= 4 -> 50
        else -> 0
    }

    private fun openApp(name: String): ToolResult {
        val app = findApp(name)
            ?: return ToolResult(false, "Keine App passt zu „$name“. Ähnliche: ${similar(name)}", label = "„$name“ nicht gefunden")
        val label = app.label.toString()
        val ok = runCatching {
            context.getSystemService(LauncherApps::class.java).startMainActivity(app.componentName, app.user, null, null)
        }.isSuccess
        return if (ok) {
            ToolResult(true, "$label geöffnet.", label = "$label geöffnet", say = "Ich öffne $label.", opensScreen = true)
        } else {
            ToolResult(false, "$label ließ sich nicht öffnen.", label = "$label ließ sich nicht öffnen")
        }
    }

    private fun similar(name: String): String {
        val q = fold(name).take(3)
        val list = apps().map { it.label.toString() }.filter { q.isNotEmpty() && fold(it).contains(q) }.distinct().take(8)
        return list.joinToString().ifEmpty { "keine (list_apps zeigt alle)" }
    }

    private fun listApps(query: String): ToolResult {
        val q = fold(query)
        val names = apps().map { it.label.toString() }.distinct()
            .filter { q.isEmpty() || fold(it).contains(q) }
            .sortedBy { it.lowercase() }
        return ToolResult(true, if (names.isEmpty()) "Keine passende App." else names.take(250).joinToString())
    }

    private fun appSettings(name: String, page: String): ToolResult {
        val app = findApp(name) ?: return ToolResult(false, "Keine App passt zu „$name“.", label = "„$name“ nicht gefunden")
        val pkg = app.componentName.packageName
        val label = app.label.toString()
        return if (page == "notifications") {
            openScreen(
                "Benachrichtigungen von $label",
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
            )
        } else {
            openScreen("App-Infos von $label", Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
    }

    // Phone controls

    private fun flashlight(on: Boolean): ToolResult {
        if (!controls.hasTorch) return ToolResult(false, "Dieses Handy hat keine Taschenlampe.", label = "Keine Taschenlampe")
        controls.setTorch(on)
        val text = if (on) "Taschenlampe an" else "Taschenlampe aus"
        return ToolResult(true, "$text.", label = text, say = "$text.")
    }

    private fun brightness(percent: Int?, auto: Boolean?): ToolResult {
        if (!Settings.System.canWrite(context)) {
            controls.requestWriteSettings()
            return ToolResult(
                false,
                "Hearth darf die Systemeinstellungen noch nicht ändern. Der Nutzer muss „Systemeinstellungen ändern“ für Hearth erlauben (der Bildschirm dafür ist jetzt offen).",
                label = "Erlaubnis fehlt: Systemeinstellungen ändern",
                opensScreen = true,
                fix = ClaudeFix.WriteSettings,
            )
        }
        val parts = mutableListOf<String>()
        if (auto != null) {
            controls.setAutoBrightness(auto)
            parts += if (auto) "Auto-Helligkeit an" else "Auto-Helligkeit aus"
        }
        if (percent != null) {
            val p = percent.coerceIn(0, 100)
            controls.setBrightness(p / 100f)
            parts += "Helligkeit $p %"
        }
        if (parts.isEmpty()) return ToolResult(false, "Weder percent noch auto angegeben.")
        val text = parts.joinToString(", ")
        return ToolResult(true, "$text.", label = text, say = "$text.")
    }

    private fun volume(percent: Int, stream: String): ToolResult {
        val (type, name) = when (stream) {
            "ring" -> AudioManager.STREAM_RING to "Klingelton"
            "notification" -> AudioManager.STREAM_NOTIFICATION to "Benachrichtigungen"
            "alarm" -> AudioManager.STREAM_ALARM to "Wecker"
            "call" -> AudioManager.STREAM_VOICE_CALL to "Anrufe"
            else -> AudioManager.STREAM_MUSIC to "Medien"
        }
        val p = percent.coerceIn(0, 100)
        controls.setStreamLevel(type, p / 100f)
        val now = (controls.streamLevel(type) * 100).roundToInt()
        val text = "Lautstärke $name $now %"
        // "Do not disturb" can hold the ringer volume back.
        val ok = kotlin.math.abs(now - p) <= 15 || p == 0
        return ToolResult(ok, if (ok) "$text." else "$text (System hat $p % nicht zugelassen, evtl. wegen Nicht stören).", label = text, say = "$text.")
    }

    private fun soundMode(mode: String): ToolResult {
        val (value, text) = when (mode) {
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE to "Vibration"
            "silent" -> AudioManager.RINGER_MODE_SILENT to "Lautlos"
            else -> AudioManager.RINGER_MODE_NORMAL to "Ton an"
        }
        if (value == AudioManager.RINGER_MODE_SILENT && !controls.hasDoNotDisturbAccess()) {
            controls.setRingerMode(value) // opens the access screen
            return ToolResult(
                false,
                "Für Lautlos braucht Hearth Zugriff auf „Nicht stören“. Der Bildschirm dafür ist jetzt offen.",
                label = "Erlaubnis fehlt: Nicht stören",
                opensScreen = true,
                fix = ClaudeFix.DoNotDisturb,
            )
        }
        controls.setRingerMode(value)
        val ok = controls.ringerMode() == value
        return ToolResult(ok, if (ok) "$text." else "Das System hat den Modus nicht geändert.", label = text, say = "$text.")
    }

    private fun doNotDisturb(on: Boolean): ToolResult {
        if (!controls.hasDoNotDisturbAccess()) {
            controls.setDoNotDisturb(on) // opens the access screen
            return ToolResult(
                false,
                "Hearth braucht Zugriff auf „Nicht stören“. Der Bildschirm dafür ist jetzt offen; danach noch einmal fragen.",
                label = "Erlaubnis fehlt: Nicht stören",
                opensScreen = true,
                fix = ClaudeFix.DoNotDisturb,
            )
        }
        val ok = controls.setDoNotDisturb(on)
        val text = if (on) "Nicht stören an" else "Nicht stören aus"
        return ToolResult(ok, if (ok) "$text." else "Ging nicht.", label = text, say = "$text.")
    }

    private fun autoRotate(on: Boolean): ToolResult {
        if (!Settings.System.canWrite(context)) {
            controls.requestWriteSettings()
            return ToolResult(false, "Erlaubnis „Systemeinstellungen ändern“ fehlt; der Bildschirm ist offen.", label = "Erlaubnis fehlt", opensScreen = true, fix = ClaudeFix.WriteSettings)
        }
        val ok = controls.setAutoRotate(on)
        val text = if (on) "Automatisch drehen an" else "Automatisch drehen aus"
        return ToolResult(ok, "$text.", label = text, say = "$text.")
    }

    // Clock

    private fun setAlarm(hour: Int, minute: Int, label: String, days: JSONArray?): ToolResult {
        if (hour !in 0..23 || minute !in 0..59) return ToolResult(false, "Ungültige Uhrzeit $hour:$minute.")
        val time = String.format(Locale.GERMAN, "%d:%02d", hour, minute)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        val weekdays = days?.let { arr ->
            // 1 = Monday … 7 = Sunday → Calendar's days (Sunday = 1).
            ArrayList((0 until arr.length()).mapNotNull { i ->
                when (arr.optInt(i)) {
                    1 -> Calendar.MONDAY; 2 -> Calendar.TUESDAY; 3 -> Calendar.WEDNESDAY; 4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY; 6 -> Calendar.SATURDAY; 7 -> Calendar.SUNDAY; else -> null
                }
            }.distinct())
        }
        if (!weekdays.isNullOrEmpty()) intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, weekdays)
        val ok = startQuietly(intent)
        val text = "Wecker $time" + if (label.isNotBlank()) " „$label“" else ""
        return if (ok) {
            ToolResult(true, "$text gestellt.", label = "$text gestellt", say = "Wecker für $time Uhr ist gestellt.")
        } else {
            ToolResult(false, "Keine Uhr-App, die Wecker stellen kann.", label = "Wecker ging nicht")
        }
    }

    private fun setTimer(seconds: Int, label: String): ToolResult {
        if (seconds !in 1..86_400) return ToolResult(false, "Ungültige Dauer: $seconds s.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        val ok = startQuietly(intent)
        val text = "Timer " + duration(seconds)
        return if (ok) {
            ToolResult(true, "$text läuft.", label = "$text gestartet", say = "$text läuft.")
        } else {
            ToolResult(false, "Keine Uhr-App, die Timer stellen kann.", label = "Timer ging nicht")
        }
    }

    private fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(
            if (h > 0) "$h Std." else null,
            if (m > 0) "$m Min." else null,
            if (s > 0) "$s Sek." else null,
        ).joinToString(" ")
    }

    // Calendar, contacts, messages

    private fun calendarEvent(a: JSONObject): ToolResult {
        val title = a.optString("title").ifBlank { return ToolResult(false, "Titel fehlt.") }
        val zone = ZoneId.systemDefault()
        val startText = a.optString("start")
        val allDay = startText.length <= 10
        val start = parseTime(startText, zone) ?: return ToolResult(false, "Beginn „$startText“ nicht lesbar; nutze ISO 8601.")
        val end = parseTime(a.optString("end"), zone)?.takeIf { it > start }
            ?: (start + if (allDay) 86_400_000L else 3_600_000L)
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        a.optString("location").takeIf { it.isNotBlank() }?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        a.optString("description").takeIf { it.isNotBlank() }?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        return openScreen("Termin „$title“ – bitte speichern", intent)
    }

    private fun parseTime(text: String, zone: ZoneId): Long? {
        if (text.isBlank()) return null
        val t = text.trim().removeSuffix("Z")
        return runCatching { LocalDateTime.parse(t.take(19)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t.take(16)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(t.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    private suspend fun findContact(name: String): ToolResult {
        if (name.isBlank()) return ToolResult(false, "Name fehlt.")
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                false,
                "Hearth darf die Kontakte noch nicht lesen. Der Nutzer kann es mit dem Knopf „Kontakte erlauben“ freigeben, dann noch einmal fragen.",
                label = "Erlaubnis fehlt: Kontakte",
                fix = ClaudeFix.Contacts,
            )
        }
        val lines = withContext(Dispatchers.IO) {
            runCatching {
                val out = linkedSetOf<String>()
                val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                context.contentResolver.query(
                    phone,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$name%"),
                    null,
                )?.use { c ->
                    while (c.moveToNext() && out.size < 12) {
                        val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(2), "").toString()
                        out += "${c.getString(0)}: ${c.getString(1)}" + if (type.isNotBlank()) " ($type)" else ""
                    }
                }
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
                    "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIKE ?",
                    arrayOf("%$name%"),
                    null,
                )?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n++ < 6) out += "${c.getString(0)}: ${c.getString(1)} (E-Mail)"
                }
                out.toList()
            }.getOrDefault(emptyList())
        }
        return if (lines.isEmpty()) {
            ToolResult(true, "Kein Kontakt passt zu „$name“.")
        } else {
            ToolResult(true, lines.joinToString("\n"))
        }
    }

    private fun call(number: String): ToolResult {
        val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (clean.length < 3) return ToolResult(false, "Keine gültige Nummer: „$number“.")
        return openScreen("Anruf an $clean – tippe auf Anrufen", Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(clean))))
    }

    private fun sendMessage(text: String, number: String, appName: String): ToolResult {
        val clean = number.filter { it.isDigit() || it == '+' }
        val app = appName.takeIf { it.isNotBlank() }?.let { findApp(it) }
        val pkg = app?.componentName?.packageName
        if (appName.isNotBlank() && pkg == null) {
            return ToolResult(false, "App „$appName“ ist nicht installiert.", label = "„$appName“ nicht gefunden")
        }
        if (pkg != null && pkg.startsWith("com.whatsapp") && clean.isNotEmpty()) {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=${clean.removePrefix("+")}&text=" + Uri.encode(text))
            return openScreen("WhatsApp-Nachricht bereit – tippe auf Senden", Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
        }
        if (pkg != null) {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg)
            return openScreen("Nachricht in ${app?.label ?: appName} bereit – Empfänger wählen und senden", share)
        }
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(clean))).putExtra("sms_body", text)
        return openScreen(if (clean.isEmpty()) "SMS bereit – Empfänger wählen" else "SMS an $clean bereit – tippe auf Senden", sms)
    }

    private fun email(to: String, subject: String, body: String): ToolResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        if (to.isNotBlank()) intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        return openScreen("E-Mail bereit – tippe auf Senden", intent)
    }

    // Maps and web

    private fun navigate(destination: String, mode: String): ToolResult {
        if (destination.isBlank()) return ToolResult(false, "Ziel fehlt.")
        val m = when (mode) {
            "walking" -> "w"; "bicycling" -> "b"; "transit" -> "r"; else -> "d"
        }
        return openScreen(
            "Navigation nach $destination",
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=" + m)),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))),
        )
    }

    private fun webSearch(query: String): ToolResult {
        if (query.isBlank()) return ToolResult(false, "Suchbegriff fehlt.")
        val launcher = host()
        if (launcher != null) {
            launcher.webSearch(query)
            return ToolResult(true, "Websuche geöffnet.", label = "Websuche: $query", opensScreen = true)
        }
        return openScreen("Websuche: $query", Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    private fun openUrl(url: String): ToolResult {
        val uri = Uri.parse(url.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" })
        if (uri.scheme != "https" && uri.scheme != "http") return ToolResult(false, "Nur Webadressen.")
        return openScreen("${uri.host ?: url} geöffnet", Intent(Intent.ACTION_VIEW, uri))
    }

    // Media and system

    private fun media(action: String): ToolResult {
        val (key, text) = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY to "Wiedergabe"
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE to "Pause"
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT to "Nächster Titel"
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Vorheriger Titel"
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause"
        }
        controls.mediaKey(key)
        return ToolResult(true, "$text gesendet.", label = text, say = "$text.")
    }

    private fun systemAction(action: String): ToolResult {
        fun needsService(ok: Boolean, text: String, screen: Boolean = false): ToolResult = if (ok) {
            ToolResult(true, "$text.", label = text, say = "$text.", opensScreen = screen)
        } else {
            ToolResult(
                false,
                "Dafür muss Glimmer oder das Kontrollzentrum als Bedienungshilfe an sein. Die Einstellungen dafür sind offen.",
                label = "Erlaubnis fehlt: Bedienungshilfe",
                opensScreen = true,
                fix = ClaudeFix.Accessibility,
            )
        }
        return when (action) {
            // The assistant gets out of the picture before the screenshot.
            "screenshot" -> needsService(controls.takeScreenshot(900), "Bildschirmfoto", screen = true)
            "lock_screen" -> needsService(controls.lockScreen(), "Bildschirm gesperrt", screen = true)
            "power_menu" -> needsService(controls.powerMenu(), "Ausschaltmenü", screen = true)
            "back" -> needsService(controls.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK), "Zurück", screen = true)
            "home" -> needsService(controls.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME), "Startbildschirm", screen = true)
            "recents" -> needsService(controls.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS), "Letzte Apps", screen = true)
            "split_screen" -> needsService(controls.performGlobal(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN), "Geteilter Bildschirm", screen = true)
            "notifications" -> ToolResult(controls.expandNotifications(), "Benachrichtigungen.", label = "Benachrichtigungen", opensScreen = true)
            "quick_settings" -> ToolResult(controls.expandQuickSettings(), "Schnelleinstellungen.", label = "Schnelleinstellungen", opensScreen = true)
            else -> ToolResult(false, "Unbekannte Aktion $action.")
        }
    }

    private fun openSettings(page: String): ToolResult {
        val target = SettingsPages[page] ?: SettingsPages.getValue("main")
        return openScreen(target.first, *target.second.map { Intent(it) }.toTypedArray(), Intent(Settings.ACTION_SETTINGS))
    }

    private fun copyText(text: String): ToolResult {
        val clip = context.getSystemService(ClipboardManager::class.java) ?: return ToolResult(false, "Keine Zwischenablage.")
        clip.setPrimaryClip(ClipData.newPlainText("Claude", text))
        return ToolResult(true, "Kopiert.", label = "In die Zwischenablage kopiert", say = "Kopiert.")
    }

    fun currentBrightness(): Float = controls.brightness()
    fun currentVolume(): Float = controls.volume()

    /** The phone at a glance, as Claude reads it. */
    fun deviceStatus(): String {
        val now = Date()
        val lines = mutableListOf<String>()
        lines += "Zeit: " + SimpleDateFormat("EEEE, d. MMMM yyyy, HH:mm", Locale.GERMAN).format(now)
        runCatching {
            val bm = context.getSystemService(BatteryManager::class.java)
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            lines += "Akku: $level %" + if (bm.isCharging) ", lädt" else ""
        }
        runCatching {
            val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
            lines += "Nächster Wecker: " + (next?.let { SimpleDateFormat("EEEE HH:mm", Locale.GERMAN).format(Date(it.triggerTime)) } ?: "keiner")
        }
        runCatching {
            val s = controls.state()
            lines += "Ton: " + when (controls.ringerMode()) {
                AudioManager.RINGER_MODE_SILENT -> "lautlos"; AudioManager.RINGER_MODE_VIBRATE -> "Vibration"; else -> "an"
            }
            lines += "Nicht stören: " + (if (s.doNotDisturb) "an" else "aus")
            lines += "WLAN: " + (if (s.wifi) "an" else "aus") + ", Mobile Daten aktiv: " + (if (s.mobileData) "ja" else "nein")
            lines += "Bluetooth: " + (if (s.bluetooth) "an" else "aus") + ", Flugmodus: " + (if (s.airplane) "an" else "aus")
            lines += "Standort: " + (if (s.location) "an" else "aus") + ", Energiesparen: " + (if (s.batterySaver) "an" else "aus")
            lines += "Helligkeit: ${(controls.brightness() * 100).roundToInt()} %" + if (s.autoBrightness) " (automatisch)" else ""
            lines += "Dunkelmodus: " + (if (s.darkMode) "an" else "aus") + ", Auto-Drehen: " + (if (s.autoRotate) "an" else "aus")
        }
        runCatching {
            lines += "Medienlautstärke: ${(controls.volume() * 100).roundToInt()} %" + if (audio?.isMusicActive == true) ", Musik läuft" else ""
            lines += "Taschenlampe: " + (if (controls.torchOn.value) "an" else "aus")
        }
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            lines += "Speicher frei: %.1f GB von %.1f GB".format(Locale.GERMAN, stat.availableBytes / 1e9, stat.totalBytes / 1e9)
        }
        return lines.joinToString("\n")
    }

    private fun launcher(action: String, design: String): ToolResult = when (action) {
        "design" -> {
            val preset = DesignPreset.entries.firstOrNull { it.name.equals(design, ignoreCase = true) }
            val launcher = host()
            when {
                preset == null -> ToolResult(false, "Unbekannte Vorlage „$design“. Möglich: ${DesignPreset.entries.joinToString { it.name }}")
                launcher == null -> ToolResult(false, "Hearth läuft gerade nicht im Hintergrund; öffne den Startbildschirm und frag noch einmal.")
                else -> {
                    launcher.applyPreset(preset)
                    ToolResult(true, "Design „${preset.label}“ angewendet.", label = "Design: ${preset.label}", say = "Hearth sieht jetzt aus wie ${preset.label}.")
                }
            }
        }
        "wallpaper" -> {
            val launcher = host()
            if (launcher != null) {
                launcher.openWallpaperPicker()
                ToolResult(true, "Hintergrundauswahl offen.", label = "Hintergrundbild wählen", opensScreen = true)
            } else {
                openScreen("Hintergrundbild wählen", Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild wählen"))
            }
        }
        else -> openScreen(
            "Hearth-Einstellungen",
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
        )
    }

    // Helpers

    /** Starts the first intent that works; the assistant makes way for it. */
    private fun openScreen(label: String, vararg intents: Intent): ToolResult {
        val ok = intents.any { startQuietly(it) }
        return if (ok) {
            ToolResult(true, "$label.", label = label, opensScreen = true)
        } else {
            ToolResult(false, "Keine App auf dem Handy kann das öffnen.", label = "$label – ging nicht")
        }
    }

    private fun startQuietly(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    private fun tool(name: String, description: String, properties: JSONObject, vararg required: String): JSONObject {
        val schema = JSONObject().put("type", "object").put("properties", properties)
        if (required.isNotEmpty()) schema.put("required", JSONArray(required.toList()))
        return JSONObject().put("name", name).put("description", description).put("input_schema", schema)
    }

    private fun props(vararg entries: Pair<String, JSONObject>): JSONObject =
        JSONObject().apply { entries.forEach { (k, v) -> put(k, v) } }

    private fun str(description: String, options: List<String>? = null): JSONObject =
        JSONObject().put("type", "string").put("description", description).apply {
            if (options != null) put("enum", JSONArray(options))
        }

    private fun int(description: String) = JSONObject().put("type", "integer").put("description", description)
    private fun bool(description: String) = JSONObject().put("type", "boolean").put("description", description)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }?.roundToInt() else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    companion object {
        /** Lower case, without accents and punctuation, for matching spoken names. */
        fun fold(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex(" +"), " ")
            .trim()

        /** System settings pages Claude may open: name → (label, intent actions to try). */
        val SettingsPages: Map<String, Pair<String, List<String>>> = linkedMapOf(
            "main" to ("Einstellungen" to listOf(Settings.ACTION_SETTINGS)),
            "wifi" to ("WLAN" to listOf(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)),
            "internet" to ("Internet" to listOf(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)),
            "bluetooth" to ("Bluetooth" to listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
            "mobile_data" to ("Mobile Daten" to listOf(Settings.ACTION_DATA_ROAMING_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)),
            "airplane" to ("Flugmodus" to listOf(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
            "hotspot" to ("Hotspot" to listOf(Settings.ACTION_WIRELESS_SETTINGS)),
            "nfc" to ("NFC" to listOf(Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS)),
            "location" to ("Standort" to listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
            "display" to ("Anzeige" to listOf(Settings.ACTION_DISPLAY_SETTINGS)),
            "sound" to ("Töne" to listOf(Settings.ACTION_SOUND_SETTINGS)),
            "volume" to ("Lautstärke" to listOf(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)),
            "battery" to ("Akku" to listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS)),
            "battery_saver" to ("Energiesparen" to listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
            "notifications" to ("Benachrichtigungen" to listOf("android.settings.NOTIFICATION_SETTINGS", Settings.ACTION_SETTINGS)),
            "do_not_disturb" to ("Nicht stören" to listOf("android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS)),
            "apps" to ("Apps" to listOf(Settings.ACTION_APPLICATION_SETTINGS)),
            "default_apps" to ("Standard-Apps" to listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
            "storage" to ("Speicher" to listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            "security" to ("Sicherheit" to listOf(Settings.ACTION_SECURITY_SETTINGS)),
            "privacy" to ("Datenschutz" to listOf(Settings.ACTION_PRIVACY_SETTINGS)),
            "accessibility" to ("Bedienungshilfen" to listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            "language" to ("Sprache" to listOf(Settings.ACTION_LOCALE_SETTINGS)),
            "date_time" to ("Datum & Uhrzeit" to listOf(Settings.ACTION_DATE_SETTINGS)),
            "accounts" to ("Konten" to listOf(Settings.ACTION_SYNC_SETTINGS)),
            "developer" to ("Entwickleroptionen" to listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
            "about" to ("Telefoninfo" to listOf(Settings.ACTION_DEVICE_INFO_SETTINGS)),
            "cast" to ("Bildschirm übertragen" to listOf(Settings.ACTION_CAST_SETTINGS)),
            "assistant" to ("Digitaler Assistent" to listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
            "home_app" to ("Standard-Startbildschirm" to listOf(Settings.ACTION_HOME_SETTINGS)),
        )
    }
}
