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
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.DockStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.withBeColorOs

/*
 * ZENITH 22.1, "Be ColorOS": the third mode of the Clawd Illuminati, next to "Be Smarter" and "Be Comet" – the whole launcher as
 * ColorOS 17 dresses it. It is the ColorOS look of Projekt Zenith (squircle icons, the ColorOS clock, the floating dock of glass, the
 * ColorOS control center) taken further and made a copy rather than something to tune: ColorOS's frosted glass with its own lens,
 * the fluid design (touches spread like liquid light, colours drift behind the screens), a calm home screen without saying and
 * greeting, ZENITH's own effects stepping aside, and Glimmer as OPPO's Fluid Cloud (a capsule tinted in the activity's colour, the
 * spinning cover with its progress ring, a light that breathes). The drawing is in LauncherSettings.withColorOsLook; this file is the
 * switch and the few things worth choosing.
 */

/** ColorOS's colours: the dark it sits in, and the green it lights up with. */
internal val ColorOsBlack = Color(0xFF070B0A)
internal val ColorOsDeep = Color(0xFF0D1F1B)
internal val ColorOsTeal = Color(0xFF14403A)
internal val ColorOsGreen = Color(0xFF2EE6A6)

/** The Illuminati's "Be ColorOS": the card that switches the mode (the three modes never run together). */
@Composable
internal fun ColorOsSection(settings: LauncherSettings, onChange: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    ColorOsCard(settings.beColorOs) { v -> onChange { it.withBeColorOs(v) } }
    if (settings.beColorOs) {
        IlluminationSection("◐ Be ColorOS") {
            ChoiceRow(
                label = "Uhr",
                options = ClockStyle.entries,
                selected = settings.colorOsClock,
                optionLabel = { it.label },
                onSelect = { v -> onChange { it.copy(colorOsClock = v) } },
            )
            ChoiceRow(
                label = "Dock",
                options = DockStyle.entries,
                selected = settings.colorOsDock,
                optionLabel = { it.label },
                onSelect = { v -> onChange { it.copy(colorOsDock = v) } },
            )
            IlluminationSwitch(
                "✦", "Fluid-Design",
                "Berührungen breiten sich wie flüssiges Licht aus, hinter den Bildschirmen driften Farben – wie in ColorOS.",
                settings.fluidDesign,
            ) { v -> onChange { it.copy(fluidDesign = v) } }
        }
    }
    Spacer(Modifier.height(18.dp))
}

/** The big card of the mode: tapped, it switches Be ColorOS. */
@Composable
private fun ColorOsCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(ColorOsBlack, ColorOsDeep, ColorOsTeal)))
            .border(if (on) 2.dp else 1.dp, ColorOsGreen.copy(alpha = if (on) 1f else 0.5f), RoundedCornerShape(26.dp))
            .clickable { onToggle(!on) }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BE COLOROS",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFE8FFF6), ColorOsGreen)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    if (on) "Der Launcher ist jetzt ColorOS 17." else "Mach aus dem Launcher ColorOS 17.",
                    color = Color(0xFFB8F5DF).copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }
            GlassSwitch(
                checked = on,
                onCheckedChange = onToggle,
                onColor = ColorOsGreen,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Der ganze Launcher als ColorOS 17: Milchglas mit der tiefen Linse von ColorOS, runde Squircle-Icons, die ColorOS-Uhr, ein schwebendes Dock aus Glas, " +
                "das ColorOS-Kontrollzentrum, das Fluid-Design (Berührungen breiten sich wie flüssiges Licht aus, Farben driften hinter den Bildschirmen) " +
                "und Glimmer als Fluid Cloud – die Kapsel in der Farbe der Aktivität, das drehende Cover mit Fortschrittsring, ein atmendes Licht. " +
                "Das Glas ist ein Abbild und lässt sich nicht verstellen; die eigenen Looks und Effekte von ZENITH treten solange zurück. " +
                "Aus: alles wie vorher. „Be Smarter“ und „Be Comet“ schalten sich dabei aus.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
