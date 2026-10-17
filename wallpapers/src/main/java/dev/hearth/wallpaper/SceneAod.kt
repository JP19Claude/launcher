package dev.hearth.wallpaper

import dev.hearth.launcher.wallpaper.SuperAodActivity
import dev.hearth.launcher.wallpaper.SuperDream
import dev.hearth.launcher.wallpaper.SuperParams

/** This app's always-on screen: its scene and the options set in the app. */
class SceneAodActivity : SuperAodActivity() {
    override fun params(): SuperParams = SceneConfig.read(this)
}

/** This app's screen saver: the same screen, started by Android while the phone charges. */
class SceneDreamService : SuperDream() {
    override fun params(): SuperParams = SceneConfig.read(this)
}
