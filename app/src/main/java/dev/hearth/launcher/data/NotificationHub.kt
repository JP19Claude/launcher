package dev.hearth.launcher.data

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
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
enum class NoticeKind { Call, Navigation, Timer, Progress, Message }

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

    private val iconCache = HashMap<String, ImageBitmap?>()

    fun refresh(service: NotificationListenerService) {
        val all = runCatching { service.activeNotifications }.getOrNull().orEmpty()
        _active.value = all.mapNotNull { parse(service, it) }.filter { it.kind != NoticeKind.Message }
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
        _active.value = emptyList()
    }

    private fun parse(service: NotificationListenerService, sbn: StatusBarNotification): LiveNotice? {
        if (sbn.packageName == service.packageName) return null
        val n = sbn.notification
        val extras = n.extras ?: return null
        // Media is shown from its media session instead.
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val kind = when {
            n.category == Notification.CATEGORY_CALL -> NoticeKind.Call
            n.category == Notification.CATEGORY_NAVIGATION -> NoticeKind.Navigation
            sbn.isOngoing && (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) || n.category == "stopwatch") ->
                NoticeKind.Timer
            sbn.isOngoing && (progressMax > 0 || indeterminate) -> NoticeKind.Progress
            n.category == Notification.CATEGORY_MESSAGE || n.category == Notification.CATEGORY_EMAIL ||
                n.category == Notification.CATEGORY_SOCIAL -> NoticeKind.Message
            else -> return null
        }

        val pm = service.packageManager
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val icon = iconCache.getOrPut(sbn.packageName) {
            runCatching { pm.getApplicationIcon(sbn.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        return LiveNotice(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
                ?.toString().orEmpty(),
            kind = kind,
            chronometerBase = n.`when`,
            countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
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
