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
    const val VERSION = "6.0"

    /**
     * Like Android's desserts: each ClaudeOS has a codename. 1.0 was "Ember", the glow Hearth
     * started from; 2.0 "Blaze": One UI 10 Fluid and liquid glass everywhere; 3.0 "Nova", the
     * new star under Hearth UI 14; 4.0 is "Aurora", under Hearth UI 16: phones that find each
     * other (Glimmer Drop), dark glass and the hidden menus are part of the ground now; 5.0 is
     * "Mythos", under OMEGA UI 18: the Clawd Illuminati, the perks and Claude Mythos; 6.0 is
     * "Helios", the sun, under ZENITH 19: the new system with its sun, its colors that follow
     * the day and the sun's arc.
     */
    const val CODENAME = "Helios"

    /** "ClaudeOS 1.0" */
    val full: String get() = "$NAME $VERSION"
}
