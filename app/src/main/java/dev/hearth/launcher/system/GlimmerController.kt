package dev.hearth.launcher.system

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.app.NotificationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.data.GlimmerStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LiveNotice
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.NoticeKind
import dev.hearth.launcher.data.NotificationHub
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.GlimmerIsland
import dev.hearth.launcher.ui.Glyph
import dev.hearth.launcher.ui.ISLAND_HEIGHT_DP
import dev.hearth.launcher.ui.IslandContent
import dev.hearth.launcher.ui.islandKey
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.sendIntent
import dev.hearth.launcher.ui.theme.HearthTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Glimmer, Hearth's island around the front camera, shown over every app.
 * Lives in the accessibility service, because only its windows may sit on the status bar.
 */
class GlimmerController(private val service: ControlCenterService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val media = MediaRepository(service)

    private val expanded = MutableStateFlow(false)
    private val alert = MutableStateFlow<IslandContent.Alert?>(null)
    private val message = MutableStateFlow<LiveNotice?>(null)
    private val landscape = MutableStateFlow(false)

    /** The activity brought to the front by a swipe or a tap on the small bubble. */
    private val focusKey = MutableStateFlow<String?>(null)
    private var lastOrder: List<String> = emptyList()

    private var batteryFull = false
    private var dndOn: Boolean? = null

    /** Earbuds and headphones coming and going (the first report lists what's already there). */
    private var audioCallback: AudioDeviceCallback? = null
    private var audioCallbackPrimed = false
    private fun headphones(devices: Array<out AudioDeviceInfo>): List<AudioDeviceInfo> = devices.filter { d ->
        d.isSink && d.type in HeadphoneTypes
    }

    /** On the lock screen Glimmer shows live activities, but no empty idle pill. */
    private val locked = MutableStateFlow(false)
    private fun updateLocked() {
        locked.value = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var settings = LauncherSettings()
    private var receiverRegistered = false

    private val density get() = service.resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    updateLocked()
                    // Unlocking with the fingerprint can come a moment after the screen turns on.
                    handler.postDelayed({ updateLocked() }, 600)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    val level = batteryLevel()
                    flash(IslandContent.Alert(Glyph.Spark, "Lädt", "$level %", GREEN, level / 100f))
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val full = plugged && (
                        intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL ||
                            level * 100 / scale >= 100
                        )
                    if (full && !batteryFull && settings.glimmerAlerts) {
                        flash(IslandContent.Alert(Glyph.Battery, "Vollständig geladen", "100 %", GREEN, 1f))
                    }
                    batteryFull = full
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val filter = service.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                    val on = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                        filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                    if (dndOn != null && dndOn != on && settings.glimmerAlerts) {
                        flash(IslandContent.Alert(Glyph.Moon, "Nicht stören", if (on) "An" else "Aus", PURPLE))
                    }
                    dndOn = on
                }
                Intent.ACTION_BATTERY_LOW -> {
                    val level = batteryLevel()
                    flash(IslandContent.Alert(Glyph.Spark, "Akku schwach", "$level %", RED, level / 100f))
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                        AudioManager.RINGER_MODE_SILENT -> flash(IslandContent.Alert(Glyph.Bell, "Lautlos", "An", ORANGE))
                        AudioManager.RINGER_MODE_VIBRATE -> flash(IslandContent.Alert(Glyph.Vibrate, "Vibration", "An", ORANGE))
                        AudioManager.RINGER_MODE_NORMAL -> flash(IslandContent.Alert(Glyph.Bell, "Klingeln", "An", Color.White))
                    }
                }
            }
        }
    }

    private fun batteryLevel(): Int {
        val sticky = ContextCompat.registerReceiver(
            service, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level < 0 || scale <= 0) 0 else level * 100 / scale
    }

    private var alertToken = 0
    private fun flash(content: IslandContent.Alert) {
        alert.value = content
        val token = ++alertToken
        handler.postDelayed({ if (token == alertToken && !expanded.value) alert.value = null }, 3500)
    }

    private var messageToken = 0
    private fun flashMessage(notice: LiveNotice) {
        message.value = notice
        val token = ++messageToken
        handler.postDelayed({ if (token == messageToken && !expanded.value) message.value = null }, 4500)
    }

    fun start(newSettings: LauncherSettings) {
        settings = newSettings
        if (root != null) return
        landscape.value = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        media.start()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        updateLocked()
        // Start from how things are, so only real changes flash.
        dndOn = service.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.let { f ->
            f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        batteryFull = true
        startHeadphoneWatch()
        ContextCompat.registerReceiver(service, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        scope = MainScope().also { s ->
            s.launch {
                NotificationHub.incoming.collect { if (settings.glimmerMessages) flashMessage(it) }
            }
        }
        addWindow()
    }

    private fun startHeadphoneWatch() {
        val audio = service.getSystemService(AudioManager::class.java) ?: return
        audioCallbackPrimed = false
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                // The first call lists everything already connected; that's no news.
                if (!audioCallbackPrimed) {
                    audioCallbackPrimed = true
                    return
                }
                val name = headphones(addedDevices).firstOrNull()?.productName?.toString()?.takeIf { it.isNotBlank() }
                    ?: if (headphones(addedDevices).isNotEmpty()) "Kopfhörer" else return
                if (settings.glimmerAlerts) flash(IslandContent.Alert(Glyph.Headphones, name, "Verbunden", Color.White))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val name = headphones(removedDevices).firstOrNull()?.productName?.toString()?.takeIf { it.isNotBlank() }
                    ?: if (headphones(removedDevices).isNotEmpty()) "Kopfhörer" else return
                if (settings.glimmerAlerts) flash(IslandContent.Alert(Glyph.Headphones, name, "Getrennt", Color.White.copy(alpha = 0.7f)))
            }
        }
        audioCallback = callback
        runCatching { audio.registerAudioDeviceCallback(callback, handler) }
    }

    fun stop() {
        audioCallback?.let { cb ->
            runCatching { service.getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(cb) }
        }
        audioCallback = null
        focusKey.value = null
        handler.removeCallbacksAndMessages(null)
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        params = null
        media.stop()
        if (receiverRegistered) runCatching { service.unregisterReceiver(systemReceiver) }
        receiverRegistered = false
        scope?.cancel()
        scope = null
        expanded.value = false
    }

    fun onConfigurationChanged(config: Configuration) {
        landscape.value = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        // The camera moves with rotation; place the window again.
        params?.let { p ->
            val (x, _) = cameraCenter()
            p.x = x
            root?.let { runCatching { windowManager.updateViewLayout(it, p) } }
        }
    }

    /** Camera position: horizontal offset from the screen's middle, and its vertical center (px). */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun cameraCenter(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val cutout = metrics.windowInsets.displayCutout?.boundingRectTop
            if (cutout != null && !cutout.isEmpty) {
                return (cutout.centerX() - metrics.bounds.width() / 2) to cutout.centerY()
            }
        }
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (id != 0) service.resources.getDimensionPixelSize(id) else dp(24f)
        return 0 to statusBar / 2
    }

    private fun topInsetPx(): Int = (cameraCenter().second - dp(IDLE_HEIGHT) / 2).coerceAtLeast(dp(4f))

    @SuppressLint("ClickableViewAccessibility")
    private fun addWindow() {
        val (cameraX, _) = cameraCenter()
        val topInset = topInsetPx()
        val frame = object : FrameLayout(service) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // A touch anywhere else on the screen folds the island back up.
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    collapse()
                    return false
                }
                return super.dispatchTouchEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(service)
        frame.setViewTreeSavedStateRegistryOwner(service)
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setContent {
                val playing by media.nowPlaying.collectAsStateWithLifecycle()
                val live by NotificationHub.active.collectAsStateWithLifecycle()
                val currentAlert by alert.collectAsStateWithLifecycle()
                val currentMessage by message.collectAsStateWithLifecycle()
                val isExpanded by expanded.collectAsStateWithLifecycle()
                val focus by focusKey.collectAsStateWithLifecycle()
                val sideways by landscape.collectAsStateWithLifecycle()
                val onLockScreen by locked.collectAsStateWithLifecycle()

                val ongoing = live.sortedBy { priority(it.kind) }
                val ordered = buildList<IslandContent> {
                    currentAlert?.let { add(it) }
                    currentMessage?.let { add(IslandContent.Message(it)) }
                    ongoing.firstOrNull { it.kind == NoticeKind.Call }?.let { add(IslandContent.Live(it)) }
                    playing?.takeIf { it.playing }?.let { add(IslandContent.Media(it)) }
                    ongoing.filter { it.kind != NoticeKind.Call }.forEach { add(IslandContent.Live(it)) }
                }
                // What was brought to the front stays there (alerts and messages still come first).
                val focused = ordered.firstOrNull { islandKey(it) == focus }
                val activities = if (focused != null && ordered.first() !is IslandContent.Alert && ordered.first() !is IslandContent.Message) {
                    listOf(focused) + ordered.filter { it !== focused }
                } else {
                    ordered
                }
                lastOrder = activities.map { islandKey(it) }
                // On the lock screen too, but without the empty idle pill there.
                val main = activities.firstOrNull()
                    ?: if (settings.glimmerIdlePill && !sideways && !onLockScreen) IslandContent.Idle else IslandContent.Hidden
                val second = activities.drop(1).firstOrNull()

                HearthTheme(dark = true) {
                    CompositionLocalProvider(
                        LocalSettings provides settings,
                        LocalGlassStyle provides GlassStyle.from(settings),
                    ) {
                        GlimmerIsland(
                            content = main,
                            secondary = second,
                            expanded = isExpanded && main !is IslandContent.Idle && main !is IslandContent.Hidden,
                            style = settings.glimmerStyle,
                            screenWidthDp = service.resources.configuration.screenWidthDp.toFloat(),
                            topInset = (topInset / density).dp,
                            media = media,
                            tapOpens = settings.glimmerTapOpens,
                            glow = settings.glimmerGlow,
                            onToggle = { expanded.value = !expanded.value },
                            onExpand = { expanded.value = true },
                            onSwap = this@GlimmerController::swap,
                            onFocus = { item ->
                                focusKey.value = islandKey(item)
                                expanded.value = true
                            },
                            onCollapse = this@GlimmerController::collapse,
                            onOpen = this@GlimmerController::open,
                            onTargetSize = this@GlimmerController::resizeTo,
                        )
                    }
                }
            }
        }
        frame.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val p = WindowManager.LayoutParams(
            dp(IDLE_WIDTH + 2 * PAD),
            topInset + dp(IDLE_HEIGHT + PAD),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = cameraX
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "Hearth Glimmer"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.glimmerStyle == GlimmerStyle.Glass) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(24f)
            }
        }
        if (runCatching { windowManager.addView(frame, p) }.isSuccess) {
            root = frame
            params = p
        }
    }

    private var resizeToken = 0

    /**
     * Keeps the window just as big as the island: growing happens at once (so the animation
     * isn't cut off), shrinking after the animation, so touches around it reach the app below.
     */
    private fun resizeTo(size: DpSize) {
        val p = params ?: return
        val view = root ?: return
        val topInset = topInsetPx()
        val hidden = size.width.value <= 0f
        val w = if (hidden) 1 else dp(size.width.value + 2 * PAD)
        val h = if (hidden) 1 else topInset + dp(size.height.value + PAD)
        val token = ++resizeToken
        fun applySize(width: Int, height: Int) {
            p.width = width
            p.height = height
            p.flags = if (hidden) {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            runCatching { windowManager.updateViewLayout(view, p) }
        }
        if (w >= p.width && h >= p.height && !hidden) {
            applySize(w, h)
        } else {
            if (!hidden) applySize(max(w, p.width), max(h, p.height))
            handler.postDelayed({ if (token == resizeToken) applySize(w, h) }, 450)
        }
    }

    /** Sideways swipe: the second activity comes to the front. */
    private fun swap() {
        val next = lastOrder.getOrNull(1) ?: return
        focusKey.value = next
    }

    private fun collapse() {
        if (!expanded.value) return
        expanded.value = false
        // Short-lived things that were kept while open can go now.
        handler.postDelayed({
            if (!expanded.value) {
                alert.value = null
                message.value = null
            }
        }, 1200)
    }

    /** Long press (or tap on the expanded card): open what the island is about. */
    private fun open(content: IslandContent) {
        collapse()
        when (content) {
            is IslandContent.Media -> media.openPlayer()
            is IslandContent.Live -> sendIntent(service, content.notice.contentIntent)
            is IslandContent.Message -> {
                sendIntent(service, content.notice.contentIntent)
                message.value = null
            }
            else -> Unit
        }
    }

    private fun priority(kind: NoticeKind) = when (kind) {
        NoticeKind.Call -> 0
        NoticeKind.Navigation -> 1
        NoticeKind.Timer -> 2
        NoticeKind.Progress -> 3
        NoticeKind.Message -> 4
    }

    private companion object {
        const val IDLE_WIDTH = 108f
        const val IDLE_HEIGHT = ISLAND_HEIGHT_DP
        const val PAD = 8f
        val GREEN = Color(0xFF34C759)
        val ORANGE = Color(0xFFFF9F0A)
        val RED = Color(0xFFFF453A)
        val PURPLE = Color(0xFF8E7CFF)
        val HeadphoneTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
