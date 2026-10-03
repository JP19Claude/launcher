package dev.hearth.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette: limestone, walnut and ochre — warm, quiet, a bit like an old workshop. */
object HearthColors {
    val Limestone = Color(0xFFE7E3DC)
    val LimestoneRaised = Color(0xFFF2EFEA)
    val LimestoneSunken = Color(0xFFD9D3C9)
    val Walnut = Color(0xFF2E2722)
    val Stone = Color(0xFF7D736A)
    val Ochre = Color(0xFFC8952E)

    val Charcoal = Color(0xFF1C1A17)
    val CharcoalRaised = Color(0xFF26231F)
    val CharcoalSunken = Color(0xFF332F2A)
    val Bone = Color(0xFFECE6DC)
    val Ash = Color(0xFFA39A8F)
    val OchreLight = Color(0xFFE0B158)

    /** Translucent glass for dock and search pill on top of the wallpaper. */
    val Glass = Limestone.copy(alpha = 0.18f)
    val GlassEdge = Color.White.copy(alpha = 0.22f)
}

/** Claude's palette: ivory paper, slate ink and the warm terracotta accent. */
object ClaudeColors {
    val Terracotta = Color(0xFFD97757)
    val TerracottaDeep = Color(0xFFC6613F)
    val Ivory = Color(0xFFFAF9F5)
    val Paper = Color(0xFFF0EEE6)
    val Oat = Color(0xFFE8E6DC)
    val Slate = Color(0xFF141413)
    val SlateMuted = Color(0xFF73726C)
    val Night = Color(0xFF1F1E1D)
    val NightRaised = Color(0xFF262624)
    val NightSunken = Color(0xFF30302E)
    val Cloud = Color(0xFFA6A39A)
}

private val LightScheme = lightColorScheme(
    primary = ClaudeColors.TerracottaDeep,
    onPrimary = Color.White,
    background = ClaudeColors.Ivory,
    onBackground = ClaudeColors.Slate,
    surface = ClaudeColors.Paper,
    onSurface = ClaudeColors.Slate,
    surfaceVariant = ClaudeColors.Oat,
    onSurfaceVariant = ClaudeColors.SlateMuted,
)

private val DarkScheme = darkColorScheme(
    primary = ClaudeColors.Terracotta,
    onPrimary = Color.White,
    background = ClaudeColors.Night,
    onBackground = ClaudeColors.Ivory,
    surface = ClaudeColors.NightRaised,
    onSurface = ClaudeColors.Ivory,
    surfaceVariant = ClaudeColors.NightSunken,
    onSurfaceVariant = ClaudeColors.Cloud,
)

@Composable
fun HearthTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        // One clock for every flowing effect of the screen (see Fluid.kt).
        dev.hearth.launcher.ui.ProvideFluidClock(content)
    }
}
