package dev.hearth.launcher.ui

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
import dev.hearth.launcher.data.GlimmerStyle
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

    // Picks up the accessibility switch when coming back from the system settings.
    var serviceOn by remember { mutableStateOf(ControlCenterService.isEnabled) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = ControlCenterService.isEnabled
            delay(1000)
        }
    }

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
                        fontFamily = FontFamily.Serif,
                        fontSize = 34.sp,
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
                    Note("Setzt Icons, Uhr, Farben und Glas auf einmal. Danach kannst du alles einzeln anpassen.")
                }
            }

            item {
                Section("Liquid Glass") {
                    ChoiceRow(
                        label = "Voreinstellung",
                        options = GlassPreset.entries,
                        selected = null,
                        optionLabel = { it.label },
                        onSelect = { preset -> update(preset.transform) },
                    )
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
                        description = "Wölbt sich und leuchtet unter dem Finger",
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
                Section("Glimmer") {
                    Note("Hearths Insel um die Frontkamera, in jeder App: Musik mit Cover und tanzenden Balken, Anrufe, Timer, Navigation, Downloads, neue Nachrichten, Laden und Lautlos. Tippen klappt sie auf, lange drücken öffnet die App.")
                    SwitchRow(label = "Glimmer anzeigen", checked = s.glimmerEnabled) { v -> update { it.copy(glimmerEnabled = v) } }
                    ChoiceRow(
                        label = "Aussehen",
                        options = GlimmerStyle.entries,
                        selected = s.glimmerStyle,
                        optionLabel = { it.label },
                        onSelect = { style -> update { it.copy(glimmerStyle = style) } },
                    )
                    SwitchRow(
                        label = "Kleine Pille, wenn nichts läuft",
                        description = "Wie beim iPhone; im Querformat ausgeblendet",
                        checked = s.glimmerIdlePill,
                    ) { v -> update { it.copy(glimmerIdlePill = v) } }
                    SwitchRow(label = "Neue Nachrichten kurz zeigen", checked = s.glimmerMessages) { v ->
                        update { it.copy(glimmerMessages = v) }
                    }
                    RowDivider()
                    ActionRow(
                        label = "Bedienungshilfe einschalten",
                        description = if (serviceOn) "An" else "Nötig: „Hearth Kontrollzentrum“ unter Bedienungshilfen",
                    ) {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    ActionRow(
                        label = "Benachrichtigungszugriff",
                        description = "Für Musik, Anrufe, Timer und Nachrichten in Glimmer",
                        onClick = vm.media::requestAccess,
                    )
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
                    ActionRow(
                        label = "Hearth-Kontrollzentrum in allen Apps",
                        description = if (serviceOn) {
                            "An: in jeder App oben im gewählten Bereich nach unten wischen"
                        } else {
                            "Aus: tippen und unter Bedienungshilfen „Hearth Kontrollzentrum“ einschalten"
                        },
                    ) {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    SwitchRow(
                        label = "System-Kontrollzentrum ersetzen",
                        description = "Die ganze Statusleiste öffnet dann Hearths Kontrollzentrum, Mitteilungen gibt es darin per Knopf. Geht das von One UI & Co. trotzdem auf (andere Geste), schließt Hearth es sofort. Braucht den Dienst oben.",
                        checked = s.interceptSystemShade,
                    ) { v -> update { it.copy(interceptSystemShade = v) } }
                    ActionRow(
                        label = "Medienanzeige erlauben",
                        description = "Benachrichtigungszugriff, damit das Kontrollzentrum zeigt, was gerade läuft",
                        onClick = vm.media::requestAccess,
                    )
                    if (!s.interceptSystemShade) ChoiceRow(
                        label = "Bereich oben, der es öffnet",
                        options = TriggerZone.entries,
                        selected = s.triggerZone,
                        optionLabel = { it.label },
                        onSelect = { zone -> update { it.copy(triggerZone = zone) } },
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
                    Note("Ganz entfernen lässt sich das System-Kontrollzentrum nur mit Root. Mit dem Dienst fängt Hearth aber das Wischen über der Statusleiste ab, und mit „ersetzen“ schließt es das System-Kontrollzentrum, sobald es doch aufgeht. Über den Kacheln-Knopf oben im Hearth-Kontrollzentrum kommst du jederzeit an die Original-Schalter.")
                }
            }

            item {
                Section("Kontrollzentrum: Aussehen") {
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
            }

            item {
                Section("Dock") {
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
    "Design-Vorlage", "Liquid Glass", "Animationen", "Icons", "Icon-Pack", "Homescreen",
    "Glimmer", "Gesten & Kontrollzentrum", "Dock", "Suche", "Allgemein", "Ausgeblendete Apps",
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .staggeredEntrance(SectionOrder.indexOf(title).coerceAtLeast(0))
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
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
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.16f)),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun SliderRow(
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
private fun PercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) = SliderRow(label, value, range, "${(value * 100).roundToInt()} %", onChange = onChange)

@Composable
private fun IntSlider(
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
private fun SwitchRow(
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
private fun ActionRow(label: String, description: String? = null, onClick: () -> Unit) {
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
private fun <T> ChoiceRow(
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
private fun Chip(text: String, selected: Boolean, swatch: Color?, onClick: () -> Unit) {
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
