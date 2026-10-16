package dev.hearth.launcher.wallpaper

import android.app.KeyguardManager
import android.content.Context
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/*
 * What moves a Super-Wallpaper, the way the Moon of Xiaomi's HyperOS moves: when the lock screen appears the body flies in
 * from the upper left corner to the middle of the screen; the moment the phone is unlocked it moves a little up and the
 * camera zooms in until single craters can be seen (the path itself is SuperCamera's); on locking it glides back. While it
 * flies up the body turns – fast, then slower – and lands on the place that was chosen (SuperPlaces): that place is then in
 * the middle of the zoom. On the home screen it follows the page you are on (it turns a little with every swipe) and the
 * tilt of the phone; when the launcher zooms the wallpaper out (the app drawer, folders) it shrinks with it. Everything is
 * eased by time, never by frame, so it is as smooth on a 120 Hz screen as on a 60 Hz one. One director for ZENITH's
 * wallpaper, the single apps and the previews.
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
    private var lockFlyStart = 0L
    private var lockFlyOn = false

    private var lastAt = 0L
    private var pan = 0f
    private var zoomOut = 0f
    private var zoomOutTarget = 0f
    private var demoStart = 0L
    private var demoLoop = -1L
    private var demoFlown = false

    // The turning of the body: from [spinFrom] to [spinTo] (and the leaning of the axis from [latFrom] to [latTo]) for
    // [turnMs] from [turnAt], slowing down; on top of it the slow [drift].
    private var spinFrom = 0f
    private var spinTo = 0f
    private var latFrom = -0.2f
    private var latTo = -0.2f
    private var turnAt = 0L
    private var turnMs = 1L
    private var drift = 0f
    private var pending = NONE
    private var placeKey = -1
    private var flightOn = true
    private var snapPan = true
    private val random = java.util.Random()

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
        // The home page may have changed while nothing was drawn: the body lands on its place from the page it is on now.
        snapPan = true
        if (locked) {
            // The lock screen: the body flies in from the upper left to the middle.
            setDepth(0f, now)
            lockFlyStart = now
            lockFlyOn = true
            motion.lockFly = 0f
            pending = LOCK
        } else if (flight) {
            // Home screen right after the screen was off: no lock flight, the home flight from the middle on.
            setDepth(0f, now)
            glide(1f, now, UNLOCK_MS, smooth = false)
            lockFlyOn = false
            motion.lockFly = 1f
            pending = FLIGHT_FROM_REST
        } else {
            setDepth(1f, now)
            lockFlyOn = false
            motion.lockFly = 1f
            pending = LAND
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
        pending = FLIGHT
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
            val t = (now - demoStart) % DEMO_MS
            val loop = (now - demoStart) / DEMO_MS
            if (loop != demoLoop) {
                demoLoop = loop
                demoFlown = false
                pending = LOCK
            }
            if (!demoFlown && t >= DEMO_UNLOCK_AT) {
                demoFlown = true
                pending = FLIGHT
            }
            motion.lockFly = (t / LOCK_FLY_MS.toFloat()).coerceIn(0f, 1f)
            motion.depth = if (t < DEMO_UNLOCK_AT) 0f else ((t - DEMO_UNLOCK_AT) / UNLOCK_MS.toFloat()).coerceIn(0f, 1f)
            moving = true
        } else if (!p.zoomOnWake) {
            // No flight, no page movement: the close-up, still – the body just there, on its place.
            setDepth(1f, now)
            lockFlyOn = false
            motion.lockFly = 1f
            if (pending != NONE || flightOn) pending = LAND
            flightOn = false
        } else {
            flightOn = true
            if (now - checkedAt >= 100L) {
                checkedAt = now
                val lockedNow = isLocked()
                if (lockedNow != locked && !(lockedNow && now < holdOpenUntil)) {
                    locked = lockedNow
                    if (locked) {
                        glide(0f, now, LOCK_MS, smooth = true)
                        pending = RELOCK
                    } else {
                        glide(1f, now, UNLOCK_MS, smooth = false)
                        pending = FLIGHT
                    }
                }
            }
            val k = ((now - startAt).toFloat() / duration).coerceIn(0f, 1f)
            val eased = if (smoothGlide) easeInOut(k) else k
            motion.depth = from + (to - from) * eased
            if (k < 1f) moving = true
            if (lockFlyOn) {
                val f = ((now - lockFlyStart).toFloat() / LOCK_FLY_MS).coerceIn(0f, 1f)
                motion.lockFly = f
                if (f >= 1f) lockFlyOn = false else moving = true
            }
        }

        val targetPan = if (p.zoomOnWake) (xOffset - 0.5f) * 2f else 0f
        if (snapPan) {
            pan = targetPan
            snapPan = false
        }
        pan += (targetPan - pan) * follow(dt, 160f)
        if (abs(targetPan - pan) > 0.003f) moving = true
        motion.panX = pan

        motion.tiltX += (rawX - motion.tiltX) * follow(dt, 120f)
        motion.tiltY += (rawY - motion.tiltY) * follow(dt, 120f)

        zoomOut += (zoomOutTarget - zoomOut) * follow(dt, 120f)
        if (abs(zoomOutTarget - zoomOut) > 0.003f) moving = true
        motion.zoom = 1f - 0.12f * zoomOut

        // The turning: slowly on the lock screen, then – with the home flight – on to the chosen place, where it only creeps;
        // the pages turn it a little further.
        if (pending == NONE && placeKey == -1) pending = if (motion.depth > 0.5f) LAND else LOCK
        val key = p.scene.ordinal * 64 + p.place
        when (pending) {
            LOCK -> restLook(p, now)
            LAND -> landAt(p, now)
            FLIGHT -> turnToPlace(p, now, FLIGHT_TURN_MS)
            FLIGHT_FROM_REST -> {
                restLook(p, now)
                turnToPlace(p, now, FLIGHT_TURN_MS)
            }
            RELOCK -> relockLook(p, now)
            else -> if (key != placeKey) {
                // The scene or the place was changed in the settings: a close-up turns to the new place, a lock screen just changes.
                if (motion.depth > 0.5f) turnToPlace(p, now, CHANGE_TURN_MS) else restLook(p, now)
            }
        }
        pending = NONE
        placeKey = key
        val depth = motion.depth
        val rate = if (depth < 0.02f) LOCK_TURN else if (depth > 0.98f) HOME_CREEP else 0f
        drift += dt / 1000f * rate * p.speed
        motion.spin = baseSpin(now) + pan * PAN_SPIN
        motion.lat = baseLat(now)
        if (turnK(now) < 1f) moving = true
        return moving
    }

    private fun turnK(now: Long): Float = ((now - turnAt).toFloat() / turnMs).coerceIn(0f, 1f)

    /** The turn of the body about its axis now, without the pages' share (radians). */
    private fun baseSpin(now: Long): Float = spinFrom + (spinTo - spinFrom) * SuperCamera.easeOutCubic(turnK(now)) + drift

    /** How the axis leans now: the latitude in the middle of the picture (radians). */
    private fun baseLat(now: Long): Float = latFrom + (latTo - latFrom) * SuperCamera.easeOutCubic(turnK(now))

    /** Stands still where it is, the axis leaning as it does on the lock screen. */
    private fun restLook(p: SuperParams, now: Long) {
        spinFrom = baseSpin(now)
        spinTo = spinFrom
        drift = 0f
        latFrom = SuperPlaces.restTilt(p.scene)
        latTo = latFrom
        turnAt = now
        turnMs = 1L
    }

    /** Back on the lock screen: the axis leans as before, without turning back. */
    private fun relockLook(p: SuperParams, now: Long) {
        val cur = baseSpin(now)
        val lat = baseLat(now)
        spinFrom = cur
        spinTo = cur
        drift = 0f
        latFrom = lat
        latTo = SuperPlaces.restTilt(p.scene)
        turnAt = now
        turnMs = LOCK_MS
    }

    /** The place of the choice in [p]: where the middle of the picture should be, as a spin and a lean. Null if the scene has none. */
    private fun placeOf(p: SuperParams): SuperPlace? = SuperPlaces.resolve(p.scene, p.place, random.nextFloat())

    /** The body turns on to its place (always on in the way it turns, at least half a turn) in [ms]. */
    private fun turnToPlace(p: SuperParams, now: Long, ms: Long) {
        val place = placeOf(p) ?: return restLook(p, now)
        val cur = baseSpin(now)
        val lat = baseLat(now)
        val twoPi = 2f * PI.toFloat()
        val target = place.spin - pan * PAN_SPIN
        var turn = (target - cur) % twoPi
        if (turn < 0f) turn += twoPi
        if (turn < PI.toFloat()) turn += twoPi
        spinFrom = cur
        spinTo = cur + turn
        drift = 0f
        latFrom = lat
        latTo = place.tilt.coerceIn(-MAX_LEAN, MAX_LEAN)
        turnAt = now
        turnMs = ms
    }

    /** The body on its place at once. */
    private fun landAt(p: SuperParams, now: Long) {
        val place = placeOf(p) ?: return restLook(p, now)
        spinFrom = place.spin - pan * PAN_SPIN
        spinTo = spinFrom
        drift = 0f
        latFrom = place.tilt.coerceIn(-MAX_LEAN, MAX_LEAN)
        latTo = latFrom
        turnAt = now
        turnMs = 1L
    }

    /** How far to go towards the goal in [dt] ms when the motion catches up with a time constant of [tau] ms. */
    private fun follow(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    companion object {
        /** What Android's lock screen sends a wallpaper just before it is gone (the platform names it, but hides the constant). */
        const val COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway"

        /** The lock flight: from the upper left corner to the middle. */
        const val LOCK_FLY_MS = 2400L

        /** Unlocking: moving up, turning, zooming – the whole home flight. */
        const val UNLOCK_MS = 4800L

        /** The preview's loop: the lock flight, a pause, the unlock at [DEMO_UNLOCK_AT], the zoom, a pause at the end. */
        private const val DEMO_UNLOCK_AT = 3600L
        private const val DEMO_MS = 3600L + 4800L + 3000L

        /** Locking: back out, quicker. */
        const val LOCK_MS = 900L

        /** The body turns to its place in the first part of the home flight, before the zoom. */
        private val FLIGHT_TURN_MS = (UNLOCK_MS * SuperCamera.ZOOM_START).toLong()

        /** A new place chosen while the close-up is on. */
        private const val CHANGE_TURN_MS = 1800L

        /** How the body turns on the lock screen and how it creeps once it has landed (radians a second). */
        private const val LOCK_TURN = 0.03f
        private const val HOME_CREEP = 0.0025f

        /** How much the pages turn the body (radians for a swipe across the whole width). */
        private const val PAN_SPIN = 0.9f

        /** The axis never leans further than this (radians): a view of a pole is as far as it goes. */
        private const val MAX_LEAN = 1.48f

        private const val NONE = 0
        private const val LOCK = 1
        private const val LAND = 2
        private const val FLIGHT = 3
        private const val FLIGHT_FROM_REST = 4
        private const val RELOCK = 5

        /** Slow, fast, slow. */
        fun easeInOut(k: Float): Float = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }
}
