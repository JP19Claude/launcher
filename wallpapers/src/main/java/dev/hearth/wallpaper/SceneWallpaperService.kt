package dev.hearth.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.hearth.launcher.wallpaper.SuperMotion
import dev.hearth.launcher.wallpaper.SuperRenderer
import kotlin.math.abs

/**
 * The live wallpaper of this app: its one scene (the Moon, Mars …) drawn by ZENITH's renderer, about 30 pictures a second
 * while it is seen. It reads the phone's tilt only then, moves with the home page when the launcher passes its
 * position on, and comes in from close when the screen wakes. The options are the ones set in the app.
 */
class SceneWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = getSharedPreferences(SceneConfig.PREFS, Context.MODE_PRIVATE)
        private var params = SceneConfig.read(this@SceneWallpaperService)
        private val renderer = SuperRenderer(params.scene, null, this@SceneWallpaperService.assets)
        private val motion = SuperMotion()
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var rawX = 0f
        private var rawY = 0f
        private var wakeAt = 0L
        private var sensing = false

        /** Something went wrong: the wallpaper stands still and black. */
        private var broken = false

        private inline fun guarded(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                broken = true
                visible = false
                runCatching { handler.removeCallbacksAndMessages(null) }
                runCatching { stopSensing() }
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            handler.post {
                guarded {
                    params = SceneConfig.read(this@SceneWallpaperService)
                    if (visible) {
                        if (params.tilt) startSensing() else stopSensing()
                    }
                    drawFrame()
                }
            }
        }

        private val frame = object : Runnable {
            override fun run() {
                guarded {
                    drawFrame()
                    if (visible) handler.postDelayed(this, 33L)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            // The pictures are made off the main thread; until they are there the stars show.
            Thread {
                try {
                    renderer.prepare()
                    handler.post { guarded { drawFrame() } }
                } catch (t: Throwable) {
                    broken = true
                }
            }.start()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacksAndMessages(null)
            stopSensing()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            guarded { drawFrame() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            guarded {
                if (visible) {
                    wakeAt = SystemClock.uptimeMillis()
                    if (!broken) startSensing()
                    handler.post(frame)
                } else {
                    stopSensing()
                }
            }
        }

        /** The launcher says which page it is on (0 … 1). */
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            this.xOffset = xOffset
        }

        private fun startSensing() {
            if (sensing || !params.tilt) return
            val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sensing = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        private fun stopSensing() {
            if (!sensing) return
            (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(this)
            sensing = false
            rawX = 0f
            rawY = 0f
        }

        override fun onSensorChanged(event: SensorEvent) {
            rawX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
            rawY = ((event.values[2] - 4f) / 6f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun drawFrame() {
            val holder = surfaceHolder
            if (width <= 0 || height <= 0) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                if (broken) {
                    canvas.drawColor(Color.BLACK)
                    return
                }
                val now = SystemClock.uptimeMillis()
                // Smoothed: the picture follows the phone, it doesn't jitter with it.
                motion.tiltX += (rawX - motion.tiltX) * 0.1f
                motion.tiltY += (rawY - motion.tiltY) * 0.1f
                val pan = (xOffset - 0.5f) * 2f
                motion.panX = if (params.zoomOnWake) pan else 0f
                // Waking the screen: the scene comes in from close, easing to rest.
                val wake = if (params.zoomOnWake) {
                    val k = ((now - wakeAt) / 1100f).coerceIn(0f, 1f)
                    val ease = 1f - (1f - k) * (1f - k) * (1f - k)
                    1f + 0.36f * (1f - ease)
                } else {
                    1f
                }
                motion.zoom = wake * (1f + 0.05f * abs(pan))
                renderer.draw(canvas, width, height, now, params, motion)
            } finally {
                canvas?.let { runCatching { holder.unlockCanvasAndPost(it) } }
            }
        }
    }
}
