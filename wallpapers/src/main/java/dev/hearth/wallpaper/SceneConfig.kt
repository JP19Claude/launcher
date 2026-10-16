package dev.hearth.wallpaper

import android.content.Context
import dev.hearth.launcher.wallpaper.SuperParams
import dev.hearth.launcher.wallpaper.SuperScene

/** What this app's wallpaper is (fixed when the app is built) and how the user has set it. */
internal object SceneConfig {

    const val PREFS = "scene"

    val scene: SuperScene = runCatching { SuperScene.valueOf(BuildConfig.SCENE) }.getOrDefault(SuperScene.Moon)

    fun read(context: Context): SuperParams {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SuperParams(
            scene = scene,
            tilt = p.getBoolean("tilt", true),
            zoomOnWake = p.getBoolean("zoom", true),
            speed = p.getFloat("speed", 1f).coerceIn(0.2f, 3f),
            stars = p.getBoolean("stars", true),
            size = p.getFloat("size", 1f).coerceIn(0.5f, 1.6f),
            place = p.getInt("place", 0).coerceAtLeast(0),
        )
    }
}
