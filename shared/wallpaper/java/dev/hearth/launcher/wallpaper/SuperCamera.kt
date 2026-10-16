package dev.hearth.launcher.wallpaper

import kotlin.math.pow

/** Where the body is and how big (all as fractions: [cx] of the width, [cy] of the height, [r] of the width). */
class SuperPose(var cx: Float = 0.5f, var cy: Float = 0.5f, var r: Float = 0.3f)

/*
 * The flight of the camera, the way the Moon of Xiaomi's HyperOS flies. Two stages:
 *
 *  - On the lock screen the body flies in from the upper left corner to the middle of the screen (the "lock flight",
 *    [lockFly] 0 … 1, which runs by itself when the lock screen appears).
 *  - When the phone is unlocked it moves a little up, and then the camera zooms in, much closer than the orbit – for the
 *    Moon so close that single craters can be seen (the "home flight", [depth] 0 … 1).
 *
 * [pose] gives the place and the size for both; the path is the same for every scene, only the sizes differ.
 */
object SuperCamera {

    /** The home flight in the time of the depth: moving up until [UP_END], turning in place until [ZOOM_START], then the zoom. */
    private const val UP_END = 0.25f
    private const val ZOOM_START = 0.45f

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

    fun easeInOutCubic(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }

    /** How far the body has turned because of the home flight (radians): a good part of a quarter turn while it moves up and turns. */
    fun spinKick(depth: Float): Float = 1.1f * easeInOutCubic(depth / ZOOM_START)

    /**
     * Start of the lock flight (cx, cy, r), then the lock screen's rest, the home position and the closest zoom of [scene].
     * Twelve numbers: L0, L1, H1, H2.
     */
    private fun shape(scene: SuperScene): FloatArray = when (scene) {
        SuperScene.Moon -> floatArrayOf(0.08f, 0.07f, 0.10f, 0.50f, 0.48f, 0.30f, 0.50f, 0.40f, 0.34f, 0.50f, 0.64f, 2.30f)
        SuperScene.Earth -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.48f, 0.28f, 0.50f, 0.40f, 0.31f, 0.50f, 0.62f, 1.00f)
        SuperScene.Geometry -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.50f, 0.27f, 0.50f, 0.45f, 0.30f, 0.50f, 0.46f, 0.40f)
        else -> {
            // The planets: the maps are small, so they come only a little closer than the home position.
            val k = when (scene) {
                SuperScene.Saturn -> 0.75f
                SuperScene.Jupiter -> 1.15f
                SuperScene.Mars -> 0.93f
                SuperScene.Pluto -> 0.85f
                else -> 1.0f
            }
            floatArrayOf(0.08f, 0.07f, 0.09f * k, 0.50f, 0.48f, 0.27f * k, 0.50f, 0.40f, 0.30f * k, 0.50f, 0.58f, 0.75f * k)
        }
    }

    /**
     * Fills [out] with the place of [scene] when the lock flight is at [lockFly] (0 = just begun … 1 = in the middle) and
     * the home flight at [depth] (0 = still on the lock screen … 1 = the closest zoom). The home flight moves the body
     * from where the lock flight has put it, so unlocking before the lock flight has ended makes no jump.
     */
    fun pose(scene: SuperScene, lockFly: Float, depth: Float, out: SuperPose) {
        val s = shape(scene)
        val f = easeOutCubic(lockFly)
        val cx0 = lerp(s[0], s[3], f)
        val cy0 = lerp(s[1], s[4], f)
        val r0 = lerp(s[2], s[5], f)
        val d = depth.coerceIn(0f, 1f)
        // Home flight, as a change from the lock screen's rest: [dx], [dy] and the factor [scale] of the size.
        val dx: Float
        val dy: Float
        val scale: Float
        when {
            d <= UP_END -> {
                val e = easeOutCubic(d / UP_END)
                dx = lerp(0f, s[6] - s[3], e)
                dy = lerp(0f, s[7] - s[4], e)
                scale = lerp(1f, s[8] / s[5], e)
            }
            d <= ZOOM_START -> {
                dx = s[6] - s[3]
                dy = s[7] - s[4]
                scale = s[8] / s[5]
            }
            else -> {
                val e = easeInOutCubic((d - ZOOM_START) / (1f - ZOOM_START))
                dx = lerp(s[6] - s[3], s[9] - s[3], e)
                dy = lerp(s[7] - s[4], s[10] - s[4], e)
                // A zoom is felt in factors, not in steps: the size grows exponentially.
                scale = (s[8] / s[5]) * (s[11] / s[8]).pow(e)
            }
        }
        out.cx = cx0 + dx
        out.cy = cy0 + dy
        out.r = r0 * scale
    }
}
