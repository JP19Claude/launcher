package dev.hearth.launcher.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.hearth.launcher.data.NotificationHub

/**
 * Notification access for Hearth. Android only shares the running media sessions (what is
 * playing) with apps that have it, and Glimmer uses ongoing notifications (calls, timers,
 * navigation, downloads) and new messages as live activities.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.refresh(this)
    }

    override fun onListenerDisconnected() {
        NotificationHub.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) NotificationHub.onPosted(this, sbn) else NotificationHub.refresh(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotificationHub.refresh(this)
    }
}
