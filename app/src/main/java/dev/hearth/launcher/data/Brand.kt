package dev.hearth.launcher.data

/** What the app calls itself: ZENITH (everything in one app), Hearth (the launcher on its own). */
object Brand {
    val name: String get() = if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) "ZENITH" else "Hearth"
}
