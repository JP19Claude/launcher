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
 * Talks to Glimmer. Inside the Glimmer app that's a direct call; from Hearth (the launcher)
 * it goes through Glimmer's [GlimmerBridgeProvider], which only apps signed with the same key
 * may use. Every call is harmless when Glimmer isn't installed or not running.
 */
object GlimmerLink {

    const val GLIMMER_PACKAGE = "dev.hearth.glimmer"
    const val HEARTH_PACKAGE = "dev.hearth.launcher"
    const val DOWNLOAD_URL = "https://github.com/Vinted7777/launcher/releases/latest/download/Glimmer.apk"

    internal const val STATUS = "status"
    internal const val APP_LAUNCHED = "appLaunched"
    internal const val RETURNED_HOME = "returnedHome"
    internal const val PULSE = "pulse"
    internal const val GLOBAL_ACTION = "globalAction"
    internal const val NOTIFICATIONS = "notifications"
    internal const val QUICK_SETTINGS = "quickSettings"
    internal const val SYNC_SETTINGS = "syncSettings"

    private val bridge: Uri = Uri.parse("content://dev.hearth.glimmer.bridge")

    private fun isGlimmer(context: Context) = context.packageName == GLIMMER_PACKAGE

    private fun call(context: Context, method: String, arg: String? = null, extras: Bundle? = null): Bundle? {
        if (isGlimmer(context)) return GlimmerBridgeProvider.handle(context, method, arg, extras)
        return runCatching { context.contentResolver.call(bridge, method, arg, extras) }.getOrNull()
    }

    fun isInstalled(context: Context): Boolean =
        isGlimmer(context) || runCatching { context.packageManager.getPackageInfo(GLIMMER_PACKAGE, 0); true }.getOrDefault(false)

    /** Opens the Glimmer app (its settings). */
    fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(GLIMMER_PACKAGE) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
    class Return(val ready: Boolean, val snapshot: Bitmap?, val island: DpSize?)

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
        )
    }

    /** Glimmer hops and shimmers (an app just flew into it). */
    fun pulse(context: Context) {
        call(context, PULSE)
    }

    /** Lock, screenshot, power menu…: only Glimmer's accessibility service may do these. */
    fun globalAction(context: Context, action: Int): Boolean =
        call(context, GLOBAL_ACTION, action.toString())?.getBoolean("ok") == true

    fun showNotifications(context: Context): Boolean = call(context, NOTIFICATIONS)?.getBoolean("ok") == true

    fun showQuickSettings(context: Context): Boolean = call(context, QUICK_SETTINGS)?.getBoolean("ok") == true

    /** Hands Hearth's control center look (and accent color) to Glimmer, so both look the same. */
    fun syncSettings(context: Context, values: Bundle) {
        if (isGlimmer(context)) return
        call(context, SYNC_SETTINGS, extras = values)
    }
}
