package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
