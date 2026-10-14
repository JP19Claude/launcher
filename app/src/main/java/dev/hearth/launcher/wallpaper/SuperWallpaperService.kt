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
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.hearth.launcher.data.SettingsRepository
import kotlin.math.abs

/**
 * ZENITH's Super-Wallpaper as a real live wallpaper: it can be chosen as the phone's background (and shows
 * behind any launcher and on the lock screen where Android lets a live wallpaper there). The scene and its
 * options are the ones set in ZENITH's settings ("Super-Wallpaper") – a change is taken up at once. It draws
 * only while it is seen (about 30 pictures a second), reads the tilt only then, and moves with the home
 * page when the launcher passes the page's position on.
 */
class SuperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SuperEngine()

    private inner class SuperEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        private var params = SuperPrefs.read(this@SuperWallpaperService)
        private var renderer = SuperRenderer(params.scene)
        private val motion = SuperMotion()
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var rawX = 0f
        private var rawY = 0f
        private var wakeAt = 0L
        private var sensing = false

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("super")) handler.post { reload() }
        }

        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 33L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            prepare(renderer)
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
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) {
                wakeAt = SystemClock.uptimeMillis()
                startSensing()
                handler.post(frame)
            } else {
                stopSensing()
            }
        }

        /** The launcher says which page it is on (0 … 1). */
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            this.xOffset = xOffset
        }

        /** The scene or an option was changed in ZENITH's settings. */
        private fun reload() {
            val before = params.scene
            params = SuperPrefs.read(this@SuperWallpaperService)
            if (params.scene != before) {
                renderer = SuperRenderer(params.scene)
                prepare(renderer)
            }
            if (visible) {
                if (params.tilt) startSensing() else stopSensing()
            }
            drawFrame()
        }

        /** The planet's texture is made off the main thread; until it is there the stars show. */
        private fun prepare(r: SuperRenderer) {
            Thread {
                r.prepare()
                handler.post { drawFrame() }
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
