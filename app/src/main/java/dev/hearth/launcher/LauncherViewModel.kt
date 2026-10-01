package dev.hearth.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.AppRepository
import dev.hearth.launcher.data.WallpaperBackdrop
import dev.hearth.launcher.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(application)
    private val wallpaper = WallpaperRepository(application)

    val apps: StateFlow<List<AppInfo>> = repo.apps

    /** Phone, messages, browser, camera — filled up with other apps if a default is missing. */
    val dock: StateFlow<List<AppInfo>> = repo.apps
        .map { all -> pickDock(all) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wallpaper copy for the glass surfaces, or null if it can't be read. */
    val backdrop: StateFlow<WallpaperBackdrop?> = wallpaper.backdrop

    /** True while the launcher lacks permission to read the wallpaper. */
    val needsWallpaperAccess: StateFlow<Boolean> = wallpaper.needsAccess

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the home button is pressed while the launcher is already open. */
    val homeEvents: SharedFlow<Unit> = _homeEvents.asSharedFlow()

    private fun pickDock(all: List<AppInfo>): List<AppInfo> {
        if (all.isEmpty()) return emptyList()
        val preferred = repo.defaultDockPackages()
            .mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
            .distinctBy { it.key }
        val filler = all.filterNot { app -> preferred.any { it.key == app.key } }
        return (preferred + filler).take(DOCK_SIZE)
    }

    fun onHomePressed() {
        _homeEvents.tryEmit(Unit)
    }

    /** Called on resume: picks up a permission granted in the system settings. */
    fun onResume() {
        if (needsWallpaperAccess.value) wallpaper.refresh()
    }

    fun refreshWallpaper() = wallpaper.refresh()

    fun launch(app: AppInfo) = repo.launch(app)
    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)
    fun uninstall(app: AppInfo) = repo.uninstall(app)
    fun webSearch(query: String) = repo.webSearch(query)

    override fun onCleared() {
        repo.close()
        wallpaper.close()
    }

    companion object {
        const val DOCK_SIZE = 4
    }
}
