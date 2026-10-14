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
import android.app.AlarmManager
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.hearth.launcher.ui.turnedWithPhone
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.data.GlimmerStyle
import dev.hearth.launcher.data.GlimmerDoubleTap
import dev.hearth.launcher.data.GlimmerUnlock
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LiveNotice
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.NoticeKind
import dev.hearth.launcher.data.NotificationHub
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.CameraHole
import dev.hearth.launcher.ui.PrivacyUse
import dev.hearth.launcher.ui.GlimmerIsland
import dev.hearth.launcher.ui.Glyph
import dev.hearth.launcher.ui.ISLAND_HEIGHT_DP
import dev.hearth.launcher.ui.IslandContent
import dev.hearth.launcher.ui.islandKey
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.data.forNight
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

    /** OMEGA UI 18.1: the stage over the lock screen and the full Always On Display. */
    private val stage = LockStage(service) { raiseWindow() }

    /** ZENITH 19: the Z-Angriff over everything when the phone is unlocked (if it's on). */
    private val strikeStage = ZenithStrikeStage(service)
    private val media = MediaRepository(service)

    private val expanded = MutableStateFlow(false)
    private val alert = MutableStateFlow<IslandContent.Alert?>(null)
    private val message = MutableStateFlow<LiveNotice?>(null)
    private val landscape = MutableStateFlow(false)
    /** Where the camera sits and how wide the screen is; read again after rotating or waking. */
    private val insetTop = MutableStateFlow(0)

    /** The camera hole in px: center from the window's middle and the screen's top, half width and half height (0: unknown). */
    private val cameraHolePx = MutableStateFlow(listOf(0, 0, 0, 0))

    /** Held sideways with the camera at an edge: 1 = left, 2 = right; 0 = at the top as usual. */
    private val side = MutableStateFlow(0)

    /**
     * Held sideways and turned with the phone (upright around the camera): the camera's edge,
     * 1 = left, 2 = right; 0 = not turned. What the window was last laid out for: [turnApplied].
     */
    private val turn = MutableStateFlow(0)
    private var turnApplied = 0
    private val screenWidth = MutableStateFlow(360f)

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
    /** ZENITH 19.5, Akku-Wächter: already told about the limit while this charge lasts. */
    private var chargeGuarded = false
    private var dndOn: Boolean? = null

    /** ZENITH 19.5, Neue-App-Wächter: a newly installed app, in the island, with what it wants. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!settings.newAppGuard || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == service.packageName) return
            val wants = dev.hearth.launcher.data.ZenithShield.requested(service, pkg)
            val name = dev.hearth.launcher.data.ZenithShield.label(service, pkg)
            flash(
                IslandContent.Alert(
                    Glyph.Lock,
                    "Neue App: $name",
                    if (wants.isEmpty()) "will nichts Heikles" else "will " + wants.take(3).joinToString(" · ") { it.label },
                    if (wants.isEmpty()) GREEN else ORANGE,
                ),
            )
        }
    }
    private var packageReceiverRegistered = false

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
    /** The padlock on the lock screen pill opens for a moment when unlocking without the symbol. */
    private val lockOpening = MutableStateFlow(false)
    private var lockOpenToken = 0
    private fun openPadlock() {
        lockOpening.value = true
        val token = ++lockOpenToken
        handler.postDelayed({ if (token == lockOpenToken) lockOpening.value = false }, 950)
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
            // The scan ran out without the lock screen saying anything: it goes quietly (the
            // phone may have no face set up, or you just looked at the time). Only a real
            // "not recognized" from the lock screen turns it red.
            if (scanStartedAt != 0L && now - scanStartedAt > UNLOCK_SCAN_MS) hideUnlock()
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

    /** Privacy dot: some app is recording sound, or has a camera open. */
    private val micInUse = MutableStateFlow(false)
    private val cameraInUse = MutableStateFlow(false)
    private val busyCameras = mutableSetOf<String>()
    private val cameraUse = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            busyCameras += cameraId
            cameraInUse.value = busyCameras.isNotEmpty()
        }

        override fun onCameraAvailable(cameraId: String) {
            busyCameras -= cameraId
            cameraInUse.value = busyCameras.isNotEmpty()
        }
    }
    private val micUse = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>?) {
            micInUse.value = !configs.isNullOrEmpty()
        }
    }
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
            if (displayId != Display.DEFAULT_DISPLAY) return
            updateDozing()
            // Turned round: also from one sideways to the other (by 180°), which Android doesn't
            // count as a configuration change – the camera is at the other edge then.
            val rotation = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: return
            if (rotation != lastRotation) {
                lastRotation = rotation
                placeAgainSoon()
            }
        }
    }
    private var lastRotation = -1

    /**
     * Over the camera again after turning: right away, and once more shortly after (the
     * camera's new place can take a moment to be known).
     */
    private fun placeAgainSoon() {
        placeWindow()
        handler.removeCallbacks(placeCheck)
        handler.postDelayed(placeCheck, 250)
        handler.postDelayed(placeCheck, 900)
    }
    private val placeCheck = Runnable { ensureWindow() }
    private fun updateDozing() {
        // A screen that's on for the user is never the AOD, whatever the display reports in
        // between (some phones keep saying "doze" for a moment after waking up).
        val interactive = service.getSystemService(PowerManager::class.java)?.isInteractive != false
        val state = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.state
        dozing.value = !interactive && (state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND)
    }

    /** On the lock screen Glimmer shows live activities, but no empty idle pill. */
    private val locked = MutableStateFlow(false)
    private fun updateLocked() {
        locked.value = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    /**
     * After the screen goes off or on, look again a few times: the lock screen, the AOD and the
     * window settle at different moments, and a state caught halfway must not hide the island.
     */
    private val refreshState = Runnable {
        updateLocked()
        updateDozing()
        ensureWindow()
    }
    private fun refreshSoon() {
        handler.removeCallbacks(refreshState)
        refreshState.run()
        for (delay in longArrayOf(350, 1000, 2500)) handler.postDelayed(refreshState, delay)
    }

    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var settings = LauncherSettings()
    /** The same settings for the island itself, so changes show without restarting. */
    private val settingsState = MutableStateFlow(LauncherSettings())

    /** Settings that apply while running (see [SettingsRepository.GLIMMER_LIVE_KEYS]). */
    fun update(newSettings: LauncherSettings) {
        val effective = newSettings.forGlimmer()
        val moved = effective.glimmerOffsetX != settings.glimmerOffsetX || effective.glimmerOffsetY != settings.glimmerOffsetY ||
            effective.glimmerSmall != settings.glimmerSmall || effective.glimmerSideways != settings.glimmerSideways
        settings = effective
        settingsState.value = effective
        refreshCharging()
        // The permission may have been given since starting.
        if (effective.glimmerScreenshots) watchScreenshots()
        if (moved) placeWindow()
    }

    /** An app just landed (HyperOS fly-in): its icon sits in the island for a moment. */
    private val arrival = MutableStateFlow<IslandContent.Arrival?>(null)
    private var arrivalId = 0
    private val dropArrival = Runnable { arrival.value = null }

    private fun waitingArrival(): IslandContent.Arrival? =
        arrival.value?.takeIf { it.icon == null && it.color == Color.Transparent }

    fun arrive(icon: android.graphics.Bitmap?, color: Int) {
        handler.removeCallbacks(dropArrival)
        // No icon and no color: an app is on its way – open now (empty, black) to catch it.
        // Asked again while it waits (the finger still on the glass), it just stays open longer.
        if (icon == null && color == 0) {
            if (waitingArrival() == null) {
                arrival.value = IslandContent.Arrival(icon = null, color = Color.Transparent, id = ++arrivalId)
            }
            handler.postDelayed(dropArrival, 1800)
            return
        }
        // It landed: in the island that's already open (same one, so nothing jumps).
        val id = waitingArrival()?.id ?: ++arrivalId
        arrivalId = id
        arrival.value = IslandContent.Arrival(
            icon = icon?.asImageBitmap(),
            color = if (color != 0) Color(color) else Color.White,
            id = id,
        )
        handler.postDelayed(dropArrival, 1150)
    }

    /** The app didn't come after all (the gesture went back into it): the open island closes. */
    fun cancelArrival() {
        if (waitingArrival() == null) return
        handler.removeCallbacks(dropArrival)
        arrival.value = null
    }

    /**
     * Puts the island's window on top of Glimmer's others again (the fly-in's, added later), so
     * a closing app flies under the island into it, never over it.
     */
    fun raiseWindow() {
        val view = root ?: return
        val p = params ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        if (runCatching { windowManager.addView(view, p) }.isSuccess) {
            windowAddedAt = SystemClock.uptimeMillis()
        } else {
            root = null
            params = null
            handler.postDelayed({ if (root == null) ensureWindow() }, 1000)
        }
    }

    /** True while the island is small (nothing unfolded), e.g. for a calm picture of the screen. */
    val islandCalm: Boolean get() = !expanded.value && islandSize.height.value < 70f

    /** Battery level while charging, when it should stay in the island. */
    private val charging = MutableStateFlow<Int?>(null)

    /** Reads the battery right away (switched on while already plugged in shows at once). */
    private fun refreshCharging() {
        if (!settings.glimmerCharging) {
            charging.value = null
            return
        }
        val sticky = runCatching {
            ContextCompat.registerReceiver(service, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.getOrNull() ?: return
        val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        charging.value = if (plugged && level >= 0) level * 100 / scale else null
    }

    /** An app in front hides the status bar (video, game): Glimmer steps back. */
    private val fullscreen = MutableStateFlow(false)

    private fun torchToggle() {
        val id = torchId ?: return
        runCatching { camera?.setTorchMode(id, !torchOn.value) }
    }

    /** Double tap on the island: whatever was chosen in the settings. */
    private fun doubleTap() {
        when (settings.glimmerDoubleTap) {
            GlimmerDoubleTap.Off -> Unit
            GlimmerDoubleTap.PlayPause -> media.playPause()
            GlimmerDoubleTap.Torch -> torchToggle()
            GlimmerDoubleTap.Screenshot -> {
                collapse()
                // A moment for the island to fold up first.
                handler.postDelayed({
                    runCatching { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }
                }, 350)
            }
            // Hearth's assistant, over whatever app is open.
            GlimmerDoubleTap.Claude -> {
                collapse()
                dev.hearth.launcher.data.ClaudeAssistant.openAssistant(service)
            }
            // Clawd answers right in the island.
            GlimmerDoubleTap.Oracle -> {
                collapse()
                flash(IslandContent.Alert(Glyph.Spark, dev.hearth.launcher.data.ClawdOracle.ask(), "Clawd-Orakel", Color(0xFFD97757)))
            }
            GlimmerDoubleTap.Coin -> {
                collapse()
                val side = dev.hearth.launcher.data.ClawdCoin.flip(service)
                flash(IslandContent.Alert(Glyph.Spark, dev.hearth.launcher.data.ClawdCoin.label(side), "Münze 🪙", Color(0xFFF2C14E)))
            }
            GlimmerDoubleTap.Dice -> {
                collapse()
                val n = dev.hearth.launcher.data.ClawdDice.roll()
                flash(IslandContent.Alert(Glyph.Spark, "🎲 $n", "Clawd würfelt", Color(0xFFD97757)))
            }
        }
    }
    private var receiverRegistered = false

    /** The next alarm as last seen, so only a newly set one flashes. */
    private var lastAlarm: Long? = null

    private val density get() = service.resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    refreshSoon()
                    // Standby folds the island up, like the iPhone's.
                    if (intent.action == Intent.ACTION_SCREEN_OFF) expanded.value = false
                    when (intent.action) {
                        // Woken up while locked: the face (or finger) is being checked.
                        Intent.ACTION_SCREEN_ON -> startUnlockScan()
                        // Unlocked (PIN, pattern, or a biometric that wasn't caught): the tick,
                        // unless it was already shown for this wake-up.
                        Intent.ACTION_USER_PRESENT -> {
                            greetOnUnlock()
                            if (settings.zenithStrikeSystem) strikeStage.play()
                            if (settings.glimmerUnlock == GlimmerUnlock.Off || keyguard()?.isDeviceSecure != true) {
                                if (settings.glimmerIdlePill) openPadlock()
                            }
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
                    chargeGuarded = level >= settings.chargeLimit
                    // ZENITH 19.5, Z-Momente: the Z-Angriff as the charger goes in.
                    if (settings.strikeOnCharge) strikeStage.play()
                }
                // Unplugged: how full it got, briefly, like One UI's charging toast.
                Intent.ACTION_POWER_DISCONNECTED -> {
                    chargeGuarded = false
                    if (settings.glimmerAlerts) {
                        val level = batteryLevel()
                        flash(IslandContent.Alert(Glyph.Battery, "Ladekabel getrennt", "$level %", Color.White, level / 100f))
                    }
                }
                // A new alarm was set: when it rings, so you know it took.
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> if (settings.glimmerAlerts) {
                    val next = runCatching { service.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
                    if (next != null && next != lastAlarm && next > System.currentTimeMillis()) {
                        val text = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.GERMAN).format(java.util.Date(next))
                        flash(IslandContent.Alert(Glyph.Alarm, "Wecker", text, ORANGE))
                    }
                    lastAlarm = next
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val full = plugged && (
                        intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL ||
                            level * 100 / scale >= 100
                        )
                    charging.value = if (plugged && settings.glimmerCharging) level * 100 / scale else null
                    if (full && !batteryFull && settings.glimmerAlerts) {
                        flash(IslandContent.Alert(Glyph.Battery, "Vollständig geladen", "100 %", GREEN, 1f))
                    }
                    // ZENITH 19.5, Z-Momente: the Z-Angriff when it's full.
                    if (full && !batteryFull && settings.strikeOnFull) strikeStage.play()
                    // ZENITH 19.5, Akku-Wächter: once per charge, when the limit is reached.
                    val percent = level * 100 / scale
                    if (plugged && settings.chargeGuard && !chargeGuarded && percent >= settings.chargeLimit) {
                        chargeGuarded = true
                        flash(IslandContent.Alert(Glyph.Battery, "Akku-Wächter", "$percent % – Kabel ziehen schont den Akku", GREEN, percent / 100f))
                    }
                    if (!plugged) chargeGuarded = false
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
                    flash(IslandContent.Alert(Glyph.Battery, "Akku schwach", "$level %", RED, level / 100f))
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

    /**
     * Lets something short-lived go after [delay], but not while the island is unfolded on it:
     * then it waits until it's folded again (it used to stay forever if it ran out just then).
     */
    private fun dropLater(delay: Long, stillCurrent: () -> Boolean, drop: () -> Unit) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!stillCurrent()) return
                if (expanded.value) handler.postDelayed(this, 1500) else drop()
            }
        }, delay)
    }

    private var alertToken = 0
    private var lastHello = 0L

    /**
     * Clawd says hello in the island at every unlock (after the unlock tick): the first one of
     * the day with the time of day, the others varied. Only once a day if so chosen; never
     * twice within half a minute.
     */
    private fun greetOnUnlock() {
        if (!settings.glimmerClawdGreeting) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastHello < 30_000) return
        val now = java.time.LocalDateTime.now()
        val prefs = service.getSharedPreferences("clawd_glimmer", Context.MODE_PRIVATE)
        val today = now.toLocalDate().toString()
        val firstToday = prefs.getString("greeted", null) != today && now.hour >= 5
        if (settings.glimmerClawdGreetDaily && !firstToday) return
        if (firstToday) prefs.edit().putString("greeted", today).apply()
        lastHello = nowMs
        val hello = dev.hearth.launcher.data.ClawdHello.line(now.hour, firstToday)
        handler.postDelayed({
            flash(IslandContent.Alert(Glyph.Spark, hello, "Clawd", Color(0xFFD97757)))
        }, 1600)
    }

    private fun flash(content: IslandContent.Alert) {
        alert.value = content
        val token = ++alertToken
        dropLater(3500, { token == alertToken }) { alert.value = null }
    }

    private var messageToken = 0
    private fun flashMessage(notice: LiveNotice) {
        message.value = notice
        val token = ++messageToken
        dropLater(4500, { token == messageToken }) { message.value = null }
    }

    fun start(newSettings: LauncherSettings) {
        settings = newSettings.forGlimmer()
        settingsState.value = settings
        if (root != null) return
        landscape.value = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        media.start()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
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
        lastAlarm = runCatching { service.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
        refreshCharging()
        startHeadphoneWatch()
        lastRotation = runCatching {
            service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }.getOrNull() ?: -1
        runCatching { service.getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, handler) }
        runCatching { camera?.registerTorchCallback(torchCallback, handler) }
        // Camera and microphone use, for the privacy dot (the callbacks report the current state too).
        runCatching { camera?.registerAvailabilityCallback(cameraUse, handler) }
        runCatching {
            val audio = service.getSystemService(AudioManager::class.java)
            micInUse.value = audio?.activeRecordingConfigurations?.isNotEmpty() == true
            audio?.registerAudioRecordingCallback(micUse, handler)
        }
        // Sent by the Bluetooth stack only (a protected broadcast).
        batteryReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                service, earbudsBattery, IntentFilter(ACTION_BATTERY_LEVEL_CHANGED), ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        updateDozing()
        ContextCompat.registerReceiver(service, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        packageReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                service,
                packageReceiver,
                IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        stage.start(settingsState, locked, dozing)
        scope = MainScope().also { s ->
            s.launch {
                NotificationHub.incoming.collect { if (settings.glimmerMessages) flashMessage(it) }
            }
            s.launch {
                NotificationHub.codes.collect { if (settings.glimmerCodes) showCode(it) }
            }
            // Glimmer Drop: the island tells what's happening between the two phones.
            s.launch {
                dev.hearth.launcher.data.GlimmerDrop.island.collect { m ->
                    flash(IslandContent.Alert(Glyph.Spark, m.title, m.value, Color(0xFF5E9BFF), m.level))
                }
            }
            // While unlocked, Glimmer feels a phone held against this one.
            s.launch {
                kotlinx.coroutines.flow.combine(locked, dozing) { l, d -> !l && !d }.collect { awake ->
                    dev.hearth.launcher.data.GlimmerDrop.setBackground(service, awake)
                }
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
        watchScreenshots()
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
        dev.hearth.launcher.data.GlimmerDrop.setBackground(service, false)
        runCatching { service.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener) }
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
        runCatching { camera?.unregisterAvailabilityCallback(cameraUse) }
        runCatching { service.getSystemService(AudioManager::class.java)?.unregisterAudioRecordingCallback(micUse) }
        busyCameras.clear()
        cameraInUse.value = false
        micInUse.value = false
        if (batteryReceiverRegistered) runCatching { service.unregisterReceiver(earbudsBattery) }
        batteryReceiverRegistered = false
        torchOn.value = false
        if (screenshotsWatched) runCatching { service.contentResolver.unregisterContentObserver(screenshotObserver) }
        screenshotsWatched = false
        screenshot.value = null
        code.value = null
        unlock.value = null
        watching = false
        lockOpening.value = false
        // Their timers are cancelled below, so they would otherwise stay forever.
        alert.value = null
        message.value = null
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
        if (packageReceiverRegistered) runCatching { service.unregisterReceiver(packageReceiver) }
        packageReceiverRegistered = false
        stage.stop()
        strikeStage.remove()
        scope?.cancel()
        scope = null
        expanded.value = false
    }

    fun onConfigurationChanged(config: Configuration) {
        landscape.value = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        screenWidth.value = config.screenWidthDp.toFloat()
        // The camera moves with rotation (or folding); place the window again.
        placeAgainSoon()
        ensureWindow()
    }

    /** Puts the window over the camera again and re-applies the island's size. */
    private fun placeWindow() {
        val p = params ?: return
        val view = root ?: return
        insetTop.value = topInsetPx()
        cameraHolePx.value = cameraHole()
        screenWidth.value = islandScreenWidthDp()
        // Turned with the phone or back: the window lies the other way round now.
        if ((turnApplied != 0) != (turn.value != 0)) {
            val w = p.width
            p.width = p.height
            p.height = w
        }
        turnApplied = turn.value
        p.gravity = gravity()
        if (turn.value != 0) {
            placeTurned(p)
        } else {
            p.x = windowX()
            p.y = 0
        }
        runCatching { windowManager.updateViewLayout(view, p) }
        resizeTo(lastTarget)
    }

    /** Turned with the phone: at the camera's edge, centered on it along the edge. */
    private fun placeTurned(p: WindowManager.LayoutParams) {
        val u = upright() ?: return
        p.x = 0
        val mid = if (u.edge == 1) u.length / 2 - windowX() else u.length / 2 + windowX()
        p.y = mid - p.height / 2
    }

    /** How wide the screen is for the island (along the camera's edge when turned with the phone). */
    private fun islandScreenWidthDp(): Float {
        upright()?.let { u -> return u.length / service.resources.displayMetrics.density }
        return service.resources.configuration.screenWidthDp.toFloat()
    }

    /**
     * The window can be lost while the phone sleeps (the system dropped it, or it couldn't be
     * added while the screen was off). Then it's simply added again, so Glimmer never stays gone.
     */
    private fun ensureWindow() {
        if (scope == null || !settings.glimmerEnabled) return
        val view = root
        if (view != null && view.isAttachedToWindow) {
            // Still there: make sure it sits over the camera (measured only while the screen is
            // on; the AOD may report the screen without its cutout).
            if (service.getSystemService(PowerManager::class.java)?.isInteractive == false) return
            val wantTurn = upright()?.edge ?: 0
            val x = if (wantTurn != 0) 0 else windowX()
            val top = topInsetPx()
            if (turnApplied != wantTurn || params?.x != x || insetTop.value != top || cameraHolePx.value != cameraHole() ||
                params?.gravity != gravity()
            ) {
                placeWindow()
            }
            return
        }
        if (view != null) {
            // Not attached yet right after adding is normal; only replace a window that was lost.
            if (SystemClock.uptimeMillis() - windowAddedAt < 1500) return
            runCatching { windowManager.removeViewImmediate(view) }
            root = null
            params = null
        }
        addWindow()
    }
    private var windowAddedAt = 0L

    /**
     * Held sideways, the camera is at the left or right edge: which one (1 = left, 2 = right)
     * and where exactly (px on screen); null when it's at the top (or unknown).
     */
    private fun cameraAtEdge(): Pair<Int, android.graphics.Rect>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        // "At the top" sideways: as if the camera weren't at an edge.
        if (settings.glimmerSideways == dev.hearth.launcher.data.GlimmerSideways.Top) return null
        return runCatching {
            val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout ?: return null
            val top = cutout.boundingRectTop
            if (!top.isEmpty) return null
            val left = cutout.boundingRectLeft
            val right = cutout.boundingRectRight
            when {
                !left.isEmpty -> 1 to left
                !right.isEmpty -> 2 to right
                else -> null
            }
        }.getOrNull()
    }

    /** Sideways and lying across from the camera's edge into the screen. */
    private fun sideCamera(): Pair<Int, android.graphics.Rect>? =
        cameraAtEdge()?.takeIf { settings.glimmerSideways == dev.hearth.launcher.data.GlimmerSideways.Across }

    /**
     * Sideways and turned with the phone, the camera as if held upright (px): its edge (1 = left,
     * 2 = right), where it is along that edge (from the end that's "left" when upright), how far
     * from the edge, the edge's length, and the hole's half sizes along and across.
     */
    private class Upright(val edge: Int, val along: Int, val fromEdge: Int, val length: Int, val halfAlong: Int, val halfAcross: Int)

    private fun upright(): Upright? {
        if (settings.glimmerSideways != dev.hearth.launcher.data.GlimmerSideways.Upright) return null
        val (edge, rect) = cameraAtEdge() ?: return null
        val (sw, sh) = screenSize()
        // Camera on the left: upright's "left" is the bottom; on the right it's the top.
        return if (edge == 1) {
            Upright(1, sh - rect.centerY(), rect.centerX(), sh, rect.height() / 2, rect.width() / 2)
        } else {
            Upright(2, rect.centerY(), sw - rect.centerX(), sh, rect.height() / 2, rect.width() / 2)
        }
    }

    /** Turned with the phone right now (so the fly-in aims differently). */
    val turnedWithPhone: Boolean get() = turn.value != 0

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = windowManager.currentWindowMetrics.bounds
        b.width() to b.height()
    } else {
        service.resources.displayMetrics.widthPixels to service.resources.displayMetrics.heightPixels
    }

    /** The window's gravity: over the camera at the top, or at the edge where it is sideways. */
    private fun gravity(): Int = when (sideCamera()?.first ?: upright()?.edge) {
        1 -> Gravity.TOP or Gravity.LEFT
        2 -> Gravity.TOP or Gravity.RIGHT
        else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
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

    /**
     * Where the camera hole is, seen from the window: its center sideways from the window's
     * middle (the island may be nudged off it in the settings), its center from the top of the
     * screen, and its radius. Glimmer keeps every number and symbol out of it.
     */
    private fun cameraHole(): List<Int> {
        // Turned with the phone: just like upright, seen from the camera's edge.
        upright()?.let { u ->
            side.value = 0
            turn.value = u.edge
            return listOf(u.along - u.length / 2 - windowX(), u.fromEdge, u.halfAlong, u.halfAcross)
        }
        turn.value = 0
        // Sideways: how far the camera is from the island's start (left) or end (right) edge.
        sideCamera()?.let { (edge, rect) ->
            side.value = edge
            val fromEdge = if (edge == 1) rect.centerX() - dp(PAD) else screenSize().first - rect.centerX() - dp(PAD)
            return listOf(fromEdge, rect.centerY(), rect.width() / 2, rect.height() / 2)
        }
        side.value = 0
        val (cx, cy) = cameraCenter()
        var halfWidth = 0
        var halfHeight = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (cutout != null && !cutout.isEmpty) {
                    halfWidth = cutout.width() / 2
                    halfHeight = cutout.height() / 2
                }
            }
        }
        return listOf(cx - windowX(), cy, halfWidth, halfHeight)
    }

    private fun topInsetPx(): Int {
        // Turned with the phone: from the camera's edge, the same as from the top when upright.
        upright()?.let { u ->
            return ((u.fromEdge - dp(IDLE_HEIGHT) / 2).coerceAtLeast(dp(4f)) + dp(settings.glimmerOffsetY.toFloat())).coerceAtLeast(0)
        }
        // Sideways: exactly level with the camera; only on a very low screen pushed up a little,
        // so the unfolded card (music, the tallest) still fits below.
        sideCamera()?.let { (_, rect) ->
            val wanted = rect.centerY() - dp(IDLE_HEIGHT) / 2
            return wanted.coerceAtMost(screenSize().second - dp(192f)).coerceAtLeast(0)
        }
        return ((cameraCenter().second - dp(IDLE_HEIGHT) / 2).coerceAtLeast(dp(4f)) + dp(settings.glimmerOffsetY.toFloat())).coerceAtLeast(0)
    }

    /** Where the window sits sideways: over the camera, plus the nudge from the settings (at an edge: right at it). */
    private fun windowX(): Int {
        // Turned with the phone: along the camera's edge, like across the top when upright.
        upright()?.let { u -> return u.along - u.length / 2 + dp(settings.glimmerOffsetX.toFloat()) }
        return if (sideCamera() != null) 0 else cameraCenter().first + dp(settings.glimmerOffsetX.toFloat())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addWindow() {
        val cameraX = windowX()
        val topInset = topInsetPx()
        insetTop.value = topInset
        cameraHolePx.value = cameraHole()
        screenWidth.value = islandScreenWidthDp()
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
        // Whether the status bar is showing: hidden means a video or game in full screen.
        // Glimmer sits above the status bar, and some phones never tell such a window about it
        // (it then looks hidden all the time, even on the home screen). So "hidden" only counts
        // once this window has seen the status bar showing and is told its size.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var seenStatusBar = false
            frame.setOnApplyWindowInsetsListener { v, insets ->
                val bar = android.view.WindowInsets.Type.statusBars()
                val showing = insets.isVisible(bar)
                if (showing) seenStatusBar = true
                fullscreen.value = seenStatusBar && !showing && insets.getInsetsIgnoringVisibility(bar).top > 0
                v.onApplyWindowInsets(insets)
            }
        }
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
                val padlockOpen by lockOpening.collectAsStateWithLifecycle()
                val landed by arrival.collectAsStateWithLifecycle()
                val currentCode by code.collectAsStateWithLifecycle()
                val currentShot by screenshot.collectAsStateWithLifecycle()
                val chargeLevel by charging.collectAsStateWithLifecycle()
                val inFullscreen by fullscreen.collectAsStateWithLifecycle()
                // Read here, so the island follows changed settings at once.
                val settings by settingsState.collectAsStateWithLifecycle()
                val torch by torchOn.collectAsStateWithLifecycle()
                val top by insetTop.collectAsStateWithLifecycle()
                val holePx by cameraHolePx.collectAsStateWithLifecycle()
                val edge by side.collectAsStateWithLifecycle()
                val turned by turn.collectAsStateWithLifecycle()
                val micOn by micInUse.collectAsStateWithLifecycle()
                val cameraOn by cameraInUse.collectAsStateWithLifecycle()
                val widthDp by screenWidth.collectAsStateWithLifecycle()
                val onAod = aod && settings.glimmerAod

                val ongoing = live.sortedBy { priority(it.kind) }
                val ordered = buildList<IslandContent> {
                    currentAlert?.let { add(it) }
                    // Locked, codes and messages wait: whoever holds the phone shouldn't read them.
                    // A code still fresh shows once it's unlocked.
                    if (!onLockScreen) currentCode?.let { add(it) }
                    currentShot?.let { add(it) }
                    if (!onLockScreen) currentMessage?.let { add(IslandContent.Message(it)) }
                    ongoing.filter { it.kind == NoticeKind.Call || it.kind == NoticeKind.Alarm }.forEach { add(IslandContent.Live(it)) }
                    if (torch) add(IslandContent.Torch)
                    chargeLevel?.let { add(IslandContent.Charging(it)) }
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
                    onAod -> activities.filter { it is IslandContent.Media || it is IslandContent.Live || it is IslandContent.Charging }
                    else -> activities
                }
                // On the lock screen too, but without the empty idle pill there.
                // Unlocking (Face ID moment) goes before everything else.
                // On the lock screen the pill wears a padlock, so Glimmer is always there; it opens
                // when unlocking.
                // A video or game in full screen: only a call or a ringing alarm (and unlocking)
                // still come through.
                val stepBack = inFullscreen && settings.glimmerHideFullscreen && !onLockScreen && !aod
                val visible = if (stepBack) {
                    shown.filter { it is IslandContent.Live && (it.notice.kind == NoticeKind.Call || it.notice.kind == NoticeKind.Alarm) }
                } else {
                    shown
                }
                val main = unlocking
                    ?: landed
                    ?: visible.firstOrNull()
                    ?: when {
                        // Sideways it stays at the camera, if the camera's edge is known.
                        !settings.glimmerIdlePill || (sideways && edge == 0 && turned == 0) || aod || stepBack -> IslandContent.Hidden
                        // The iPhone's island is just there on the lock screen, no padlock in it.
                        settings.glimmerIsDynamicIsland -> IslandContent.Idle
                        padlockOpen -> IslandContent.Lock(open = true)
                        onLockScreen -> IslandContent.Lock(open = false)
                        else -> IslandContent.Idle
                    }
                val second = if (unlocking != null || landed != null) null else visible.drop(1).firstOrNull()

                // Dark mode: Glimmer's glass goes dark with the rest of Hearth.
                val night = dev.hearth.launcher.ui.rememberNight(settings)
                val nightLook = if (night) settings.forNight() else settings
                HearthTheme(dark = true) {
                    CompositionLocalProvider(
                        LocalSettings provides nightLook,
                        LocalGlassStyle provides GlassStyle.from(nightLook),
                    ) {
                        // Held sideways it can turn with the phone: upright around the camera.
                        Box(Modifier.fillMaxSize().turnedWithPhone(turned)) {
                        GlimmerIsland(
                            content = main,
                            secondary = second,
                            expanded = isExpanded && !aod && main !is IslandContent.Idle && main !is IslandContent.Hidden &&
                                main !is IslandContent.Unlock && main !is IslandContent.Lock && main !is IslandContent.Arrival,
                            dimmed = onAod,
                            style = settings.glimmerStyle,
                            screenWidthDp = widthDp,
                            topInset = (top / density).dp,
                            privacy = when {
                                !settings.glimmerPrivacy || aod -> PrivacyUse.None
                                cameraOn -> PrivacyUse.Camera
                                micOn -> PrivacyUse.Microphone
                                else -> PrivacyUse.None
                            },
                            camera = run {
                                val unit = density
                                val (hx, hy, hw, hh) = holePx
                                val known = hw > 0 && hh > 0
                                CameraHole(
                                    x = if (edge == 0) (hx / unit).dp else 0.dp,
                                    y = ((hy - top) / unit).dp,
                                    radius = if (known) (hw / unit).coerceIn(4f, 40f).dp else CameraHole().radius,
                                    halfHeight = if (known) (hh / unit).coerceIn(4f, 20f).dp else CameraHole().halfHeight,
                                    fromStart = if (edge == 1) (hx / unit).dp else null,
                                    fromEnd = if (edge == 2) (hx / unit).dp else null,
                                )
                            },
                            media = media,
                            // Locked, apps can't come up anyway: a tap unfolds instead.
                            tapOpens = settings.glimmerTapOpens && !onLockScreen,
                            pulse = pulseCount,
                            glow = settings.glimmerGlow,
                            onToggle = { expanded.value = !expanded.value },
                            onExpand = { expanded.value = true },
                            onSwap = this@GlimmerController::swap,
                            onShare = this@GlimmerController::share,
                            activityCount = visible.size,
                            onFocus = { item ->
                                focusKey.value = islandKey(item)
                                expanded.value = true
                            },
                            onCollapse = this@GlimmerController::collapse,
                            onOpen = this@GlimmerController::open,
                            onDoubleTap = if (settings.glimmerDoubleTap != GlimmerDoubleTap.Off) this@GlimmerController::doubleTap else null,
                            onTargetSize = this@GlimmerController::resizeTo,
                            anchor = when (edge) {
                                1 -> androidx.compose.ui.Alignment.Start
                                2 -> androidx.compose.ui.Alignment.End
                                else -> androidx.compose.ui.Alignment.CenterHorizontally
                            },
                        )
                        }
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
            gravity = gravity()
            x = cameraX
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "${dev.hearth.launcher.data.Brand.name} Glimmer"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.glimmerStyle == GlimmerStyle.Glass) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(24f)
            }
        }
        // Turned with the phone: the window lies the other way round, centered on the camera.
        turnApplied = turn.value
        if (turn.value != 0) {
            val w = p.width
            p.width = p.height
            p.height = w
            placeTurned(p)
        }
        if (runCatching { windowManager.addView(frame, p) }.isSuccess) {
            root = frame
            params = p
            windowAddedAt = SystemClock.uptimeMillis()
        } else {
            // Couldn't be added right now (screen off, system busy): try again shortly.
            handler.postDelayed({ if (root == null) ensureWindow() }, 2000)
        }
    }

    private var resizeToken = 0

    /** The island's middle on the screen (px), for the fly-in with another launcher to aim at. */
    fun islandCenterOnScreen(): android.graphics.PointF {
        upright()?.let { u ->
            val across = topInsetPx() + dp(IDLE_HEIGHT) / 2f
            val mid = if (u.edge == 1) u.length / 2f - windowX() else u.length / 2f + windowX()
            return android.graphics.PointF(if (u.edge == 1) across else screenSize().first - across, mid)
        }
        val screenW = screenSize().first
        val y = topInsetPx() + dp(IDLE_HEIGHT) / 2f
        sideCamera()?.let { (edge, _) ->
            val half = dp(islandSize.width.value.coerceAtLeast(IDLE_WIDTH)) / 2f
            return android.graphics.PointF(if (edge == 1) dp(PAD) + half else screenW - dp(PAD) - half, y)
        }
        return android.graphics.PointF(screenW / 2f + windowX(), y)
    }

    /**
     * Turned with the phone: where an app's icon lands in the island (its upright "left" end,
     * as wide as [widthDp]); null when not turned (then it's worked out from the island's middle).
     */
    fun landingOnScreen(widthDp: Float): android.graphics.PointF? {
        val u = upright() ?: return null
        val c = islandCenterOnScreen()
        val along = dp(widthDp.coerceAtLeast(IDLE_WIDTH)) / 2f - dp(16f)
        // Upright's left is down with the camera on the left, up with it on the right.
        return android.graphics.PointF(c.x, if (u.edge == 1) c.y + along else c.y - along)
    }

    /** The island's current size on screen (zero while hidden), for the app fly-in to aim at. */
    var islandSize: DpSize = DpSize.Zero
        private set

    /** The size the island asked for, in its own (possibly 85 %) units. */
    private var lastTarget: DpSize = DpSize.Zero

    /**
     * Keeps the window just as big as the island: growing happens at once (so the animation
     * isn't cut off), shrinking after the animation, so touches around it reach the app below.
     */
    private fun resizeTo(size: DpSize) {
        lastTarget = size
        islandSize = size
        val p = params ?: return
        val view = root ?: return
        val topInset = insetTop.value
        val hidden = size.width.value <= 0f
        // As if upright: across (w) and down from the camera's edge (h).
        val w = if (hidden) 1 else dp(size.width.value + 2 * PAD)
        val h = if (hidden) 1 else topInset + dp(size.height.value + PAD)
        val turned = turnApplied != 0
        val token = ++resizeToken
        fun applySize(width: Int, height: Int) {
            // Turned with the phone, the window lies the other way round (and stays centered on the camera).
            p.width = if (turned) height else width
            p.height = if (turned) width else height
            if (turned) placeTurned(p)
            p.flags = if (hidden) {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            runCatching { windowManager.updateViewLayout(view, p) }
        }
        val nowW = if (turned) p.height else p.width
        val nowH = if (turned) p.width else p.height
        if (w >= nowW && h >= nowH && !hidden) {
            applySize(w, h)
        } else {
            if (!hidden) applySize(max(w, nowW), max(h, nowH))
            handler.postDelayed({ if (token == resizeToken) applySize(w, h) }, 450)
        }
    }

    /**
     * Sideways swipe: the next (or previous) activity comes to the front, going round through
     * all of them like Samsung's Now Bar.
     */
    private fun swap(forward: Boolean = true) {
        val order = lastOrder
        if (order.size < 2) return
        focusKey.value = if (forward) order[1] else order.last()
    }

    // ---- One-time codes (vivo, OPPO): in the island, a tap copies them ----

    private val code = MutableStateFlow<IslandContent.Code?>(null)
    private var codeToken = 0
    private fun showCode(found: dev.hearth.launcher.data.VerificationCode) {
        code.value = IslandContent.Code(found.code, found.appLabel, found.icon)
        val token = ++codeToken
        // Codes go stale; the island lets it go after a while.
        dropLater(45_000, { token == codeToken }) { code.value = null }
    }
    private fun copyCode(c: IslandContent.Code) {
        val clipboard = service.getSystemService(android.content.ClipboardManager::class.java) ?: return
        val clip = android.content.ClipData.newPlainText("Code", c.code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Marked as sensitive, so the keyboard's clipboard preview doesn't show it around.
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
        code.value = c.copy(copied = true)
        val token = ++codeToken
        handler.postDelayed({ if (token == codeToken) code.value = null }, 1400)
    }

    // ---- Screenshots (vivo's Origin Island): a little picture in the island ----

    private val screenshot = MutableStateFlow<IslandContent.Screenshot?>(null)
    private var screenshotToken = 0
    private var lastScreenshotId = -1L
    private val screenshotObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (settings.glimmerScreenshots) scope?.launch { findScreenshot() }
        }
    }
    private var screenshotsWatched = false

    private fun canReadImages(): Boolean = ContextCompat.checkSelfPermission(
        service,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun watchScreenshots() {
        if (screenshotsWatched || !canReadImages()) return
        screenshotsWatched = runCatching {
            service.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver,
            )
        }.isSuccess
    }

    /** The newest picture, if it's a screenshot taken just now. */
    private suspend fun findScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val images = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val columns = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                )
                service.contentResolver.query(images, columns, null, null, "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val id = c.getLong(0)
                    val added = c.getLong(1)
                    val where = (c.getString(2).orEmpty() + "/" + c.getString(3).orEmpty()).lowercase()
                    val fresh = System.currentTimeMillis() / 1000 - added < 15
                    if (!fresh || id == lastScreenshotId || "screenshot" !in where) return@use null
                    val uri = android.content.ContentUris.withAppendedId(images, id)
                    val thumb = runCatching {
                        service.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null).asImageBitmap()
                    }.getOrNull()
                    Triple(id, uri, thumb)
                }
            }.getOrNull()
        } ?: return
        lastScreenshotId = found.first
        screenshot.value = IslandContent.Screenshot(found.second, found.third, found.first)
        val token = ++screenshotToken
        dropLater(5000, { token == screenshotToken }) { screenshot.value = null }
    }

    private fun shareScreenshot(shot: IslandContent.Screenshot) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_STREAM, shot.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            service.startActivity(Intent.createChooser(send, "Bildschirmfoto teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        screenshot.value = null
    }

    /** "Share" in the unfolded island. */
    private fun share(content: IslandContent) {
        collapse()
        if (content is IslandContent.Screenshot) shareScreenshot(content)
    }

    // Short-lived things that ran out while unfolded go once it's folded (see dropLater).
    private fun collapse() {
        if (!expanded.value) return
        expanded.value = false
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
            is IslandContent.Code -> if (!content.copied) copyCode(content)
            is IslandContent.Screenshot -> {
                runCatching {
                    service.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(content.uri, "image/*")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }
                screenshot.value = null
            }
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
        const val UNLOCK_SCAN_MS = 3000L
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
