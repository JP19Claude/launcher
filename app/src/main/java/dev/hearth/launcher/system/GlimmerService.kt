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
import dev.hearth.launcher.data.AppLock
import dev.hearth.launcher.data.SettingsRepository
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The Glimmer app's accessibility service: the island around the camera in every app (with
 * the Face ID moment, on the lock screen and the always-on display), and the last picture of
 * the app in front for Hearth's fly-in.
 *
 * Accessibility, because only such services may draw over the status bar and lock screen
 * and take that picture. The control center over other apps is its own app now.
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
            AppLock.KEY, "glimmerFlyIn", "glimmerFlyInEverywhere" -> applyEventScope()
            // Applied while running, so sliders and switches show at once without a flicker.
            in SettingsRepository.GLIMMER_LIVE_KEYS -> glimmer?.update(SettingsRepository(this).settings.value)
            in SettingsRepository.GLIMMER_KEYS -> restartGlimmer()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stopAppSnapshots()
                    frontApp = null
                    AppLockGuard.relock()
                }
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
        applyEventScope()
    }

    /**
     * Normally only the system UI's events arrive. With locked apps, or the fly-in with another
     * launcher, Glimmer also needs to hear which app comes to the front (nothing else of other
     * apps is looked at).
     */
    private fun applyEventScope() {
        runCatching {
            val info = serviceInfo ?: return
            homePackage = null
            val guard = AppLock.lockedPackages(this).isNotEmpty() || flyInHere()
            info.packageNames = if (guard) null else arrayOf(SYSTEM_UI)
            serviceInfo = info
        }
    }

    /**
     * Only the system UI's events arrive. While the lock screen is up, its messages are read
     * for one thing: a face or finger that wasn't recognized, so Glimmer's symbol can shake.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        // App lock: a locked app came to the front.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != SYSTEM_UI) {
            AppLockGuard.onWindow(this, pkg, event.className?.toString())
            onFront(pkg)
            return
        }
        // Everything else is only about the system UI (the lock screen's messages).
        if (pkg != SYSTEM_UI) return
        val g = glimmer ?: return
        if (!g.wantsUnlockTexts()) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> event.text?.forEach { g.onLockScreenText(it) }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT == 0) return
                val node = runCatching { event.source }.getOrNull() ?: return
                node.text?.let { g.onLockScreenText(it) }
                node.contentDescription?.let { g.onLockScreenText(it) }
            }
        }
    }

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
        removeFlyWindow()
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

    // ---- The fly-in with any launcher ----

    /** The app in front (an app with an icon in the launcher), while the fly-in follows apps. */
    private var frontApp: String? = null
    private var homePackage: String? = null
    private val launchable = HashMap<String, Boolean>()
    private var flyWindow: android.view.View? = null

    /** The phone's home app (the launcher), as Android has it set. */
    private fun home(): String? = homePackage ?: runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
    }.getOrNull().also { homePackage = it }

    /**
     * Glimmer plays the fly-in itself when another launcher is home (One UI, ColorOS, Pixel …);
     * with Hearth as home, Hearth plays it.
     */
    private fun flyInHere(): Boolean {
        val s = SettingsRepository(this).settings.value
        if (!s.glimmerEnabled || !s.glimmerFlyIn || !s.glimmerFlyInEverywhere) return false
        val h = home() ?: return false
        return h != GlimmerLink.HEARTH_PACKAGE
    }

    private fun isKeyboard(pkg: String): Boolean = runCatching {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.enabledInputMethodList?.any { it.packageName == pkg } == true
    }.getOrDefault(false)

    /** Something new came to the front: an app (keep its picture), or home (let the app fly). */
    private fun onFront(pkg: String) {
        if (!flyInHere()) return
        val h = home() ?: return
        when {
            pkg == h -> {
                val app = frontApp ?: return
                frontApp = null
                // The launcher has just shrunk the app into its icon: give it a moment to
                // settle, then the icon lifts off from there.
                handler.postDelayed({ showFlyIn(app) }, 60)
            }
            pkg == packageName || isLocked() -> Unit
            launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null && !isKeyboard(pkg) } -> {
                frontApp = pkg
            }
        }
    }

    /**
     * Where the app's icon sits on the home screen right now (px on screen), found by its name
     * in the launcher's window; null if it isn't on the page (in a folder, the drawer …).
     */
    private fun iconOnHome(label: String): android.graphics.RectF? = runCatching {
        val root = rootInActiveWindow ?: return null
        val screenW = resources.displayMetrics.widthPixels
        val hits = root.findAccessibilityNodeInfosByText(label).orEmpty()
        val cell = hits.firstNotNullOfOrNull { node ->
            val named = node.text?.toString() == label || node.contentDescription?.toString()?.startsWith(label) == true
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            r.takeIf { named && node.isVisibleToUser && r.width() in 1 until screenW / 2 && r.height() > 0 }
        } ?: return null
        // A cell holds the icon with its name below; a bare name has the icon above it.
        val side: Float
        val cx = cell.exactCenterX()
        val cy: Float
        if (cell.height() > cell.width() * 0.6f) {
            side = minOf(cell.width() * 0.62f, cell.height() * 0.6f)
            cy = cell.top + cell.height() * 0.4f
        } else {
            side = cell.width() * 0.62f
            cy = cell.top - side / 2f - side * 0.12f
        }
        android.graphics.RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }.getOrNull()

    /** The closed app's icon flies from the home screen into the island, drawn by Glimmer. */
    private fun showFlyIn(pkg: String) {
        val settings = SettingsRepository(this).settings.value
        val controller = glimmer ?: return
        val app = runCatching {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val icon = pm.getApplicationIcon(info)
            dev.hearth.launcher.data.AppInfo(
                label = pm.getApplicationLabel(info).toString(),
                component = pm.getLaunchIntentForPackage(pkg)?.component ?: android.content.ComponentName(pkg, pkg),
                user = android.os.Process.myUserHandle(),
                icon = icon.toBitmap(192, 192).asImageBitmap(),
            )
        }.getOrNull() ?: return
        val from = iconOnHome(app.label)
        val center = controller.islandCenterOnScreen()
        val islandW = controller.islandSize.width.value.takeIf { it > 0f }?.let { it * resources.displayMetrics.density }
            ?: (110f * resources.displayMetrics.density)
        removeFlyWindow()
        val compose = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GlimmerService)
            setViewTreeSavedStateRegistryOwner(this@GlimmerService)
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(dev.hearth.launcher.ui.LocalSettings provides settings) {
                    dev.hearth.launcher.ui.IconFlyIn(
                        app = app,
                        from = from,
                        to = androidx.compose.ui.geometry.Offset(center.x, center.y),
                        islandWidth = islandW,
                        style = settings.glimmerFlyInStyle,
                        onPulse = { pulseGlimmer() },
                        onArrive = { icon, color -> arriveGlimmer(icon, color) },
                        onDone = { removeFlyWindow() },
                    )
                }
            }
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Glimmer fly-in"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        val wm = getSystemService(android.view.WindowManager::class.java)
        if (wm != null && runCatching { wm.addView(compose, params) }.isSuccess) {
            flyWindow = compose
            // Never left on screen, whatever happens.
            handler.postDelayed({ removeFlyWindow() }, 3000)
        }
    }

    private fun removeFlyWindow() {
        val view = flyWindow ?: return
        flyWindow = null
        runCatching { getSystemService(android.view.WindowManager::class.java)?.removeView(view) }
    }

    /** Glimmer hops and shimmers (an app just flew into it). */
    fun pulseGlimmer() {
        glimmer?.pulse()
    }

    /** An app lands in the island (HyperOS fly-in): its icon sits in it for a moment. */
    fun arriveGlimmer(icon: android.graphics.Bitmap?, color: Int) {
        glimmer?.arrive(icon, color)
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
        private const val SYSTEM_UI = "com.android.systemui"
    }
}
