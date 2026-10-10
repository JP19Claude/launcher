package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 19, the Z-Angriff: the Z appears like Zygarde's Core Enforcer – beams of green energy
 * draw a giant Z across the screen stroke by stroke, sparks flying off their heads; the Z flares
 * white-hot, and bursts in rings of green light.
 */

/** A spark flung off a beam's head: which stroke, where on it, which way, how fast, how big, how long. */
internal class StrikeSpark(val stroke: Int, val at: Float, val angle: Float, val speed: Float, val size: Float, val life: Float)

/** The same sparks every time. */
internal val StrikeSparks: List<StrikeSpark> = Random(2019).let { r ->
    List(90) {
        StrikeSpark(r.nextInt(3), r.nextFloat(), (r.nextFloat() * 2f * PI).toFloat(), 0.04f + r.nextFloat() * 0.12f, 1.5f + r.nextFloat() * 3.5f, 0.12f + r.nextFloat() * 0.18f)
    }
}

/** When each of the Z's three strokes is drawn (fractions of the whole). */
private val StrokeTimes = listOf(0f to 0.16f, 0.16f to 0.38f, 0.38f to 0.54f)

/**
 * The Z-Angriff at [p] (0..1) over this whole scope: the dark coming in, the three beams of the
 * Z drawn one after the other with sparks, the flare, the rings of the burst, the dark going.
 */
internal fun DrawScope.drawZenithStrike(p: Float) {
    val w = size.width
    val h = size.height
    val unit = minOf(w, h)
    val corners = listOf(Offset(w * 0.16f, h * 0.31f), Offset(w * 0.88f, h * 0.31f), Offset(w * 0.12f, h * 0.69f), Offset(w * 0.84f, h * 0.69f))
    fun along(stroke: Int, f: Float): Offset = corners[stroke] + (corners[stroke + 1] - corners[stroke]) * f
    // The dark coming in, and going.
    val dark = (p / 0.08f).coerceIn(0f, 1f) * (1f - ((p - 0.82f) / 0.18f).coerceIn(0f, 1f))
    drawRect(Color.Black.copy(alpha = 0.55f * dark))
    // The flare after the last stroke, and the Z fading as it bursts.
    val flare = if (p in 0.54f..0.7f) sin(((p - 0.54f) / 0.16f) * PI).toFloat() else 0f
    val fade = 1f - ((p - 0.68f) / 0.27f).coerceIn(0f, 1f)
    if (flare > 0f) drawRect(ZenithGreen.copy(alpha = 0.16f * flare))
    val swell = 1f + 1.2f * flare
    for (i in 0 until 3) {
        val (t0, t1) = StrokeTimes[i]
        val f = ((p - t0) / (t1 - t0)).coerceIn(0f, 1f)
        if (f <= 0f) continue
        val a = corners[i]
        val b = along(i, f)
        // The beam: a wide green haze, the beam, a white-hot core.
        drawLine(ZenithGreen.copy(alpha = 0.18f * fade), a, b, strokeWidth = unit * 0.09f * swell, cap = StrokeCap.Round)
        drawLine(Color(0xFF46EB6E).copy(alpha = 0.55f * fade), a, b, strokeWidth = unit * 0.035f * swell, cap = StrokeCap.Round)
        drawLine(Color(0xFFE6FFEA).copy(alpha = 0.95f * fade), a, b, strokeWidth = unit * 0.012f * swell, cap = StrokeCap.Round)
        // The head, while this stroke is being drawn.
        if (f < 1f) {
            val r = unit * 0.11f
            drawCircle(Brush.radialGradient(listOf(Color.White, ZenithGreen.copy(alpha = 0.7f), Color.Transparent), center = b, radius = r), r, b)
        }
    }
    // Sparks off the heads.
    StrikeSparks.forEach { s ->
        val (t0, t1) = StrokeTimes[s.stroke]
        val born = t0 + s.at * (t1 - t0)
        val age = p - born
        if (age < 0f || age > s.life) return@forEach
        val k = age / s.life
        val from = along(s.stroke, s.at)
        val at = from + Offset(cos(s.angle), sin(s.angle)) * (s.speed * unit * k)
        drawCircle(Color(0xFFB8FFC8).copy(alpha = (1f - k) * 0.9f), s.size * (1f - 0.5f * k) * unit / 400f, at)
    }
    // The burst: rings of green light from the Z's middle.
    val middle = Offset(w * 0.5f, h * 0.5f)
    for (ring in 0 until 3) {
        val start = 0.6f + ring * 0.07f
        val q = ((p - start) / 0.35f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        drawCircle(
            ZenithGreen.copy(alpha = (1f - q) * 0.6f),
            radius = q * maxOf(w, h) * 0.8f,
            center = middle,
            style = Stroke(unit * 0.02f * (1f - q) + 1f),
        )
    }
}

/** Places a popup over the whole window. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

/**
 * The Z-Angriff over the whole screen, once: a knock as it starts, a tick for each stroke, a
 * heavy knock as it bursts – then [onDone].
 */
@Composable
fun ZenithStrikeOverlay(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        launch {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            for (at in listOf(290L, 400L, 290L)) {
                delay(at)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(250)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        p.animateTo(1f, tween(1800, easing = LinearEasing))
        onDone()
    }
    Popup(
        popupPositionProvider = WholeWindow,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawZenithStrike(p.value) }
    }
}

/** The Z-Angriff filling a window of its own (over everything, from Glimmer), once – then [onDone]. */
@Composable
fun ZenithStrikeScreen(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1800, easing = LinearEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) { drawZenithStrike(p.value) }
}
