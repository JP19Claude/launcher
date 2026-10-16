package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * The Super-Wallpaper as a picture in an app (the settings' preview, the preview of a single wallpaper app): the very
 * thing the live wallpaper runs, with the flight from the lock screen to the home screen playing in a loop.
 */
open class SuperLiveView(context: Context, private val photoFile: File? = null) : TextureView(context), TextureView.SurfaceTextureListener {

    private var live: SuperLive? = null
    private var surface: Surface? = null
    private var params = SuperParams()

    init {
        surfaceTextureListener = this
    }

    /** The scene or an option changed. */
    fun setParams(p: SuperParams) {
        params = p
        live?.setParams(p)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val s = Surface(texture)
        surface = s
        val l = SuperLive(context, photoFile, demo = true)
        live = l
        l.setParams(params)
        l.attach(s, width, height)
        l.setVisible(true)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        val s = surface ?: return
        live?.attach(s, width, height)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        live?.setVisible(false)
        live?.detach()
        live?.destroy()
        live = null
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
