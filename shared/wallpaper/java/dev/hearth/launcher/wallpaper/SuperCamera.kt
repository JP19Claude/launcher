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
    private const val UP_END = 0.22f
    const val ZOOM_START = 0.44f

    /** The lock flight reaches the middle the first time at this share of its time (then it swings a little past and back). */
    const val LOCK_ARRIVES = 0.40f

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

    fun easeInOutCubic(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }

    /** Out like [easeOutCubic], but with a swing a little past the goal and back ([overshoot] 1.5 goes about 7 % too far). */
    fun easeOutBack(t: Float, overshoot: Float = 1.5f): Float {
        val k = t.coerceIn(0f, 1f) - 1f
        return 1f + (overshoot + 1f) * k * k * k + overshoot * k * k
    }

    /**
     * The zoom of the home flight: it starts with a punch, runs out softly, and just before the end swings about 4 % past the closest
     * point and settles back – energetic, but never a jolt. 0 → 1 (and a little over 1 on the way).
     */
    fun easeZoom(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        val body = 0.5f * easeInOutCubic(k) + 0.5f * easeOutCubic(k)
        val swing = kotlin.math.sin(Math.PI.toFloat() * ((k - 0.55f) / 0.45f).coerceIn(0f, 1f))
        // (squared: the swing starts and ends without a kink)
        return body + 0.07f * swing * swing
    }

    /**
     * Start of the lock flight (cx, cy, r), then the lock screen's rest, the home position and the closest zoom of [scene].
     * Twelve numbers: L0, L1, H1, H2.
     */
    private fun shape(scene: SuperScene): FloatArray = when (scene) {
        SuperScene.Moon -> floatArrayOf(0.08f, 0.07f, 0.10f, 0.50f, 0.48f, 0.30f, 0.50f, 0.40f, 0.34f, 0.50f, 0.64f, 2.30f)
        SuperScene.Earth -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.48f, 0.28f, 0.50f, 0.40f, 0.31f, 0.50f, 0.62f, 1.55f)
        SuperScene.Geometry -> floatArrayOf(0.08f, 0.07f, 0.09f, 0.50f, 0.50f, 0.27f, 0.50f, 0.45f, 0.30f, 0.50f, 0.46f, 0.40f)
        // The photos: the camera looks at a point of the picture (cx, cy as fractions of it) with a magnification r (1 = the
        // picture just fills the screen). The "Iwo Jima" photo starts close on the flag in the upper left, pulls back to the whole
        // picture – the flag, the Moon and the statue of the soldiers – and then, when unlocked, pushes in only so far that all
        // of it stays in view.
        SuperScene.Flag -> floatArrayOf(0.10f, 0.20f, 2.4f, 0.50f, 0.50f, 1.04f, 0.50f, 0.50f, 1.05f, 0.46f, 0.51f, 1.14f)
        SuperScene.Photo -> floatArrayOf(0.15f, 0.15f, 1.9f, 0.50f, 0.50f, 1.06f, 0.50f, 0.47f, 1.10f, 0.50f, 0.50f, 1.35f)
        else -> {
            // The planets: as close as the Moon. The solid ones have maps of 8192 × 4096 or 16384 × 8192 (Io's, the softest, not quite as close); the gas planets' maps are smaller and a fine
            // cloud structure is drawn on them, Saturn only comes as close as its ring still sweeps across the picture.
            val k = when (scene) {
                SuperScene.Saturn -> 0.75f
                SuperScene.Jupiter -> 1.15f
                SuperScene.Mars -> 0.93f
                SuperScene.Pluto -> 0.85f
                else -> 1.0f
            }
            val closest = when (scene) {
                SuperScene.Saturn -> 0.72f
                SuperScene.Jupiter, SuperScene.Neptune -> 1.70f
                SuperScene.Venus -> 1.60f
                SuperScene.Io -> 1.50f
                else -> 1.90f
            }
            // Saturn stays far enough away for its ring to sweep across the picture, with the planet's shadow on it.
            val lowest = if (scene == SuperScene.Saturn) 0.56f else 0.62f
            floatArrayOf(0.08f, 0.07f, 0.09f * k, 0.50f, 0.48f, 0.27f * k, 0.50f, 0.40f, 0.30f * k, 0.50f, lowest, closest)
        }
    }

    /**
     * Fills [out] with the place of [scene] when the lock flight is at [lockFly] (0 = just begun … 1 = in the middle) and
     * the home flight at [depth] (0 = still on the lock screen … 1 = the closest zoom). The home flight moves the body
     * from where the lock flight has put it, so unlocking before the lock flight has ended makes no jump.
     */
    fun pose(scene: SuperScene, lockFly: Float, depth: Float, out: SuperPose) {
        val s = shape(scene)
        // The body shoots in from the corner, swings a little past the middle (and grows a little past its size) and settles back.
        val f = easeOutBack(lockFly)
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
                val e = easeZoom((d - ZOOM_START) / (1f - ZOOM_START))
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
