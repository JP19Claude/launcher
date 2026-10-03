package dev.hearth.launcher.data

/**
 * ClaudeOS: the ground Hearth, Glimmer and Clawd stand on – what Android is to One UI.
 * Shown in each app's software update screen next to the app's own version.
 *
 * Versions: the apps count up by 0.1 for small updates (fixes, little things: 10.0 → 10.1)
 * and by half a number for big ones (many new features, a redesign: 10.0 → 10.5, then 11.0).
 * ClaudeOS itself only moves when the ground under all of them changes.
 */
object ClaudeOs {
    const val NAME = "ClaudeOS"
    const val VERSION = "1.0"

    /** "ClaudeOS 1.0" */
    val full: String get() = "$NAME $VERSION"
}
