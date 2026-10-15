package dev.hearth.launcher.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dev.hearth.launcher.data.ColorWorld
import dev.hearth.launcher.data.LauncherSettings
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.Date
import kotlin.system.exitProcess

/*
 * ZENITH 21.5 hotfix: when ZENITH crashes it no longer just dies. The error is written down (what, where, on which
 * phone), a screen in a process of its own shows it – with "copy", "share" and the ways back in – and after three
 * crashes within two minutes ZENITH starts in the safe mode: its optional looks (Be Smarter, Be Comet, ZENITH's own
 * wallpaper, the ceremonies after an update) are off and a Super-Wallpaper that is the phone's wallpaper is taken off
 * again, so a crash that comes with one of them can't repeat itself every start.
 */
object CrashGuard {

    private const val PREFS = "hearth_crash"
    private const val FILE = "last_crash.txt"

    /** True while ZENITH runs in the safe mode (set at the start of the process, read all over). */
    @Volatile
    var safe: Boolean = false
        private set

    /** True from a crash until its report was shown once. */
    @Volatile
    var pending: Boolean = false
        private set

    /** Reads what the last crashes decided; call at the start of the process. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        safe = prefs.getBoolean("safe", false)
        pending = prefs.getBoolean("pending", false)
    }

    /** From now on an uncaught error is written down and shown. Only in the process of ZENITH itself – the crash screen mustn't catch its own crashes. */
    fun install(app: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error, count = true) }
            runCatching {
                app.startActivity(
                    Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            }
            // Gone for good: no system dialog, no restart inside this process.
            runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
            exitProcess(10)
        }
    }

    /** An error that was caught on purpose (the wallpaper's engine, say): written down for the next start, the launcher lives on. */
    fun note(context: Context, error: Throwable) {
        runCatching {
            record(context, Thread.currentThread(), error, count = false)
            pending = true
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable, count: Boolean) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("?")
        val text = buildString {
            append("ZENITH ").append(version).append(" · ").append(context.packageName).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append(" · ").append(DateFormat.getDateTimeInstance().format(Date())).append("\n\n")
            append(trace)
        }
        File(context.filesDir, FILE).writeText(text.take(60_000))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit().putBoolean("pending", true)
        if (count) {
            val now = System.currentTimeMillis()
            val recent = prefs.getString("times", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 120_000L } + now
            edit.putString("times", recent.joinToString(","))
            if (recent.size >= 3) edit.putBoolean("safe", true)
        }
        edit.commit()
    }

    /** The report of the last crash, or null. */
    fun report(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    /** The report was shown. */
    fun shown(context: Context) {
        pending = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pending", false).commit()
    }

    /** Safe mode on or off (and the count of the crashes starts again). */
    fun setSafe(context: Context, on: Boolean) {
        safe = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("safe", on)
            .remove("times")
            .commit()
    }

    /** The last resort: ZENITH's settings are deleted (the apps, widgets and data stay). */
    fun resetSettings(context: Context) {
        runCatching { context.deleteSharedPreferences("hearth_settings") }
        runCatching { context.deleteSharedPreferences("hearth_ui") }
        runCatching { context.deleteSharedPreferences("zenith_boot") }
        setSafe(context, false)
    }
}

/** The safe mode's looks: what is optional and heavy is off. (Not written back: the settings stay as they were.) */
fun LauncherSettings.safeLook(): LauncherSettings = if (!CrashGuard.safe) {
    this
} else {
    copy(
        beSmarter = false,
        beComet = false,
        zenithWallpaper = false,
        zenithAurora = false,
        projectZenith = false,
        projectColorOs = false,
        hyperGlass = false,
        omegaOverdrive = false,
        colorWorld = ColorWorld.Off,
        clawdParade = false,
        giantClawd = false,
        allSeeingEye = false,
        weightless = false,
        zenithBootIntro = false,
        strikeOnUpdate = false,
    )
}
