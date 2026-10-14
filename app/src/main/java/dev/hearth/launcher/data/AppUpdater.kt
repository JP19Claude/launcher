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
 * Updates for every app of the family (Hearth UI, Hearth, Glimmer, Clawd), straight from
 * the app: it asks GitHub for the newest release of itself, downloads the APK and hands it
 * to Android's installer (which asks once more before anything changes).
 */
object AppUpdater {

    private const val REPO = "JP19Claude/launcher"

    /** An app of the family as the releases name it (tag "glimmer-v9.5", file "Glimmer-9.5.apk"). */
    class Kind(val slug: String, val name: String, val packageName: String)

    /** Hearth UI (once Hearth One): Hearth with Glimmer and Clawd built in. Its slug stays. */
    val HearthOne = Kind("hearthone", "ZENITH", "dev.hearth.launcher")
    /** ZENITH Smart: an app of its own next to ZENITH, with its own update line. */
    val SmartApp = Kind("smart", "ZENITH Smart", "dev.hearth.smart")
    val GlimmerApp = Kind("glimmer", "Glimmer", "dev.hearth.glimmer")
    val ClawdApp = Kind("clawd", "Clawd", "dev.hearth.clawd")

    private fun kind(context: Context): Kind = when {
        dev.hearth.launcher.BuildConfig.SMART -> SmartApp
        dev.hearth.launcher.BuildConfig.ALL_IN_ONE -> HearthOne
        context.packageName == "dev.hearth.glimmer" -> GlimmerApp
        context.packageName == "dev.hearth.clawd" -> ClawdApp
        else -> Kind("hearth", "Hearth", "dev.hearth.launcher")
    }

    /** The version of an installed app of the family, or null. */
    fun installedVersion(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun appName(context: Context): String = kind(context).name

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** The build number of this app (hotfixes raise it, the version stays). */
    fun currentBuild(context: Context): Long = runCatching {
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    /** A newer version, ready to download – or a hotfix: the same version, a newer [build]. */
    class Release(
        val version: String,
        val url: String,
        val size: Long,
        val notes: String = "",
        val build: Long = 0L,
        /** The release's own version card as JSON (ZENITH 20: the update screen draws it before installing); "" for none. */
        val card: String = "",
    ) {
        /** A hotfix for [installed]: no newer version, only a newer build. */
        fun isHotfix(installed: String): Boolean = build > 0L && !AppUpdater.isNewer(version, installed)

        /** "19.0" – or, for a hotfix, "19.0 Hotfix (Build 150)". */
        fun label(installed: String): String = if (isHotfix(installed)) "$version Hotfix (Build $build)" else version
    }

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

    /**
     * Asks GitHub for this app's newest release – or [other]'s (Hearth One to upgrade to, an
     * old Glimmer to bring up to date), newer than [than] (this app's version by default).
     */
    suspend fun check(context: Context, other: Kind? = null, than: String? = null): Check = withContext(Dispatchers.IO) {
        val kind = other ?: kind(context)
        val current = than ?: currentVersion(context)
        // First the plain versions file every build publishes (no API limit), then the API.
        fromVersionsFile(kind.slug)?.let { newest ->
            prefs(context).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            // A newer version – or, for this app itself, a hotfix: the same version, a newer build.
            val hotfix = other == null && than == null && newest.build > 0L && !isNewer(current, newest.version) &&
                newest.build > currentBuild(context)
            return@withContext if (isNewer(newest.version, current) || hotfix) Check.Newer(newest) else Check.UpToDate
        }
        runCatching {
            val connection = open("https://api.github.com/repos/$REPO/releases?per_page=100")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code != 200) {
                connection.disconnect()
                return@runCatching Check.Failed(
                    if (code == 403) "GitHub lässt gerade keine Abfrage zu – versuch es später nochmal" else "GitHub antwortet nicht (Fehler $code)",
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

    /**
     * Every published version of this app, newest first – for going back to an older one
     * (Software-Update → Downgrade). Reads GitHub's release list page by page.
     */
    suspend fun allVersions(context: Context): Result<List<Release>> = withContext(Dispatchers.IO) {
        val kind = kind(context)
        runCatching {
            val found = mutableListOf<Release>()
            for (page in 1..5) {
                val connection = open("https://api.github.com/repos/$REPO/releases?per_page=100&page=$page")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                val code = connection.responseCode
                if (code != 200) {
                    connection.disconnect()
                    if (found.isNotEmpty()) break
                    error(if (code == 403) "GitHub lässt gerade keine Abfrage zu – versuch es später nochmal" else "GitHub antwortet nicht (Fehler $code)")
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val releases = JSONArray(text)
                if (releases.length() == 0) break
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val tag = release.optString("tag_name")
                    if (!tag.startsWith("${kind.slug}-v")) continue
                    val assets = release.optJSONArray("assets") ?: continue
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.optString("name").endsWith(".apk")) {
                            found += Release(tag.removePrefix("${kind.slug}-v"), asset.optString("browser_download_url"), asset.optLong("size"), releaseNotes(release.optString("body")))
                            break
                        }
                    }
                }
                if (releases.length() < 100) break
            }
            found.distinctBy { it.version }.sortedWith { a, b ->
                when {
                    isNewer(a.version, b.version) -> -1
                    isNewer(b.version, a.version) -> 1
                    else -> 0
                }
            }
        }
    }

    /**
     * The newest versions as the latest build lists them (versions.json): a plain download,
     * not GitHub's API, so it works however often phones ask. Null if it isn't there.
     */
    private fun fromVersionsFile(slug: String): Release? = runCatching {
        val connection = open("https://github.com/$REPO/releases/latest/download/versions.json")
        if (connection.responseCode != 200) {
            connection.disconnect()
            return@runCatching null
        }
        val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val app = json.optJSONObject(slug) ?: return@runCatching null
        Release(
            app.getString("version"),
            app.getString("url"),
            app.optLong("size"),
            json.optString("notes"),
            app.optLong("build", json.optLong("build")),
            json.optJSONObject("card")?.toString().orEmpty(),
        )
    }.getOrNull()

    /** What's new, as the release tells it (without the build line at its end). */
    private fun releaseNotes(body: String): String =
        body.lines().filterNot { it.startsWith("Debug-APK") || it.contains("Debug-APK aus Build") }.joinToString("\n").trim()

    /** Downloads [release] into the app's cache; [progress] goes from 0 to 1. Null if it failed. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "${kind(context).slug}-${release.version}-${release.url.hashCode()}.apk")
            val connection = open(release.url)
            if (connection.responseCode != 200) {
                connection.disconnect()
                return@runCatching null
            }
            val announced = connection.contentLengthLong
            val total = announced.takeIf { it > 0 } ?: release.size
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
            // A connection that drops can end the stream early without an error: a cut-off file is
            // no APK (Android would only say it is invalid), so it's thrown away here.
            if ((announced > 0 && received != announced) || archivePackage(context, file) == null) {
                file.delete()
                return@runCatching null
            }
            file
        }.getOrNull()
    }

    /** The package a downloaded file is for – or null if it isn't a whole APK (a cut-off download). */
    private fun archivePackage(context: Context, apk: File): String? = runCatching {
        context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName
    }.getOrNull()

    /** What Android said when it didn't install the last update, in full and in plain words; "" for nothing. */
    fun installError(context: Context): String = prefs(context).getString("installError", "").orEmpty()

    fun saveInstallError(context: Context, text: String) {
        prefs(context).edit().putString("installError", text).apply()
    }

    fun clearInstallError(context: Context) {
        prefs(context).edit().remove("installError").apply()
    }

    /** Calls [onError] whenever an install fails; the returned function stops listening. */
    fun watchInstallError(context: Context, onError: (String) -> Unit): () -> Unit {
        val preferences = prefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "installError") p.getString(key, "")?.takeIf { it.isNotBlank() }?.let(onError)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Android's own message for a failed install, with what it means and what to do. */
    fun explainInstallError(message: String): String {
        val hint = when {
            "inconsistent with" in message || "Mismatched package" in message ->
                "Die Datei gehört zu einer anderen App (ein anderes Paket) und kann diese App nicht ersetzen. Lade sie von GitHub herunter und installiere sie von Hand – sie kommt dann als eigene App daneben."
            "VERSION_DOWNGRADE" in message ->
                "Auf dem Handy ist schon eine neuere Version."
            "UPDATE_INCOMPATIBLE" in message || "SIGNATURE" in message || "CERTIFICATE" in message ->
                "Die Signatur der installierten App passt nicht zur neuen. Die alte App muss einmal deinstalliert werden."
            "INSUFFICIENT_STORAGE" in message -> "Auf dem Handy ist zu wenig Platz."
            "INVALID_APK" in message || "NOT_APK" in message || "MANIFEST" in message ->
                "Android hält die Datei für ungültig. Meist ist sie unvollständig angekommen – tipp noch einmal auf „Installieren“."
            else -> ""
        }
        return "Nicht installiert: " + message.ifBlank { "Android nennt keinen Grund." } + if (hint.isNotEmpty()) "\n$hint" else ""
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
    fun install(context: Context, apk: File, packageName: String = context.packageName, downgrade: Boolean = false): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            // The file's own package: naming a different one is what Android answers with
            // INSTALL_FAILED_INVALID_APK ("Specified package … inconsistent with …").
            setAppPackageName(archivePackage(context, apk) ?: packageName)
        }
        // Going back to an older version: Android allows it for Hearth's (debuggable) builds
        // when the installer asks for it – which only works where the system lets an app ask.
        if (downgrade) requestDowngrade(params)
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

/** Asks the installer to allow an older version (INSTALL_REQUEST_DOWNGRADE); quietly does nothing where it can't. */
private fun requestDowngrade(params: PackageInstaller.SessionParams) {
    val viaMethod = runCatching {
        PackageInstaller.SessionParams::class.java
            .getMethod("setRequestDowngrade", Boolean::class.javaPrimitiveType)
            .invoke(params, true)
    }.isSuccess
    if (!viaMethod) {
        runCatching {
            val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
            field.isAccessible = true
            field.setInt(params, field.getInt(params) or 0x00000080)
        }
    }
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
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                // The whole text for the update screen (a toast is cut off after a line).
                AppUpdater.saveInstallError(context, AppUpdater.explainInstallError(message))
                runCatching {
                    android.widget.Toast.makeText(
                        context,
                        "Update nicht installiert" + (if (message.isNotBlank()) ": $message" else ""),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
