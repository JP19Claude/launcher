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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

/**
 * Makes the Hearth control center available in every app, as close to replacing the
 * system one as Android allows: an invisible strip over the right part of the status bar
 * catches the swipe down and opens the glass control center as an overlay.
 * The left part keeps opening the normal notification shade.
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
    private var panel: View? = null
    private var controls: SystemControls? = null
    private var media: MediaRepository? = null
    private var glimmer: GlimmerController? = null

    private fun restartGlimmer() {
        val current = SettingsRepository(this).settings.value
        glimmer?.stop()
        if (current.glimmerEnabled) glimmer?.start(current)
    }
    private val handler = Handler(Looper.getMainLooper())

    /** Read from the settings; see [onAccessibilityEvent]. */
    @Volatile private var interceptShade = false

    /** While Hearth itself opened the system shade, it must not be closed again right away. */
    private var allowSystemShadeUntil = 0L

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "triggerZone" -> if (trigger != null) addTrigger()
            SettingsRepository.KEY_INTERCEPT -> interceptShade = prefs.getBoolean(key, false)
            in SettingsRepository.GLIMMER_KEYS -> restartGlimmer()
        }
    }

    // Hide the strip on the lock screen, bring it back once unlocked.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    hidePanel()
                    removeTrigger()
                }
                Intent.ACTION_USER_PRESENT -> addTrigger()
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
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!locked) addTrigger()
        glimmer = GlimmerController(this)
        restartGlimmer()
    }

    /**
     * "Replace the system control center": when the system's notification shade or quick
     * settings open (from a swipe the strip didn't catch), close them and show Hearth's
     * control center instead. Only window changes of the system UI are delivered here.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!interceptShade || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != SYSTEM_UI) return
        if (SystemClock.elapsedRealtime() < allowSystemShadeUntil) return
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return
        val text = buildString {
            event.text?.forEach { append(it).append(' ') }
            append(event.contentDescription ?: "").append(' ')
            append(event.className ?: "")
        }.lowercase()
        if (ShadeWords.none { it in text }) return

        dismissSystemShade()
        handler.postDelayed({ showPanel() }, 150)
    }

    private fun dismissSystemShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

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
        hidePanel()
        removeTrigger()
        glimmer?.stop()
        glimmer = null
        runCatching { unregisterReceiver(screenReceiver) }
        getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        controls?.close()
        controls = null
        handler.removeCallbacksAndMessages(null)
    }

    /** Pulls down the normal notification shade. */
    fun showNotifications(): Boolean {
        allowSystemShadeUntil = SystemClock.elapsedRealtime() + SYSTEM_SHADE_GRACE_MS
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /** Opens the system's own quick settings, in case something is only there. */
    fun showSystemQuickSettings(): Boolean {
        allowSystemShadeUntil = SystemClock.elapsedRealtime() + SYSTEM_SHADE_GRACE_MS
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val height = if (id != 0) resources.getDimensionPixelSize(id) else 0
        return height.coerceAtLeast(dp(24))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTrigger() {
        val wm = windowManager ?: return
        removeTrigger()
        val zone = SettingsRepository(this).settings.value.triggerZone
        val width = (resources.displayMetrics.widthPixels * zone.fraction).roundToInt()

        val view = View(this)
        var startY = 0f
        var opened = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    opened = false
                }
                MotionEvent.ACTION_MOVE -> if (!opened && event.rawY - startY > dp(18)) {
                    opened = true
                    showPanel()
                }
                MotionEvent.ACTION_UP -> if (!opened && event.rawY - startY > dp(6)) showPanel()
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

    fun showPanel() {
        val wm = windowManager ?: return
        if (panel != null) return
        val settings = SettingsRepository(this).settings.value
        val systemControls = controls ?: SystemControls(this).also { controls = it }
        val mediaRepository = media ?: MediaRepository(this).also { media = it }

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
                OverlayControlCenter(
                    settings = settings,
                    controls = systemControls,
                    media = mediaRepository,
                    onClose = this@ControlCenterService::hidePanel,
                    onOpenLauncherSettings = {
                        hidePanel()
                        val intent = Intent(this@ControlCenterService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                        runCatching { startActivity(intent) }
                    },
                    onShowNotifications = {
                        hidePanel()
                        showNotifications()
                    },
                    onShowSystemQuickSettings = {
                        hidePanel()
                        showSystemQuickSettings()
                    },
                )
            }
        }
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Hearth control center"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            // Real glass over whatever app is open (Android 12+, if the phone supports it).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(36)
            }
        }
        if (runCatching { wm.addView(root, params) }.isSuccess) panel = root
    }

    fun hidePanel() {
        val view = panel ?: return
        panel = null
        runCatching { windowManager?.removeView(view) }
    }

    companion object {
        /** The running service, if the user turned it on in the accessibility settings. */
        @Volatile
        var instance: ControlCenterService? = null
            private set

        val isEnabled: Boolean get() = instance != null

        private const val SYSTEM_UI = "com.android.systemui"
        private const val SYSTEM_SHADE_GRACE_MS = 20_000L

        /** How the system names its shade (Android, One UI, ColorOS; German and English). */
        private val ShadeWords = listOf(
            "notification shade", "quick settings", "quick panel", "notification panel",
            "benachrichtigungsfeld", "benachrichtigungsleiste", "benachrichtigungsbereich",
            "benachrichtigungsfenster", "schnelleinstellungen", "schnellzugriff", "schnellfeld",
            "mitteilungszentrale", "kontrollzentrum", "statusleiste erweitert",
        )
    }
}
