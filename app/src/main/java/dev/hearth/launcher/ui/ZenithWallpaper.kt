package dev.hearth.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.WallpaperBackdrop
import dev.hearth.launcher.data.blurred

/*
 * ZENITH 19 as a product of its own: its own background on the home screen, and the Z lighting
 * up once after the phone starts.
 */

/** ZENITH's background, made once: the picture for the home screen and its blurred copies for the metal over it. */
internal class ZenithWall(val image: ImageBitmap, val backdrop: WallpaperBackdrop)

/**
 * ZENITH's background: near-black with a breath of green, the grain of brushed metal, the
 * logo's Z big and low in the middle with its green light burning (veiled, so apps stay clear),
 * green light pooling along the bottom and a glint high on the right.
 */
internal fun DrawScope.drawZenithWallpaper() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(0f to Color(0xFF050607), 0.5f to Color(0xFF0A0F0D), 1f to Color(0xFF07170E)))
    // The grain of brushed metal, faint, over everything.
    val unit = w * 0.6f
    ZenithMetal.forEach { m ->
        if (m.len <= 0f) return@forEach
        val at = Offset(m.x * w, m.y * h)
        val color = if (m.light) Color.White.copy(alpha = 0.012f + m.alpha * 0.025f) else Color.Black.copy(alpha = 0.08f + m.alpha * 0.12f)
        drawLine(color, at, at + Offset(kotlin.math.cos(m.angle) * m.len * unit * 2f, kotlin.math.sin(m.angle) * m.len * unit), strokeWidth = m.width * unit)
    }
    // The Z, big and low.
    val zw = w * 0.88f
    drawZenithZ((w - zw) / 2f, h * 0.42f, zw, light = 1f)
    drawRect(Color.Black.copy(alpha = 0.3f))
    // Green light pooling along the bottom, and a glint high on the right.
    val pool = Offset(w * 0.55f, h)
    scale(1f, 0.4f, pivot = pool) {
        drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.35f), Color.Transparent), center = pool, radius = w * 0.9f), w * 0.9f, pool)
    }
    val glint = Offset(w * 0.86f, h * 0.07f)
    drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.16f), Color.Transparent), center = glint, radius = w * 0.45f), w * 0.45f, glint)
}

/** Draws [block] into a new picture of [width] × [height]. */
private fun renderPicture(width: Int, height: Int, block: DrawScope.() -> Unit): Bitmap {
    val image = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) { block() }
    return image.asAndroidBitmap()
}

/** The screen's real size, upright. */
@Suppress("DEPRECATION")
private fun realScreenSize(context: Context): Pair<Int, Int> {
    val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
    val metrics = android.util.DisplayMetrics()
    display.getRealMetrics(metrics)
    return minOf(metrics.widthPixels, metrics.heightPixels) to maxOf(metrics.widthPixels, metrics.heightPixels)
}

/** ZENITH's background for this screen: drawn at half size for the home screen, a quarter for the blur. */
internal fun makeZenithWall(context: Context): ZenithWall {
    val (sw, sh) = realScreenSize(context)
    val full = renderPicture(sw / 2, sh / 2) { drawZenithWallpaper() }
    val small = renderPicture(sw / 4, sh / 4) { drawZenithWallpaper() }
    val soft = blurred(small, radius = 9)
    val strong = blurred(small, radius = 10, downscale = 3)
    return ZenithWall(
        full.asImageBitmap(),
        WallpaperBackdrop(
            image = small.asImageBitmap(),
            soft = soft.asImageBitmap(),
            strong = strong.asImageBitmap(),
            screenWidth = sw,
            screenHeight = sh,
        ),
    )
}

/** Whether the phone has started since ZENITH last looked (then the Z lights up once). */
internal object ZenithBoot {
    fun isNewStart(context: Context): Boolean {
        val prefs = context.getSharedPreferences("zenith_boot", Context.MODE_PRIVATE)
        // When the phone started, to the minute; the same start always gives about the same time.
        val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val last = prefs.getLong("bootAt", 0L)
        if (kotlin.math.abs(bootAt - last) < 60_000L) return false
        prefs.edit().putLong("bootAt", bootAt).apply()
        return true
    }
}

/**
 * The ZENITH start, once after the phone starts: black; the metal Z comes out of the dark, its
 * green edges light up one after the other, "ZENITH" under it – then it fades into the home
 * screen. A tap skips it.
 */
@Composable
internal fun ZenithBootIntro(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2800, easing = LinearEasing))
        onDone()
    }
    BackHandler(onBack = onDone)
    val p = t.value
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f) }
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        ZenithLogo(
            Modifier.graphicsLayer {
                alpha = (p / 0.15f).coerceIn(0f, 1f)
                val k = 0.92f + 0.08f * (p / 0.5f).coerceIn(0f, 1f)
                scaleX = k
                scaleY = k
            },
            size = 260.dp,
            caption = "ZENITH",
            interactive = false,
            light = ((p - 0.15f) / 0.4f).coerceIn(0f, 1f),
        )
    }
}
