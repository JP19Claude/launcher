package dev.hearth.launcher.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dev.hearth.launcher.system.ControlsLink
import dev.hearth.launcher.system.GlimmerLink
import dev.hearth.launcher.system.HearthApp
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import dev.hearth.launcher.data.AppInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.AccentColor
import dev.hearth.launcher.data.BadgeStyle
import dev.hearth.launcher.data.CcColorMode
import dev.hearth.launcher.data.CcGlassTint
import dev.hearth.launcher.data.CcSliderStyle
import dev.hearth.launcher.data.CcStyle
import dev.hearth.launcher.data.CcToggleShape
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.DesignPreset
import dev.hearth.launcher.data.GlimmerMusicStyle
import dev.hearth.launcher.data.GlimmerStyle
import dev.hearth.launcher.data.GlimmerUnlock
import dev.hearth.launcher.data.GlimmerMode
import dev.hearth.launcher.data.GlimmerGlowColor
import dev.hearth.launcher.data.GlimmerOutline
import dev.hearth.launcher.data.GlimmerMotion
import dev.hearth.launcher.data.GlimmerDoubleTap
import dev.hearth.launcher.data.DockStyle
import dev.hearth.launcher.data.FlyInStyle
import dev.hearth.launcher.data.GlassQuality
import dev.hearth.launcher.data.GlassTint
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.IconStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.PageTransition
import dev.hearth.launcher.data.SearchEngine
import dev.hearth.launcher.data.SwipeDownAction
import dev.hearth.launcher.data.TriggerZone
import dev.hearth.launcher.system.ControlCenterService
import kotlinx.coroutines.delay
import dev.hearth.launcher.data.ThemeMode
import kotlin.math.roundToInt

private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val SwitchOn = Color(0xFF34C759)

private enum class GlassPreset(val label: String, val transform: (LauncherSettings) -> LauncherSettings) {
    Subtle("Dezent", {
        it.copy(glassRefraction = 0.7f, glassBlur = 0.8f, glassDispersion = 0.3f, glassSpecular = 0.7f, glassTintStrength = 1f)
    }),
    Apple("Apple", {
        it.copy(glassRefraction = 1.3f, glassBlur = 1f, glassDispersion = 0.7f, glassSpecular = 1f, glassTintStrength = 1f)
    }),
    Extreme("Extrem", {
        it.copy(glassRefraction = 2.4f, glassBlur = 0.6f, glassDispersion = 1.4f, glassSpecular = 1.7f, glassTintStrength = 0.6f)
    }),
    Frosted("Milchglas", {
        it.copy(glassRefraction = 0.5f, glassBlur = 2.2f, glassDispersion = 0.2f, glassSpecular = 0.8f, glassTintStrength = 2f)
    }),
}

/** All launcher settings, on glass cards over the blurred wallpaper. */
@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    needsWallpaperAccess: Boolean,
    onRequestWallpaperAccess: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalSettings.current
    val iconPacks by vm.iconPacks.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasGlass = LocalBackdrop.current != null
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = vm::updateSettings

    LaunchedEffect(Unit) { vm.refreshIconPacks() }

    var pickerOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GlassBackdropFill(blur = 36.dp, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (hasGlass) 0.30f else 0.88f)),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Einstellungen",
                        color = TextPrimary,
                        // One UI: Samsung's plain big title instead of the serif one.
                        fontFamily = if (s.galaxyClaude) FontFamily.Default else FontFamily.Serif,
                        fontWeight = if (s.galaxyClaude) FontWeight.Medium else null,
                        fontSize = if (s.galaxyClaude) 32.sp else 34.sp,
                        modifier = Modifier.weight(1f),
                        style = OnWallpaperText,
                    )
                    LiquidGlass(
                        cornerRadius = 22.dp,
                        refraction = 12.dp,
                        interactive = true,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Schließen",
                            tint = TextPrimary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            item {
                Section("Design-Vorlage") {
                    ChoiceRow(
                        label = "Look",
                        options = DesignPreset.entries,
                        selected = null,
                        optionLabel = { it.label },
                        onSelect = { preset -> vm.applyPreset(preset) },
                    )
                    Note("Setzt Icons, Uhr, Farben und Glas auf einmal. Danach kannst du alles einzeln anpassen. „Galaxy × Claude“ macht den Launcher zu One UI mit Glas und Claude im System.")
                    SwitchRow(
                        label = "Claude im System (Galaxy × Claude)",
                        description = "Wie Galaxy AI, nur mit Claude: Now Brief auf dem Startbildschirm, Claude zuerst in der Suche (Los ohne passende App fragt Claude), Vorschläge zum Antippen und „Claude fragen“ im Menü jeder App",
                        checked = s.galaxyClaude,
                    ) { v -> update { it.copy(galaxyClaude = v) } }
                }
            }

            item { ClaudeAssistantSettings() }

            item {
                Section("Liquid Glass") {
                    ChoiceRow(
                        label = "Voreinstellung",
                        options = GlassPreset.entries,
                        selected = null,
                        optionLabel = { it.label },
                        onSelect = { preset -> update(preset.transform) },
                    )
                    ChoiceRow(
                        label = "Glas-Qualität",
                        options = GlassQuality.entries,
                        selected = s.glassQuality,
                        optionLabel = { it.label },
                        onSelect = { q -> update { it.copy(glassQuality = q) } },
                    )
                    Note("Flüssig: auch Knöpfe, Schalter und Icons bekommen die echte Linse. Ruckelt es, nimm „Ausgewogen“.")
                    RowDivider()
                    PercentSlider("Lichtbrechung", s.glassRefraction, 0f..2.5f) { v -> update { it.copy(glassRefraction = v) } }
                    PercentSlider("Unschärfe", s.glassBlur, 0f..2.5f) { v -> update { it.copy(glassBlur = v) } }
                    PercentSlider("Farbsäume", s.glassDispersion, 0f..1.5f) { v -> update { it.copy(glassDispersion = v) } }
                    PercentSlider("Glanz", s.glassSpecular, 0f..2f) { v -> update { it.copy(glassSpecular = v) } }
                    PercentSlider("Tönung", s.glassTintStrength, 0f..2.5f) { v -> update { it.copy(glassTintStrength = v) } }
                    RowDivider()
                    ChoiceRow(
                        label = "Glasfarbe",
                        options = GlassTint.entries,
                        selected = s.glassTint,
                        optionLabel = { it.label },
                        swatch = { it.color },
                        onSelect = { tint -> update { it.copy(glassTint = tint) } },
                    )
                    RowDivider()
                    SwitchRow(
                        label = "Licht folgt der Bewegung",
                        description = "Glanzlichter wandern, wenn du das Handy kippst",
                        checked = s.glassMotion,
                    ) { v -> update { it.copy(glassMotion = v) } }
                    SwitchRow(
                        label = "Glanz auf Icons",
                        description = "Lichtreflex auf jedem Icon, der beim Kippen mitwandert",
                        checked = s.iconGloss,
                    ) { v -> update { it.copy(iconGloss = v) } }
                    SwitchRow(
                        label = "Glas reagiert auf Berührung",
                        description = "Wölbt sich, zieht sich wie ein Tropfen zum Finger und federt zurück",
                        checked = s.glassInteractive,
                    ) { v -> update { it.copy(glassInteractive = v) } }
                    if (needsWallpaperAccess) {
                        RowDivider()
                        ActionRow(
                            label = "Echtes Glas aktivieren",
                            description = "Zugriff auf alle Dateien erlauben, damit das Glas dein Hintergrundbild zeigt",
                            onClick = onRequestWallpaperAccess,
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Note("Lichtbrechung, Farbsäume und Glanzlichter brauchen Android 13. Auf diesem Gerät gibt es Unschärfe und Lichtkanten.")
                    }
                }
            }

            item {
                Section("Animationen") {
                    SwitchRow(
                        label = "Animationen",
                        description = "Zoom beim Zurückkehren, rollende Uhr, Karten erscheinen nacheinander",
                        checked = s.animations,
                    ) { v -> update { it.copy(animations = v) } }
                    ChoiceRow(
                        label = "Seitenwechsel",
                        options = PageTransition.entries,
                        selected = s.pageTransition,
                        optionLabel = { it.label },
                        onSelect = { t -> update { it.copy(pageTransition = t) } },
                    )
                }
            }

            item {
                Section("Icons") {
                    ChoiceRow(
                        label = "Stil",
                        options = IconStyle.entries,
                        selected = s.iconStyle,
                        optionLabel = { it.label },
                        onSelect = { style -> update { it.copy(iconStyle = style) } },
                    )
                    if (s.iconStyle == IconStyle.Tinted) {
                        ChoiceRow(
                            label = "Farbe",
                            options = AccentColor.entries,
                            selected = s.accent,
                            optionLabel = { it.label },
                            swatch = { it.color },
                            onSelect = { accent -> update { it.copy(accent = accent) } },
                        )
                    }
                    ChoiceRow(
                        label = "Form",
                        options = IconShape.entries,
                        selected = s.iconShape,
                        optionLabel = { it.label },
                        onSelect = { shape -> update { it.copy(iconShape = shape) } },
                    )
                    RowDivider()
                    IntSlider("Größe", s.iconSize, 40..72, unit = " dp") { v -> update { it.copy(iconSize = v) } }
                    SwitchRow(label = "Beschriftung anzeigen", checked = s.showLabels) { v ->
                        update { it.copy(showLabels = v) }
                    }
                    RowDivider()
                    ChoiceRow(
                        label = "Benachrichtigungs-Badges",
                        options = BadgeStyle.entries,
                        selected = s.badgeStyle,
                        optionLabel = { it.label },
                        onSelect = { style -> update { it.copy(badgeStyle = style) } },
                    )
                    Note("Braucht den Benachrichtigungszugriff (siehe Glimmer).")
                }
            }

            item {
                Section("Icon-Pack") {
                    PackRow(label = "Keins (System-Icons)", icon = null, selected = s.iconPack == null) {
                        update { it.copy(iconPack = null) }
                    }
                    iconPacks.forEach { pack ->
                        RowDivider()
                        PackRow(label = pack.label, icon = pack.icon, selected = s.iconPack == pack.packageName) {
                            update { it.copy(iconPack = pack.packageName) }
                        }
                    }
                    if (iconPacks.isEmpty()) {
                        Note("Keine Icon-Packs gefunden. Es funktionieren Packs für Nova, ADW, Apex und ähnliche Launcher.")
                    }
                    RowDivider()
                    ActionRow(label = "Icon-Packs im Play Store suchen") {
                        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(market) }.onFailure {
                            val web = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/search?q=icon%20pack&c=apps"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(web) }
                        }
                    }
                    SwitchRow(
                        label = "Fehlende Icons anpassen",
                        description = "Apps ohne Icon im Pack bekommen dessen Rahmen",
                        checked = s.adaptUnthemedIcons,
                    ) { v -> update { it.copy(adaptUnthemedIcons = v) } }
                }
            }

            item {
                Section("Homescreen") {
                    IntSlider("Spalten", s.columns, 3..6) { v -> update { it.copy(columns = v) } }
                    IntSlider("Reihen", s.rows, 4..8) { v -> update { it.copy(rows = v) } }
                    RowDivider()
                    ChoiceRow(
                        label = "Uhr",
                        options = ClockStyle.entries,
                        selected = s.clockStyle,
                        optionLabel = { it.label },
                        onSelect = { style -> update { it.copy(clockStyle = style) } },
                    )
                    SwitchRow(label = "Begrüßung", checked = s.showGreeting) { v -> update { it.copy(showGreeting = v) } }
                    SwitchRow(label = "Akkustand in der Glas-Karte", checked = s.showBattery) { v ->
                        update { it.copy(showBattery = v) }
                    }
                    RowDivider()
                    PercentSlider("Hintergrund abdunkeln", s.dimWallpaper, 0f..0.7f) { v -> update { it.copy(dimWallpaper = v) } }
                    SwitchRow(label = "Such-Pille anzeigen", checked = s.showSearchPill) { v ->
                        update { it.copy(showSearchPill = v) }
                    }
                    SwitchRow(label = "Claude-Karte anzeigen", checked = s.showClaudeCard) { v ->
                        update { it.copy(showClaudeCard = v) }
                    }
                    SwitchRow(
                        label = "App-Mediathek",
                        description = "Hinter der letzten Seite: alle Apps in Glas-Ordnern",
                        checked = s.showAppLibrary,
                    ) { v -> update { it.copy(showAppLibrary = v) } }
                    SwitchRow(
                        label = "Doppeltippen sperrt den Bildschirm",
                        description = "Auf eine freie Stelle; braucht die Bedienungshilfe",
                        checked = s.doubleTapLock,
                    ) { v -> update { it.copy(doubleTapLock = v) } }
                    ActionRow(
                        label = "Anordnung zurücksetzen",
                        description = "Apps wieder automatisch sortieren (frei verschobene Plätze vergessen)",
                        onClick = vm::resetHomeLayout,
                    )
                    if (s.removedFromHome.isNotEmpty()) {
                        ActionRow(
                            label = "Alle Apps zurück auf den Startbildschirm",
                            description = "${s.removedFromHome.size} Apps sind nur in der App-Mediathek",
                            onClick = vm::restoreHome,
                        )
                    }
                    SwitchRow(
                        label = "Widget-Seite",
                        description = "Links neben dem Homescreen: deine Widgets auf Glas",
                        checked = s.showWidgetPage,
                    ) { v -> update { it.copy(showWidgetPage = v) } }
                    RowDivider()
                    ActionRow(label = "Hintergrundbild ändern", onClick = vm::openWallpaperPicker)
                }
            }

            item {
                Section("Glimmer & Kontrollzentrum") {
                    GlimmerLinkRows(s, update)
                }
            }

            item {
                Section("Gesten & Kontrollzentrum") {
                    ChoiceRow(
                        label = "Nach unten wischen",
                        options = SwipeDownAction.entries,
                        selected = s.swipeDownAction,
                        optionLabel = { it.label },
                        onSelect = { action -> update { it.copy(swipeDownAction = action) } },
                    )
                    SwitchRow(label = "Nach oben wischen öffnet die Suche", checked = s.swipeOpensSearch) { v ->
                        update { it.copy(swipeOpensSearch = v) }
                    }
                    RowDivider()
                    SwitchRow(
                        label = "Hearth-Kontrollzentrum verwenden",
                        description = if (s.ccEnabled) {
                            "An: Hearths Glas-Kontrollzentrum beim Herunterwischen auf dem Homescreen (über anderen Apps: App „Kontrollzentrum“)"
                        } else {
                            "Aus: auf dem Homescreen das normale Kontrollzentrum von One UI"
                        },
                        checked = s.ccEnabled,
                    ) { v -> update { it.copy(ccEnabled = v) } }
                    ActionRow(
                        label = "Medienanzeige erlauben",
                        description = "Benachrichtigungszugriff, damit das Kontrollzentrum zeigt, was gerade läuft",
                        onClick = vm.media::requestAccess,
                    )
                    RowDivider()
                    ActionRow(
                        label = "Helligkeit & Drehung erlauben",
                        description = "„Systemeinstellungen ändern“ für das Kontrollzentrum",
                        onClick = vm.controls::requestWriteSettings,
                    )
                    ActionRow(
                        label = "„Nicht stören“ erlauben",
                        description = "Zugriff auf „Bitte nicht stören“",
                    ) {
                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    Note("Das Kontrollzentrum über anderen Apps und das Ersetzen von One UIs Kontrollzentrum sind die App „Kontrollzentrum“; das Aussehen unten gilt auch dort.")
                }
            }

            item {
                Section("Kontrollzentrum: Aussehen") {
                    CcLookRows(s, update)
                }
            }

            item {
                Section("Dock") {
                    ChoiceRow(
                        label = "Aussehen",
                        options = DockStyle.entries,
                        selected = s.dockStyle,
                        optionLabel = { it.label },
                        onSelect = { v -> update { it.copy(dockStyle = v) } },
                    )
                    RowDivider()
                    IntSlider("Anzahl Apps", s.dockSize.coerceIn(1, LauncherSettings.MAX_DOCK), 1..LauncherSettings.MAX_DOCK) { v ->
                        vm.setDockSize(v)
                    }
                    Note("Lange auf eine App drücken, um sie zum Dock hinzuzufügen, zu entfernen oder zu verschieben.")
                    RowDivider()
                    ActionRow(label = "Dock zurücksetzen", onClick = vm::resetDock)
                }
            }

            item {
                Section("Suche") {
                    ChoiceRow(
                        label = "Suchmaschine",
                        options = SearchEngine.entries,
                        selected = s.searchEngine,
                        optionLabel = { it.label },
                        onSelect = { engine -> update { it.copy(searchEngine = engine) } },
                    )
                }
            }

            item {
                Section("Allgemein") {
                    ChoiceRow(
                        label = "Design der Suche",
                        options = ThemeMode.entries,
                        selected = s.theme,
                        optionLabel = { it.label },
                        onSelect = { mode -> update { it.copy(theme = mode) } },
                    )
                    SwitchRow(label = "Vibration", checked = s.haptics) { v -> update { it.copy(haptics = v) } }
                    RowDivider()
                    ActionRow(label = "Als Standard-Launcher festlegen", onClick = vm::openDefaultLauncherSettings)
                }
            }

            item {
                val hidden = allApps.filter { it.key in s.hiddenApps }
                Section("Ausgeblendete Apps") {
                    ActionRow(
                        label = "Apps auswählen …",
                        description = "Mehrere Apps auf einmal aus- oder einblenden",
                    ) { pickerOpen = true }
                    if (hidden.isNotEmpty()) {
                        ActionRow(label = "Alle wieder einblenden", onClick = vm::unhideAll)
                    }
                    RowDivider()
                    if (hidden.isEmpty()) {
                        Note("Keine. Lange auf eine App drücken und „Ausblenden“ oder „Auswählen“ wählen.")
                    }
                    hidden.forEachIndexed { index, app ->
                        if (index > 0) RowDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(app, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Chip(text = "Einblenden", selected = false, swatch = null) { vm.unhide(app.key) }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClaudeSpark(s.accent.color, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Hearth · gebaut mit Claude",
                        color = TextSecondary,
                        fontFamily = FontFamily.Serif,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (pickerOpen) {
            HiddenAppsPicker(
                apps = allApps,
                hidden = s.hiddenApps,
                onToggle = { app, hide -> vm.setHidden(app.key, hide) },
                onDone = { pickerOpen = false },
            )
        }
    }
}

/** All apps with a mark each: marked apps are hidden from home screen, library and search. */
/** Glimmer's own options (the Glimmer app shows these). */
/** All of Glimmer's options, grouped into sections (shown in the Glimmer app). */
@Composable
internal fun GlimmerSections(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    // In the Dynamic Island mode the iPhone decides the look and handling; those rows step aside.
    val di = s.glimmerIsDynamicIsland
    Column {
    Section("Glimmer") {
        Note("Die Insel um die Frontkamera, in jeder App: Musik, Anrufe, Timer, Stoppuhr, Navigation, Downloads, Nachrichten, Laden und mehr. Nach unten ziehen klappt sie auf, nach oben schließt sie, zur Seite wechselt zwischen zwei Aktivitäten.")
        SwitchRow(label = "Glimmer anzeigen", checked = s.glimmerEnabled) { v -> update { it.copy(glimmerEnabled = v) } }
        ChoiceRow(
            label = "Modus",
            options = GlimmerMode.entries,
            selected = s.glimmerMode,
            optionLabel = { it.label },
            onSelect = { m -> update { it.copy(glimmerMode = m) } },
        )
        if (di) Note(
            "Dynamic Island 1:1: Glimmer verhält sich wie die Insel des iPhones – reines Schwarz, ihre Proportionen (Pille, kompakte Aktivitäten, aufgeklappt fast bildschirmbreit), Cover als abgerundetes Quadrat, auf dem Sperrbildschirm nur die Pille. Antippen öffnet die App, gedrückt halten klappt auf. Glimmers Extras (Leuchten, Rand, Nachrichten, Doppeltippen, Titel wischen, Akku beim Laden) sind aus. Deine eigenen Einstellungen bleiben gespeichert und kommen zurück, wenn du wieder „Glimmer“ wählst.",
        )
        if (!di) ChoiceRow(
            label = "Aussehen",
            options = GlimmerStyle.entries,
            selected = s.glimmerStyle,
            optionLabel = { it.label },
            onSelect = { style -> update { it.copy(glimmerStyle = style) } },
        )
        if (!di) ChoiceRow(
            label = "Musik",
            options = GlimmerMusicStyle.entries,
            selected = s.glimmerMusicStyle,
            optionLabel = { it.label },
            onSelect = { style -> update { it.copy(glimmerMusicStyle = style) } },
        )
        if (!di) SwitchRow(
            label = "Kleine Pille, wenn nichts läuft",
            description = "Wie beim iPhone; im Querformat ausgeblendet",
            checked = s.glimmerIdlePill,
        ) { v -> update { it.copy(glimmerIdlePill = v) } }
    }

    if (!di) Section("Gestaltung") {
        SwitchRow(label = "Leuchten bei Neuem", description = "Glimmer schimmert kurz, wenn etwas Neues kommt", checked = s.glimmerGlow) { v ->
            update { it.copy(glimmerGlow = v) }
        }
        if (s.glimmerGlow) ChoiceRow(
            label = "Leuchtfarbe",
            options = GlimmerGlowColor.entries,
            selected = s.glimmerGlowColor,
            optionLabel = { it.label },
            onSelect = { c -> update { it.copy(glimmerGlowColor = c) } },
        )
        SwitchRow(
            label = "Atmendes Leuchten",
            description = "Solange etwas läuft, atmet ein weiches Licht in seiner Farbe um die Insel – wie OPPOs Fluid Cloud",
            checked = s.glimmerBreathe,
        ) { v -> update { it.copy(glimmerBreathe = v) } }
        ChoiceRow(
            label = "Rand",
            options = GlimmerOutline.entries,
            selected = s.glimmerOutline,
            optionLabel = { it.label },
            onSelect = { o -> update { it.copy(glimmerOutline = o) } },
        )
        ChoiceRow(
            label = "Bewegung",
            options = GlimmerMotion.entries,
            selected = s.glimmerMotion,
            optionLabel = { it.label },
            onSelect = { m -> update { it.copy(glimmerMotion = m) } },
        )
    }

    Section(if (di) "Position" else "Größe & Position") {
        SwitchRow(
            label = "Kleiner (85 %)",
            description = "Glimmer ist 15 % schmaler – in jedem Zustand. Höhe, Inhalte und Schrift bleiben gleich groß",
            checked = s.glimmerSmall,
        ) { v -> update { it.copy(glimmerSmall = v) } }
        if (!di) SliderRow(
            label = "Breite",
            value = s.glimmerWidth,
            range = 0.8f..1.3f,
            valueText = "${(s.glimmerWidth * 100).roundToInt()} %",
        ) { v -> update { it.copy(glimmerWidth = (v * 20).roundToInt() / 20f) } }
        IntSlider("Nach links / rechts", s.glimmerOffsetX, -40..40, " dp") { v -> update { it.copy(glimmerOffsetX = v) } }
        IntSlider("Nach oben / unten", s.glimmerOffsetY, -10..24, " dp") { v -> update { it.copy(glimmerOffsetY = v) } }
        Note("Sitzt Glimmer nicht genau um deine Kamera, schieb es hier zurecht. Die Änderung siehst du sofort.")
        ActionRow(
            label = "Größe & Position zurücksetzen",
            description = if (s.glimmerSizeIsDefault) {
                "Ist schon Standard: normale Breite, genau über der Kamera"
            } else {
                "Breite 100 %, nicht verschoben – wieder genau über der Kamera"
            },
        ) { update { it.withGlimmerSizeReset() } }
    }

    Section("Bedienung") {
        if (!di) SwitchRow(
            label = "Antippen öffnet die App",
            description = if (s.glimmerTapOpens) "Wie beim iPhone; gedrückt halten klappt auf" else "Aus: Antippen klappt auf, gedrückt halten öffnet die App",
            checked = s.glimmerTapOpens,
        ) { v -> update { it.copy(glimmerTapOpens = v) } }
        if (!di) ChoiceRow(
            label = "Doppeltippen",
            options = GlimmerDoubleTap.entries,
            selected = s.glimmerDoubleTap,
            optionLabel = { it.label },
            onSelect = { d -> update { it.copy(glimmerDoubleTap = d) } },
        )
        ChoiceRow(
            label = "Von selbst zuklappen",
            options = listOf(5, 9, 15, 30, 0),
            selected = s.glimmerAutoCollapse,
            optionLabel = { if (it == 0) "Nie" else "nach $it s" },
            onSelect = { secs -> update { it.copy(glimmerAutoCollapse = secs) } },
        )
        if (!di) SwitchRow(
            label = "Zur Seite wischen wechselt den Titel",
            description = "Läuft nur Musik: über Glimmer nach links für den nächsten, nach rechts für den vorherigen Titel",
            checked = s.glimmerSwipeTracks,
        ) { v -> update { it.copy(glimmerSwipeTracks = v) } }
        SwitchRow(label = "Vibration", description = "Leichtes Feedback beim Antippen, Aufklappen und Wischen", checked = s.glimmerHaptics) { v ->
            update { it.copy(glimmerHaptics = v) }
        }
    }

    Section("Was Glimmer zeigt") {
        if (!di) SwitchRow(
            label = "Bestätigungscodes zum Kopieren",
            description = "Kommt ein Code per SMS oder App (Bank, Login), sitzt er in Glimmer; antippen kopiert ihn – wie bei vivo und OPPO",
            checked = s.glimmerCodes,
        ) { v -> update { it.copy(glimmerCodes = v) } }
        if (!di) SwitchRow(
            label = "Bildschirmfotos in der Insel",
            description = "Ein neues Bildschirmfoto erscheint als kleines Bild: antippen öffnet es, aufgeklappt auch Teilen – wie vivos Origin Island (braucht den Zugriff auf Fotos, siehe Einrichten)",
            checked = s.glimmerScreenshots,
        ) { v -> update { it.copy(glimmerScreenshots = v) } }
        if (!di) SwitchRow(label = "Neue Nachrichten kurz zeigen", checked = s.glimmerMessages) { v ->
            update { it.copy(glimmerMessages = v) }
        }
        SwitchRow(
            label = "Kopfhörer, Nicht stören, Akku voll",
            description = "Kurz anzeigen, wenn Kopfhörer sich verbinden, „Nicht stören“ umschaltet oder der Akku voll ist",
            checked = s.glimmerAlerts,
        ) { v -> update { it.copy(glimmerAlerts = v) } }
        if (!di) SwitchRow(
            label = "Akku beim Laden zeigen",
            description = "Solange das Handy lädt, bleibt der Akkustand mit rollenden Zahlen in Glimmer",
            checked = s.glimmerCharging,
        ) { v -> update { it.copy(glimmerCharging = v) } }
        SwitchRow(
            label = "Im Vollbild ausblenden (Test)",
            description = "Bei Videos und Spielen ohne Statusleiste bleibt nur Wichtiges (Anruf, Wecker, Entsperren). Nicht jedes Handy meldet das zuverlässig: verschwindet Glimmer dann auch anderswo, schalte es wieder aus.",
            checked = s.glimmerHideFullscreen,
        ) { v -> update { it.copy(glimmerHideFullscreen = v) } }
    }

    Section("Sperrbildschirm & AOD") {
        ChoiceRow(
            label = "Beim Entsperren",
            options = GlimmerUnlock.entries,
            selected = s.glimmerUnlock,
            optionLabel = { it.label },
            onSelect = { u -> update { it.copy(glimmerUnlock = u) } },
        )
        SwitchRow(
            label = "Auf dem Always-On-Display",
            description = "Musik und Aktivitäten bleiben im AOD sichtbar, gedimmt und ohne Bewegung (wenn das Handy fremde Einblendungen dort zulässt)",
            checked = s.glimmerAod,
        ) { v -> update { it.copy(glimmerAod = v) } }
    }

    Section("Zurücksetzen") {
        // Everything back as it came; a second tap confirms, so it can't happen by accident.
        var confirmReset by remember { mutableStateOf(false) }
        LaunchedEffect(confirmReset) {
            if (confirmReset) {
                delay(4000)
                confirmReset = false
            }
        }
        ActionRow(
            label = if (confirmReset) "Wirklich alles zurücksetzen? Nochmal tippen" else "Alle Glimmer-Einstellungen zurücksetzen",
            description = "Aussehen, Größe, Position, Farben, Bewegung und Funktionen wie am Anfang; Glimmer bleibt an",
        ) {
            if (confirmReset) {
                update { it.withGlimmerDefaults() }
                confirmReset = false
            } else {
                confirmReset = true
            }
        }
    }
    }
}

/** How the control center looks; Hearth shows these and hands them to Glimmer. */
@Composable
internal fun CcLookRows(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
        ChoiceRow(
            label = "Stil",
            options = CcStyle.entries,
            selected = s.ccStyle,
            optionLabel = { it.label },
            // The matching colors and details come along; all can be changed below.
            onSelect = { style -> update { it.withCcStyle(style) } },
        )
        Note("iOS 27: klares Glas, iOS-Farben, Verbindungen neben der Medien-Karte, Mitteilungen gestapelt wie auf dem iPhone. ColorOS 17: große Kacheln, leuchtende Schalter.")
        RowDivider()
        SwitchRow(
            label = "Nach rechts wischen: Mitteilungen",
            description = "Eigene Glas-Seite mit deinen Mitteilungen links neben den Schaltern; antippen öffnet, nach rechts wischen löscht. Mit „System-Kontrollzentrum ersetzen“ öffnet die linke Hälfte der Statusleiste gleich die Mitteilungen.",
            checked = s.ccNotifications,
        ) { v -> update { it.copy(ccNotifications = v) } }
        RowDivider()
        ChoiceRow(
            label = "Farbe eingeschalteter Schalter",
            options = CcColorMode.entries,
            selected = s.ccColors,
            optionLabel = { it.label },
            onSelect = { mode -> update { it.copy(ccColors = mode) } },
        )
        ChoiceRow(
            label = "Form der Schalter",
            options = CcToggleShape.entries,
            selected = s.ccShape,
            optionLabel = { it.label },
            onSelect = { shape -> update { it.copy(ccShape = shape) } },
        )
        ChoiceRow(
            label = "Regler für Helligkeit und Lautstärke",
            options = CcSliderStyle.entries,
            selected = s.ccSliders,
            optionLabel = { it.label },
            onSelect = { style -> update { it.copy(ccSliders = style) } },
        )
        SwitchRow(label = "Leuchtende Kontur", description = "Eingeschaltete Schalter glühen wie bei ColorOS 17", checked = s.ccGlow) { v ->
            update { it.copy(ccGlow = v) }
        }
        SwitchRow(label = "Beschriftungen unter den Schaltern", checked = s.ccLabels) { v ->
            update { it.copy(ccLabels = v) }
        }
        RowDivider()
        SwitchRow(label = "Große WLAN- und Mobil-Kacheln (ColorOS)", checked = s.ccBigTiles) { v ->
            update { it.copy(ccBigTiles = v) }
        }
        SwitchRow(label = "Medien-Karte", checked = s.ccShowMedia) { v ->
            update { it.copy(ccShowMedia = v) }
        }
        SwitchRow(label = "Uhr und Datum oben", checked = s.ccShowClock) { v ->
            update { it.copy(ccShowClock = v) }
        }
        SwitchRow(label = "Schnellstart (Kamera, Wecker, Rechner, Claude)", checked = s.ccShowShortcuts) { v ->
            update { it.copy(ccShowShortcuts = v) }
        }
        IntSlider("Größe der Schalter", s.ccToggleSize.coerceIn(44, 66), 44..66, " dp") { v ->
            update { it.copy(ccToggleSize = v) }
        }
        IntSlider("Rundung der Flächen", s.ccCorner.coerceIn(12, 44), 12..44, " dp") { v ->
            update { it.copy(ccCorner = v) }
        }
        RowDivider()
        ChoiceRow(
            label = "Glas der Flächen",
            options = CcGlassTint.entries,
            selected = s.ccGlassTint,
            optionLabel = { it.label },
            onSelect = { tint -> update { it.copy(ccGlassTint = tint) } },
        )
        PercentSlider("Deckkraft des Glases", s.ccGlassOpacity, 0f..1f) { v ->
            update { it.copy(ccGlassOpacity = v) }
        }
        PercentSlider("Glanz an den Kanten", s.ccSpecular / 2f, 0f..1f) { v ->
            update { it.copy(ccSpecular = v * 2f) }
        }
        PercentSlider("Lichtbrechung", s.ccRefraction / 2.5f, 0f..1f) { v ->
            update { it.copy(ccRefraction = v * 2.5f) }
        }
        PercentSlider("Hintergrund weichzeichnen", s.ccBlur, 0f..1f) { v ->
            update { it.copy(ccBlur = v) }
        }
        PercentSlider("Hintergrund abdunkeln", s.ccDim, 0f..1f) { v ->
            update { it.copy(ccDim = v) }
        }
        Note("Änderungen gelten beim nächsten Öffnen des Kontrollzentrums, in Hearth und über anderen Apps. Das Weichzeichnen über anderen Apps braucht Android 12+ und ein Handy, das es unterstützt; sonst dunkelt Hearth stärker ab.")
}

/** In Hearth: the family's other apps (Glimmer, control center), and the fly-in. */
@Composable
private fun GlimmerLinkRows(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    Note("Die Insel um die Kamera und das Kontrollzentrum über anderen Apps sind eigene Apps: „Glimmer“ (Insel mit Live-Aktivitäten) und „Kontrollzentrum“ (Glas-Kontrollzentrum in jeder App, ersetzt One UIs). Beide laufen mit jedem Launcher; mit Hearth fliegen geschlossene Apps in Glimmer.")
    FamilyAppRow("Glimmer", GlimmerLink, GlimmerLink.DOWNLOAD_URL)
    FamilyAppRow("Kontrollzentrum", ControlsLink, ControlsLink.DOWNLOAD_URL)
    SwitchRow(
        label = "Apps fliegen in Glimmer",
        description = "Schließt du eine App, die du über Hearth geöffnet hast, fliegt sie selbst in die Insel (wie bei HarmonyOS). Braucht Glimmer.",
        checked = s.glimmerFlyIn,
    ) { v -> update { it.copy(glimmerFlyIn = v) } }
    if (s.glimmerFlyIn) ChoiceRow(
        label = "So fliegt sie hinein",
        options = FlyInStyle.entries,
        selected = s.glimmerFlyInStyle,
        optionLabel = { it.label },
        onSelect = { style -> update { it.copy(glimmerFlyInStyle = style) } },
    )
    if (s.glimmerFlyIn) Note(
        if (s.glimmerFlyInStyle == FlyInStyle.HyperOS) {
            "HyperOS: Die App bleibt als Ganzes, schrumpft in einem schnellen Bogen zur Kamera, wird dabei zu ihrem Icon und landet in der Insel, die sich kurz für sie öffnet."
        } else {
            "HarmonyOS: Die App steigt zur Kamera, wird flach wie eine Kapsel und dunkel und verschmilzt über eine flüssige Brücke mit der Insel."
        },
    )
}

/** One of the other apps: open it (with its state), or get it. */
@Composable
private fun FamilyAppRow(name: String, link: HearthApp, downloadUrl: String) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(link.isInstalled(context)) }
    var running by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            installed = link.isInstalled(context)
            running = if (installed) withContext(Dispatchers.IO) { link.isRunning(context) } else null
            delay(1500)
        }
    }
    if (installed) {
        ActionRow(
            label = "$name öffnen",
            description = when (running) {
                true -> "Läuft"
                false -> "Installiert, aber die Bedienungshilfe „$name“ ist aus"
                null -> "Installiert"
            },
        ) { link.openApp(context) }
    } else {
        ActionRow(
            label = "$name installieren",
            description = "Lädt die APK aus dem neuesten Release",
        ) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
private fun HiddenAppsPicker(
    apps: List<AppInfo>,
    hidden: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Apps ausblenden", color = TextPrimary, fontFamily = FontFamily.Serif, fontSize = 28.sp)
                    Text("${hidden.size} ausgeblendet", color = TextSecondary, fontSize = 14.sp)
                }
                GlassChip("Fertig", onClick = onDone)
            }
            LiquidGlass(
                cornerRadius = 26.dp,
                refraction = 16.dp,
                blur = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp)),
            ) {
                LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(apps, key = { it.key }) { app ->
                        val isHidden = app.key in hidden
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app, !isHidden) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(app, 38.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                app.label,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            SelectionBadge(isHidden)
                        }
                    }
                }
            }
        }
    }
}

private val SectionOrder = listOf(
    "Design-Vorlage", "Claude-Assistent", "Liquid Glass", "Animationen", "Icons", "Icon-Pack", "Homescreen",
    "Glimmer", "Gesten & Kontrollzentrum", "Dock", "Suche", "Allgemein", "Ausgeblendete Apps",
)

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .staggeredEntrance(SectionOrder.indexOf(title).coerceAtLeast(0))
            .padding(vertical = 8.dp),
    ) {
        // One UI: section names in normal case and Galaxy blue; otherwise small capitals.
        val oneUi = LocalSettings.current.galaxyClaude
        Text(
            text = if (oneUi) title else title.uppercase(),
            color = if (oneUi) LocalSettings.current.accent.color else TextSecondary,
            fontSize = if (oneUi) 14.sp else 13.sp,
            fontWeight = if (oneUi) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = if (oneUi) 0.sp else 1.sp,
            style = OnWallpaperText,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
        )
        LiquidGlass(
            cornerRadius = 26.dp,
            refraction = 16.dp,
            blur = 20.dp,
            // Clipped, so touch ripples of the rows stay inside the rounded card.
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp)),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
internal fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.16f)),
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = TextSecondary, fontSize = 14.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun PercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) = SliderRow(label, value, range, "${(value * 100).roundToInt()} %", onChange = onChange)

@Composable
internal fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    onChange: (Int) -> Unit,
) = SliderRow(
    label = label,
    value = value.toFloat(),
    range = range.first.toFloat()..range.last.toFloat(),
    valueText = "$value$unit",
    steps = (range.last - range.first - 1).coerceAtLeast(0),
    onChange = { v ->
        val rounded = v.roundToInt()
        if (rounded != value) onChange(rounded)
    },
)

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp)
            if (description != null) {
                Text(description, color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SwitchOn,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
internal fun ActionRow(label: String, description: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp)
            if (description != null) {
                Text(description, color = TextSecondary, fontSize = 13.sp)
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    swatch: ((T) -> Color)? = null,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(label, color = TextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                Chip(
                    text = optionLabel(option),
                    selected = option == selected,
                    swatch = swatch?.invoke(option),
                ) { onSelect(option) }
            }
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, swatch: Color?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.25f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(swatch.copy(alpha = 1f))
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (selected) Color.Black else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun PackRow(
    label: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(SquircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Ausgewählt", tint = SwitchOn)
        }
    }
}
