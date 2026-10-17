package dev.hearth.launcher.wallpaper

import android.app.KeyguardManager
import android.content.Context
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * What moves a Super-Wallpaper, the way the Moon of Xiaomi's HyperOS moves – and a good deal more lively: when the lock screen
 * appears the body shoots in from the upper left corner, swings a little past the middle and settles; the moment the phone is
 * unlocked it moves a little up, turns, and the camera punches in until single craters can be seen (the path itself is
 * SuperCamera's); on locking it glides back. While it flies up the body turns – fast, then slower – and lands on the place that was
 * chosen (SuperPlaces). On the home screen it follows the page you are on (the springs swing a little past and back, so every swipe
 * has some life in it, and the body turns with it) and the tilt of the phone; when the launcher zooms the wallpaper out (the app
 * drawer, folders) it shrinks with it – and turns. Everything that happens gives the scene a "Schwung": a flash, a ring of light running
 * out from the body, and for a moment time itself runs faster (the clouds, the rings, the moons, the stars). Everything is eased by time,
 * never by frame, so it is as smooth on a 120 Hz screen as on a 60 Hz one. One director for ZENITH's wallpaper, the single apps, the
 * previews and the always-on screen ([ambient]: calm, dim, and moving a few pixels now and then against burn-in).
 */
class SuperDirector(context: Context, private val demo: Boolean = false, private val ambient: Boolean = false) {

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
    private var landed = true

    private var lastAt = 0L
    private var pan = 0f
    private var zoomOutTarget = 0f

    /**
     * A spring: it runs towards its goal with a soft start, and with a [zeta] below 1 it swings a little past it and back (the lower, the
     * more). The step is the exact solution, not an approximation per frame, so it takes the same path at any frame rate.
     */
    private class Spring {
        var x = 0f
        var v = 0f

        fun snap(to: Float) {
            x = to
            v = 0f
        }

        /** Moves on by [dtMs] towards [target]; [omega] is how quick it is (1/s: 8 settles in about half a second). */
        fun step(target: Float, dtMs: Float, omega: Float, zeta: Float = 1f): Float {
            val t = dtMs / 1000f
            val d = x - target
            if (zeta >= 0.999f) {
                val e = exp(-omega * t)
                val k = (v + omega * d) * t
                x = target + (d + k) * e
                v = (v - omega * k) * e
            } else {
                val za = zeta * omega
                val wd = omega * sqrt(1f - zeta * zeta)
                val e = exp(-za * t)
                val c = cos(wd * t)
                val s = sin(wd * t)
                val b = (v + za * d) / wd
                x = target + e * (d * c + b * s)
                v = e * ((b * wd - za * d) * c - (d * wd + za * b) * s)
            }
            return x
        }

        fun settled(target: Float): Boolean = abs(x - target) < 0.003f && abs(v) < 0.02f
    }

    private val panSpring = Spring()
    private val tiltXSpring = Spring()
    private val tiltYSpring = Spring()
    private val zoomSpring = Spring()
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

    // The Schwung: time runs faster for a moment ([boost], 1/s, fades by itself), a flash and a ring of light go out from the body, and a
    // flick makes it turn once.
    private var boost = 0f
    private var flash = 0f
    private var kickAt = -1L
    private var warp = 0f
    private var lastLogR = Float.NaN
    private var ambientAt = -1L
    private val tmpPose = SuperPose()

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
        lastAt = now
        if (ambient) {
            ambientAt = now
            pending = LOCK
            return
        }
        locked = isLocked()
        checkedAt = now
        holdOpenUntil = 0L
        // The home page may have changed while nothing was drawn: the body lands on its place from the page it is on now.
        snapPan = true
        if (locked) {
            // The lock screen: the body flies in from the upper left to the middle.
            setDepth(0f, now)
            lockFlyStart = now
            lockFlyOn = true
            landed = false
            motion.lockFly = 0f
            pending = LOCK
        } else if (flight) {
            // Home screen right after the screen was off: no lock flight, the home flight from the middle on.
            setDepth(0f, now)
            glide(1f, now, UNLOCK_MS, smooth = false)
            lockFlyOn = false
            landed = true
            motion.lockFly = 1f
            pending = FLIGHT_FROM_REST
        } else {
            // Back on the home screen from an app: the close-up is there at once, with a small flash and a ring of light as a welcome.
            setDepth(1f, now)
            lockFlyOn = false
            landed = true
            motion.lockFly = 1f
            pending = LAND
            burst(0.22f, WELCOME_BOOST)
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
        if (ambient || !locked) return
        locked = false
        // For a moment the keyguard still says "locked"; that is not a reason to fly back out.
        holdOpenUntil = now + 1500L
        glide(1f, now, UNLOCK_MS, smooth = false)
        pending = FLIGHT
        burst(0.55f, UNLOCK_BOOST)
    }

    /** The launcher zooms the wallpaper out (0 = rest … 1 = fully out), for instance while the app drawer opens. */
    fun zoomOut(amount: Float) {
        zoomOutTarget = amount.coerceIn(0f, 1f)
    }

    /** A new scene was chosen (or a new photo): it comes in the way it does when the screen lights up. */
    fun introduce(now: Long) {
        if (ambient || demo) return
        show(now, true)
        burst(0.80f, INTRO_BOOST)
    }

    /** Someone tapped the wallpaper (the launcher says so): a flash, a ring of light, the clouds race, and – on the far view – the body turns once. */
    fun tap(now: Long) {
        if (ambient) return
        burst(0.45f, TAP_BOOST)
        if (motion.depth < 0.5f && kickAt < 0L) kickAt = now
    }

    /** A flash of [level] and time running [boostLevel] times faster for a moment. */
    private fun burst(level: Float, boostLevel: Float) {
        flash = max(flash, level)
        boost = max(boost, boostLevel)
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
        if (ambient) return ambientFrame(now, p, dt)
        var moving = false

        if (demo) {
            val t = (now - demoStart) % DEMO_MS
            val loop = (now - demoStart) / DEMO_MS
            if (loop != demoLoop) {
                demoLoop = loop
                demoFlown = false
                landed = false
                pending = LOCK
            }
            if (!demoFlown && t >= DEMO_UNLOCK_AT) {
                demoFlown = true
                pending = FLIGHT
                burst(0.55f, UNLOCK_BOOST)
            }
            motion.lockFly = (t / LOCK_FLY_MS.toFloat()).coerceIn(0f, 1f)
            if (!landed && motion.lockFly >= SuperCamera.LOCK_ARRIVES) {
                landed = true
                burst(0.30f, LAND_BOOST)
            }
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
                        burst(0.25f, LOCK_BOOST)
                    } else {
                        glide(1f, now, UNLOCK_MS, smooth = false)
                        pending = FLIGHT
                        burst(0.55f, UNLOCK_BOOST)
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
                // The moment the body first reaches the middle it lands with a thump: a flash and a ring of light.
                if (!landed && f >= SuperCamera.LOCK_ARRIVES) {
                    landed = true
                    burst(0.30f, LAND_BOOST)
                }
                if (f >= 1f) lockFlyOn = false else moving = true
            }
        }

        val targetPan = if (p.zoomOnWake) (xOffset - 0.5f) * 2f else 0f
        if (snapPan) {
            panSpring.snap(targetPan)
            snapPan = false
        }
        // The pages, the tilt and the launcher's zoom move the picture on springs: a quick start, a swing a little past the goal, a soft landing.
        pan = panSpring.step(targetPan, dt, 11f, 0.72f)
        if (!panSpring.settled(targetPan)) moving = true
        motion.panX = pan

        motion.tiltX = tiltXSpring.step(rawX, dt, 11f, 0.8f)
        motion.tiltY = tiltYSpring.step(rawY, dt, 11f, 0.8f)
        if (!tiltXSpring.settled(rawX) || !tiltYSpring.settled(rawY)) moving = true

        val zoomOut = zoomSpring.step(zoomOutTarget, dt, 12f, 0.55f)
        if (!zoomSpring.settled(zoomOutTarget)) moving = true

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
                if (motion.depth > 0.5f) {
                    turnToPlace(p, now, CHANGE_TURN_MS)
                    burst(0.35f, TAP_BOOST)
                } else {
                    restLook(p, now)
                }
            }
        }
        pending = NONE
        placeKey = key
        val depth = motion.depth
        val rate = if (depth < 0.02f) LOCK_TURN else if (depth > 0.98f) HOME_CREEP else 0f
        drift += dt / 1000f * rate * p.speed
        motion.spin = baseSpin(now) + pan * PAN_SPIN + zoomSpring.x * ZOOM_SPIN + kickExtra(now)
        motion.lat = baseLat(now)
        if (turnK(now) < 1f || kickAt >= 0L) moving = true

        if (energy(now, p, dt, zoomOut)) moving = true
        return moving
    }

    /**
     * The Schwung and the sky's reactions: the clock (time runs on, faster while pages or the launcher's zoom move and for a while after an
     * event), the flash, the ring of light, how fast the camera flies (the stars stretch) and how far the sun has swept. True while any of
     * it is still going on.
     */
    private fun energy(now: Long, p: SuperParams, dt: Float, zoomOut: Float): Boolean {
        val s = dt / 1000f
        val whoosh = abs(panSpring.v) * 0.9f + abs(zoomSpring.v) * 3f
        if (whoosh > boost) boost = whoosh
        val decay = exp(-BOOST_DECAY * s)
        motion.clock += (BASE_RATE * p.speed * s + boost * (1f - decay) / BOOST_DECAY).toDouble()
        boost *= decay
        if (boost < 0.15f) boost = 0f

        flash *= exp(-FLASH_DECAY * s)
        if (flash < 0.004f) flash = 0f
        motion.flash = flash
        // A small thump: the body swells a little with the flash, and shrinks with the launcher's zoom.
        motion.zoom = (1f - 0.14f * zoomOut) * (1f + 0.04f * flash)

        // The sun comes round while the body lands (its light sweeps across the surface).
        motion.sunSweep = -SUN_SWEEP * (1f - SuperCamera.easeOutCubic(motion.lockFly))

        // How fast the picture grows or shrinks: the stars stretch to streaks.
        SuperCamera.pose(p.scene, motion.lockFly, motion.depth, tmpPose)
        val lr = ln(max(tmpPose.r, 0.01f))
        if (lastLogR.isNaN()) lastLogR = lr
        val rate = abs(lr - lastLogR) / max(s, 0.001f)
        lastLogR = lr
        warp += (min(rate * 0.30f, 1f) - warp) * (1f - exp(-10f * s))
        if (warp < 0.004f) warp = 0f
        motion.warp = warp
        return boost > 0f || flash > 0f || warp > 0f
    }

    /** The always-on screen: the body hangs in the far view, turns slowly, and wanders a few pixels in a slow circle against burn-in. */
    private fun ambientFrame(now: Long, p: SuperParams, dt: Float): Boolean {
        val s = dt / 1000f
        if (ambientAt < 0L) {
            ambientAt = now
            pending = LOCK
        }
        motion.clock += (AMBIENT_RATE * p.speed * s).toDouble()
        motion.lockFly = 1f
        motion.depth = 0f
        motion.zoom = 1f
        motion.ambient = true
        val t = motion.clock.toFloat()
        motion.panX = 0.30f * sin(t * 0.37f)
        motion.tiltX = 0f
        motion.tiltY = 0.45f * cos(t * 0.29f)
        if (pending != NONE) {
            restLook(p, now)
            pending = NONE
        }
        drift += s * AMBIENT_TURN * p.speed
        motion.spin = baseSpin(now)
        motion.lat = baseLat(now)
        motion.flash = 0f
        motion.warp = 0f
        motion.sunSweep = 0f
        // It fades in from black.
        val fade = ((now - ambientAt).toFloat() / AMBIENT_FADE_MS).coerceIn(0f, 1f)
        motion.dim = p.aodDim.coerceIn(0.05f, 0.8f) * fade * fade * (3f - 2f * fade)
        return fade < 1f
    }

    private fun turnK(now: Long): Float = ((now - turnAt).toFloat() / turnMs).coerceIn(0f, 1f)

    /** The flick of a tap: once round, quickly slowing down – 0 when none is on. */
    private fun kickExtra(now: Long): Float {
        if (kickAt < 0L) return 0f
        val k = (now - kickAt).toFloat() / KICK_MS
        if (k >= 1f) {
            kickAt = -1L
            return 0f
        }
        // Once round is the same picture as none: it ends where it began.
        return 2f * PI.toFloat() * easeTurn(k)
    }

    /** The turn of the body about its axis now, without the pages' share (radians). */
    private fun baseSpin(now: Long): Float = spinFrom + (spinTo - spinFrom) * easeTurn(turnK(now)) + drift

    /** How the axis leans now: the latitude in the middle of the picture (radians). */
    private fun baseLat(now: Long): Float = latFrom + (latTo - latFrom) * easeTurn(turnK(now))

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

    companion object {
        /** What Android's lock screen sends a wallpaper just before it is gone (the platform names it, but hides the constant). */
        const val COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway"

        /** What a launcher sends the wallpaper when someone taps an empty place of the home screen (WallpaperManager.COMMAND_TAP). */
        const val COMMAND_TAP = "android.wallpaper.tap"

        /** The lock flight: from the upper left corner to the middle, with a swing past it. */
        const val LOCK_FLY_MS = 1500L

        /** Unlocking: moving up, turning, zooming – the whole home flight. */
        const val UNLOCK_MS = 3500L

        /** The preview's loop: the lock flight, a pause, the unlock at [DEMO_UNLOCK_AT], the zoom, a pause at the end. */
        private const val DEMO_UNLOCK_AT = 2900L
        private const val DEMO_MS = 2900L + 3500L + 2600L

        /** Locking: back out, quicker. */
        const val LOCK_MS = 700L

        /** The body turns to its place in the first part of the home flight, before the zoom. */
        private val FLIGHT_TURN_MS = (UNLOCK_MS * SuperCamera.ZOOM_START).toLong()

        /** A new place chosen while the close-up is on. */
        private const val CHANGE_TURN_MS = 1100L

        /** How the body turns on the lock screen and how it creeps once it has landed (radians a second; the speed setting multiplies them). */
        private const val LOCK_TURN = 0.07f
        private const val HOME_CREEP = 0.008f

        /** How much the pages turn the body (radians for a swipe across the whole width) and the launcher's zoom (the app drawer fully open). */
        private const val PAN_SPIN = 1.4f
        private const val ZOOM_SPIN = 1.6f

        /** The axis never leans further than this (radians): a view of a pole is as far as it goes. */
        private const val MAX_LEAN = 1.48f

        /** The scene's clock runs this much faster than real time at the speed setting 1 (and the always-on screen's calmer). */
        private const val BASE_RATE = 1.6f
        private const val AMBIENT_RATE = 0.5f
        private const val AMBIENT_TURN = 0.02f
        private const val AMBIENT_FADE_MS = 1600f

        /** The Schwung: how many times faster time runs at the start of an event, and how quickly that dies away (1/s). */
        private const val UNLOCK_BOOST = 9f
        private const val LAND_BOOST = 4f
        private const val LOCK_BOOST = 5f
        private const val INTRO_BOOST = 9f
        private const val TAP_BOOST = 6f
        private const val WELCOME_BOOST = 4f
        private const val BOOST_DECAY = 1.15f
        private const val FLASH_DECAY = 3.2f

        /** The flick of a tap, and how far the sun sweeps while the body lands (radians). */
        private const val KICK_MS = 1500f
        private const val SUN_SWEEP = 0.45f

        private const val NONE = 0
        private const val LOCK = 1
        private const val LAND = 2
        private const val FLIGHT = 3
        private const val FLIGHT_FROM_REST = 4
        private const val RELOCK = 5

        /** A turn: it starts at once and slows down all the way (twice as fast as its average at the start – a whirl, not a blur). */
        fun easeTurn(k: Float): Float {
            val r = 1f - k.coerceIn(0f, 1f)
            return 1f - r * r
        }

        /** Slow, fast, slow. */
        fun easeInOut(k: Float): Float = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f
    }
}
