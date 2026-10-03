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
import androidx.core.view.doOnNextLayout
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
                    removeFlyWindow()
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
        // The fly-in: the moment the launcher becomes the active window (often before it says
        // so itself, once its closing animation is over), so the app flies without delay.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (frontApp != null) checkHomeActive()
            return
        }
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

    /** The windows changed: is the launcher the active one now? Then the app was just closed. */
    private fun checkHomeActive() {
        val h = home() ?: return
        val active = runCatching { windows.firstOrNull { it.isActive }?.root?.packageName?.toString() }.getOrNull()
        if (active == h) onFront(h)
    }

    /** Something new came to the front: an app (keep its picture), or home (let the app fly). */
    private fun onFront(pkg: String) {
        if (!flyInHere()) return
        val h = home() ?: return
        when {
            pkg == h -> {
                val app = frontApp ?: return
                frontApp = null
                // A picture from just before the home gesture, not one of the window shrinking.
                val shot = finishAppSnapshots(SystemClock.uptimeMillis() - 300)
                showFlyIn(app, shot)
            }
            pkg == packageName || isLocked() -> Unit
            launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null && !isKeyboard(pkg) } -> {
                // The next app is opening: a fly-in still going makes way at once.
                if (flight.value != null) endFlight()
                if (pkg != frontApp) {
                    frontApp = pkg
                    startAppSnapshots()
                    // Ready ahead of time, so the fly-in starts the moment the app is closed.
                    prepareFlight(pkg)
                }
            }
        }
    }

    /** One fly-in: everything it needs, handed to the waiting window. */
    private class Flight(
        val app: dev.hearth.launcher.data.AppInfo,
        val snapshot: androidx.compose.ui.graphics.ImageBitmap?,
        val island: DpSize?,
        val landing: GlimmerLink.Landing,
        val target: androidx.compose.ui.geometry.Offset,
        val settings: dev.hearth.launcher.data.LauncherSettings,
        val look: dev.hearth.launcher.ui.FlyInLook?,
    )

    private val flight = androidx.compose.runtime.mutableStateOf<Flight?>(null)
    private var flyWindow: android.view.View? = null
    private var flyParams: android.view.WindowManager.LayoutParams? = null
    private var preparedApp: Pair<String, dev.hearth.launcher.data.AppInfo>? = null
    @Volatile
    private var preparedLook: Pair<String, dev.hearth.launcher.ui.FlyInLook>? = null

    private fun appInfo(pkg: String): dev.hearth.launcher.data.AppInfo? {
        preparedApp?.let { (p, info) -> if (p == pkg) return info }
        return runCatching {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            dev.hearth.launcher.data.AppInfo(
                label = pm.getApplicationLabel(info).toString(),
                component = pm.getLaunchIntentForPackage(pkg)?.component ?: android.content.ComponentName(pkg, pkg),
                user = android.os.Process.myUserHandle(),
                icon = pm.getApplicationIcon(info).toBitmap(192, 192).asImageBitmap(),
            )
        }.getOrNull()
    }

    /**
     * While an app is open: its details are looked up and the fly-in's window waits, tiny and
     * empty, so closing the app doesn't have to build anything first.
     */
    private fun prepareFlight(pkg: String) {
        appInfo(pkg)?.let { info ->
            preparedApp = pkg to info
            // Its colors and small icon, worked out in the background.
            if (preparedLook?.first != pkg) {
                val app = applicationContext
                Thread { runCatching { preparedLook = pkg to dev.hearth.launcher.ui.flyInLook(app, info) } }.start()
            }
        }
        if (flyWindow != null) return
        val compose = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GlimmerService)
            setViewTreeSavedStateRegistryOwner(this@GlimmerService)
            setContent {
                val f = flight.value
                if (f != null) {
                    androidx.compose.runtime.CompositionLocalProvider(dev.hearth.launcher.ui.LocalSettings provides f.settings) {
                        dev.hearth.launcher.ui.GlimmerFlyIn(
                            app = f.app,
                            island = f.island,
                            onPulse = { pulseGlimmer() },
                            onDone = { endFlight() },
                            snapshot = f.snapshot,
                            style = f.settings.glimmerFlyInStyle,
                            landing = f.landing,
                            onArrive = { icon, color -> arriveGlimmer(icon, color) },
                            target = f.target,
                            look = f.look,
                        )
                    }
                }
            }
        }
        val params = android.view.WindowManager.LayoutParams(
            1,
            1,
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
        val wm = getSystemService(android.view.WindowManager::class.java) ?: return
        if (runCatching { wm.addView(compose, params) }.isSuccess) {
            flyWindow = compose
            flyParams = params
        }
    }

    /**
     * The closing app flies into the island – the same fly-in as in Hearth (the app itself,
     * from its last picture), drawn by Glimmer over whatever launcher is home and aimed at the
     * island's real place on screen.
     */
    private fun showFlyIn(pkg: String, shot: HardwareBuffer?) {
        val settings = SettingsRepository(this).settings.value
        val controller = glimmer
        val app = appInfo(pkg)
        if (app == null || controller == null) {
            shot?.let { runCatching { it.close() } }
            return
        }
        if (flyWindow == null) prepareFlight(pkg)
        val view = flyWindow
        val params = flyParams
        val wm = getSystemService(android.view.WindowManager::class.java)
        if (view == null || params == null || wm == null) {
            shot?.let { runCatching { it.close() } }
            return
        }
        val picture = shot?.let { hb ->
            runCatching {
                android.graphics.Bitmap.wrapHardwareBuffer(hb, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            }.getOrNull()
        }
        val status = GlimmerBridgeProvider.handle(this, GlimmerLink.STATUS, null, null)
        val landing = GlimmerLink.Landing(
            status?.getFloat("lw") ?: 0f,
            status?.getInt("ox") ?: 0,
            status?.getInt("oy") ?: 0,
        )
        val center = controller.islandCenterOnScreen()
        val next = Flight(
            app = app,
            snapshot = picture?.asImageBitmap(),
            island = controller.islandSize.takeIf { it.width.value > 0f },
            landing = landing,
            target = androidx.compose.ui.geometry.Offset(center.x, center.y),
            settings = settings,
            look = preparedLook?.takeIf { it.first == pkg }?.second,
        )
        // The waiting window covers the screen; the flight starts as soon as it's laid out.
        params.width = android.view.WindowManager.LayoutParams.MATCH_PARENT
        params.height = android.view.WindowManager.LayoutParams.MATCH_PARENT
        runCatching { wm.updateViewLayout(view, params) }
        view.doOnNextLayout { flight.value = next }
        handler.removeCallbacks(endFlightLater)
        // Never left on screen, whatever happens.
        handler.postDelayed(endFlightLater, 4000)
        // The picture is shared with the window; let it go once the flight is surely over.
        shot?.let { hb -> handler.postDelayed({ runCatching { hb.close() } }, 5000) }
    }

    private val endFlightLater = Runnable { endFlight() }

    /** The flight is over: the window goes back to waiting, tiny and empty. */
    private fun endFlight() {
        flight.value = null
        val view = flyWindow ?: return
        val params = flyParams ?: return
        params.width = 1
        params.height = 1
        runCatching { getSystemService(android.view.WindowManager::class.java)?.updateViewLayout(view, params) }
    }

    private fun removeFlyWindow() {
        flight.value = null
        handler.removeCallbacks(endFlightLater)
        val view = flyWindow ?: return
        flyWindow = null
        flyParams = null
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
