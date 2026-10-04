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

/**
 * A small copy of the wallpaper plus the screen size it should be stretched to.
 * [soft] and [strong] are blurred once up front, so glass surfaces only have to draw
 * them instead of blurring the wallpaper again in every frame (that was the main lag).
 */
class WallpaperBackdrop(
    val image: ImageBitmap,
    val soft: ImageBitmap,
    val strong: ImageBitmap,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** Fast blur on the CPU: three box blurs in a row look like a Gaussian blur. */
internal fun blurred(source: Bitmap, radius: Int, downscale: Int = 1): Bitmap {
    val small = if (downscale > 1) {
        Bitmap.createScaledBitmap(
            source,
            (source.width / downscale).coerceAtLeast(1),
            (source.height / downscale).coerceAtLeast(1),
            true,
        )
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val w = small.width
    val h = small.height
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    val buffer = IntArray(w * h)
    val r = radius.coerceAtLeast(1)
    repeat(3) {
        boxBlur(pixels, buffer, w, h, r, horizontal = true)
        boxBlur(buffer, pixels, w, h, r, horizontal = false)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val window = 2 * r + 1
    for (line in 0 until lines) {
        fun index(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -r..r) {
            val p = src[index(i)]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (i in 0 until length) {
            dst[index(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            val out = src[index(i - r)]
            val inn = src[index(i + r + 1)]
            sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            sb += (inn and 0xFF) - (out and 0xFF)
        }
    }
}

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

        val soft = if (factor <= 4) blurred(bitmap, radius = 9) else bitmap
        val strong = if (factor <= 4) blurred(bitmap, radius = 10, downscale = 3) else bitmap
        _backdrop.value = WallpaperBackdrop(
            image = bitmap.asImageBitmap(),
            soft = soft.asImageBitmap(),
            strong = strong.asImageBitmap(),
            screenWidth = screenW,
            screenHeight = screenH,
        )
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        // Hearth stands upright: measured while the phone was sideways (a video, a game), the
        // copy came out landscape and glass behind the settings covered only the top half.
        return minOf(metrics.widthPixels, metrics.heightPixels) to maxOf(metrics.widthPixels, metrics.heightPixels)
    }

    fun close() {
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        scope.cancel()
    }
}
