package dev.hearth.launcher.wallpaper

import android.app.KeyguardManager
import android.content.Context
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/*
 * What moves a Super-Wallpaper, the way the super wallpapers of Xiaomi's HyperOS move: on the lock screen the whole planet
 * hangs small and calm; the moment the phone is unlocked the camera dives in towards the surface – very fast at first,
 * then a long soft landing –, and on locking it glides back out. On the home screen the planet follows the page you are
 * on (it turns a little with every swipe) and the tilt of the phone; when the launcher zooms the wallpaper out (the app
 * drawer, folders) it shrinks with it. Everything is eased by time, never by frame, so it is as smooth on a 120 Hz screen
 * as on a 60 Hz one. One director for ZENITH's wallpaper and for the single apps.
 */
class SuperDirector(context: Context) {

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

    private var lastAt = 0L
    private var pan = 0f
    private var zoomOut = 0f
    private var zoomOutTarget = 0f

    private fun isLocked(): Boolean = try {
        keyguard?.isKeyguardLocked == true
    } catch (e: Exception) {
        false
    }

    /** The wallpaper is seen (screen on, back from an app): it takes the state the phone is in, without moving. */
    fun show(now: Long) {
        locked = isLocked()
        checkedAt = now
        holdOpenUntil = 0L
        val depth = if (locked) 0f else 1f
        from = depth
        to = depth
        startAt = now
        lastAt = now
        motion.depth = depth
    }

    /** The lock screen is leaving (Android says so before it is gone): the camera dives in. */
    fun unlocking(now: Long) {
        if (!locked) return
        locked = false
        // For a moment the keyguard still says "locked"; that is not a reason to fly back out.
        holdOpenUntil = now + 1500L
        glide(1f, now, UNLOCK_MS)
    }

    /** The launcher zooms the wallpaper out (0 = rest … 1 = fully out), for instance while the app drawer opens. */
    fun zoomOut(amount: Float) {
        zoomOutTarget = amount.coerceIn(0f, 1f)
    }

    private fun glide(target: Float, now: Long, ms: Long) {
        from = motion.depth
        to = target
        startAt = now
        duration = ms
    }

    /**
     * Moves everything on to [now]. [xOffset] is the home page (0 … 1), [rawX] and [rawY] the tilt (-1 … 1). True while
     * something is still moving, so that the caller draws at the screen's full speed and can idle at half otherwise.
     */
    fun frame(now: Long, p: SuperParams, xOffset: Float, rawX: Float, rawY: Float): Boolean {
        val dt = (now - lastAt).coerceIn(1L, 100L).toFloat()
        lastAt = now
        var moving = false

        if (!p.zoomOnWake) {
            // No lock/unlock flight, no page movement: the home screen's framing, still.
            from = 1f
            to = 1f
            motion.depth = 1f
        } else {
            if (now - checkedAt >= 100L) {
                checkedAt = now
                val lockedNow = isLocked()
                if (lockedNow != locked && !(lockedNow && now < holdOpenUntil)) {
                    locked = lockedNow
                    glide(if (locked) 0f else 1f, now, if (locked) LOCK_MS else UNLOCK_MS)
                }
            }
            val k = ((now - startAt).toFloat() / duration).coerceIn(0f, 1f)
            val eased = if (to > from) easeOutQuart(k) else easeInOut(k)
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
        return moving
    }

    /** How far to go towards the goal in [dt] ms when the motion catches up with a time constant of [tau] ms. */
    private fun follow(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    companion object {
        /** What Android's lock screen sends a wallpaper just before it is gone (the platform names it, but hides the constant). */
        const val COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway"

        /** Unlocking: fast dive, soft landing. */
        const val UNLOCK_MS = 1100L

        /** Locking: out again, a little quicker. */
        const val LOCK_MS = 700L

        /** Starts fast and slows for a long time. */
        fun easeOutQuart(k: Float): Float = 1f - (1f - k).pow(4)

        /** Slow, fast, slow. */
        fun easeInOut(k: Float): Float = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f

        /** The flight made visible, for the previews: lock screen, dive, home screen, back out – round and round (7 s). */
        fun demoDepth(timeMs: Long): Float {
            val t = (timeMs % 7000L).toInt()
            return when {
                t < 1800 -> 0f
                t < 2900 -> easeOutQuart((t - 1800) / 1100f)
                t < 5200 -> 1f
                t < 5900 -> 1f - easeInOut((t - 5200) / 700f)
                else -> 0f
            }
        }
    }
}
