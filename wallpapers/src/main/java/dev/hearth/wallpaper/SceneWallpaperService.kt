package dev.hearth.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.hearth.launcher.wallpaper.SuperDirector
import dev.hearth.launcher.wallpaper.SuperLive

/**
 * The live wallpaper of this app: its one scene (the Moon, Mars …), drawn by ZENITH's [SuperLive] – on its own thread, in
 * step with the screen while it moves, by the graphics chip (the processor on phones that can't), only while it is seen.
 * On the lock screen the body hangs small in the upper left; unlocking flies it in to the bottom centre, where it turns,
 * and then the camera zooms in – the way the super wallpapers of Xiaomi's HyperOS do (see [SuperDirector]). The home page
 * and the tilt move it. The options are the ones set in the app.
 */
class SceneWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine() {

        private val prefs = getSharedPreferences(SceneConfig.PREFS, Context.MODE_PRIVATE)
        private var live: SuperLive? = null

        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            live?.setParams(SceneConfig.read(this@SceneWallpaperService))
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val l = SuperLive(this@SceneWallpaperService)
            l.setParams(SceneConfig.read(this@SceneWallpaperService))
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

        /** Android tells the lock screen's wallpaper that the phone is being unlocked (the flight begins), or the launcher that someone tapped (a flash). */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == SuperDirector.COMMAND_KEYGUARD_GOING_AWAY) live?.unlocking()
            // A tap on an empty place of the home screen (the launcher passes it on): a flash and a ring of light.
            if (action == SuperDirector.COMMAND_TAP) live?.tap()
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        /** The launcher zooms the wallpaper out (the app drawer, folders): it shrinks with it. */
        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            live?.zoomOut(zoom)
        }
    }
}
