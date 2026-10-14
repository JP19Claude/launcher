package dev.hearth.launcher.system

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.hearth.launcher.data.AppLock

/**
 * Glimmer's half of the app lock: it sees which app comes to the front (its accessibility
 * service), and when that's a locked one that wasn't unlocked yet, it puts Hearth's lock
 * screen over it – however the app was opened (notification, recent apps, another app).
 *
 * Unlocked apps stay open until the screen turns off or the home screen comes back.
 */
object AppLockGuard {

    private val unlocked = mutableSetOf<String>()
    private var pending: String? = null
    private var pendingAt = 0L

    @Synchronized
    fun unlocked(packageName: String) {
        unlocked += packageName
        if (pending == packageName) pending = null
    }

    /** Screen off or home: everything is locked again. */
    @Synchronized
    fun relock() {
        unlocked.clear()
        pending = null
    }

    @Synchronized
    fun onWindow(context: Context, packageName: String, className: String?) {
        if (isHome(packageName, className)) {
            relock()
            return
        }
        if (isPassing(packageName)) return
        if (packageName !in AppLock.lockedPackages(context) || packageName in unlocked) return
        val now = SystemClock.uptimeMillis()
        // One lock screen per opening, even if the app shows several windows.
        if (pending == packageName && now - pendingAt < 5_000) return
        pending = packageName
        pendingAt = now
        val intent = Intent()
            .setClassName(AppLock.HEARTH_PACKAGE, AppLock.ACTIVITY)
            .putExtra(AppLock.EXTRA_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) {
            // Without Hearth there's no lock screen: send the app away instead of showing it.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun isHome(packageName: String, className: String?): Boolean =
        (packageName == AppLock.HEARTH_PACKAGE && className == "dev.hearth.launcher.MainActivity") ||
            packageName.contains("launcher", ignoreCase = true) && packageName != AppLock.HEARTH_PACKAGE

    /** System windows, keyboards and the family's own screens pass without asking. */
    private fun isPassing(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName.startsWith("dev.hearth.") ||
            packageName.contains("inputmethod") ||
            packageName.contains("keyboard") ||
            packageName.contains("honeyboard") ||
            packageName.contains("permissioncontroller")
}
