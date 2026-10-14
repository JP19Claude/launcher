package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.withComet

/*
 * ZENITH 21.5, "Be Comet": the launcher after CometOS – clear Liquid Glass (or solid surfaces), the big glass
 * clock, the dock as a capsule of glass, the App Library after the last page, the control center and the
 * notifications pulled down from the two top corners – with ZENITH and ClaudeOS at the core of its settings.
 * Drawn after the look of Comet Launcher 3.28 (its colors, its names, its way of ordering things); the code is
 * ZENITH's own. This file is its switch in the Clawd Illuminati; the settings are in CometSettings.kt.
 */

/** Comet's colors: the void it sits in, and the blue it lights up with. */
internal val CometBlack = Color(0xFF050508)
internal val CometDeep = Color(0xFF0A0F1E)
internal val CometNavy = Color(0xFF1A2744)
internal val CometBlue = Color(0xFF3B82F6)

/** The Illuminati's "Be Comet": the card that switches the mode (it and "Be Smarter" never run together). */
@Composable
internal fun CometSection(settings: LauncherSettings, onChange: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    CometCard(settings.beComet) { v -> onChange { it.withComet(v) } }
    Spacer(Modifier.height(18.dp))
}

/** The big card of the mode: tapped, it switches Be Comet. */
@Composable
private fun CometCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(CometBlack, CometDeep, CometNavy)))
            .border(if (on) 2.dp else 1.dp, CometBlue.copy(alpha = if (on) 1f else 0.5f), RoundedCornerShape(26.dp))
            .clickable { onToggle(!on) }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BE COMET",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFE6F0FF), CometBlue)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    if (on) "Der Launcher ist jetzt Comet – mit ZENITH im Kern." else "Mach aus dem Launcher CometOS – mit ZENITH im Kern.",
                    color = Color(0xFFBBD0FF).copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }
            GlassSwitch(
                checked = on,
                onCheckedChange = onToggle,
                onColor = CometBlue,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Nach dem Vorbild von Comet Launcher: klares Liquid Glass oder solide Flächen, eine große Uhr aus Glas mit Datum, Wetter und Wecker, " +
                "ein Dock als gläserne Kapsel, die App-Bibliothek nach der letzten Seite, das Kontrollzentrum von oben rechts und die Mitteilungen von oben links. " +
                "ZENITH und ClaudeOS stehen im Kern der Comet-Einstellungen (Launcher-Einstellungen), Claude ersetzt Comet AI. " +
                "Die eigenen Looks und Effekte von ZENITH treten solange zurück. Aus: alles wie vorher. „Be Smarter“ schaltet sich dabei aus.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
