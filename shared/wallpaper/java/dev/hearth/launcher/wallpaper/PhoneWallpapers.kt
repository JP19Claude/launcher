package dev.hearth.launcher.wallpaper

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.wallpaper.WallpaperService

/** A live wallpaper that is installed on this phone: what Android's own wallpaper picker lists. */
class PhoneWallpaper(
    val component: ComponentName,
    val label: String,
    val author: String,
    val summary: String,
    val thumbnail: Bitmap?,
    /** One of OnePlus' own (see [PhoneWallpapers.isOnePlus]). */
    val onePlus: Boolean,
)

/**
 * The live wallpapers installed on the phone – OnePlus' own and everybody else's – for a list of their own in the settings. They are
 * only looked at and handed to Android's preview ("Hintergrund festlegen"): nothing of them is copied, changed or passed on, they stay
 * what their makers made.
 */
object PhoneWallpapers {

    /**
     * OnePlus' wallpapers sit in packages with the names of the company and of the system under OxygenOS (which is built on OPPO's ColorOS):
     * oneplus, oplus, coloros, heytap, oppo.
     */
    private val ONEPLUS_MARKS = listOf("oneplus", "oplus", "coloros", "heytap", "oppo")

    fun isOnePlus(packageName: String): Boolean = ONEPLUS_MARKS.any { packageName.contains(it, ignoreCase = true) }

    /** Every live wallpaper of the phone except ZENITH's own (those have their own places): OnePlus' first, then the others, each by name. Off the main thread. */
    fun load(context: Context): List<PhoneWallpaper> {
        val pm = context.packageManager
        val services = runCatching {
            pm.queryIntentServices(Intent(WallpaperService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        return services.mapNotNull { resolved ->
            runCatching {
                val info = WallpaperInfo(context, resolved)
                val pkg = info.packageName
                if (pkg == context.packageName || pkg.startsWith("dev.hearth.")) return@runCatching null
                PhoneWallpaper(
                    component = info.component,
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg),
                    author = runCatching { info.loadAuthor(pm)?.toString().orEmpty() }.getOrDefault(""),
                    summary = runCatching { info.loadDescription(pm)?.toString().orEmpty() }.getOrDefault(""),
                    thumbnail = runCatching { info.loadThumbnail(pm)?.let { thumb(it) } }.getOrNull(),
                    onePlus = isOnePlus(pkg),
                )
            }.getOrNull()
        }.sortedWith(compareBy({ !it.onePlus }, { it.label.lowercase() }))
    }

    /** The picture of a wallpaper's thumbnail, 160 × 240. */
    private fun thumb(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 160, 240)
        drawable.draw(canvas)
        return bitmap
    }

    /** Android's preview of [wallpaper] (where "Hintergrund festlegen" sets it); the system's picker if the phone has no direct way. */
    fun openPreview(context: Context, wallpaper: PhoneWallpaper) {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, wallpaper.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isFailure) openPicker(context)
    }

    /** The phone's own list of live wallpapers. */
    fun openPicker(context: Context) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
