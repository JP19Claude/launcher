package dev.hearth.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/** A small copy of the wallpaper plus the screen size it should be stretched to. */
class WallpaperBackdrop(
    val image: ImageBitmap,
    val screenWidth: Int,
    val screenHeight: Int,
)

/**
 * Provides the wallpaper as a bitmap so glass surfaces can blur and refract it.
 *
 * Reading the wallpaper needs "All files access" on Android 11+
 * (or storage permission on Android 9/10). Without it, glass falls back to a frosted tint.
 */
class WallpaperRepository(private val context: Context) {

    private val wallpaperManager = WallpaperManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    private val _backdrop = MutableStateFlow<WallpaperBackdrop?>(null)
    val backdrop: StateFlow<WallpaperBackdrop?> = _backdrop.asStateFlow()

    private val _needsAccess = MutableStateFlow(false)
    val needsAccess: StateFlow<Boolean> = _needsAccess.asStateFlow()

    // Fires when the wallpaper changes.
    private val colorsListener = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) refresh()
    }

    init {
        wallpaperManager.addOnColorsChangedListener(colorsListener, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch { load() }
    }

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    private fun load() {
        if (!hasAccess()) {
            _backdrop.value = null
            _needsAccess.value = true
            return
        }
        _needsAccess.value = false

        // Live wallpapers usually have no readable image; glass then uses the frosted fallback.
        val drawable = runCatching { wallpaperManager.drawable }.getOrNull()
        if (drawable == null) {
            _backdrop.value = null
            return
        }

        val (screenW, screenH) = screenSize()
        // Android 12+ blurs on the GPU, so a 1/4 copy is enough.
        // Older versions get a tiny copy; stretching it back up acts as the blur.
        val factor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 4 else 16
        val outW = (screenW / factor).coerceAtLeast(1)
        val outH = (screenH / factor).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dw = drawable.intrinsicWidth.takeIf { it > 0 } ?: screenW
        val dh = drawable.intrinsicHeight.takeIf { it > 0 } ?: screenH
        // Center-crop, like the system draws the wallpaper behind the launcher.
        val scale = max(outW.toFloat() / dw, outH.toFloat() / dh)
        val w = (dw * scale).toInt()
        val h = (dh * scale).toInt()
        val left = (outW - w) / 2
        val top = (outH - h) / 2
        drawable.setBounds(left, top, left + w, top + h)
        drawable.draw(canvas)

        _backdrop.value = WallpaperBackdrop(bitmap.asImageBitmap(), screenW, screenH)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    fun close() {
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        scope.cancel()
    }
}
