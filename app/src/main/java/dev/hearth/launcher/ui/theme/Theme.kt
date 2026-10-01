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

private val LightScheme = lightColorScheme(
    primary = HearthColors.Ochre,
    onPrimary = HearthColors.Walnut,
    background = HearthColors.Limestone,
    onBackground = HearthColors.Walnut,
    surface = HearthColors.LimestoneRaised,
    onSurface = HearthColors.Walnut,
    surfaceVariant = HearthColors.LimestoneSunken,
    onSurfaceVariant = HearthColors.Stone,
)

private val DarkScheme = darkColorScheme(
    primary = HearthColors.OchreLight,
    onPrimary = HearthColors.Charcoal,
    background = HearthColors.Charcoal,
    onBackground = HearthColors.Bone,
    surface = HearthColors.CharcoalRaised,
    onSurface = HearthColors.Bone,
    surfaceVariant = HearthColors.CharcoalSunken,
    onSurfaceVariant = HearthColors.Ash,
)

@Composable
fun HearthTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
