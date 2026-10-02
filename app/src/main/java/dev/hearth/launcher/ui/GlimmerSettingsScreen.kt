package dev.hearth.launcher.ui

import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.os.PowerManager
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
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
import dev.hearth.launcher.system.GlimmerService
import kotlinx.coroutines.delay

/** Which app of the family this screen belongs to. */
enum class FamilyApp { Glimmer, Controls }

/** The Glimmer app: setting it up, then everything about the island and the lock screen. */
@Composable
fun GlimmerSettingsScreen(repo: SettingsRepository, media: MediaRepository) = FamilySettingsScreen(FamilyApp.Glimmer, repo, media)

/** The control center app: setting it up, then the control center over other apps and its look. */
@Composable
fun ControlsSettingsScreen(repo: SettingsRepository, media: MediaRepository) = FamilySettingsScreen(FamilyApp.Controls, repo, media)

@Composable
private fun FamilySettingsScreen(app: FamilyApp, repo: SettingsRepository, media: MediaRepository) {
    val s by repo.settings.collectAsStateWithLifecycle()
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { repo.update(it) }
    val context = LocalContext.current
    val light = rememberGlassLight(false)

    // Picks up switches flipped in the system settings when coming back.
    val serviceEnabled = { if (app == FamilyApp.Glimmer) GlimmerService.isEnabled else ControlCenterService.isEnabled }
    var serviceOn by remember { mutableStateOf(serviceEnabled()) }
    var notificationAccess by remember { mutableStateOf(media.hasAccess()) }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var hearthInstalled by remember { mutableStateOf(false) }
    val batteryFree = {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    var unrestricted by remember { mutableStateOf(batteryFree()) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = serviceEnabled()
            unrestricted = batteryFree()
            notificationAccess = media.hasAccess()
            canWrite = Settings.System.canWrite(context)
            hearthInstalled = runCatching {
                context.packageManager.getPackageInfo(GlimmerLink.HEARTH_PACKAGE, 0)
                true
            }.getOrDefault(false)
            delay(1000)
        }
    }
    val bluetoothGranted = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
    var bluetoothAllowed by remember { mutableStateOf(bluetoothGranted()) }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bluetoothAllowed = bluetoothGranted()
    }
    // Screenshots in the island need to see new pictures.
    val photosPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val photosGranted = { ContextCompat.checkSelfPermission(context, photosPermission) == PackageManager.PERMISSION_GRANTED }
    var photosAllowed by remember { mutableStateOf(photosGranted()) }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        photosAllowed = photosGranted()
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
                        if (app == FamilyApp.Glimmer) {
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
                        } else {
                            // Four glass switches, as a logo.
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                listOf(Color(0xFF0A84FF), Color(0xFF30D158), Color.White.copy(alpha = 0.25f), Color(0xFFFF9F0A)).forEach { c ->
                                    Box(
                                        Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(c),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (app == FamilyApp.Glimmer) "Glimmer" else "Kontrollzentrum",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (app == FamilyApp.Glimmer) "Die Insel um deine Kamera, in jeder App" else "Glas-Kontrollzentrum über jeder App",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 14.sp,
                        )
                    }
                }

                item {
                    Section("Einrichten") {
                        ActionRow(
                            label = if (app == FamilyApp.Glimmer) "Bedienungshilfe „Glimmer“" else "Bedienungshilfe „Kontrollzentrum“",
                            description = if (serviceOn) {
                                "An"
                            } else {
                                "Aus: tippen und unter „Installierte Apps“ einschalten."
                            },
                        ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        ActionRow(
                            label = "Benachrichtigungszugriff",
                            description = when {
                                notificationAccess -> "An"
                                app == FamilyApp.Glimmer -> "Aus: für Musik, Anrufe, Timer, Stoppuhr und Nachrichten"
                                else -> "Aus: für die Medien-Karte und die Mitteilungen im Kontrollzentrum"
                            },
                        ) { media.requestAccess() }
                        // One UI puts rarely opened apps to sleep, and with them their service:
                        // then the island (or control center) is gone after standby.
                        ActionRow(
                            label = "Akku: Nicht eingeschränkt",
                            description = if (unrestricted) {
                                "Erlaubt. Falls es trotzdem verschwindet: in den App-Infos unter „Akku“ auf „Nicht eingeschränkt“ stellen"
                            } else {
                                "Aus: Damit das Handy ${if (app == FamilyApp.Glimmer) "Glimmer" else "das Kontrollzentrum"} im Standby nicht schlafen legt"
                            },
                        ) {
                            if (unrestricted) {
                                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } else {
                                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                            }
                        }
                        if (app == FamilyApp.Glimmer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActionRow(
                            label = "Kopfhörer-Akku anzeigen",
                            description = if (bluetoothAllowed) "Erlaubt" else "Erlauben, damit Glimmer Name und Akku deiner Kopfhörer zeigt",
                        ) { askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        if (app == FamilyApp.Glimmer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ActionRow(
                            label = "Bildschirmfotos zeigen",
                            description = if (photosAllowed) "Erlaubt" else "Zugriff auf Fotos erlauben, damit neue Bildschirmfotos in Glimmer erscheinen",
                        ) { askPhotos.launch(photosPermission) }
                        if (app == FamilyApp.Controls) ActionRow(
                            label = "Helligkeit & Drehung erlauben",
                            description = if (canWrite) "Erlaubt" else "„Systemeinstellungen ändern“ für das Kontrollzentrum",
                        ) {
                            open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                        if (app == FamilyApp.Controls) ActionRow(
                            label = "„Nicht stören“ erlauben",
                            description = "Zugriff auf „Bitte nicht stören“ für das Kontrollzentrum",
                        ) { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                        Note(
                            when {
                                app == FamilyApp.Controls && hearthInstalled ->
                                    "Mit Hearth: Das Aussehen stellst du in Hearth ein, es gilt dann auch hier."
                                app == FamilyApp.Controls ->
                                    "Läuft mit jedem Launcher. Die Insel um die Kamera ist die App „Glimmer“."
                                hearthInstalled ->
                                    "Mit Hearth: Apps, die du über Hearth öffnest, fliegen beim Schließen in Glimmer."
                                else ->
                                    "Glimmer läuft mit jedem Launcher. Mit Hearth als Launcher fliegen geschlossene Apps zusätzlich in die Insel."
                            },
                        )
                    }
                }

                if (app == FamilyApp.Glimmer) {
                    item { GlimmerPreview(s, media) }
                    item { GlimmerSections(s, update) }
                }

                if (app == FamilyApp.Controls) item {
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

                if (app == FamilyApp.Controls) item {
                    Section("Kontrollzentrum: Aussehen") {
                        if (hearthInstalled) {
                            Note("Änderst du das Aussehen in Hearth, übernimmt das Kontrollzentrum es.")
                        }
                        CcLookRows(s, update)
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        ClaudeSpark(s.accent.color, Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (app == FamilyApp.Glimmer) "Glimmer · Teil von Hearth" else "Kontrollzentrum · Teil von Hearth",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Glimmer as it looks with the current settings (style, width, outline, glow, motion and the
 * nudge), going through a few things it shows; tap it to unfold, like the real one.
 */
@Composable
private fun GlimmerPreview(chosen: LauncherSettings, media: MediaRepository) {
    // As it really runs: in the Dynamic Island mode with the iPhone's look.
    val s = chosen.forGlimmer()
    val demos = remember {
        listOf<IslandContent>(
            IslandContent.Idle,
            IslandContent.Charging(72),
            IslandContent.Code("482913", "Bank", null),
            IslandContent.Alert(Glyph.Bell, "Klingeln", "An", Color(0xFFFF9F0A)),
            IslandContent.Torch,
            IslandContent.Lock(open = false),
        )
    }
    var index by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    // Moves on by itself while folded; stays put while you look at it unfolded.
    LaunchedEffect(expanded) {
        if (!expanded) {
            while (true) {
                delay(2600)
                index = (index + 1) % demos.size
            }
        }
    }
    val content = demos[index].let { if (s.glimmerIsDynamicIsland && it is IslandContent.Lock) IslandContent.Idle else it }
    val unfoldable = content !is IslandContent.Idle && content !is IslandContent.Lock
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    Column(Modifier.padding(vertical = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A2F4A), Color(0xFF15121B)))),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset(x = s.glimmerOffsetX.dp, y = s.glimmerOffsetY.dp),
            ) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalSettings provides s,
                    LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * s.glimmerScale, baseDensity.fontScale),
                ) {
                    GlimmerIsland(
                        content = if (s.glimmerEnabled) content else IslandContent.Hidden,
                        secondary = null,
                        expanded = expanded && unfoldable,
                        style = s.glimmerStyle,
                        screenWidthDp = screenWidth,
                        topInset = 14.dp,
                        media = media,
                        glow = s.glimmerGlow,
                        onToggle = { expanded = !expanded },
                        onCollapse = { expanded = false },
                        onOpen = { expanded = false },
                        onTargetSize = {},
                    )
                }
            }
        }
        Text(
            if (s.glimmerEnabled) "Vorschau · antippen zum Aufklappen" else "Glimmer ist aus",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
