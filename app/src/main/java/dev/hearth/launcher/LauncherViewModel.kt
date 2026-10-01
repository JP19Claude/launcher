package dev.hearth.launcher

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.AppRepository
import dev.hearth.launcher.data.AppUsage
import dev.hearth.launcher.data.DesignPreset
import dev.hearth.launcher.data.IconConfig
import dev.hearth.launcher.data.IconPackInfo
import dev.hearth.launcher.data.IconPackRepository
import dev.hearth.launcher.data.IconStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LibraryCategory
import dev.hearth.launcher.data.UsageRepository
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.SystemControls
import dev.hearth.launcher.data.WallpaperBackdrop
import dev.hearth.launcher.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Content of the App Library: suggestions, recently added, and one folder per category. */
@Immutable
data class AppLibraryData(
    val suggestions: List<AppInfo> = emptyList(),
    val recent: List<AppInfo> = emptyList(),
    val folders: List<Pair<LibraryCategory, List<AppInfo>>> = emptyList(),
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val iconPackRepo = IconPackRepository(application)
    private val repo = AppRepository(application, iconPackRepo)
    private val wallpaper = WallpaperRepository(application)
    private val usage = UsageRepository(application)

    /** Brightness, volume, flashlight & co. for the launcher's control center. */
    val controls = SystemControls(application)

    val settings: StateFlow<LauncherSettings> = settingsRepo.settings

    /** Every launchable app, hidden ones included (for the settings). */
    val allApps: StateFlow<List<AppInfo>> = repo.apps

    /** Apps shown on the home screen and in search. */
    val apps: StateFlow<List<AppInfo>> = combine(repo.apps, settings) { all, s ->
        if (s.hiddenApps.isEmpty()) all else all.filterNot { it.key in s.hiddenApps }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Own dock order if the user changed it, otherwise phone, messages, browser, camera. */
    val dock: StateFlow<List<AppInfo>> = combine(apps, settings) { all, s -> pickDock(all, s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps sorted into folders, like the App Library on iOS. */
    val library: StateFlow<AppLibraryData> = combine(apps, usage.usage) { all, used -> buildLibrary(all, used) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLibraryData())

    /** Wallpaper copy for the glass surfaces, or null if it can't be read. */
    val backdrop: StateFlow<WallpaperBackdrop?> = wallpaper.backdrop

    /** True while the launcher lacks permission to read the wallpaper. */
    val needsWallpaperAccess: StateFlow<Boolean> = wallpaper.needsAccess

    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val iconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    private val _homeEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /**
     * Emits on the home button. True: pressed while the launcher was already in front
     * (jump back to the first page). False: coming back from an app (only close overlays,
     * instantly, so the next tap reaches an icon right away).
     */
    val homeEvents: SharedFlow<Boolean> = _homeEvents.asSharedFlow()

    init {
        // New default look (ColorOS × Claude), applied once to existing installs.
        if (settingsRepo.settings.value.designVersion < LauncherSettings.DESIGN_VERSION) {
            settingsRepo.update {
                it.withPreset(DesignPreset.ColorOSClaude).copy(designVersion = LauncherSettings.DESIGN_VERSION)
            }
        }

        // Re-render icons whenever the icon pack or icon style changes.
        viewModelScope.launch {
            settings
                .map { s ->
                    IconConfig(
                        iconPack = s.iconPack,
                        glyphs = s.iconStyle != IconStyle.Original,
                        preferMonochrome = s.iconStyle == IconStyle.Clear || s.iconStyle == IconStyle.Tinted,
                        adaptUnthemed = s.adaptUnthemedIcons,
                    )
                }
                .distinctUntilChanged()
                .collect { repo.setIconConfig(it) }
        }
    }

    private fun pickDock(all: List<AppInfo>, s: LauncherSettings): List<AppInfo> {
        if (all.isEmpty()) return emptyList()
        if (s.dockCustomized) {
            return s.dockApps.mapNotNull { key -> all.firstOrNull { it.key == key } }
        }
        val preferred = repo.defaultDockPackages()
            .mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
            .distinctBy { it.key }
        val filler = all.filterNot { app -> preferred.any { it.key == app.key } }
        return (preferred + filler).take(s.dockSize.coerceIn(1, LauncherSettings.MAX_DOCK))
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = settingsRepo.update(transform)

    fun isInDock(app: AppInfo): Boolean = dock.value.any { it.key == app.key }

    fun canAddToDock(): Boolean = dock.value.size < LauncherSettings.MAX_DOCK

    fun addToDock(app: AppInfo) {
        val current = dock.value.map { it.key }
        if (app.key in current || current.size >= LauncherSettings.MAX_DOCK) return
        val keys = current + app.key
        settingsRepo.update { it.copy(dockCustomized = true, dockApps = keys, dockSize = keys.size) }
    }

    fun removeFromDock(app: AppInfo) {
        val keys = dock.value.map { it.key } - app.key
        settingsRepo.update { it.copy(dockCustomized = true, dockApps = keys, dockSize = keys.size.coerceAtLeast(1)) }
    }

    /** Moves an app one place left (-1) or right (+1) in the dock. */
    fun moveInDock(app: AppInfo, direction: Int) {
        val keys = dock.value.map { it.key }.toMutableList()
        val from = keys.indexOf(app.key)
        val to = from + direction
        if (from < 0 || to !in keys.indices) return
        keys.add(to, keys.removeAt(from))
        settingsRepo.update { it.copy(dockCustomized = true, dockApps = keys) }
    }

    fun resetDock() {
        settingsRepo.update { it.copy(dockCustomized = false, dockApps = emptyList(), dockSize = 4) }
    }

    /** Changing the dock size switches back to the automatic dock with that many apps. */
    fun setDockSize(size: Int) {
        settingsRepo.update { s ->
            if (s.dockCustomized) {
                val keys = dock.value.map { it.key }
                val filled = if (keys.size >= size) keys.take(size) else {
                    keys + apps.value.map { it.key }.filterNot { it in keys }.take(size - keys.size)
                }
                s.copy(dockSize = size, dockApps = filled)
            } else {
                s.copy(dockSize = size)
            }
        }
    }

    fun hide(app: AppInfo) {
        settingsRepo.update { s ->
            s.copy(
                hiddenApps = s.hiddenApps + app.key,
                dockApps = s.dockApps - app.key,
            )
        }
    }

    fun unhide(key: String) {
        settingsRepo.update { it.copy(hiddenApps = it.hiddenApps - key) }
    }

    fun refreshIconPacks() {
        viewModelScope.launch(Dispatchers.Default) {
            _iconPacks.value = iconPackRepo.installedPacks()
        }
    }

    private val _settingsRequests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Someone outside the home screen (the control center overlay) wants the settings opened. */
    val settingsRequests: SharedFlow<Unit> = _settingsRequests.asSharedFlow()

    fun requestSettings() {
        _settingsRequests.tryEmit(Unit)
    }

    fun consumeSettingsRequest() {
        _settingsRequests.resetReplayCache()
    }

    fun onHomePressed(alreadyInFront: Boolean) {
        _homeEvents.tryEmit(alreadyInFront)
    }

    private fun buildLibrary(all: List<AppInfo>, used: Map<String, AppUsage>): AppLibraryData {
        val now = System.currentTimeMillis()
        val suggestions = all
            .filter { it.key in used }
            .sortedByDescending { UsageRepository.score(used.getValue(it.key), now) }
            .take(4)
        val recent = all
            .filter { it.installTime > 0 }
            .sortedByDescending { it.installTime }
            .take(4)
        val folders = all
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
            .map { (category, list) -> category to list }
        return AppLibraryData(suggestions, recent, folders)
    }

    /** Called on resume: picks up a permission granted in the system settings. */
    fun onResume() {
        if (needsWallpaperAccess.value) wallpaper.refresh()
    }

    fun refreshWallpaper() = wallpaper.refresh()

    fun launch(app: AppInfo, sourceBounds: Rect? = null, options: Bundle? = null) {
        usage.recordLaunch(app.key)
        repo.launch(app, sourceBounds, options)
    }
    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)
    fun uninstall(app: AppInfo) = repo.uninstall(app)
    fun webSearch(query: String) = repo.webSearch(query, settings.value.searchEngine)
    fun askClaude(question: String? = null) = controls.openClaude(question)

    fun applyPreset(preset: DesignPreset) = settingsRepo.update { it.withPreset(preset) }

    fun openWallpaperPicker() {
        val pick = Intent(Intent.ACTION_SET_WALLPAPER)
        val chooser = Intent.createChooser(pick, "Hintergrundbild wählen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(chooser) }
    }

    fun openDefaultLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    override fun onCleared() {
        repo.close()
        wallpaper.close()
        controls.close()
    }
}
