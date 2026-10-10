package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AccentColor
import dev.hearth.launcher.data.ClockFont
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.CoreProfile
import dev.hearth.launcher.data.CoreProfiles
import dev.hearth.launcher.data.DockStyle
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.GlassLook
import dev.hearth.launcher.data.GlassQuality
import dev.hearth.launcher.data.GlassTint
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.IconStyle
import dev.hearth.launcher.data.LabelSize
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.PageTransition
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.StrikeColor
import dev.hearth.launcher.data.StrikeSpeed
import dev.hearth.launcher.data.colorOsLook
import dev.hearth.launcher.data.shownClock
import dev.hearth.launcher.data.shownDock
import dev.hearth.launcher.data.withClock
import dev.hearth.launcher.data.withDock
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * ZENITH 19.3, the Core Enforcer Studio: the workshop for rebuilding the system – a die that rolls a
 * whole new look (and takes it back), looks saved under a name, the Z-Angriff's color and tempo
 * with a test run, and every part of the look in one place: icons, labels, clock, dock, page
 * turn, accent and glass. What's set here is the system's setting at once.
 */
@Composable
fun CoreEnforcerScreen(repo: SettingsRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val s by repo.settings.collectAsState()
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { change -> repo.update(change) }
    var profiles by remember { mutableStateOf(CoreProfiles.list(context)) }
    var undo by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    // ZENITH 19.6: ten rolls of the die in one visit.
    var rolls by remember { mutableIntStateOf(0) }
    BackHandler { onClose() }
    LaunchedEffect(note) {
        if (note != null) {
            delay(3800)
            note = null
        }
    }

    Box(Modifier.fillMaxSize().background(VaultBg)) {
        VaultBackdrop()
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { VaultTopBar(onClose, title = "CORE ENFORCER") }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ZenithMark(color = ZenithGreen, width = 76.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Core Enforcer Studio",
                        color = VaultText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        // ZENITH 19.6: held, the terminal.
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { SecretRoutes.open(context, SecretMenu.Console) })
                        },
                    )
                    Text(
                        "Bau dein System um. Alles, was du hier einstellst, gilt sofort.",
                        color = VaultDim,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item {
                StudioPanel("Würfel", "Wirft Akzent, Icons, Uhr, Dock, Glas und Z-Angriff neu zusammen. Gefällt es dir nicht: Zurück.") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        VaultButton("Look würfeln", Modifier.weight(1f)) {
                            undo = CoreProfiles.snapshot(repo)
                            update { CoreProfiles.rolled(it) }
                            note = "Neuer Look gewürfelt."
                            // ZENITH 19.5: the new look arrives with the Z-Angriff in its new color.
                            if (s.strikeOnDice) testing = true
                            rolls++
                            if (rolls >= 10) EasterEggs.find(context, "dice10")
                        }
                        Spacer(Modifier.width(8.dp))
                        VaultButton("Zurück", Modifier.weight(1f), primary = false, enabled = undo != null) {
                            undo?.let { CoreProfiles.restore(repo, it) }
                            undo = null
                            note = "Der Look davor ist wieder da."
                        }
                    }
                }
            }

            item {
                StudioPanel("Profile", "Ein Profil merkt sich den ganzen Look – Icons, Uhr, Dock, Farben, Glas, Z-Angriff. Nicht darin: App-Sperren, Glimmer und Geheimes.") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NameField(name, { name = it }, Modifier.weight(1f)) {
                            CoreProfiles.save(context, repo, name)
                            profiles = CoreProfiles.list(context)
                            name = ""
                            note = "Look gespeichert."
                        }
                        Spacer(Modifier.width(8.dp))
                        VaultButton("Speichern") {
                            CoreProfiles.save(context, repo, name)
                            profiles = CoreProfiles.list(context)
                            name = ""
                            note = "Look gespeichert."
                        }
                    }
                    if (profiles.isEmpty()) {
                        Text("Noch keins gespeichert.", color = VaultDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            onApply = {
                                undo = CoreProfiles.snapshot(repo)
                                CoreProfiles.restore(repo, profile.values)
                                note = "„${profile.name}“ ist an."
                            },
                            onDelete = {
                                CoreProfiles.delete(context, profile.id)
                                profiles = CoreProfiles.list(context)
                            },
                        )
                    }
                }
            }

            item {
                StudioPanel("Z-Angriff", "Der Angriff beim vollen Z – auf Wunsch bei jedem Entsperren.") {
                    ChoiceRow(
                        label = "Farbe",
                        options = StrikeColor.entries,
                        selected = s.strikeColor,
                        optionLabel = { it.label },
                        swatch = { it.color },
                        onSelect = { v -> update { it.copy(strikeColor = v) } },
                    )
                    ChoiceRow(
                        label = "Tempo",
                        options = StrikeSpeed.entries,
                        selected = s.strikeSpeed,
                        optionLabel = { it.label },
                        onSelect = { v -> update { it.copy(strikeSpeed = v) } },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        VaultButton("Probe", Modifier.weight(1f)) { testing = true }
                    }
                    SwitchRow(label = "Z-Angriff beim vollen Z", checked = s.zenithStrike) { v -> update { it.copy(zenithStrike = v) } }
                    SwitchRow(
                        label = "Bei jedem Entsperren",
                        description = "Über allen Apps (braucht Glimmer)",
                        checked = s.zenithStrikeSystem,
                    ) { v -> update { it.copy(zenithStrikeSystem = v) } }
                    SwitchRow(label = "ZENITH-Start nach dem Neustart", checked = s.zenithBootIntro) { v -> update { it.copy(zenithBootIntro = v) } }
                }
            }

            // ZENITH 19.5: the moments the Z-Angriff comes on its own.
            item {
                StudioPanel("Z-Momente", "Wann der Z-Angriff von selbst kommt. Doppeltippen auf den Startbildschirm kann ihn auch auslösen (Einstellungen → Startbildschirm).") {
                    SwitchRow(label = "Nach jedem ZENITH-Update", checked = s.strikeOnUpdate) { v -> update { it.copy(strikeOnUpdate = v) } }
                    SwitchRow(label = "Wenn der Tresor aufgeht", checked = s.strikeOnVault) { v -> update { it.copy(strikeOnVault = v) } }
                    SwitchRow(label = "Beim Würfeln", checked = s.strikeOnDice) { v -> update { it.copy(strikeOnDice = v) } }
                    SwitchRow(
                        label = "Beim Einstecken des Ladekabels",
                        description = "Über allen Apps (braucht Glimmer)",
                        checked = s.strikeOnCharge,
                    ) { v -> update { it.copy(strikeOnCharge = v) } }
                    SwitchRow(
                        label = "Wenn der Akku voll ist",
                        description = "Über allen Apps (braucht Glimmer)",
                        checked = s.strikeOnFull,
                    ) { v -> update { it.copy(strikeOnFull = v) } }
                }
            }

            item {
                StudioPanel("Icons & Namen") {
                    ChoiceRow(label = "Form", options = IconShape.entries, selected = s.iconShape, optionLabel = { it.label }, onSelect = { v -> update { it.copy(iconShape = v) } })
                    ChoiceRow(label = "Stil", options = IconStyle.entries, selected = s.iconStyle, optionLabel = { it.label }, onSelect = { v -> update { it.copy(iconStyle = v) } })
                    IntSlider("Größe", s.iconSize, 40..72, " dp") { v -> update { it.copy(iconSize = v) } }
                    SwitchRow(label = "Namen unter den Icons", checked = s.showLabels) { v -> update { it.copy(showLabels = v) } }
                    ChoiceRow(label = "Schriftgröße der Namen", options = LabelSize.entries, selected = s.labelSize, optionLabel = { it.label }, onSelect = { v -> update { it.copy(labelSize = v) } })
                }
            }

            item {
                StudioPanel("Uhr, Dock & Blättern") {
                    ChoiceRow(label = if (s.colorOsLook) "Uhr (ColorOS-Look)" else "Uhr", options = ClockStyle.entries, selected = s.shownClock, optionLabel = { it.label }, onSelect = { v -> update { it.withClock(v) } })
                    ChoiceRow(label = "Schrift der Uhr", options = ClockFont.entries, selected = s.clockFont, optionLabel = { it.label }, onSelect = { v -> update { it.copy(clockFont = v) } })
                    ChoiceRow(label = if (s.colorOsLook) "Dock (ColorOS-Look)" else "Dock", options = DockStyle.entries, selected = s.shownDock, optionLabel = { it.label }, onSelect = { v -> update { it.withDock(v) } })
                    ChoiceRow(label = "Seitenwechsel", options = PageTransition.entries, selected = s.pageTransition, optionLabel = { it.label }, onSelect = { v -> update { it.copy(pageTransition = v) } })
                    SwitchRow(label = "Begrüßung", checked = s.showGreeting) { v -> update { it.copy(showGreeting = v) } }
                    SwitchRow(label = "Suchleiste", checked = s.showSearchPill) { v -> update { it.copy(showSearchPill = v) } }
                }
            }

            item {
                StudioPanel("Farbe & Glas") {
                    ChoiceRow(
                        label = "Akzent",
                        options = AccentColor.entries,
                        selected = s.accent,
                        optionLabel = { it.label },
                        swatch = { it.color },
                        onSelect = { v -> update { it.copy(accent = v) } },
                    )
                    ChoiceRow(label = "Glas-Stil", options = GlassLook.entries, selected = s.glassLook, optionLabel = { it.label }, onSelect = { v -> update { it.copy(glassLook = v) } })
                    ChoiceRow(label = "Glas-Qualität", options = GlassQuality.entries, selected = s.glassQuality, optionLabel = { it.label }, onSelect = { v -> update { it.copy(glassQuality = v) } })
                    PercentSlider("Lichtbrechung", s.glassRefraction, 0f..2.5f) { v -> update { it.copy(glassRefraction = v) } }
                    PercentSlider("Unschärfe", s.glassBlur, 0f..2.5f) { v -> update { it.copy(glassBlur = v) } }
                    PercentSlider("Farbsäume", s.glassDispersion, 0f..1.5f) { v -> update { it.copy(glassDispersion = v) } }
                    PercentSlider("Glanz", s.glassSpecular, 0f..2f) { v -> update { it.copy(glassSpecular = v) } }
                    PercentSlider("Tönung", s.glassTintStrength, 0f..2.5f) { v -> update { it.copy(glassTintStrength = v) } }
                    ChoiceRow(
                        label = "Glasfarbe",
                        options = GlassTint.entries,
                        selected = s.glassTint,
                        optionLabel = { it.label },
                        swatch = { it.color },
                        onSelect = { v -> update { it.copy(glassTint = v) } },
                    )
                }
            }

            item {
                StudioPanel("Hintergrund & Fluss") {
                    SwitchRow(label = "ZENITH-Hintergrund", description = "Schwarzes Metall mit dem Z statt deines Bildes", checked = s.zenithWallpaper) { v ->
                        update { it.copy(zenithWallpaper = v) }
                    }
                    SwitchRow(label = "Farben nach Sonnenstand", checked = s.zenithDaylight) { v -> update { it.copy(zenithDaylight = v) } }
                    SwitchRow(label = "Fließende Ränder", description = "Claudes Farben fließen um jede Fläche", checked = s.fluidDesign) { v -> update { it.copy(fluidDesign = v) } }
                    SwitchRow(label = "Flüssig-Modus", description = "Leichter und ruckelfreier", checked = s.smoothMode) { v -> update { it.copy(smoothMode = v) } }
                }
            }
        }

        note?.let {
            Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                val shape = ZenithCutShape(10.dp)
                Text(
                    it,
                    color = VaultText,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(shape)
                        .background(VaultPanelHigh)
                        .border(0.8.dp, ZenithGreen.copy(alpha = 0.5f), shape)
                        .clickable { note = null }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (testing) ZenithStrikeOverlay { testing = false }
    }
}

/** A panel of the studio: a title in green, the controls on dark metal, a line of explanation under it. */
@Composable
internal fun StudioPanel(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = ZenithCutShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            title.uppercase(),
            color = ZenithGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(VaultPanel)
                .drawBehind { drawZenithOutlineEdge(shape.createOutline(size, layoutDirection, this)) }
                .padding(vertical = 6.dp),
            content = content,
        )
        if (hint != null) {
            Text(hint, color = VaultDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp))
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val shape = ZenithCutShape(9.dp)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(30)) },
        singleLine = true,
        textStyle = TextStyle(color = VaultText, fontSize = 15.sp),
        cursorBrush = SolidColor(ZenithGreen),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(shape)
                    .background(VaultBg)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text("Name des Looks", color = VaultDim.copy(alpha = 0.7f), fontSize = 15.sp)
                inner()
            }
        },
    )
}

@Composable
private fun ProfileRow(profile: CoreProfile, onApply: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, color = VaultText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(profile.saved)), color = VaultDim, fontSize = 12.sp)
        }
        val chip = ZenithCutShape(6.dp)
        Text(
            "Anwenden",
            color = ZenithGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(chip)
                .border(0.8.dp, ZenithGreen.copy(alpha = 0.6f), chip)
                .clickable(onClick = onApply)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Löschen",
            color = VaultRed,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(chip)
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}
