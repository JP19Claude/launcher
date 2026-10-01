package dev.hearth.launcher.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The icon's average color, for a card that looks like the app's splash screen. */
private fun iconColor(app: AppInfo): Color = runCatching {
    val bitmap = app.icon.asAndroidBitmap()
    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    Color(Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)).copy(alpha = 1f)
}.getOrDefault(Color(0xFF2A2A2E))

/**
 * Closing an app, the way HarmonyOS does it: the app shrinks to a card in its own colors,
 * rises towards the camera while it flattens into a capsule, turns dark, and flows into
 * Glimmer through a liquid neck (Android 12+); the island takes it in with a springy squash.
 * Draws only, never takes touches.
 */
@Composable
fun GlimmerFlyIn(app: AppInfo, island: DpSize?, onPulse: () -> Unit, onDone: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val travel = remember(app) { Animatable(0f) }
    val absorb = remember(app) { Animatable(0f) }
    val pulse by rememberUpdatedState(onPulse)
    val done by rememberUpdatedState(onDone)
    val color = remember(app) { iconColor(app) }
    val icon = app.icon

    // Where Glimmer sits: centered on the camera cutout at the top, or the top middle.
    val camera = remember(view) {
        val rects: List<Rect> = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        rects.minByOrNull { it.top }?.takeIf { it.top < view.height / 4 }
    }
    // Aim at the island as it is now; without one, into the camera itself.
    val islandShown = island != null && island.width.value > 0f
    val targetW = with(density) {
        if (islandShown) island!!.width.toPx() else ISLAND_HEIGHT_DP.dp.toPx()
    }
    val targetH = with(density) { ISLAND_HEIGHT_DP.dp.toPx() }

    // The liquid neck: blur the dark shapes, then keep only what's dense enough.
    val goo = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = with(density) { 9.dp.toPx() }
                val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
                val threshold = ColorMatrixColorFilter(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 22f, -9f * 255f,
                    ),
                )
                RenderEffect.createColorFilterEffect(threshold, blur).asComposeRenderEffect()
            }.getOrNull()
        } else {
            null
        }
    }

    LaunchedEffect(app) {
        launch {
            var pulsed = false
            snapshotFlow { travel.value }.collect { t ->
                if (!pulsed && t > 0.84f) {
                    pulsed = true
                    // The island swallows it: squashed wide, then springs back.
                    pulse()
                    launch {
                        absorb.animateTo(1f, tween(90))
                        absorb.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 420f))
                    }
                }
            }
        }
        travel.animateTo(1f, spring(dampingRatio = 1f, stiffness = 150f, visibilityThreshold = 0.002f))
        delay(220)
        done()
    }

    Box(Modifier.fillMaxSize()) {
        val w = view.width.toFloat().coerceAtLeast(1f)
        val h = view.height.toFloat().coerceAtLeast(1f)
        val camX = camera?.exactCenterX() ?: (w / 2f)
        val camY = camera?.exactCenterY() ?: with(density) { 15.dp.toPx() }

        // One place for the card's shape, read while drawing (no recomposition per frame).
        fun card(t: Float): Pair<Offset, Size> {
            val startW = w * 0.84f
            val startH = h * 0.74f
            // Height collapses faster than width: the card flattens into a capsule on the way.
            val fw = 1f - (1f - t).pow(1.9f)
            val fh = 1f - (1f - t).pow(2.9f)
            val cw = lerp(startW, targetW, fw)
            val ch = lerp(startH, targetH, fh)
            val cx = lerp(w / 2f, camX, t)
            // Rises a little ahead of its shrinking: a soft arc up into the island.
            val cy = lerp(h * 0.52f, camY, 1f - (1f - t).pow(1.5f))
            return Offset(cx - cw / 2f, cy - ch / 2f) to Size(cw, ch)
        }

        // The dark body and the island, melting into each other.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = goo
                    alpha = if (islandShown) 1f else 1f - smoothstep(0.88f, 1f, travel.value)
                },
        ) {
            val t = travel.value.coerceIn(0f, 1f)
            if (t < 0.32f) return@Canvas
            val (pos, size) = card(t)
            val radius = min(size.width, size.height) / 2f
            drawRoundRect(Color.Black, pos, size, CornerRadius(min(lerp(36.dp.toPx(), radius, t), radius)))
            // The island, swelling as it takes the card in.
            val a = absorb.value
            val pw = targetW * (1f + 0.18f * a)
            val ph = targetH * (1f - 0.10f * a)
            drawRoundRect(
                Color.Black,
                Offset(camX - pw / 2f, camY - ph / 2f),
                Size(pw, ph),
                CornerRadius(ph / 2f),
                alpha = smoothstep(0.32f, 0.5f, t),
            )
        }

        // The app itself: its colors and icon, fading into the dark body as it nears the top.
        Canvas(Modifier.fillMaxSize()) {
            val t = travel.value.coerceIn(0f, 1f)
            val appear = smoothstep(0f, 0.06f, t)
            val body = (1f - smoothstep(0.42f, 0.76f, t)) * appear
            if (body <= 0.001f) return@Canvas
            val (pos, size) = card(t)
            val corner = CornerRadius(min(lerp(36.dp.toPx(), min(size.width, size.height) / 2f, t), min(size.width, size.height) / 2f))
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to lerpColor(color, Color.White, 0.18f),
                    1f to lerpColor(color, Color.Black, 0.35f),
                    startY = pos.y,
                    endY = pos.y + size.height,
                ),
                topLeft = pos,
                size = size,
                cornerRadius = corner,
                alpha = body,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.22f * body),
                pos,
                size,
                corner,
                style = Stroke(1.dp.toPx()),
            )
            val iconAlpha = (1f - smoothstep(0.3f, 0.58f, t)) * appear
            if (iconAlpha > 0.001f) {
                val iconSize = max(lerp(84.dp.toPx(), 18.dp.toPx(), t), 1f)
                val c = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                drawImage(
                    image = icon,
                    dstOffset = IntOffset((c.x - iconSize / 2f).roundToInt(), (c.y - iconSize / 2f).roundToInt()),
                    dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
                    alpha = iconAlpha,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = lerp(a.red, b.red, t),
    green = lerp(a.green, b.green, t),
    blue = lerp(a.blue, b.blue, t),
    alpha = 1f,
)
