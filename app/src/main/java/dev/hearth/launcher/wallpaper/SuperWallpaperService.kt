package dev.hearth.launcher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import dev.hearth.launcher.crash.CrashGuard
import dev.hearth.launcher.data.SettingsRepository

/**
 * ZENITH's Super-Wallpaper as a real live wallpaper: it can be chosen as the phone's background (and shows
 * behind any launcher and on the lock screen where Android lets a live wallpaper there). The scene and its
 * options are the ones set in ZENITH's settings ("Super-Wallpaper") – a change is taken up at once. It draws
 * only while it is seen (in step with the screen while it moves, 30 pictures a second while it rests), reads the
 * tilt only then, and moves with the home page when the launcher passes the page's position on. On the lock
 * screen the planet hangs small and calm, unlocking flies the camera in and locking out again (see [SuperDirector]).
 */
class SuperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SuperEngine()

    private inner class SuperEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        private var params = SuperPrefs.read(this@SuperWallpaperService)
        private var renderer = SuperRenderer(params.scene, SuperPhoto.file(this@SuperWallpaperService), this@SuperWallpaperService.assets)
        private val director = SuperDirector(this@SuperWallpaperService)
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

        /** Something went wrong (or ZENITH runs in the safe mode): the wallpaper stands still and black, the launcher lives on. */
        private var broken = CrashGuard.safe

        /** An error here must never take ZENITH down with it: it is written down for the next start and the wallpaper stops. */
        private inline fun guarded(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                broken = true
                visible = false
                runCatching { handler.removeCallbacksAndMessages(null) }
                runCatching { choreographer.removeFrameCallback(frame) }
                runCatching { stopSensing() }
                CrashGuard.note(this@SuperWallpaperService, t)
            }
        }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("super")) handler.post { guarded { reload() } }
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
            guarded { if (!broken) prepare(renderer) }
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

        /** The scene or an option was changed in ZENITH's settings. */
        private fun reload() {
            val before = params
            params = SuperPrefs.read(this@SuperWallpaperService)
            if (params.scene != before.scene || params.photo != before.photo) {
                renderer = SuperRenderer(params.scene, SuperPhoto.file(this@SuperWallpaperService), this@SuperWallpaperService.assets)
                prepare(renderer)
            }
            if (visible) {
                if (params.tilt) startSensing() else stopSensing()
            }
            drawFrame(SystemClock.uptimeMillis())
        }

        /** The planet's texture is made off the main thread; until it is there the stars show. */
        private fun prepare(r: SuperRenderer) {
            Thread {
                try {
                    r.prepare()
                    handler.post { guarded { drawFrame(SystemClock.uptimeMillis()) } }
                } catch (t: Throwable) {
                    broken = true
                    CrashGuard.note(this@SuperWallpaperService, t)
                }
            }.start()
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
            guarded {
                rawX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                rawY = ((event.values[2] - 4f) / 6f).coerceIn(-1f, 1f)
            }
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

    companion object {
        private var activeAt = 0L
        private var active = false

        /** Is this the phone's live wallpaper right now? (Asked now and then, not on every call.) */
        fun isActive(context: Context): Boolean {
            val now = SystemClock.uptimeMillis()
            if (now - activeAt > 3000L) {
                activeAt = now
                active = runCatching {
                    val info = WallpaperManager.getInstance(context).wallpaperInfo
                    info != null && info.packageName == context.packageName && info.serviceInfo.name == SuperWallpaperService::class.java.name
                }.getOrDefault(false)
            }
            return active
        }

        /** Asked fresh (the settings page, after the wallpaper was changed). */
        fun isActiveNow(context: Context): Boolean {
            activeAt = 0L
            return isActive(context)
        }
    }
}
