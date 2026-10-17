package dev.hearth.wallpaper

import android.content.Context
import dev.hearth.launcher.wallpaper.SuperParams
import dev.hearth.launcher.wallpaper.SuperScene

/** What this app's wallpaper is (fixed when the app is built – or, in the app with every scene, chosen in it) and how the user has set it. */
internal object SceneConfig {

    const val PREFS = "scene"

    /** The app with every scene ("ZENITH Hintergründe"): the scene is chosen in the app, not by the build. */
    val all: Boolean = BuildConfig.SCENE == "All"

    private val fixed: SuperScene = runCatching { SuperScene.valueOf(BuildConfig.SCENE) }.getOrDefault(SuperScene.Moon)

    /** The scenes the app with every scene offers (the photo of the gallery needs ZENITH). */
    val choices: List<SuperScene> = listOf(
        SuperScene.Moon, SuperScene.Earth, SuperScene.Mars, SuperScene.Venus, SuperScene.Jupiter, SuperScene.Saturn, SuperScene.Neptune,
        SuperScene.Pluto, SuperScene.Io, SuperScene.Europa, SuperScene.Ganymede, SuperScene.Flag, SuperScene.Geometry,
    )

    /** The scene now: the build's own, or the one chosen in the app with every scene. */
    fun scene(context: Context): SuperScene {
        if (!all) return fixed
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("scene", "").orEmpty()
        return choices.firstOrNull { it.name == name } ?: SuperScene.Moon
    }

    fun choose(context: Context, scene: SuperScene) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("scene", scene.name).apply()
    }

    /** Where the landing place of [scene] is kept: one for the app's own scene, one for each in the app with every scene. */
    fun placeKey(scene: SuperScene): String = if (all) "place_${scene.name}" else "place"

    fun read(context: Context): SuperParams {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scene = scene(context)
        return SuperParams(
            scene = scene,
            tilt = p.getBoolean("tilt", true),
            zoomOnWake = p.getBoolean("zoom", true),
            speed = p.getFloat("speed", 1f).coerceIn(0.2f, 3f),
            stars = p.getBoolean("stars", true),
            size = p.getFloat("size", 1f).coerceIn(0.5f, 1.6f),
            place = p.getInt(placeKey(scene), 0).coerceAtLeast(0),
            aod = p.getBoolean("aod", false),
            aodDim = p.getFloat("aodDim", 0.45f).coerceIn(0.1f, 0.8f),
            aodClock = p.getBoolean("aodClock", true),
        )
    }
}
