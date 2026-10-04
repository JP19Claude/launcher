package dev.hearth.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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

    /** Like [run], but hands back what the command printed (null if it failed). */
    suspend fun output(command: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        }.getOrNull()
    }

    fun isReady(context: Context): Boolean = status(context) == Status.Ready

    /** Stops an app at once (it starts again when it's opened). */
    suspend fun forceStop(packageName: String) = run("am force-stop $packageName")

    /** Freezes an app: it stays installed but can't start until it's thawed. */
    suspend fun freeze(packageName: String) = run("pm disable-user --user 0 $packageName")

    suspend fun thaw(packageName: String) = run("pm enable $packageName")

    /** The apps that are frozen (disabled) right now. */
    suspend fun frozenApps(): List<String> =
        output("pm list packages -d")?.lines().orEmpty()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }

    /** Ends apps waiting in the background, to free memory. */
    suspend fun killBackgroundApps() = run("am kill-all")

    /** System animations (windows, transitions, animators): 0 = off, 1 = normal. */
    suspend fun setAnimationScale(scale: Float): Boolean {
        val v = if (scale == 0f) "0" else scale.toString()
        return run(
            "settings put global window_animation_scale $v && settings put global transition_animation_scale $v && " +
                "settings put global animator_duration_scale $v",
        )
    }

    fun animationScale(context: Context): Float =
        runCatching { Settings.Global.getFloat(context.contentResolver, "animator_duration_scale", 1f) }.getOrDefault(1f)

    /** Private DNS: a blocker of ads and trackers for the whole phone, or the system's default. */
    enum class Dns(val label: String, val host: String?) {
        Off("Aus", null),
        AdGuard("AdGuard (Werbung blocken)", "dns.adguard-dns.com"),
        Cloudflare("Cloudflare (schnell)", "one.one.one.one"),
        Quad9("Quad9 (Schutz vor Schadseiten)", "dns.quad9.net"),
    }

    fun dns(context: Context): Dns {
        val mode = runCatching { Settings.Global.getString(context.contentResolver, "private_dns_mode") }.getOrNull()
        val host = runCatching { Settings.Global.getString(context.contentResolver, "private_dns_specifier") }.getOrNull()
        return if (mode == "hostname") Dns.entries.firstOrNull { it.host == host } ?: Dns.Off else Dns.Off
    }

    suspend fun setDns(dns: Dns): Boolean = if (dns.host == null) {
        run("settings put global private_dns_mode opportunistic")
    } else {
        run("settings put global private_dns_specifier ${dns.host} && settings put global private_dns_mode hostname")
    }

    /** The packages installed for this user ([flags] e.g. "-u" for removed ones too). */
    suspend fun packages(flags: String = ""): Set<String> =
        output("pm list packages $flags")?.lines().orEmpty()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .toSet()

    /** Removes a preinstalled app for this user; it stays on the phone and can come back. */
    suspend fun removeForUser(packageName: String) = run("pm uninstall -k --user 0 $packageName")

    /** Brings back an app removed with [removeForUser]. */
    suspend fun restore(packageName: String) = run("cmd package install-existing $packageName")

    /** Takes a permission back from an app (it can ask again). */
    suspend fun revoke(packageName: String, permission: String) = run("pm revoke $packageName $permission")

    /** Forbids (or allows again) an app to run in the background – saves battery. */
    suspend fun restrictBackground(packageName: String, restrict: Boolean) = if (restrict) {
        run("cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore && (am set-standby-bucket $packageName restricted || true)")
    } else {
        run("cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow && (am set-standby-bucket $packageName active || true)")
    }

    /** Empties the caches of all apps (what each app can download again anyway). */
    suspend fun trimCaches() = run("pm trim-caches 999G")

    suspend fun putGlobal(key: String, value: String) = run("settings put global $key $value")

    suspend fun putSecure(key: String, value: String) = run("settings put secure $key $value")

    /**
     * Installs [apk] over the app that's there – also an older version (Hearth's builds allow
     * going back). The file is handed to the installer through the shell's input.
     */
    suspend fun installApk(apk: java.io.File, allowDowngrade: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val flags = if (allowDowngrade) "-r -d" else "-r"
            val process = method.invoke(null, arrayOf("sh", "-c", "pm install $flags -S ${apk.length()}"), null, null) as Process
            process.outputStream.use { out -> apk.inputStream().use { it.copyTo(out) } }
            val result = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            result.contains("Success")
        }.getOrDefault(false)
    }

    suspend fun screenOff() = run("input keyevent 223")

    /** OMEGA UI 17: an app as a floating window (Android's freeform window mode). */
    suspend fun openInWindow(component: String): Boolean = run("am start --windowingMode 5 -n $component")

    suspend fun reboot() = run("svc power reboot")

    suspend fun shutdown() = run("svc power shutdown")
}
