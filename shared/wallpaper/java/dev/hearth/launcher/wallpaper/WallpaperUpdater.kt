package dev.hearth.launcher.wallpaper

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * Updates for the wallpaper apps (ZENITH Hintergründe), from the apps themselves and from ZENITH: the newest build lists them
 * in versions.json (a plain download, no limit on how often phones may ask), each app takes its own file, downloads it,
 * checks that it is a whole APK of the very same app and hands it to Android's installer, which asks once more before
 * anything changes. Plain Java threads' work – every function here blocks, call it from a thread of its own.
 */
object WallpaperUpdater {

    private const val REPO = "JP19Claude/launcher"
    private const val PREFIX = "dev.hearth.wallpaper."

    /** The ending of a wallpaper app's package ("moon" for dev.hearth.wallpaper.moon), or null if it isn't one. */
    fun idOf(packageName: String): String? = packageName.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }

    fun packageOf(id: String): String = PREFIX + id

    /** Every wallpaper app: the ending of its package and the name of its scene. */
    val NAMES = listOf(
        "alle" to "Alle Szenen",
        "moon" to "Mond",
        "earth" to "Erde",
        "mars" to "Mars",
        "venus" to "Venus",
        "jupiter" to "Jupiter",
        "saturn" to "Saturn",
        "neptune" to "Neptun",
        "pluto" to "Pluto",
        "io" to "Io",
        "europa" to "Europa",
        "ganymede" to "Ganymed",
        "flag" to "Iwo Jima",
        "geometry" to "Geometrie",
    )

    /** A newer build of an app, ready to download. */
    class Update(val version: String, val build: Long, val url: String, val size: Long, val notes: String)

    sealed class Check {
        class Newer(val update: Update) : Check()
        object UpToDate : Check()
        class Failed(val reason: String) : Check()
    }

    /** What an installed app is: its version name and build number. */
    class Installed(val version: String, val build: Long)

    /** The installed wallpaper app [packageName], or null if it isn't installed (or this app may not see it). */
    fun installed(context: Context, packageName: String): Installed? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        Installed(info.versionName.orEmpty(), if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
    }.getOrNull()

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Hearth-Updater")
    }

    /** The newest build of the wallpaper apps, as the latest release lists it: its version, its number, what's new and a file for each app. */
    class Latest(val version: String, val build: Long, val notes: String, private val apps: JSONObject) {
        /** The download of the app with the ending [id], or null if there is none. */
        fun updateFor(id: String): Update? {
            val app = apps.optJSONObject(id) ?: return null
            val url = app.optString("url").takeIf { it.isNotEmpty() } ?: return null
            return Update(version, build, url, app.optLong("size"), notes)
        }
    }

    /** What the newest release says about the wallpaper apps; null if it can't be reached or doesn't list them. */
    fun latest(): Latest? {
        val wallpapers = versionsFile() ?: return null
        return Latest(wallpapers.optString("version"), wallpapers.optLong("build"), wallpapers.optString("notes"), wallpapers.optJSONObject("apps") ?: JSONObject())
    }

    private fun versionsFile(): JSONObject? = runCatching {
        val connection = open("https://github.com/$REPO/releases/latest/download/versions.json")
        if (connection.responseCode != 200) {
            connection.disconnect()
            return@runCatching null
        }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        json.optJSONObject("wallpapers")
    }.getOrNull()

    /**
     * Asks GitHub whether there is a newer build of the wallpaper app [packageName] than [installedBuild]. The builds count
     * up (a change of a picture raises it too), so "newer" is simply a bigger number.
     */
    fun check(packageName: String, installedBuild: Long): Check {
        val id = idOf(packageName) ?: return Check.Failed("Das ist keine Hintergrund-App")
        val latest = latest() ?: return Check.Failed("Keine Verbindung – bist du online? (Oder diese Version steht noch nicht im Netz.)")
        if (latest.build <= installedBuild) return Check.UpToDate
        return latest.updateFor(id)?.let { Check.Newer(it) } ?: Check.Failed("Für diese App gibt es keine Datei")
    }

    /** Downloads [update] into [context]'s cache; [progress] goes from 0 to 1. Null if it failed or the file is no whole APK of [packageName]. */
    fun download(context: Context, packageName: String, update: Update, progress: (Float) -> Unit): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$packageName-${update.build}.apk")
        val connection = open(update.url)
        if (connection.responseCode != 200) {
            connection.disconnect()
            return@runCatching null
        }
        val announced = connection.contentLengthLong
        val total = announced.takeIf { it > 0 } ?: update.size
        var received = 0L
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    received += n
                    if (total > 0) progress((received.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
        // A connection that drops can end the stream early without an error: a cut-off file is no APK.
        if ((announced > 0 && received != announced) || archivePackage(context, file) != packageName) {
            file.delete()
            return@runCatching null
        }
        file
    }.getOrNull()

    private fun archivePackage(context: Context, apk: File): String? = runCatching {
        context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
    }.getOrNull()

    /** May this app install updates? Android asks once ("Unbekannte Apps installieren"). */
    fun canInstall(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where this app is allowed to install updates. */
    fun askInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Hands [apk] to Android's installer; it asks the user (see [WallpaperUpdateReceiver]). False if it couldn't even start. */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            archivePackage(context, apk)?.let { setAppPackageName(it) }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, WallpaperUpdateReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(status.intentSender)
        }
        true
    }.getOrDefault(false)

    /** Android's own message for a failed install, with what it means. */
    fun explain(message: String): String {
        val hint = when {
            "inconsistent with" in message || "Mismatched package" in message -> "Die Datei gehört zu einer anderen App."
            "VERSION_DOWNGRADE" in message -> "Auf dem Handy ist schon eine neuere Version."
            "UPDATE_INCOMPATIBLE" in message || "SIGNATURE" in message || "CERTIFICATE" in message ->
                "Die Signatur der installierten App passt nicht zur neuen. Die alte App muss einmal deinstalliert werden."
            "INSUFFICIENT_STORAGE" in message -> "Auf dem Handy ist zu wenig Platz."
            "INVALID_APK" in message || "NOT_APK" in message || "MANIFEST" in message -> "Android hält die Datei für ungültig – meist ist sie unvollständig angekommen. Tipp noch einmal auf „Installieren“."
            else -> ""
        }
        return "Nicht installiert: " + message.ifBlank { "Android nennt keinen Grund." } + if (hint.isNotEmpty()) "\n$hint" else ""
    }
}

/** What Android's installer says about an update of a wallpaper app: it asks the user, or it's done (or failed). */
class WallpaperUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                runCatching { Toast.makeText(context, WallpaperUpdater.explain(message), Toast.LENGTH_LONG).show() }
            }
        }
    }
}
