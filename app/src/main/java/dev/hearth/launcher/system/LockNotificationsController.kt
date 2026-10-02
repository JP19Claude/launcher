package dev.hearth.launcher.system

import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LockLayout
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.LockScreenNotifications
import kotlin.math.roundToInt

/**
 * Puts Hearth's iOS-style notifications on the lock screen: a window in the lower part of
 * the screen, above fingerprint sensor and shortcuts, only while the phone is locked and on.
 * It's only as tall as what it shows, so the rest of the lock screen still unlocks as usual.
 */
class LockNotificationsController(private val service: GlimmerService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var settings by mutableStateOf(LauncherSettings())

    /** Shows or hides the window to match lock state, screen and settings. */
    fun update() {
        val current = SettingsRepository(service).settings.value
        settings = current
        val locked = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val screenOn = service.getSystemService(PowerManager::class.java)?.isInteractive != false
        val show = current.lockNotifications != LockLayout.Off && locked && screenOn && !appOverLockScreen()
        if (show) attach() else detach()
    }

    /** Checks again a moment later; unlocking by fingerprint can come after the screen turns on. */
    fun updateSoon() {
        update()
        handler.postDelayed({ update() }, 400)
        handler.postDelayed({ update() }, 1200)
    }

    /** Camera, calls and the like can show over the lock screen; then stay out of the way. */
    private fun appOverLockScreen(): Boolean {
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        return windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
    }

    private fun attach() {
        if (root != null) return
        val wm = windowManager ?: return
        val frame = FrameLayout(service)
        frame.setViewTreeLifecycleOwner(service)
        frame.setViewTreeSavedStateRegistryOwner(service)
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setContent { LockScreenNotifications(settings) }
        }
        frame.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val metrics = service.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // Above the fingerprint sensor and the lock screen shortcuts.
            y = (metrics.heightPixels * BOTTOM_GAP).roundToInt()
            title = "Hearth lock screen notifications"
        }
        if (runCatching { wm.addView(frame, params) }.isSuccess) root = frame
    }

    private fun detach() {
        val view = root ?: return
        root = null
        runCatching { windowManager?.removeView(view) }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        detach()
    }

    companion object {
        /** Share of the screen height kept free at the bottom. */
        private const val BOTTOM_GAP = 0.2f
    }
}
