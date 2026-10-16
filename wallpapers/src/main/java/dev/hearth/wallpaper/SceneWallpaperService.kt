package dev.hearth.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import dev.hearth.launcher.wallpaper.SuperDirector
import dev.hearth.launcher.wallpaper.SuperRenderer

/**
 * The live wallpaper of this app: its one scene (the Moon, Mars …) drawn by ZENITH's renderer, in step with the screen
 * while it moves and 30 pictures a second while it rests, only while it is seen. On the lock screen the body hangs small
 * and calm, unlocking flies the camera in and locking out again, the home page and the tilt move it – the way the super
 * wallpapers of Xiaomi's HyperOS do (see [SuperDirector]). The options are the ones set in the app.
 */
class SceneWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = getSharedPreferences(SceneConfig.PREFS, Context.MODE_PRIVATE)
        private var params = SceneConfig.read(this@SceneWallpaperService)
        private val renderer = SuperRenderer(params.scene, null, this@SceneWallpaperService.assets)
        private val director = SuperDirector(this@SceneWallpaperService)
        private val choreographer = Choreographer.getInstance()
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var rawX = 0f
        private var rawY = 0f
        private var sensing = false
        private var moving = true
        private var lastDrawNs = 0L

        /** Something went wrong: the wallpaper stands still and black. */
        private var broken = false

        private inline fun guarded(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                broken = true
                visible = false
                runCatching { handler.removeCallbacksAndMessages(null) }
                runCatching { choreographer.removeFrameCallback(frame) }
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
                    drawFrame(SystemClock.uptimeMillis())
                }
            }
        }

        /** Once per screen refresh: drawn at once while something moves, every other refresh when it rests. */
        private val frame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                guarded {
                    if (visible) {
                        val gap = frameTimeNanos - lastDrawNs
                        if (gap >= (if (moving) 14_000_000L else 30_000_000L)) {
                            lastDrawNs = frameTimeNanos
                            moving = drawFrame(frameTimeNanos / 1_000_000L)
                        }
                        choreographer.postFrameCallback(this)
                    }
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
                    handler.post { guarded { drawFrame(SystemClock.uptimeMillis()) } }
                } catch (t: Throwable) {
                    broken = true
                }
            }.start()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacksAndMessages(null)
            choreographer.removeFrameCallback(frame)
            stopSensing()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            guarded { drawFrame(SystemClock.uptimeMillis()) }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            choreographer.removeFrameCallback(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            choreographer.removeFrameCallback(frame)
            guarded {
                if (visible) {
                    director.show(SystemClock.uptimeMillis())
                    if (!broken) startSensing()
                    moving = true
                    lastDrawNs = 0L
                    choreographer.postFrameCallback(frame)
                } else {
                    stopSensing()
                }
            }
        }

        /** Android tells the lock screen's wallpaper that the phone is being unlocked: the camera dives in. */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == SuperDirector.COMMAND_KEYGUARD_GOING_AWAY) {
                guarded {
                    director.unlocking(SystemClock.uptimeMillis())
                    moving = true
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        /** The launcher zooms the wallpaper out (the app drawer, folders): it shrinks with it. */
        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            director.zoomOut(zoom)
            moving = true
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

        private fun lockCanvas(holder: SurfaceHolder): Canvas? = try {
            // The graphics chip draws the scaled pictures: smooth at the screen's full speed.
            holder.lockHardwareCanvas()
        } catch (e: Exception) {
            try {
                holder.lockCanvas()
            } catch (e2: Exception) {
                null
            }
        }

        /** One picture at [now] (ms); true while the scene is still moving. */
        private fun drawFrame(now: Long): Boolean {
            val holder = surfaceHolder
            if (width <= 0 || height <= 0) return false
            var canvas: Canvas? = null
            try {
                canvas = lockCanvas(holder) ?: return false
                if (broken) {
                    canvas.drawColor(Color.BLACK)
                    return false
                }
                val stillMoving = director.frame(now, params, xOffset, rawX, rawY)
                renderer.draw(canvas, width, height, now, params, director.motion)
                return stillMoving
            } finally {
                canvas?.let { runCatching { holder.unlockCanvasAndPost(it) } }
            }
        }
    }
}
