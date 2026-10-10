package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/*
 * ZENITH's mark, drawn: the slanted Z with its cut ends (as in the ZENITH logo), the metal it's
 * made of with its green edge light, and the wide, thin ZENITH lettering.
 */

/** Where ZENITH's symbol stands in plain text (where Ω used to). */
const val ZenithSymbol = "Ƶ"

/** The Z's height to its width. */
internal const val ZenithZAspect = 0.531f

/** The Z's corners, clockwise from the top left: (x of its width, y of its height). */
private val ZenithZPoints = floatArrayOf(
    0.200f, 0f,
    0.910f, 0f,
    0.355f, 0.766f,
    1f, 0.766f,
    0.823f, 1f,
    0.007f, 1f,
    0.535f, 0.199f,
    0.080f, 0.199f,
)

/** Corner [i] of the Z in the box at [left]/[top], [w] wide. */
private fun zCorner(i: Int, left: Float, top: Float, w: Float): Offset =
    Offset(left + ZenithZPoints[i * 2] * w, top + ZenithZPoints[i * 2 + 1] * w * ZenithZAspect)

/** The Z as a path, in the box at [left]/[top], [w] wide. */
internal fun zenithZPath(left: Float, top: Float, w: Float): Path = Path().apply {
    for (i in 0 until 8) {
        val p = zCorner(i, left, top, w)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** The Z, flat, in [brush] – ZENITH's symbol wherever a small one is drawn. */
internal fun DrawScope.drawZenithMark(left: Float, top: Float, w: Float, brush: Brush, alpha: Float = 1f) {
    drawPath(zenithZPath(left, top, w), brush, alpha = alpha)
}

/** The Z centered on [center], [w] wide. */
internal fun DrawScope.drawZenithMarkAt(center: Offset, w: Float, color: Color, alpha: Float = 1f) {
    val h = w * ZenithZAspect
    drawZenithMark(center.x - w / 2f, center.y - h / 2f, w, SolidColor(color), alpha)
}

/** ZENITH's symbol as a little composable: the flat Z in [color], [width] wide. */
@Composable
fun ZenithMark(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = width, height = width * ZenithZAspect)) {
        drawZenithMark(0f, 0f, size.width, SolidColor(color))
    }
}

/** A scratch or a speck in the Z's metal (where, how long, which way, light or dark). */
internal class MetalMark(val x: Float, val y: Float, val len: Float, val angle: Float, val light: Boolean, val alpha: Float, val width: Float)

/** The Z's worn metal: scratches and specks, the same every time. */
internal val ZenithMetal: List<MetalMark> = Random(19).let { r ->
    List(300) {
        MetalMark(r.nextFloat(), r.nextFloat(), 0.01f + r.nextFloat() * 0.05f, -0.3f + r.nextFloat() * 0.6f, r.nextBoolean(), r.nextFloat(), 0.001f + r.nextFloat() * 0.002f)
    } + List(400) {
        MetalMark(r.nextFloat(), r.nextFloat(), 0f, 0f, r.nextBoolean(), r.nextFloat(), 0.003f)
    }
}

private val RimColors = listOf(Color(0xFF2FD25A), Color(0xFF46EB6E), Color(0xFFD2FFD7))
private val RimWidths = floatArrayOf(0.03f, 0.012f, 0.004f)
private val RimAlphas = floatArrayOf(0.22f, 0.55f, 0.95f)

/** Green light along the edge [a]→[b]: a wide haze, a line, a white-hot core; [k] its strength at start, middle, end. */
private fun DrawScope.rimLight(a: Offset, b: Offset, w: Float, grow: Float, k0: Float, k1: Float, k2: Float) {
    for (i in 0..2) {
        val c = RimColors[i]
        val base = RimAlphas[i]
        drawLine(
            Brush.linearGradient(
                listOf(
                    c.copy(alpha = (base * k0).coerceIn(0f, 1f)),
                    c.copy(alpha = (base * k1).coerceIn(0f, 1f)),
                    c.copy(alpha = (base * k2).coerceIn(0f, 1f)),
                ),
                start = a,
                end = b,
            ),
            a,
            b,
            strokeWidth = w * RimWidths[i] * (1f + 0.6f * grow),
            cap = StrokeCap.Round,
        )
    }
}

/** A hot spot of green light at [p]. */
private fun DrawScope.rimSpot(p: Offset, r: Float, a: Float) {
    if (r <= 0f || a <= 0f) return
    val al = a.coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFEBFFEB).copy(alpha = al),
            0.25f to Color(0xFF50F078).copy(alpha = al * 0.6f),
            1f to Color(0x0028C85A),
            center = p,
            radius = r,
        ),
        r,
        p,
    )
}

/**
 * The ZENITH Z as in its logo, in the box at [left]/[top], [w] wide: dark, worn metal lit from
 * the top left, a silver bevel round it, and green light along its lower edges. [light] scales
 * the green (0 = none, 1 = as in the logo); [charge] (0..1, held) makes it burn brighter and
 * runs a band of green light over the metal.
 */
internal fun DrawScope.drawZenithZ(left: Float, top: Float, w: Float, light: Float = 1f, charge: Float = 0f) {
    val h = w * ZenithZAspect
    val z = zenithZPath(left, top, w)
    val p = List(8) { zCorner(it, left, top, w) }
    // The metal.
    drawPath(
        z,
        Brush.linearGradient(
            listOf(Color(0xFF55595C), Color(0xFF33373A), Color(0xFF1A1C1E)),
            start = Offset(left, top),
            end = Offset(left + w * 0.4f, top + h * 1.2f),
        ),
    )
    clipPath(z) {
        // Worn: scratches and specks.
        ZenithMetal.forEach { m ->
            val at = Offset(left + m.x * w, top + m.y * h)
            val color = if (m.light) Color.White.copy(alpha = 0.02f + m.alpha * 0.06f) else Color.Black.copy(alpha = 0.12f + m.alpha * 0.2f)
            if (m.len > 0f) {
                drawLine(color, at, at + Offset(kotlin.math.cos(m.angle) * m.len * w, kotlin.math.sin(m.angle) * m.len * w), strokeWidth = m.width * w)
            } else {
                drawRect(color, at, Size(m.width * w, m.width * w))
            }
        }
        // Light from the top left.
        drawRect(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), center = Offset(left + w * 0.35f, top), radius = w * 0.7f),
            Offset(left, top),
            Size(w, h),
        )
        if (charge > 0.01f) {
            // Held: green light floods the metal and a bright band runs over it.
            drawRect(Color(0xFF2FD27A).copy(alpha = 0.12f * charge), Offset(left, top), Size(w, h))
            val band = left + w * (-0.2f + 1.4f * charge)
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0x0078FFA0), Color(0xFFA0FFBE).copy(alpha = 0.35f), Color(0x0078FFA0)),
                    start = Offset(band - w * 0.12f, top),
                    end = Offset(band + w * 0.12f, top + h),
                ),
                Offset(left, top),
                Size(w, h),
            )
        }
        // The bevel: light on top, dark below.
        drawPath(
            z,
            Brush.verticalGradient(
                listOf(Color(0xFFE6EBF0).copy(alpha = 0.75f), Color(0xFF787D82).copy(alpha = 0.4f), Color(0xFF141414).copy(alpha = 0.6f)),
                startY = top,
                endY = top + h,
            ),
            style = Stroke(w * 0.018f),
        )
    }
    // Silver catching the light on its upper edges.
    drawLine(Color(0xFFEBF0F5).copy(alpha = 0.85f), p[0], p[1], strokeWidth = w * 0.003f, cap = StrokeCap.Round)
    drawLine(Color(0xFFC8CDD2).copy(alpha = 0.6f), p[3], p[4], strokeWidth = w * 0.003f, cap = StrokeCap.Round)
    drawLine(Color(0xFFB4B9BE).copy(alpha = 0.5f), p[7], p[0], strokeWidth = w * 0.003f, cap = StrokeCap.Round)
    // The green light along its lower edges.
    val k = light * (0.6f + 0.8f * charge)
    if (k > 0.01f) {
        rimLight(p[7], p[6], w, charge, 1f * k, 0.7f * k, 0.15f * k)
        rimLight(p[5], p[6], w, charge, 0.35f * k, 1f * k, 0.6f * k)
        rimLight(p[2], p[3], w, charge, 0.25f * k, 0.9f * k, 1f * k)
        rimLight(p[5], p[4], w, charge, 0f, 0.1f * k, 0.8f * k)
        rimSpot(p[5] + (p[6] - p[5]) * 0.35f, w * 0.05f * (1f + charge), 0.9f * k)
        rimSpot(Offset(p[2].x + (p[3].x - p[2].x) * 0.62f, p[2].y), w * 0.045f * (1f + charge), 0.9f * k)
        rimSpot(p[7] + Offset(w * 0.02f, 0f), w * 0.03f * (1f + charge), 0.7f * k)
    }
}

/** The ZENITH lettering's letters, each drawn of bars: Z, the E as three bars, N, I, T, H. */
private const val Lettering = "ZENITH"

/** How wide the lettering is for letters [h] high. */
internal fun zenithLetteringWidth(h: Float): Float = h * (5 * 1.5f + 0.11f + 5 * 1.55f)

/**
 * "ZENITH", as under the logo: wide, thin, chrome – the E three bars without a stem – starting at
 * [left]/[top], letters [h] high.
 */
internal fun DrawScope.drawZenithLettering(left: Float, top: Float, h: Float, alpha: Float = 1f) {
    val t = h * 0.11f
    val wide = h * 1.5f
    val gap = h * 1.55f
    val chrome = Brush.verticalGradient(
        0f to Color(0xFFF4F6F7),
        0.45f to Color(0xFFA9AEB2),
        0.55f to Color(0xFFE8EBED),
        1f to Color(0xFF7C8186),
        startY = top,
        endY = top + h,
    )
    var x = left
    fun bar(dx: Float, dy: Float, bw: Float, bh: Float) = drawRect(chrome, Offset(x + dx, top + dy), Size(bw, bh), alpha = alpha)
    fun slant(x0: Float, y0: Float, x1: Float, y1: Float) {
        // A diagonal stroke from (x0, y0) to (x1, y1), t thick across.
        val path = Path().apply {
            moveTo(x + x0, top + y0)
            lineTo(x + x0 + t * 1.3f, top + y0)
            lineTo(x + x1 + t * 1.3f, top + y1)
            lineTo(x + x1, top + y1)
            close()
        }
        drawPath(path, chrome, alpha = alpha)
    }
    Lettering.forEach { letter ->
        val lw = if (letter == 'I') t else wide
        when (letter) {
            'Z' -> {
                bar(0f, 0f, lw, t)
                bar(0f, h - t, lw, t)
                slant(lw - t * 1.3f, t, 0f, h - t)
            }
            'E' -> {
                bar(0f, 0f, lw, t)
                bar(0f, h / 2f - t / 2f, lw, t)
                bar(0f, h - t, lw, t)
            }
            'N' -> {
                bar(0f, 0f, t, h)
                bar(lw - t, 0f, t, h)
                slant(0f, 0f, lw - t * 1.3f, h)
            }
            'I' -> bar(0f, 0f, t, h)
            'T' -> {
                bar(0f, 0f, lw, t)
                bar(lw / 2f - t / 2f, 0f, t, h)
            }
            'H' -> {
                bar(0f, 0f, t, h)
                bar(lw - t, 0f, t, h)
                bar(0f, h / 2f - t / 2f, lw, t)
            }
        }
        x += lw + gap
    }
}

/** The ZENITH lettering as a composable, letters [height] high. */
@Composable
fun ZenithLettering(height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = height * (5 * 1.5f + 0.11f + 5 * 1.55f), height = height)) {
        drawZenithLettering(0f, 0f, size.height)
    }
}

/**
 * A plate of the Z's metal filling this scope – the ZENITH dock: slanted ends cut like the Z's
 * bars ([slant] wide), dark worn metal, a silver top edge, and green light along its foot,
 * pooling on whatever is under it. [unit] sets how wide the green light is (the logo's width it
 * would belong to).
 */
internal fun DrawScope.drawZenithPlate(slant: Float, unit: Float, alpha: Float = 0.92f) {
    val w = size.width
    val h = size.height
    val plate = Path().apply {
        moveTo(slant, 0f)
        lineTo(w, 0f)
        lineTo(w - slant, h)
        lineTo(0f, h)
        close()
    }
    // A breath of green light under it.
    val pool = Offset(w * 0.6f, h)
    scale(1f, 0.3f, pivot = pool) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2FD27A).copy(alpha = 0.22f), Color.Transparent), center = pool, radius = w * 0.5f), w * 0.5f, pool)
    }
    // The metal: dark, light falling from the top, a faint diagonal sheen.
    drawPath(plate, Brush.verticalGradient(listOf(Color(0xFF3E4245), Color(0xFF26292C), Color(0xFF141618)), startY = 0f, endY = h), alpha = alpha)
    drawPath(
        plate,
        Brush.linearGradient(
            0f to Color.Transparent,
            0.46f to Color.White.copy(alpha = 0.04f),
            0.54f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(w * 0.6f, h),
        ),
    )
    // A silver hairline along its top and its left cut.
    drawLine(Color.White.copy(alpha = 0.45f), Offset(slant, 0f), Offset(w, 0f), strokeWidth = unit * 0.004f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, h), Offset(slant, 0f), strokeWidth = unit * 0.004f, cap = StrokeCap.Round)
    // One crisp green line along its foot.
    zenithFootLine(1f)
}

/**
 * ZENITH's green line along a surface's foot: one crisp line, brightest a little right of the
 * middle and fading out towards both ends, with a soft glow – the logo's light, held back. Small
 * surfaces (buttons, chips) don't get it; [k] is its strength.
 */
private fun DrawScope.zenithFootLine(k: Float) {
    if (k <= 0f || size.minDimension < 56.dp.toPx()) return
    val px = 1.dp.toPx()
    val y = size.height - px * 0.6f
    val a = Offset(size.width * 0.05f, y)
    val b = Offset(size.width * 0.93f, y)
    fun line(width: Float, alpha: Float, core: Color) = drawLine(
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.3f to ZenithGreen.copy(alpha = (alpha * 0.6f * k).coerceIn(0f, 1f)),
            0.68f to core.copy(alpha = (alpha * k).coerceIn(0f, 1f)),
            1f to Color.Transparent,
            startX = a.x,
            endX = b.x,
        ),
        a,
        b,
        strokeWidth = width,
    )
    line(px * 6f, 0.1f, ZenithGreen)
    line(px * 2f, 0.32f, ZenithGreen)
    line(px, 0.95f, Color(0xFFC8FFD6))
}

/** A silver hairline round the top of [outline], fading out down its sides. */
private fun DrawScope.zenithTopLine(outline: androidx.compose.ui.graphics.Outline) {
    drawOutline(
        outline,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.4f),
            0.35f to Color.White.copy(alpha = 0.04f),
            1f to Color.Transparent,
        ),
        style = Stroke(1.dp.toPx()),
    )
}

/**
 * ZENITH's edge on a rounded plate filling this scope: a silver hairline round its top and one
 * crisp green line along its foot. [strength] dims it; [press] (0..1, while held) brightens it.
 */
internal fun DrawScope.drawZenithEdge(corner: Float, strength: Float = 1f, press: Float = 0f) {
    val r = corner.coerceAtMost(size.minDimension / 2f)
    val outline = androidx.compose.ui.graphics.Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    zenithTopLine(outline)
    zenithFootLine((strength * (1f + 0.6f * press)).coerceIn(0f, 2f))
}

/**
 * ZENITH's shape: a plate with two corners cut off at a slant – the top left and the bottom
 * right, like the ends of the Z's bars – the other two square. [cut] is how much is cut off.
 */
class ZenithCutShape(private val cut: Dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val c = with(density) { cut.toPx() }.coerceIn(0f, minOf(size.width, size.height) * 0.4f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** ZENITH's edge along any [outline]: a silver hairline round its top, a crisp green line along its foot. */
internal fun DrawScope.drawZenithOutlineEdge(outline: androidx.compose.ui.graphics.Outline, strength: Float = 1f, press: Float = 0f) {
    zenithTopLine(outline)
    zenithFootLine((strength * (1f + 0.6f * press)).coerceIn(0f, 2f))
}

/**
 * "ZENITH Metall": a surface of the logo's dark metal instead of glass, in [outline] (ZENITH's
 * cut shape) – smoked [fill] over the blurred background, light falling from the top, a faint
 * diagonal sheen like light on brushed metal, a green glow where it's [touch]ed ([glow] 0..1)
 * and ZENITH's edge.
 */
internal fun DrawScope.drawZenithSurface(outline: androidx.compose.ui.graphics.Outline, fill: Color, glow: Float, touch: Offset) {
    drawOutline(outline, fill)
    drawOutline(
        outline,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.07f),
            0.4f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.2f),
        ),
    )
    drawOutline(
        outline,
        Brush.linearGradient(
            0f to Color.Transparent,
            0.46f to Color.White.copy(alpha = 0.035f),
            0.54f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    if (glow > 0.01f) {
        drawOutline(
            outline,
            Brush.radialGradient(
                0f to ZenithGreen.copy(alpha = (0.22f * glow).coerceIn(0f, 1f)),
                1f to Color.Transparent,
                center = touch,
                radius = size.minDimension.coerceAtLeast(1f),
            ),
        )
    }
    drawZenithOutlineEdge(outline, 1f, glow)
}

/**
 * Projekt Zenith: a hologram frame round [outline] – a thin green line, brighter brackets at the
 * square corners, tick marks along the top; [press] (0..1) makes it burn.
 */
internal fun DrawScope.drawHudFrame(outline: androidx.compose.ui.graphics.Outline, press: Float = 0f) {
    val px = 1.dp.toPx()
    drawOutline(outline, ZenithGreen.copy(alpha = (0.32f + 0.45f * press).coerceIn(0f, 1f)), style = Stroke(px))
    val l = minOf(size.minDimension * 0.24f, 18.dp.toPx())
    val bracket = Color(0xFF7DFFA8).copy(alpha = 0.95f)
    val sw = px * 2f
    drawLine(bracket, Offset(size.width - l, 0f), Offset(size.width, 0f), strokeWidth = sw)
    drawLine(bracket, Offset(size.width, 0f), Offset(size.width, l), strokeWidth = sw)
    drawLine(bracket, Offset(0f, size.height - l), Offset(0f, size.height), strokeWidth = sw)
    drawLine(bracket, Offset(0f, size.height), Offset(l, size.height), strokeWidth = sw)
    if (size.width > 120.dp.toPx()) {
        for (i in 0 until 4) {
            val x = size.width * 0.62f + i * 6.dp.toPx()
            drawLine(ZenithGreen.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, 4.dp.toPx()), strokeWidth = px)
        }
    }
}

/**
 * Projekt Zenith: a hologram panel in [outline] instead of metal – dark glass with a green
 * breath, scan lines, a green glow where it's [touch]ed ([glow] 0..1), and the hologram frame.
 */
internal fun DrawScope.drawHudSurface(outline: androidx.compose.ui.graphics.Outline, glow: Float, touch: Offset) {
    val px = 1.dp.toPx()
    drawOutline(outline, Color(0xFF020805).copy(alpha = 0.74f))
    drawOutline(
        outline,
        Brush.verticalGradient(
            0f to ZenithGreen.copy(alpha = 0.1f),
            0.5f to Color.Transparent,
            1f to ZenithGreen.copy(alpha = 0.06f),
        ),
    )
    if (size.minDimension > 40.dp.toPx()) {
        val panel = Path().apply { addOutline(outline) }
        clipPath(panel) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(ZenithGreen.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), strokeWidth = px * 0.6f)
                y += step
            }
        }
    }
    if (glow > 0.01f) {
        drawOutline(
            outline,
            Brush.radialGradient(
                0f to ZenithGreen.copy(alpha = (0.32f * glow).coerceIn(0f, 1f)),
                1f to Color.Transparent,
                center = touch,
                radius = size.minDimension.coerceAtLeast(1f),
            ),
        )
    }
    drawHudFrame(outline, glow)
}

/** Projekt Zenith: the dock as a hologram – a slanted panel with scan lines, a green frame and brackets. */
internal fun DrawScope.drawHudPlate(slant: Float) {
    val w = size.width
    val h = size.height
    val plate = Path().apply {
        moveTo(slant, 0f)
        lineTo(w, 0f)
        lineTo(w - slant, h)
        lineTo(0f, h)
        close()
    }
    val pool = Offset(w * 0.5f, h)
    scale(1f, 0.35f, pivot = pool) {
        drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.3f), Color.Transparent), center = pool, radius = w * 0.55f), w * 0.55f, pool)
    }
    drawHudSurface(androidx.compose.ui.graphics.Outline.Generic(plate), 0f, Offset.Zero)
}
