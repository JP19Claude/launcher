package dev.hearth.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku: with it Hearth runs commands with the rights of ADB (no root, no new ROM), and so
 * can switch what Android keeps from normal apps – Wi-Fi, Bluetooth, mobile data, airplane
 * mode, location, NFC, dark mode and battery saver.
 *
 * Shizuku is its own app; it's started once after each reboot (wireless debugging), then
 * Hearth asks for permission once.
 */
object ShizukuBridge {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 4711

    enum class Status { NotInstalled, NotRunning, NoPermission, Ready }

    fun status(context: Context): Status {
        val installed = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return if (installed) Status.NotRunning else Status.NotInstalled
        val granted = runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) Status.Ready else Status.NoPermission
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST) }
    }

    /** Opens Shizuku (to start it), or its store page when it isn't installed. */
    fun openApp(context: Context) {
        val launch = runCatching { context.packageManager.getLaunchIntentForPackage(PACKAGE) }.getOrNull()
        val intents = listOfNotNull(
            launch,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")),
        )
        intents.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    /**
     * Runs [command] in a shell with Shizuku's rights; true if it worked. Shizuku keeps
     * newProcess out of its public API since version 13, so it's reached directly (as
     * Shizuku's own samples did before).
     */
    suspend fun run(command: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun onOff(on: Boolean) = if (on) "enable" else "disable"

    suspend fun setWifi(on: Boolean) = run("svc wifi ${onOff(on)}")

    suspend fun setBluetooth(on: Boolean) = run("cmd bluetooth_manager ${onOff(on)} || svc bluetooth ${onOff(on)}")

    suspend fun setMobileData(on: Boolean) = run("svc data ${onOff(on)}")

    suspend fun setAirplane(on: Boolean) =
        run("cmd connectivity airplane-mode ${onOff(on)} || settings put global airplane_mode_on ${if (on) 1 else 0}")

    suspend fun setLocation(on: Boolean) =
        run("cmd location set-location-enabled $on || settings put secure location_mode ${if (on) 3 else 0}")

    suspend fun setNfc(on: Boolean) = run("svc nfc ${onOff(on)}")

    suspend fun setDarkMode(on: Boolean) = run("cmd uimode night ${if (on) "yes" else "no"}")

    suspend fun setBatterySaver(on: Boolean) =
        run("cmd power set-mode ${if (on) 1 else 0} || settings put global low_power ${if (on) 1 else 0}")
}
