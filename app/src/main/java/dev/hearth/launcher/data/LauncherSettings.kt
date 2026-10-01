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
        )
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
        )
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

    companion object {
        const val MAX_DOCK = 6

        /** Current default look; installs with a lower [designVersion] get it once. */
        const val DESIGN_VERSION = 2
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
    }
}
