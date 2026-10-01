package dev.hearth.launcher.system

import android.service.notification.NotificationListenerService

/**
 * Does nothing by itself. Android only shares the running media sessions (what is playing,
 * cover, controls) with apps that have notification access, and that access is granted
 * to a listener service like this one.
 */
class MediaListenerService : NotificationListenerService()
