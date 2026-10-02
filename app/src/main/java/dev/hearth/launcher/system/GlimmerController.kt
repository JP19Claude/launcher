package dev.hearth.launcher.system

import android.os.SystemClock
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import androidx.compose.runtime.LaunchedEffect
import android.os.PowerManager
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
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
import android.view.Display
import android.hardware.display.DisplayManager
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
import dev.hearth.launcher.data.GlimmerUnlock
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
class GlimmerController(private val service: GlimmerService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val media = MediaRepository(service)

    private val expanded = MutableStateFlow(false)
    private val alert = MutableStateFlow<IslandContent.Alert?>(null)
    private val message = MutableStateFlow<LiveNotice?>(null)
    private val landscape = MutableStateFlow(false)

    /**
     * Paused music stays in Glimmer for a while (like on the iPhone), so it can be resumed
     * from there; it used to vanish the moment it was paused.
     */
    private val keepMedia = MutableStateFlow(false)
    private val dropMedia = Runnable { keepMedia.value = false }

    /** A short hop and shimmer, e.g. when an app just flew in. */
    private val pulse = MutableStateFlow(0)
    fun pulse() {
        pulse.value = pulse.value + 1
    }

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

    /**
     * Face ID-style moment while unlocking: scanning when the screen wakes locked, a green tick
     * the moment the phone knows you (even if it stays on the lock screen), the symbol turning
     * red and the island shaking when the face or finger isn't recognized.
     */
    private val unlock = MutableStateFlow<IslandContent.Unlock?>(null)
    private var unlockToken = 0
    /** The tick was shown for this wake-up; unlocking afterwards doesn't show it again. */
    private var unlockTicked = false
    /** Counts failed tries, so every one shakes again. */
    private var unlockAttempt = 0
    private var lastFailAt = 0L
    /** The scan symbol is up and waiting for the face (not a tick, not a shake). */
    private var scanStartedAt = 0L
    private var watchUntil = 0L
    private var watching = false
    private fun keyguard() = service.getSystemService(KeyguardManager::class.java)
    private fun showUnlock(success: Boolean, hideAfter: Long, failed: Boolean = false) {
        val symbol = settings.glimmerUnlock
        if (symbol == GlimmerUnlock.Off) return
        if (success) unlockTicked = true
        scanStartedAt = 0L
        unlock.value = IslandContent.Unlock(
            success = success,
            face = symbol == GlimmerUnlock.FaceId,
            failed = failed,
            attempt = unlockAttempt,
        )
        val token = ++unlockToken
        handler.postDelayed({ if (token == unlockToken) unlock.value = null }, hideAfter)
    }
    private fun hideUnlock() {
        unlockToken++
        scanStartedAt = 0L
        unlock.value = null
    }

    /**
     * Android doesn't tell apps about biometrics, but the device stops being "locked" the moment
     * it recognizes you, even while the lock screen is still up, so that's what is watched here.
     */
    private val unlockWatch = object : Runnable {
        override fun run() {
            watching = false
            if (unlockTicked) return
            val k = keyguard() ?: return
            if (!k.isDeviceLocked) {
                showUnlock(success = true, hideAfter = UNLOCK_DONE_MS)
                return
            }
            val now = SystemClock.uptimeMillis()
            // The scan ran out without the lock screen saying anything.
            if (scanStartedAt != 0L && now - scanStartedAt > UNLOCK_SCAN_MS) {
                // Face ID looks on its own, so finding no one counts as a miss; the finger may
                // simply not have been put on yet, so its symbol just goes quietly.
                if (settings.glimmerUnlock == GlimmerUnlock.FaceId) failUnlock() else hideUnlock()
            }
            if (now < watchUntil) {
                watching = true
                handler.postDelayed(this, UNLOCK_POLL_MS)
            }
        }
    }
    private fun watchUnlock() {
        watchUntil = SystemClock.uptimeMillis() + UNLOCK_WATCH_MS
        if (!watching) {
            watching = true
            handler.postDelayed(unlockWatch, UNLOCK_POLL_MS)
        }
    }
    private fun stopUnlockWatch() {
        handler.removeCallbacks(unlockWatch)
        watching = false
    }

    /** Whether lock screen texts are worth reading right now (cheap: called for system UI events). */
    fun wantsUnlockTexts(): Boolean =
        settings.glimmerUnlock != GlimmerUnlock.Off && locked.value && !unlockTicked

    /** The lock screen said something; a "not recognized" or "try again" is a failed try. */
    fun onLockScreenText(text: CharSequence) {
        if (!wantsUnlockTexts()) return
        val t = text.toString().lowercase()
        if (UnlockFailureWords.any { it in t }) failUnlock()
    }

    private fun failUnlock() {
        val k = keyguard() ?: return
        if (settings.glimmerUnlock == GlimmerUnlock.Off || unlockTicked || !k.isDeviceSecure || !k.isKeyguardLocked) return
        val now = SystemClock.uptimeMillis()
        // One try often comes as several events.
        if (now - lastFailAt < 700) return
        lastFailAt = now
        unlockAttempt++
        showUnlock(success = false, hideAfter = UNLOCK_FAIL_MS, failed = true)
        // Another try may follow: keep watching for the tick.
        watchUnlock()
    }

    /** Woken up locked: scan while the phone checks the face (or finger). */
    private fun startUnlockScan() {
        val kg = keyguard() ?: return
        if (settings.glimmerUnlock == GlimmerUnlock.Off || !kg.isDeviceSecure || !kg.isKeyguardLocked) return
        unlockTicked = false
        // Recognized already (the fingerprint woke the phone): straight to the tick.
        if (!kg.isDeviceLocked) {
            showUnlock(success = true, hideAfter = UNLOCK_DONE_MS)
            return
        }
        showUnlock(success = false, hideAfter = UNLOCK_SCAN_MS + 1000)
        scanStartedAt = SystemClock.uptimeMillis()
        watchUnlock()
    }

    /**
     * Earbuds report their battery: shown once after connecting, like AirPods on the iPhone
     * ("Galaxy Buds 85 %"). Names need the Bluetooth permission (asked in the Glimmer app).
     */
    private val lastBattery = HashMap<String, Long>()
    private var batteryReceiverRegistered = false
    private val earbudsBattery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
            if (level !in 0..100 || !settings.glimmerAlerts) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            // Headphones and speakers only (not watches or keyboards), when that can be told.
            val audio = runCatching {
                device?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            }.getOrDefault(true)
            if (!audio) return
            val key = runCatching { device?.address }.getOrNull() ?: "earbuds"
            val now = SystemClock.elapsedRealtime()
            if (now - (lastBattery[key] ?: Long.MIN_VALUE / 2) < 10 * 60_000L) return
            lastBattery[key] = now
            val name = runCatching { device?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Kopfhörer"
            flash(IslandContent.Alert(Glyph.Headphones, name, "$level %", if (level <= 20) RED else GREEN, level / 100f))
        }
    }

    /** The flashlight is on: a live activity, tap turns it off. */
    private val torchOn = MutableStateFlow(false)
    private val camera = service.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        camera?.cameraIdList?.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn.value = enabled
        }
    }

    private fun torchOff() {
        val id = torchId ?: return
        runCatching { camera?.setTorchMode(id, false) }
    }

    /** The always-on display is showing (screen "off" but drawing). */
    private val dozing = MutableStateFlow(false)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) updateDozing()
        }
    }
    private fun updateDozing() {
        val state = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.state
        dozing.value = state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
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
                    updateDozing()
                    // Unlocking with the fingerprint can come a moment after the screen turns on.
                    handler.postDelayed({
                        updateLocked()
                        updateDozing()
                    }, 600)
                    when (intent.action) {
                        // Woken up while locked: the face (or finger) is being checked.
                        Intent.ACTION_SCREEN_ON -> startUnlockScan()
                        // Unlocked (PIN, pattern, or a biometric that wasn't caught): the tick,
                        // unless it was already shown for this wake-up.
                        Intent.ACTION_USER_PRESENT -> {
                            if (!unlockTicked && keyguard()?.isDeviceSecure == true) {
                                showUnlock(success = true, hideAfter = UNLOCK_DONE_MS)
                            } else if (unlock.value?.success != true) {
                                hideUnlock()
                            }
                        }
                        else -> {
                            unlockTicked = false
                            stopUnlockWatch()
                            hideUnlock()
                        }
                    }
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
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> if (settings.glimmerAlerts) {
                    val on = service.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                    flash(IslandContent.Alert(Glyph.Battery, "Energiesparmodus", if (on) "An" else "Aus", YELLOW))
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> if (settings.glimmerAlerts) {
                    val on = intent.getBooleanExtra("state", false)
                    flash(IslandContent.Alert(Glyph.Airplane, "Flugmodus", if (on) "An" else "Aus", ORANGE))
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
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        updateLocked()
        // Start from how things are, so only real changes flash.
        dndOn = service.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.let { f ->
            f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        batteryFull = true
        startHeadphoneWatch()
        runCatching { service.getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, handler) }
        runCatching { camera?.registerTorchCallback(torchCallback, handler) }
        // Sent by the Bluetooth stack only (a protected broadcast).
        batteryReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                service, earbudsBattery, IntentFilter(ACTION_BATTERY_LEVEL_CHANGED), ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        updateDozing()
        ContextCompat.registerReceiver(service, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        scope = MainScope().also { s ->
            s.launch {
                NotificationHub.incoming.collect { if (settings.glimmerMessages) flashMessage(it) }
            }
            s.launch {
                media.nowPlaying.collect { now ->
                    handler.removeCallbacks(dropMedia)
                    when {
                        now == null -> keepMedia.value = false
                        now.playing -> keepMedia.value = true
                        keepMedia.value -> handler.postDelayed(dropMedia, KEEP_PAUSED_MS)
                        else -> Unit
                    }
                }
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
        runCatching { service.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener) }
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
        if (batteryReceiverRegistered) runCatching { service.unregisterReceiver(earbudsBattery) }
        batteryReceiverRegistered = false
        torchOn.value = false
        unlock.value = null
        watching = false
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
                val keepPaused by keepMedia.collectAsStateWithLifecycle()
                val pulseCount by pulse.collectAsStateWithLifecycle()
                val sideways by landscape.collectAsStateWithLifecycle()
                val onLockScreen by locked.collectAsStateWithLifecycle()
                val unlocking by unlock.collectAsStateWithLifecycle()
                val aod by dozing.collectAsStateWithLifecycle()
                val torch by torchOn.collectAsStateWithLifecycle()
                val onAod = aod && settings.glimmerAod

                val ongoing = live.sortedBy { priority(it.kind) }
                val ordered = buildList<IslandContent> {
                    currentAlert?.let { add(it) }
                    currentMessage?.let { add(IslandContent.Message(it)) }
                    ongoing.filter { it.kind == NoticeKind.Call || it.kind == NoticeKind.Alarm }.forEach { add(IslandContent.Live(it)) }
                    if (torch) add(IslandContent.Torch)
                    playing?.takeIf { it.playing }?.let { add(IslandContent.Media(it)) }
                    ongoing.filter { it.kind != NoticeKind.Call && it.kind != NoticeKind.Alarm }.forEach { add(IslandContent.Live(it)) }
                    // Paused, but still there to resume.
                    playing?.takeIf { !it.playing && keepPaused }?.let { add(IslandContent.Media(it)) }
                }
                // What was brought to the front stays there (alerts and messages still come first).
                val focused = ordered.firstOrNull { islandKey(it) == focus }
                val activities = if (focused != null && ordered.first() !is IslandContent.Alert && ordered.first() !is IslandContent.Message) {
                    listOf(focused) + ordered.filter { it !== focused }
                } else {
                    ordered
                }
                lastOrder = activities.map { islandKey(it) }
                // A call coming in or an alarm ringing opens up right away, like on the iPhone.
                val urgent = ongoing.firstOrNull { it.kind == NoticeKind.Alarm || (it.kind == NoticeKind.Call && !it.hasTime) }
                LaunchedEffect(urgent?.key) {
                    if (urgent != null && !dozing.value) {
                        focusKey.value = islandKey(IslandContent.Live(urgent))
                        expanded.value = true
                    }
                }
                // On the always-on display only music and live activities, still and dimmed.
                val shown = when {
                    aod && !settings.glimmerAod -> emptyList()
                    onAod -> activities.filter { it is IslandContent.Media || it is IslandContent.Live }
                    else -> activities
                }
                // On the lock screen too, but without the empty idle pill there.
                // Unlocking (Face ID moment) goes before everything else.
                val main = unlocking
                    ?: shown.firstOrNull()
                    ?: if (settings.glimmerIdlePill && !sideways && !onLockScreen && !aod) IslandContent.Idle else IslandContent.Hidden
                val second = if (unlocking != null) null else shown.drop(1).firstOrNull()

                HearthTheme(dark = true) {
                    CompositionLocalProvider(
                        LocalSettings provides settings,
                        LocalGlassStyle provides GlassStyle.from(settings),
                    ) {
                        GlimmerIsland(
                            content = main,
                            secondary = second,
                            expanded = isExpanded && !aod && main !is IslandContent.Idle && main !is IslandContent.Hidden &&
                                main !is IslandContent.Unlock,
                            dimmed = onAod,
                            style = settings.glimmerStyle,
                            screenWidthDp = service.resources.configuration.screenWidthDp.toFloat(),
                            topInset = (topInset / density).dp,
                            media = media,
                            tapOpens = settings.glimmerTapOpens,
                            pulse = pulseCount,
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
    /** The island's current size (zero while hidden), for the app fly-in to aim at. */
    var islandSize: DpSize = DpSize.Zero
        private set

    private fun resizeTo(size: DpSize) {
        islandSize = size
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
            IslandContent.Torch -> torchOff()
            else -> Unit
        }
    }

    private fun priority(kind: NoticeKind) = when (kind) {
        NoticeKind.Call -> 0
        NoticeKind.Alarm -> 0
        NoticeKind.Navigation -> 1
        NoticeKind.Timer -> 2
        NoticeKind.Recording -> 2
        NoticeKind.Update -> 2
        NoticeKind.Hotspot -> 3
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
        val YELLOW = Color(0xFFFFD60A)
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val KEEP_PAUSED_MS = 10 * 60 * 1000L
        const val UNLOCK_POLL_MS = 120L
        /** How long the face is looked for before the shake (Samsung gives up after about as long). */
        const val UNLOCK_SCAN_MS = 4000L
        const val UNLOCK_DONE_MS = 1300L
        const val UNLOCK_FAIL_MS = 1100L
        /** How long after waking further tries are watched for (a second finger, a PIN). */
        const val UNLOCK_WATCH_MS = 30_000L
        /** What lock screens say when a face, finger, PIN or pattern didn't match. */
        val UnlockFailureWords = listOf(
            "nicht erkannt", "nicht erkennen", "kein treffer", "keine übereinstimmung",
            "stimmt nicht überein", "erneut versuchen", "es erneut", "noch einmal versuchen",
            "nochmal versuchen", "zu viele versuche", "falsche pin", "falsches muster", "falsches passwort",
            "not recognized", "not recognised", "no match", "doesn't match", "does not match",
            "didn't match", "couldn't recognize", "can't recognize", "try again", "too many attempts",
            "wrong pin", "wrong pattern", "wrong password",
        )
        val HeadphoneTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
