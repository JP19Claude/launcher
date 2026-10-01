package dev.hearth.launcher.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads all launchable apps (personal and work profile) and keeps the list
 * up to date when apps are installed, updated or removed.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconSizePx = (context.resources.displayMetrics.density * 96).toInt()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = refresh()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch { _apps.value = loadApps() }
    }

    private fun loadApps(): List<AppInfo> =
        launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                AppInfo(
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    icon = renderIcon(info).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }

    private fun renderIcon(info: LauncherActivityInfo): Bitmap {
        val drawable = info.getIcon(context.resources.displayMetrics.densityDpi)
        return if (drawable is AdaptiveIconDrawable) renderAdaptive(drawable) else renderLegacy(drawable)
    }

    /**
     * Draws the adaptive icon layers without the system mask,
     * so the UI can clip them to its own squircle shape.
     */
    private fun renderAdaptive(drawable: AdaptiveIconDrawable): Bitmap {
        val size = iconSizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Layers are 108dp with a 72dp visible area: extend by 1/4 on each side.
        val inset = size / 4
        val bounds = Rect(-inset, -inset, size + inset, size + inset)
        drawable.background?.let { it.bounds = bounds; it.draw(canvas) }
        drawable.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        return bitmap
    }

    /** Older icons get a light plate behind them so they fit the grid. */
    private fun renderLegacy(drawable: Drawable): Bitmap {
        val size = iconSizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFE7E3DC.toInt())
        val pad = (size * 0.14f).toInt()
        val inner = drawable.toBitmap(size - 2 * pad, size - 2 * pad)
        canvas.drawBitmap(inner, pad.toFloat(), pad.toFloat(), null)
        return bitmap
    }

    fun launch(app: AppInfo, sourceBounds: Rect? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, sourceBounds, null) }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun webSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            val url = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            val fallback = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    /** Packages of the default phone, messages, browser and camera apps. */
    @Suppress("DEPRECATION")
    fun defaultDockPackages(): List<String> {
        val pm = context.packageManager
        return listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        ).mapNotNull { intent ->
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.filter { it != "android" } // "android" = system chooser, no default set
    }

    fun close() {
        launcherApps.unregisterCallback(callback)
        scope.cancel()
    }
}
