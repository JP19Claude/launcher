package dev.hearth.launcher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.hearth.launcher.crash.CrashGuard
import dev.hearth.launcher.data.SettingsRepository

/**
 * ZENITH's Super-Wallpaper as a real live wallpaper: it can be chosen as the phone's background (and shows
 * behind any launcher and on the lock screen where Android lets a live wallpaper there). The scene and its
 * options are the ones set in ZENITH's settings ("Super-Wallpaper") – a change is taken up at once. The drawing
 * is [SuperLive]'s: its own thread, in step with the screen while it moves, drawn by the graphics chip (the
 * processor on phones that can't). On the lock screen the body hangs small, unlocking flies it in and the camera
 * zooms (see [SuperDirector]); it moves with the home page and the tilt of the phone.
 */
class SuperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SuperEngine()

    private inner class SuperEngine : Engine() {

        private val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        private var live: SuperLive? = null

        /** The wallpaper's own trouble must never take ZENITH down with it: only a stop of the drawing is written down for the next start. */
        private val problem: (Throwable, Boolean) -> Unit = { t, fatal -> if (fatal) CrashGuard.note(this@SuperWallpaperService, t) }

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("super")) live?.setParams(SuperPrefs.read(this@SuperWallpaperService))
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val l = SuperLive(this@SuperWallpaperService, SuperPhoto.file(this@SuperWallpaperService), onProblem = problem)
            l.setParams(SuperPrefs.read(this@SuperWallpaperService))
            // In ZENITH's safe mode the wallpaper stands still and black, the launcher lives on.
            if (CrashGuard.safe) l.setBlank(true)
            live = l
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            live?.detach()
            live?.destroy()
            live = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            live?.attach(holder.surface, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            live?.detach()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            live?.setVisible(visible)
        }

        /** The launcher says which page it is on (0 … 1). */
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            live?.setOffset(xOffset)
        }

        /** Android tells the lock screen's wallpaper that the phone is being unlocked: the flight begins. */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == SuperDirector.COMMAND_KEYGUARD_GOING_AWAY) live?.unlocking()
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        /** The launcher zooms the wallpaper out (the app drawer, folders): it shrinks with it. */
        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            live?.zoomOut(zoom)
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
