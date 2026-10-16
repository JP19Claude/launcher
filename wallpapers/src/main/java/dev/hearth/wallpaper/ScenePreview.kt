package dev.hearth.wallpaper

import android.content.Context
import dev.hearth.launcher.wallpaper.SuperLiveView

/** The scene as it runs, in the app: the flight from the lock screen to the home screen in a loop, with a slow sway as if the phone were tilted. */
class ScenePreview(context: Context) : SuperLiveView(context) {

    init {
        setParams(SceneConfig.read(context))
    }

    /** The options were changed. */
    fun reload() {
        setParams(SceneConfig.read(context))
    }
}
