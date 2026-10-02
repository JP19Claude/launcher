package dev.hearth.launcher.data

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
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
}

enum class ClockStyle(val label: String) {
    ColorOS("ColorOS"),
    /** Samsung's home clock: big, bold, tight digits over the date. */
    OneUI("One UI"),
    Glass("Glas-Karte"),
    Large("Groß"),
    Hidden("Aus"),
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
    Split("Links Mitteilungen, rechts Kontrollzentrum"),
    ControlCenter("Kontrollzentrum"),
    Notifications("Mitteilungen"),
    Search("Suche"),
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
}

/** How a closing app flies into Glimmer. */
enum class FlyInStyle(val label: String) {
    HarmonyOS("HarmonyOS"),
    HyperOS("HyperOS"),
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
    GalaxyClaude("Galaxy × Claude"),
    ColorOSClaude("ColorOS × Claude"),
    IOSGlass("iOS Liquid Glass"),
    Hearth("Hearth Klassik"),
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
    val swipeDownAction: SwipeDownAction = SwipeDownAction.Split,
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
            accent = AccentColor.Galaxy,
            glassTint = GlassTint.Galaxy,
            glassTintStrength = 1f,
            glassRefraction = 1.2f,
            glassBlur = 1f,
            glassDispersion = 0.5f,
            glassSpecular = 0.95f,
            clockStyle = ClockStyle.OneUI,
            showGreeting = true,
            showClaudeCard = false,
            galaxyClaude = true,
            // One UI has no search on the home screen: it sits on top of the app drawer.
            showSearchPill = false,
            swipeDownAction = SwipeDownAction.Split,
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
            swipeDownAction = SwipeDownAction.Split,
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
        const val DESIGN_VERSION = 7
    }
}

/** Keeps the launcher settings in SharedPreferences and exposes them as a flow. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        _settings.update(transform)
        write(_settings.value)
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
            swipeDownAction = enumOf("swipeDownAction", d.swipeDownAction),
            showClaudeCard = prefs.getBoolean("showClaudeCard", d.showClaudeCard),
            galaxyClaude = prefs.getBoolean("galaxyClaude", d.galaxyClaude),
            drawerSort = enumOf("drawerSort", d.drawerSort),
            drawerSuggestions = prefs.getBoolean("drawerSuggestions", d.drawerSuggestions),
            dockStyle = enumOf("dockStyle", d.dockStyle),
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
            glimmerMessages = prefs.getBoolean("glimmerMessages", d.glimmerMessages),
            glimmerTapOpens = prefs.getBoolean("glimmerTapOpens", d.glimmerTapOpens),
            glimmerAlerts = prefs.getBoolean("glimmerAlerts", d.glimmerAlerts),
            glimmerGlow = prefs.getBoolean("glimmerGlow", d.glimmerGlow),
            glimmerFlyIn = prefs.getBoolean("glimmerFlyIn", d.glimmerFlyIn),
            glimmerFlyInStyle = enumOf("glimmerFlyInStyle", d.glimmerFlyInStyle),
            glimmerMusicStyle = enumOf("glimmerMusicStyle", d.glimmerMusicStyle),
            glimmerUnlock = enumOf("glimmerUnlock", d.glimmerUnlock),
            glimmerAod = prefs.getBoolean("glimmerAod", d.glimmerAod),
            glimmerGlowColor = enumOf("glimmerGlowColor", d.glimmerGlowColor),
            glimmerOutline = enumOf("glimmerOutline", d.glimmerOutline),
            glimmerOffsetX = prefs.getInt("glimmerOffsetX", d.glimmerOffsetX),
            glimmerOffsetY = prefs.getInt("glimmerOffsetY", d.glimmerOffsetY),
            glimmerWidth = prefs.getFloat("glimmerWidth", d.glimmerWidth),
            glimmerMotion = enumOf("glimmerMotion", d.glimmerMotion),
            glimmerAutoCollapse = prefs.getInt("glimmerAutoCollapse", d.glimmerAutoCollapse),
            glimmerDoubleTap = enumOf("glimmerDoubleTap", d.glimmerDoubleTap),
            glimmerSwipeTracks = prefs.getBoolean("glimmerSwipeTracks", d.glimmerSwipeTracks),
            glimmerCharging = prefs.getBoolean("glimmerCharging", d.glimmerCharging),
            glimmerHaptics = prefs.getBoolean("glimmerHaptics", d.glimmerHaptics),
            glimmerHideFullscreen = prefs.getBoolean("glimmerHideFullscreen", d.glimmerHideFullscreen),
            glimmerCodes = prefs.getBoolean("glimmerCodes", d.glimmerCodes),
            glimmerScreenshots = prefs.getBoolean("glimmerScreenshots", d.glimmerScreenshots),
            glimmerBreathe = prefs.getBoolean("glimmerBreathe", d.glimmerBreathe),
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
            .putString("swipeDownAction", s.swipeDownAction.name)
            .putBoolean("showClaudeCard", s.showClaudeCard)
            .putBoolean("galaxyClaude", s.galaxyClaude)
            .putString("drawerSort", s.drawerSort.name)
            .putBoolean("drawerSuggestions", s.drawerSuggestions)
            .putString("dockStyle", s.dockStyle.name)
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
            .putBoolean("glimmerMessages", s.glimmerMessages)
            .putBoolean("glimmerTapOpens", s.glimmerTapOpens)
            .putBoolean("glimmerAlerts", s.glimmerAlerts)
            .putBoolean("glimmerGlow", s.glimmerGlow)
            .putBoolean("glimmerFlyIn", s.glimmerFlyIn)
            .putString("glimmerFlyInStyle", s.glimmerFlyInStyle.name)
            .putString("glimmerMusicStyle", s.glimmerMusicStyle.name)
            .putString("glimmerUnlock", s.glimmerUnlock.name)
            .putBoolean("glimmerAod", s.glimmerAod)
            .putString("glimmerGlowColor", s.glimmerGlowColor.name)
            .putString("glimmerOutline", s.glimmerOutline.name)
            .putInt("glimmerOffsetX", s.glimmerOffsetX)
            .putInt("glimmerOffsetY", s.glimmerOffsetY)
            .putFloat("glimmerWidth", s.glimmerWidth)
            .putString("glimmerMotion", s.glimmerMotion.name)
            .putInt("glimmerAutoCollapse", s.glimmerAutoCollapse)
            .putString("glimmerDoubleTap", s.glimmerDoubleTap.name)
            .putBoolean("glimmerSwipeTracks", s.glimmerSwipeTracks)
            .putBoolean("glimmerCharging", s.glimmerCharging)
            .putBoolean("glimmerHaptics", s.glimmerHaptics)
            .putBoolean("glimmerHideFullscreen", s.glimmerHideFullscreen)
            .putBoolean("glimmerCodes", s.glimmerCodes)
            .putBoolean("glimmerScreenshots", s.glimmerScreenshots)
            .putBoolean("glimmerBreathe", s.glimmerBreathe)
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
        fun isShared(key: String) = key.startsWith("cc") || key == "accent" || key == AppLock.KEY

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
                if (!isShared(key)) continue
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
            "glimmerIdlePill", "glimmerMessages", "glimmerTapOpens", "glimmerAlerts", "glimmerGlow",
            "glimmerMusicStyle", "glimmerUnlock", "glimmerAod", "glimmerGlowColor", "glimmerOutline",
            "glimmerOffsetX", "glimmerOffsetY", "glimmerWidth", "glimmerMotion", "glimmerAutoCollapse",
            "glimmerDoubleTap", "glimmerSwipeTracks", "glimmerCharging", "glimmerHaptics", "glimmerHideFullscreen", "glimmerCodes", "glimmerScreenshots", "glimmerBreathe", "glimmerSmall", "glimmerPrivacy", "accent", "animations",
        )
        const val KEY_CC_ENABLED = "ccEnabled"
    }
}
