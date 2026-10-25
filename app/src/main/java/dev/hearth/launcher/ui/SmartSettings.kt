package dev.hearth.launcher.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClockFont
import dev.hearth.launcher.data.DefaultSmartTabs
import dev.hearth.launcher.data.HomeGesture
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.LabelSize
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.SmartClockLayout
import dev.hearth.launcher.data.SmartColor
import dev.hearth.launcher.data.SmartGlass
import dev.hearth.launcher.data.SmartHours
import dev.hearth.launcher.data.SmartPageEffect
import dev.hearth.launcher.data.SmartTab
import dev.hearth.launcher.data.SwipeDownAction
import dev.hearth.launcher.data.ZenithTheme
import dev.hearth.launcher.data.withSmartGlass
import dev.hearth.launcher.data.withSmartGlimmer
import dev.hearth.launcher.data.withComet
import dev.hearth.launcher.data.withSmarter
import dev.hearth.launcher.data.withZenithTheme
import kotlin.math.roundToInt

/*
 * ZENITH Smart: the settings the way Smart Launcher 6 draws its own – a dark ground, a big title under a back arrow,
 * plain rows with a line icon and a chevron, switches that turn blue, sliders with their value and a button that
 * sets them back, cards for what belongs together, a theme with its preview on top. At the core of it, before
 * everything else: ClaudeOS and ZENITH. What has no page of its own here (Claude's key, Clawd, the Zentrale, the
 * vault …) opens from those two, in ZENITH's own settings.
 */

internal val SsBg = Color(0xFF22252B)
internal val SsCardColor = Color(0xFF2F3238)
internal val SsLine = Color(0xFF3F434A)
internal val SsDim = Color(0xFFA9ADB4)
internal val SsBlue = Color(0xFF2F8CFF)
internal val SsOff = Color(0xFF50535A)
internal val SsKey = Color(0xFF3A3D44)

// ---------------------------------------------------------------------------------------------
// The pages
// ---------------------------------------------------------------------------------------------

/** Where the back arrow of a page leads (null: out of the settings). */
private fun ssParent(page: String): String? = when (page) {
    "main" -> null
    "theme", "icons", "widget", "font" -> "look"
    "clock" -> "home"
    "tabs" -> "drawer"
    else -> "main"
}

/**
 * The settings of ZENITH Smart (and of "Be Smarter" in ZENITH). [onZenith] opens ZENITH's own settings on one of their
 * pages ("claude", "clawd", "glimmer", "design", "vorteile", "cloud", "privacy", "general", "system") or, with null, on
 * their start page.
 */
@Composable
fun SmartSettingsScreen(
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onZenith: (String?) -> Unit,
    /** ZENITH Smart: opens the welcome that helps to move over from Smart Launcher once more. */
    onWelcome: () -> Unit = {},
) {
    val s = LocalSettings.current
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = vm::updateSettings
    var page by rememberSaveable { mutableStateOf("main") }
    val back: () -> Unit = {
        val parent = ssParent(page)
        if (parent == null) onClose() else page = parent
    }
    BackHandler { back() }
    val go: (String) -> Unit = { page = it }
    Box(
        Modifier
            .fillMaxSize()
            .background(SsBg),
    ) {
        when (page) {
            "look" -> SsLookPage(s, update, go, back)
            "theme" -> SsThemePage(s, update, back)
            "icons" -> SsIconsPage(s, update, back)
            "widget" -> SsWidgetPage(s, update, back)
            "font" -> SsFontPage(s, update, back)
            "home" -> SsHomePage(s, update, go, back)
            "clock" -> SsClockPage(s, update, back)
            "drawer" -> SsDrawerPage(s, update, go, back)
            "tabs" -> SsTabsPage(s, update, back)
            "why" -> SsWhyPage(back)
            "search" -> SsSearchPage(s, update, back)
            "gestures" -> SsGesturesPage(s, update, back)
            "anim" -> SsAnimPage(s, update, onZenith, back)
            "claudeos" -> SsClaudeOsPage(onZenith, back)
            "zenith" -> SsZenithPage(s, update, onZenith, back)
            else -> SsMainPage(go, onZenith, onWelcome, back)
        }
    }
}

@Composable
internal fun SsMainPage(go: (String) -> Unit, onZenith: (String?) -> Unit, onWelcome: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SsPage("Einstellungen", back) {
        // The system card: what this is and what it runs on. Tapped: the phone's info.
        SsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { ssOpen(context, "AboutPhoneActivity") }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SsIcon(SsGlyph.Z, ssAccent(version), 44)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("ZENITH Smart $version", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                        style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SsDim)
            }
            SloganBanner(Modifier.padding(top = 4.dp, bottom = 14.dp), accent = ssAccent(version), size = 34.sp, textAlign = TextAlign.Start)
        }
        // The core: the two systems this launcher is made of.
        SsHeader("Kern")
        SsLink("ClaudeOS", "Claude & KI, Clawd, Glimmer, Software-Update", { SsIcon(SsGlyph.Spark, Color(0xFFE5845F)) }) { go("claudeos") }
        SsLink("ZENITH", "Zentrale, Design, Illuminati, Datenschutz, System", { SsIcon(SsGlyph.Z, ssAccent(version)) }) { go("zenith") }
        // Moving over from Smart Launcher: why, and the steps once more.
        SsLink("Warum ZENITH Smart", "Was hier frei ist, was Smart Launcher nur mit Pro hat", { SsIcon(SsGlyph.Z, ssAccent(version)) }) { go("why") }
        SsLink("Wechsel-Assistent", "Thema, Icons, Uhr und der Standard-Launcher – Schritt für Schritt", { SsIcon(SsGlyph.Swipe) }) { onWelcome() }
        SsHeader("Launcher")
        SsLink("Allgemeine Darstellung", null, { SsIcon(SsGlyph.Look) }) { go("look") }
        SsLink("Super-Wallpaper", "Mond, Erde und Planeten als echte NASA-Bilder – Neigung, Zoom, Seiten", { SsIcon(SsGlyph.Look) }) { onZenith("superwall") }
        SsLink("Wallpaper-Galerie", "Mond, Planeten, Weltraum … – zum Auswählen und Herunterladen", { SsIcon(SsGlyph.Look) }) { onZenith("wallpapers") }
        SsLink("OnePlus Live-Wallpaper", "Die Live-Wallpaper deines Telefons – ansehen und setzen", { SsIcon(SsGlyph.Look) }) { onZenith("phonewall") }
        SsLink("Startbildschirm", null, { SsIcon(SsGlyph.Home) }) { go("home") }
        SsLink("App-Raster", null, { SsIcon(SsGlyph.Grid) }) { go("drawer") }
        SsLink("Suchseite", null, { SsIcon(SsGlyph.Search) }) { go("search") }
        SsLink("Gesten und Hotkeys", null, { SsIcon(SsGlyph.Swipe) }) { go("gestures") }
        SsLink("Animationen", null, { SsIcon(SsGlyph.Bolt) }) { go("anim") }
        SsHeader("Mehr")
        SsLink("Sicherheit und Privatsphäre", null, { SsIcon(SsGlyph.Lock) }) { onZenith("privacy") }
        SsLink("Sicherung", null, { SsIcon(SsGlyph.Cloud) }) { onZenith("cloud") }
        SsLink("Erweiterte Einstellungen", "Alle Einstellungen von ZENITH", { SsIcon(SsGlyph.Gear) }) { onZenith(null) }
    }
}

@Composable
internal fun SsLookPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, go: (String) -> Unit, back: () -> Unit) {
    SsPage("Allgemeine Darstellung", back, trailing = { SsKeyButton { update { it.withSmartGlass(it.smartGlass) } } }) {
        SsPreview(s) { theme -> update { it.withSmartGlass(theme) } }
        Spacer(Modifier.height(20.dp))
        SsOutlineButton("Thema Bearbeiten") { go("theme") }
        Spacer(Modifier.height(10.dp))
        SsLink("Symbol-Darstellung", null, { SsIcon(SsGlyph.Look) }) { go("icons") }
        SsLink("Widget", null, { SsIcon(SsGlyph.Widget) }) { go("widget") }
        SsLink("Schriftart", null, { SsIcon(SsGlyph.Font) }) { go("font") }
    }
}

@Composable
internal fun SsThemePage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    val theme = s.smartGlass
    SsPage("Thema bearbeiten", back, trailing = { SsKeyButton { update { it.withSmartGlass(it.smartGlass) } } }) {
        SsCard {
            SsSliderRow(
                "Unschärferadius", "${"%.1f".format(s.smartBlur)}%", ssFrac(s.smartBlur, 0f, 2.5f),
                { f -> update { it.copy(smartBlur = ssUnfrac(f, 0f, 2.5f)) } },
                { update { it.copy(smartBlur = theme.blur) } },
            )
            SsDivider()
            SsHeader("Erweiterte Einstellungen")
            SsSliderRow(
                "Lebendigkeit verwischen", "+${(s.smartVibrancy * 100).roundToInt()}%", ssFrac(s.smartVibrancy, 0f, 1f),
                { f -> update { it.copy(smartVibrancy = ssUnfrac(f, 0f, 1f)) } },
                { update { it.copy(smartVibrancy = theme.vibrancy) } },
            )
            SsSliderRow(
                "Brechung", "${(s.smartRefraction * 40).roundToInt()}dp", ssFrac(s.smartRefraction, 0f, 2.5f),
                { f -> update { it.copy(smartRefraction = ssUnfrac(f, 0f, 2.5f)) } },
                { update { it.copy(smartRefraction = theme.refraction) } },
            )
            SsSliderRow(
                "Tiefe", "${(s.smartDepth * 40).roundToInt()}dp", ssFrac(s.smartDepth, 0.5f, 3f),
                { f -> update { it.copy(smartDepth = ssUnfrac(f, 0.5f, 3f)) } },
                { update { it.copy(smartDepth = theme.depth) } },
            )
            SsSliderRow(
                "Streuung", "${(s.smartDispersion * 100).roundToInt()}%", ssFrac(s.smartDispersion, 0f, 1.5f),
                { f -> update { it.copy(smartDispersion = ssUnfrac(f, 0f, 1.5f)) } },
                { update { it.copy(smartDispersion = theme.dispersion) } },
            )
            SsSliderRow(
                "Glanz", "${(s.smartSpecular * 100).roundToInt()}%", ssFrac(s.smartSpecular, 0f, 2f),
                { f -> update { it.copy(smartSpecular = ssUnfrac(f, 0f, 2f)) } },
                { update { it.copy(smartSpecular = theme.specular) } },
            )
            SsSliderRow(
                "Tönung", "${(s.smartTint * 100).roundToInt()}%", ssFrac(s.smartTint, 0f, 2.5f),
                { f -> update { it.copy(smartTint = ssUnfrac(f, 0f, 2.5f)) } },
                { update { it.copy(smartTint = theme.tint) } },
            )
        }
        SsCard {
            SsHeader("Karten und Symbole")
            SsChoice(SmartColor.entries, s.smartColor, { it.label }) { color -> update { it.copy(smartColor = color) } }
            if (s.smartColor == SmartColor.Custom) {
                Spacer(Modifier.height(6.dp))
                SsColorPicker(s.smartCustomColor) { argb -> update { it.copy(smartCustomColor = argb) } }
                Spacer(Modifier.height(10.dp))
            }
            SsNote("Die Farbe gilt für Ambient Design; Material You nimmt die Farben des Telefons.")
        }
    }
}

@Composable
internal fun SsIconsPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Symbol-Darstellung", back) {
        SsHeader("Form")
        SsChoice(IconShape.entries, s.smartShape, { it.label }) { shape -> update { it.copy(smartShape = shape) } }
        SsHeader("Größe")
        SsIntSlider("Symbolgröße", s.iconSize, 40..96, " dp", { v -> update { it.copy(iconSize = v) } }, { update { it.copy(iconSize = 58) } })
        SsHeader("Beschriftung")
        SsSwitchRow("Namen im App-Raster", "Der Name der App steht unter dem Symbol.", s.smartDrawerNames) { v -> update { it.copy(smartDrawerNames = v) } }
        SsChoice(LabelSize.entries, s.labelSize, { it.label }) { size -> update { it.copy(labelSize = size) } }
    }
}

@Composable
internal fun SsWidgetPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Widget", back) {
        SsSwitchRow("Abgerundete Widget-Ecken", null, s.smartWidgetRadius > 0) { v -> update { it.copy(smartWidgetRadius = if (v) 28 else 0) } }
        SsIntSlider("Eckenradius", s.smartWidgetRadius, 0..44, " dp", { v -> update { it.copy(smartWidgetRadius = v) } }, { update { it.copy(smartWidgetRadius = 28) } })
    }
}

@Composable
internal fun SsFontPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Schriftart", back) {
        SsHeader("Uhr")
        SsChoice(ClockFont.entries, s.clockFont, { it.label }) { font -> update { it.copy(clockFont = font) } }
        SsHeader("Textgröße der Symbole")
        SsChoice(LabelSize.entries, s.labelSize, { it.label }) { size -> update { it.copy(labelSize = size) } }
    }
}

@Composable
internal fun SsHomePage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, go: (String) -> Unit, back: () -> Unit) {
    SsPage("Startbildschirm", back) {
        SsLink("Uhr und Wetter", "Anordnung, Größe, Wetter, Wecker, Termin", { SsIcon(SsGlyph.Home) }) { go("clock") }
        SsHeader("Raster-Eigenschaften")
        SsIntSlider("Spalten", s.columns, 3..6, "", { v -> update { it.copy(columns = v) } }, { update { it.copy(columns = 4) } })
        SsIntSlider("Zeilen", s.rows, 4..8, "", { v -> update { it.copy(rows = v) } }, { update { it.copy(rows = 6) } })
        SsHeader("Seiten")
        SsSwitchRow("Zeige Indikator", "Zeigt einen Indikator für die Seite, auf der du gerade bist.", s.smartIndicator) { v -> update { it.copy(smartIndicator = v) } }
        SsSwitchRow("Lesbarkeit verbessern", "Fügt ein dunkles Layout zur Verbesserung der Lesbarkeit auf hellen Hintergründen hinzu.", s.smartReadable) { v -> update { it.copy(smartReadable = v) } }
        SsSwitchRow("Smart-Suche", "Die Suchleiste ganz unten, unter dem Dock.", s.showSearchPill) { v -> update { it.copy(showSearchPill = v) } }
    }
}

@Composable
internal fun SsClockPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Uhr und Wetter", back) {
        SsHeader("Anordnung")
        SsChoice(SmartClockLayout.entries, s.smartClockLayout, { it.label }) { layout -> update { it.copy(smartClockLayout = layout) } }
        SsIntSlider("Größe der Uhr", s.smartClockSize.roundToInt(), 48..100, " sp", { v -> update { it.copy(smartClockSize = v.toFloat()) } }, { update { it.copy(smartClockSize = 78f) } })
        SsHeader("Stundenformat")
        SsChoice(SmartHours.entries, s.smartHours, { it.label }) { hours -> update { it.copy(smartHours = hours) } }
        SsHeader("Anzeigen")
        SsSwitchRow("Wetter anzeigen", "Wetterbedingungen für deinen Ort (der Ort steht in der Zentrale).", s.smartClockWeather) { v -> update { it.copy(smartClockWeather = v) } }
        SsSwitchRow("Temperatur in Fahrenheit", null, s.smartFahrenheit) { v -> update { it.copy(smartFahrenheit = v) } }
        SsSwitchRow("Nächsten Alarm anzeigen", null, s.smartClockAlarm) { v -> update { it.copy(smartClockAlarm = v) } }
        SsCalendarSwitch(s, update)
        SsSwitchRow("Ganztägige Ereignisse ausblenden", "Schließt ganztägige Ereignisse wie Geburtstage aus.", s.smartHideAllDay) { v -> update { it.copy(smartHideAllDay = v) } }
    }
}

@Composable
internal fun SsDrawerPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, go: (String) -> Unit, back: () -> Unit) {
    SsPage("App-Raster", back) {
        SsLink("Kategorien bearbeiten", "Hinzufügen, umbenennen, verschieben und entfernen", { SsIcon(SsGlyph.Grid) }) { go("tabs") }
        SsSwitchRow("Kategorieleiste", "Die Leiste mit den Kategorien unten. Aus: eine einzige Liste mit allen Apps.", s.smartCategoryBar) { v -> update { it.copy(smartCategoryBar = v) } }
        SsSwitchRow("Kategorientitel", "Titel über den Kategorien einblenden.", s.smartCategoryTitles) { v -> update { it.copy(smartCategoryTitles = v) } }
        SsSwitchRow("Letzte Kategorie geöffnet", "Öffnet das App-Raster wieder auf der zuletzt angezeigten Kategorie.", s.smartLastCategory) { v -> update { it.copy(smartLastCategory = v) } }
        SsSwitchRow("Namen unter den Symbolen", null, s.smartDrawerNames) { v -> update { it.copy(smartDrawerNames = v) } }
        SsHeader("Übergangseffekt der Kategorien")
        SsChoice(SmartPageEffect.entries, s.smartPageEffect, { it.label }) { effect -> update { it.copy(smartPageEffect = effect) } }
        SsHeader("Raster")
        SsIntSlider("Spalten", s.smartDrawerColumns, 3..6, "", { v -> update { it.copy(smartDrawerColumns = v) } }, { update { it.copy(smartDrawerColumns = 4) } })
        SsIntSlider("Abstand zwischen den Icons", s.smartItemSpacing, 0..32, " dp", { v -> update { it.copy(smartItemSpacing = v) } }, { update { it.copy(smartItemSpacing = 0) } })
        SsNote("Eine App in eine andere Kategorie legen: im Menü der App (Halten) „In … verschieben“.")
    }
}

@Composable
internal fun SsSearchPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Suchseite", back) {
        SsSwitchRow("Smart-Suche", "Die Suchleiste ganz unten, unter dem Dock.", s.showSearchPill) { v -> update { it.copy(showSearchPill = v) } }
        SsHeader("Wischen: Runter")
        SsChoice(SwipeDownAction.entries, s.swipeDownAction, { it.label }) { action -> update { it.copy(swipeDownAction = action) } }
    }
}

@Composable
internal fun SsGesturesPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    SsPage("Gesten und Hotkeys", back) {
        SsHeader("Doppelklick")
        SsChoice(HomeGesture.entries, s.doubleTapAction, { it.label }) { gesture -> update { it.copy(doubleTapAction = gesture) } }
    }
}

@Composable
internal fun SsAnimPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onZenith: (String?) -> Unit, back: () -> Unit) {
    SsPage("Animationen", back) {
        SsSwitchRow(
            "Ohne Verzögerung",
            "Apps öffnen sich im Moment des Tippens: kein Fluid-Spritzer, keine Feder am Icon, nur ein kurzes Einblenden. Aus: Die App klappt aus ihrem Icon auf.",
            s.smartInstant,
        ) { v -> update { it.copy(smartInstant = v) } }
        SsSwitchRow(
            "Glimmer ins System binden",
            "Beim Schließen schrumpft jede App – auch mit einem anderen Startbildschirm – zurück in ihr Icon, im Stil des Smart Launchers.",
            s.smartGlimmer,
        ) { v -> update { it.withSmartGlimmer(v) } }
        SsLink("Glimmer-Einstellungen", "Insel um die Kamera, Schließ-Animation", { SsIcon(SsGlyph.Bolt) }) { onZenith("glimmer") }
    }
}

@Composable
internal fun SsClaudeOsPage(onZenith: (String?) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    SsPage("ClaudeOS", back) {
        SsCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SsIcon(SsGlyph.Spark, Color(0xFFE5845F), 44)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("${ClaudeOs.full} „${ClaudeOs.CODENAME}“", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Das System unter ZENITH: Claude im ganzen Telefon.", color = SsDim, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
        SsLink("Claude & KI", "Assistent, Anbieter, Schlüssel, Sparmodus", { SsIcon(SsGlyph.Spark, Color(0xFFE5845F)) }) { onZenith("claude") }
        SsLink("Clawd", "Farbe, Hüte, Begleiter, Widgets", { SsIcon(SsGlyph.Spark) }) { onZenith("clawd") }
        SsLink("Glimmer", "Insel um die Kamera, Schließ-Animation", { SsIcon(SsGlyph.Bolt) }) { onZenith("glimmer") }
        SsLink("Software-Update", null, { SsIcon(SsGlyph.Cloud) }) { ssOpen(context, "UpdateActivity") }
        SsLink("Über das Telefon", null, { SsIcon(SsGlyph.Gear) }) { ssOpen(context, "AboutPhoneActivity") }
    }
}

@Composable
internal fun SsZenithPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onZenith: (String?) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SsPage("ZENITH", back) {
        SsCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                SsIcon(SsGlyph.Z, ssAccent(version), 44)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("ZENITH $version", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Der Launcher, an dem alles hängt.", color = SsDim, fontSize = 14.sp)
                }
            }
            SloganBanner(Modifier.padding(top = 2.dp, bottom = 14.dp), accent = ssAccent(version), size = 34.sp, textAlign = TextAlign.Start)
        }
        SsLink("ZENITH-Zentrale", "Wetter, Schnelleinstellungen, Z-Moment", { SsIcon(SsGlyph.Z, ssAccent(version)) }) { ssOpen(context, "ZentraleActivity") }
        SsLink("Design & Liquid Glass", "Vorlagen, Glas, Icons, Animationen", { SsIcon(SsGlyph.Look) }) { onZenith("design") }
        SsLink("Clawd Illuminati", "Die geheimen Modi, auch „Be Smarter“", { SsIcon(SsGlyph.Spark) }) { ssOpen(context, "IlluminationActivity") }
        SsLink("Warum ZENITH", "Was One UI und ColorOS so nicht können", { SsIcon(SsGlyph.Z) }) { onZenith("vorteile") }
        SsLink("ZENITH Cloud", "Sicherung über dein Google-Konto und als Datei", { SsIcon(SsGlyph.Cloud) }) { onZenith("cloud") }
        SsLink("Datenschutz & Sicherheit", "App-Sperre, ausgeblendete Apps", { SsIcon(SsGlyph.Lock) }) { onZenith("privacy") }
        SsLink("Allgemein", "Suche, Vibration, Sichern & Wiederherstellen", { SsIcon(SsGlyph.Gear) }) { onZenith("general") }
        SsLink("System", "Helligkeit, Töne, WLAN, Bluetooth, Akku …", { SsIcon(SsGlyph.Gear) }) { onZenith("system") }
        SsHeader("Theme")
        ZenithThemeTiles(s.zenithTheme) { theme -> update { it.copy(zenithTheme = theme) } }
        SsNote(s.zenithTheme.note)
        SsSwitchRow(
            "Jubiläums-Theme behalten",
            "Die goldene Karte und die goldenen Akzente von ZENITH 20 bleiben – auch mit ZENITH 21.",
            s.zenithTheme == ZenithTheme.Jubilee,
        ) { v -> update { it.copy(zenithTheme = if (v) ZenithTheme.Jubilee else ZenithTheme.Horizon) } }
        SsLink("Theme auf das ganze System anwenden", "Polarlicht, Glas, Z-Angriff und ZENITH-Hintergrund passend setzen", { SsIcon(SsGlyph.Look) }) {
            update { it.withZenithTheme(it.zenithTheme) }
        }
        SsHeader("Der Spruch")
        SsSwitchRow("„Aim Higher.“ überall", "Der Spruch von ZENITH in jedem Winkel des Systems, der Platz für ihn hat.", s.sloganEverywhere) { v -> update { it.copy(sloganEverywhere = v) } }
        SsHeader("Aussehen")
        SsSwitchRow(
            "Smart-Look (Be Smarter)",
            "Aus: ZENITH sieht wieder aus wie ZENITH – mit seinen eigenen Einstellungen. Alles Gewählte bleibt.",
            s.beSmarter,
        ) { v -> update { it.withSmarter(v) } }
        SsSwitchRow(
            "Comet-Look (Be Comet)",
            "Der Launcher wie CometOS: Liquid Glass, die Flüssig-Uhr, die App-Bibliothek – mit ZENITH im Kern. Schaltet „Be Smarter“ aus.",
            s.beComet,
        ) { v -> update { it.withComet(v) } }
    }
}

/** "Nächsten Termin anzeigen": the switch, and – once, by Android's own dialog – the calendar's permission behind it. */
@Composable
internal fun SsCalendarSwitch(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    val calendarAllowed = {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        update { it.copy(smartClockEvent = ok) }
    }
    SsSwitchRow(
        "Nächsten Termin anzeigen",
        "Zeigt anstehende Termine auf dem Startbildschirm. Erfordert die Kalenderberechtigung; Android fragt einmal.",
        s.smartClockEvent && calendarAllowed(),
    ) { v ->
        if (v && !calendarAllowed()) askCalendar.launch(android.Manifest.permission.READ_CALENDAR) else update { it.copy(smartClockEvent = v) }
    }
}

/** The list moved one place: the item at [index] swapped with its neighbour [delta] away. */
private fun <T> List<T>.swapped(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    val copy = toMutableList()
    val keep = copy[index]
    copy[index] = copy[target]
    copy[target] = keep
    return copy
}

/** "Kategorien bearbeiten": the tabs of the drawer – tapped, one opens its name, its sign and its place. */
@Composable
internal fun SsTabsPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = s.smartTabs
    SsPage("Kategorien bearbeiten", back, trailing = { SsKeyButton { update { it.copy(smartTabs = DefaultSmartTabs, smartCategories = emptyMap()) } } }) {
        SsNote("Die Leiste im App-Raster: Kategorien hinzufügen, umbenennen, verschieben und entfernen. Apps ordnen sich von selbst ein; in eine andere Kategorie legst du eine App im Menü (Halten) – „In … verschieben“. Der Pfeil oben stellt die sechs Kategorien des Anfangs wieder her.")
        tabs.forEachIndexed { index, tab ->
            val expanded = open == tab.id
            SsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = if (expanded) null else tab.id }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmartGlyphIcon(smartGlyphOf(tab.glyph), Color.White)
                    Spacer(Modifier.width(18.dp))
                    Text(tab.title, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = SsDim)
                }
                if (expanded) {
                    SsNameField(tab.id, tab.title) { name ->
                        update { cur -> cur.copy(smartTabs = cur.smartTabs.map { if (it.id == tab.id) it.copy(title = name) else it }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    SmartGlyph.entries.filter { it != SmartGlyph.Bag }.chunked(6).forEach { signs ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            signs.forEach { sign ->
                                val on = sign.name == tab.glyph
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (on) SsBlue.copy(alpha = 0.35f) else SsKey)
                                        .border(if (on) 2.dp else 0.dp, SsBlue, RoundedCornerShape(14.dp))
                                        .clickable {
                                            update { cur -> cur.copy(smartTabs = cur.smartTabs.map { if (it.id == tab.id) it.copy(glyph = sign.name) else it }) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SmartGlyphIcon(sign, Color.White)
                                }
                            }
                            repeat(6 - signs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (index > 0) {
                            SsSmallButton("Nach oben", Modifier.weight(1f)) { update { cur -> cur.copy(smartTabs = cur.smartTabs.swapped(index, -1)) } }
                        }
                        if (index < tabs.lastIndex) {
                            SsSmallButton("Nach unten", Modifier.weight(1f)) { update { cur -> cur.copy(smartTabs = cur.smartTabs.swapped(index, 1)) } }
                        }
                        if (tabs.size > 1) {
                            SsSmallButton("Entfernen", Modifier.weight(1f), danger = true) {
                                update { cur ->
                                    cur.copy(
                                        smartTabs = cur.smartTabs.filter { it.id != tab.id },
                                        smartCategories = cur.smartCategories.filterValues { it != tab.id },
                                    )
                                }
                                open = null
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SsOutlineButton("Kategorie hinzufügen") {
            val id = "c" + System.currentTimeMillis().toString(36)
            update { cur -> cur.copy(smartTabs = cur.smartTabs + SmartTab(id, "Neue Kategorie", "Star")) }
            open = id
        }
    }
}

/** The name of a tab: a field with a round outline; kept here while it is typed. */
@Composable
private fun SsNameField(key: String, value: String, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(value) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                val clean = typed.take(24).filter { it != ';' && it != '|' }
                text = clean
                if (clean.isNotBlank()) onChange(clean)
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SsSmallButton(text: String, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SsKey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) Color(0xFFFF8A80) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** "Warum ZENITH Smart": what is here, free, and what Smart Launcher keeps for its Pro version (as its own texts say). */
@Composable
internal fun SsWhyPage(back: () -> Unit) {
    SsPage("Warum ZENITH Smart", back) {
        SsNote("Wer vom Smart Launcher kommt, findet hier alles wieder – und vieles, das dort Geld kostet, ohne Abo, ohne Werbung.")
        SsWhyRow("Kategorien selbst bauen", "Hinzufügen, umbenennen, verschieben, entfernen – und jede App in eine andere legen.", true)
        SsWhyRow("13 Formen für die Icons", "Squircle, Kreis, Sechseck, Stern, Blume, Schild und mehr – frei wählbar, mit Größe.", true)
        SsWhyRow("Eigene Farbe mit Farbwähler", "Für Ambient Design: Farbfeld, Farbton und der Wert als #RRGGBB.", false)
        SsWhyRow("Keine Werbung, kein Abo", "ZENITH Smart kostet nichts, zeigt keine Werbung und hat keine Pro-Stufe.", true)
        SsWhyRow("App-Sperre mit PIN oder Fingerabdruck", "Im Menü einer App: sperren, ausblenden – ohne Umwege.", true)
        SsWhyRow("Die Suche rechnet und rechnet um", "„12 * 7“ oder „5 km in mi“ – gleich im Suchfeld.", false)
        SsWhyRow("Smart-Widgets und mehr", "Akku-Balken, nächste Termine, dazu die Widgets von ZENITH und Clawd.", false)
        SsWhyRow("Claude im ganzen Telefon", "ClaudeOS: der Assistent in jedem Winkel – mit eigenem Schlüssel und Sparmodus.", false)
        SsWhyRow("Glimmer: die Insel um die Kamera", "Musik, Anrufe, Timer und eine Schließ-Animation für jede App.", false)
        SsWhyRow("Sicherung als Datei", "Alle Einstellungen sichern und wiederherstellen, auf Wunsch mit Passwort.", false)
        SsNote("Die Angaben zu Smart Launcher stammen aus dessen eigenen Texten (Version 6.6, Pro-Version) und können sich ändern. Dein Layout lässt sich nicht übernehmen – das Format ist ein anderes –, aber alle Apps ordnet ZENITH Smart sofort selbst in Kategorien.")
    }
}

@Composable
internal fun SsWhyRow(title: String, text: String, pro: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(28.dp).background(SsBlue, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(14.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.1f, size.height * 0.55f)
                    lineTo(size.width * 0.4f, size.height * 0.85f)
                    lineTo(size.width * 0.92f, size.height * 0.18f)
                }
                drawPath(tick, Color.White, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 19.sp)
            Text(text, color = SsDim, fontSize = 15.sp, lineHeight = 20.sp)
            if (pro) {
                Text("Bei Smart Launcher ein Pro-Feature", color = Color(0xFFFFC27A), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The preview of the theme
// ---------------------------------------------------------------------------------------------

/** The card on top of "Allgemeine Darstellung": a little home screen in the theme's colors, and the list of the themes under it. */
@Composable
internal fun SsPreview(s: LauncherSettings, onPick: (SmartGlass) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val base = if (s.smartColor == SmartColor.Custom) Color(s.smartCustomColor) else s.smartColor.color ?: Color(0xFF8A6A38)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.28f), base, lerp(base, Color.Black, 0.6f))))
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("12:46", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("28 C°", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9ECF2)))
                    Spacer(Modifier.width(18.dp))
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(lerp(base, Color.Black, 0.7f)))
                    Spacer(Modifier.width(18.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(29.dp))
                            .background(lerp(base, Color.Black, 0.45f).copy(alpha = 0.9f)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("50%", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(lerp(base, Color.Black, 0.35f).copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    listOf(SmartGlyph.Chat, SmartGlyph.Social, SmartGlyph.Games, SmartGlyph.Media, SmartGlyph.Tools, SmartGlyph.Gear).forEach { glyph ->
                        SmartGlyphIcon(glyph, Color.White)
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Launcher Theme", color = SsDim, fontSize = 15.sp)
                    Text(s.smartGlass.label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            if (open) {
                if (s.smartGlass.note.isNotEmpty()) {
                    Text(s.smartGlass.note, color = SsDim, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
                }
                Column(Modifier.padding(horizontal = 22.dp)) {
                    SsChoice(SmartGlass.entries, s.smartGlass, { it.label }, onPick)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// What the pages are made of
// ---------------------------------------------------------------------------------------------

/** One page: the back arrow (and what else sits in the bar), the big title, then the rows. */
@Composable
internal fun SsPage(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SsBg)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )
            content()
            Spacer(Modifier.height(56.dp))
        }
    }
}

/** A row that opens another page: a line icon, the name (and what is inside), a chevron. */
@Composable
internal fun SsLink(title: String, subtitle: String?, icon: (@Composable () -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 21.sp)
            if (subtitle != null) {
                Text(subtitle, color = SsDim, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SsDim)
    }
}

@Composable
internal fun SsSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 18.dp),
        ) {
            Text(title, color = Color.White, fontSize = 21.sp, lineHeight = 26.sp)
            if (subtitle != null) {
                Text(subtitle, color = SsDim, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        SsSwitch(checked)
    }
}

/** Smart Launcher's switch: a blue track with the white knob right, a grey one with it left. */
@Composable
internal fun SsSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 24.dp else 0.dp, tween(140), label = "ssSwitch")
    Box(
        Modifier
            .width(58.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(if (on) SsBlue else SsOff)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(28.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
internal fun SsRadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 21.sp, modifier = Modifier.weight(1f).padding(end = 16.dp))
        Box(
            Modifier
                .size(28.dp)
                .border(2.5.dp, if (selected) SsBlue else Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(SsBlue, CircleShape),
                )
            }
        }
    }
}

/** A list of choices with a radio mark each. */
@Composable
internal fun <T> SsChoice(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    options.forEach { option -> SsRadioRow(label(option), option == selected) { onPick(option) } }
}

/** A name with its value on the right, the slider under it and – if wanted – the button that sets it back. */
@Composable
internal fun SsSliderRow(title: String, valueText: String, fraction: Float, onFraction: (Float) -> Unit, onReset: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text(valueText, color = SsDim, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            SsSlider(fraction, onFraction)
        }
        if (onReset != null) {
            Spacer(Modifier.width(12.dp))
            SsKeyButton(onReset)
        }
    }
}

@Composable
internal fun SsIntSlider(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit, onReset: (() -> Unit)?) {
    val lo = range.first.toFloat()
    val hi = range.last.toFloat()
    SsSliderRow(
        title,
        "$value$unit",
        ssFrac(value.toFloat(), lo, hi),
        { f -> onChange(ssUnfrac(f, lo, hi).roundToInt().coerceIn(range.first, range.last)) },
        onReset,
    )
}

/** The slider: a thick track, blue up to the knob, grey after it; tapped or dragged anywhere. */
@Composable
internal fun SsSlider(fraction: Float, onFraction: (Float) -> Unit) {
    var width by remember { mutableStateOf(1f) }
    val current by rememberUpdatedState(onFraction)
    val knob = 20.dp
    val knobPx = with(LocalDensity.current) { knob.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { at -> current((at.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    current((change.position.x / width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(SsOff),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(12.dp)
                .clip(CircleShape)
                .background(SsBlue),
        )
        Box(
            Modifier
                .offset { IntOffset(((width - knobPx) * fraction.coerceIn(0f, 1f)).roundToInt(), 0) }
                .size(knob)
                .background(Color.White, CircleShape),
        )
    }
}

/** The square button with the arrow that goes round: sets a value (or the whole theme) back. */
@Composable
internal fun SsKeyButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SsKey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = "Zurücksetzen", tint = Color.White.copy(alpha = 0.8f))
    }
}

/** The button with the rainbow outline ("Thema Bearbeiten"). */
@Composable
internal fun SsOutlineButton(text: String, onClick: () -> Unit) {
    val rainbow = Brush.sweepGradient(listOf(Color(0xFFFFA726), Color(0xFFEF3F7A), Color(0xFF8E4DE0), Color(0xFF2F8CFF), Color(0xFFFFA726)))
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .border(3.dp, rainbow, RoundedCornerShape(29.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SsHeader(text: String) {
    Text(
        text.uppercase(),
        color = SsDim,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
internal fun SsNote(text: String) {
    Text(text, color = SsDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
internal fun SsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(SsCardColor)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
internal fun SsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SsLine),
    )
}

private fun ssFrac(value: Float, lo: Float, hi: Float): Float = ((value - lo) / (hi - lo)).coerceIn(0f, 1f)

private fun ssUnfrac(fraction: Float, lo: Float, hi: Float): Float = lo + fraction.coerceIn(0f, 1f) * (hi - lo)

/** The color of the system card and the Z: that of the theme (Horizont, or the jubilee's gold kept). */
@Composable
internal fun ssAccent(version: String): Color = zenithAccent(version, LocalSettings.current.zenithTheme)

/** One of the app's own screens (they are in this app's package, whichever app it is). */
private fun ssOpen(context: android.content.Context, activity: String) {
    runCatching {
        context.startActivity(
            Intent().setClassName(context.packageName, "dev.hearth.launcher.$activity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The color picker
// ---------------------------------------------------------------------------------------------

/**
 * "Eigene Farbe": a square of saturation (across) and brightness (up and down) in the chosen hue, the hue's own bar
 * under it, the color as it is written (#RRGGBB) and the eight colors of the list to start from.
 */
@Composable
internal fun SsColorPicker(argb: Int, onChange: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var bright by remember { mutableStateOf(start[2]) }
    var boxW by remember { mutableStateOf(1f) }
    var boxH by remember { mutableStateOf(1f) }
    val current by rememberUpdatedState(onChange)
    val push = { current(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright))) }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .onSizeChanged {
                    boxW = it.width.toFloat().coerceAtLeast(1f)
                    boxH = it.height.toFloat().coerceAtLeast(1f)
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val center = Offset(sat * size.width, (1f - bright) * size.height)
                    drawCircle(Color.Black.copy(alpha = 0.35f), 12.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White, 11.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                }
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        sat = (at.x / boxW).coerceIn(0f, 1f)
                        bright = 1f - (at.y / boxH).coerceIn(0f, 1f)
                        push()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / boxW).coerceIn(0f, 1f)
                        bright = 1f - (change.position.y / boxH).coerceIn(0f, 1f)
                        push()
                    }
                },
        )
        Spacer(Modifier.height(12.dp))
        SsHueSlider(hue) { h ->
            hue = h
            push()
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(Color(argb), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Text(String.format("#%06X", argb and 0xFFFFFF), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            SmartColor.entries.mapNotNull { it.color }.forEach { preset ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preset)
                        .clickable {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(preset.toArgb(), hsv)
                            hue = hsv[0]
                            sat = hsv[1]
                            bright = hsv[2]
                            push()
                        },
                )
            }
        }
    }
}

/** The bar of the hues, the knob in the hue's color. */
@Composable
internal fun SsHueSlider(hue: Float, onHue: (Float) -> Unit) {
    var width by remember { mutableStateOf(1f) }
    val current by rememberUpdatedState(onHue)
    val rainbow = remember {
        Brush.horizontalGradient(List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })
    }
    val knob = 24.dp
    val knobPx = with(LocalDensity.current) { knob.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { at -> current((at.x / width).coerceIn(0f, 1f) * 360f) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    current((change.position.x / width).coerceIn(0f, 1f) * 360f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(rainbow),
        )
        Box(
            Modifier
                .offset { IntOffset(((width - knobPx) * (hue / 360f).coerceIn(0f, 1f)).roundToInt(), 0) }
                .size(knob)
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))), CircleShape)
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The line icons
// ---------------------------------------------------------------------------------------------

internal enum class SsGlyph { Look, Home, Grid, Search, Swipe, Bolt, Spark, Z, Lock, Cloud, Gear, Widget, Font }

/** A line icon of the kind Smart Launcher uses, drawn in a 24 × 24 box of [dp] size. */
@Composable
internal fun SsIcon(glyph: SsGlyph, tint: Color = Color.White, dp: Int = 28) {
    Canvas(Modifier.size(dp.dp)) {
        val u = size.minDimension / 24f
        val line = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        when (glyph) {
            SsGlyph.Look -> {
                drawRoundRect(tint, topLeft = at(3f, 3f), size = Size(18f * u, 18f * u), cornerRadius = CornerRadius(5f * u), style = line)
                drawCircle(tint, 4.5f * u, at(12f, 12f), style = line)
            }
            SsGlyph.Home -> {
                val path = Path().apply {
                    moveTo(3.5f * u, 11f * u)
                    lineTo(12f * u, 4f * u)
                    lineTo(20.5f * u, 11f * u)
                    moveTo(5.5f * u, 9.5f * u)
                    lineTo(5.5f * u, 20f * u)
                    lineTo(18.5f * u, 20f * u)
                    lineTo(18.5f * u, 9.5f * u)
                }
                drawPath(path, tint, style = line)
            }
            SsGlyph.Grid -> {
                for (x in listOf(3.5f, 13.5f)) {
                    for (y in listOf(3.5f, 13.5f)) {
                        drawRoundRect(tint, topLeft = at(x, y), size = Size(7f * u, 7f * u), cornerRadius = CornerRadius(2f * u), style = line)
                    }
                }
            }
            SsGlyph.Search -> {
                drawCircle(tint, 6.5f * u, at(10.5f, 10.5f), style = line)
                drawLine(tint, at(15.5f, 15.5f), at(20.5f, 20.5f), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
            }
            SsGlyph.Swipe -> {
                drawCircle(tint, 3f * u, at(12f, 17f), style = line)
                val arrow = Path().apply {
                    moveTo(12f * u, 12f * u)
                    lineTo(12f * u, 3.5f * u)
                    moveTo(8f * u, 7.5f * u)
                    lineTo(12f * u, 3.5f * u)
                    lineTo(16f * u, 7.5f * u)
                }
                drawPath(arrow, tint, style = line)
            }
            SsGlyph.Bolt -> {
                val bolt = Path().apply {
                    moveTo(13.5f * u, 2.5f * u)
                    lineTo(5.5f * u, 13.5f * u)
                    lineTo(11.5f * u, 13.5f * u)
                    lineTo(10.5f * u, 21.5f * u)
                    lineTo(18.5f * u, 10.5f * u)
                    lineTo(12.5f * u, 10.5f * u)
                    close()
                }
                drawPath(bolt, tint, style = line)
            }
            SsGlyph.Spark -> {
                // Eight rays around a point, as Claude's mark.
                for (k in 0 until 8) {
                    val a = k * (Math.PI / 4.0)
                    val inner = if (k % 2 == 0) 3.5f else 4.5f
                    val outer = if (k % 2 == 0) 10.5f else 8.5f
                    val dx = Math.cos(a).toFloat()
                    val dy = Math.sin(a).toFloat()
                    drawLine(tint, at(12f + dx * inner, 12f + dy * inner), at(12f + dx * outer, 12f + dy * outer), strokeWidth = 2.1f * u, cap = StrokeCap.Round)
                }
            }
            SsGlyph.Z -> {
                // The Z of ZENITH: slanted, a stroke across.
                val z = Path().apply {
                    moveTo(5f * u, 5.5f * u)
                    lineTo(19f * u, 5.5f * u)
                    lineTo(5f * u, 18.5f * u)
                    lineTo(19f * u, 18.5f * u)
                }
                drawPath(z, tint, style = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            SsGlyph.Lock -> {
                drawRoundRect(tint, topLeft = at(5f, 10.5f), size = Size(14f * u, 10f * u), cornerRadius = CornerRadius(3f * u), style = line)
                val shackle = Path().apply {
                    moveTo(8f * u, 10.5f * u)
                    lineTo(8f * u, 8f * u)
                    cubicTo(8f * u, 2.5f * u, 16f * u, 2.5f * u, 16f * u, 8f * u)
                    lineTo(16f * u, 10.5f * u)
                }
                drawPath(shackle, tint, style = line)
            }
            SsGlyph.Cloud -> {
                val cloud = Path().apply {
                    moveTo(7f * u, 18.5f * u)
                    cubicTo(2.5f * u, 18.5f * u, 2.5f * u, 11f * u, 8f * u, 11f * u)
                    cubicTo(8.5f * u, 5f * u, 17f * u, 5f * u, 17.5f * u, 11.5f * u)
                    cubicTo(22f * u, 11.5f * u, 22f * u, 18.5f * u, 17.5f * u, 18.5f * u)
                    close()
                }
                drawPath(cloud, tint, style = line)
            }
            SsGlyph.Gear -> {
                drawCircle(tint, 3.5f * u, at(12f, 12f), style = line)
                for (k in 0 until 8) {
                    val a = k * (Math.PI / 4.0)
                    val dx = Math.cos(a).toFloat()
                    val dy = Math.sin(a).toFloat()
                    drawLine(tint, at(12f + dx * 7.5f, 12f + dy * 7.5f), at(12f + dx * 10f, 12f + dy * 10f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
                }
                drawCircle(tint, 7.5f * u, at(12f, 12f), style = line)
            }
            SsGlyph.Widget -> {
                drawRoundRect(tint, topLeft = at(3.5f, 3.5f), size = Size(7f * u, 7f * u), cornerRadius = CornerRadius(2f * u), style = line)
                drawRoundRect(tint, topLeft = at(3.5f, 13.5f), size = Size(7f * u, 7f * u), cornerRadius = CornerRadius(2f * u), style = line)
                drawRoundRect(tint, topLeft = at(13.5f, 13.5f), size = Size(7f * u, 7f * u), cornerRadius = CornerRadius(2f * u), style = line)
                val diamond = Path().apply {
                    moveTo(17f * u, 3f * u)
                    lineTo(21f * u, 7f * u)
                    lineTo(17f * u, 11f * u)
                    lineTo(13f * u, 7f * u)
                    close()
                }
                drawPath(diamond, tint, style = line)
            }
            SsGlyph.Font -> {
                val a = Path().apply {
                    moveTo(3f * u, 20f * u)
                    lineTo(9.5f * u, 4f * u)
                    lineTo(16f * u, 20f * u)
                    moveTo(5.5f * u, 14.5f * u)
                    lineTo(13.5f * u, 14.5f * u)
                }
                drawPath(a, tint, style = line)
                drawCircle(tint, 2.2f * u, at(19f, 12.5f), style = line)
            }
        }
    }
}
