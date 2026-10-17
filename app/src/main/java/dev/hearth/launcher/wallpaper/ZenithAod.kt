package dev.hearth.launcher.wallpaper

import java.io.File

/** ZENITH's always-on screen: the scene and the options set in ZENITH's settings ("Super-Wallpaper"). */
class ZenithAodActivity : SuperAodActivity() {
    override fun params(): SuperParams = SuperPrefs.read(this)
    override fun photoFile(): File? = SuperPhoto.file(this)
}

/** ZENITH's screen saver: the same screen, started by Android while the phone charges. */
class ZenithDreamService : SuperDream() {
    override fun params(): SuperParams = SuperPrefs.read(this)
    override fun photoFile(): File? = SuperPhoto.file(this)
}
