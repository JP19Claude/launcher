package dev.hearth.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.hearth.launcher.system.OldClawdApp
import dev.hearth.launcher.system.OldGlimmerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moving to Hearth One (Hearth with Glimmer and Clawd built in): first Glimmer's and Clawd's
 * data comes into Hearth, then Hearth One installs over Hearth (same app, so Hearth keeps
 * everything), and the separate apps can go.
 */
object HearthOneUpgrade {

    private val OldApps = listOf(OldGlimmerApp to AppUpdater.GlimmerApp, OldClawdApp to AppUpdater.ClawdApp)

    /** The separate Glimmer and Clawd apps still on the phone (package names). */
    fun oldAppsInstalled(context: Context): List<String> = OldApps
        .filter { (app, _) -> app.packageName != context.packageName && app.isInstalled(context) }
        .map { (app, _) -> app.packageName }

    /**
     * Takes Glimmer's and Clawd's data into this app. Returns the old apps too old to hand
     * it over (they need an update first).
     */
    suspend fun takeOver(context: Context): List<AppUpdater.Kind> = withContext(Dispatchers.IO) {
        val tooOld = mutableListOf<AppUpdater.Kind>()
        for ((app, kind) in OldApps) {
            if (app.packageName == context.packageName || !app.isInstalled(context)) continue
            val data = app.exportData(context)
            if (data == null) {
                tooOld += kind
            } else {
                AppData.import(context, data, fromGlimmer = kind === AppUpdater.GlimmerApp)
            }
        }
        if (tooOld.isEmpty()) context.getSharedPreferences("hearth_one", Context.MODE_PRIVATE).edit().putBoolean("takenOver", true).apply()
        tooOld
    }

    /** Was everything taken over already (before the upgrade)? */
    fun takenOver(context: Context): Boolean =
        context.getSharedPreferences("hearth_one", Context.MODE_PRIVATE).getBoolean("takenOver", false)

    /** Android asks once whether to remove the old app. */
    fun uninstall(context: Context, packageName: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun label(packageName: String): String = when (packageName) {
        AppUpdater.GlimmerApp.packageName -> "Glimmer"
        AppUpdater.ClawdApp.packageName -> "Clawd"
        else -> packageName
    }
}
