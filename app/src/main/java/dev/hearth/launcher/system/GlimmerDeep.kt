package dev.hearth.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.ShizukuBridge

/**
 * ZENITH 22.1, Glimmer deeper in the system (with Shizuku): things a normal app may not do by itself, done once with the rights of
 * ADB that Shizuku lends – switching on Glimmer's own services and permissions without the trip through Android's settings, keeping
 * the island switched on when Android has switched its service off, and silencing the system's own banners so that only the island
 * announces what is new.
 *
 * Shizuku is needed for the setting up only: it also grants "change secure settings" to this app, and with that Glimmer does the
 * rest by itself afterwards – also after a restart, when Shizuku is not running.
 */
object GlimmerDeep {
    private const val PREFS = "glimmer_deep"
    private const val KEY_KEEP = "keepAlive"
    private const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
    private const val SERVICES = "enabled_accessibility_services"
    private const val HEADS_UP = "heads_up_notifications_enabled"

    /** May this app change secure settings by itself (granted through Shizuku once)? */
    fun canWriteSecure(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED

    private fun service(context: Context) = ComponentName(context, GlimmerService::class.java)

    /** Is Glimmer's service among the ones Android has switched on? */
    fun serviceListed(context: Context): Boolean {
        val mine = service(context)
        val list = runCatching { Settings.Secure.getString(context.contentResolver, SERVICES) }.getOrNull().orEmpty()
        return list.split(':').any { it.isNotBlank() && ComponentName.unflattenFromString(it) == mine }
    }

    /** The list of switched-on services with Glimmer's added. */
    private fun withService(context: Context): String {
        val list = runCatching { Settings.Secure.getString(context.contentResolver, SERVICES) }.getOrNull().orEmpty()
        val mine = service(context).flattenToString()
        return if (list.isBlank()) mine else "$list:$mine"
    }

    /** What the setting up did, step by step (its name, and whether it worked). */
    class Step(val name: String, val ok: Boolean)

    /**
     * Everything Glimmer needs, switched on with Shizuku's rights: its service, the access to notifications, no battery saving for it,
     * its permissions – and the right to change secure settings, so it can look after itself from then on.
     */
    suspend fun setUp(context: Context): List<Step> {
        val pkg = context.packageName
        val listener = ComponentName(context, MediaListenerService::class.java).flattenToString()
        val steps = ArrayList<Step>()
        steps += Step(
            "Glimmers Bedienungshilfe einschalten",
            serviceListed(context) || ShizukuBridge.run(
                "settings put secure $SERVICES '${withService(context)}' && settings put secure accessibility_enabled 1",
            ),
        )
        steps += Step("Zugriff auf Benachrichtigungen", ShizukuBridge.run("cmd notification allow_listener $listener"))
        steps += Step("Vom Akkusparen ausnehmen", ShizukuBridge.run("cmd deviceidle whitelist +$pkg || dumpsys deviceidle whitelist +$pkg"))
        steps += Step("Glimmer darf sich selbst wieder einschalten", ShizukuBridge.run("pm grant $pkg $WRITE_SECURE"))
        // (Each one only if the app asks for it at all; one that it does not ask for is no failure.)
        ShizukuBridge.run(
            listOf(
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.BLUETOOTH_CONNECT",
            ).joinToString("; ") { "pm grant $pkg $it" } + "; true",
        )
        return steps
    }

    /** "Keep Glimmer switched on": chosen by the user (off as it comes). */
    fun keepAlive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP, false)

    fun setKeepAlive(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_KEEP, on).apply()
        if (on) heal(context)
    }

    /**
     * Android switched Glimmer's service off (after a crash, an update, or by a battery saver): switched on again – only if the
     * user chose "keep Glimmer switched on", Glimmer itself is on and the app has the right. True if it had to do something.
     */
    fun heal(context: Context): Boolean {
        return runCatching {
            if (!keepAlive(context) || !canWriteSecure(context) || serviceListed(context)) {
                false
            } else if (!SettingsRepository(context, listen = false).settings.value.glimmerEnabled) {
                false
            } else {
                Settings.Secure.putString(context.contentResolver, SERVICES, withService(context))
                Settings.Secure.putInt(context.contentResolver, "accessibility_enabled", 1)
                true
            }
        }.getOrDefault(false)
    }

    /** Are the system's own banners (the notifications sliding in from the top) switched off? */
    fun bannersOff(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, HEADS_UP, 1) == 0 }.getOrDefault(false)

    /** Switches the system's banners off (only the island announces what is new) or on again. */
    suspend fun setBannersOff(context: Context, off: Boolean): Boolean {
        val value = if (off) 0 else 1
        val direct = canWriteSecure(context) && runCatching { Settings.Global.putInt(context.contentResolver, HEADS_UP, value) }.getOrDefault(false)
        return direct || ShizukuBridge.run("settings put global $HEADS_UP $value")
    }
}
