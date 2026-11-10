package dev.hearth.launcher.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.hearth.launcher.data.ShizukuBridge
import kotlinx.coroutines.delay

/**
 * ZENITH 22.1, Shizuku deeper in the normal system: the permissions the launcher otherwise gets one by one through a dozen pages of
 * Android's settings (change system settings, Do not disturb, all files, read notifications, the accessibility service, no battery
 * saving, install apps, change secure settings) given in a single tap with the rights of ADB – and made the home screen of the phone
 * without the trip through the default-apps settings. The shell stand-ins for the accessibility actions (screenshot, lock, back, home …)
 * and the settings the app writes by itself once it has been given the right are in [ShizukuBridge]. See also [GlimmerDeep], which
 * sets up Glimmer's own service and permissions (and is part of "give all rights").
 */
object ZenithDeep {
    /** One permission and whether the app has it right now. */
    class Right(val label: String, val granted: Boolean)

    /** Where the launcher stands: every special permission it can use, and whether Android has given it. */
    fun rights(context: Context): List<Right> {
        val pkg = context.packageName
        fun has(block: () -> Boolean) = runCatching(block).getOrDefault(false)
        return listOf(
            Right("Systemeinstellungen ändern", has { Settings.System.canWrite(context) }),
            Right("Nicht stören steuern", has { context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted }),
            Right("Alle Dateien (fürs Hintergrundbild)", has { Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager() }),
            Right("Benachrichtigungen lesen", has { NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg) }),
            Right("Bedienungshilfe", GlimmerDeep.serviceListed(context)),
            Right("Vom Akkusparen ausgenommen", has { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg) }),
            Right("Apps installieren (Updates)", has { context.packageManager.canRequestPackageInstalls() }),
            Right("Geheime Systemeinstellungen ändern", GlimmerDeep.canWriteSecure(context)),
        )
    }

    /**
     * Gives the app every right of [rights] with Shizuku's rights, and reads them again afterwards: what Android still withholds is
     * named (an exit code says little – a command for a right this version of Android does not know would "succeed" too).
     */
    suspend fun grantAll(context: Context): List<String> {
        val pkg = context.packageName
        ShizukuBridge.run(
            listOf(
                "cmd appops set $pkg WRITE_SETTINGS allow",
                "cmd notification allow_dnd $pkg",
                "cmd appops set $pkg MANAGE_EXTERNAL_STORAGE allow",
                "cmd appops set $pkg REQUEST_INSTALL_PACKAGES allow",
            ).joinToString("; ") + "; true",
        )
        // (Glimmer's own: the service, the access to notifications, no battery saving, secure settings and the runtime permissions.)
        GlimmerDeep.setUp(context)
        delay(700)
        return rights(context).filterNot { it.granted }.map { it.label }
    }

    /** Is this app the home screen of the phone? */
    fun isHome(context: Context): Boolean = runCatching {
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        home?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)
}
