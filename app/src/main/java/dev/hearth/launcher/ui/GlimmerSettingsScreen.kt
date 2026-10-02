package dev.hearth.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.TriggerZone
import dev.hearth.launcher.system.ControlCenterService
import dev.hearth.launcher.system.GlimmerLink
import kotlinx.coroutines.delay

/** The Glimmer app: setting it up, then everything about the island and the control center. */
@Composable
fun GlimmerSettingsScreen(repo: SettingsRepository, media: MediaRepository) {
    val s by repo.settings.collectAsStateWithLifecycle()
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { repo.update(it) }
    val context = LocalContext.current
    val light = rememberGlassLight(false)

    // Picks up switches flipped in the system settings when coming back.
    var serviceOn by remember { mutableStateOf(ControlCenterService.isEnabled) }
    var notificationAccess by remember { mutableStateOf(media.hasAccess()) }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var hearthInstalled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = ControlCenterService.isEnabled
            notificationAccess = media.hasAccess()
            canWrite = Settings.System.canWrite(context)
            hearthInstalled = runCatching {
                context.packageManager.getPackageInfo(GlimmerLink.HEARTH_PACKAGE, 0)
                true
            }.getOrDefault(false)
            delay(1000)
        }
    }
    val open: (Intent) -> Unit = { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    CompositionLocalProvider(
        LocalSettings provides s,
        LocalGlassStyle provides GlassStyle.from(s),
        LocalGlassLight provides light,
        LocalBackdrop provides null,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1B1720), Color(0xFF09090B)))),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // A little island, as a logo.
                        Box(
                            Modifier
                                .size(width = 120.dp, height = 34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(
                                Modifier
                                    .padding(end = 12.dp)
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1C1C22)),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Glimmer", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Die Insel um deine Kamera, in jeder App",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 14.sp,
                        )
                    }
                }

                item {
                    Section("Einrichten") {
                        ActionRow(
                            label = "Bedienungshilfe „Glimmer“",
                            description = if (serviceOn) "An" else "Aus: tippen und unter „Installierte Apps“ einschalten. Nötig für die Insel und das Kontrollzentrum.",
                        ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        ActionRow(
                            label = "Benachrichtigungszugriff",
                            description = if (notificationAccess) "An" else "Aus: für Musik, Anrufe, Timer, Stoppuhr und Nachrichten",
                        ) { media.requestAccess() }
                        ActionRow(
                            label = "Helligkeit & Drehung erlauben",
                            description = if (canWrite) "Erlaubt" else "„Systemeinstellungen ändern“ für das Kontrollzentrum",
                        ) {
                            open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                        ActionRow(
                            label = "„Nicht stören“ erlauben",
                            description = "Zugriff auf „Bitte nicht stören“ für das Kontrollzentrum",
                        ) { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                        Note(
                            if (hearthInstalled) {
                                "Mit Hearth: Apps, die du über Hearth öffnest, fliegen beim Schließen in Glimmer, und Hearth reicht das Aussehen seines Kontrollzentrums hierher weiter."
                            } else {
                                "Glimmer läuft mit jedem Launcher. Mit Hearth als Launcher fliegen geschlossene Apps zusätzlich in die Insel."
                            },
                        )
                    }
                }

                item {
                    Section("Glimmer") { GlimmerOptionRows(s, update) }
                }

                item {
                    Section("Kontrollzentrum über anderen Apps") {
                        SwitchRow(
                            label = "Glas-Kontrollzentrum verwenden",
                            description = if (s.ccEnabled) {
                                "An: oben über die Statusleiste nach unten wischen, in jeder App"
                            } else {
                                "Aus: überall das normale Kontrollzentrum von One UI; die Insel läuft weiter"
                            },
                            checked = s.ccEnabled,
                        ) { v -> update { it.copy(ccEnabled = v) } }
                        SwitchRow(
                            label = "System-Kontrollzentrum ersetzen",
                            description = "Die ganze Statusleiste öffnet dann das Glas-Kontrollzentrum (links die Mitteilungen). Geht das von One UI trotzdem auf, schließt Glimmer es sofort.",
                            checked = s.interceptSystemShade,
                        ) { v -> update { it.copy(interceptSystemShade = v) } }
                        if (!s.interceptSystemShade) {
                            ChoiceRow(
                                label = "Bereich oben, der es öffnet",
                                options = TriggerZone.entries,
                                selected = s.triggerZone,
                                optionLabel = { it.label },
                                onSelect = { zone -> update { it.copy(triggerZone = zone) } },
                            )
                        }
                        Note("Ganz entfernen lässt sich One UIs Kontrollzentrum nur mit Root. Über den Kacheln-Knopf oben im Glas-Kontrollzentrum kommst du jederzeit an die Original-Schalter.")
                    }
                }

                item {
                    Section("Kontrollzentrum: Aussehen") {
                        if (hearthInstalled) {
                            Note("Änderst du das Aussehen in Hearth, übernimmt Glimmer es.")
                        }
                        CcLookRows(s, update)
                    }
                }

                item {
                    Section("Sperrbildschirm") { LockScreenRows(s, update) }
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        ClaudeSpark(s.accent.color, Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Glimmer · Teil von Hearth", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
