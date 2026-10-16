package dev.hearth.launcher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/**
 * A Super-Wallpaper that runs: its own thread draws in step with the screen (the launcher is never held up), the graphics
 * chip does the drawing (SuperGl) and, where a phone can't, the processor does (SuperRenderer). The live wallpaper
 * services and the previews are only shells round this. [demo] makes the flight lock screen → home screen run in a loop
 * (for the previews) instead of waiting for the phone to be locked and unlocked. Every call may come from any thread.
 */
class SuperLive(
    private val context: Context,
    private val photoFile: File? = null,
    private val demo: Boolean = false,
    /** Something went wrong: [fatal] if the wallpaper had to stop drawing, else it only changed how it draws (to the processor's pictures). */
    private val onProblem: (Throwable, Boolean) -> Unit = { _, _ -> },
) : SensorEventListener {

    private val thread = HandlerThread("zenith-wallpaper", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val assets = context.applicationContext.assets
    private val director = SuperDirector(context, demo)

    @Volatile
    private var xOffset = 0.5f

    // Everything below is touched only on the drawing thread.
    private var choreographer: Choreographer? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var params = SuperParams()
    private var visible = false
    private var rawX = 0f
    private var rawY = 0f
    private var sensing = false
    private var moving = true
    private var lastDrawNs = 0L
    private var flightPending = true
    private var gl: SuperGl? = null
    private var glFailed = false
    private var canvasRenderer: SuperRenderer? = null
    private var blank = false
    private var receiver: BroadcastReceiver? = null

    init {
        handler.post {
            choreographer = Choreographer.getInstance()
            // Until the screen is first seen it hangs in the lock pose, where the flight begins.
            director.show(SystemClock.uptimeMillis(), true)
            // When the screen goes off, the next time the home screen shows the flight plays from the start.
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    flightPending = true
                }
            }
            receiver = r
            runCatching { context.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF), null, handler) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // What the shells tell it
    // ---------------------------------------------------------------------------------------------

    private fun post(block: () -> Unit) {
        handler.post {
            try {
                block()
            } catch (t: Throwable) {
                problem(t, false)
            }
        }
    }

    /** The surface is there (or has a new size). */
    fun attach(surface: Surface, w: Int, h: Int) = post {
        if (this.surface !== surface) {
            releaseRenderers()
            this.surface = surface
        }
        width = w
        height = h
        drawFrame(SystemClock.uptimeMillis())
    }

    /** The surface is going: the drawing lets go of it before this returns. */
    fun detach() {
        val latch = CountDownLatch(1)
        val queued = handler.post {
            try {
                choreographer?.removeFrameCallback(frameCallback)
                releaseRenderers()
                surface = null
            } catch (t: Throwable) {
                // gone anyway
            } finally {
                latch.countDown()
            }
        }
        if (queued) latch.await(1500, TimeUnit.MILLISECONDS)
    }

    /** The scene or an option changed. */
    fun setParams(p: SuperParams) = post {
        val before = params
        params = p
        if (before.scene != p.scene || before.photo != p.photo) changeScene()
        if (visible) {
            if (p.tilt) startSensing() else stopSensing()
        }
        moving = true
        drawFrame(SystemClock.uptimeMillis())
    }

    /** The wallpaper is seen (or no longer). */
    fun setVisible(v: Boolean) = post {
        visible = v
        choreographer?.removeFrameCallback(frameCallback)
        if (v) {
            director.show(SystemClock.uptimeMillis(), flightPending)
            flightPending = false
            if (params.tilt) startSensing()
            moving = true
            lastDrawNs = 0L
            choreographer?.postFrameCallback(frameCallback)
        } else {
            stopSensing()
        }
    }

    /** The launcher says which page it is on (0 … 1). */
    fun setOffset(x: Float) {
        xOffset = x
    }

    /** Android says the lock screen is going away: the flight begins. */
    fun unlocking() = post {
        director.unlocking(SystemClock.uptimeMillis())
        moving = true
    }

    /** The launcher zooms the wallpaper out (0 … 1): the app drawer, folders. */
    fun zoomOut(amount: Float) = post {
        director.zoomOut(amount)
        moving = true
    }

    /** Nothing is drawn: a black screen (ZENITH's safe mode). */
    fun setBlank(b: Boolean) = post {
        blank = b
        drawFrame(SystemClock.uptimeMillis())
    }

    /** Over: stops the drawing thread. Call [detach] first. */
    fun destroy() {
        handler.post {
            choreographer?.removeFrameCallback(frameCallback)
            stopSensing()
            receiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
            receiver = null
            releaseRenderers()
        }
        thread.quitSafely()
    }

    // ---------------------------------------------------------------------------------------------
    // The drawing thread
    // ---------------------------------------------------------------------------------------------

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            try {
                val gap = frameTimeNanos - lastDrawNs
                // In step with the screen while something moves, every other refresh when it rests.
                if (gap >= (if (moving) 14_000_000L else 30_000_000L)) {
                    lastDrawNs = frameTimeNanos
                    moving = drawFrame(frameTimeNanos / 1_000_000L)
                }
            } catch (t: Throwable) {
                problem(t, false)
            }
            if (visible) choreographer?.postFrameCallback(this)
        }
    }

    private fun problem(t: Throwable, fatal: Boolean) {
        runCatching { onProblem(t, fatal) }
    }

    private fun releaseRenderers() {
        gl?.stop()
        gl = null
        canvasRenderer = null
    }

    private fun changeScene() {
        val g = gl
        if (g != null) {
            if (!g.load(params.scene)) fallBack(IllegalStateException("scene ${params.scene} won't load on the graphics chip"))
        } else if (canvasRenderer != null) {
            canvasRenderer = SuperRenderer(params.scene, photoFile, assets).also { it.prepare() }
        }
    }

    /** The graphics chip can't do it (on this phone): from now on the processor draws. */
    private fun fallBack(why: Throwable) {
        gl?.stop()
        gl = null
        glFailed = true
        problem(why, false)
        if (canvasRenderer == null && surface != null) {
            canvasRenderer = SuperRenderer(params.scene, photoFile, assets).also { it.prepare() }
        }
    }

    private fun ensureRenderer(s: Surface) {
        if (gl != null || canvasRenderer != null) return
        if (!glFailed) {
            val g = SuperGl(assets, photoFile)
            if (g.start(s) && g.load(params.scene)) {
                gl = g
                return
            }
            g.stop()
            glFailed = true
            problem(IllegalStateException("OpenGL ES 3 is not available"), false)
        }
        canvasRenderer = SuperRenderer(params.scene, photoFile, assets).also { it.prepare() }
    }

    /** One picture at [now] (ms); true while the scene is still moving. */
    private fun drawFrame(now: Long): Boolean {
        val s = surface ?: return false
        if (!s.isValid || width <= 0 || height <= 0) return false
        if (blank) {
            releaseRenderers()
            drawBlack(s)
            return false
        }
        ensureRenderer(s)
        if (demo) rawX = sin(now / 2600f) * 0.4f
        val stillMoving = director.frame(now, params, xOffset, rawX, rawY)
        val g = gl
        if (g != null) {
            try {
                g.draw(width, height, now, params, director.motion)
                if (!g.looksRight()) fallBack(IllegalStateException("the graphics chip drew nothing"))
            } catch (t: Throwable) {
                fallBack(t)
            }
        } else {
            val r = canvasRenderer
            if (r != null) {
                try {
                    val c = s.lockHardwareCanvas()
                    try {
                        r.draw(c, width, height, now, params, director.motion)
                    } finally {
                        s.unlockCanvasAndPost(c)
                    }
                } catch (t: Throwable) {
                    blank = true
                    problem(t, true)
                }
            }
        }
        return stillMoving
    }

    private fun drawBlack(s: Surface) {
        try {
            val c = s.lockCanvas(null)
            c.drawColor(0xFF000000.toInt())
            s.unlockCanvasAndPost(c)
        } catch (t: Throwable) {
            // nothing to show it on
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The tilt
    // ---------------------------------------------------------------------------------------------

    private fun startSensing() {
        if (sensing || demo) return
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensing = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, handler)
    }

    private fun stopSensing() {
        if (!sensing) return
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(this)
        sensing = false
        rawX = 0f
        rawY = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        rawX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
        rawY = ((event.values[2] - 4f) / 6f).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
