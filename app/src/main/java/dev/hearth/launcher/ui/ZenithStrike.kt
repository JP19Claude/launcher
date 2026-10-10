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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 19, the Z-Angriff – like Zygarde's Core Enforcer: the dark falls and a floor of
 * hexagons lights up; a hexagonal core charges at the top, drawing in motes of light; it fires
 * a beam down at the floor, and the beam burns a giant Z into it, stroke by stroke, sparks
 * flying; the Z flares white-hot and erupts in flames of green light and hexagon shards, a
 * shockwave runs over the floor, and it all fades.
 */

/** The Z's three strokes on the floor (in the Z's own 0..1 box): top bar, diagonal, bottom bar. */
private val StrikeTrace = listOf(Offset(0.13f, 0.1f), Offset(0.86f, 0.1f), Offset(0.15f, 0.883f), Offset(0.92f, 0.883f))

/**
 * The ZENITH Z's three bars on the floor (in the Z's own 0..1 box, as its logo): for each, where
 * its one side starts and ends and where its other side starts and ends – the beam burns it
 * from start to end.
 */
private val StrikeBars = listOf(
    listOf(Offset(0.2f, 0f), Offset(0.91f, 0f), Offset(0.08f, 0.199f), Offset(0.766f, 0.199f)),
    listOf(Offset(0.535f, 0.199f), Offset(0.161f, 0.766f), Offset(0.766f, 0.199f), Offset(0.355f, 0.766f)),
    listOf(Offset(0.161f, 0.766f), Offset(1f, 0.766f), Offset(0.007f, 1f), Offset(0.823f, 1f)),
)

/** When each stroke is burnt in (fractions of the whole). */
private val StrikeStrokes = listOf(0.18f to 0.31f, 0.31f to 0.47f, 0.47f to 0.6f)

/** A spark off the beam's tip: when, which way, how fast, how big, how long. */
internal class StrikeSpark(val born: Float, val angle: Float, val speed: Float, val size: Float, val life: Float)

internal val StrikeSparks: List<StrikeSpark> = Random(2019).let { r ->
    List(110) {
        StrikeSpark(
            0.18f + r.nextFloat() * 0.42f,
            (-PI * (0.1 + 0.8 * r.nextFloat())).toFloat(),
            0.05f + r.nextFloat() * 0.16f,
            1f + r.nextFloat() * 2.6f,
            0.08f + r.nextFloat() * 0.12f,
        )
    }
}

/** A hexagon shard flung up when the Z erupts: where on the Z, drift, how high, size, spin, delay. */
internal class StrikeShard(val at: Float, val drift: Float, val rise: Float, val size: Float, val spin: Float, val delay: Float)

internal val StrikeShards: List<StrikeShard> = Random(19).let { r ->
    List(26) {
        StrikeShard(r.nextFloat(), (r.nextFloat() - 0.5f) * 0.1f, 0.25f + r.nextFloat() * 0.45f, 0.012f + r.nextFloat() * 0.022f, (r.nextFloat() - 0.5f) * 6f, r.nextFloat() * 0.08f)
    }
}

/** A mote of light drawn into the charging core. */
internal class StrikeMote(val angle: Float, val distance: Float, val delay: Float, val size: Float)

internal val StrikeMotes: List<StrikeMote> = Random(7).let { r ->
    List(28) { StrikeMote(r.nextFloat() * 2f * PI.toFloat(), 0.18f + r.nextFloat() * 0.25f, r.nextFloat() * 0.1f, 1f + r.nextFloat() * 2f) }
}

private fun span(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

private val StrikeWhite = Color(0xFFEBFFF0)
private val StrikeBright = Color(0xFF46EB78)
private val StrikeMint = Color(0xFFA0FFBE)

/** A straight burning line from [a] to [b], [wa] wide at [a] and [wb] at [b] (farther is thinner). */
private fun DrawScope.burn(path: Path, a: Offset, b: Offset, wa: Float, wb: Float, color: Color) {
    val d = b - a
    val len = d.getDistance()
    if (len < 0.5f) return
    val n = Offset(-d.y / len, d.x / len)
    val back = d / len * (wa * 0.5f)
    val fore = d / len * (wb * 0.5f)
    path.reset()
    path.moveTo(a.x - back.x + n.x * wa / 2f, a.y - back.y + n.y * wa / 2f)
    path.lineTo(b.x + fore.x + n.x * wb / 2f, b.y + fore.y + n.y * wb / 2f)
    path.lineTo(b.x + fore.x - n.x * wb / 2f, b.y + fore.y - n.y * wb / 2f)
    path.lineTo(a.x - back.x - n.x * wa / 2f, a.y - back.y - n.y * wa / 2f)
    path.close()
    drawPath(path, color, blendMode = BlendMode.Plus)
}

/**
 * The Z-Angriff at [p] (0..1) over this whole scope: the dark and the floor coming in, the core
 * charging, the beam burning the Z's three strokes, the flare, the eruption, the dark going.
 */
internal fun DrawScope.drawZenithStrike(p: Float, tint: Color = ZenithGreen) {
    // The whole palette from one color: its light, brighter, mint-pale, white-hot.
    val green = tint
    val bright = lerp(tint, Color.White, 0.25f)
    val mint = lerp(tint, Color.White, 0.55f)
    val white = lerp(tint, Color.White, 0.9f)
    val spark = lerp(tint, Color.White, 0.75f)
    val hud = lerp(tint, Color.White, 0.45f)
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val unit = minOf(w, h)
    val plus = BlendMode.Plus
    val out = 1f - span(p, 0.84f, 1f)
    drawRect(Color(0xFF020604).copy(alpha = 0.78f * span(p, 0f, 0.08f) * out))

    // The floor, in perspective: u across (-1..1), v into the distance (0 near, 1 far).
    val horizon = h * 0.36f
    fun depth(v: Float): Float = 1f + v.coerceAtLeast(-0.6f) * 1.25f
    fun ground(u: Float, v: Float): Offset {
        val z = depth(v)
        return Offset(w / 2f + u * w * 0.62f / z, horizon + h * 0.6f / z)
    }
    fun zv(y: Float): Float = 0.92f - y * 0.9f
    fun onZ(pt: Offset): Offset = ground((pt.x - 0.5f) * 1.95f, zv(pt.y))
    fun zDepth(pt: Offset): Float = depth(zv(pt.y))
    fun trace(stroke: Int, f: Float): Offset = StrikeTrace[stroke] + (StrikeTrace[stroke + 1] - StrikeTrace[stroke]) * f
    fun alongZ(t: Float): Offset {
        val tt = (t * 3f).coerceIn(0f, 3f)
        val i = minOf(2, tt.toInt())
        return trace(i, tt - i)
    }
    val path = Path()
    val third = PI.toFloat() / 3f

    // The floor of hexagons, like the ground under Zygarde.
    val floor = span(p, 0.04f, 0.2f) * out * (1f - 0.5f * span(p, 0.7f, 0.84f))
    if (floor > 0f) {
        val r = 0.16f
        for (row in 0 until 9) {
            for (col in -6..6) {
                val cu = col * r * 1.732f + if (row % 2 == 1) r * 0.866f else 0f
                val cv = row * r * 1.5f - 0.05f
                val fade = (1f - cv / 1.3f).coerceIn(0f, 1f) * (1f - abs(cu) / 1.6f).coerceIn(0f, 1f)
                if (fade <= 0f) continue
                path.reset()
                for (k in 0 until 6) {
                    val a = third * k - PI.toFloat() / 2f
                    val q = ground(cu + cos(a) * r * 0.97f, cv + sin(a) * r * 0.97f)
                    if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                path.close()
                drawPath(path, green.copy(alpha = 0.22f * floor * fade), style = Stroke(unit * 0.0025f), blendMode = plus)
            }
        }
    }

    // The core, charging at the top.
    val core = Offset(w / 2f, h * 0.16f)
    val charge = span(p, 0.02f, 0.18f)
    val coreOn = charge * (1f - span(p, 0.62f, 0.72f))
    val firing = p > 0.18f && p < 0.6f
    if (coreOn > 0f) {
        val r = unit * 0.075f * (0.4f + 0.6f * charge) * (if (firing) 1f + 0.06f * sin(p * 120f) else 1f)
        drawCircle(
            Brush.radialGradient(listOf(green.copy(alpha = 0.55f * coreOn), Color.Transparent), center = core, radius = r * 3.2f),
            radius = r * 3.2f,
            center = core,
            blendMode = plus,
        )
        for (n in 0 until 3) {
            val rr = r * (1f + n * 0.45f)
            val turn = p * (if (n % 2 == 1) -9f else 7f) + n
            path.reset()
            for (k in 0 until 6) {
                val a = turn + third * k
                val x = core.x + cos(a) * rr
                val y = core.y + sin(a) * rr
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path,
                hud.copy(alpha = ((0.8f - n * 0.22f) * coreOn).coerceIn(0f, 1f)),
                style = Stroke(unit * 0.004f * (3 - n) / 2f + 1f),
                blendMode = plus,
            )
        }
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = coreOn),
                0.45f to mint.copy(alpha = 0.9f * coreOn),
                1f to Color.Transparent,
                center = core,
                radius = r,
            ),
            radius = r,
            center = core,
            blendMode = plus,
        )
        StrikeMotes.forEach { m ->
            val k = span(p, m.delay, m.delay + 0.12f)
            if (k <= 0f || k >= 1f) return@forEach
            val d = unit * m.distance * (1f - k)
            drawCircle(
                spark.copy(alpha = (1f - k) * 0.9f),
                m.size * unit / 400f * (1f + k),
                core + Offset(cos(m.angle) * d, sin(m.angle) * d),
                blendMode = plus,
            )
        }
    }

    // The ZENITH Z burnt into the floor, bar by bar, as wide and heavy as the logo's: each bar
    // fills from where the beam starts to where it is, a haze round it, a bright rim, and a
    // white-hot front where the beam burns.
    val fade = 1f - span(p, 0.64f, 0.84f)
    val flare = span(p, 0.58f, 0.62f) * (1f - span(p, 0.62f, 0.78f))
    var tip: Offset? = null
    var tipDepth = 1f
    if (fade > 0f) {
        for (i in 0 until 3) {
            val (t0, t1) = StrikeStrokes[i]
            val f = span(p, t0, t1)
            if (f <= 0f) continue
            val bar = StrikeBars[i]
            val s0 = bar[0]
            val e0 = bar[1]
            val s1 = bar[2]
            val e1 = bar[3]
            val front0 = s0 + (e0 - s0) * f
            val front1 = s1 + (e1 - s1) * f
            val corners = listOf(onZ(s0), onZ(front0), onZ(front1), onZ(s1))
            path.reset()
            corners.forEachIndexed { k, c -> if (k == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y) }
            path.close()
            drawPath(path, green.copy(alpha = 0.14f * fade), style = Stroke(unit * 0.05f, join = StrokeJoin.Round), blendMode = plus)
            drawPath(path, green.copy(alpha = ((0.28f + 0.3f * flare) * fade).coerceIn(0f, 1f)), blendMode = plus)
            drawPath(path, mint.copy(alpha = 0.8f * fade), style = Stroke(unit * 0.008f, join = StrokeJoin.Round), blendMode = plus)
            if (f < 1f) {
                drawLine(white, corners[1], corners[2], unit * 0.012f, StrokeCap.Round, blendMode = plus)
                tip = (corners[1] + corners[2]) / 2f
                tipDepth = (zDepth(front0) + zDepth(front1)) / 2f
            }
        }
    }

    // The beam from the core to where it burns.
    val at = tip
    if (at != null && firing) {
        drawLine(bright.copy(alpha = 0.22f), core, at, unit * 0.05f, StrokeCap.Round, blendMode = plus)
        drawLine(bright.copy(alpha = 0.6f), core, at, unit * 0.018f, StrokeCap.Round, blendMode = plus)
        drawLine(white, core, at, unit * 0.006f, StrokeCap.Round, blendMode = plus)
        val r = unit * 0.13f / tipDepth
        drawCircle(
            Brush.radialGradient(0f to Color.White, 0.3f to hud.copy(alpha = 0.8f), 1f to Color.Transparent, center = at, radius = r),
            radius = r,
            center = at,
            blendMode = plus,
        )
    }

    // Sparks off the tip.
    StrikeSparks.forEach { s ->
        val age = p - s.born
        if (age < 0f || age > s.life) return@forEach
        val k = age / s.life
        var i = 0
        while (i < 2 && s.born > StrikeStrokes[i].second) i++
        val pt = trace(i, span(s.born, StrikeStrokes[i].first, StrikeStrokes[i].second))
        val o = onZ(pt)
        val z = zDepth(pt)
        val d = s.speed * unit * k / z * 1.4f
        drawCircle(
            spark.copy(alpha = (1f - k) * 0.9f),
            s.size * unit / 400f * (1f - 0.5f * k) * 1.6f / z,
            o + Offset(cos(s.angle) * d, sin(s.angle) * d + k * k * unit * 0.05f),
            blendMode = plus,
        )
    }

    // The eruption: flames of green light rising along the Z, from its start to its end.
    if (p > 0.6f && p < 0.9f) {
        for (m in 0 until 16) {
            val t = m / 15f
            val start = 0.6f + t * 0.05f
            val k = span(p, start, start + 0.24f)
            if (k <= 0f || k >= 1f) continue
            val pt = alongZ(t)
            val q = onZ(pt)
            val z = zDepth(pt)
            val a = sin(minOf(1f, k * 1.6f) * PI.toFloat() / 2f) * (1f - k)
            val tall = h * 0.32f * (0.4f + 0.6f * sin(minOf(1f, k * 2f) * PI.toFloat() / 2f)) / z * (0.7f + 0.6f * ((m * 37) % 10) / 10f)
            val wide = unit * 0.07f / z
            withTransform({
                translate(q.x, q.y - tall * 0.45f)
                scale(1f, tall / wide, Offset.Zero)
            }) {
                drawCircle(
                    Brush.radialGradient(
                        0f to white.copy(alpha = 0.75f * a),
                        0.35f to bright.copy(alpha = 0.45f * a),
                        1f to Color.Transparent,
                        center = Offset.Zero,
                        radius = wide,
                    ),
                    radius = wide,
                    center = Offset.Zero,
                    blendMode = plus,
                )
            }
        }
    }

    // Hexagon shards flung up.
    StrikeShards.forEach { s ->
        val k = span(p, 0.62f + s.delay, 0.86f + s.delay)
        if (k <= 0f || k >= 1f) return@forEach
        val pt = alongZ(s.at)
        val q = onZ(pt)
        val z = zDepth(pt)
        val c = Offset(q.x + s.drift * unit * k * 2f, q.y - s.rise * h * 0.5f * (1f - (1f - k) * (1f - k)) / z)
        val rr = unit * s.size / z * 1.4f
        path.reset()
        for (j in 0 until 6) {
            val a = s.spin * k + third * j
            val x = c.x + cos(a) * rr
            val y = c.y + sin(a) * rr
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, green.copy(alpha = (1f - k) * 0.3f), blendMode = plus)
        drawPath(path, mint.copy(alpha = (1f - k) * 0.9f), style = Stroke(unit * 0.003f + 0.5f), blendMode = plus)
    }

    // The ZENITH Z rises out of the floor, huge – the logo's metal Z with its green light, nearly
    // as wide as the screen; a band of light runs over it, Z-shaped shock waves spread from it,
    // and at the end it swells and fades.
    val rise = span(p, 0.58f, 0.7f)
    if (rise > 0f) {
        val ending = span(p, 0.86f, 1f)
        val shown = (rise * (1f - ending)).coerceIn(0f, 1f)
        val eased = 1f - (1f - rise) * (1f - rise) * (1f - rise)
        val zw = minOf(w * 0.94f, h * 0.55f / ZenithZAspect)
        val zh = zw * ZenithZAspect
        val floorY = onZ(Offset(0.5f, 0.5f)).y
        val restY = h * 0.42f
        val midY = floorY + (restY - floorY) * eased
        val middle = Offset(w / 2f, midY)
        val swell = 1f + 0.12f * ending
        drawCircle(
            Brush.radialGradient(listOf(green.copy(alpha = 0.35f * shown), Color.Transparent), center = middle, radius = zw * 0.75f),
            radius = zw * 0.75f,
            center = middle,
            blendMode = plus,
        )
        withTransform({
            scale((0.7f + 0.3f * eased) * swell, (0.15f + 0.85f * eased) * swell, middle)
        }) {
            val layer = Paint().apply { alpha = shown }
            drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), layer)
            drawZenithZ(w / 2f - zw / 2f, midY - zh / 2f, zw, light = 1f, charge = span(p, 0.62f, 0.86f))
            drawContext.canvas.restore()
            // Its edges in the Z-Angriff's color (green, or what the studio chose).
            drawPath(
                zenithZPath(w / 2f - zw / 2f, midY - zh / 2f, zw),
                hud.copy(alpha = 0.55f * shown),
                style = Stroke(zw * 0.006f, join = StrokeJoin.Round),
                blendMode = plus,
            )
        }
        for (start in listOf(0.68f, 0.76f)) {
            val q = span(p, start, start + 0.25f)
            if (q <= 0f || q >= 1f) continue
            withTransform({ scale(1f + 0.5f * q, 1f + 0.5f * q, Offset(w / 2f, restY)) }) {
                drawPath(
                    zenithZPath(w / 2f - zw / 2f, restY - zh / 2f, zw),
                    hud.copy(alpha = 0.7f * (1f - q)),
                    style = Stroke((unit * 0.01f * (1f - q) + 1f) / (1f + 0.5f * q)),
                    blendMode = plus,
                )
            }
        }
    }

    // The shockwave running over the floor.
    val wave = span(p, 0.62f, 0.92f)
    if (wave > 0f && wave < 1f) {
        path.reset()
        for (k in 0..48) {
            val a = k / 48f * 2f * PI.toFloat()
            val q = ground(cos(a) * (0.5f + wave * 2.4f), 0.48f + sin(a) * (0.25f + wave * 1.1f))
            if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        drawPath(path, green.copy(alpha = 0.25f * (1f - wave)), style = Stroke(unit * 0.05f * (1f - wave) + 1f), blendMode = plus)
        drawPath(path, spark.copy(alpha = 0.8f * (1f - wave)), style = Stroke(unit * 0.008f * (1f - wave) + 1f), blendMode = plus)
    }

    // The flash as the Z erupts.
    val flash = span(p, 0.59f, 0.62f) * (1f - span(p, 0.62f, 0.74f))
    if (flash > 0f) {
        val middle = onZ(Offset(0.5f, 0.5f))
        drawRect(
            Brush.radialGradient(
                0f to white.copy(alpha = 0.6f * flash),
                0.5f to green.copy(alpha = 0.18f * flash),
                1f to Color.Transparent,
                center = middle,
                radius = h * 0.7f,
            ),
            blendMode = plus,
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
    // Core Enforcer Studio: its color and tempo.
    val look = LocalSettings.current
    val millis = look.strikeSpeed.millis
    val tint = look.strikeColor.color
    LaunchedEffect(Unit) {
        val scale = millis / StrikeMillis.toFloat()
        launch {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // A tick as each stroke starts burning, a heavy knock as the Z erupts.
            for (at in listOf(432L, 312L, 384L)) {
                delay((at * scale).toLong())
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay((312 * scale).toLong())
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        p.animateTo(1f, tween(millis, easing = LinearEasing))
        onDone()
    }
    Popup(
        popupPositionProvider = WholeWindow,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawZenithStrike(p.value, tint) }
    }
}

/** The Z-Angriff filling a window of its own (over everything, from Glimmer), once – then [onDone]. */
@Composable
fun ZenithStrikeScreen(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val look = LocalSettings.current
    val millis = look.strikeSpeed.millis
    val tint = look.strikeColor.color
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(millis, easing = LinearEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) { drawZenithStrike(p.value, tint) }
}

/** How long the Z-Angriff takes at normal tempo (the others scale from it). */
private const val StrikeMillis = 2400
