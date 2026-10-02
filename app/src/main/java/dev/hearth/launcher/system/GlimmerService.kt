package dev.hearth.launcher.system

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import dev.hearth.launcher.data.SettingsRepository

/**
 * The Glimmer app's accessibility service: the island around the camera in every app (not on
 * the lock screen), and the last picture of the app in front for Hearth's fly-in.
 *
 * Accessibility, because only such services may draw over the status bar and take that picture. The control center over other apps is its own app now.
 */
class GlimmerService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val handler = Handler(Looper.getMainLooper())
    private var glimmer: GlimmerController? = null


    private fun restartGlimmer() {
        val current = SettingsRepository(this).settings.value
        glimmer?.stop()
        if (current.glimmerEnabled) glimmer?.start(current)
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            in SettingsRepository.GLIMMER_KEYS -> restartGlimmer()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopAppSnapshots()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        glimmer = GlimmerController(this)
        restartGlimmer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        glimmer?.onConfigurationChanged(newConfig)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance === this) instance = null
        glimmer?.stop()
        glimmer = null
        stopAppSnapshots()
        runCatching { unregisterReceiver(screenReceiver) }
        getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
    }

    // ---- App snapshots for the fly-in ----

    /**
     * While an app opened from Hearth is in front, its look is captured about once a second
     * (Android 11+), so that when it's closed, the app itself (not just its color) can fly
     * into Glimmer. Only the last two pictures are kept, in memory; nothing is saved.
     */
    private var snapshotting = false
    private val snapshots = ArrayDeque<Pair<Long, HardwareBuffer>>()
    private val snapshotTick = object : Runnable {
        override fun run() {
            if (!snapshotting || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            val usable = !isLocked() &&
                getSystemService(PowerManager::class.java)?.isInteractive != false
            if (usable) {
                runCatching {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                val buffer = result.hardwareBuffer
                                if (!snapshotting) {
                                    buffer.close()
                                    return
                                }
                                snapshots.addLast(SystemClock.uptimeMillis() to buffer)
                                while (snapshots.size > 2) snapshots.removeFirst().second.close()
                            }

                            override fun onFailure(errorCode: Int) = Unit
                        },
                    )
                }
            }
            handler.postDelayed(this, SNAPSHOT_INTERVAL_MS)
        }
    }

    /** An app opened from Hearth came to the front. */
    fun startAppSnapshots() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        snapshots.clear()
        snapshotting = true
        handler.removeCallbacks(snapshotTick)
        // Give the app a moment to draw its first screen.
        handler.postDelayed(snapshotTick, 700)
    }

    /**
     * Back home: stop capturing and hand out the last picture taken before [beforeUptime]
     * (pictures from during the home gesture would show the window already shrinking).
     */
    fun finishAppSnapshots(beforeUptime: Long): HardwareBuffer? {
        val pick = snapshots.lastOrNull { it.first <= beforeUptime && beforeUptime - it.first < 4000 }
        if (pick != null) snapshots.remove(pick)
        stopAppSnapshots()
        // Handed on to Hearth (the parcel shares it); closed here once that's done.
        return pick?.second?.also { buffer -> handler.postDelayed({ runCatching { buffer.close() } }, 5000) }
    }

    private fun stopAppSnapshots() {
        snapshotting = false
        handler.removeCallbacks(snapshotTick)
        snapshots.forEach { runCatching { it.second.close() } }
        snapshots.clear()
    }

    /** Glimmer hops and shimmers (an app just flew into it). */
    fun pulseGlimmer() {
        glimmer?.pulse()
    }

    /** Size of Glimmer's island right now, or null without Glimmer. */
    fun glimmerIslandSize(): DpSize? = glimmer?.islandSize

    /** Pulls down the normal notification shade. */
    fun showNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Opens the system's own quick settings. */
    fun showSystemQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    companion object {
        /** The running service, if the user turned it on in the accessibility settings. */
        @Volatile
        var instance: GlimmerService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        private const val SNAPSHOT_INTERVAL_MS = 900L
    }
}
