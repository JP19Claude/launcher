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

    /** Where the US flag is in the photo of the "Iwo Jima" scene (as fractions of the picture) and how far its light reaches. */
    const val FLAG_U = 0.05f
    const val FLAG_V = 0.23f
    const val FLAG_RU = 0.22f
    const val FLAG_RV = 0.17f

    /** The home flight in the time of the depth: moving up until [UP_END], turning in place until [ZOOM_START], then the zoom. */
    private const val UP_END = 0.25f
    const val ZOOM_START = 0.45f

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

    fun easeInOutCubic(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }

    /**
     * Start of the lock flight (cx, cy, r), then the lock screen's rest, the home position and the closest zoom of [scene].
     * Twelve numbers: L0, L1, H1, H2.
     */
    private fun shape(scene: SuperScene): FloatArray = when (scene) {
        SuperScene.Moon -> floatArrayOf(0.08f, 0.07f, 0.10f, 0.50f, 0.48f, 0.30f, 0.50f, 0.40f, 0.34f, 0.50f, 0.64f, 2.30f)
        SuperScene.Earth -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.48f, 0.28f, 0.50f, 0.40f, 0.31f, 0.50f, 0.62f, 1.00f)
        SuperScene.Geometry -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.50f, 0.27f, 0.50f, 0.45f, 0.30f, 0.50f, 0.46f, 0.40f)
        // The photos: the camera looks at a point of the picture (cx, cy as fractions of it) with a magnification r (1 = the
        // picture just fills the screen). The "Iwo Jima" photo starts on the flag in the upper left, pulls back to a view of the
        // flag, the Moon and the pole (the flag stays big), moves a little up and then comes close on the flag and the Moon.
        SuperScene.Flag -> floatArrayOf(0.10f, 0.22f, 2.6f, 0.24f, 0.40f, 1.50f, 0.24f, 0.35f, 1.60f, 0.17f, 0.33f, 2.0f)
        SuperScene.Photo -> floatArrayOf(0.15f, 0.15f, 1.9f, 0.50f, 0.50f, 1.06f, 0.50f, 0.47f, 1.10f, 0.50f, 0.50f, 1.35f)
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

    /**
     * The place of a photo of [photoW] × [photoH] pixels on a screen of [w] × [h] for the [pose] of the photo camera (see
     * [shape]) and the launcher's [zoom]; [shiftX] and [shiftY] move it in pixels (tilt, home page). [out] gets the
     * left edge, the top edge, the width and the height in pixels (y downwards). The photo never leaves a gap at the edges.
     */
    fun photoBox(w: Int, h: Int, photoW: Int, photoH: Int, pose: SuperPose, zoom: Float, shiftX: Float, shiftY: Float, out: FloatArray) {
        val cover = maxOf(w / photoW.toFloat(), h / photoH.toFloat())
        val scale = cover * maxOf(1f, pose.r * zoom)
        val dw = photoW * scale
        val dh = photoH * scale
        out[0] = (w / 2f - pose.cx * dw + shiftX).coerceIn(w - dw, 0f)
        out[1] = (h / 2f - pose.cy * dh + shiftY).coerceIn(h - dh, 0f)
        out[2] = dw
        out[3] = dh
    }
}
