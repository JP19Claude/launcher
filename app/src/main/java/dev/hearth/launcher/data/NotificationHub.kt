package dev.hearth.launcher.data

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a notification is about, as far as Glimmer cares. */
enum class NoticeKind { Call, Alarm, Navigation, Timer, Recording, Hotspot, Update, Progress, Message }

@Immutable
class NoticeAction(val title: String, val intent: PendingIntent)

/** A notification boiled down to what Glimmer shows. */
@Immutable
class LiveNotice(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val kind: NoticeKind,
    /** For timers and calls: the chronometer base (wall clock), and whether it counts down. */
    val chronometerBase: Long,
    val countDown: Boolean,
    val progress: Int,
    val progressMax: Int,
    val indeterminate: Boolean,
    val icon: ImageBitmap?,
    val contentIntent: PendingIntent?,
    val actions: List<NoticeAction>,
    /** Stopwatch or timer on hold: [pausedMs] is the time to show, standing still. */
    val paused: Boolean = false,
    val pausedMs: Long = 0L,
    /** False when the notification gives no time at all (then Glimmer shows a dot). */
    val hasTime: Boolean = true,
    /** A stopwatch counting up (rather than a timer counting down). */
    val stopwatch: Boolean = false,
    /** Android 16 live updates: the app's own short text for the island ("5 min", "2 Stopps"). */
    val shortText: String? = null,
) {
    /** The time to show right now (ms), or null without one. */
    fun timeMs(now: Long): Long? = when {
        !hasTime -> null
        paused -> pausedMs
        countDown -> (chronometerBase - now).coerceAtLeast(0L)
        else -> (now - chronometerBase).coerceAtLeast(0L)
    }
}

/** A notification as listed on the control center's notification page. */
@Immutable
class ShadeNotice(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: ImageBitmap?,
    val contentIntent: PendingIntent?,
    val actions: List<NoticeAction>,
    val clearable: Boolean,
    val autoCancel: Boolean,
    /** [Notification.visibility]: what may show on the lock screen. */
    val visibility: Int = Notification.VISIBILITY_PRIVATE,
    /** The app's own lock screen version of the text, if it gave one. */
    val publicText: String? = null,
)

/**
 * Collects live notifications (calls, timers, navigation, downloads) and new messages for
 * Glimmer. Filled by the notification listener, which needs "notification access".
 */
object NotificationHub {

    private val _active = MutableStateFlow<List<LiveNotice>>(emptyList())

    /** Ongoing things: calls, timers, navigation, progress. */
    val active: StateFlow<List<LiveNotice>> = _active.asStateFlow()

    private val _incoming = MutableSharedFlow<LiveNotice>(extraBufferCapacity = 8)

    /** New, important messages, to flash briefly. */
    val incoming: SharedFlow<LiveNotice> = _incoming.asSharedFlow()

    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Unread notifications per app package, for the badges on the icons. */
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    private val _all = MutableStateFlow<List<ShadeNotice>>(emptyList())

    /** Every notification worth listing, newest first, for the control center. */
    val all: StateFlow<List<ShadeNotice>> = _all.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /** Whether Hearth has notification access right now. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var listener: NotificationListenerService? = null

    private val iconCache = HashMap<String, ImageBitmap?>()

    fun refresh(service: NotificationListenerService) {
        listener = service
        _connected.value = true
        val all = runCatching { service.activeNotifications }.getOrNull().orEmpty()
        // Group summaries only repeat their children; keep one only if it stands alone.
        val groupsWithChildren = all
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.groupKey }
            .toSet()
        _all.value = all
            .filter { sbn ->
                sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 || sbn.groupKey !in groupsWithChildren
            }
            .mapNotNull { shadeNotice(service, it) }
            .sortedByDescending { it.time }
        // One activity per app and kind (some apps post a summary next to the real one).
        _active.value = all.mapNotNull { parse(service, it) }
            .filter { it.kind != NoticeKind.Message }
            .distinctBy { it.packageName + it.kind.name }
        _badges.value = all
            .filter { sbn ->
                val n = sbn.notification
                sbn.packageName != service.packageName &&
                    !sbn.isOngoing &&
                    n.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.notification.number.coerceAtLeast(1) } }
    }

    fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification) {
        refresh(service)
        val notice = parse(service, sbn) ?: return
        if (notice.kind != NoticeKind.Message || sbn.isOngoing) return
        val ranking = NotificationListenerService.Ranking()
        val important = runCatching {
            service.currentRanking.getRanking(sbn.key, ranking) &&
                ranking.importance >= NotificationManager.IMPORTANCE_HIGH
        }.getOrDefault(false)
        val alertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        if (important && !alertOnce) _incoming.tryEmit(notice)
    }

    fun clear() {
        listener = null
        _connected.value = false
        _active.value = emptyList()
        _all.value = emptyList()
        _badges.value = emptyMap()
    }

    /** Swiped away in the control center. */
    fun dismiss(key: String) {
        val service = listener ?: return
        runCatching { service.cancelNotification(key) }
        _all.value = _all.value.filterNot { it.key == key }
    }

    /** "Clear all": everything that can be cleared. */
    fun dismissAll() {
        val service = listener ?: return
        runCatching { service.cancelAllNotifications() }
        _all.value = _all.value.filterNot { it.clearable }
    }

    /** Opens what the notification points to, like tapping it in the system shade. */
    fun open(context: Context, notice: ShadeNotice): Boolean {
        val intent = notice.contentIntent
        val sent = if (intent != null) {
            send(context, intent)
        } else {
            context.packageManager.getLaunchIntentForPackage(notice.packageName)?.let { launch ->
                runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            } ?: false
        }
        if (sent && notice.autoCancel && notice.clearable) dismiss(notice.key)
        return sent
    }

    /** Sends a notification's intent; Android 14+ wants the sender to allow starting a screen. */
    fun send(context: Context, intent: PendingIntent): Boolean {
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        return runCatching { intent.send(context, 0, null, null, null, null, options) }.isSuccess ||
            runCatching { intent.send() }.isSuccess
    }

    private fun appIcon(service: NotificationListenerService, packageName: String): ImageBitmap? =
        iconCache.getOrPut(packageName) {
            runCatching { service.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }

    private fun shadeNotice(service: NotificationListenerService, sbn: StatusBarNotification): ShadeNotice? {
        if (sbn.packageName == service.packageName) return null
        val n = sbn.notification
        val extras = n.extras ?: return null
        // What is playing is on the media card already.
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val pm = service.packageManager
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        return ShadeNotice(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = text,
            time = if (n.`when` > 0) n.`when` else sbn.postTime,
            icon = appIcon(service, sbn.packageName),
            contentIntent = n.contentIntent,
            // Replies need typed text; those open the app instead.
            actions = n.actions.orEmpty().mapNotNull { a ->
                val intent = a.actionIntent ?: return@mapNotNull null
                if (!a.remoteInputs.isNullOrEmpty()) return@mapNotNull null
                NoticeAction(a.title?.toString().orEmpty(), intent)
            }.filter { it.title.isNotBlank() }.take(3),
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            visibility = n.visibility,
            publicText = n.publicVersion?.extras?.let { e ->
                (e.getCharSequence(Notification.EXTRA_TEXT) ?: e.getCharSequence(Notification.EXTRA_TITLE))?.toString()
            },
        )
    }

    /** "12:34", "1:02:03" or "12:34,56" in a text, as milliseconds. */
    private fun parseClockTime(text: String): Long? {
        val m = ClockTimeRegex.find(text) ?: return null
        val a = m.groupValues[1].toLongOrNull() ?: return null
        val b = m.groupValues[2].toLongOrNull() ?: return null
        val c = m.groupValues[3].toLongOrNull()
        val seconds = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
        val hundredths = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { h -> h.padEnd(2, '0').toLong() * 10 } ?: 0L
        return seconds * 1000 + hundredths
    }

    private val ClockTimeRegex = Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?::(\d{2}))?(?:[.,](\d{1,2}))?(?![\d:])""")

    /** Clock apps whose ongoing notifications are stopwatches and timers. */
    private val ClockPackages = setOf(
        "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock",
        "com.oneplus.deskclock", "com.coloros.alarmclock", "com.oplus.alarmclock", "com.android.BBKClock",
        "com.huawei.deskclock", "com.hihonor.deskclock", "com.miui.clock", "com.motorola.timeweatherwidget",
    )
    /** Android 16's promoted (live update) flag and short island text, by value for older SDKs. */
    private const val FLAG_PROMOTED_ONGOING = 0x00040000
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private val HotspotWords = listOf("hotspot", "tethering", "mobiler hotspot", "wlan-hotspot")
    private val StopwatchWords = listOf("stoppuhr", "stopwatch", "chronometer", "chronomètre", "cronometro")
    private val TimerWords = listOf("timer", "countdown", "kurzzeitwecker", "minuteur", "temporizador")
    private val RecordingWords = listOf("bildschirmaufnahme", "screen recording", "screen recorder", "aufnahme läuft", "recording")
    private val ResumeWords = listOf("fortsetzen", "resume", "weiter", "start", "continue", "reprendre")
    private val PauseWords = listOf("pause", "anhalten", "stopp", "stop", "runde", "lap")

    private fun parse(service: NotificationListenerService, sbn: StatusBarNotification): LiveNotice? {
        if (sbn.packageName == service.packageName) return null
        val n = sbn.notification
        val extras = n.extras ?: return null
        // Media is shown from its media session instead.
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            ?.toString().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val words = "$title $body $sub".lowercase()
        val actionTitles = n.actions.orEmpty().mapNotNull { it.title?.toString()?.lowercase() }
        val showChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val clockApp = sbn.packageName in ClockPackages || sbn.packageName.contains("clock", ignoreCase = true)
        val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val timerLike = sbn.isOngoing && (
            clockApp || showChronometer || n.category == "stopwatch" ||
                StopwatchWords.any { it in words } || TimerWords.any { it in words }
            ) && n.category != Notification.CATEGORY_CALL
        val recording = sbn.isOngoing && (
            sbn.packageName.contains("screenrecorder", ignoreCase = true) || RecordingWords.any { it in words }
            )
        // Summaries only repeat their children, except a clock's lone stopwatch.
        if (summary && !timerLike) return null

        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val kind = when {
            n.category == Notification.CATEGORY_CALL -> NoticeKind.Call
            // A ringing alarm (full screen when the phone is in use).
            n.category == Notification.CATEGORY_ALARM && (n.fullScreenIntent != null || (clockApp && sbn.isOngoing && !timerLike)) ->
                NoticeKind.Alarm
            n.category == Notification.CATEGORY_NAVIGATION -> NoticeKind.Navigation
            recording -> NoticeKind.Recording
            timerLike -> NoticeKind.Timer
            sbn.isOngoing && HotspotWords.any { it in words } -> NoticeKind.Hotspot
            // Android 16 live updates (promoted ongoing notifications), like iPhone live activities.
            sbn.isOngoing && (n.flags and FLAG_PROMOTED_ONGOING != 0 || extras.containsKey(EXTRA_SHORT_CRITICAL_TEXT)) &&
                !(progressMax > 0 || indeterminate) -> NoticeKind.Update
            sbn.isOngoing && (progressMax > 0 || indeterminate) -> NoticeKind.Progress
            n.category == Notification.CATEGORY_MESSAGE || n.category == Notification.CATEGORY_EMAIL ||
                n.category == Notification.CATEGORY_SOCIAL -> NoticeKind.Message
            else -> return null
        }

        val pm = service.packageManager
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val icon = appIcon(service, sbn.packageName)

        // Stopwatch and timer: a running chronometer gives its start (or end) time directly.
        // Otherwise (Samsung's clock and others draw their own) the time is read from the text,
        // and kept ticking from when the notification was posted.
        val stopwatch = StopwatchWords.any { it in words } || n.category == "stopwatch"
        var countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) ||
            (!stopwatch && TimerWords.any { it in words })
        val paused = actionTitles.any { a -> ResumeWords.any { it in a } } &&
            actionTitles.none { a -> PauseWords.any { it in a } }
        var base = n.`when`
        var pausedMs = 0L
        var hasTime = true
        if (kind == NoticeKind.Timer || kind == NoticeKind.Recording || kind == NoticeKind.Call) {
            val textTime = parseClockTime(title) ?: parseClockTime(body) ?: parseClockTime(sub)
            val chronometerUsable = showChronometer && base > 0 && !paused
            when {
                chronometerUsable -> if (extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) countDown = true
                textTime != null && paused -> pausedMs = textTime
                textTime != null -> {
                    val seen = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
                    base = if (countDown) seen + textTime else seen - textTime
                }
                // A call without a running time is still ringing.
                else -> hasTime = false
            }
        }
        return LiveNotice(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = body,
            kind = kind,
            chronometerBase = base,
            countDown = countDown,
            paused = paused,
            pausedMs = pausedMs,
            hasTime = hasTime,
            stopwatch = stopwatch && !countDown,
            shortText = extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT)?.toString()?.takeIf { it.isNotBlank() }
                ?: if (kind == NoticeKind.Hotspot) Regex("""\d+""").find(body)?.value else null,
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax = progressMax,
            indeterminate = indeterminate,
            icon = icon,
            contentIntent = n.contentIntent,
            actions = n.actions.orEmpty().mapNotNull { a ->
                val intent = a.actionIntent ?: return@mapNotNull null
                NoticeAction(a.title?.toString().orEmpty(), intent)
            }.filter { it.title.isNotBlank() }.take(3),
        )
    }
}
