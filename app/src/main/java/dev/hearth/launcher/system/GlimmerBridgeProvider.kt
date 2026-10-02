package dev.hearth.launcher.system

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.hearth.launcher.data.SettingsRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Glimmer's door for Hearth: status, the app snapshot for the fly-in, the pulse, and the
 * global actions only an accessibility service may perform. Protected by a signature
 * permission, so only Hearth (same key) gets in. Holds no data, answers [call] only.
 */
class GlimmerBridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        return handle(context, method, arg, extras)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val main = Handler(Looper.getMainLooper())

        /** Runs [block] on the main thread (where the service lives) and waits a moment for it. */
        private fun <T> onMain(block: () -> T): T? {
            if (Looper.myLooper() == Looper.getMainLooper()) return block()
            var result: T? = null
            val done = CountDownLatch(1)
            main.post {
                result = runCatching(block).getOrNull()
                done.countDown()
            }
            done.await(1500, TimeUnit.MILLISECONDS)
            return result
        }

        fun handle(context: Context, method: String, arg: String?, extras: Bundle?): Bundle? = onMain {
            // Glimmer's service in the Glimmer app, the control center's in its app.
            val service = GlimmerService.instance
            val controlCenter = ControlCenterService.instance
            val settings = SettingsRepository(context).settings.value.forGlimmer()
            fun status(into: Bundle) = into.apply {
                putBoolean("running", if (context.packageName == ControlsLink.packageName) controlCenter != null else service != null)
                putBoolean("glimmer", settings.glimmerEnabled)
                val size = service?.glimmerIslandSize()
                putFloat("w", size?.width?.value ?: 0f)
                putFloat("h", size?.height?.value ?: 0f)
                // Where an app lands with the HyperOS fly-in: the small island's width and
                // Glimmer's nudge off the camera.
                val landing = dev.hearth.launcher.ui.islandSize(
                    dev.hearth.launcher.ui.IslandContent.Arrival(null, androidx.compose.ui.graphics.Color.White, 0),
                    expanded = false,
                    screenWidthDp = context.resources.configuration.screenWidthDp.toFloat(),
                    hasSecondary = false,
                    widthScale = settings.glimmerWidth,
                    dynamicIsland = settings.glimmerIsDynamicIsland,
                    narrow = settings.glimmerNarrow,
                    clawd = settings.glimmerClawd,
                )
                putFloat("lw", landing.width.value)
                putInt("ox", settings.glimmerOffsetX)
                putInt("oy", settings.glimmerOffsetY)
            }
            when (method) {
                GlimmerLink.STATUS -> status(Bundle())
                GlimmerLink.APP_LAUNCHED -> {
                    service?.startAppSnapshots()
                    null
                }
                GlimmerLink.RETURNED_HOME -> {
                    val before = extras?.getLong("before") ?: SystemClock.uptimeMillis()
                    val shot = service?.finishAppSnapshots(before)
                    status(Bundle()).apply { if (shot != null) putParcelable("snapshot", shot) }
                }
                GlimmerLink.PULSE -> {
                    service?.pulseGlimmer()
                    null
                }
                GlimmerLink.ARRIVE -> {
                    @Suppress("DEPRECATION")
                    val icon = extras?.getParcelable<android.graphics.Bitmap>("icon")
                    service?.arriveGlimmer(icon, extras?.getInt("color") ?: 0)
                    null
                }
                GlimmerLink.GLOBAL_ACTION -> Bundle().apply {
                    val action = arg?.toIntOrNull()
                    val any = controlCenter ?: service
                    putBoolean("ok", action != null && any?.performGlobalAction(action) == true)
                }
                // The control center lets the system shade it opened itself stay open.
                GlimmerLink.NOTIFICATIONS -> Bundle().apply {
                    putBoolean("ok", controlCenter?.showNotifications() ?: service?.showNotifications() ?: false)
                }
                GlimmerLink.QUICK_SETTINGS -> Bundle().apply {
                    putBoolean("ok", controlCenter?.showSystemQuickSettings() ?: service?.showSystemQuickSettings() ?: false)
                }
                GlimmerLink.SYNC_SETTINGS -> {
                    if (extras != null) SettingsRepository.writeRaw(context, extras)
                    // The Clawd app: his widgets take the new look right away.
                    if (context.packageName == ClawdLink.packageName) {
                        context.sendBroadcast(android.content.Intent(ClawdLink.ACTION_REFRESH).setPackage(context.packageName))
                    }
                    null
                }
                GlimmerLink.APP_UNLOCKED -> {
                    if (!arg.isNullOrBlank()) AppLockGuard.unlocked(arg)
                    null
                }
                else -> null
            }
        }
    }
}
