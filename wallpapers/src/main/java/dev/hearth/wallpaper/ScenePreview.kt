package dev.hearth.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import dev.hearth.launcher.wallpaper.SuperMotion
import dev.hearth.launcher.wallpaper.SuperRenderer
import kotlin.math.sin

/** The scene as it runs, in the app: with a slow sway as if the phone were tilted. */
class ScenePreview(context: Context) : View(context) {

    private val renderer = SuperRenderer(SceneConfig.scene, null, context.assets)
    private val motion = SuperMotion()
    private var params = SceneConfig.read(context)
    private val start = SystemClock.uptimeMillis()

    init {
        Thread {
            runCatching { renderer.prepare() }
            postInvalidate()
        }.start()
    }

    /** The options were changed. */
    fun reload() {
        params = SceneConfig.read(context)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() - start
        motion.tiltX = sin(t / 2600f) * 0.4f
        renderer.draw(canvas, width, height, t, params, motion)
        postInvalidateOnAnimation()
    }
}
