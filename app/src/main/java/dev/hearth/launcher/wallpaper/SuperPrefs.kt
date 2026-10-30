package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.hearth.launcher.data.SettingsRepository

/** The settings of the Super-Wallpaper, as ZENITH's settings keep them. */
object SuperPrefs {
    fun read(context: Context): SuperParams {
        val p = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val scene = runCatching { SuperScene.valueOf(p.getString("superScene", "").orEmpty()) }.getOrDefault(SuperScene.Moon)
        return SuperParams(
            scene = scene,
            tilt = p.getBoolean("superTilt", true),
            zoomOnWake = p.getBoolean("superZoom", true),
            speed = p.getFloat("superSpeed", 1f).coerceIn(0.2f, 3f),
            stars = p.getBoolean("superStars", true),
            size = p.getFloat("superSize", 1f).coerceIn(0.5f, 1.6f),
            place = p.getInt("superPlace", 0).coerceAtLeast(0),
            photo = p.getLong("superPhoto", 0L),
            aod = p.getBoolean("superAod", false),
            aodDim = p.getFloat("superAodDim", 0.45f).coerceIn(0.1f, 0.8f),
            aodClock = p.getBoolean("superAodClock", true),
        )
    }

    /**
     * The still for the always-on display, [w] × [h]: the body in the far view of the lock screen (on the place that was chosen), a little
     * below the middle, on pure black – no sky, no stars: an AOD should light as few pixels as it can. The photos and the geometry fill
     * the screen as they are. Null if it couldn't be drawn.
     */
    fun aodStill(context: Context, w: Int, h: Int): Bitmap? = runCatching {
        val params = read(context).copy(stars = false)
        val renderer = SuperRenderer(params.scene, SuperPhoto.file(context), context.assets)
        renderer.prepare()
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val place = SuperPlaces.of(params.scene).let { it.getOrNull(params.place) ?: it.firstOrNull() }
        val motion = SuperMotion().also { m ->
            m.depth = 0f
            m.lockFly = 1f
            place?.let { m.spin = it.spin; m.lat = it.tilt }
        }
        renderer.draw(Canvas(scene), w, h, 5000L, params, motion)
        if (params.scene == SuperScene.Photo || params.scene == SuperScene.Flag || params.scene == SuperScene.Geometry || params.scene.isScape) return@runCatching scene
        val pose = SuperPose()
        SuperCamera.pose(params.scene, 1f, 0f, pose)
        val cx = w * pose.cx
        val cy = h * pose.cy
        val reach = w * pose.r * params.size * (if (params.scene == SuperScene.Saturn) 2.7f else 1.45f)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        // Only the body and its glow: a round window onto the picture that fades out at the edge.
        canvas.translate(0f, h * 0.08f)
        val fade = RadialGradient(cx, cy, reach, intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = ComposeShader(BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), fade, PorterDuff.Mode.DST_IN)
        }
        canvas.drawCircle(cx, cy, reach, paint)
        scene.recycle()
        out
    }.getOrNull()

    /** One still frame of the wallpaper as set, [w] × [h] – the glass of ZENITH bends and blurs this. Null if it couldn't be drawn. */
    fun snapshot(context: Context, w: Int, h: Int): Bitmap? = runCatching {
        val params = read(context)
        val renderer = SuperRenderer(params.scene, SuperPhoto.file(context), context.assets)
        renderer.prepare()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // The body on the place that was chosen (the surprise shows the first).
        val place = SuperPlaces.of(params.scene).let { it.getOrNull(params.place) ?: it.firstOrNull() }
        val motion = SuperMotion().also { m -> place?.let { m.spin = it.spin; m.lat = it.tilt } }
        renderer.draw(Canvas(bitmap), w, h, 5000L, params, motion)
        bitmap
    }.getOrNull()
}
