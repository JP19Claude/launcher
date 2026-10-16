package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.AccentColor
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClockFont
import dev.hearth.launcher.data.CometClockKind
import dev.hearth.launcher.data.CometGlass
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.IconStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.ZenithTheme
import dev.hearth.launcher.data.withComet
import dev.hearth.launcher.data.withSmarter
import dev.hearth.launcher.data.withZenithTheme
import kotlin.math.roundToInt

/*
 * ZENITH 21.5, "Be Comet": the settings, ordered the way Comet Launcher orders its own – wallpaper & style, Liquid
 * Glass, the home screen, the dock and the App Library – on Comet's void with cards of clear glass. At the core,
 * before everything else: ClaudeOS and ZENITH. What has no page of its own here (Claude's key, Clawd, Glimmer's
 * lock screen, the vault …) opens from those two, in ZENITH's own settings.
 */

private val CmCardFill = Color.White.copy(alpha = 0.08f)
private val CmCardLine = Color.White.copy(alpha = 0.16f)
private val CmDim = Color(0xFF9FB0D0)

/** Where the back arrow of a page leads (null: out of the settings). */
private fun cmParent(page: String): String? = if (page == "main") null else "main"

/**
 * The settings while "Be Comet" is on. [onZenith] opens ZENITH's own settings on one of their pages ("claude",
 * "clawd", "glimmer", "design", "vorteile", "cloud", "privacy", "general", "system") or, with null, on their start page.
 */
@Composable
fun CometSettingsScreen(
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onZenith: (String?) -> Unit,
) {
    val s = LocalSettings.current
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = vm::updateSettings
    var page by rememberSaveable { mutableStateOf("main") }
    val back: () -> Unit = {
        val parent = cmParent(page)
        if (parent == null) onClose() else page = parent
    }
    BackHandler { back() }
    when (page) {
        "look" -> CmLookPage(s, update, back)
        "glass" -> CmGlassPage(s, update, back)
        "home" -> CmHomePage(s, update, back)
        "dock" -> CmDockPage(s, update, back)
        "claudeos" -> CmClaudeOsPage(onZenith, back)
        "zenith" -> CmZenithPage(s, update, onZenith, back)
        else -> CmMainPage({ page = it }, onZenith, back)
    }
}

@Composable
private fun CmMainPage(go: (String) -> Unit, onZenith: (String?) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val accent = ssAccent(version)
    CmPage("Einstellungen", back) {
        // The system card: what this is and what it runs on. Tapped: the phone's info.
        CmCard(Modifier.clickable { cmOpen(context, "AboutPhoneActivity") }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SsIcon(SsGlyph.Z, accent, 44)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("ZENITH $version", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                        style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CmDim)
            }
            SloganBanner(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                accent = accent,
                size = 30.sp,
                textAlign = TextAlign.Start,
            )
        }
        // The core: the two systems this launcher is made of.
        CmHeader("Kern")
        CmCard {
            CmLink("ClaudeOS", "Claude & KI, Clawd, Glimmer, Software-Update", SsGlyph.Spark, Color(0xFFE5845F)) { go("claudeos") }
            CmDivider()
            CmLink("ZENITH", "Zentrale, Design, Illuminati, Datenschutz, System", SsGlyph.Z, accent) { go("zenith") }
        }
        CmHeader("Launcher")
        CmCard {
            CmLink("Hintergrund & Stil", "Akzentfarbe, Icons, Abdunkelung", SsGlyph.Look) { go("look") }
            CmDivider()
            CmLink("Super-Wallpaper", "Mond, Erde und Planeten als echte NASA-Bilder – Neigung, Zoom, Seiten", SsGlyph.Look) { onZenith("superwall") }
            CmDivider()
            CmLink("Wallpaper-Galerie", "Mond, Planeten, Weltraum … – zum Auswählen und Herunterladen", SsGlyph.Look) { onZenith("wallpapers") }
            CmDivider()
            CmLink("Liquid Glass", "Klar oder solide, Stärke, Deckkraft", SsGlyph.Widget) { go("glass") }
            CmDivider()
            CmLink("Startbildschirm", "Uhr, Raster, Suchleiste", SsGlyph.Home) { go("home") }
            CmDivider()
            CmLink("Dock & App-Bibliothek", null, SsGlyph.Grid) { go("dock") }
        }
        CmHeader("System")
        CmCard {
            CmLink("Sperrbildschirm & Always-on", "Glimmer: Uhr, Widgets, Fingerabdruck-Animation", SsGlyph.Lock) { onZenith("glimmer") }
            CmDivider()
            CmLink("Claude statt Comet AI", "Assistent, Anbieter, Schlüssel, Sparmodus", SsGlyph.Spark, Color(0xFFE5845F)) { onZenith("claude") }
            CmDivider()
            CmLink("Datenschutz & Sicherheit", "App-Sperre, ausgeblendete Apps", SsGlyph.Lock) { onZenith("privacy") }
            CmDivider()
            CmLink("Sicherung", null, SsGlyph.Cloud) { onZenith("cloud") }
            CmDivider()
            CmLink("Erweiterte Einstellungen", "Alle Einstellungen von ZENITH", SsGlyph.Gear) { onZenith(null) }
        }
        CmNote(
            "Auf dem Startbildschirm: von oben rechts nach unten ziehen für das Kontrollzentrum, von oben links für die Mitteilungen. " +
                "In anderen Apps öffnet das Kontrollzentrum der Streifen am oberen Rand, wenn er in den ZENITH-Einstellungen (System) eingeschaltet ist.",
        )
    }
}

@Composable
private fun CmLookPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    CmPage("Hintergrund & Stil", back) {
        CmHeader("Akzentfarbe")
        CmCard {
            CmSwatches(
                listOf(AccentColor.Galaxy, AccentColor.Blue, AccentColor.Mint, AccentColor.Rose, AccentColor.Lilac, AccentColor.Ochre, AccentColor.White, AccentColor.Claude),
                s.cometAccent,
            ) { accent -> update { it.copy(cometStyle = it.cometStyle.copy(accent = accent)) } }
        }
        CmHeader("Hintergrund")
        CmCard {
            CmSliderRow("Abdunkelung", "${(s.cometDim / 0.6f * 100f).roundToInt()} %", (s.cometDim / 0.6f).coerceIn(0f, 1f)) { f ->
                update { it.copy(cometStyle = it.cometStyle.copy(dim = (f * 0.6f).coerceIn(0f, 0.6f))) }
            }
        }
        CmNote("Dunkelt das Hintergrundbild unter den Icons ab – hilft bei hellen Bildern.")
        CmHeader("Icons")
        CmCard {
            CmChoiceRow("Darstellung", IconStyle.entries, s.cometIcons, { it.label }) { style -> update { it.copy(cometStyle = it.cometStyle.copy(icons = style)) } }
            CmDivider()
            CmChoiceRow("Form", IconShape.entries, s.cometShape, { it.label }) { shape -> update { it.copy(cometStyle = it.cometStyle.copy(shape = shape)) } }
            CmDivider()
            CmSwitchRow("App-Namen zeigen", null, s.cometLabels) { v -> update { it.copy(cometStyle = it.cometStyle.copy(labels = v)) } }
        }
    }
}

@Composable
private fun CmGlassPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    CmPage("Liquid Glass", back) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            CmGlassTile(CometGlass.Liquid, s.cometGlass == CometGlass.Liquid, Modifier.weight(1f)) { update { it.copy(cometStyle = it.cometStyle.copy(glass = CometGlass.Liquid)) } }
            Spacer(Modifier.width(12.dp))
            CmGlassTile(CometGlass.Solid, s.cometGlass == CometGlass.Solid, Modifier.weight(1f)) { update { it.copy(cometStyle = it.cometStyle.copy(glass = CometGlass.Solid)) } }
        }
        CmNote(s.cometGlass.note)
        CmCard {
            CmSliderRow("Stärke", "${(s.cometStrength * 100f).roundToInt()} %", s.cometStrength.coerceIn(0f, 1f)) { f ->
                update { it.copy(cometStyle = it.cometStyle.copy(strength = f.coerceIn(0f, 1f))) }
            }
            CmDivider()
            CmSliderRow("Deckkraft der Flächen", "${(s.cometOpacity * 100f).roundToInt()} %", s.cometOpacity.coerceIn(0f, 1f)) { f ->
                update { it.copy(cometStyle = it.cometStyle.copy(opacity = f.coerceIn(0f, 1f))) }
            }
        }
        CmNote("Die Stärke bestimmt, wie sehr das Glas den Hintergrund weichzeichnet und bricht; die Deckkraft, wie milchig Dock, Widgets und Menüs sind. Mit „Solide Flächen“ gibt es keine Linse – das schont ältere Telefone.")
        CmCard {
            CmLink("Zurücksetzen", "Stärke und Deckkraft wie am Anfang", SsGlyph.Look) { update { it.copy(cometStyle = it.cometStyle.copy(strength = 0.7f, opacity = 0.35f)) } }
        }
    }
}

@Composable
private fun CmHomePage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    CmPage("Startbildschirm", back) {
        CmHeader("Uhr")
        CmCard {
            CmChoiceRow("Uhr", CometClockKind.entries, s.cometClock, { it.label }) { kind -> update { it.copy(cometStyle = it.cometStyle.copy(clock = kind)) } }
            CmDivider()
            CmChoiceRow("Schrift", ClockFont.entries, s.cometClockFont, { it.label }) { font -> update { it.copy(cometStyle = it.cometStyle.copy(clockFont = font)) } }
            CmDivider()
            CmSliderRow("Deckkraft der Uhr", "${(s.cometClockOpacity * 100f).roundToInt()} %", ((s.cometClockOpacity - 0.2f) / 0.8f).coerceIn(0f, 1f)) { f ->
                update { it.copy(cometStyle = it.cometStyle.copy(clockOpacity = (0.2f + 0.8f * f).coerceIn(0.2f, 1f))) }
            }
        }
        CmNote("Die Flüssig-Uhr ist aus Glas, wenn „Liquid Glass“ gewählt ist, sonst weiß und weich. Datum, Wetter und Wecker stehen dabei: ein Tipp aufs Datum öffnet den Kalender, aufs Wetter die ZENITH-Zentrale, auf den Wecker die Wecker.")
        CmHeader("Raster")
        CmCard {
            CmIntSlider("Größe der Icons", s.iconSize, 40..96, " dp") { v -> update { it.copy(iconSize = v) } }
            CmDivider()
            CmIntSlider("Spalten", s.columns, 3..6, "") { v -> update { it.copy(columns = v) } }
            CmDivider()
            CmIntSlider("Reihen", s.rows, 4..8, "") { v -> update { it.copy(rows = v) } }
        }
        CmCard {
            CmSwitchRow("Suchleiste", "Die Pille unter dem Dock; gehalten oder getippt öffnet sie die Suche (mit Claude).", s.showSearchPill) { v ->
                update { it.copy(showSearchPill = v) }
            }
        }
    }
}

@Composable
private fun CmDockPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, back: () -> Unit) {
    CmPage("Dock & App-Bibliothek", back) {
        CmCard {
            CmSwitchRow("Dock als Glas-Kapsel", "Aus: die Icons sitzen direkt auf dem Hintergrund.", s.cometCapsuleDock) { v ->
                update { it.copy(cometStyle = it.cometStyle.copy(capsuleDock = v)) }
            }
            CmDivider()
            CmSwitchRow(
                "App-Bibliothek",
                "Die Seite nach der letzten: alle Apps nach Art geordnet, dazu „Vorgeschlagen“ und die Suche.",
                s.cometLibrary,
            ) { v -> update { it.copy(cometStyle = it.cometStyle.copy(library = v)) } }
        }
        CmNote("Apps legst du mit langem Druck auf einen Platz im Dock oder auf den Startbildschirm; aus dem Dock nimmst du sie mit „Aus dem Dock entfernen“. Sie bleiben installiert und in der App-Bibliothek.")
    }
}

@Composable
private fun CmClaudeOsPage(onZenith: (String?) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    CmPage("ClaudeOS", back) {
        CmCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SsIcon(SsGlyph.Spark, Color(0xFFE5845F), 44)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("${ClaudeOs.full} „${ClaudeOs.CODENAME}“", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Das System unter ZENITH: Claude im ganzen Telefon.", color = CmDim, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
        CmCard {
            CmLink("Claude & KI", "Assistent, Anbieter, Schlüssel, Sparmodus", SsGlyph.Spark, Color(0xFFE5845F)) { onZenith("claude") }
            CmDivider()
            CmLink("Clawd", "Farbe, Hüte, Begleiter, Widgets", SsGlyph.Spark) { onZenith("clawd") }
            CmDivider()
            CmLink("Glimmer", "Insel um die Kamera, Sperrbildschirm, Schließ-Animation", SsGlyph.Bolt) { onZenith("glimmer") }
            CmDivider()
            CmLink("Software-Update", null, SsGlyph.Cloud) { cmOpen(context, "UpdateActivity") }
            CmDivider()
            CmLink("Über das Telefon", null, SsGlyph.Gear) { cmOpen(context, "AboutPhoneActivity") }
        }
    }
}

@Composable
private fun CmZenithPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onZenith: (String?) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val accent = ssAccent(version)
    CmPage("ZENITH", back) {
        CmCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SsIcon(SsGlyph.Z, accent, 44)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ZENITH $version", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Der Launcher, an dem alles hängt.", color = CmDim, fontSize = 14.sp)
                }
            }
            SloganBanner(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                accent = accent,
                size = 30.sp,
                textAlign = TextAlign.Start,
            )
        }
        CmCard {
            CmLink("ZENITH-Zentrale", "Wetter, Schnelleinstellungen, Z-Moment", SsGlyph.Z, accent) { cmOpen(context, "ZentraleActivity") }
            CmDivider()
            CmLink("Design & Liquid Glass", "Vorlagen, Glas, Icons, Animationen", SsGlyph.Look) { onZenith("design") }
            CmDivider()
            CmLink("Clawd Illuminati", "Die geheimen Modi, auch „Be Comet“ und „Be Smarter“", SsGlyph.Spark) { cmOpen(context, "IlluminationActivity") }
            CmDivider()
            CmLink("Warum ZENITH", "Was One UI und ColorOS so nicht können", SsGlyph.Z) { onZenith("vorteile") }
            CmDivider()
            CmLink("ZENITH Cloud", "Sicherung über dein Google-Konto und als Datei", SsGlyph.Cloud) { onZenith("cloud") }
            CmDivider()
            CmLink("Datenschutz & Sicherheit", "App-Sperre, ausgeblendete Apps", SsGlyph.Lock) { onZenith("privacy") }
            CmDivider()
            CmLink("Allgemein", "Suche, Vibration, Sichern & Wiederherstellen", SsGlyph.Gear) { onZenith("general") }
            CmDivider()
            CmLink("System", "Helligkeit, Töne, WLAN, Bluetooth, Akku …", SsGlyph.Gear) { onZenith("system") }
        }
        CmHeader("Theme")
        CmCard {
            Column(Modifier.padding(14.dp)) {
                ZenithThemeTiles(s.zenithTheme) { theme -> update { it.copy(zenithTheme = theme) } }
            }
            CmDivider()
            CmSwitchRow(
                "Jubiläums-Theme behalten",
                "Die goldene Karte und die goldenen Akzente von ZENITH 20 bleiben – auch mit ZENITH 21.",
                s.zenithTheme == ZenithTheme.Jubilee,
            ) { v -> update { it.copy(zenithTheme = if (v) ZenithTheme.Jubilee else ZenithTheme.Horizon) } }
            CmDivider()
            CmLink("Theme auf das ganze System anwenden", "Polarlicht, Glas, Z-Angriff und ZENITH-Hintergrund passend setzen", SsGlyph.Look) {
                update { it.withZenithTheme(it.zenithTheme) }
            }
        }
        CmHeader("Der Spruch")
        CmCard {
            CmSwitchRow("„Aim Higher.“ überall", "Der Spruch von ZENITH in jedem Winkel des Systems, der Platz für ihn hat.", s.sloganEverywhere) { v ->
                update { it.copy(sloganEverywhere = v) }
            }
        }
        CmHeader("Aussehen")
        CmCard {
            CmSwitchRow(
                "Comet-Look (Be Comet)",
                "Aus: ZENITH sieht wieder aus wie ZENITH – mit seinen eigenen Einstellungen. Alles Gewählte bleibt.",
                s.beComet,
            ) { v -> update { it.withComet(v) } }
            CmDivider()
            CmSwitchRow(
                "Smart-Look (Be Smarter)",
                "Der Launcher wie Smart Launcher 6. Schaltet „Be Comet“ aus.",
                s.beSmarter,
            ) { v -> update { it.withSmarter(v) } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The parts
// ---------------------------------------------------------------------------------------------

/** One page: Comet's void, the back arrow in a round of glass, the big title, then the cards. */
@Composable
private fun CmPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CometBlack, CometDeep, CometNavy)))
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, CmCardLine, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            content()
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** A card of clear glass for what belongs together. */
@Composable
private fun CmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CmCardFill)
            .border(1.dp, CmCardLine, RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun CmDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
private fun CmHeader(text: String) {
    Text(
        text.uppercase(),
        color = CmDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun CmNote(text: String) {
    Text(text, color = CmDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
}

/** A row that opens another page or screen: a line icon, the name (and what is inside), a chevron. */
@Composable
private fun CmLink(title: String, subtitle: String?, glyph: SsGlyph?, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { SsIcon(glyph, tint, 26) }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp)
            if (subtitle != null) {
                Text(subtitle, color = CmDim, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CmDim)
    }
}

@Composable
private fun CmSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 14.dp),
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp)
            if (subtitle != null) {
                Text(subtitle, color = CmDim, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        GlassSwitch(checked = checked, onCheckedChange = onChange, onColor = CometBlue)
    }
}

/** A name with its value on the right and the slider under it. */
@Composable
private fun CmSliderRow(title: String, valueText: String, fraction: Float, onFraction: (Float) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = CmDim, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        SsSlider(fraction, onFraction)
    }
}

@Composable
private fun CmIntSlider(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    val span = (range.last - range.first).coerceAtLeast(1).toFloat()
    CmSliderRow(title, "$value$unit", ((value - range.first) / span).coerceIn(0f, 1f)) { f ->
        onChange((range.first + f.coerceIn(0f, 1f) * span).roundToInt().coerceIn(range.first, range.last))
    }
}

/** A name, and under it the choices as pills that scroll sideways. */
@Composable
private fun <T> CmChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val on = option == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) CometBlue else Color.White.copy(alpha = 0.10f))
                        .border(1.dp, if (on) CometBlue else CmCardLine, RoundedCornerShape(18.dp))
                        .clickable { onPick(option) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label(option), color = Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** The accent colors as round swatches. */
@Composable
private fun CmSwatches(options: List<AccentColor>, selected: AccentColor, onPick: (AccentColor) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(option.color)
                    .border(if (on) 3.dp else 1.dp, if (on) Color.White else CmCardLine, CircleShape)
                    .clickable { onPick(option) },
            )
        }
    }
}

/** Liquid Glass or solid: a little picture of each (clear glass with a bright rim, or a flat navy plate). */
@Composable
private fun CmGlassTile(kind: CometGlass, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val liquid = kind == CometGlass.Liquid
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (liquid) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)))
                else Brush.linearGradient(listOf(Color(0xFF223055), Color(0xFF16213F))),
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) CometBlue else CmCardLine, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (liquid) Color.White.copy(alpha = 0.18f) else Color(0xFF2E3F6E))
                .border(1.dp, if (liquid) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Text(kind.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** One of the app's own screens (they are in this app's package). */
private fun cmOpen(context: android.content.Context, activity: String) {
    runCatching {
        context.startActivity(
            android.content.Intent()
                .setClassName(context.packageName, "dev.hearth.launcher.$activity")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
