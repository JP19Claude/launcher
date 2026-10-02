package dev.hearth.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.UserHandle
import java.util.concurrent.ConcurrentHashMap

/**
 * App lock: chosen apps only open after the phone's own PIN, pattern, password, fingerprint
 * or face. Hearth asks when it opens them; Glimmer (when on) asks too when they're opened any
 * other way – from a notification, the recent apps or another app.
 */
object AppLock {

    /** The locked packages, joined with "|" (shared with Glimmer and the control center). */
    const val KEY = "lockedApps"

    const val HEARTH_PACKAGE = "dev.hearth.launcher"
    const val ACTIVITY = "dev.hearth.launcher.AppLockActivity"
    const val EXTRA_PACKAGE = "dev.hearth.launcher.LOCK_PACKAGE"
    const val EXTRA_COMPONENT = "dev.hearth.launcher.LOCK_COMPONENT"
    const val EXTRA_USER = "dev.hearth.launcher.LOCK_USER"
    const val EXTRA_REMOVE = "dev.hearth.launcher.LOCK_REMOVE"

    /** The launcher, while it runs: takes an app off the lock once the PIN confirmed it. */
    @Volatile
    var removeHandler: ((String) -> Unit)? = null

    /** Taking the lock off needs the PIN or biometrics too, so nobody else can just switch it off. */
    fun requestRemove(context: Context, packageName: String) {
        val intent = Intent()
            .setClassName(HEARTH_PACKAGE, ACTIVITY)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_REMOVE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** In this process: apps just unlocked, so the same opening doesn't ask twice. */
    private val unlockedAt = ConcurrentHashMap<String, Long>()

    fun lockedPackages(context: Context): Set<String> = runCatching {
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    fun isLocked(context: Context, packageName: String): Boolean = packageName in lockedPackages(context)

    fun markUnlocked(packageName: String) {
        unlockedAt[packageName] = SystemClock.elapsedRealtime()
    }

    private fun justUnlocked(packageName: String): Boolean =
        unlockedAt[packageName]?.let { SystemClock.elapsedRealtime() - it < 4_000 } == true

    /**
     * Opening an app from Hearth: if it's locked, the lock screen opens instead and starts the
     * app after the PIN or biometrics. True when that happened (the caller then does nothing).
     */
    fun guardLaunch(context: Context, component: ComponentName, user: UserHandle?): Boolean {
        val pkg = component.packageName
        if (!isLocked(context, pkg) || justUnlocked(pkg)) return false
        val intent = Intent()
            .setClassName(HEARTH_PACKAGE, ACTIVITY)
            .putExtra(EXTRA_PACKAGE, pkg)
            .putExtra(EXTRA_COMPONENT, component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (user != null) intent.putExtra(EXTRA_USER, user)
        // If the lock screen can't open, the app stays shut rather than opening unguarded.
        runCatching { context.startActivity(intent) }
        return true
    }
}
