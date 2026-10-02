package dev.hearth.launcher.data

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.view.KeyEvent
import dev.hearth.launcher.system.ControlCenterService
import dev.hearth.launcher.system.ControlsLink
import dev.hearth.launcher.system.GlimmerLink
import dev.hearth.launcher.system.GlimmerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Snapshot of the toggles shown in the control center. */
data class ControlState(
    val wifi: Boolean = false,
    val mobileData: Boolean = false,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    val location: Boolean = false,
    val doNotDisturb: Boolean = false,
    val vibrate: Boolean = false,
    val autoRotate: Boolean = false,
    val autoBrightness: Boolean = false,
    val canWriteSettings: Boolean = false,
    val batterySaver: Boolean = false,
    val darkMode: Boolean = false,
    val nfc: Boolean = false,
)

enum class OutputKind(val label: String) {
    Bluetooth("Bluetooth"),
    Wired("Kabel"),
    Usb("USB"),
    Speaker("Lautsprecher"),
}

/** Where sound goes right now. */
data class AudioOutput(val name: String, val kind: OutputKind)

/** A volume the audio sheet can change. */
data class VolumeStream(val stream: Int, val label: String)

/**
 * What the launcher's own control center can do. Android lets normal apps change
 * brightness, rotation (with "Modify system settings"), volume, flashlight,
 * vibration and Do not disturb (with access). Wi-Fi, Bluetooth and mobile data
 * can only be switched in the system's own panels, so those buttons open them.
 */
class SystemControls(private val context: Context) {

    private val resolver = context.contentResolver
    private val audio = context.getSystemService(AudioManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private val torchId: String? = runCatching {
        val cm = camera ?: return@runCatching null
        cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()
    val hasTorch: Boolean get() = torchId != null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) _torchOn.value = enabled
        }
    }

    init {
        runCatching { camera?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
    }

    /** Some phones (OPPO and OnePlus among them) use more than 255 brightness steps. */
    @SuppressLint("DiscouragedApi")
    private val maxBrightness: Int = runCatching {
        val res = Resources.getSystem()
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id != 0) res.getInteger(id) else 255
    }.getOrDefault(255).takeIf { it > 0 } ?: 255

    /** The last state read, so the control center opens with the right toggles lit. */
    @Volatile
    var lastState: ControlState? = null
        private set

    fun state(): ControlState = readState().also { lastState = it }

    /** Reads the state in the background, e.g. when a finger lands on the status bar. */
    fun prefetch() {
        writer.post { runCatching { state() } }
    }

    private fun readState(): ControlState = ControlState(
        wifi = runCatching { context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true }.getOrDefault(false),
        mobileData = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }.getOrDefault(false),
        bluetooth = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        }.getOrDefault(false),
        airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
        location = runCatching { context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }
            .getOrDefault(false),
        doNotDisturb = runCatching {
            val filter = notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false),
        vibrate = audio?.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
        autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
        autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        canWriteSettings = Settings.System.canWrite(context),
        batterySaver = runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }
            .getOrDefault(false),
        darkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES,
        nfc = runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }.getOrDefault(false),
    )

    // Audio

    val streams = listOf(
        VolumeStream(AudioManager.STREAM_MUSIC, "Medien"),
        VolumeStream(AudioManager.STREAM_VOICE_CALL, "Anrufe"),
        VolumeStream(AudioManager.STREAM_RING, "Klingelton"),
        VolumeStream(AudioManager.STREAM_NOTIFICATION, "Benachrichtigungen"),
        VolumeStream(AudioManager.STREAM_ALARM, "Wecker"),
        VolumeStream(AudioManager.STREAM_SYSTEM, "System"),
    )

    fun streamLevel(stream: Int): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(stream).coerceAtLeast(1)
        return a.getStreamVolume(stream).toFloat() / max
    }

    /** Some volumes can't be changed while "Do not disturb" is on; that is ignored then. */
    fun setStreamLevel(stream: Int, level: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(stream)
        runCatching { a.setStreamVolume(stream, (level.coerceIn(0f, 1f) * max).roundToInt(), 0) }
    }

    fun ringerMode(): Int = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    /** Silent needs "Do not disturb" access on Android; without it the settings open. */
    fun setRingerMode(mode: Int) {
        val a = audio ?: return
        if (mode == AudioManager.RINGER_MODE_SILENT && notifications?.isNotificationPolicyAccessGranted != true) {
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        runCatching { a.ringerMode = mode }
    }

    fun currentOutput(): AudioOutput {
        val devices = runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
        fun find(vararg types: Int) = devices.firstOrNull { it.type in types }
        val bt = find(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
        if (bt != null) return AudioOutput(bt.productName?.toString().orEmpty().ifBlank { "Bluetooth-Kopfhörer" }, OutputKind.Bluetooth)
        val wired = find(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
        if (wired != null) return AudioOutput("Kopfhörer (Kabel)", OutputKind.Wired)
        val usb = find(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
        if (usb != null) return AudioOutput(usb.productName?.toString().orEmpty().ifBlank { "USB-Audio" }, OutputKind.Usb)
        return AudioOutput("Telefon-Lautsprecher", OutputKind.Speaker)
    }

    /** Spatial / 3D audio: null if the phone has none. */
    fun spatialAudio(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return null
        val spatializer = audio?.spatializer ?: return null
        if (spatializer.immersiveAudioLevel == android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE) return null
        return spatializer.isEnabled
    }

    /**
     * The earbuds' own app, where noise cancelling is switched. Android has no way for
     * other apps to switch it, so Hearth opens the right app instead.
     */
    fun headphoneApp(): Pair<String, Intent>? {
        val pm = context.packageManager
        return HeadphoneApps.firstNotNullOfOrNull { pkg ->
            val launch = pm.getLaunchIntentForPackage(pkg) ?: return@firstNotNullOfOrNull null
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            label to launch
        }
    }

    fun openHeadphoneApp(): Boolean {
        val app = headphoneApp() ?: return start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        return start(app.second)
    }

    /** The system's "media output" picker (which speaker or earbuds to play on). */
    fun openMediaOutput() {
        val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        if (start(panel)) return
        val dialog = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", context.packageName)
        if (runCatching { context.sendBroadcast(dialog) }.isSuccess) {
            onOpenedScreen?.invoke()
            return
        }
        start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openVolumePanel() = start(Intent(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS))

    // One UI style extras

    /**
     * Lock, screenshot, power menu: only an accessibility service may do these. In Glimmer
     * that's its own; from Hearth they're asked of Glimmer. Without Glimmer: false, and the
     * accessibility settings open.
     */
    private fun globalAction(action: Int, delayMs: Long = 0): Boolean {
        val service = ControlCenterService.instance ?: GlimmerService.instance
        if (service != null) {
            Handler(Looper.getMainLooper()).postDelayed({ service.performGlobalAction(action) }, delayMs)
            return true
        }
        // Another app of the family does it: Glimmer, or else the control center app.
        val other = listOf(GlimmerLink, ControlsLink).firstOrNull { it.isInstalled(context) }
        if (other != null) {
            writer.postDelayed({ other.globalAction(context, action) }, delayMs)
            return true
        }
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return false
    }

    /** Waits a moment, so the control center itself is gone from the screenshot. */
    fun takeScreenshot() = globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 500)
    fun lockScreen() = globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 250)
    fun powerMenu() = globalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, 150)

    fun openBatterySaver() = start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    fun openHotspot() = start(
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
    fun openNfc() = start(Intent(Settings.Panel.ACTION_NFC), Intent(Settings.ACTION_NFC_SETTINGS))
    fun openCast() = start(Intent(Settings.ACTION_CAST_SETTINGS))

    fun requestWriteSettings() {
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    // Brightness, 0..1 on a perceptual curve (the eye sees brightness roughly as a square root).

    fun brightness(): Float {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, maxBrightness / 2)
        val max = max(maxBrightness, raw).coerceAtLeast(1)
        return sqrt(raw.toFloat() / max).coerceIn(0f, 1f)
    }

    // Sliders send a value for every finger movement (up to 120 per second). Writing each one
    // into the system settings made them stutter, so values are handed to a background thread
    // that writes the newest one at most every 40 ms.
    private val writerThread = HandlerThread("hearth-controls").apply { start() }
    private val writer = Handler(writerThread.looper)
    @Volatile private var pendingBrightness = -1
    @Volatile private var pendingVolume = -1
    @Volatile private var flushScheduled = false
    @Volatile private var manualModeSet = false
    private val flush = Runnable {
        flushScheduled = false
        val raw = pendingBrightness
        if (raw >= 0) {
            pendingBrightness = -1
            runCatching {
                if (!manualModeSet) {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    manualModeSet = true
                }
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            }
        }
        val volumeIndex = pendingVolume
        if (volumeIndex >= 0) {
            pendingVolume = -1
            runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex, 0) }
        }
    }

    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        writer.postDelayed(flush, 40)
    }

    /** Returns false when the permission is missing. */
    fun setBrightness(level: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val l = level.coerceIn(0f, 1f)
        pendingBrightness = (l * l * maxBrightness).roundToInt().coerceIn(1, maxBrightness)
        scheduleFlush()
        return true
    }

    fun setAutoBrightness(on: Boolean): Boolean {
        manualModeSet = !on
        return writeSystem(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    fun setAutoRotate(on: Boolean): Boolean = writeSystem(Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)

    private fun writeSystem(key: String, value: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettings()
            return false
        }
        return runCatching { Settings.System.putInt(resolver, key, value) }.isSuccess
    }

    // Media volume, 0..1

    fun volume(): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(level: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        pendingVolume = (level.coerceIn(0f, 1f) * max).roundToInt()
        scheduleFlush()
    }

    fun setTorch(on: Boolean) {
        val id = torchId ?: return
        runCatching { camera?.setTorchMode(id, on) }
    }

    fun toggleVibrate() {
        val a = audio ?: return
        val next = if (a.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            AudioManager.RINGER_MODE_NORMAL
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }
        runCatching { a.ringerMode = next }
    }

    fun toggleDoNotDisturb() {
        val nm = notifications ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        runCatching {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            )
        }
    }

    /** Sends a media button press to whatever played last (no permission needed). */
    fun mediaKey(keyCode: Int) {
        val a = audio ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            a.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            a.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    /**
     * Pulls down the system notification shade. Uses a hidden but long-standing
     * system call; returns false if the phone doesn't allow it.
     */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(): Boolean = runCatching {
        // The accessibility service can do this officially; use it when it's on.
        // The control center app first: it lets a shade it opened itself stay open.
        if (ControlCenterService.instance?.showNotifications() == true) return true
        if (ControlCenterService.instance == null) {
            if (ControlsLink.showNotifications(context)) return true
            if (GlimmerService.instance?.showNotifications() == true) return true
            if (GlimmerLink.showNotifications(context)) return true
        }
        val service = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        true
    }.getOrDefault(false)

    /** The system's own quick settings (One UI's control center). */
    fun expandQuickSettings(): Boolean = runCatching {
        if (ControlCenterService.instance?.showSystemQuickSettings() == true) return true
        if (ControlCenterService.instance == null) {
            if (ControlsLink.showQuickSettings(context)) return true
            if (GlimmerService.instance?.showSystemQuickSettings() == true) return true
            if (GlimmerLink.showQuickSettings(context)) return true
        }
        val service = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(service)
        true
    }.getOrDefault(false)

    fun openWifi() = start(Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))
    fun openInternet() = start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
    fun openBluetooth() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openAirplane() = start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openLocation() = start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openDisplay() = start(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun openSound() = start(Intent(Settings.ACTION_SOUND_SETTINGS))
    fun openSystemSettings() = start(Intent(Settings.ACTION_SETTINGS))
    fun openCamera() = start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    fun openAlarms() = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun openCalculator() = start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))

    /** Opens the Claude app (with the question, if given), or claude.ai if the app isn't installed. */
    fun openClaude(question: String? = null) {
        val pm = context.packageManager
        val installed = pm.getLaunchIntentForPackage(CLAUDE_PACKAGE)
        if (installed != null) {
            if (!question.isNullOrBlank()) {
                val share = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, question)
                    .setPackage(CLAUDE_PACKAGE)
                if (start(share)) return
            }
            start(installed)
            return
        }
        val url = if (question.isNullOrBlank()) "https://claude.ai/new" else "https://claude.ai/new?q=" + Uri.encode(question)
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Starts the first intent that works. */
    /**
     * Called whenever a system screen or app was opened from here. The control center closes
     * then; otherwise it would stay on top and hide what just opened.
     */
    var onOpenedScreen: (() -> Unit)? = null

    private fun start(vararg intents: Intent): Boolean {
        val opened = intents.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (opened) onOpenedScreen?.invoke()
        return opened
    }

    fun close() {
        writerThread.quitSafely()
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
    }

    companion object {
        const val CLAUDE_PACKAGE = "com.anthropic.claude"

        /** Companion apps of popular earbuds (noise cancelling lives there). */
        val HeadphoneApps = listOf(
            "com.samsung.android.app.watchmanager",
            "com.sony.songpal.mdr",
            "com.bose.bosemusic",
            "com.bose.monet",
            "com.jabra.moments",
            "com.sennheiser.control",
            "com.nothing.ear",
            "com.nothing.x",
            "com.oneplus.twspods",
            "com.heytap.headset",
            "com.google.android.apps.wearables.maestro.companion",
            "com.apple.android.beats",
            "com.harman.jblmusicflow",
            "com.jbl.headphones",
            "com.soundcore.android",
            "com.oppo.melody",
            "com.huawei.smarthome",
            "com.xiaomi.wearable",
        )
    }
}
