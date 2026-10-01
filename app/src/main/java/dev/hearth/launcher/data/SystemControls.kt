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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import dev.hearth.launcher.system.ControlCenterService
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
)

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

    fun state(): ControlState = ControlState(
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
    )

    fun requestWriteSettings() {
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    // Brightness, 0..1 on a perceptual curve (the eye sees brightness roughly as a square root).

    fun brightness(): Float {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, maxBrightness / 2)
        val max = max(maxBrightness, raw).coerceAtLeast(1)
        return sqrt(raw.toFloat() / max).coerceIn(0f, 1f)
    }

    /** Returns false when the permission is missing. */
    fun setBrightness(level: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val l = level.coerceIn(0f, 1f)
            val raw = (l * l * maxBrightness).roundToInt().coerceIn(1, maxBrightness)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        }.isSuccess
    }

    fun setAutoBrightness(on: Boolean): Boolean = writeSystem(
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    )

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
        runCatching { a.setStreamVolume(AudioManager.STREAM_MUSIC, (level.coerceIn(0f, 1f) * max).roundToInt(), 0) }
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
        if (ControlCenterService.instance?.showNotifications() == true) return true
        val service = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
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
    private fun start(vararg intents: Intent): Boolean = intents.any { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun close() {
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
    }

    companion object {
        const val CLAUDE_PACKAGE = "com.anthropic.claude"
    }
}
