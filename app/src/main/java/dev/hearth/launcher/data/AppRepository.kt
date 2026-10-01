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
import android.os.Build
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which icons to render: icon pack, and full icons or symbols for glass tiles. */
data class IconConfig(
    val iconPack: String? = null,
    val glyphs: Boolean = false,
    val preferMonochrome: Boolean = false,
    val adaptUnthemed: Boolean = true,
)

/**
 * Loads all launchable apps (personal and work profile) and keeps the list
 * up to date when apps are installed, updated or removed.
 *
 * Nothing is loaded until [setIconConfig] is called the first time.
 */
class AppRepository(
    private val context: Context,
    private val iconPacks: IconPackRepository,
) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconSizePx = (context.resources.displayMetrics.density * 96).toInt()
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    @Volatile private var config: IconConfig? = null
    @Volatile private var pack: IconPack? = null
    @Volatile private var packLoaded = false

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packageEvent(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = packageEvent(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = packageEvent(packageName)
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
    }

    fun setIconConfig(newConfig: IconConfig) {
        if (newConfig == config) return
        if (newConfig.iconPack != config?.iconPack) packLoaded = false
        config = newConfig
        refresh()
    }

    private fun packageEvent(packageName: String) {
        // An updated icon pack has to be parsed again.
        if (packageName == config?.iconPack) packLoaded = false
        refresh()
    }

    fun refresh() {
        val current = config ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            if (!packLoaded) {
                pack = current.iconPack?.let(iconPacks::load)
                packLoaded = true
            }
            val list = loadApps(current, pack)
            ensureActive()
            _apps.value = list
        }
    }

    private fun loadApps(config: IconConfig, pack: IconPack?): List<AppInfo> =
        launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    val (bitmap, kind) = renderIcon(info, config, pack)
                    AppInfo(
                        label = info.label.toString(),
                        component = info.componentName,
                        user = info.user,
                        icon = bitmap.asImageBitmap(),
                        iconKind = kind,
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }

    private fun renderIcon(info: LauncherActivityInfo, config: IconConfig, pack: IconPack?): Pair<Bitmap, IconKind> {
        val original = info.getIcon(densityDpi)

        if (pack != null) {
            val themed = pack.iconFor(info.componentName, densityDpi)
            if (themed != null) {
                return if (config.glyphs) renderScaled(themed, GLYPH_SCALE) to IconKind.Glyph
                else renderScaled(themed, 1f) to IconKind.Free
            }
            if (config.adaptUnthemed) {
                val adapted = pack.adapt(original, info.componentName, iconSizePx, densityDpi)
                if (adapted != null) {
                    return if (config.glyphs) scaleBitmap(adapted, GLYPH_SCALE) to IconKind.Glyph
                    else adapted to IconKind.Free
                }
            }
        }

        if (!config.glyphs) {
            val bitmap = if (original is AdaptiveIconDrawable) renderAdaptive(original) else renderLegacy(original)
            return bitmap to IconKind.Shaped
        }

        // Glass tiles: only the symbol, the glass is the background.
        if (original is AdaptiveIconDrawable) {
            if (config.preferMonochrome && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                original.monochrome?.let { return renderLayer(it) to IconKind.MonoGlyph }
            }
            original.foreground?.let { return renderLayer(it) to IconKind.Glyph }
        }
        return renderScaled(original, LEGACY_GLYPH_SCALE) to IconKind.Glyph
    }

    /** One adaptive icon layer, unmasked, with the same framing as [renderAdaptive]. */
    private fun renderLayer(layer: Drawable): Bitmap {
        val size = iconSizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size / 4
        layer.bounds = Rect(-inset, -inset, size + inset, size + inset)
        layer.draw(Canvas(bitmap))
        return bitmap
    }

    /** Draws the whole drawable centered, at [scale] of the icon size. */
    private fun renderScaled(drawable: Drawable, scale: Float): Bitmap {
        val size = iconSizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inner = (size * scale).toInt()
        val offset = (size - inner) / 2
        drawable.bounds = Rect(offset, offset, offset + inner, offset + inner)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun scaleBitmap(source: Bitmap, scale: Float): Bitmap {
        val size = iconSizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inner = (size * scale).toInt()
        val offset = (size - inner) / 2
        Canvas(bitmap).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(offset, offset, offset + inner, offset + inner),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        return bitmap
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

    fun webSearch(query: String, engine: SearchEngine) {
        if (engine.url != null) {
            val url = Uri.parse(engine.url + Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
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

    private companion object {
        /** Icon pack icons on glass tiles: a bit smaller, so the glass shows around them. */
        const val GLYPH_SCALE = 0.78f
        const val LEGACY_GLYPH_SCALE = 0.66f
    }
}
