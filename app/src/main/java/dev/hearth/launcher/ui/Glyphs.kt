package dev.hearth.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small line icons for the control center, drawn in code (the core icon set has none of these). */
enum class Glyph {
    Sun, AutoSun, Speaker, Torch, Moon, Vibrate, Rotate,
    Wifi, Bluetooth, Airplane, Cellular, Location,
    Camera, Alarm, Calculator,
    Play, Pause, Next, Previous,
    Spark, Bell, Tiles,
    Screenshot, Power, Battery, Hotspot, Nfc, Cast, Contrast, Lock, Headphones,
}

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) { drawGlyph(glyph, tint) }
}

private fun polygon(w: Float, h: Float, vararg points: Pair<Float, Float>): Path = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) }
    close()
}

/** Four-pointed sparkle with soft, concave sides. */
private fun sparkPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h / 2
    val pinch = 0.12f
    return Path().apply {
        moveTo(cx, 0f)
        cubicTo(cx + w * pinch, cy - h * pinch, cx + w * pinch, cy - h * pinch, w, cy)
        cubicTo(cx + w * pinch, cy + h * pinch, cx + w * pinch, cy + h * pinch, cx, h)
        cubicTo(cx - w * pinch, cy + h * pinch, cx - w * pinch, cy + h * pinch, 0f, cy)
        cubicTo(cx - w * pinch, cy - h * pinch, cx - w * pinch, cy - h * pinch, cx, 0f)
        close()
    }
}

fun DrawScope.drawGlyph(glyph: Glyph, c: Color) {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    val sw = size.minDimension * 0.09f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) = drawArc(
        color = c,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset((cx - r) * w, (cy - r) * h),
        size = Size(2 * r * w, 2 * r * h),
        style = stroke,
    )
    fun rays(count: Int, inner: Float, outer: Float) {
        for (i in 0 until count) {
            val a = (i * 2 * PI / count).toFloat()
            val d = Offset(cos(a), sin(a))
            drawLine(c, center + d * (inner * w), center + d * (outer * w), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    when (glyph) {
        Glyph.Sun -> {
            drawCircle(c, radius = w * 0.17f, center = center)
            rays(8, 0.29f, 0.42f)
        }
        Glyph.AutoSun -> {
            drawCircle(c, radius = w * 0.2f, center = center, style = stroke)
            drawArc(c, 90f, 180f, true, topLeft = p(0.3f, 0.3f), size = Size(w * 0.4f, h * 0.4f))
            rays(8, 0.32f, 0.43f)
        }
        Glyph.Speaker -> {
            drawPath(polygon(w, h, 0.12f to 0.38f, 0.3f to 0.38f, 0.5f to 0.2f, 0.5f to 0.8f, 0.3f to 0.62f, 0.12f to 0.62f), c)
            arc(0.5f, 0.5f, 0.17f, -45f, 90f)
            arc(0.5f, 0.5f, 0.33f, -45f, 90f)
        }
        Glyph.Torch -> {
            drawPath(polygon(w, h, 0.26f to 0.1f, 0.74f to 0.1f, 0.63f to 0.4f, 0.37f to 0.4f), c)
            drawRoundRect(c, topLeft = p(0.38f, 0.44f), size = Size(w * 0.24f, h * 0.46f), cornerRadius = CornerRadius(w * 0.06f))
        }
        Glyph.Moon -> {
            val disc = Path().apply { addOval(Rect(p(0.16f, 0.16f), Size(w * 0.68f, h * 0.68f))) }
            val bite = Path().apply { addOval(Rect(p(0.38f, 0.04f), Size(w * 0.58f, h * 0.58f))) }
            val moon = Path().apply { op(disc, bite, PathOperation.Difference) }
            drawPath(moon, c)
        }
        Glyph.Vibrate -> {
            drawRoundRect(c, topLeft = p(0.33f, 0.16f), size = Size(w * 0.34f, h * 0.68f), cornerRadius = CornerRadius(w * 0.07f), style = stroke)
            line(0.17f, 0.36f, 0.17f, 0.64f)
            line(0.83f, 0.36f, 0.83f, 0.64f)
        }
        Glyph.Rotate -> {
            arc(0.5f, 0.5f, 0.3f, -50f, 290f)
            drawPath(polygon(w, h, 0.83f to 0.2f, 0.86f to 0.46f, 0.62f to 0.36f), c)
        }
        Glyph.Wifi -> {
            arc(0.5f, 0.8f, 0.2f, -135f, 90f)
            arc(0.5f, 0.8f, 0.42f, -135f, 90f)
            arc(0.5f, 0.8f, 0.64f, -135f, 90f)
            drawCircle(c, radius = w * 0.06f, center = p(0.5f, 0.8f))
        }
        Glyph.Bluetooth -> {
            val path = Path().apply {
                moveTo(0.3f * w, 0.32f * h)
                lineTo(0.68f * w, 0.66f * h)
                lineTo(0.5f * w, 0.84f * h)
                lineTo(0.5f * w, 0.16f * h)
                lineTo(0.68f * w, 0.34f * h)
                lineTo(0.3f * w, 0.68f * h)
            }
            drawPath(path, c, style = stroke)
        }
        Glyph.Airplane -> drawPath(
            polygon(
                w, h,
                0.5f to 0.08f, 0.56f to 0.18f, 0.56f to 0.4f, 0.92f to 0.6f, 0.92f to 0.68f,
                0.56f to 0.57f, 0.56f to 0.78f, 0.67f to 0.86f, 0.67f to 0.92f, 0.5f to 0.88f,
                0.33f to 0.92f, 0.33f to 0.86f, 0.44f to 0.78f, 0.44f to 0.57f, 0.08f to 0.68f,
                0.08f to 0.6f, 0.44f to 0.4f, 0.44f to 0.18f,
            ),
            c,
        )
        Glyph.Cellular -> {
            listOf(0.2f, 0.36f, 0.52f, 0.68f).forEachIndexed { i, height ->
                val x = 0.16f + i * 0.18f
                drawRoundRect(
                    c,
                    topLeft = p(x, 0.84f - height),
                    size = Size(w * 0.12f, h * height),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
            }
        }
        Glyph.Location -> {
            drawCircle(c, radius = w * 0.2f, center = p(0.5f, 0.4f), style = stroke)
            val tip = Path().apply {
                moveTo(0.31f * w, 0.5f * h)
                lineTo(0.5f * w, 0.88f * h)
                lineTo(0.69f * w, 0.5f * h)
            }
            drawPath(tip, c, style = stroke)
        }
        Glyph.Camera -> {
            drawRoundRect(c, topLeft = p(0.12f, 0.3f), size = Size(w * 0.76f, h * 0.5f), cornerRadius = CornerRadius(w * 0.1f), style = stroke)
            drawRoundRect(c, topLeft = p(0.36f, 0.2f), size = Size(w * 0.28f, h * 0.12f), cornerRadius = CornerRadius(w * 0.04f))
            drawCircle(c, radius = w * 0.13f, center = p(0.5f, 0.55f), style = stroke)
        }
        Glyph.Alarm -> {
            drawCircle(c, radius = w * 0.3f, center = p(0.5f, 0.55f), style = stroke)
            line(0.5f, 0.55f, 0.5f, 0.38f)
            line(0.5f, 0.55f, 0.62f, 0.62f)
            line(0.14f, 0.26f, 0.26f, 0.15f)
            line(0.86f, 0.26f, 0.74f, 0.15f)
        }
        Glyph.Calculator -> {
            drawRoundRect(c, topLeft = p(0.22f, 0.12f), size = Size(w * 0.56f, h * 0.76f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
            line(0.35f, 0.29f, 0.65f, 0.29f)
            for (row in 0 until 3) for (col in 0 until 3) {
                drawCircle(c, radius = w * 0.035f, center = p(0.36f + col * 0.14f, 0.48f + row * 0.13f))
            }
        }
        Glyph.Play -> drawPath(polygon(w, h, 0.3f to 0.2f, 0.8f to 0.5f, 0.3f to 0.8f), c)
        Glyph.Pause -> {
            drawRoundRect(c, topLeft = p(0.26f, 0.2f), size = Size(w * 0.16f, h * 0.6f), cornerRadius = CornerRadius(w * 0.04f))
            drawRoundRect(c, topLeft = p(0.58f, 0.2f), size = Size(w * 0.16f, h * 0.6f), cornerRadius = CornerRadius(w * 0.04f))
        }
        Glyph.Next -> {
            drawPath(polygon(w, h, 0.2f to 0.22f, 0.62f to 0.5f, 0.2f to 0.78f), c)
            drawRoundRect(c, topLeft = p(0.66f, 0.22f), size = Size(w * 0.12f, h * 0.56f), cornerRadius = CornerRadius(w * 0.03f))
        }
        Glyph.Previous -> {
            drawPath(polygon(w, h, 0.8f to 0.22f, 0.38f to 0.5f, 0.8f to 0.78f), c)
            drawRoundRect(c, topLeft = p(0.22f, 0.22f), size = Size(w * 0.12f, h * 0.56f), cornerRadius = CornerRadius(w * 0.03f))
        }
        Glyph.Bell -> {
            val bell = Path().apply {
                moveTo(0.2f * w, 0.72f * h)
                lineTo(0.8f * w, 0.72f * h)
                lineTo(0.71f * w, 0.6f * h)
                lineTo(0.71f * w, 0.42f * h)
                cubicTo(0.71f * w, 0.18f * h, 0.29f * w, 0.18f * h, 0.29f * w, 0.42f * h)
                lineTo(0.29f * w, 0.6f * h)
                close()
            }
            drawPath(bell, c, style = stroke)
            drawCircle(c, radius = w * 0.065f, center = p(0.5f, 0.84f))
        }
        Glyph.Tiles -> {
            for (row in 0 until 2) for (col in 0 until 2) {
                drawRoundRect(
                    c,
                    topLeft = p(0.16f + col * 0.38f, 0.16f + row * 0.38f),
                    size = Size(w * 0.3f, h * 0.3f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
            }
        }
        Glyph.Screenshot -> {
            // Four corner brackets
            line(0.16f, 0.32f, 0.16f, 0.16f); line(0.16f, 0.16f, 0.32f, 0.16f)
            line(0.68f, 0.16f, 0.84f, 0.16f); line(0.84f, 0.16f, 0.84f, 0.32f)
            line(0.84f, 0.68f, 0.84f, 0.84f); line(0.84f, 0.84f, 0.68f, 0.84f)
            line(0.32f, 0.84f, 0.16f, 0.84f); line(0.16f, 0.84f, 0.16f, 0.68f)
            drawCircle(c, radius = w * 0.1f, center = center)
        }
        Glyph.Power -> {
            arc(0.5f, 0.55f, 0.3f, -60f, 300f)
            line(0.5f, 0.14f, 0.5f, 0.5f)
        }
        Glyph.Battery -> {
            drawRoundRect(c, topLeft = p(0.3f, 0.18f), size = Size(w * 0.4f, h * 0.68f), cornerRadius = CornerRadius(w * 0.07f), style = stroke)
            drawRoundRect(c, topLeft = p(0.42f, 0.1f), size = Size(w * 0.16f, h * 0.07f), cornerRadius = CornerRadius(w * 0.02f))
            drawRoundRect(c, topLeft = p(0.37f, 0.5f), size = Size(w * 0.26f, h * 0.29f), cornerRadius = CornerRadius(w * 0.03f))
        }
        Glyph.Hotspot -> {
            drawCircle(c, radius = w * 0.07f, center = center)
            arc(0.5f, 0.5f, 0.22f, -140f, 100f)
            arc(0.5f, 0.5f, 0.22f, 40f, 100f)
            arc(0.5f, 0.5f, 0.38f, -140f, 100f)
            arc(0.5f, 0.5f, 0.38f, 40f, 100f)
        }
        Glyph.Nfc -> {
            drawRoundRect(c, topLeft = p(0.16f, 0.16f), size = Size(w * 0.68f, h * 0.68f), cornerRadius = CornerRadius(w * 0.14f), style = stroke)
            line(0.36f, 0.66f, 0.36f, 0.34f)
            line(0.36f, 0.34f, 0.64f, 0.66f)
            line(0.64f, 0.66f, 0.64f, 0.34f)
        }
        Glyph.Cast -> {
            val frame = Path().apply {
                moveTo(0.14f * w, 0.36f * h)
                lineTo(0.14f * w, 0.22f * h)
                lineTo(0.86f * w, 0.22f * h)
                lineTo(0.86f * w, 0.78f * h)
                lineTo(0.56f * w, 0.78f * h)
            }
            drawPath(frame, c, style = stroke)
            arc(0.14f, 0.82f, 0.14f, -90f, 90f)
            arc(0.14f, 0.82f, 0.28f, -90f, 90f)
            drawCircle(c, radius = w * 0.045f, center = p(0.16f, 0.8f))
        }
        Glyph.Contrast -> {
            drawCircle(c, radius = w * 0.32f, center = center, style = stroke)
            drawArc(c, 90f, 180f, true, topLeft = p(0.18f, 0.18f), size = Size(w * 0.64f, h * 0.64f))
        }
        Glyph.Lock -> {
            arc(0.5f, 0.42f, 0.17f, 180f, 180f)
            line(0.33f, 0.42f, 0.33f, 0.48f)
            line(0.67f, 0.42f, 0.67f, 0.48f)
            drawRoundRect(c, topLeft = p(0.24f, 0.46f), size = Size(w * 0.52f, h * 0.38f), cornerRadius = CornerRadius(w * 0.08f))
        }
        Glyph.Headphones -> {
            arc(0.5f, 0.55f, 0.32f, 180f, 180f)
            drawRoundRect(c, topLeft = p(0.14f, 0.52f), size = Size(w * 0.16f, h * 0.3f), cornerRadius = CornerRadius(w * 0.06f))
            drawRoundRect(c, topLeft = p(0.7f, 0.52f), size = Size(w * 0.16f, h * 0.3f), cornerRadius = CornerRadius(w * 0.06f))
        }
        Glyph.Spark -> {
            drawPath(sparkPath(Size(w * 0.78f, h * 0.78f)).also { it.translate(p(0f, 0.22f)) }, c)
            drawPath(sparkPath(Size(w * 0.34f, h * 0.34f)).also { it.translate(p(0.66f, 0f)) }, c.copy(alpha = c.alpha * 0.8f))
        }
    }
}

/**
 * A sparkle in Claude's terracotta that turns and breathes for a few seconds when it
 * appears, then rests (a never-ending animation kept the whole screen redrawing).
 */
@Composable
fun ClaudeSpark(color: Color, modifier: Modifier = Modifier) {
    val animate = LocalSettings.current.animations
    val spin = remember { Animatable(0f) }
    val breath = remember { Animatable(1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { spin.animateTo(360f, tween(5200, easing = FastOutSlowInEasing)) }
        repeat(3) {
            breath.animateTo(0.78f, tween(550, easing = FastOutSlowInEasing))
            breath.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    val rotation = spin.value
    val pulse = breath.value
    Canvas(modifier) {
        val big = Size(size.width * 0.8f, size.height * 0.8f)
        translate(left = 0f, top = size.height * 0.2f) {
            rotate(rotation, pivot = Offset(big.width / 2, big.height / 2)) {
                scale(pulse, pivot = Offset(big.width / 2, big.height / 2)) {
                    drawPath(sparkPath(big), color)
                }
            }
        }
        val small = Size(size.width * 0.32f, size.height * 0.32f)
        translate(left = size.width * 0.68f, top = 0f) {
            scale(1.78f - pulse, pivot = Offset(small.width / 2, small.height / 2)) {
                drawPath(sparkPath(small), color.copy(alpha = 0.85f))
            }
        }
    }
}
