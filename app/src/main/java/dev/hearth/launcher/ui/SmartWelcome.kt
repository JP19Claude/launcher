package dev.hearth.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.withSmartGlass

/*
 * ZENITH Smart: the welcome that moves you over from Smart Launcher – five short steps (the theme, the icons, the
 * clock, the default launcher), in the dark of Smart Launcher's own settings. Shown once on the first start, and
 * from the settings ("Wechsel-Assistent") whenever you like.
 */

@Composable
internal fun SmartWelcome(vm: LauncherViewModel, onDone: () -> Unit) {
    val stored by vm.settings.collectAsState()
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = vm::updateSettings
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    val steps = 5
    val accent = ssAccent(dev.hearth.launcher.BuildConfig.VERSION_NAME)
    BackHandler { if (step > 0) step -= 1 else onDone() }
    Column(
        Modifier
            .fillMaxSize()
            .background(SsBg)
            .systemBarsPadding()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(steps) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .height(6.dp)
                        .width(if (i == step) 26.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) SsBlue else SsOff),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Überspringen",
                color = SsDim,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDone)
                    .padding(8.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SsIcon(SsGlyph.Z, accent, 88) }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Willkommen bei ZENITH Smart",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    SloganBanner(Modifier.fillMaxWidth(), accent = accent, size = 30.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Der Smart Launcher, den du kennst – mit allem, was dort Geld kostet, dazu ClaudeOS und ZENITH. In fünf kurzen Schritten ist er so, wie du ihn willst.",
                        color = SsDim,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    SsWhyRow("Kategorien selbst bauen", "Hinzufügen, umbenennen, verschieben, entfernen.", true)
                    SsWhyRow("13 Formen für die Icons", "Und eine eigene Farbe mit Farbwähler.", true)
                    SsWhyRow("Keine Werbung, kein Abo", "ZENITH Smart kostet nichts und hat keine Pro-Stufe.", true)
                }
                1 -> {
                    SsStepTitle("Dein Thema", "Fünf Themes wie im Smart Launcher. Tippe auf den Namen, um sie zu sehen – gewählt ist, was angehakt ist.")
                    SsPreview(stored) { theme -> update { it.withSmartGlass(theme) } }
                    SsNote("Alles Weitere – Unschärfe, Brechung, Tiefe, eigene Farbe – findest du später unter Allgemeine Darstellung → Thema bearbeiten.")
                }
                2 -> {
                    SsStepTitle("Deine Icons", "Welche Form sollen die Symbole haben? Dreizehn Formen, alle frei.")
                    IconShape.entries.chunked(5).forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { shape ->
                                val on = shape == stored.smartShape
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (on) SsBlue.copy(alpha = 0.3f) else SsKey)
                                        .border(if (on) 2.dp else 0.dp, SsBlue, RoundedCornerShape(16.dp))
                                        .clickable { update { it.copy(smartShape = shape) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxSize(0.56f)
                                            .clip(shape.clipShape())
                                            .background(if (on) SsBlue else Color.White.copy(alpha = 0.75f)),
                                    )
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SsIntSlider("Symbolgröße", stored.iconSize, 40..96, " dp", { v -> update { it.copy(iconSize = v) } }, { update { it.copy(iconSize = 58) } })
                }
                3 -> {
                    SsStepTitle("Uhr und Termine", "Was soll auf dem Startbildschirm unter der Uhrzeit stehen?")
                    SsSwitchRow("Wetter anzeigen", "Wetterbedingungen für deinen Ort (der Ort steht in der Zentrale).", stored.smartClockWeather) { v -> update { it.copy(smartClockWeather = v) } }
                    SsSwitchRow("Nächsten Alarm anzeigen", null, stored.smartClockAlarm) { v -> update { it.copy(smartClockAlarm = v) } }
                    SsCalendarSwitch(stored, update)
                }
                else -> {
                    SsStepTitle("Zum Standard machen", "Damit die Home-Taste zu ZENITH Smart führt, muss es der Standard-Launcher sein.")
                    SsOutlineButton("Als Standard-Launcher festlegen") {
                        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Spacer(Modifier.height(14.dp))
                    SsNote("Wähle in der Liste „ZENITH Smart“. Den Smart Launcher kannst du behalten oder später löschen – dein Layout lässt sich nicht übernehmen (das Format ist ein anderes), aber alle Apps ordnet ZENITH Smart sofort selbst in Kategorien.")
                    SsNote("Widgets legst du neu an: den Startbildschirm halten → „Widget hinzufügen“. Alles, was hier eingestellt ist, findest du unter Einstellungen.")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                Text(
                    "Zurück",
                    color = SsDim,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { step -= 1 }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SsBlue)
                    .clickable { if (step < steps - 1) step += 1 else onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (step < steps - 1) "Weiter" else "Fertig", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SsStepTitle(title: String, text: String) {
    Text(title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
    Text(text, color = SsDim, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
}
