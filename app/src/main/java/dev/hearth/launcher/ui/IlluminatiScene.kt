package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * OMEGA UI 17.6 – the Clawd Illuminati: the secret council of six Clawds in hooded robes at a
 * triangular table by candlelight, under the all-seeing eye in its pyramid.
 */

internal val IlluminatiGold = Color(0xFFD4AF37)
private val RobeColor = Color(0xFF2E0812)
private val HoodColor = Color(0xFF3D0D18)

/** Clawd's pixel grid (as in Clawd.kt): 14 wide, 9 high. */
private const val GRID_COLS = 14f
private const val GRID_ROWS = 9f

/**
 * A Clawd of the council: Clawd himself in [face], wrapped in a hooded robe – only his face
 * looks out of the hood – with a golden trim and clasp.
 */
internal fun DrawScope.drawRobedClawd(topLeft: Offset, w: Float, h: Float, face: Color, trim: Color, mirrored: Boolean) {
    val full = size
    val px = minOf(w / GRID_COLS, h / (GRID_ROWS + 2f))
    val left = topLeft.x + (w - px * GRID_COLS) / 2f
    val top = topLeft.y + (h - px * GRID_ROWS) / 2f + px * 0.5f
    fun x(c: Float) = if (mirrored) left + (GRID_COLS - c) * px else left + c * px
    fun y(r: Float) = top + r * px
    // Clawd himself, then the robe over him.
    drawContext.transform.translate(topLeft.x, topLeft.y)
    drawContext.size = Size(w, h)
    drawClawd(face, blink = false, bob = 0f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f)
    drawContext.size = full
    drawContext.transform.translate(-topLeft.x, -topLeft.y)

    // The robe: from the shoulders to the floor, wider at the bottom, over arms and legs.
    val robe = Path().apply {
        moveTo(x(0.8f), y(3.4f))
        lineTo(x(13.2f), y(3.4f))
        lineTo(x(14.4f), y(9.4f))
        lineTo(x(-0.4f), y(9.4f))
        close()
    }
    drawPath(robe, Brush.verticalGradient(listOf(HoodColor, RobeColor), startY = y(3.4f), endY = y(9.4f)))
    // The hood: a pointed arch round the head, the face looking out of it.
    val hood = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(x(0.4f), y(5.4f))
        lineTo(x(0.4f), y(0.8f))
        quadraticTo(x(0.8f), y(-2.2f), x(7f), y(-3.1f))
        quadraticTo(x(13.2f), y(-2.2f), x(13.6f), y(0.8f))
        lineTo(x(13.6f), y(5.4f))
        close()
        val l = minOf(x(2.5f), x(11.5f))
        val r = maxOf(x(2.5f), x(11.5f))
        addRoundRect(RoundRect(l, y(0.6f), r, y(4.9f), CornerRadius(px * 1.6f)))
    }
    drawPath(hood, HoodColor)
    // Gold along the hood's opening, down the front, and the clasp.
    val l = minOf(x(2.5f), x(11.5f))
    val r = maxOf(x(2.5f), x(11.5f))
    drawRoundRect(trim.copy(alpha = 0.75f), Offset(l, y(0.6f)), Size(r - l, y(4.9f) - y(0.6f)), CornerRadius(px * 1.6f), style = Stroke(px * 0.3f))
    drawLine(trim.copy(alpha = 0.6f), Offset(x(7f), y(5.6f)), Offset(x(7f), y(9.3f)), strokeWidth = px * 0.3f)
    drawCircle(trim, radius = px * 0.55f, center = Offset(x(7f), y(5.4f)))
}

/**
 * The all-seeing eye in its pyramid: a golden triangle, apex at [apex], [height] tall, with an
 * eye inside – [open] 0..1 (it blinks), its pupil turned by [look] (−1..1 each way).
 */
internal fun DrawScope.drawEyePyramid(apex: Offset, height: Float, gold: Color, open: Float = 1f, look: Offset = Offset.Zero, glow: Float = 1f) {
    val half = height * 0.62f
    val tri = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(apex.x + half, apex.y + height)
        lineTo(apex.x - half, apex.y + height)
        close()
    }
    drawPath(tri, Brush.verticalGradient(listOf(gold.copy(alpha = 0.28f * glow), gold.copy(alpha = 0.05f)), startY = apex.y, endY = apex.y + height))
    drawPath(tri, gold, style = Stroke(height * 0.035f))
    // The eye.
    val c = Offset(apex.x, apex.y + height * 0.64f)
    val ew = half * 0.86f
    val eh = height * 0.2f * open.coerceIn(0.05f, 1f)
    val eye = Path().apply {
        moveTo(c.x - ew / 2f, c.y)
        quadraticTo(c.x, c.y - eh, c.x + ew / 2f, c.y)
        quadraticTo(c.x, c.y + eh, c.x - ew / 2f, c.y)
        close()
    }
    drawPath(eye, Color(0xFFFFF6DC).copy(alpha = 0.9f))
    if (open > 0.2f) {
        val ir = height * 0.075f * open
        val at = Offset(c.x + look.x * ew * 0.18f, c.y + look.y * eh * 0.15f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE5243F), Color(0xFF6A0A1A)), center = at, radius = ir), radius = ir, center = at)
        drawCircle(Color.Black, radius = ir * 0.45f, center = at)
        drawCircle(Color.White.copy(alpha = 0.8f), radius = ir * 0.18f, center = at + Offset(-ir * 0.3f, -ir * 0.3f))
    }
    drawPath(eye, gold, style = Stroke(height * 0.02f))
}

/** A small eye-in-the-pyramid, for headings and the like. */
@Composable
internal fun EyeTriangle(modifier: Modifier = Modifier, gold: Color = IlluminatiGold) {
    Canvas(modifier) { drawEyePyramid(Offset(size.width / 2f, size.height * 0.04f), size.height * 0.92f, gold) }
}

/**
 * The council at their table: dim, lit only by a candle in the middle and the glowing eye
 * above; six robed Clawds sit round a triangular table, two on every side. The candle
 * flickers, the eye blinks now and then and looks around.
 */
@Composable
internal fun IlluminatiCouncil(modifier: Modifier = Modifier, face: Color = ClawdColor) {
    val t = rememberInfiniteTransition(label = "council")
    val flame by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse), label = "flame")
    val drift by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "drift")
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(kotlin.random.Random.nextLong(2600, 6000))
            blink.animateTo(0f, tween(110))
            blink.animateTo(1f, tween(170))
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val flicker = 0.85f + 0.15f * sin(flame * PI.toFloat() * 3f) * cos(flame * 7f)
        // The dark room.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050305), Color(0xFF0E0607), Color(0xFF040203))))
        // Light: the eye's glow above, the candle's below.
        val eyeAt = Offset(cx, h * 0.04f)
        val eyeHeight = h * 0.32f
        drawCircle(
            Brush.radialGradient(listOf(IlluminatiGold.copy(alpha = 0.22f), Color.Transparent), center = eyeAt + Offset(0f, eyeHeight * 0.6f), radius = w * 0.45f),
            radius = w * 0.45f,
            center = eyeAt + Offset(0f, eyeHeight * 0.6f),
        )
        // Rays round the pyramid.
        val rayC = eyeAt + Offset(0f, eyeHeight * 0.62f)
        for (i in 0 until 18) {
            val a = i * (2.0 * PI / 18) + drift * 0.05
            val from = rayC + Offset(cos(a).toFloat(), sin(a).toFloat()) * eyeHeight * 0.75f
            val to = rayC + Offset(cos(a).toFloat(), sin(a).toFloat()) * eyeHeight * (if (i % 2 == 0) 1.35f else 1.1f)
            drawLine(IlluminatiGold.copy(alpha = 0.16f), from, to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawEyePyramid(eyeAt, eyeHeight, IlluminatiGold, open = blink.value, look = Offset(sin(drift), cos(drift * 0.7f) * 0.4f))

        // The triangular table, in perspective: one corner far, two near.
        val far = Offset(cx, h * 0.55f)
        val nearL = Offset(cx - w * 0.33f, h * 0.86f)
        val nearR = Offset(cx + w * 0.33f, h * 0.86f)
        val mid = Offset(cx, (far.y + nearL.y + nearR.y) / 3f)
        val candleGlow = w * 0.62f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.32f * flicker), Color(0xFF7A2A0A).copy(alpha = 0.12f * flicker), Color.Transparent), center = mid, radius = candleGlow),
            radius = candleGlow,
            center = mid,
        )
        val table = Path().apply {
            moveTo(far.x, far.y)
            lineTo(nearR.x, nearR.y)
            lineTo(nearL.x, nearL.y)
            close()
        }
        // Six seats: two on every side, a little outside the table's edge.
        fun seat(a: Offset, b: Offset, f: Float): Offset {
            val p = a + (b - a) * f
            val out = p - mid
            val len = out.getDistance().coerceAtLeast(1f)
            return p + out * (h * 0.09f / len)
        }
        val seats = listOf(
            seat(far, nearL, 0.32f) to false,
            seat(far, nearL, 0.72f) to false,
            seat(far, nearR, 0.32f) to true,
            seat(far, nearR, 0.72f) to true,
            seat(nearL, nearR, 0.3f) to false,
            seat(nearL, nearR, 0.7f) to true,
        ).sortedBy { it.first.y }
        fun drawSeat(s: Pair<Offset, Boolean>, leader: Boolean) {
            val depth = ((s.first.y - far.y) / (nearL.y - far.y)).coerceIn(0f, 1.3f)
            val ch = h * (0.15f + 0.08f * depth)
            val cw = ch * (GRID_COLS / GRID_ROWS)
            drawRobedClawd(
                Offset(s.first.x - cw / 2f, s.first.y - ch * 0.78f),
                cw,
                ch,
                face,
                if (leader) Color(0xFFFFE08A) else IlluminatiGold,
                s.second,
            )
        }
        // The far ones sit behind the table, the near ones in front of it.
        seats.filter { it.first.y < mid.y }.forEach { drawSeat(it, false) }
        drawPath(table, Brush.verticalGradient(listOf(Color(0xFF3A1B10), Color(0xFF170905)), startY = far.y, endY = nearL.y))
        drawPath(table, IlluminatiGold.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
        // An inlaid triangle on the table top.
        val inset = Path().apply {
            moveTo(mid.x + (far.x - mid.x) * 0.55f, mid.y + (far.y - mid.y) * 0.55f)
            lineTo(mid.x + (nearR.x - mid.x) * 0.55f, mid.y + (nearR.y - mid.y) * 0.55f)
            lineTo(mid.x + (nearL.x - mid.x) * 0.55f, mid.y + (nearL.y - mid.y) * 0.55f)
            close()
        }
        drawPath(inset, IlluminatiGold.copy(alpha = 0.3f), style = Stroke(1.dp.toPx()))
        // The candle.
        val cw = w * 0.022f
        val chh = h * 0.07f
        drawRect(Color(0xFFF3E6C8), Offset(mid.x - cw / 2f, mid.y - chh), Size(cw, chh))
        val flameH = h * 0.05f * (0.85f + 0.3f * flicker - 0.15f)
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFA235), Color(0x00FF5A1A)), startY = mid.y - chh - flameH, endY = mid.y - chh),
            Offset(mid.x - cw * 0.6f, mid.y - chh - flameH),
            Size(cw * 1.2f, flameH),
        )
        seats.filter { it.first.y >= mid.y }.forEachIndexed { i, s -> drawSeat(s, leader = i == 0) }
        // Vignette: the corners stay in the dark.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), center = Offset(cx, h * 0.55f), radius = w * 0.8f))
    }
}

/** Clawd Illuminati "Allsehendes Auge": the eye in its pyramid on the home screen, blinking and looking around. */
@Composable
internal fun AllSeeingEye(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "allSeeing")
    val look by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(11_000, easing = LinearEasing)), label = "look")
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(kotlin.random.Random.nextLong(3500, 9000))
            blink.animateTo(0f, tween(110))
            blink.animateTo(1f, tween(170))
        }
    }
    Canvas(modifier) {
        drawCircle(
            Brush.radialGradient(listOf(IlluminatiGold.copy(alpha = 0.3f), Color.Transparent)),
            radius = size.maxDimension * 0.8f,
        )
        drawEyePyramid(
            Offset(size.width / 2f, size.height * 0.04f),
            size.height * 0.92f,
            IlluminatiGold,
            open = blink.value,
            look = Offset(sin(look), cos(look * 1.3f) * 0.5f),
        )
    }
}
