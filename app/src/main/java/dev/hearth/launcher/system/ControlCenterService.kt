package dev.hearth.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.MainActivity
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.SystemControls
import dev.hearth.launcher.ui.OverlayControlCenter
import dev.hearth.launcher.ui.PanelReveal
import kotlin.math.roundToInt

/**
 * Makes the Hearth control center available in every app, as close to replacing the
 * system one as Android allows: an invisible strip over the status bar catches the swipe
 * down and pulls the glass control center out with the finger, like on ColorOS.
 * If the system shade opens anyway (another part of the status bar, a gesture), it is
 * closed again and Hearth's control center shows instead.
 *
 * Runs as an accessibility service, because only those may draw over the status bar.
 */
class ControlCenterService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var trigger: View? = null
    private var controls: SystemControls? = null
    private var media: MediaRepository? = null
    private var glimmer: GlimmerController? = null

    /**
     * The panel window stays added and only hides between uses, so opening doesn't have to
     * set up a window and Compose from scratch each time (that was the delay).
     */
    private var panelRoot: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelVisible = false
    private var panelOpen by mutableStateOf(false)
    private var panelSettings by mutableStateOf(LauncherSettings())
    private val reveal = PanelReveal()
    private var blurRadius = -1

    private fun restartGlimmer() {
        val current = SettingsRepository(this).settings.value
        glimmer?.stop()
        if (current.glimmerEnabled) glimmer?.start(current)
    }
    private val handler = Handler(Looper.getMainLooper())

    /** Read from the settings; see [onAccessibilityEvent]. */
    @Volatile private var interceptShade = false

    /**
     * Hearth itself opened the system shade (notifications, system switches): leave it open
     * until it was closed again. [allowShadeUntil] is the fallback if its closing isn't seen.
     */
    private var allowShade = false
    private var allowShadeSeen = false
    private var allowShadeUntil = 0L

    private var shadeCheckPending = false
    private val shadeCheck = Runnable {
        shadeCheckPending = false
        checkSystemShade()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "triggerZone" -> if (trigger != null) addTrigger()
            SettingsRepository.KEY_INTERCEPT -> {
                interceptShade = prefs.getBoolean(key, false)
                // Covering the whole status bar is what makes the replacement reliable.
                if (trigger != null) addTrigger()
            }
            in SettingsRepository.GLIMMER_KEYS -> restartGlimmer()
        }
    }

    // Hide the strip on the lock screen, bring it back as soon as the phone is usable.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    hidePanel(immediately = true)
                    removeTrigger()
                }
                // Screen back on without the lock screen (it locks only after a while):
                // there's no "user present" then, the strip used to stay missing.
                Intent.ACTION_SCREEN_ON -> handler.postDelayed({ ensureTrigger() }, 300)
                Intent.ACTION_USER_PRESENT -> ensureTrigger()
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
        windowManager = getSystemService(WindowManager::class.java)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        interceptShade = prefs.getBoolean(SettingsRepository.KEY_INTERCEPT, false)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        controls = SystemControls(this).also { it.prefetch() }
        media = MediaRepository(this)
        ensureTrigger()
        glimmer = GlimmerController(this)
        restartGlimmer()
        // Warm up the panel window once the service is settled.
        handler.postDelayed({ ensurePanelWindow() }, 1200)
    }

    /**
     * "Replace the system control center": when the system's notification shade or quick
     * settings open (from a swipe the strip didn't catch), close them and show Hearth's
     * control center instead. Only events of the system UI are delivered here.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Any sign of life from the status bar: make sure the strip is there.
        if (trigger == null) ensureTrigger()
        if (!interceptShade) return
        if (event.packageName?.toString() != SYSTEM_UI) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val text = buildString {
                    event.text?.forEach { append(it).append(' ') }
                    append(event.contentDescription ?: "").append(' ')
                    append(event.className ?: "")
                }.lowercase()
                if (ShadeWords.any { it in text } && !isLocked() && !shadeAllowed()) {
                    takeOverShade()
                } else {
                    scheduleShadeCheck(0)
                }
            }
            // Fires many times while the shade moves; looked at in small batches.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleShadeCheck(90)
        }
    }

    private fun scheduleShadeCheck(delayMs: Long) {
        if (shadeCheckPending) return
        shadeCheckPending = true
        handler.postDelayed(shadeCheck, delayMs)
    }

    private fun checkSystemShade() {
        if (!interceptShade && !allowShade) return
        val open = isSystemShadeOpen()
        if (allowShade) {
            if (open) {
                allowShadeSeen = true
            } else if (allowShadeSeen || SystemClock.elapsedRealtime() > allowShadeUntil) {
                // The shade Hearth opened was closed again: from now on, replace it again.
                allowShade = false
                allowShadeSeen = false
            }
            return
        }
        if (open && interceptShade && !isLocked()) takeOverShade()
    }

    private fun shadeAllowed(): Boolean {
        if (!allowShade) return false
        if (!allowShadeSeen && SystemClock.elapsedRealtime() > allowShadeUntil) {
            allowShade = false
            return false
        }
        return true
    }

    /** Hearth's panel first (it sits above the shade), then the shade goes away underneath. */
    private fun takeOverShade() {
        showPanel()
        dismissSystemShade()
        // A finger still on the shade can hold it open; try again once it let go.
        handler.postDelayed({ if (isSystemShadeOpen()) dismissSystemShade() }, 350)
        handler.postDelayed({ if (isSystemShadeOpen()) dismissSystemShade() }, 900)
    }

    /**
     * Whether the system's quick settings are on screen: a system UI window showing one of
     * the quick settings views. A heads-up notification lives in the same window, so the
     * window alone doesn't count; the quick settings are hidden while it's only that.
     */
    private fun isSystemShadeOpen(): Boolean {
        if (isLocked()) return false
        val list = runCatching { windows }.getOrNull() ?: return false
        for (window in list) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() != SYSTEM_UI) continue
            if (showsQuickSettings(root)) return true
        }
        return false
    }

    private fun showsQuickSettings(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val bounds = Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && id.startsWith(SYSTEM_UI_ID)) {
                val name = id.substring(SYSTEM_UI_ID.length)
                if (QuickSettingsIds.any { name.startsWith(it) || name.contains(it) } && node.isVisibleToUser) {
                    node.getBoundsInScreen(bounds)
                    if (bounds.height() > 0 && bounds.width() > 0) return true
                }
            }
            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let(queue::add)
            }
        }
        return false
    }

    private fun dismissSystemShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Width and status bar height change with rotation.
        if (trigger != null) addTrigger()
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
        removePanelWindow()
        removeTrigger()
        glimmer?.stop()
        glimmer = null
        runCatching { unregisterReceiver(screenReceiver) }
        getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        controls?.close()
        controls = null
        handler.removeCallbacksAndMessages(null)
        shadeCheckPending = false
    }

    private fun allowSystemShade() {
        allowShade = true
        allowShadeSeen = false
        allowShadeUntil = SystemClock.elapsedRealtime() + SYSTEM_SHADE_GRACE_MS
    }

    /** Pulls down the normal notification shade. */
    fun showNotifications(): Boolean {
        allowSystemShade()
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /** Opens the system's own quick settings, in case something is only there. */
    fun showSystemQuickSettings(): Boolean {
        allowSystemShade()
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val height = if (id != 0) resources.getDimensionPixelSize(id) else 0
        return height.coerceAtLeast(dp(24))
    }

    private fun ensureTrigger() {
        if (trigger != null || windowManager == null) return
        if (isLocked()) return
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return
        addTrigger()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTrigger() {
        val wm = windowManager ?: return
        removeTrigger()
        val zone = SettingsRepository(this).settings.value.triggerZone
        // Replacing the system control center: the whole status bar opens Hearth's,
        // so the system's doesn't flash up first. Notifications are a button inside.
        val fraction = if (interceptShade) 1f else zone.fraction
        val width = (resources.displayMetrics.widthPixels * fraction).roundToInt()

        val view = View(this)
        var startY = 0f
        var pulling = false
        var tracker: VelocityTracker? = null
        val distance = dp(PULL_DISTANCE_DP).toFloat()
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    // Like ColorOS: pulled from the left, the notifications come first.
                    val settings = SettingsRepository(this).settings.value
                    reveal.startPage = if (
                        interceptShade && settings.ccNotifications &&
                        event.rawX < resources.displayMetrics.widthPixels / 2f
                    ) 1 else 0
                    pulling = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    // Read the toggles while the finger is still moving.
                    controls?.prefetch()
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    val dy = event.rawY - startY
                    if (!pulling && dy > dp(5)) {
                        pulling = true
                        startPull()
                    }
                    if (pulling) reveal.progress = (dy / distance).coerceIn(0f, 1.06f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event)
                    val velocity = tracker?.let {
                        it.computeCurrentVelocity(1000)
                        it.yVelocity
                    } ?: 0f
                    tracker?.recycle()
                    tracker = null
                    if (pulling) {
                        endPull(velocity, distance)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && event.rawY - startY > dp(2)) {
                        showPanel(reveal.startPage)
                    }
                    pulling = false
                }
            }
            true
        }

        val params = WindowManager.LayoutParams(
            width,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Hearth control center trigger"
        }
        if (runCatching { wm.addView(view, params) }.isSuccess) trigger = view
    }

    private fun removeTrigger() {
        val view = trigger ?: return
        trigger = null
        runCatching { windowManager?.removeView(view) }
    }

    // ---- Panel ----

    private fun shownFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0)

    private fun hiddenFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun ensurePanelWindow(): Boolean {
        if (panelRoot != null) return true
        val wm = windowManager ?: return false
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hidePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ControlCenterService)
            setViewTreeSavedStateRegistryOwner(this@ControlCenterService)
            setContent {
                val systemControls = controls
                val mediaRepository = media
                if (panelOpen && systemControls != null && mediaRepository != null) {
                    OverlayControlCenter(
                        settings = panelSettings,
                        controls = systemControls,
                        media = mediaRepository,
                        reveal = reveal,
                        onClosed = this@ControlCenterService::onPanelClosed,
                        onRevealChanged = this@ControlCenterService::setPanelBlur,
                        onOpenLauncherSettings = {
                            hidePanel(immediately = true)
                            val intent = Intent(this@ControlCenterService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                            runCatching { startActivity(intent) }
                        },
                        onShowNotifications = {
                            hidePanel(immediately = true)
                            showNotifications()
                        },
                        onShowSystemQuickSettings = {
                            hidePanel(immediately = true)
                            showSystemQuickSettings()
                        },
                    )
                }
            }
        }
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            hiddenFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Hearth control center"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = 0
        }
        if (runCatching { wm.addView(root, params) }.isFailure) return false
        panelRoot = root
        panelParams = params
        panelVisible = false
        blurRadius = 0
        return true
    }

    private fun makePanelVisible(): Boolean {
        if (panelVisible) return true
        if (!ensurePanelWindow()) return false
        val root = panelRoot ?: return false
        val params = panelParams ?: return false
        panelSettings = SettingsRepository(this).settings.value
        panelOpen = true
        params.flags = shownFlags()
        root.visibility = View.VISIBLE
        if (runCatching { windowManager?.updateViewLayout(root, params) }.isFailure) {
            removePanelWindow()
            return false
        }
        panelVisible = true
        return true
    }

    /** The finger started pulling on the status bar: the panel follows it. */
    private fun startPull() {
        if (panelVisible && !reveal.dragging && reveal.target == 1f) return
        reveal.progress = 0f
        reveal.flingVelocity = 0f
        reveal.dragging = true
        if (!makePanelVisible()) reveal.dragging = false
    }

    /** The finger let go: open fully, or slide back if it was only a small tug. */
    private fun endPull(velocity: Float, distance: Float) {
        if (!reveal.dragging) return
        val open = when {
            velocity > dp(400) -> true
            velocity < -dp(300) -> false
            else -> reveal.progress > 0.32f
        }
        reveal.flingVelocity = (velocity / distance).coerceIn(-12f, 12f)
        reveal.target = if (open) 1f else 0f
        reveal.dragging = false
    }

    fun showPanel(page: Int = 0) {
        if (!panelVisible) reveal.startPage = page
        reveal.flingVelocity = 0f
        reveal.dragging = false
        reveal.target = 1f
        makePanelVisible()
    }

    /** Slides the panel away; [immediately] for screen off and opening other things. */
    fun hidePanel(immediately: Boolean = false) {
        if (!panelVisible) return
        if (immediately) {
            onPanelClosed()
        } else {
            reveal.dragging = false
            reveal.flingVelocity = 0f
            reveal.target = 0f
        }
    }

    /** Called when the panel finished sliding away. */
    private fun onPanelClosed() {
        reveal.dragging = false
        reveal.target = 0f
        reveal.progress = 0f
        panelOpen = false
        val root = panelRoot ?: return
        val params = panelParams ?: return
        if (!panelVisible) return
        panelVisible = false
        root.visibility = View.GONE
        params.flags = hiddenFlags()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.blurBehindRadius = 0
        blurRadius = 0
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    /** The blur of the app behind grows with the panel, in a few steps (each is a window update). */
    private fun setPanelBlur(fraction: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !panelVisible) return
        val root = panelRoot ?: return
        val params = panelParams ?: return
        val steps = 5
        val step = (fraction.coerceIn(0f, 1f) * steps).roundToInt()
        // Strength from "Hintergrund weichzeichnen" in the settings.
        val maxRadius = dp((BLUR_MIN_DP + BLUR_RANGE_DP * panelSettings.ccBlur.coerceIn(0f, 1f)).roundToInt())
        val radius = maxRadius * step / steps
        if (radius == blurRadius) return
        blurRadius = radius
        params.blurBehindRadius = radius
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    private fun removePanelWindow() {
        val root = panelRoot ?: return
        panelRoot = null
        panelParams = null
        panelVisible = false
        panelOpen = false
        runCatching { windowManager?.removeView(root) }
    }

    companion object {
        /** The running service, if the user turned it on in the accessibility settings. */
        @Volatile
        var instance: ControlCenterService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        private const val SYSTEM_UI = "com.android.systemui"
        private const val SYSTEM_UI_ID = "com.android.systemui:id/"
        private const val SYSTEM_SHADE_GRACE_MS = 20_000L
        private const val PULL_DISTANCE_DP = 260
        private const val BLUR_MIN_DP = 6
        private const val BLUR_RANGE_DP = 74
        private const val MAX_NODES = 220

        /** How the system names its shade (Android, One UI, ColorOS; German and English). */
        private val ShadeWords = listOf(
            "notification shade", "quick settings", "quick panel", "notification panel",
            "benachrichtigungsfeld", "benachrichtigungsleiste", "benachrichtigungsbereich",
            "benachrichtigungsfenster", "schnelleinstellungen", "schnellzugriff", "schnellfeld",
            "mitteilungszentrale", "kontrollzentrum", "statusleiste erweitert", "schnelleinstellungsbereich",
        )

        /** View ids of the system's quick settings (AOSP, One UI and others). */
        private val QuickSettingsIds = listOf(
            "qs_", "quick_qs", "quick_settings", "quick_panel", "qspanel", "brightness_slider",
            "sec_qs", "expanded_qs",
        )
    }
}
