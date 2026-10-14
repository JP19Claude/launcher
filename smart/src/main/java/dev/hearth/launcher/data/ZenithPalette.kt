package dev.hearth.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * ZENITH 20.1, ZENITH's color: the green of the Z's edge light, the number, the HUD – or, system-wide,
 * the jubilee's gold and others. [main] is the light, [deep] its shadow, [hud] the pale tone of
 * HUD texts; the rest are what goes with it when the color is chosen in one tap (the aurora, the
 * Z-Angriff, the system accent and glass).
 */
enum class ZenithTint(
    val label: String,
    val main: Color,
    val deep: Color,
    val hud: Color,
    val aurora: AuroraLook,
    val strike: StrikeColor,
    val accent: AccentColor,
    val glass: GlassTint,
) {
    Green("Zenith-Grün", Color(0xFF2FD27A), Color(0xFF15965A), Color(0xFF7DFFA8), AuroraLook.Zenith, StrikeColor.Green, AccentColor.Zenith, GlassTint.Zenith),
    Gold("Jubiläums-Gold", Color(0xFFFFC94D), Color(0xFFC98A12), Color(0xFFFFE08A), AuroraLook.Gold, StrikeColor.Gold, AccentColor.Ochre, GlassTint.Warm),
    Ice("Eisblau", Color(0xFF3FA9FF), Color(0xFF1B6FC4), Color(0xFF9AD8FF), AuroraLook.Eis, StrikeColor.Blue, AccentColor.Blue, GlassTint.Blue),
    Violet("Violett", Color(0xFF9A6BFF), Color(0xFF6038C8), Color(0xFFC9B0FF), AuroraLook.Violett, StrikeColor.Violet, AccentColor.Lilac, GlassTint.Galaxy),
    Ruby("Rubin", Color(0xFFFF4155), Color(0xFFB5192B), Color(0xFFFF9AA5), AuroraLook.Rubin, StrikeColor.Red, AccentColor.Ruby, GlassTint.Ruby),
}

/** ZENITH 20.1: the color of the home screen's light – ZENITH's own, or one of its own. */
enum class HomeLightColor(val label: String, val tint: ZenithTint?) {
    Follow("Wie die ZENITH-Farbe", null),
    Green("Zenith-Grün", ZenithTint.Green),
    Gold("Gold", ZenithTint.Gold),
    Ice("Eisblau", ZenithTint.Ice),
    Violet("Violett", ZenithTint.Violet),
    Ruby("Rubin", ZenithTint.Ruby),
}

/**
 * The color ZENITH is in right now – a piece of state, so everything that draws with [ZenithTint.main]
 * (the Z's light, cards, panels, the HUD) follows a change at once, system-wide. The settings
 * write it (see [SettingsRepository]).
 */
object ZenithPalette {
    var tint: ZenithTint by mutableStateOf(ZenithTint.Green)

    /** The color when it isn't ZENITH's own green – null for green, which is drawn just as it always was. */
    val other: Color? get() = if (tint == ZenithTint.Green) null else tint.main
}
