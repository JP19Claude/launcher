package dev.hearth.launcher.data

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.hearth.launcher.wallpaper.SuperParams
import dev.hearth.launcher.wallpaper.SuperScene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class IconStyle(val label: String) {
    Original("Original"),
    Glass("Glas"),
    Clear("Klar"),
    Tinted("Getönt"),
}

enum class IconShape(val label: String) {
    Squircle("Squircle"),
    Circle("Kreis"),
    Rounded("Abgerundet"),
    /** ZENITH 20.5, "Be Smarter": shapes of Smart Launcher's "Form". */
    Square("Quadrat"),
    Teardrop("Tropfen"),
    Leaf("Blatt"),
    Cut("Kante"),
    /** ZENITH 21: the other shapes of Smart Launcher's list, free here. */
    Hexagon("Sechseck"),
    Octagon("Achteck"),
    Diamond("Raute"),
    Star("Stern"),
    Flower("Blume"),
    Shield("Schild"),
}

enum class ClockStyle(val label: String) {
    /** ZENITH 19: the sun's arc over the time – where the sun stands now, by day; the moon by night. */
    Zenith("ZENITH (Sonnenbogen)"),
    /** OMEGA UI 17: thin numerals in the accent's light, the seconds as a line under them. */
    Omega("Schmal"),
    /** OMEGA UI 17: a glass dial with a ruby at twelve, the date beside it. */
    Analog("Analog-Glas"),
    /** OMEGA UI 17: "Viertel nach drei". */
    Words("In Worten"),
    /** OMEGA UI 17: hours big, minutes in the accent over the date. */
    Split("Geteilt"),
    /** Hearth UI 14: hours over minutes, big and stacked, with glass chips for date and battery. */
    Stacked("Gestapelt"),
    ColorOS("ColorOS"),
    /** Samsung's home clock: big, bold, tight digits over the date. */
    OneUI("One UI"),
    Glass("Glas-Karte"),
    Large("Groß"),
    /** ZENITH 20.5, "Be Smarter": the Smart Launcher's clock – light digits, the date, the weather beside them. */
    Smart("Smart"),
    /** ZENITH 21.5, "Be Comet": big soft digits of glass, the date over them. */
    Comet("Comet (Flüssig-Uhr)"),
    Hidden("Aus"),
}

/** How strongly Claude's colors show round glass that stands still (flowing rims stay vivid). */
enum class FluidRims(val label: String) {
    /** Hearth UI 14: calm glass with only a hint of Claude's colors. */
    Calm("Dezent"),
    Vivid("Lebendig"),
}

/** Hearth UI's dark mode: darker glass – always, never, like Android or by the clock. */
enum class NightMode(val label: String) {
    Off("Aus"),
    On("An"),
    System("Wie Android"),
    Auto("Nach Uhrzeit"),
}

/** Base color the glass is tinted with. */
enum class GlassTint(val label: String, val color: Color) {
    Claude("Claude", Color(0xFFD97757).copy(alpha = 0.14f)),
    /** Cool blue-violet, like One UI's glass. */
    Galaxy("Galaxy", Color(0xFF7C8CFF).copy(alpha = 0.16f)),
    Clear("Klar", Color.White.copy(alpha = 0.04f)),
    Light("Hell", Color.White.copy(alpha = 0.20f)),
    Dark("Dunkel", Color.Black.copy(alpha = 0.26f)),
    Warm("Warm", Color(0xFFC8952E).copy(alpha = 0.18f)),
    Blue("Blau", Color(0xFF6FA8FF).copy(alpha = 0.18f)),
    /** OMEGA UI: glass with a breath of ruby. */
    Ruby("Rubin", Color(0xFFB0102C).copy(alpha = 0.16f)),
    /** OMEGA UI 17.3: deep Galaxy blue glass (the ruby comes from OMEGA Fluid). */
    GalaxyOmega("Galaxy Zenith", Color(0xFF1E46B4).copy(alpha = 0.2f)),
    /** ZENITH 19: sunlit glass with a breath of green. */
    Zenith("Sonnenglas", Color(0xFF3FBF7F).copy(alpha = 0.13f)),
}

/**
 * OMEGA UI 17.5, Clawd Illumination: the whole system in another color world (its hue turned
 * by [degrees]); Spektrum runs through all of them.
 */
enum class ColorWorld(val label: String, val degrees: Float) {
    Off("Aus", 0f),
    Emerald("Smaragd", 135f),
    Sapphire("Saphir", 215f),
    Amethyst("Amethyst", 285f),
    Gold("Gold", 40f),
    Spectrum("Spektrum", 0f),
}

/** OMEGA UI 18.1: the clock on the full Always On Display. */
enum class AodClock(val label: String) {
    /** ZENITH 19: the sun's arc over the time, the moon at night. */
    Zenith("Sonnenbogen"),
    Omega("Schmal"),
    Big("Groß"),
    Thin("Dünn"),
    Words("In Worten"),
}

/** How bright the wallpaper shows behind the full Always On Display. */
enum class AodBrightness(val label: String, val light: Float) {
    Bright("Hell", 0.55f),
    Medium("Mittel", 0.35f),
    Dark("Dunkel", 0.18f),
}

/** The color of the Always On Display's clock. */
enum class AodTint(val label: String) {
    White("Weiß"),
    Omega("ZENITH"),
    Gold("Gold"),
}

/**
 * ZENITH 19.2, Projekt Zenith's "ColorOS 17.1" look is on: the whole system as ColorOS dresses it
 * – frosted glass, round squircle icons, the ColorOS clock and quick panel – with the glass
 * adjustable freely. (Only seen, never saved over what's chosen: see [withColorOsLook].)
 */
val LauncherSettings.colorOsLook: Boolean get() = projectZenith && projectColorOs

/** Projekt Zenith's hologram look: on with Projekt Zenith, off in the ColorOS 17.1 look. */
val LauncherSettings.hologram: Boolean get() = projectZenith && !projectColorOs

/** ZENITH 20, Aurora: the colors of the curtains of light moving over the home screen. */
enum class AuroraLook(val label: String, val colors: List<Color>) {
    Zenith("Zenith-Grün", listOf(Color(0xFF2FD27A), Color(0xFF3EE0C8), Color(0xFF5CC8FF))),
    Gold("Jubiläums-Gold", listOf(Color(0xFFFFD34D), Color(0xFFFF9A3D), Color(0xFFFFF1B0))),
    Eis("Eisblau", listOf(Color(0xFF3FA9FF), Color(0xFF7DE3FF), Color(0xFF9A6BFF))),
    Violett("Violett", listOf(Color(0xFF9A6BFF), Color(0xFFFF6FB5), Color(0xFF5CC8FF))),
    Rubin("Rubin", listOf(Color(0xFFFF4155), Color(0xFFFF9A3D), Color(0xFFFF6FB5))),
}

/**
 * ZENITH 20.1: ZENITH's color [tint] in one tap, system-wide – the Z's light, the HUD and the cards
 * follow it, and the aurora, the Z-Angriff, the system accent and the glass are set to match.
 */
fun LauncherSettings.withZenithTint(tint: ZenithTint): LauncherSettings = copy(
    zenithTint = tint,
    auroraLook = tint.aurora,
    strikeColor = tint.strike,
    accent = tint.accent,
    glassTint = tint.glass,
)

/** ZENITH 19.3, Core Enforcer Studio: the color of the Z-Angriff's light. */
enum class StrikeColor(val label: String, val color: Color) {
    Green("Zenith-Grün", Color(0xFF2FD27A)),
    Blue("Eisblau", Color(0xFF3FA9FF)),
    Violet("Violett", Color(0xFF9A6BFF)),
    Red("Rubin", Color(0xFFFF4155)),
    Gold("Gold", Color(0xFFFFB82E)),
    White("Weiß", Color(0xFFDDE8F0)),
}

/** ZENITH 19.3, Core Enforcer Studio: how long the Z-Angriff takes. */
enum class StrikeSpeed(val label: String, val millis: Int) {
    Fast("Schnell", 1600),
    Normal("Normal", 2400),
    Slow("Langsam", 3600),
}

/** ZENITH 19: every surface of dark metal with ZENITH's green edge light instead of glass. */
val LauncherSettings.zenithMetal: Boolean get() = !colorOsLook && (glassLook == GlassLook.Zenith || projectZenith)

/**
 * ZENITH 19.2: the ColorOS 17.1 look laid over what's chosen, when drawing – the home screen,
 * its menus and the settings see it, but it's never written back, so switching it off brings
 * everything exactly as it was. The glass itself stays the user's: blur, lens, color fringes,
 * shine and tint come from the glass settings.
 */
fun LauncherSettings.withColorOsLook(): LauncherSettings = if (!colorOsLook) {
    this
} else {
    copy(
        galaxyClaude = false,
        iconStyle = IconStyle.Original,
        iconShape = IconShape.Squircle,
        clockStyle = colorOsClock,
        dockStyle = colorOsDock,
        showGreeting = true,
        showSearchPill = true,
        zenithWallpaper = false,
        zenithAurora = false,
        omegaGlass = false,
        glassLook = if (glassLook == GlassLook.Zenith) GlassLook.Liquid else glassLook,
    ).withCcStyle(CcStyle.ColorOS)
}

/** ZENITH 20.5, "Be Smarter": how the clock sits on the home screen. */
enum class SmartClockLayout(val label: String) {
    Center("Zentriert"),
    Compact("Kompakt (Zeit links, Wetter rechts)"),
}

/** ZENITH 20.5, "Übergangseffekt der Kategorien": how the pages of the Smart drawer move into one another. */
enum class SmartPageEffect(val label: String) {
    Slide("Schieben"),
    Fade("Überblenden"),
    Zoom("Zoomen"),
    Rotate("Drehen"),
    Flip("3D-Wendung"),
    Stretch("Strecken"),
}

/** ZENITH 20.5, "Stundenformat" of the Smart clock. */
enum class SmartHours(val label: String) {
    System("Wie im System"),
    H24("24 Stunden"),
    H12("12 Stunden"),
}

/**
 * ZENITH 21, the theme of the system's cards: "Horizont", the new one – deep blue into the light of the horizon –
 * or "Jubiläum", the midnight and gold of ZENITH 20, kept.
 */
enum class ZenithTheme(val label: String, val note: String) {
    Horizon("ZENITH 21 · Horizont", "Das neue Theme: tiefes Blau ins Licht des Horizonts, eisblaue Akzente."),
    Jubilee("Jubiläum · Gold", "Das Theme von ZENITH 20 bleibt: Mitternacht ins Gold, die goldene Karte, goldene Akzente."),
}

/** ZENITH 21, the whole system in a theme's colors at once (the aurora, the Z, the glass …) – and the theme itself. */
fun LauncherSettings.withZenithTheme(theme: ZenithTheme): LauncherSettings = when (theme) {
    ZenithTheme.Horizon -> CoreProfiles.horizonLook(this)
    ZenithTheme.Jubilee -> CoreProfiles.jubileeLook(this)
}.copy(zenithTheme = theme)

/** ZENITH 20.5, "Thema bearbeiten": the color Ambient Design tints its surfaces with – the wallpaper's, or one of these. */
enum class SmartColor(val label: String, val color: Color?) {
    Auto("Wie das Hintergrundbild", null),
    Indigo("Indigo", Color(0xFF3A3F8F)),
    Violet("Violett", Color(0xFF6A3F9A)),
    Teal("Petrol", Color(0xFF1F6F78)),
    Green("Grün", Color(0xFF2F7A4F)),
    Amber("Bernstein", Color(0xFF8A5A1E)),
    Red("Rot", Color(0xFF8A2F3A)),
    Rose("Rosé", Color(0xFF9A3F6F)),
    Slate("Schiefer", Color(0xFF3A444E)),
    /** ZENITH 21: a color of one's own, from the picker ([LauncherSettings.smartCustomColor]). */
    Custom("Eigene Farbe", null),
}

/**
 * ZENITH 20.5, "Be Smarter": the five themes of Smart Launcher 6 ("Allgemeine Darstellung"). Ambient
 * Design and Material You are flat, translucent surfaces in the wallpaper's (or the phone's) colors;
 * Frosted is milky and soft, Opaque the most solid (readable on any wallpaper, follows light and
 * dark), Liquid clear with a lens – it bends what lies behind it and catches the light. Choosing one
 * sets the sliders (blur radius, refraction, depth, dispersion, vibrancy …); they stay free after.
 */
enum class SmartGlass(
    val label: String,
    val note: String,
    val blur: Float,
    val refraction: Float,
    val dispersion: Float,
    val specular: Float,
    val tint: Float,
    val depth: Float,
    val vibrancy: Float,
) {
    Ambient("Ambient Design", "Transparente Oberflächen, die den Farben deines Hintergrundbildes entsprechen.", 1f, 0.15f, 0f, 0.3f, 1f, 1f, 0.2f),
    MaterialYou("Material You", "Die Farben deines Telefons (ab Android 12) als weiche, durchscheinende Flächen.", 1f, 0.15f, 0f, 0.3f, 1f, 1f, 0.2f),
    Frosted("Frostetes Glas", "", 2.1f, 0.4f, 0.1f, 0.7f, 1.7f, 1f, 0.5f),
    Opaque("Opakisches Glas", "", 2.5f, 0.15f, 0f, 0.4f, 2.4f, 1f, 0.3f),
    Liquid("Liquid Glass", "Dynamische, durchscheinende Oberflächen mit fließenden Lichtreflexen für einen dynamischen Effekt.", 0.7f, 1.7f, 0.9f, 1.4f, 0.6f, 1f, 0.5f),
}

/** The mode's glass, set to what [glass] brings (the four sliders move to its values). */
fun LauncherSettings.withSmartGlass(glass: SmartGlass): LauncherSettings = copy(
    smartGlass = glass,
    smartBlur = glass.blur,
    smartRefraction = glass.refraction,
    smartDispersion = glass.dispersion,
    smartSpecular = glass.specular,
    smartTint = glass.tint,
    smartDepth = glass.depth,
    smartVibrancy = glass.vibrancy,
    // Liquid Glass shows its icons in round glass tiles; the others in adaptive squircles.
    smartShape = if (glass == SmartGlass.Liquid) IconShape.Circle else IconShape.Squircle,
)

/** ZENITH's saying is shown (it is ZENITH's own: only in the all-in-one app). */
val LauncherSettings.showSlogan: Boolean get() = sloganEverywhere && dev.hearth.launcher.BuildConfig.ALL_IN_ONE

/** Apps open the moment they are tapped (only in "Be Smarter", and only while that is chosen). */
/**
 * ZENITH 21: a tab of the Smart drawer. [id] is what apps are sorted into, [title] its name, [glyph] the name of its sign
 * (a SmartGlyph). The six of Smart Launcher are the start; tabs can be added, renamed, moved and removed.
 */
data class SmartTab(val id: String, val title: String, val glyph: String)

/** The six tabs the drawer starts with. */
val DefaultSmartTabs: List<SmartTab> = listOf(
    SmartTab("comm", "Kommunikation", "Chat"),
    SmartTab("social", "Social", "Social"),
    SmartTab("games", "Spiele", "Games"),
    SmartTab("media", "Medien", "Media"),
    SmartTab("tools", "Dienstprogramme", "Tools"),
    SmartTab("prefs", "Einstellungen", "Gear"),
)

/** The tabs as they are kept: "id|name|sign;id|name|sign". */
fun smartTabsText(tabs: List<SmartTab>): String =
    tabs.joinToString(";") { "${it.id.filter { c -> c.isLetterOrDigit() }}|${it.title.filter { c -> c != ';' && c != '|' }}|${it.glyph.filter { c -> c.isLetterOrDigit() }}" }

/** The tabs from [smartTabsText]; the six of the start when there is nothing (usable) kept. */
fun parseSmartTabs(text: String?): List<SmartTab> {
    val tabs = text.orEmpty().split(';').mapNotNull { part ->
        val bits = part.split('|')
        if (bits.size == 3 && bits[0].isNotBlank() && bits[1].isNotBlank()) SmartTab(bits[0], bits[1], bits[2]) else null
    }
    return tabs.ifEmpty { DefaultSmartTabs }
}

/** The drawer's moved apps as they are kept: "package=tab;package=tab" (older versions kept the tab's number). */
fun parseSmartCategories(text: String?): Map<String, String> =
    text.orEmpty().split(';').mapNotNull { part ->
        val at = part.lastIndexOf('=')
        if (at <= 0 || at == part.length - 1) {
            null
        } else {
            val tab = part.substring(at + 1)
            val id = tab.toIntOrNull()?.let { DefaultSmartTabs.getOrNull(it)?.id } ?: tab
            part.substring(0, at) to id
        }
    }.toMap()

val LauncherSettings.instantOpen: Boolean get() = beSmarter && smartInstant

/**
 * ZENITH 20.5, "Be Smarter": the launcher dressed as the Smart Launcher, when drawing – round
 * icons, its clock with the weather, the dock, the drawer with its category bar, and the glass
 * Frosted or Liquid by the mode's own sliders. It is never written back, so switching it off
 * brings everything exactly as it was. ZENITH's own looks and effects step aside meanwhile.
 */
fun LauncherSettings.withSmartLook(): LauncherSettings = if (!beSmarter) {
    this
} else {
    copy(
        projectZenith = false,
        projectColorOs = false,
        galaxyClaude = false,
        zenithWallpaper = false,
        zenithAurora = false,
        omegaGlass = false,
        fluidDesign = false,
        omegaOverdrive = false,
        hyperGlass = false,
        colorWorld = ColorWorld.Off,
        omegaRain = false,
        weightless = false,
        clawdParade = false,
        giantClawd = false,
        allSeeingEye = false,
        clawdHalo = false,
        clawdCompanion = false,
        omegaLight = false,
        omegaZeros = false,
        perks = emptySet(),
        showClaudeCard = false,
        // Liquid Glass shows its icons on glass tiles; the other themes as they are.
        iconStyle = if (smartGlass == SmartGlass.Liquid) IconStyle.Glass else IconStyle.Original,
        iconShape = smartShape,
        clockStyle = ClockStyle.Smart,
        // The dock has no bar of its own: the icons sit right on the wallpaper, and no names under them.
        dockStyle = DockStyle.Clear,
        showLabels = false,
        showGreeting = false,
        showAppLibrary = false,
        animations = true,
        glassLook = if (smartGlass == SmartGlass.Liquid) GlassLook.Liquid else GlassLook.Omega,
        // Opaque Glass follows light and dark; Frosted is a soft white veil; Liquid hardly any color.
        glassTint = when (smartGlass) {
            SmartGlass.Liquid -> GlassTint.Clear
            SmartGlass.Opaque -> if (darkGlass) GlassTint.Dark else GlassTint.Light
            SmartGlass.Frosted -> GlassTint.Light
            // Ambient Design and Material You tint the glass with their own color (see the home screen).
            SmartGlass.Ambient, SmartGlass.MaterialYou -> GlassTint.Dark
        },
        // "Lesbarkeit verbessern": a darker veil over the wallpaper, for light ones.
        dimWallpaper = if (smartReadable) maxOf(dimWallpaper, 0.42f) else dimWallpaper,
        // Swiping down from the middle brings the search and the apps you might want next.
        swipeDownAction = SwipeDownAction.Search,
        glassBlur = smartBlur,
        glassRefraction = smartRefraction,
        glassDispersion = smartDispersion,
        glassSpecular = smartSpecular,
        glassTintStrength = smartTint,
        glassInteractive = true,
    )
}

/**
 * ZENITH 20.5, "Be Smarter", Glimmer bound into the system: Glimmer on, the fly-in over every
 * launcher with the launcher's own closing animation covered, and the Smart style for it. (Written
 * for real, because Glimmer's service reads what is stored, not what is drawn.) Off, the
 * style goes back to the one before if it was the Smart one.
 */
fun LauncherSettings.withSmartGlimmer(on: Boolean): LauncherSettings = if (on) {
    copy(
        smartGlimmer = true,
        glimmerEnabled = true,
        glimmerFlyIn = true,
        glimmerFlyInEverywhere = true,
        glimmerFlyInCover = true,
        glimmerFlyInStyle = FlyInStyle.Smart,
        animations = true,
    )
} else {
    copy(
        smartGlimmer = false,
        glimmerFlyInStyle = if (glimmerFlyInStyle == FlyInStyle.Smart) FlyInStyle.HarmonyOS else glimmerFlyInStyle,
    )
}

/** ZENITH 21.5, "Be Comet": the surfaces – Liquid Glass (clear, bending what is behind it) or solid. */
enum class CometGlass(val label: String, val note: String) {
    Liquid("Liquid Glass", "Klares Glas, das den Hintergrund bricht und Licht an seinem Rand sammelt."),
    Solid("Solide Flächen", "Ruhige, flache Flächen ohne Linse – schonend für ältere Telefone."),
}

/** ZENITH 21.5, "Be Comet": the look's own choices (see [LauncherSettings.cometStyle]). */
data class CometStyle(
    val glass: CometGlass = CometGlass.Liquid,
    /** How much the glass frosts and bends what lies behind it (0–1). */
    val strength: Float = 0.7f,
    /** How strongly the glass is tinted – how see-through the dock and the widgets are (0 clear … 1 milky). */
    val opacity: Float = 0.35f,
    val shape: IconShape = IconShape.Squircle,
    val icons: IconStyle = IconStyle.Original,
    val labels: Boolean = true,
    val clock: CometClockKind = CometClockKind.Liquid,
    val clockFont: ClockFont = ClockFont.Default,
    val clockOpacity: Float = 0.92f,
    /** The wallpaper's dimming (0–0.6). */
    val dim: Float = 0.14f,
    /** The dock as a floating capsule of glass (off: the icons sit on the wallpaper). */
    val capsuleDock: Boolean = true,
    /** The App Library, the page after the last home page. */
    val library: Boolean = true,
    val accent: AccentColor = AccentColor.Galaxy,
)

/** ZENITH 21.5, "Be Comet": the home clock. */
enum class CometClockKind(val label: String) {
    Liquid("Flüssig-Uhr (Glas)"),
    Serif("Serif"),
    Analog("Analog"),
}

/**
 * ZENITH 21.5, "Be Comet": the launcher dressed after CometOS, when drawing – Liquid Glass or solid
 * surfaces, big glass digits for the clock, the labels, the dock as a capsule of glass, the App Library
 * after the last page, the control center and notifications from the top corners. Never written back, so
 * switching it off brings everything exactly as it was; ZENITH's own looks and effects step aside meanwhile.
 * (If "Be Smarter" is on too, that one is drawn.)
 */
fun LauncherSettings.withCometLook(): LauncherSettings = if (!beComet || beSmarter) {
    this
} else {
    val liquid = cometGlass == CometGlass.Liquid
    copy(
        projectZenith = false,
        projectColorOs = false,
        galaxyClaude = false,
        zenithWallpaper = false,
        zenithAurora = false,
        omegaGlass = false,
        fluidDesign = false,
        omegaOverdrive = false,
        hyperGlass = false,
        colorWorld = ColorWorld.Off,
        omegaRain = false,
        weightless = false,
        clawdParade = false,
        giantClawd = false,
        allSeeingEye = false,
        clawdHalo = false,
        clawdCompanion = false,
        omegaLight = false,
        omegaZeros = false,
        perks = emptySet(),
        showClaudeCard = false,
        iconStyle = cometIcons,
        iconShape = cometShape,
        clockStyle = if (cometClock == CometClockKind.Analog) ClockStyle.Analog else ClockStyle.Comet,
        clockFont = if (cometClock == CometClockKind.Serif) ClockFont.Serif else cometClockFont,
        // The digits are glass on Liquid Glass; plain, soft white on solid surfaces.
        glassClocks = liquid,
        dockStyle = if (cometCapsuleDock) DockStyle.Floating else DockStyle.Clear,
        showLabels = cometLabels,
        showGreeting = false,
        showAppLibrary = cometLibrary,
        animations = true,
        accent = cometAccent,
        glassLook = if (liquid) GlassLook.Liquid else GlassLook.Omega,
        glassTint = if (liquid) GlassTint.Clear else GlassTint.Dark,
        dimWallpaper = cometDim,
        // Pulled down from the left corner: the notifications; from the right: the control center.
        swipeDownAction = SwipeDownAction.Split,
        glassBlur = if (liquid) 0.6f + 1.6f * cometStrength else 2.2f,
        glassRefraction = if (liquid) 0.4f + 2.0f * cometStrength else 0.1f,
        glassDispersion = if (liquid) 0.1f + 0.9f * cometStrength else 0f,
        glassSpecular = if (liquid) 0.6f + 1.2f * cometStrength else 0.3f,
        glassTintStrength = if (liquid) 0.3f + 1.7f * cometOpacity else 1f + 1.2f * cometOpacity,
        glassInteractive = true,
    )
}

/** ZENITH 21.5: "Be Comet" on or off – it and "Be Smarter" are two looks for the same screen, never both. */
fun LauncherSettings.withComet(on: Boolean): LauncherSettings = copy(beComet = on, beSmarter = if (on) false else beSmarter)

/** ZENITH 21.5: "Be Smarter" on or off (on turns "Be Comet" off). */
fun LauncherSettings.withSmarter(on: Boolean): LauncherSettings = copy(beSmarter = on, beComet = if (on) false else beComet)

/** ZENITH 19.3: the clock as it's shown – in the ColorOS 17.1 look, that look's own choice. */
val LauncherSettings.shownClock: ClockStyle get() = if (colorOsLook) colorOsClock else clockStyle

/** ZENITH 19.3: the dock as it's shown – in the ColorOS 17.1 look, that look's own choice. */
val LauncherSettings.shownDock: DockStyle get() = if (colorOsLook) colorOsDock else dockStyle

/** Picks the clock for what's shown now: in the ColorOS 17.1 look its own, else the usual one. */
fun LauncherSettings.withClock(style: ClockStyle): LauncherSettings = if (colorOsLook) copy(colorOsClock = style) else copy(clockStyle = style)

/** Picks the dock for what's shown now: in the ColorOS 17.1 look its own, else the usual one. */
fun LauncherSettings.withDock(style: DockStyle): LauncherSettings = if (colorOsLook) copy(colorOsDock = style) else copy(dockStyle = style)

/** OMEGA UI 18.3: OMEGA Glass in the "Liquid Glass" color – clear like Apple's. */
val LauncherSettings.liquidOmega: Boolean get() = glassLook == GlassLook.Liquid || (omegaGlass && omegaColor == OmegaColor.Liquid)

/**
 * OMEGA UI 18.5: which glass the whole system is made of – OMEGA Glass (tinted, OMEGA Fluid in
 * its colors) or Liquid Glass like Apple's (clear, a deep lens, bright edges, white light).
 */
enum class GlassLook(val label: String) {
    /** ZENITH 19: no glass – dark metal like the logo, silver on top, green light at the foot. */
    Zenith("ZENITH Metall"),
    Omega("ZENITH Glass"),
    Liquid("Liquid Glass (wie Apple)"),
}

/** OMEGA UI 18.1: does anything of the lock screen or the full AOD need Glimmer's stage? */
fun LauncherSettings.wantsLockStage(): Boolean =
    fullAod || lockClawd || lockEdge || lockCharge || lockGreeting || lockAtmosphere || lockBattery || lockOmega || lockMessage.isNotBlank()

/** The color OMEGA Glass and OMEGA Fluid run in. */
enum class OmegaColor(val label: String) {
    /** ZENITH 19: green, sun and sky – the colors of the sun at its highest point. */
    Zenith("Zenith"),
    Ruby("Rubin"),
    GalaxyOmega("Galaxy Zenith"),
    /** OMEGA UI 18.3: clear, colorless glass like Apple's Liquid Glass – light, lens and shine. */
    Liquid("Liquid Glass"),
}

/** Which glass gets the real lens (bending, color split); smaller glass gets the drawn edge. */
enum class GlassQuality(val label: String, val lensMinDp: Float) {
    High("Flüssig (auch Knöpfe)", 52f),
    Balanced("Ausgewogen (große Flächen)", 96f),
}

/** Color for tinted icons and highlights. */
enum class AccentColor(val label: String, val color: Color) {
    Claude("Claude", Color(0xFFD97757)),
    /** Samsung's One UI blue. */
    Galaxy("Galaxy-Blau", Color(0xFF4F8DFF)),
    White("Weiß", Color(0xFFF5F5F5)),
    Ochre("Ocker", Color(0xFFE0B158)),
    Blue("Blau", Color(0xFF8DB8FF)),
    Mint("Mint", Color(0xFF8FE3C0)),
    Rose("Rosé", Color(0xFFFFA8C0)),
    Lilac("Flieder", Color(0xFFC6A8FF)),
    /** OMEGA UI's ruby red. */
    Ruby("Rubin", Color(0xFFE5243F)),
    /** OMEGA UI 17.3: Galaxy blue – no more violet; the ruby comes from OMEGA Fluid. */
    GalaxyOmega("Galaxy Zenith", Color(0xFF3D7BFF)),
    /** ZENITH 19: the green of the 19 in ZENITH's mark. */
    Zenith("Zenith-Grün", Color(0xFF2FD27A)),
}

enum class SearchEngine(val label: String, val url: String?) {
    Default("Standard", null),
    Google("Google", "https://www.google.com/search?q="),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q="),
    Ecosia("Ecosia", "https://www.ecosia.org/search?q="),
    Bing("Bing", "https://www.bing.com/search?q="),
    Startpage("Startpage", "https://www.startpage.com/do/search?q="),
}

/** What swiping down on the home screen does. */
enum class SwipeDownAction(val label: String) {
    Search("Suche (Finder)"),
    Split("Links Mitteilungen, rechts Schnelleinstellungen"),
    ControlCenter("Schnelleinstellungen"),
    Notifications("Mitteilungen"),
}

/** Strip along the top edge that opens the Hearth control center in every app. */
enum class TriggerZone(val label: String, val fraction: Float) {
    RightHalf("Rechte Hälfte", 0.5f),
    RightThird("Rechtes Drittel", 0.34f),
    Full("Ganze Breite", 1f),
}

/** How home pages move when swiping between them. */
enum class PageTransition(val label: String) {
    Depth("Tiefe"),
    Cube("Würfel"),
    Flat("Flach"),
}

/** How unread notifications show on app icons. */
enum class BadgeStyle(val label: String) {
    Number("Zahl"),
    Dot("Punkt"),
    Off("Aus"),
}

/** What Glimmer shows while the phone unlocks. */
enum class GlimmerUnlock(val label: String) {
    FaceId("Face ID"),
    Fingerprint("Fingerabdruck"),
    Off("Aus"),
}

/** Glimmer with its own extras, or as close to the iPhone's Dynamic Island as it gets. */
enum class GlimmerMode(val label: String) {
    Glimmer("Glimmer"),
    DynamicIsland("Dynamic Island 1:1"),
}

/** The color Glimmer shimmers in when something new arrives. */
enum class GlimmerGlowColor(val label: String) {
    Activity("Farbe der Aktivität"),
    Accent("Akzentfarbe"),
    White("Weiß"),
    Rainbow("Regenbogen"),
}

/** A fine edge around the island. */
enum class GlimmerOutline(val label: String) {
    Off("Aus"),
    Subtle("Dezent"),
    Colored("In Farbe der Aktivität"),
}

/** How springy Glimmer moves. */
/** Where Glimmer sits with the phone held sideways. */
enum class GlimmerSideways(val label: String) {
    /** Turned with the phone: upright along the edge, the camera in it, like held upright. */
    Upright("Senkrecht"),
    /** Lying across from the camera's edge into the screen. */
    Across("Quer"),
    /** At the top in the middle, away from the camera (only while something runs). */
    Top("Oben"),
}

enum class GlimmerMotion(val label: String) {
    Calm("Ruhig"),
    Normal("Normal"),
    Playful("Verspielt"),
}

/** What a double tap on the island does. */
enum class GlimmerDoubleTap(val label: String) {
    Off("Nichts"),
    PlayPause("Musik Play/Pause"),
    Torch("Taschenlampe"),
    Screenshot("Bildschirmfoto"),
    Claude("Claude"),
    Oracle("Clawd-Orakel"),
    Coin("Münze werfen"),
    Dice("Würfeln"),
}

/** How a closing app flies into Glimmer. */
enum class FlyInStyle(val label: String) {
    HarmonyOS("HarmonyOS"),
    HyperOS("HyperOS"),
    /** ZENITH 20.5, "Be Smarter": the app shrinks back into its icon, as it does in the Smart Launcher. */
    Smart("Smart Launcher"),
}

/** How Glimmer shows music. */
enum class GlimmerMusicStyle(val label: String) {
    Hyper("Hyper Island (Xiaomi)"),
    Classic("Klassisch"),
    /** OPPO's and OnePlus' Fluid Cloud: a spinning cover with a progress ring, time left. */
    FluidCloud("Fluid Cloud (OPPO/OnePlus)"),
}

/** Look of Glimmer, the island around the front camera. */
enum class GlimmerStyle(val label: String) {
    Black("Schwarz wie die Kamera"),
    Glass("Liquid Glass"),
    /** Dark, tinted in the activity's color (like vivo's and OPPO's colored capsules). */
    Tinted("Farbig getönt"),
    /** Black with Claude's colors slowly flowing inside, like an AI thinking (AI Fluid). */
    Fluid("AI Fluid"),
}

/** Overall look of control center and notifications. */
enum class CcStyle(val label: String) {
    IOS("iOS 27"),
    ColorOS("ColorOS 17"),
    OneUI("One UI 9"),
}

/** Brightness and volume sliders in the control center. */
enum class CcSliderStyle(val label: String) {
    Tall("Hoch (ColorOS)"),
    Wide("Breit (One UI)"),
}

/** Color of switches that are on in the control center. */
enum class CcColorMode(val label: String) {
    IOS("iOS"),
    Accent("Akzentfarbe"),
    Multicolor("Bunt (ColorOS 16)"),
    White("Weiß"),
}

/** What the control center's glass panels are tinted with. */
enum class CcGlassTint(val label: String) {
    Clear("Klar"),
    Frosted("Milchig"),
    Dark("Dunkel"),
    Accent("Akzentfarbe"),
    Launcher("Wie der Launcher"),
}

/** Shape of the round switches in the control center. */
enum class CcToggleShape(val label: String) {
    Circle("Rund"),
    Rounded("Abgerundet"),
}

/** Ready-made looks that set many options at once. */
enum class DesignPreset(val label: String) {
    /** One UI's look with liquid glass, and Claude woven through the launcher. */
    GalaxyClaude("Galaxy × Claude (ZENITH)"),
    ColorOSClaude("ColorOS × Claude"),
    IOSGlass("iOS Liquid Glass"),
    Hearth("${dev.hearth.launcher.data.Brand.name} Klassik"),
}

/** What a double tap on an empty spot of the home screen does. */
enum class HomeGesture(val label: String) {
    Off("Nichts"),
    Lock("Bildschirm sperren"),
    Claude("Claude öffnen"),
    Search("Suche"),
    Drawer("App-Übersicht"),
    Notifications("Mitteilungen"),
    Torch("Taschenlampe"),
    Menu("ZENITH-Menü"),
    Power("Ein/Aus-Menü"),
    Vault("ZENITH-Tresor"),
    Shield("ZENITH-Schutzschild"),
    Strike("Z-Angriff"),
    Hub("Z-Zentrale"),
    Night("Z-Nacht"),
}

/** The home clock's typeface. */
enum class ClockFont(val label: String) {
    Default("Wie der Look"),
    Sans("Schlicht"),
    Serif("Serif"),
    Mono("Digital"),
    Thin("Hauchdünn"),
    Bold("Fett"),
}

/** Size of the app names under the icons. */
enum class LabelSize(val label: String, val sp: Float) {
    Small("Klein", 10.5f),
    Normal("Normal", 12f),
    Large("Groß", 13.5f),
}

/** How the dock looks. */
enum class DockStyle(val label: String) {
    Glass("Glasleiste"),
    Floating("Schwebende Glas-Kapsel"),
    Clear("Ohne Hintergrund"),
    /** OMEGA UI 17: a dark glass island just around the icons, a ruby glow along its rim. */
    Island("ZENITH-Insel"),
    /** OMEGA UI 17: the full bar, tinted ruby. */
    Ruby("Rubin-Glas"),
    /** ZENITH 19: a slanted plate of the Z's dark metal, green light along its foot. */
    Zenith("ZENITH-Dock"),
}

/** The color of Clawd's clothes; [color] null keeps each outfit's own colors. */
enum class ClawdCloth(val label: String, val color: Color?) {
    Original("Original", null),
    Ruby("Rubin", Color(0xFFC8102E)),
    Navy("Marine", Color(0xFF243A6B)),
    Black("Schwarz", Color(0xFF1C1C21)),
    Forest("Tanne", Color(0xFF2E6B45)),
    Camel("Camel", Color(0xFFB98A55)),
    Sky("Himmel", Color(0xFF5AA5E8)),
    Lilac("Flieder", Color(0xFF9B7FD4)),
    Cream("Creme", Color(0xFFEDE3CF)),
}

/** Clawd's color; the rainbow one changes color as he moves. */
enum class ClawdSkin(val label: String, val color: Color) {
    Terracotta("Terrakotta", Color(0xFFD97757)),
    Ocean("Ozean", Color(0xFF5B8DEF)),
    Mint("Minze", Color(0xFF4CB782)),
    Lavender("Lavendel", Color(0xFF9B7BE0)),
    Gold("Gold", Color(0xFFE5B53A)),
    Rose("Rosa", Color(0xFFF07AA8)),
    Snow("Schnee", Color(0xFFF2F0EA)),
    Midnight("Mitternacht", Color(0xFF5A6A8C)),
    Rainbow("Regenbogen", Color(0xFFD97757)),
    /** OMEGA UI 17.2: deep space – indigo, violet and magenta drifting, with little stars. */
    Galaxy("Galaxy", Color(0xFF6A4BE0)),
}

/** What Clawd wears on his head (or his eyes). */
enum class ClawdHat(val label: String) {
    None("Nichts"),
    Party("Partyhut"),
    Crown("Krone"),
    Beanie("Mütze"),
    Cap("Cap"),
    Bow("Schleife"),
    Headphones("Kopfhörer"),
    Sunglasses("Sonnenbrille"),
    Wizard("Zauberhut"),
    Chef("Kochmütze"),
    Halo("Heiligenschein"),
    Horns("Hörner"),
    ;

    /** Sits above his head, so he needs a little more room up there. */
    val tall: Boolean get() = this != None && this != Sunglasses
}

/** Which side of the camera Clawd sits on in Glimmer. */
enum class ClawdSide(val label: String) {
    Left("Links"),
    Right("Rechts"),
}

/** What Clawd wears on his body – his wardrobe. */
enum class ClawdOutfit(val label: String) {
    None("Nichts"),
    Suit("Anzug"),
    Tuxedo("Smoking"),
    Hero("Superheld"),
    Astronaut("Astronaut"),
    Doctor("Arzt"),
    Chef("Koch"),
    Pirate("Pirat"),
    Ninja("Ninja"),
    Sweater("Pulli"),
    Scarf("Schal"),
    /** OMEGA UI 17.1: a long coat with a collar and buttons. */
    Coat("Mantel"),
    Hoodie("Hoodie"),
    Raincoat("Regenjacke"),
    Overalls("Latzhose"),
    ;

    /** The space helmet reaches above his head. */
    val tall: Boolean get() = this == Astronaut
}

/** Order of the apps in One UI's app drawer. */
enum class DrawerSort(val label: String) {
    Alphabet("A–Z"),
    Newest("Neueste zuerst"),
    MostUsed("Meistgenutzt"),
}

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Hell"),
    Dark("Dunkel"),
}

@Immutable
data class LauncherSettings(
    // Liquid glass
    val glassBlur: Float = 1f,
    val glassRefraction: Float = 1.6f,
    val glassDispersion: Float = 0.8f,
    val glassSpecular: Float = 1.15f,
    val glassTintStrength: Float = 1f,
    val glassTint: GlassTint = GlassTint.Clear,
    val glassMotion: Boolean = true,
    val glassInteractive: Boolean = true,
    val glassQuality: GlassQuality = GlassQuality.High,
    // Icons
    val iconStyle: IconStyle = IconStyle.Glass,
    val iconShape: IconShape = IconShape.Squircle,
    val iconSize: Int = 58,
    val showLabels: Boolean = true,
    val iconPack: String? = null,
    val adaptUnthemedIcons: Boolean = true,
    val accent: AccentColor = AccentColor.White,
    // Home screen
    val columns: Int = 4,
    val rows: Int = 6,
    val clockStyle: ClockStyle = ClockStyle.Glass,
    val showGreeting: Boolean = true,
    val showBattery: Boolean = true,
    val dimWallpaper: Float = 0.2f,
    val showSearchPill: Boolean = true,
    val swipeOpensSearch: Boolean = true,
    val swipeDownAction: SwipeDownAction = SwipeDownAction.Search,
    val showClaudeCard: Boolean = true,
    /**
     * Galaxy × Claude: Claude woven into the launcher like Galaxy AI into One UI – the Now Brief
     * card on the home screen, Claude first in the search, "Ask Claude" in every app's menu.
     */
    val galaxyClaude: Boolean = false,
    /** One UI app drawer: order of the apps, and the suggested apps on top. */
    val drawerSort: DrawerSort = DrawerSort.Alphabet,
    val drawerSuggestions: Boolean = true,
    val dockStyle: DockStyle = DockStyle.Glass,
    val doubleTapAction: HomeGesture = HomeGesture.Lock,
    /** Clawd: his color, what he wears, and whether he walks around on the home screen. */
    val clawdSkin: ClawdSkin = ClawdSkin.Terracotta,
    val clawdHat: ClawdHat = ClawdHat.None,
    val clawdOutfit: ClawdOutfit = ClawdOutfit.None,
    /** OMEGA UI 17.1: the color of his clothes, and the Ω on his chest. */
    val clawdCloth: ClawdCloth = ClawdCloth.Original,
    val clawdOmega: Boolean = false,
    /** OMEGA UI 17.1: ruby glass and OMEGA Fluid (ruby, rose, amber) all through the system. */
    val omegaGlass: Boolean = true,
    val omegaColor: OmegaColor = OmegaColor.Zenith,
    /** ZENITH 19: Zenith's colors follow the sun – gold in the morning, green at noon, glow at dusk, moon blue at night. */
    val zenithDaylight: Boolean = true,
    /** ZENITH 19: ZENITH's own background on the home screen instead of the wallpaper. */
    val zenithWallpaper: Boolean = false,
    /** ZENITH 20, Aurora: curtains of light drift over the home screen's background. */
    val zenithAurora: Boolean = false,
    val auroraLook: AuroraLook = AuroraLook.Zenith,
    /** The aurora's strength (0.3–1.5) and speed (0.3–2.5). */
    val auroraStrength: Float = 0.85f,
    val auroraSpeed: Float = 1f,
    /** ZENITH 20.1: ZENITH's color, system-wide (the green of the Z's light, the HUD, the cards – or gold, ice, violet, ruby). */
    val zenithTint: ZenithTint = ZenithTint.Green,
    /** ZENITH 20.1, the home screen's light: how strong (0–2), in which color, and which parts shine. */
    val homeLightStrength: Float = 1f,
    val homeLightColor: HomeLightColor = HomeLightColor.Follow,
    val homeLightBeam: Boolean = true,
    val homeLightGlint: Boolean = true,
    val homeLightFloor: Boolean = true,
    /** ZENITH 20.5, "Be Smarter": the Illuminati's mode that makes the launcher a copy of the Smart Launcher. */
    val beSmarter: Boolean = false,
    /** ZENITH 21.5, "Be Comet": the Illuminati's second mode – the launcher dressed after CometOS, with ZENITH at its core. */
    val beComet: Boolean = false,
    /**
     * ZENITH 21.5, "Be Comet": the look's own choices – in one field of their own, because a data class with more than about
     * 245 fields can't be copied on the phone any more (its copy needs more registers than Android's code format allows).
     * Read them by the old names (cometGlass, cometAccent …); write with `copy(cometStyle = cometStyle.copy(accent = …))`.
     */
    val cometStyle: CometStyle = CometStyle(),
    /** ZENITH 21.5, the Super-Wallpaper (a live wallpaper): its scene, how it moves and how fast – one field, for the same reason. The wallpaper service reads the keys itself. */
    val superParams: SuperParams = SuperParams(),
    /** The glass of that look (Frosted or Liquid) and its four sliders, which are the mode's own. */
    val smartGlass: SmartGlass = SmartGlass.Ambient,
    val smartBlur: Float = SmartGlass.Ambient.blur,
    val smartRefraction: Float = SmartGlass.Ambient.refraction,
    val smartDispersion: Float = SmartGlass.Ambient.dispersion,
    val smartSpecular: Float = SmartGlass.Ambient.specular,
    val smartTint: Float = SmartGlass.Ambient.tint,
    val smartDepth: Float = SmartGlass.Ambient.depth,
    val smartVibrancy: Float = SmartGlass.Ambient.vibrancy,
    /** The form of the icons, the clock's size and what it shows, and the drawer's columns and names. */
    val smartShape: IconShape = IconShape.Squircle,
    val smartClockSize: Float = 78f,
    val smartClockWeather: Boolean = true,
    val smartClockAlarm: Boolean = true,
    val smartDrawerColumns: Int = 4,
    val smartDrawerNames: Boolean = true,
    val smartClockLayout: SmartClockLayout = SmartClockLayout.Center,
    val smartColor: SmartColor = SmartColor.Auto,
    /** Apps open the moment they are tapped: no splash, no press animation, no wait. */
    val smartInstant: Boolean = true,
    /** Glimmer bound into the whole system: its fly-in over every launcher, in the Smart style. */
    val smartGlimmer: Boolean = false,
    /** The clock shows the next event of the calendar ("Termin · 11:00 Zahnarzt"; asks the calendar's permission once). */
    val smartClockEvent: Boolean = false,
    /** How round the widgets' corners are (dp). */
    val smartWidgetRadius: Int = 28,
    /** Apps the user moved to another tab of the drawer: package name → the tab's number. */
    val smartCategories: Map<String, String> = emptyMap(),
    /** ZENITH 21: the tabs of the drawer – the six of Smart Launcher, or the ones made. */
    val smartTabs: List<SmartTab> = DefaultSmartTabs,
    /** The drawer: the bar of the categories, its titles, remembering the last one, the pages' effect, the room between rows. */
    val smartCategoryBar: Boolean = true,
    val smartCategoryTitles: Boolean = false,
    val smartLastCategory: Boolean = true,
    val smartPageEffect: SmartPageEffect = SmartPageEffect.Slide,
    val smartItemSpacing: Int = 0,
    /** The dots that show the page you are on. */
    val smartIndicator: Boolean = true,
    /** "Lesbarkeit verbessern": a darker veil over the wallpaper. */
    val smartReadable: Boolean = false,
    /** The clock: °F instead of °C, the hours' format, no all-day events in the line of the next event. */
    val smartFahrenheit: Boolean = false,
    val smartHours: SmartHours = SmartHours.System,
    val smartHideAllDay: Boolean = false,
    /** ZENITH 21: the color picked for Ambient Design with "Eigene Farbe" (ARGB). */
    val smartCustomColor: Int = 0xFF3A3F8F.toInt(),
    /** ZENITH 21: the theme of the system's cards – the new one, or the jubilee's gold kept. */
    val zenithTheme: ZenithTheme = ZenithTheme.Horizon,
    /** ZENITH 20.5: the saying "Aim Higher." in every place of the system that has room for it. */
    val sloganEverywhere: Boolean = true,
    /** ZENITH 19: after the phone starts, the Z lights up once before the home screen. */
    val zenithBootIntro: Boolean = true,
    /** ZENITH 19, Z-Angriff: a fully charged Z draws a giant Z across the screen, like Zygarde's Core Enforcer. */
    val zenithStrike: Boolean = true,
    /** ZENITH 19: the Z-Angriff over everything, every time the phone is unlocked (through Glimmer). */
    val zenithStrikeSystem: Boolean = false,
    /** ZENITH 19.3, Core Enforcer Studio: the color and tempo of the Z-Angriff. */
    val strikeColor: StrikeColor = StrikeColor.Green,
    val strikeSpeed: StrikeSpeed = StrikeSpeed.Normal,
    /** ZENITH 19.5, Z-Momente: where else the Z-Angriff comes – after an update, opening the vault, rolling the die, plugging in, full. */
    val strikeOnUpdate: Boolean = true,
    val strikeOnVault: Boolean = true,
    val strikeOnDice: Boolean = true,
    val strikeOnCharge: Boolean = false,
    val strikeOnFull: Boolean = false,
    /** ZENITH 19.5, Akku-Wächter: a word in the island when charging reaches [chargeLimit] %, to spare the battery. */
    val chargeGuard: Boolean = false,
    val chargeLimit: Int = 85,
    /** ZENITH 19.5, Neue-App-Wächter: a newly installed app shows in the island with what it wants to use. */
    val newAppGuard: Boolean = true,
    /** ZENITH 19, "Projekt Zenith": the whole system futuristic – holograms, HUD, a grid horizon. */
    val projectZenith: Boolean = false,
    /** ZENITH 19.1, "Projekt Zenith Glas": Projekt Zenith in real liquid glass, green and lit. */
    val projectZenithGlass: Boolean = false,
    /** ZENITH 19.2, Projekt Zenith's "ColorOS 17.1" look: the whole system as ColorOS dresses it, glass adjustable. */
    val projectColorOs: Boolean = false,
    /** ZENITH 19.3: the clock and dock of the ColorOS 17.1 look (its own, apart from the usual ones). */
    val colorOsClock: ClockStyle = ClockStyle.ColorOS,
    val colorOsDock: DockStyle = DockStyle.Floating,
    /** OMEGA UI 17.1: flowing effects at a calmer rate and lighter glass – smoother, less lag. */
    val smoothMode: Boolean = true,
    /** OMEGA UI 17.5: OMEGA light between the wallpaper and the home screen's apps and widgets. */
    val omegaLight: Boolean = true,
    /**
     * OMEGA UI 17.5 – the Clawd Illumination, hidden behind the Ω crystal: big, wild changes
     * to the whole system. [illumination] is set once it has been found (then it's in Settings).
     */
    val illumination: Boolean = false,
    val omegaOverdrive: Boolean = false,
    val hyperGlass: Boolean = false,
    val colorWorld: ColorWorld = ColorWorld.Off,
    val omegaRain: Boolean = false,
    val weightless: Boolean = false,
    val clawdParade: Boolean = false,
    val omegaZeros: Boolean = false,
    val clawdHalo: Boolean = false,
    val giantClawd: Boolean = false,
    /** OMEGA UI 17.6, Clawd Illuminati: the all-seeing eye watching over the home screen. */
    val allSeeingEye: Boolean = false,
    /** OMEGA UI 18, Clawd Illuminati: the perks switched on (names of [Perk]). */
    val perks: Set<String> = emptySet(),
    /** OMEGA UI 18: Claude Mythos – while on, the Clawds take over the whole system. */
    val mythos: Boolean = false,
    /**
     * OMEGA UI 18.1 – lock screen and Always On Display (drawn by Glimmer over them): the full
     * AOD like a Galaxy S24 Ultra (the wallpaper dimmed behind the clock), its clock, light and
     * what it shows; and what lies on the lock screen.
     */
    val aodClock: AodClock = AodClock.Zenith,
    val aodBrightness: AodBrightness = AodBrightness.Medium,
    val aodTint: AodTint = AodTint.Omega,
    val lockMessage: String = "",
    /** OMEGA UI 18.2: an own picture for the full AOD (file in the app's storage; "" = wallpaper). */
    val aodImage: String = "",
    /** OMEGA UI 18.5: the glass of the whole system (see [GlassLook]). */
    val glassLook: GlassLook = GlassLook.Omega,
    /** OMEGA UI 18.5: every clock on the home screen made of glass. */
    val glassClocks: Boolean = true,
    /** OMEGA UI 18.5: a strip of glass behind the status bar on the home screen and in the drawer. */
    val glassStatusBar: Boolean = true,
    val fullAod: Boolean = false,
    val aodClawd: Boolean = true,
    val aodBattery: Boolean = true,
    val aodNotifications: Boolean = true,
    val aodEdge: Boolean = true,
    val aodDate: Boolean = true,
    val lockClawd: Boolean = false,
    val lockEdge: Boolean = false,
    val lockCharge: Boolean = false,
    val lockGreeting: Boolean = false,
    val lockAtmosphere: Boolean = false,
    val lockBattery: Boolean = false,
    val lockOmega: Boolean = false,
    val clawdCompanion: Boolean = true,
    val clockFont: ClockFont = ClockFont.Default,
    val labelSize: LabelSize = LabelSize.Normal,
    val showAppLibrary: Boolean = true,
    val showWidgetPage: Boolean = true,
    val triggerZone: TriggerZone = TriggerZone.RightHalf,
    /** Close the system's own shade whenever it opens and show Hearth's instead. */
    val interceptSystemShade: Boolean = false,
    /** Apps taken off the home screen; they stay in the App Library and search. */
    val removedFromHome: Set<String> = emptySet(),
    /** Specular sheen on every icon that follows the phone's tilt. */
    val iconGloss: Boolean = true,
    /** Entrance, page and text animations throughout the launcher. */
    val animations: Boolean = true,
    /** One UI 10 "Fluid": touches spread like liquid light, colors drift behind the screens. */
    val fluidDesign: Boolean = true,
    /** Hearth UI 14: how colorful the still rims are. */
    val fluidRims: FluidRims = FluidRims.Calm,
    val pageTransition: PageTransition = PageTransition.Depth,
    /** Hearth's own control center; off = only the system's (One UI) panel. */
    val ccEnabled: Boolean = true,
    // Control center look
    val ccStyle: CcStyle = CcStyle.IOS,
    val ccBigTiles: Boolean = true,
    val ccSliders: CcSliderStyle = CcSliderStyle.Tall,
    val ccColors: CcColorMode = CcColorMode.IOS,
    val ccShape: CcToggleShape = CcToggleShape.Circle,
    val ccGlow: Boolean = false,
    val ccLabels: Boolean = true,
    val ccShowClock: Boolean = true,
    val ccShowMedia: Boolean = true,
    val ccShowShortcuts: Boolean = true,
    /** How much the control center darkens what's behind it (0..1). */
    val ccDim: Float = 0.5f,
    /** Swipe right in the control center for Hearth's own notification list. */
    val ccNotifications: Boolean = true,
    // Control center glass
    val ccGlassTint: CcGlassTint = CcGlassTint.Clear,
    /** How milky the panels are (0 = clear, 1 = nearly solid). */
    val ccGlassOpacity: Float = 0.35f,
    /** Light-catching rim and sheen. */
    val ccSpecular: Float = 1.35f,
    /** Lens bending at the edges (with the wallpaper behind). */
    val ccRefraction: Float = 1.4f,
    /** How blurred the app behind is (0..1). */
    val ccBlur: Float = 0.6f,
    /** Corner radius of the panels, in dp. */
    val ccCorner: Int = 30,
    /** Size of the round switches, in dp. */
    val ccToggleSize: Int = 56,
    // Glimmer: live activities around the front camera, in every app
    val glimmerEnabled: Boolean = true,
    val glimmerMode: GlimmerMode = GlimmerMode.Glimmer,
    val glimmerStyle: GlimmerStyle = GlimmerStyle.Black,
    val glimmerIdlePill: Boolean = true,
    /** Clawd sits in the island beside the camera: asleep at night, dancing on the charger (shared with Glimmer). */
    val glimmerClawd: Boolean = false,
    /** Where he sits: which side, how close to the camera (0 = beside the content, 1 = at the camera), how big. */
    val glimmerClawdSide: ClawdSide = ClawdSide.Left,
    val glimmerClawdSpot: Float = 0.55f,
    val glimmerClawdSize: Float = 1f,
    /** Clawd says hello in Glimmer when the phone is unlocked (every time, or once a day). */
    val glimmerClawdGreeting: Boolean = true,
    val glimmerClawdGreetDaily: Boolean = false,
    /** Clawd also in the empty pill, when nothing is going on. */
    val glimmerClawdIdle: Boolean = true,
    val glimmerMessages: Boolean = true,
    /** Tap opens the app (like the iPhone) instead of unfolding; holding does the other. */
    val glimmerTapOpens: Boolean = false,
    /** Short moments: earbuds connected, Do not disturb, fully charged. */
    val glimmerAlerts: Boolean = true,
    /** A soft glow in the activity's color when something new appears. */
    val glimmerGlow: Boolean = true,
    /** Closing an app: it shrinks and flies into Glimmer, like on the iPhone. */
    val glimmerFlyIn: Boolean = true,
    val glimmerFlyInStyle: FlyInStyle = FlyInStyle.HarmonyOS,
    /** The fly-in with any launcher (One UI, ColorOS, Pixel …): Glimmer plays it itself. */
    val glimmerFlyInEverywhere: Boolean = true,
    /**
     * With another launcher: a picture of the home screen hides the launcher's own closing
     * animation (the app shrinking back into its icon), so only the flight into Glimmer shows.
     */
    val glimmerFlyInCover: Boolean = true,
    val glimmerMusicStyle: GlimmerMusicStyle = GlimmerMusicStyle.Hyper,
    val glimmerUnlock: GlimmerUnlock = GlimmerUnlock.FaceId,
    /** Keep showing music and live activities on the always-on display (dimmed, still). */
    val glimmerAod: Boolean = true,
    val glimmerGlowColor: GlimmerGlowColor = GlimmerGlowColor.Activity,
    val glimmerOutline: GlimmerOutline = GlimmerOutline.Off,
    /** Nudges the island off the camera, in dp (for phones where it doesn't sit quite right). */
    val glimmerOffsetX: Int = 0,
    val glimmerOffsetY: Int = 0,
    /** How wide the small island is, relative to normal. */
    val glimmerWidth: Float = 1f,
    val glimmerMotion: GlimmerMotion = GlimmerMotion.Normal,
    /** Held sideways: upright around the camera (as if turned with the phone), across, or at the top. */
    val glimmerSideways: GlimmerSideways = GlimmerSideways.Upright,
    /** Seconds until an unfolded island folds back up by itself; 0 = never. */
    val glimmerAutoCollapse: Int = 9,
    val glimmerDoubleTap: GlimmerDoubleTap = GlimmerDoubleTap.Off,
    /** Swiping sideways over music skips tracks (when there's no second activity). */
    val glimmerSwipeTracks: Boolean = true,
    /** Keep the battery in the island while charging. */
    val glimmerCharging: Boolean = false,
    val glimmerHaptics: Boolean = true,
    /** Videos and games without a status bar: only what matters stays (a call, an alarm). */
    val glimmerHideFullscreen: Boolean = false,
    /** One-time codes from notifications sit in the island, a tap copies them (vivo, OPPO). */
    val glimmerCodes: Boolean = true,
    /** A screenshot shows as a little picture in the island (vivo's Origin Island). */
    val glimmerScreenshots: Boolean = true,
    /** A soft light breathes around the island while something runs (OPPO's Fluid Cloud). */
    val glimmerBreathe: Boolean = false,
    /** AI Fluid: Claude's colors flow round the island's edge while something runs. */
    val glimmerAiFluid: Boolean = true,
    /** Night mode, its hours (Auto) and whether Android goes dark with it (Shizuku). */
    val nightMode: NightMode = NightMode.Off,
    val nightFrom: Int = 21,
    val nightTo: Int = 7,
    val nightSystem: Boolean = true,
    /** Dark mode right now: the glass is tinted dark (set while running, never saved). */
    val darkGlass: Boolean = false,
    /** A green or orange dot in Glimmer while an app uses the camera or the microphone. */
    val glimmerPrivacy: Boolean = true,
    /** 85 % of the size, and unfolded content kept below the camera so it never covers any. */
    val glimmerSmall: Boolean = false,
    val badgeStyle: BadgeStyle = BadgeStyle.Number,
    /** Double tap on empty home screen space locks the phone (needs the accessibility service). */
    val doubleTapLock: Boolean = true,
    // Dock
    val dockSize: Int = 4,
    val dockCustomized: Boolean = false,
    val dockApps: List<String> = emptyList(),
    // General
    val searchEngine: SearchEngine = SearchEngine.Default,
    val theme: ThemeMode = ThemeMode.System,
    val haptics: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    /** App lock: packages that need the phone's PIN, fingerprint or face to open. */
    val lockedApps: Set<String> = emptySet(),
    /** Bumped when a new default look should be applied once to existing installs. */
    val designVersion: Int = 0,
) {
    // Be Comet's choices and the Super-Wallpaper's, by the names they had before they moved into their own classes.
    val cometGlass: CometGlass get() = cometStyle.glass
    val cometStrength: Float get() = cometStyle.strength
    val cometOpacity: Float get() = cometStyle.opacity
    val cometShape: IconShape get() = cometStyle.shape
    val cometIcons: IconStyle get() = cometStyle.icons
    val cometLabels: Boolean get() = cometStyle.labels
    val cometClock: CometClockKind get() = cometStyle.clock
    val cometClockFont: ClockFont get() = cometStyle.clockFont
    val cometClockOpacity: Float get() = cometStyle.clockOpacity
    val cometDim: Float get() = cometStyle.dim
    val cometCapsuleDock: Boolean get() = cometStyle.capsuleDock
    val cometLibrary: Boolean get() = cometStyle.library
    val cometAccent: AccentColor get() = cometStyle.accent
    val superScene: SuperScene get() = superParams.scene
    val superTilt: Boolean get() = superParams.tilt
    val superZoom: Boolean get() = superParams.zoomOnWake
    val superSpeed: Float get() = superParams.speed
    val superStars: Boolean get() = superParams.stars

    /** Glimmer's size and place as they come: normal width, right over the camera. */
    fun withGlimmerSizeReset(): LauncherSettings {
        val d = LauncherSettings()
        return copy(
            glimmerWidth = d.glimmerWidth,
            glimmerOffsetX = d.glimmerOffsetX,
            glimmerOffsetY = d.glimmerOffsetY,
            glimmerSmall = d.glimmerSmall,
        )
    }

    /** Is Glimmer's size and place as they come? */
    val glimmerSizeIsDefault: Boolean
        get() = LauncherSettings().let { d ->
            glimmerWidth == d.glimmerWidth && glimmerOffsetX == d.glimmerOffsetX && glimmerOffsetY == d.glimmerOffsetY &&
                glimmerSmall == d.glimmerSmall
        }

    /** How wide Glimmer is: the 85 % mode makes it narrower (height, content and text stay as they are). */
    val glimmerNarrow: Float get() = if (glimmerSmall) 0.85f else 1f

    /** True when Glimmer copies the iPhone's Dynamic Island. */
    val glimmerIsDynamicIsland: Boolean get() = glimmerMode == GlimmerMode.DynamicIsland

    /**
     * The settings Glimmer actually runs with: as chosen, or in the Dynamic Island mode the
     * iPhone's way (black, its proportions, tap opens and holding unfolds, none of Glimmer's
     * extras). The own choices stay saved and come back when switching back.
     */
    fun forGlimmer(): LauncherSettings = if (!glimmerIsDynamicIsland) this else copy(
        glimmerStyle = GlimmerStyle.Black,
        glimmerMusicStyle = GlimmerMusicStyle.Classic,
        glimmerIdlePill = true,
        glimmerGlow = false,
        glimmerOutline = GlimmerOutline.Off,
        glimmerWidth = 1f,
        glimmerMotion = GlimmerMotion.Normal,
        glimmerTapOpens = true,
        glimmerDoubleTap = GlimmerDoubleTap.Off,
        glimmerSwipeTracks = false,
        glimmerMessages = false,
        glimmerCharging = false,
        glimmerCodes = false,
        glimmerScreenshots = false,
        glimmerBreathe = false,
        glimmerAiFluid = false,
    )

    /** Every Glimmer setting as it comes (it stays switched on). */
    fun withGlimmerDefaults(): LauncherSettings {
        val d = LauncherSettings()
        return copy(
            glimmerMode = d.glimmerMode,
            glimmerStyle = d.glimmerStyle,
            glimmerIdlePill = d.glimmerIdlePill,
            glimmerMessages = d.glimmerMessages,
            glimmerTapOpens = d.glimmerTapOpens,
            glimmerAlerts = d.glimmerAlerts,
            glimmerGlow = d.glimmerGlow,
            glimmerMusicStyle = d.glimmerMusicStyle,
            glimmerUnlock = d.glimmerUnlock,
            glimmerAod = d.glimmerAod,
            glimmerGlowColor = d.glimmerGlowColor,
            glimmerOutline = d.glimmerOutline,
            glimmerOffsetX = d.glimmerOffsetX,
            glimmerOffsetY = d.glimmerOffsetY,
            glimmerWidth = d.glimmerWidth,
            glimmerMotion = d.glimmerMotion,
            glimmerAutoCollapse = d.glimmerAutoCollapse,
            glimmerDoubleTap = d.glimmerDoubleTap,
            glimmerSwipeTracks = d.glimmerSwipeTracks,
            glimmerCharging = d.glimmerCharging,
            glimmerHaptics = d.glimmerHaptics,
            glimmerHideFullscreen = d.glimmerHideFullscreen,
            glimmerCodes = d.glimmerCodes,
            glimmerScreenshots = d.glimmerScreenshots,
            glimmerBreathe = d.glimmerBreathe,
            glimmerSmall = d.glimmerSmall,
        )
    }

    fun withPreset(preset: DesignPreset): LauncherSettings = when (preset) {
        // One UI: squircle icons, its bold clock, blue, split panels, a plain slide between
        // pages, round toggles and wide sliders; the glass tinted cool; Claude in Galaxy AI's place.
        DesignPreset.GalaxyClaude -> copy(
            iconStyle = IconStyle.Original,
            iconShape = IconShape.Squircle,
            iconSize = 56,
            // ZENITH is part of Galaxy × Claude: Zenith green, sunlit glass, the sun's arc as the
            // clock, the OMEGA island as the dock.
            accent = AccentColor.Zenith,
            glassTint = GlassTint.Zenith,
            omegaColor = OmegaColor.Zenith,
            omegaGlass = true,
            glassLook = GlassLook.Zenith,
            glassTintStrength = 1f,
            glassRefraction = 1.2f,
            glassBlur = 1f,
            glassDispersion = 0.5f,
            glassSpecular = 0.95f,
            clockStyle = ClockStyle.Zenith,
            dockStyle = DockStyle.Zenith,
            showGreeting = true,
            showClaudeCard = false,
            galaxyClaude = true,
            // One UI has no search on the home screen: it sits on top of the app drawer.
            showSearchPill = false,
            // Hearth 13.2: up opens the drawer, down the finder.
            swipeDownAction = SwipeDownAction.Search,
            pageTransition = PageTransition.Flat,
        ).withCcStyle(CcStyle.OneUI)
        DesignPreset.ColorOSClaude -> copy(
            galaxyClaude = false,
            iconStyle = IconStyle.Original,
            iconShape = IconShape.Rounded,
            iconSize = 56,
            accent = AccentColor.Claude,
            glassTint = GlassTint.Claude,
            glassTintStrength = 0.9f,
            glassRefraction = 1.3f,
            glassBlur = 1f,
            glassDispersion = 0.6f,
            glassSpecular = 1f,
            clockStyle = ClockStyle.ColorOS,
            showGreeting = true,
            showClaudeCard = true,
            swipeDownAction = SwipeDownAction.Search,
        ).withCcStyle(CcStyle.ColorOS)
        DesignPreset.IOSGlass -> copy(
            galaxyClaude = false,
            iconStyle = IconStyle.Glass,
            iconShape = IconShape.Squircle,
            iconSize = 58,
            accent = AccentColor.White,
            glassTint = GlassTint.Clear,
            glassTintStrength = 1f,
            glassRefraction = 1.4f,
            glassBlur = 1f,
            glassDispersion = 0.8f,
            glassSpecular = 1.1f,
            clockStyle = ClockStyle.Glass,
            showClaudeCard = false,
            swipeDownAction = SwipeDownAction.ControlCenter,
        ).withCcStyle(CcStyle.IOS)
        DesignPreset.Hearth -> copy(
            galaxyClaude = false,
            iconStyle = IconStyle.Original,
            iconShape = IconShape.Squircle,
            iconSize = 58,
            accent = AccentColor.Ochre,
            glassTint = GlassTint.Warm,
            glassTintStrength = 1f,
            clockStyle = ClockStyle.Large,
            showClaudeCard = false,
        )
    }

    /** Switches the control center's look, with the colors and details that belong to it. */
    fun withCcStyle(style: CcStyle): LauncherSettings = when (style) {
        CcStyle.IOS -> copy(
            ccStyle = style,
            ccColors = CcColorMode.IOS,
            ccGlow = false,
            ccLabels = false,
            ccShowClock = false,
            ccShape = CcToggleShape.Circle,
            ccGlassTint = CcGlassTint.Clear,
            ccCorner = 30,
            ccToggleSize = 56,
        )
        CcStyle.ColorOS -> copy(
            ccStyle = style,
            ccColors = CcColorMode.Accent,
            ccGlow = true,
            ccLabels = true,
            ccShowClock = true,
            ccCorner = 26,
            ccToggleSize = 52,
        )
        // Samsung's Quick Panel: Wi-Fi and Bluetooth as big buttons, round switches with names,
        // wide sliders, Smart View and media output, all in glass.
        CcStyle.OneUI -> copy(
            ccStyle = style,
            ccColors = CcColorMode.Accent,
            ccGlow = false,
            ccLabels = true,
            ccShowClock = true,
            ccShape = CcToggleShape.Circle,
            ccSliders = CcSliderStyle.Wide,
            ccCorner = 28,
            ccToggleSize = 52,
        )
    }

    companion object {
        const val MAX_DOCK = 6

        /** Current default look; installs with a lower [designVersion] get it once. */
        const val DESIGN_VERSION = 12
    }
}

/** Keeps the launcher settings in SharedPreferences and exposes them as a flow. */
/**
 * [listen] false: a plain look at what is stored, with no hold on the preferences – for the ones
 * that only need the settings once (the Glimmer bridge, answering the launcher on every app that
 * opens and closes): every repository that listens stays registered until the next garbage
 * collection, and every write to the preferences makes each of them read everything again.
 */
class SettingsRepository(context: Context, listen: Boolean = true) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ZENITH Smart: "Be Smarter" is on from its first start (once – after that it is for the user to switch).
    init {
        if (dev.hearth.launcher.BuildConfig.SMART && !prefs.getBoolean("smartVariantApplied", false)) {
            prefs.edit()
                .putBoolean("beSmarter", true)
                // ZENITH Smart starts like a launcher, not with ZENITH's ceremonies (all of them switchable in the settings).
                .putBoolean("zenithBootIntro", false)
                .putBoolean("strikeOnUpdate", false)
                .putBoolean("smartVariantApplied", true)
                .apply()
        }
    }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    // OMEGA UI 17.5: what another screen of the app changes (its own activity – the update
    // screen with the Clawd Illumination, About phone) reaches this one at once.
    // OMEGA UI 18.3: one write changes many keys – read them back once, a moment later, not
    // once per key (that was dozens of full reads for every switch).
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var rereadPending = false
    private val reread = Runnable {
        rereadPending = false
        val now = read()
        if (now != _settings.value) _settings.value = now
        syncPalette(now)
    }
    private val onPrefsChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!rereadPending) {
            rereadPending = true
            mainHandler.postDelayed(reread, 60)
        }
    }

    init {
        if (listen) {
            prefs.registerOnSharedPreferenceChangeListener(onPrefsChanged)
            syncPalette(_settings.value)
        }
    }

    /** ZENITH 20.1: the color ZENITH is in follows the setting, in every screen. */
    private fun syncPalette(s: LauncherSettings) {
        if (ZenithPalette.tint != s.zenithTint) ZenithPalette.tint = s.zenithTint
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        _settings.update(transform)
        syncPalette(_settings.value)
        write(_settings.value)
        // ZENITH 20: every color of the aurora tried is remembered (all five is an egg).
        _settings.value.let { if (it.zenithAurora) JubileeTrail.sawAurora(appContext, it.auroraLook) }
    }

    /** All launcher settings as JSON, for "Einstellungen sichern" (the Claude key lives elsewhere). */
    fun exportJson(): String {
        val values = org.json.JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = org.json.JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Set<*> -> entry.put("t", "set").put("v", org.json.JSONArray(value.filterIsInstance<String>()))
                else -> return@forEach
            }
            values.put(key, entry)
        }
        return org.json.JSONObject().put("hearth", 1).put("settings", values).toString(2)
    }

    /** Brings settings back from [exportJson]; anything else is refused (false). */
    fun importJson(text: String): Boolean {
        val root = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return false
        if (root.optInt("hearth") != 1) return false
        val values = root.optJSONObject("settings") ?: return false
        val edit = prefs.edit()
        values.keys().forEach { key ->
            val entry = values.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "s" -> edit.putString(key, entry.optString("v"))
                "b" -> edit.putBoolean(key, entry.optBoolean("v"))
                "i" -> edit.putInt(key, entry.optInt("v"))
                "l" -> edit.putLong(key, entry.optLong("v"))
                "f" -> edit.putFloat(key, entry.optDouble("v").toFloat())
                "set" -> entry.optJSONArray("v")?.let { a -> edit.putStringSet(key, (0 until a.length()).map { a.optString(it) }.toSet()) }
            }
        }
        if (!edit.commit()) return false
        _settings.value = read()
        return true
    }

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private fun read(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            glassBlur = prefs.getFloat("glassBlur", d.glassBlur),
            glassRefraction = prefs.getFloat("glassRefraction", d.glassRefraction),
            glassDispersion = prefs.getFloat("glassDispersion", d.glassDispersion),
            glassSpecular = prefs.getFloat("glassSpecular", d.glassSpecular),
            glassTintStrength = prefs.getFloat("glassTintStrength", d.glassTintStrength),
            glassTint = enumOf("glassTint", d.glassTint),
            glassMotion = prefs.getBoolean("glassMotion", d.glassMotion),
            glassInteractive = prefs.getBoolean("glassInteractive", d.glassInteractive),
            glassQuality = enumOf("glassQuality", d.glassQuality),
            iconStyle = enumOf("iconStyle", d.iconStyle),
            iconShape = enumOf("iconShape", d.iconShape),
            iconSize = prefs.getInt("iconSize", d.iconSize),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            iconPack = prefs.getString("iconPack", null),
            adaptUnthemedIcons = prefs.getBoolean("adaptUnthemedIcons", d.adaptUnthemedIcons),
            accent = enumOf("accent", d.accent),
            columns = prefs.getInt("columns", d.columns),
            rows = prefs.getInt("rows", d.rows),
            clockStyle = enumOf("clockStyle", d.clockStyle),
            showGreeting = prefs.getBoolean("showGreeting", d.showGreeting),
            showBattery = prefs.getBoolean("showBattery", d.showBattery),
            dimWallpaper = prefs.getFloat("dimWallpaper", d.dimWallpaper),
            showSearchPill = prefs.getBoolean("showSearchPill", d.showSearchPill),
            swipeOpensSearch = prefs.getBoolean("swipeOpensSearch", d.swipeOpensSearch),
            swipeDownAction = enumOf("swipeDownAction13", d.swipeDownAction),
            showClaudeCard = prefs.getBoolean("showClaudeCard", d.showClaudeCard),
            galaxyClaude = prefs.getBoolean("galaxyClaude", d.galaxyClaude),
            drawerSort = enumOf("drawerSort", d.drawerSort),
            drawerSuggestions = prefs.getBoolean("drawerSuggestions", d.drawerSuggestions),
            dockStyle = enumOf("dockStyle", d.dockStyle),
            clawdSkin = enumOf("clawdSkin", d.clawdSkin),
            clawdHat = enumOf("clawdHat", d.clawdHat),
            clawdOutfit = enumOf("clawdOutfit", d.clawdOutfit),
            clawdCloth = enumOf("clawdCloth", d.clawdCloth),
            clawdOmega = prefs.getBoolean("clawdOmega", d.clawdOmega),
            omegaGlass = prefs.getBoolean("omegaGlass", d.omegaGlass),
            omegaColor = enumOf("omegaColor", d.omegaColor),
            zenithDaylight = prefs.getBoolean("zenithDaylight", d.zenithDaylight),
            zenithWallpaper = prefs.getBoolean("zenithWallpaper", d.zenithWallpaper),
            zenithAurora = prefs.getBoolean("zenithAurora", d.zenithAurora),
            auroraLook = enumOf("auroraLook", d.auroraLook),
            auroraStrength = prefs.getFloat("auroraStrength", d.auroraStrength),
            auroraSpeed = prefs.getFloat("auroraSpeed", d.auroraSpeed),
            zenithTint = enumOf("zenithTint", d.zenithTint),
            homeLightStrength = prefs.getFloat("homeLightStrength", d.homeLightStrength),
            homeLightColor = enumOf("homeLightColor", d.homeLightColor),
            homeLightBeam = prefs.getBoolean("homeLightBeam", d.homeLightBeam),
            homeLightGlint = prefs.getBoolean("homeLightGlint", d.homeLightGlint),
            homeLightFloor = prefs.getBoolean("homeLightFloor", d.homeLightFloor),
            beSmarter = prefs.getBoolean("beSmarter", d.beSmarter),
            smartGlass = enumOf("smartGlass", d.smartGlass),
            smartBlur = prefs.getFloat("smartBlur", d.smartBlur),
            smartRefraction = prefs.getFloat("smartRefraction", d.smartRefraction),
            smartDispersion = prefs.getFloat("smartDispersion", d.smartDispersion),
            smartDepth = prefs.getFloat("smartDepth", d.smartDepth),
            smartVibrancy = prefs.getFloat("smartVibrancy", d.smartVibrancy),
            smartShape = enumOf("smartShape", d.smartShape),
            smartClockSize = prefs.getFloat("smartClockSize", d.smartClockSize),
            smartClockWeather = prefs.getBoolean("smartClockWeather", d.smartClockWeather),
            smartClockAlarm = prefs.getBoolean("smartClockAlarm", d.smartClockAlarm),
            smartDrawerColumns = prefs.getInt("smartDrawerColumns", d.smartDrawerColumns),
            smartDrawerNames = prefs.getBoolean("smartDrawerNames", d.smartDrawerNames),
            smartClockLayout = enumOf("smartClockLayout", d.smartClockLayout),
            smartColor = enumOf("smartColor", d.smartColor),
            smartSpecular = prefs.getFloat("smartSpecular", d.smartSpecular),
            smartTint = prefs.getFloat("smartTint", d.smartTint),
            smartInstant = prefs.getBoolean("smartInstant", d.smartInstant),
            smartGlimmer = prefs.getBoolean("smartGlimmer", d.smartGlimmer),
            smartClockEvent = prefs.getBoolean("smartClockEvent", d.smartClockEvent),
            smartWidgetRadius = prefs.getInt("smartWidgetRadius", d.smartWidgetRadius),
            smartCategories = parseSmartCategories(prefs.getString("smartCategories", null)),
            smartTabs = parseSmartTabs(prefs.getString("smartTabs", null)),
            smartCategoryBar = prefs.getBoolean("smartCategoryBar", d.smartCategoryBar),
            smartCategoryTitles = prefs.getBoolean("smartCategoryTitles", d.smartCategoryTitles),
            smartLastCategory = prefs.getBoolean("smartLastCategory", d.smartLastCategory),
            smartPageEffect = enumOf("smartPageEffect", d.smartPageEffect),
            smartItemSpacing = prefs.getInt("smartItemSpacing", d.smartItemSpacing),
            smartIndicator = prefs.getBoolean("smartIndicator", d.smartIndicator),
            smartReadable = prefs.getBoolean("smartReadable", d.smartReadable),
            smartFahrenheit = prefs.getBoolean("smartFahrenheit", d.smartFahrenheit),
            smartHours = enumOf("smartHours", d.smartHours),
            smartHideAllDay = prefs.getBoolean("smartHideAllDay", d.smartHideAllDay),
            smartCustomColor = prefs.getInt("smartCustomColor", d.smartCustomColor),
            zenithTheme = enumOf("zenithTheme", d.zenithTheme),
            beComet = prefs.getBoolean("beComet", d.beComet),
            cometStyle = CometStyle(
                glass = enumOf("cometGlass", d.cometGlass),
                strength = prefs.getFloat("cometStrength", d.cometStrength),
                opacity = prefs.getFloat("cometOpacity", d.cometOpacity),
                shape = enumOf("cometShape", d.cometShape),
                icons = enumOf("cometIcons", d.cometIcons),
                labels = prefs.getBoolean("cometLabels", d.cometLabels),
                clock = enumOf("cometClock", d.cometClock),
                clockFont = enumOf("cometClockFont", d.cometClockFont),
                clockOpacity = prefs.getFloat("cometClockOpacity", d.cometClockOpacity),
                dim = prefs.getFloat("cometDim", d.cometDim),
                capsuleDock = prefs.getBoolean("cometCapsuleDock", d.cometCapsuleDock),
                library = prefs.getBoolean("cometLibrary", d.cometLibrary),
                accent = enumOf("cometAccent", d.cometAccent),
            ),
            superParams = SuperParams(
                scene = enumOf("superScene", d.superScene),
                tilt = prefs.getBoolean("superTilt", d.superTilt),
                zoomOnWake = prefs.getBoolean("superZoom", d.superZoom),
                speed = prefs.getFloat("superSpeed", d.superSpeed),
                stars = prefs.getBoolean("superStars", d.superStars),
            ),
            sloganEverywhere = prefs.getBoolean("sloganEverywhere", d.sloganEverywhere),
            zenithBootIntro = prefs.getBoolean("zenithBootIntro", d.zenithBootIntro),
            zenithStrike = prefs.getBoolean("zenithStrike", d.zenithStrike),
            zenithStrikeSystem = prefs.getBoolean("zenithStrikeSystem", d.zenithStrikeSystem),
            strikeColor = enumOf("strikeColor", d.strikeColor),
            strikeSpeed = enumOf("strikeSpeed", d.strikeSpeed),
            strikeOnUpdate = prefs.getBoolean("strikeOnUpdate", d.strikeOnUpdate),
            strikeOnVault = prefs.getBoolean("strikeOnVault", d.strikeOnVault),
            strikeOnDice = prefs.getBoolean("strikeOnDice", d.strikeOnDice),
            strikeOnCharge = prefs.getBoolean("strikeOnCharge", d.strikeOnCharge),
            strikeOnFull = prefs.getBoolean("strikeOnFull", d.strikeOnFull),
            chargeGuard = prefs.getBoolean("chargeGuard", d.chargeGuard),
            chargeLimit = prefs.getInt("chargeLimit", d.chargeLimit),
            newAppGuard = prefs.getBoolean("newAppGuard", d.newAppGuard),
            projectZenith = prefs.getBoolean("projectZenith", d.projectZenith),
            projectZenithGlass = prefs.getBoolean("projectZenithGlass", d.projectZenithGlass),
            projectColorOs = prefs.getBoolean("projectColorOs", d.projectColorOs),
            colorOsClock = enumOf("colorOsClock", d.colorOsClock),
            colorOsDock = enumOf("colorOsDock", d.colorOsDock),
            smoothMode = prefs.getBoolean("smoothMode", d.smoothMode),
            omegaLight = prefs.getBoolean("omegaLight", d.omegaLight),
            illumination = prefs.getBoolean("illumination", d.illumination),
            omegaOverdrive = prefs.getBoolean("omegaOverdrive", d.omegaOverdrive),
            hyperGlass = prefs.getBoolean("hyperGlass", d.hyperGlass),
            colorWorld = enumOf("colorWorld", d.colorWorld),
            omegaRain = prefs.getBoolean("omegaRain", d.omegaRain),
            weightless = prefs.getBoolean("weightless", d.weightless),
            clawdParade = prefs.getBoolean("clawdParade", d.clawdParade),
            omegaZeros = prefs.getBoolean("omegaZeros", d.omegaZeros),
            clawdHalo = prefs.getBoolean("clawdHalo", d.clawdHalo),
            giantClawd = prefs.getBoolean("giantClawd", d.giantClawd),
            allSeeingEye = prefs.getBoolean("allSeeingEye", d.allSeeingEye),
            perks = prefs.getStringSet("perks", null)?.toSet() ?: d.perks,
            mythos = prefs.getBoolean("mythos", d.mythos),
            fullAod = prefs.getBoolean("fullAod", d.fullAod),
            aodClawd = prefs.getBoolean("aodClawd", d.aodClawd),
            aodBattery = prefs.getBoolean("aodBattery", d.aodBattery),
            aodNotifications = prefs.getBoolean("aodNotifications", d.aodNotifications),
            aodEdge = prefs.getBoolean("aodEdge", d.aodEdge),
            aodDate = prefs.getBoolean("aodDate", d.aodDate),
            lockClawd = prefs.getBoolean("lockClawd", d.lockClawd),
            lockEdge = prefs.getBoolean("lockEdge", d.lockEdge),
            lockCharge = prefs.getBoolean("lockCharge", d.lockCharge),
            lockGreeting = prefs.getBoolean("lockGreeting", d.lockGreeting),
            lockAtmosphere = prefs.getBoolean("lockAtmosphere", d.lockAtmosphere),
            lockBattery = prefs.getBoolean("lockBattery", d.lockBattery),
            lockOmega = prefs.getBoolean("lockOmega", d.lockOmega),
            aodClock = enumOf("aodClock", d.aodClock),
            aodBrightness = enumOf("aodBrightness", d.aodBrightness),
            aodTint = enumOf("aodTint", d.aodTint),
            lockMessage = prefs.getString("lockMessage", null) ?: d.lockMessage,
            aodImage = prefs.getString("aodImage", null) ?: d.aodImage,
            glassLook = enumOf("glassLook", d.glassLook),
            glassClocks = prefs.getBoolean("glassClocks", d.glassClocks),
            glassStatusBar = prefs.getBoolean("glassStatusBar", d.glassStatusBar),
            clawdCompanion = prefs.getBoolean("clawdCompanion", d.clawdCompanion),
            // Older installs had only "double tap locks" on or off.
            doubleTapAction = enumOf(
                "doubleTapAction",
                if (prefs.getBoolean("doubleTapLock", true)) HomeGesture.Lock else HomeGesture.Off,
            ),
            clockFont = enumOf("clockFont", d.clockFont),
            labelSize = enumOf("labelSize", d.labelSize),
            showAppLibrary = prefs.getBoolean("showAppLibrary", d.showAppLibrary),
            showWidgetPage = prefs.getBoolean("showWidgetPage", d.showWidgetPage),
            triggerZone = enumOf("triggerZone", d.triggerZone),
            interceptSystemShade = prefs.getBoolean(KEY_INTERCEPT, d.interceptSystemShade),
            removedFromHome = prefs.getStringSet("removedFromHome", null)?.toSet() ?: d.removedFromHome,
            iconGloss = prefs.getBoolean("iconGloss", d.iconGloss),
            animations = prefs.getBoolean("animations", d.animations),
            fluidDesign = prefs.getBoolean("fluidDesign", d.fluidDesign),
            fluidRims = enumOf("fluidRims", FluidRims.Vivid),
            pageTransition = enumOf("pageTransition", d.pageTransition),
            ccEnabled = prefs.getBoolean(KEY_CC_ENABLED, d.ccEnabled),
            ccStyle = enumOf("ccStyle", d.ccStyle),
            ccBigTiles = prefs.getBoolean("ccBigTiles", d.ccBigTiles),
            ccSliders = enumOf("ccSliders", d.ccSliders),
            ccColors = enumOf("ccColors", d.ccColors),
            ccShape = enumOf("ccShape", d.ccShape),
            ccGlow = prefs.getBoolean("ccGlow", d.ccGlow),
            ccLabels = prefs.getBoolean("ccLabels", d.ccLabels),
            ccShowClock = prefs.getBoolean("ccShowClock", d.ccShowClock),
            ccShowMedia = prefs.getBoolean("ccShowMedia", d.ccShowMedia),
            ccShowShortcuts = prefs.getBoolean("ccShowShortcuts", d.ccShowShortcuts),
            ccDim = prefs.getFloat("ccDim", d.ccDim),
            ccNotifications = prefs.getBoolean("ccNotifications", d.ccNotifications),
            ccGlassTint = enumOf("ccGlassTint", d.ccGlassTint),
            ccGlassOpacity = prefs.getFloat("ccGlassOpacity", d.ccGlassOpacity),
            ccSpecular = prefs.getFloat("ccSpecular", d.ccSpecular),
            ccRefraction = prefs.getFloat("ccRefraction", d.ccRefraction),
            ccBlur = prefs.getFloat("ccBlur", d.ccBlur),
            ccCorner = prefs.getInt("ccCorner", d.ccCorner),
            ccToggleSize = prefs.getInt("ccToggleSize", d.ccToggleSize),
            glimmerEnabled = prefs.getBoolean(KEY_GLIMMER, d.glimmerEnabled),
            glimmerMode = enumOf("glimmerMode", d.glimmerMode),
            glimmerStyle = enumOf("glimmerStyle", d.glimmerStyle),
            glimmerIdlePill = prefs.getBoolean("glimmerIdlePill", d.glimmerIdlePill),
            glimmerClawd = prefs.getBoolean(KEY_CLAWD_GLIMMER, d.glimmerClawd),
            glimmerClawdSide = enumOf("glimmerClawdSide", d.glimmerClawdSide),
            glimmerClawdSpot = prefs.getFloat("glimmerClawdSpot", d.glimmerClawdSpot),
            glimmerClawdSize = prefs.getFloat("glimmerClawdSize", d.glimmerClawdSize),
            glimmerClawdGreeting = prefs.getBoolean("glimmerClawdGreeting", d.glimmerClawdGreeting),
            glimmerClawdGreetDaily = prefs.getBoolean("glimmerClawdGreetDaily", d.glimmerClawdGreetDaily),
            glimmerClawdIdle = prefs.getBoolean("glimmerClawdIdle", d.glimmerClawdIdle),
            glimmerMessages = prefs.getBoolean("glimmerMessages", d.glimmerMessages),
            glimmerTapOpens = prefs.getBoolean("glimmerTapOpens", d.glimmerTapOpens),
            glimmerAlerts = prefs.getBoolean("glimmerAlerts", d.glimmerAlerts),
            glimmerGlow = prefs.getBoolean("glimmerGlow", d.glimmerGlow),
            glimmerFlyIn = prefs.getBoolean("glimmerFlyIn", d.glimmerFlyIn),
            glimmerFlyInStyle = enumOf("glimmerFlyInStyle", d.glimmerFlyInStyle),
            glimmerFlyInEverywhere = prefs.getBoolean("glimmerFlyInEverywhere", d.glimmerFlyInEverywhere),
            glimmerFlyInCover = prefs.getBoolean("glimmerFlyInCover", d.glimmerFlyInCover),
            glimmerMusicStyle = enumOf("glimmerMusicStyle", d.glimmerMusicStyle),
            glimmerUnlock = enumOf("glimmerUnlock", d.glimmerUnlock),
            glimmerAod = prefs.getBoolean("glimmerAod", d.glimmerAod),
            glimmerGlowColor = enumOf("glimmerGlowColor", d.glimmerGlowColor),
            glimmerOutline = enumOf("glimmerOutline", d.glimmerOutline),
            glimmerOffsetX = prefs.getInt("glimmerOffsetX", d.glimmerOffsetX),
            glimmerOffsetY = prefs.getInt("glimmerOffsetY", d.glimmerOffsetY),
            glimmerWidth = prefs.getFloat("glimmerWidth", d.glimmerWidth),
            glimmerMotion = enumOf("glimmerMotion", d.glimmerMotion),
            glimmerSideways = enumOf("glimmerSideways", d.glimmerSideways),
            glimmerAutoCollapse = prefs.getInt("glimmerAutoCollapse", d.glimmerAutoCollapse),
            glimmerDoubleTap = enumOf("glimmerDoubleTap", d.glimmerDoubleTap),
            glimmerSwipeTracks = prefs.getBoolean("glimmerSwipeTracks", d.glimmerSwipeTracks),
            glimmerCharging = prefs.getBoolean("glimmerCharging", d.glimmerCharging),
            glimmerHaptics = prefs.getBoolean("glimmerHaptics", d.glimmerHaptics),
            glimmerHideFullscreen = prefs.getBoolean("glimmerHideFullscreen", d.glimmerHideFullscreen),
            glimmerCodes = prefs.getBoolean("glimmerCodes", d.glimmerCodes),
            glimmerScreenshots = prefs.getBoolean("glimmerScreenshots", d.glimmerScreenshots),
            glimmerBreathe = prefs.getBoolean("glimmerBreathe", d.glimmerBreathe),
            glimmerAiFluid = prefs.getBoolean("glimmerAiFluid", d.glimmerAiFluid),
            nightMode = enumOf("nightMode", d.nightMode),
            nightFrom = prefs.getInt("nightFrom", d.nightFrom),
            nightTo = prefs.getInt("nightTo", d.nightTo),
            nightSystem = prefs.getBoolean("nightSystem", d.nightSystem),
            glimmerPrivacy = prefs.getBoolean("glimmerPrivacy", d.glimmerPrivacy),
            glimmerSmall = prefs.getBoolean("glimmerSmall", d.glimmerSmall),
            badgeStyle = enumOf("badgeStyle", d.badgeStyle),
            doubleTapLock = prefs.getBoolean("doubleTapLock", d.doubleTapLock),
            dockSize = prefs.getInt("dockSize", d.dockSize),
            dockCustomized = prefs.getBoolean("dockCustomized", d.dockCustomized),
            dockApps = prefs.getString("dockApps", null)
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: d.dockApps,
            searchEngine = enumOf("searchEngine", d.searchEngine),
            theme = enumOf("theme", d.theme),
            haptics = prefs.getBoolean("haptics", d.haptics),
            hiddenApps = prefs.getStringSet("hiddenApps", null)?.toSet() ?: d.hiddenApps,
            // A plain string, so it can be handed to Glimmer, which guards the apps elsewhere too.
            lockedApps = prefs.getString(AppLock.KEY, "").orEmpty().split('|').filter { it.isNotBlank() }.toSet(),
            designVersion = prefs.getInt("designVersion", d.designVersion),
        )
    }

    private fun write(s: LauncherSettings) {
        prefs.edit()
            .putFloat("glassBlur", s.glassBlur)
            .putFloat("glassRefraction", s.glassRefraction)
            .putFloat("glassDispersion", s.glassDispersion)
            .putFloat("glassSpecular", s.glassSpecular)
            .putFloat("glassTintStrength", s.glassTintStrength)
            .putString("glassTint", s.glassTint.name)
            .putBoolean("glassMotion", s.glassMotion)
            .putBoolean("glassInteractive", s.glassInteractive)
            .putString("glassQuality", s.glassQuality.name)
            .putString("iconStyle", s.iconStyle.name)
            .putString("iconShape", s.iconShape.name)
            .putInt("iconSize", s.iconSize)
            .putBoolean("showLabels", s.showLabels)
            .putString("iconPack", s.iconPack)
            .putBoolean("adaptUnthemedIcons", s.adaptUnthemedIcons)
            .putString("accent", s.accent.name)
            .putInt("columns", s.columns)
            .putInt("rows", s.rows)
            .putString("clockStyle", s.clockStyle.name)
            .putBoolean("showGreeting", s.showGreeting)
            .putBoolean("showBattery", s.showBattery)
            .putFloat("dimWallpaper", s.dimWallpaper)
            .putBoolean("showSearchPill", s.showSearchPill)
            .putBoolean("swipeOpensSearch", s.swipeOpensSearch)
            .putString("swipeDownAction13", s.swipeDownAction.name)
            .putBoolean("showClaudeCard", s.showClaudeCard)
            .putBoolean("galaxyClaude", s.galaxyClaude)
            .putString("drawerSort", s.drawerSort.name)
            .putBoolean("drawerSuggestions", s.drawerSuggestions)
            .putString("dockStyle", s.dockStyle.name)
            .putString("clawdSkin", s.clawdSkin.name)
            .putString("clawdHat", s.clawdHat.name)
            .putString("clawdOutfit", s.clawdOutfit.name)
            .putString("clawdCloth", s.clawdCloth.name)
            .putBoolean("clawdOmega", s.clawdOmega)
            .putBoolean("omegaGlass", s.omegaGlass)
            .putString("omegaColor", s.omegaColor.name)
            .putBoolean("zenithDaylight", s.zenithDaylight)
            .putBoolean("zenithWallpaper", s.zenithWallpaper)
            .putBoolean("zenithAurora", s.zenithAurora)
            .putString("auroraLook", s.auroraLook.name)
            .putFloat("auroraStrength", s.auroraStrength)
            .putFloat("auroraSpeed", s.auroraSpeed)
            .putString("zenithTint", s.zenithTint.name)
            .putFloat("homeLightStrength", s.homeLightStrength)
            .putString("homeLightColor", s.homeLightColor.name)
            .putBoolean("homeLightBeam", s.homeLightBeam)
            .putBoolean("homeLightGlint", s.homeLightGlint)
            .putBoolean("homeLightFloor", s.homeLightFloor)
            .putBoolean("beSmarter", s.beSmarter)
            .putString("smartGlass", s.smartGlass.name)
            .putFloat("smartBlur", s.smartBlur)
            .putFloat("smartRefraction", s.smartRefraction)
            .putFloat("smartDispersion", s.smartDispersion)
            .putFloat("smartDepth", s.smartDepth)
            .putFloat("smartVibrancy", s.smartVibrancy)
            .putString("smartShape", s.smartShape.name)
            .putFloat("smartClockSize", s.smartClockSize)
            .putBoolean("smartClockWeather", s.smartClockWeather)
            .putBoolean("smartClockAlarm", s.smartClockAlarm)
            .putInt("smartDrawerColumns", s.smartDrawerColumns)
            .putBoolean("smartDrawerNames", s.smartDrawerNames)
            .putString("smartClockLayout", s.smartClockLayout.name)
            .putString("smartColor", s.smartColor.name)
            .putFloat("smartSpecular", s.smartSpecular)
            .putFloat("smartTint", s.smartTint)
            .putBoolean("smartInstant", s.smartInstant)
            .putBoolean("smartGlimmer", s.smartGlimmer)
            .putBoolean("smartClockEvent", s.smartClockEvent)
            .putInt("smartWidgetRadius", s.smartWidgetRadius)
            .putString("smartCategories", s.smartCategories.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putString("smartTabs", smartTabsText(s.smartTabs))
            .putBoolean("smartCategoryBar", s.smartCategoryBar)
            .putBoolean("smartCategoryTitles", s.smartCategoryTitles)
            .putBoolean("smartLastCategory", s.smartLastCategory)
            .putString("smartPageEffect", s.smartPageEffect.name)
            .putInt("smartItemSpacing", s.smartItemSpacing)
            .putBoolean("smartIndicator", s.smartIndicator)
            .putBoolean("smartReadable", s.smartReadable)
            .putBoolean("smartFahrenheit", s.smartFahrenheit)
            .putString("smartHours", s.smartHours.name)
            .putBoolean("smartHideAllDay", s.smartHideAllDay)
            .putInt("smartCustomColor", s.smartCustomColor)
            .putString("zenithTheme", s.zenithTheme.name)
            .putBoolean("beComet", s.beComet)
            .putString("cometGlass", s.cometGlass.name)
            .putFloat("cometStrength", s.cometStrength)
            .putFloat("cometOpacity", s.cometOpacity)
            .putString("cometShape", s.cometShape.name)
            .putString("cometIcons", s.cometIcons.name)
            .putBoolean("cometLabels", s.cometLabels)
            .putString("cometClock", s.cometClock.name)
            .putString("cometClockFont", s.cometClockFont.name)
            .putFloat("cometClockOpacity", s.cometClockOpacity)
            .putFloat("cometDim", s.cometDim)
            .putBoolean("cometCapsuleDock", s.cometCapsuleDock)
            .putBoolean("cometLibrary", s.cometLibrary)
            .putString("cometAccent", s.cometAccent.name)
            .putString("superScene", s.superScene.name)
            .putBoolean("superTilt", s.superTilt)
            .putBoolean("superZoom", s.superZoom)
            .putFloat("superSpeed", s.superSpeed)
            .putBoolean("superStars", s.superStars)
            .putBoolean("sloganEverywhere", s.sloganEverywhere)
            .putBoolean("zenithBootIntro", s.zenithBootIntro)
            .putBoolean("zenithStrike", s.zenithStrike)
            .putBoolean("zenithStrikeSystem", s.zenithStrikeSystem)
            .putString("strikeColor", s.strikeColor.name)
            .putString("strikeSpeed", s.strikeSpeed.name)
            .putBoolean("strikeOnUpdate", s.strikeOnUpdate)
            .putBoolean("strikeOnVault", s.strikeOnVault)
            .putBoolean("strikeOnDice", s.strikeOnDice)
            .putBoolean("strikeOnCharge", s.strikeOnCharge)
            .putBoolean("strikeOnFull", s.strikeOnFull)
            .putBoolean("chargeGuard", s.chargeGuard)
            .putInt("chargeLimit", s.chargeLimit)
            .putBoolean("newAppGuard", s.newAppGuard)
            .putBoolean("projectZenith", s.projectZenith)
            .putBoolean("projectZenithGlass", s.projectZenithGlass)
            .putBoolean("projectColorOs", s.projectColorOs)
            .putString("colorOsClock", s.colorOsClock.name)
            .putString("colorOsDock", s.colorOsDock.name)
            .putBoolean("smoothMode", s.smoothMode)
            .putBoolean("omegaLight", s.omegaLight)
            .putBoolean("illumination", s.illumination)
            .putBoolean("omegaOverdrive", s.omegaOverdrive)
            .putBoolean("hyperGlass", s.hyperGlass)
            .putString("colorWorld", s.colorWorld.name)
            .putBoolean("omegaRain", s.omegaRain)
            .putBoolean("weightless", s.weightless)
            .putBoolean("clawdParade", s.clawdParade)
            .putBoolean("omegaZeros", s.omegaZeros)
            .putBoolean("clawdHalo", s.clawdHalo)
            .putBoolean("giantClawd", s.giantClawd)
            .putBoolean("allSeeingEye", s.allSeeingEye)
            .putStringSet("perks", s.perks)
            .putBoolean("mythos", s.mythos)
            .putBoolean("fullAod", s.fullAod)
            .putBoolean("aodClawd", s.aodClawd)
            .putBoolean("aodBattery", s.aodBattery)
            .putBoolean("aodNotifications", s.aodNotifications)
            .putBoolean("aodEdge", s.aodEdge)
            .putBoolean("aodDate", s.aodDate)
            .putBoolean("lockClawd", s.lockClawd)
            .putBoolean("lockEdge", s.lockEdge)
            .putBoolean("lockCharge", s.lockCharge)
            .putBoolean("lockGreeting", s.lockGreeting)
            .putBoolean("lockAtmosphere", s.lockAtmosphere)
            .putBoolean("lockBattery", s.lockBattery)
            .putBoolean("lockOmega", s.lockOmega)
            .putString("aodClock", s.aodClock.name)
            .putString("aodBrightness", s.aodBrightness.name)
            .putString("aodTint", s.aodTint.name)
            .putString("lockMessage", s.lockMessage)
            .putString("aodImage", s.aodImage)
            .putString("glassLook", s.glassLook.name)
            .putBoolean("glassClocks", s.glassClocks)
            .putBoolean("glassStatusBar", s.glassStatusBar)
            .putBoolean("clawdCompanion", s.clawdCompanion)
            .putString("doubleTapAction", s.doubleTapAction.name)
            .putString("clockFont", s.clockFont.name)
            .putString("labelSize", s.labelSize.name)
            .putBoolean("showAppLibrary", s.showAppLibrary)
            .putBoolean("showWidgetPage", s.showWidgetPage)
            .putString("triggerZone", s.triggerZone.name)
            .putBoolean(KEY_INTERCEPT, s.interceptSystemShade)
            .putStringSet("removedFromHome", s.removedFromHome)
            .putBoolean("iconGloss", s.iconGloss)
            .putBoolean("animations", s.animations)
            .putBoolean("fluidDesign", s.fluidDesign)
            .putString("fluidRims", s.fluidRims.name)
            .putString("pageTransition", s.pageTransition.name)
            .putBoolean(KEY_CC_ENABLED, s.ccEnabled)
            .putString("ccStyle", s.ccStyle.name)
            .putBoolean("ccBigTiles", s.ccBigTiles)
            .putString("ccSliders", s.ccSliders.name)
            .putString("ccColors", s.ccColors.name)
            .putString("ccShape", s.ccShape.name)
            .putBoolean("ccGlow", s.ccGlow)
            .putBoolean("ccLabels", s.ccLabels)
            .putBoolean("ccShowClock", s.ccShowClock)
            .putBoolean("ccShowMedia", s.ccShowMedia)
            .putBoolean("ccShowShortcuts", s.ccShowShortcuts)
            .putFloat("ccDim", s.ccDim)
            .putBoolean("ccNotifications", s.ccNotifications)
            .putString("ccGlassTint", s.ccGlassTint.name)
            .putFloat("ccGlassOpacity", s.ccGlassOpacity)
            .putFloat("ccSpecular", s.ccSpecular)
            .putFloat("ccRefraction", s.ccRefraction)
            .putFloat("ccBlur", s.ccBlur)
            .putInt("ccCorner", s.ccCorner)
            .putInt("ccToggleSize", s.ccToggleSize)
            .putBoolean(KEY_GLIMMER, s.glimmerEnabled)
            .putString("glimmerMode", s.glimmerMode.name)
            .putString("glimmerStyle", s.glimmerStyle.name)
            .putBoolean("glimmerIdlePill", s.glimmerIdlePill)
            .putBoolean(KEY_CLAWD_GLIMMER, s.glimmerClawd)
            .putString("glimmerClawdSide", s.glimmerClawdSide.name)
            .putFloat("glimmerClawdSpot", s.glimmerClawdSpot)
            .putFloat("glimmerClawdSize", s.glimmerClawdSize)
            .putBoolean("glimmerClawdGreeting", s.glimmerClawdGreeting)
            .putBoolean("glimmerClawdGreetDaily", s.glimmerClawdGreetDaily)
            .putBoolean("glimmerClawdIdle", s.glimmerClawdIdle)
            .putBoolean("glimmerMessages", s.glimmerMessages)
            .putBoolean("glimmerTapOpens", s.glimmerTapOpens)
            .putBoolean("glimmerAlerts", s.glimmerAlerts)
            .putBoolean("glimmerGlow", s.glimmerGlow)
            .putBoolean("glimmerFlyIn", s.glimmerFlyIn)
            .putString("glimmerFlyInStyle", s.glimmerFlyInStyle.name)
            .putBoolean("glimmerFlyInEverywhere", s.glimmerFlyInEverywhere)
            .putBoolean("glimmerFlyInCover", s.glimmerFlyInCover)
            .putString("glimmerMusicStyle", s.glimmerMusicStyle.name)
            .putString("glimmerUnlock", s.glimmerUnlock.name)
            .putBoolean("glimmerAod", s.glimmerAod)
            .putString("glimmerGlowColor", s.glimmerGlowColor.name)
            .putString("glimmerOutline", s.glimmerOutline.name)
            .putInt("glimmerOffsetX", s.glimmerOffsetX)
            .putInt("glimmerOffsetY", s.glimmerOffsetY)
            .putFloat("glimmerWidth", s.glimmerWidth)
            .putString("glimmerMotion", s.glimmerMotion.name)
            .putString("glimmerSideways", s.glimmerSideways.name)
            .putInt("glimmerAutoCollapse", s.glimmerAutoCollapse)
            .putString("glimmerDoubleTap", s.glimmerDoubleTap.name)
            .putBoolean("glimmerSwipeTracks", s.glimmerSwipeTracks)
            .putBoolean("glimmerCharging", s.glimmerCharging)
            .putBoolean("glimmerHaptics", s.glimmerHaptics)
            .putBoolean("glimmerHideFullscreen", s.glimmerHideFullscreen)
            .putBoolean("glimmerCodes", s.glimmerCodes)
            .putBoolean("glimmerScreenshots", s.glimmerScreenshots)
            .putBoolean("glimmerBreathe", s.glimmerBreathe)
            .putBoolean("glimmerAiFluid", s.glimmerAiFluid)
            .putString("nightMode", s.nightMode.name)
            .putInt("nightFrom", s.nightFrom)
            .putInt("nightTo", s.nightTo)
            .putBoolean("nightSystem", s.nightSystem)
            .putBoolean("glimmerPrivacy", s.glimmerPrivacy)
            .putBoolean("glimmerSmall", s.glimmerSmall)
            .putString("badgeStyle", s.badgeStyle.name)
            .putBoolean("doubleTapLock", s.doubleTapLock)
            .putInt("dockSize", s.dockSize)
            .putBoolean("dockCustomized", s.dockCustomized)
            .putString("dockApps", s.dockApps.joinToString("\n"))
            .putString("searchEngine", s.searchEngine.name)
            .putString("theme", s.theme.name)
            .putBoolean("haptics", s.haptics)
            .putStringSet("hiddenApps", s.hiddenApps)
            .putString(AppLock.KEY, s.lockedApps.sorted().joinToString("|"))
            .putInt("designVersion", s.designVersion)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "hearth_settings"

        /** Settings Hearth hands to Glimmer: the control center's look and the accent color. */
        fun isShared(key: String) =
            (key.startsWith("cc") || key.startsWith("clawd") || key == "accent" || key == AppLock.KEY) && key != KEY_CLAWD_GLIMMER

        /**
         * Clawd in Glimmer is switched in either app: Hearth hands it over only when it's
         * switched there (see [writeRaw]), so it never undoes the switch in the Glimmer app.
         */
        const val KEY_CLAWD_GLIMMER = "clawdInGlimmer"

        /** Set in either app, handed over only when changed in Hearth (never undoing Glimmer's own choice). */
        val PUSHED_KEYS = setOf(KEY_CLAWD_GLIMMER, "glimmerFlyIn", "glimmerFlyInStyle")

        /** The shared settings as they are stored, for [writeRaw] in the other app. */
        fun readShared(context: Context): Bundle = Bundle().apply {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (!isShared(key)) return@forEach
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                }
            }
        }

        /** Stores settings handed over from the other app (only the shared ones). */
        fun writeRaw(context: Context, values: Bundle) {
            val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            for (key in values.keySet()) {
                if (!isShared(key) && key !in PUSHED_KEYS) continue
                @Suppress("DEPRECATION")
                when (val value = values.get(key)) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                }
            }
            edit.apply()
        }
        const val KEY_INTERCEPT = "interceptSystemShade"
        const val KEY_GLIMMER = "glimmerEnabled"
        val GLIMMER_KEYS = setOf(
            KEY_GLIMMER, "glimmerStyle", "glimmerIdlePill", "glimmerMessages",
            "glimmerTapOpens", "glimmerAlerts", "glimmerGlow", "glimmerMusicStyle",
            "glimmerUnlock", "glimmerAod", "glimmerMode",
        )

        /**
         * Glimmer settings that apply while it runs (no restart, so sliders move it live).
         * Only the switch itself and the look (black or glass, which changes the window) restart it.
         */
        val GLIMMER_LIVE_KEYS = setOf(
            "glimmerIdlePill", "clawdInGlimmer", "glimmerClawdSide", "glimmerClawdSpot", "glimmerClawdSize", "glimmerClawdGreeting", "glimmerClawdGreetDaily", "glimmerClawdIdle", "clawdSkin", "clawdHat", "clawdOutfit", "clawdCloth", "clawdOmega", "omegaGlass", "omegaColor", "zenithDaylight", "zenithStrikeSystem", "strikeColor", "strikeSpeed", "strikeOnCharge", "strikeOnFull", "chargeGuard", "chargeLimit", "newAppGuard", "projectZenith", "projectZenithGlass", "projectColorOs", "smoothMode", "clawdHalo", "omegaOverdrive", "hyperGlass", "fullAod", "aodClawd", "aodBattery", "aodNotifications", "aodEdge", "aodDate", "lockClawd", "lockEdge", "lockCharge", "lockGreeting", "lockAtmosphere", "lockBattery", "lockOmega", "aodClock", "aodBrightness", "aodTint", "lockMessage", "aodImage", "glassLook", "perks", "mythos", "glimmerMessages", "glimmerTapOpens", "glimmerAlerts", "glimmerGlow",
            "glimmerMusicStyle", "glimmerUnlock", "glimmerAod", "glimmerGlowColor", "glimmerOutline",
            "glimmerOffsetX", "glimmerOffsetY", "glimmerWidth", "glimmerMotion", "glimmerSideways", "glimmerAutoCollapse",
            "glimmerDoubleTap", "glimmerSwipeTracks", "glimmerCharging", "glimmerHaptics", "glimmerHideFullscreen", "glimmerCodes", "glimmerScreenshots", "glimmerBreathe", "glimmerAiFluid", "glimmerSmall", "glimmerPrivacy", "accent", "animations",
        )
        const val KEY_CC_ENABLED = "ccEnabled"
    }
}

/** Is it night for Hearth at [hour]? */
fun LauncherSettings.nightActive(hour: Int, systemDark: Boolean = false): Boolean = when (nightMode) {
    NightMode.Off -> false
    NightMode.On -> true
    NightMode.System -> systemDark
    NightMode.Auto -> if (nightFrom <= nightTo) hour in nightFrom until nightTo else hour >= nightFrom || hour < nightTo
}

/** The dark mode look: only the glass gets darker – dock, widgets, menus, search bar. */
fun LauncherSettings.forNight(): LauncherSettings = copy(darkGlass = true)

/**
 * OMEGA UI 18, Clawd Illuminati: the perks – small and big things to switch on all over the
 * system. Stored by name in [LauncherSettings.perks].
 */
enum class Perk(val title: String, val emoji: String, val text: String, val group: String) {
    Snow("Schneefall", "❄", "Leise Flocken fallen über den Startbildschirm.", "Atmosphäre"),
    Fireflies("Glühwürmchen", "✨", "Kleine Lichter schweben und glimmen hinter Apps und Widgets.", "Atmosphäre"),
    Bubbles("Seifenblasen", "○", "Schillernde Blasen steigen langsam auf.", "Atmosphäre"),
    Leaves("Herbstlaub", "🍂", "Blätter in Rot und Gold segeln herab.", "Atmosphäre"),
    Stars("Sternenhimmel", "⭐", "Funkelnde Sterne hinter allem.", "Atmosphäre"),
    Matrix("Matrix-Regen", "▦", "Grüne Zeichen rieseln wie im Film.", "Atmosphäre"),
    Rubies("Rubin-Regen", "♦", "Kleine Rubine glitzern im Fallen.", "Atmosphäre"),
    Comets("Sternschnuppen", "☄", "Ab und zu zieht eine Sternschnuppe über den Himmel.", "Atmosphäre"),
    Vignette("Vignette", "◐", "Dunkle, weiche Ecken wie im Kino.", "Licht"),
    Grain("Filmkorn", "▒", "Feines Korn über dem Startbildschirm, wie auf echtem Film.", "Licht"),
    EyeCare("Augenschutz", "🌙", "Warmes Licht im ganzen System – schont abends die Augen.", "Licht"),
    NightRest("Nachtruhe", "💤", "Zwischen 23 und 6 Uhr wird der Startbildschirm sanft dunkler.", "Licht"),
    Aurora("Aurora-Himmel", "🌌", "Nordlichter wehen oben über den Startbildschirm.", "Licht"),
    DayLight("Tageszeit-Licht", "🌅", "Die ZENITH-Beleuchtung folgt dem Tag: morgens Gold, mittags hell, abends Rot, nachts Blau.", "Licht"),
    BatteryAura("Akku-Aura", "🔋", "Unten leuchtet der Akku: grün voll, rot fast leer, beim Laden pulsierend.", "Licht"),
    OmegaMark("Z-Wasserzeichen", "Ƶ", "Ein großes, leises ZENITH-Z mitten im Hintergrund.", "Licht"),
    Parallax("Parallax", "◇", "Der Startbildschirm bewegt sich mit, wenn du das Handy neigst – echte Tiefe.", "Bewegung"),
    GiantClock("Riesen-Uhr", "🕰", "Die Uhr auf dem Startbildschirm ein gutes Stück größer.", "Bewegung"),
    Pulse("Icon-Puls", "♥", "Die Apps atmen sanft, jede in ihrem Takt.", "Bewegung"),
    Sparks("Funkenspur", "✦", "Wo dein Finger den Startbildschirm berührt, sprühen ZENITH-Funken.", "Bewegung"),
    Noir("Noir", "◑", "Das ganze System in Schwarz-Weiß.", "Farbe"),
    Sepia("Sepia", "📜", "Das ganze System in warmen, alten Farben.", "Farbe"),
    Vivid("Leuchtfarben", "🌈", "Alle Farben im ganzen System viel kräftiger.", "Farbe"),
    Swarm("Clawd-Schwarm", "🦀", "Drei weitere Clawds wandern über den Startbildschirm.", "Clawd"),
    Greeting("Clawd-Gruß", "👋", "Ab und zu schaut ein Clawd vom Rand herein und winkt.", "Clawd"),
}

/** Is this perk on? */
fun LauncherSettings.has(perk: Perk): Boolean = perk.name in perks

/** What Claude Mythos switches on (besides its own takeover). */
val MythosPerks: Set<String> = setOf(
    Perk.Stars, Perk.Fireflies, Perk.Aurora, Perk.Comets, Perk.Parallax, Perk.Sparks,
    Perk.Vignette, Perk.Swarm, Perk.Greeting, Perk.GiantClock,
).map { it.name }.toSet()

/**
 * OMEGA UI 18, Claude Mythos: while it is on, the Clawds take over – everything at its
 * strongest (OMEGA Glass and Fluid in Overdrive, Hyperglas, halos, the parade, the eye, the
 * best perks), on top of what was chosen. Switched off, everything is as it was.
 */
fun LauncherSettings.mythic(): LauncherSettings = if (!mythos) this else copy(
    omegaGlass = true,
    omegaLight = true,
    fluidDesign = true,
    animations = true,
    hyperGlass = true,
    clawdHalo = true,
    clawdParade = true,
    clawdCompanion = true,
    allSeeingEye = true,
    perks = perks + MythosPerks,
)
