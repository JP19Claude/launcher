package dev.hearth.launcher.data

import android.content.Context
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
    Glass("Glas-Karte"),
    Large("Groß"),
    Hidden("Aus"),
}

/** Base color the glass is tinted with. */
enum class GlassTint(val label: String, val color: Color) {
    Claude("Claude", Color(0xFFD97757).copy(alpha = 0.14f)),
    Clear("Klar", Color.White.copy(alpha = 0.04f)),
    Light("Hell", Color.White.copy(alpha = 0.20f)),
    Dark("Dunkel", Color.Black.copy(alpha = 0.26f)),
    Warm("Warm", Color(0xFFC8952E).copy(alpha = 0.18f)),
    Blue("Blau", Color(0xFF6FA8FF).copy(alpha = 0.18f)),
}

/** Color for tinted icons and highlights. */
enum class AccentColor(val label: String, val color: Color) {
    Claude("Claude", Color(0xFFD97757)),
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

/** Look of Glimmer, the island around the front camera. */
enum class GlimmerStyle(val label: String) {
    Black("Schwarz wie die Kamera"),
    Glass("Liquid Glass"),
}

/** Overall look of control center and notifications. */
enum class CcStyle(val label: String) {
    IOS("iOS 27"),
    ColorOS("ColorOS 17"),
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
    ColorOSClaude("ColorOS × Claude"),
    IOSGlass("iOS Liquid Glass"),
    Hearth("Hearth Klassik"),
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
    val glassRefraction: Float = 1.3f,
    val glassDispersion: Float = 0.7f,
    val glassSpecular: Float = 1f,
    val glassTintStrength: Float = 1f,
    val glassTint: GlassTint = GlassTint.Clear,
    val glassMotion: Boolean = true,
    val glassInteractive: Boolean = true,
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
    val glimmerStyle: GlimmerStyle = GlimmerStyle.Black,
    val glimmerIdlePill: Boolean = true,
    val glimmerMessages: Boolean = true,
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
    /** Bumped when a new default look should be applied once to existing installs. */
    val designVersion: Int = 0,
) {
    fun withPreset(preset: DesignPreset): LauncherSettings = when (preset) {
        DesignPreset.ColorOSClaude -> copy(
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
    }

    companion object {
        const val MAX_DOCK = 6

        /** Current default look; installs with a lower [designVersion] get it once. */
        const val DESIGN_VERSION = 4
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
            showAppLibrary = prefs.getBoolean("showAppLibrary", d.showAppLibrary),
            showWidgetPage = prefs.getBoolean("showWidgetPage", d.showWidgetPage),
            triggerZone = enumOf("triggerZone", d.triggerZone),
            interceptSystemShade = prefs.getBoolean(KEY_INTERCEPT, d.interceptSystemShade),
            removedFromHome = prefs.getStringSet("removedFromHome", null)?.toSet() ?: d.removedFromHome,
            iconGloss = prefs.getBoolean("iconGloss", d.iconGloss),
            animations = prefs.getBoolean("animations", d.animations),
            pageTransition = enumOf("pageTransition", d.pageTransition),
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
            glimmerStyle = enumOf("glimmerStyle", d.glimmerStyle),
            glimmerIdlePill = prefs.getBoolean("glimmerIdlePill", d.glimmerIdlePill),
            glimmerMessages = prefs.getBoolean("glimmerMessages", d.glimmerMessages),
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
            .putBoolean("showAppLibrary", s.showAppLibrary)
            .putBoolean("showWidgetPage", s.showWidgetPage)
            .putString("triggerZone", s.triggerZone.name)
            .putBoolean(KEY_INTERCEPT, s.interceptSystemShade)
            .putStringSet("removedFromHome", s.removedFromHome)
            .putBoolean("iconGloss", s.iconGloss)
            .putBoolean("animations", s.animations)
            .putString("pageTransition", s.pageTransition.name)
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
            .putString("glimmerStyle", s.glimmerStyle.name)
            .putBoolean("glimmerIdlePill", s.glimmerIdlePill)
            .putBoolean("glimmerMessages", s.glimmerMessages)
            .putString("badgeStyle", s.badgeStyle.name)
            .putBoolean("doubleTapLock", s.doubleTapLock)
            .putInt("dockSize", s.dockSize)
            .putBoolean("dockCustomized", s.dockCustomized)
            .putString("dockApps", s.dockApps.joinToString("\n"))
            .putString("searchEngine", s.searchEngine.name)
            .putString("theme", s.theme.name)
            .putBoolean("haptics", s.haptics)
            .putStringSet("hiddenApps", s.hiddenApps)
            .putInt("designVersion", s.designVersion)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "hearth_settings"
        const val KEY_INTERCEPT = "interceptSystemShade"
        const val KEY_GLIMMER = "glimmerEnabled"
        val GLIMMER_KEYS = setOf(KEY_GLIMMER, "glimmerStyle", "glimmerIdlePill", "glimmerMessages")
    }
}
