package dev.hearth.launcher.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates for every app of the family (Hearth, Glimmer, Kontrollzentrum, Clawd), straight from
 * the app: it asks GitHub for the newest release of itself, downloads the APK and hands it
 * to Android's installer (which asks once more before anything changes).
 */
object AppUpdater {

    private const val REPO = "JP19Claude/launcher"

    /** This app's name in the releases (tag "glimmer-v9.5", file "Glimmer-9.5.apk"). */
    private class Kind(val slug: String, val name: String)

    private fun kind(context: Context): Kind = when (context.packageName) {
        "dev.hearth.glimmer" -> Kind("glimmer", "Glimmer")
        "dev.hearth.controls" -> Kind("kontrollzentrum", "Kontrollzentrum")
        "dev.hearth.clawd" -> Kind("clawd", "Clawd")
        else -> Kind("hearth", "Hearth")
    }

    fun appName(context: Context): String = kind(context).name

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** A newer version, ready to download. */
    class Release(val version: String, val url: String, val size: Long, val notes: String = "")

    private fun prefs(context: Context) = context.getSharedPreferences("hearth_updates", Context.MODE_PRIVATE)

    /** When it last asked GitHub (ms), 0 = never. */
    fun lastChecked(context: Context): Long = prefs(context).getLong("lastCheck", 0L)

    sealed interface Check {
        class Newer(val release: Release) : Check
        data object UpToDate : Check
        class Failed(val reason: String) : Check
    }

    /** "9.5" → [9, 5], to compare versions number by number. */
    private fun parts(version: String): List<Int> = version.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Hearth-Updater")
    }

    /** Asks GitHub for this app's newest release. */
    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        val kind = kind(context)
        val current = currentVersion(context)
        runCatching {
            val connection = open("https://api.github.com/repos/$REPO/releases?per_page=100")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code != 200) {
                connection.disconnect()
                return@runCatching Check.Failed(
                    if (code == 403) "GitHub hat gerade zu viele Anfragen – versuch es in einer Stunde nochmal" else "GitHub antwortet nicht (Fehler $code)",
                )
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val releases = JSONArray(text)
            var best: Release? = null
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name")
                if (!tag.startsWith("${kind.slug}-v")) continue
                val version = tag.removePrefix("${kind.slug}-v")
                if (best != null && !isNewer(version, best.version)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name").endsWith(".apk")) {
                        best = Release(version, asset.optString("browser_download_url"), asset.optLong("size"), releaseNotes(release.optString("body")))
                        break
                    }
                }
            }
            val newest = best
            prefs(context).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            when {
                newest == null -> Check.Failed("Keine Version von ${kind.name} gefunden")
                isNewer(newest.version, current) -> Check.Newer(newest)
                else -> Check.UpToDate
            }
        }.getOrElse { Check.Failed("Keine Verbindung – bist du online?") }
    }

    /** What's new, as the release tells it (without the build line at its end). */
    private fun releaseNotes(body: String): String =
        body.lines().filterNot { it.startsWith("Debug-APK") || it.contains("Debug-APK aus Build") }.joinToString("\n").trim()

    /** Downloads [release] into the app's cache; [progress] goes from 0 to 1. Null if it failed. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "${kind(context).name}-${release.version}.apk")
            val connection = open(release.url)
            if (connection.responseCode != 200) {
                connection.disconnect()
                return@runCatching null
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            file
        }.getOrNull()
    }

    /** May this app install updates? Android asks once ("Unbekannte Apps installieren"). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where this app is allowed to install updates. */
    fun askInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Hands the downloaded APK to Android's installer; it shows its own "update this app?"
     * question (see [UpdateReceiver]). False if it couldn't even start.
     */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
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
                Intent(context, UpdateReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(status.intentSender)
        }
        true
    }.getOrDefault(false)
}

/** What Android's installer says about an update: it asks the user, or it's done (or failed). */
class UpdateReceiver : android.content.BroadcastReceiver() {
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
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                runCatching {
                    android.widget.Toast.makeText(
                        context,
                        "Update nicht installiert" + (message?.let { ": $it" } ?: ""),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
