package dev.hearth.launcher.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * One of the Hearth family's apps, reached through its [GlimmerBridgeProvider]. Inside that
 * app the call is direct; from another app it goes through the bridge, which only apps
 * signed with the same key may use. Harmless when the app isn't installed or not running.
 */
open class HearthApp internal constructor(val packageName: String, authority: String) {

    private val bridge: Uri = Uri.parse("content://$authority")

    internal fun call(context: Context, method: String, arg: String? = null, extras: Bundle? = null): Bundle? {
        if (context.packageName == packageName) return GlimmerBridgeProvider.handle(context, method, arg, extras)
        return runCatching { context.contentResolver.call(bridge, method, arg, extras) }.getOrNull()
    }

    fun isInstalled(context: Context): Boolean =
        context.packageName == packageName ||
            runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    /** Opens the app (its settings screen). */
    fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Is the app's accessibility service on? Null if the app couldn't be asked. */
    fun isRunning(context: Context): Boolean? = call(context, GlimmerLink.STATUS)?.getBoolean("running")

    /** Lock, screenshot, power menu…: only an accessibility service may do these. */
    fun globalAction(context: Context, action: Int): Boolean =
        call(context, GlimmerLink.GLOBAL_ACTION, action.toString())?.getBoolean("ok") == true

    fun showNotifications(context: Context): Boolean = call(context, GlimmerLink.NOTIFICATIONS)?.getBoolean("ok") == true

    fun showQuickSettings(context: Context): Boolean = call(context, GlimmerLink.QUICK_SETTINGS)?.getBoolean("ok") == true

    /** Hands Hearth's control center look (and accent color) over. */
    fun syncSettings(context: Context, values: Bundle) {
        if (context.packageName == packageName) return
        call(context, GlimmerLink.SYNC_SETTINGS, extras = values)
    }
}

/** The control center app: the glass control center over other apps, replacing One UI's. */
object ControlsLink : HearthApp("dev.hearth.controls", "dev.hearth.controls.bridge") {
    const val DOWNLOAD_URL = "https://github.com/Vinted7777/launcher/releases/latest/download/Kontrollzentrum.apk"
}

/** Clawd: his own app with his widgets for any launcher (Samsung's too); takes Hearth's look of him. */
object ClawdLink : HearthApp("dev.hearth.clawd", "dev.hearth.clawd.bridge") {
    const val DOWNLOAD_URL = "https://github.com/Vinted7777/launcher/releases/latest/download/Clawd.apk"

    /** Sent inside the Clawd app when his look changed: the widgets draw him again. */
    const val ACTION_REFRESH = "dev.hearth.clawd.REFRESH"
}

/** Glimmer: the island; also takes the app picture for Hearth's fly-in. */
object GlimmerLink : HearthApp("dev.hearth.glimmer", "dev.hearth.glimmer.bridge") {

    const val GLIMMER_PACKAGE = "dev.hearth.glimmer"
    const val HEARTH_PACKAGE = "dev.hearth.launcher"
    const val DOWNLOAD_URL = "https://github.com/Vinted7777/launcher/releases/latest/download/Glimmer.apk"

    internal const val STATUS = "status"
    internal const val APP_LAUNCHED = "appLaunched"
    internal const val RETURNED_HOME = "returnedHome"
    internal const val PULSE = "pulse"
    internal const val ARRIVE = "arrive"
    internal const val GLOBAL_ACTION = "globalAction"
    internal const val NOTIFICATIONS = "notifications"
    internal const val QUICK_SETTINGS = "quickSettings"
    internal const val SYNC_SETTINGS = "syncSettings"
    internal const val APP_UNLOCKED = "appUnlocked"

    /** App lock: [packageName] was just unlocked, so Glimmer lets it open without asking again. */
    fun appUnlocked(context: Context, packageName: String) {
        call(context, APP_UNLOCKED, packageName)
    }

    /** Glimmer's state: is its accessibility service on, is the island shown, how big is it. */
    class Status(val running: Boolean, val glimmerOn: Boolean, val island: DpSize?)

    fun status(context: Context): Status? = call(context, STATUS)?.let { b ->
        Status(
            running = b.getBoolean("running"),
            glimmerOn = b.getBoolean("glimmer"),
            island = b.getFloat("w").takeIf { it > 0f }?.let { DpSize(it.dp, b.getFloat("h").dp) },
        )
    }

    /** An app was opened from Hearth: Glimmer starts keeping its last picture. */
    fun appLaunched(context: Context) {
        call(context, APP_LAUNCHED)
    }

    /** What Hearth needs for the fly-in when it's back home. */
    class Return(val ready: Boolean, val snapshot: Bitmap?, val island: DpSize?, val landing: Landing)

    /** Where a HyperOS fly-in lands: the small island's width (dp, 0 = unknown) and Glimmer's nudge. */
    class Landing(val width: Float = 0f, val dx: Int = 0, val dy: Int = 0)

    /** Back home: Glimmer stops capturing and hands over the picture from before [beforeUptime]. */
    fun returnedHome(context: Context, beforeUptime: Long): Return? {
        val b = call(context, RETURNED_HOME, extras = Bundle().apply { putLong("before", beforeUptime) }) ?: return null
        val buffer: HardwareBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            b.getParcelable("snapshot", HardwareBuffer::class.java)
        } else {
            @Suppress("DEPRECATION")
            b.getParcelable("snapshot")
        }
        val bitmap = buffer?.let { hb ->
            runCatching { Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB)) }.getOrNull()
                .also { hb.close() }
        }
        return Return(
            ready = b.getBoolean("running") && b.getBoolean("glimmer"),
            snapshot = bitmap,
            island = b.getFloat("w").takeIf { it > 0f }?.let { DpSize(it.dp, b.getFloat("h").dp) },
            landing = Landing(b.getFloat("lw"), b.getInt("ox"), b.getInt("oy")),
        )
    }

    /** Glimmer hops and shimmers (an app just flew into it). */
    fun pulse(context: Context) {
        call(context, PULSE)
    }

    /** An app lands in Glimmer (HyperOS fly-in): Glimmer shows its icon for a moment. */
    fun arrive(context: Context, icon: Bitmap?, color: Int) {
        call(context, ARRIVE, extras = Bundle().apply {
            if (icon != null) putParcelable("icon", icon)
            putInt("color", color)
        })
    }
}
