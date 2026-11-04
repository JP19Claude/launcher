package dev.hearth.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ZENITH 22: a rectangle with the corners of iOS – "continuous" ones. A circular corner meets the straight edge in a jolt of curvature (the
 * eye sees the seam); here the curve eases in and out over a length of about 1.6 times the radius, the way the Dynamic Island, the icons
 * and the cards of iOS are cut. [top] and [bottom] are the radii of the upper and the lower pair of corners (0: square, like the notch's
 * top where it hangs from the screen's edge). Where the radius takes all the room (a pill) it is a plain semicircle, as it should be.
 *
 * The curve is Figma's "corner smoothing" (0.6 is iOS's): a cubic, an arc of the circle, a cubic – worked out on one corner and turned
 * round to the other three.
 */
class ContinuousCornerShape(private val top: Dp, private val bottom: Dp, private val smoothing: Float = 0.6f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val budget = min(w, h) / 2f
        val rt = with(density) { top.toPx() }.coerceIn(0f, budget)
        val rb = with(density) { bottom.toPx() }.coerceIn(0f, budget)
        val path = Path()
        // Clockwise from the top-right corner: top right, bottom right, bottom left, top left.
        path.addCorner(rt, smoothing, budget, w, 0f, 0, first = true)
        path.addCorner(rb, smoothing, budget, w, h, 1, first = false)
        path.addCorner(rb, smoothing, budget, 0f, h, 2, first = false)
        path.addCorner(rt, smoothing, budget, 0f, 0f, 3, first = false)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Adds one corner (at ([ox], [oy]); [quarter] 0 = top right, 1 = bottom right, 2 = bottom left, 3 = top left, all travelled clockwise)
 * to the path. Worked out for the top right – the corner at the origin, the edge arriving along the negative x axis, the next one leaving
 * down the y axis – and turned for the others.
 */
private fun Path.addCorner(r: Float, smoothing: Float, budget: Float, ox: Float, oy: Float, quarter: Int, first: Boolean) {
    fun map(x: Float, y: Float): Offset = when (quarter) {
        0 -> Offset(ox + x, oy + y)
        1 -> Offset(ox - y, oy + x)
        2 -> Offset(ox - x, oy - y)
        else -> Offset(ox + y, oy - x)
    }
    if (r <= 0.5f) {
        if (first) moveTo(ox, oy) else lineTo(ox, oy)
        return
    }
    var xi = smoothing
    var p = (1f + xi) * r
    if (p > budget) {
        // Not enough room for the whole easing: it gives way (down to a plain round corner).
        xi = maxOf(0f, budget / r - 1f)
        p = budget
    }
    val arcMeasure = 90f * (1f - xi)
    val arcLength = sin(Math.toRadians((arcMeasure / 2f).toDouble())).toFloat() * r * sqrt(2f)
    val alphaDeg = (90f - arcMeasure) / 2f
    val p3p4 = r * tan(Math.toRadians((alphaDeg / 2f).toDouble())).toFloat()
    val beta = 45f * xi
    val c = p3p4 * cos(Math.toRadians(beta.toDouble())).toFloat()
    val d = c * tan(Math.toRadians(beta.toDouble())).toFloat()
    val b = (p - arcLength - c - d) / 3f
    val a = 2f * b

    val start = map(-p, 0f)
    if (first) moveTo(start.x, start.y) else lineTo(start.x, start.y)
    // The easing in: a cubic to where the circle begins.
    val sx = -p + a + b + c
    val sy = d
    val c1 = map(-p + a, 0f)
    val c2 = map(-p + a + b, 0f)
    val s = map(sx, sy)
    cubicTo(c1.x, c1.y, c2.x, c2.y, s.x, s.y)
    // The circle's own part (a cubic standing in for the arc).
    val al = Math.toRadians(alphaDeg.toDouble()).toFloat()
    val theta = Math.toRadians((90f - 2f * alphaDeg).toDouble()).toFloat()
    val k = 4f / 3f * tan(theta / 4f)
    val ex = sx + arcLength
    val ey = sy + arcLength
    val a1 = map(sx + k * r * cos(al), sy + k * r * sin(al))
    val a2 = map(ex - k * r * sin(al), ey - k * r * cos(al))
    val e = map(ex, ey)
    cubicTo(a1.x, a1.y, a2.x, a2.y, e.x, e.y)
    // The easing out, down the next edge.
    val f1 = map(ex + d, ey + c)
    val f2 = map(ex + d, ey + b + c)
    val f3 = map(ex + d, ey + a + b + c)
    cubicTo(f1.x, f1.y, f2.x, f2.y, f3.x, f3.y)
}
