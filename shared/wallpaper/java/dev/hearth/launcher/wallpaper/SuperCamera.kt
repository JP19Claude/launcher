package dev.hearth.launcher.wallpaper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/** Where the body is and how big (all as fractions: [cx] of the width, [cy] of the height, [r] of the width). */
class SuperPose(var cx: Float = 0.5f, var cy: Float = 0.5f, var r: Float = 0.3f)

/*
 * The flight of the camera, the way the Moon of Xiaomi's HyperOS flies: on the lock screen the body hangs small in the
 * upper left; when the phone is unlocked it flies in from there to the bottom centre, where it turns for a while; then
 * the camera zooms in until single craters can be seen. [pose] gives the place and the size for a "depth" of the
 * flight, 0 (the lock screen) … 1 (the close-up on the home screen); the path is the same for every scene, only the
 * sizes differ.
 */
object SuperCamera {

    /** The flight in the time of the depth: flying in until [FLY_END], turning in the orbit until [HOLD_END], then the zoom. */
    private const val FLY_END = 0.22f
    private const val HOLD_END = 0.50f

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

    fun easeInOutCubic(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }

    private fun easeInOutSine(t: Float): Float = (1f - cos(PI.toFloat() * t.coerceIn(0f, 1f))) / 2f

    /** How far the body has turned because of the flight (radians): a good part of a quarter turn while it flies and orbits. */
    fun spinKick(depth: Float): Float = 1.2f * easeInOutCubic(depth / HOLD_END)

    /** Lock screen, orbit and close-up of [scene]: (cx, cy, r) each. */
    private fun shape(scene: SuperScene): FloatArray = when (scene) {
        SuperScene.Moon -> floatArrayOf(0.26f, 0.31f, 0.24f, 0.50f, 0.80f, 0.62f, 0.50f, 0.66f, 1.25f)
        SuperScene.Earth -> floatArrayOf(0.26f, 0.31f, 0.23f, 0.50f, 0.80f, 0.60f, 0.50f, 0.72f, 0.86f)
        SuperScene.Geometry -> floatArrayOf(0.50f, 0.58f, 0.27f, 0.50f, 0.54f, 0.34f, 0.50f, 0.52f, 0.40f)
        else -> {
            // The planets: the maps are small, so they come only a little closer than the orbit.
            val k = when (scene) {
                SuperScene.Saturn -> 0.75f
                SuperScene.Jupiter -> 1.15f
                SuperScene.Mars -> 0.93f
                SuperScene.Pluto -> 0.85f
                else -> 1.0f
            }
            floatArrayOf(0.26f, 0.31f, 0.20f * k, 0.50f, 0.78f, 0.52f * k, 0.50f, 0.72f, 0.66f * k)
        }
    }

    /** Fills [out] with the place of [scene] at [depth] (0 = lock screen … 1 = close-up). */
    fun pose(scene: SuperScene, depth: Float, out: SuperPose) {
        val d = depth.coerceIn(0f, 1f)
        val s = shape(scene)
        when {
            d <= FLY_END -> {
                val t = d / FLY_END
                // Down first, then across: a swoop, not a straight line.
                out.cx = lerp(s[0], s[3], easeInOutSine(t))
                out.cy = lerp(s[1], s[4], easeOutCubic(t))
                out.r = lerp(s[2], s[5], easeOutCubic(t))
            }
            d <= HOLD_END -> {
                out.cx = s[3]
                out.cy = s[4]
                out.r = s[5]
            }
            else -> {
                val e = easeInOutCubic((d - HOLD_END) / (1f - HOLD_END))
                out.cx = lerp(s[3], s[6], e)
                out.cy = lerp(s[4], s[7], e)
                out.r = lerp(s[5], s[8], e)
            }
        }
    }
}
