package dev.hearth.launcher.wallpaper

import android.app.KeyguardManager
import android.content.Context
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/*
 * What moves a Super-Wallpaper, the way the Moon of Xiaomi's HyperOS moves: on the lock screen the body hangs small in
 * the upper left; the moment the phone is unlocked it flies in to the bottom centre, turns there, and then the camera
 * zooms in until single craters can be seen (the path itself is SuperCamera's); on locking it glides back. On the home
 * screen it follows the page you are on (it turns a little with every swipe) and the tilt of the phone; when the launcher
 * zooms the wallpaper out (the app drawer, folders) it shrinks with it. Everything is eased by time, never by frame, so it
 * is as smooth on a 120 Hz screen as on a 60 Hz one. One director for ZENITH's wallpaper, the single apps and the previews.
 */
class SuperDirector(context: Context, private val demo: Boolean = false) {

    /** What the renderer draws: updated by [frame]. */
    val motion = SuperMotion()

    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private var locked = false
    private var checkedAt = 0L
    private var holdOpenUntil = 0L

    private var from = 1f
    private var to = 1f
    private var startAt = 0L
    private var duration = 1L
    private var smoothGlide = false

    private var lastAt = 0L
    private var pan = 0f
    private var zoomOut = 0f
    private var zoomOutTarget = 0f
    private var drift = 0f
    private var demoStart = 0L

    private fun isLocked(): Boolean = try {
        keyguard?.isKeyguardLocked == true
    } catch (e: Exception) {
        false
    }

    /**
     * The wallpaper is seen (screen on, back from an app). On the lock screen it hangs in the lock pose; on the home
     * screen it takes the close-up – or, right after the screen was off ([flight]), flies in from the lock pose.
     */
    fun show(now: Long, flight: Boolean) {
        demoStart = now
        locked = isLocked()
        checkedAt = now
        holdOpenUntil = 0L
        lastAt = now
        if (locked) {
            setDepth(0f, now)
        } else if (flight) {
            setDepth(0f, now)
            glide(1f, now, UNLOCK_MS, smooth = false)
        } else {
            setDepth(1f, now)
        }
    }

    private fun setDepth(depth: Float, now: Long) {
        from = depth
        to = depth
        startAt = now
        duration = 1L
        motion.depth = depth
    }

    /** The lock screen is leaving (Android says so before it is gone): the flight begins. */
    fun unlocking(now: Long) {
        if (!locked) return
        locked = false
        // For a moment the keyguard still says "locked"; that is not a reason to fly back out.
        holdOpenUntil = now + 1500L
        glide(1f, now, UNLOCK_MS, smooth = false)
    }

    /** The launcher zooms the wallpaper out (0 = rest … 1 = fully out), for instance while the app drawer opens. */
    fun zoomOut(amount: Float) {
        zoomOutTarget = amount.coerceIn(0f, 1f)
    }

    private fun glide(target: Float, now: Long, ms: Long, smooth: Boolean) {
        from = motion.depth
        to = target
        startAt = now
        duration = ms
        smoothGlide = smooth
    }

    /**
     * Moves everything on to [now]. [xOffset] is the home page (0 … 1), [rawX] and [rawY] the tilt (-1 … 1). True while
     * something is still moving, so that the caller draws at the screen's full speed and can idle at half otherwise.
     */
    fun frame(now: Long, p: SuperParams, xOffset: Float, rawX: Float, rawY: Float): Boolean {
        val dt = (now - lastAt).coerceIn(1L, 100L).toFloat()
        lastAt = now
        var moving = false

        if (demo) {
            motion.depth = demoDepth(now - demoStart)
            moving = true
        } else if (!p.zoomOnWake) {
            // No flight, no page movement: the close-up, still.
            setDepth(1f, now)
        } else {
            if (now - checkedAt >= 100L) {
                checkedAt = now
                val lockedNow = isLocked()
                if (lockedNow != locked && !(lockedNow && now < holdOpenUntil)) {
                    locked = lockedNow
                    if (locked) glide(0f, now, LOCK_MS, smooth = true) else glide(1f, now, UNLOCK_MS, smooth = false)
                }
            }
            val k = ((now - startAt).toFloat() / duration).coerceIn(0f, 1f)
            val eased = if (smoothGlide) easeInOut(k) else k
            motion.depth = from + (to - from) * eased
            if (k < 1f) moving = true
        }

        val targetPan = if (p.zoomOnWake) (xOffset - 0.5f) * 2f else 0f
        pan += (targetPan - pan) * follow(dt, 160f)
        if (abs(targetPan - pan) > 0.003f) moving = true
        motion.panX = pan

        motion.tiltX += (rawX - motion.tiltX) * follow(dt, 120f)
        motion.tiltY += (rawY - motion.tiltY) * follow(dt, 120f)

        zoomOut += (zoomOutTarget - zoomOut) * follow(dt, 120f)
        if (abs(zoomOutTarget - zoomOut) > 0.003f) moving = true
        motion.zoom = 1f - 0.12f * zoomOut

        // The turning: the flight gives the body a push, and on the home screen it keeps on turning slowly.
        val depth = motion.depth
        val gate = ((depth - 0.2f) / 0.3f).coerceIn(0f, 1f)
        if (depth <= 0.001f) {
            drift = 0f
        } else {
            drift = (drift + dt / 1000f * 0.07f * p.speed * gate) % (2f * PI.toFloat())
        }
        motion.spin = SuperCamera.spinKick(depth) + drift * gate + pan * 0.9f
        return moving
    }

    /** How far to go towards the goal in [dt] ms when the motion catches up with a time constant of [tau] ms. */
    private fun follow(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    companion object {
        /** What Android's lock screen sends a wallpaper just before it is gone (the platform names it, but hides the constant). */
        const val COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway"

        /** Unlocking: flying in, turning, zooming – the whole flight. */
        const val UNLOCK_MS = 5200L

        /** Locking: back out, quicker. */
        const val LOCK_MS = 900L

        /** Slow, fast, slow. */
        fun easeInOut(k: Float): Float = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f

        /** The flight made visible, for the previews: lock screen, the flight, the close-up, back out – round and round (12 s). */
        fun demoDepth(timeMs: Long): Float {
            val t = (timeMs % 12000L).toInt()
            return when {
                t < 1800 -> 0f
                t < 7000 -> (t - 1800) / 5200f
                t < 9500 -> 1f
                t < 10300 -> 1f - easeInOut((t - 9500) / 800f)
                else -> 0f
            }
        }
    }
}
