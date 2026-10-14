package dev.hearth.launcher.system

import android.app.WallpaperManager
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.wantsLockStage
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.LockStageContent
import dev.hearth.launcher.ui.theme.HearthTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OMEGA UI 18.1 – Glimmer's stage: one full-screen window that lies over the lock screen and
 * the Always On Display (and only there). It draws the full AOD – the wallpaper dimmed behind
 * the clock, like a Galaxy S24 Ultra – and what was chosen for the lock screen (Clawd, the
 * OMEGA rim, a message, the charging light …). It never takes a touch: everything below it
 * stays usable.
 */
class LockStage(private val service: GlimmerService, private val onShown: () -> Unit) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var scope: CoroutineScope? = null
    private val wallpaper = MutableStateFlow<ImageBitmap?>(null)

    fun start(settings: StateFlow<LauncherSettings>, locked: StateFlow<Boolean>, dozing: StateFlow<Boolean>) {
        if (scope != null) return
        val s = MainScope()
        scope = s
        s.launch {
            combine(settings, locked, dozing) { st, l, d -> st.wantsLockStage() && (l || d) }
                .distinctUntilChanged()
                .collect { show -> if (show) add(settings, locked, dozing) else remove() }
        }
        // The full AOD shows the chosen picture, or else the wallpaper: read (smaller) when it is
        // switched on or another picture is chosen.
        s.launch {
            settings.map { it.fullAod to it.aodImage }.distinctUntilChanged().collect { (on, image) ->
                if (on) wallpaper.value = withContext(Dispatchers.IO) { readPicture(image) ?: readWallpaper() }
            }
        }
    }

    fun stop() {
        remove()
        scope?.cancel()
        scope = null
    }

    private fun readPicture(name: String): ImageBitmap? = if (name.isBlank()) null else runCatching {
        android.graphics.BitmapFactory.decodeFile(java.io.File(service.filesDir, name).path)?.asImageBitmap()
    }.getOrNull()

    private fun readWallpaper(): ImageBitmap? = runCatching {
        val drawable = WallpaperManager.getInstance(service).drawable ?: return@runCatching null
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = (720f / w).coerceAtMost(1f)
        drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
    }.getOrNull()

    private fun add(settings: StateFlow<LauncherSettings>, locked: StateFlow<Boolean>, dozing: StateFlow<Boolean>) {
        if (view != null) return
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setContent {
                val s by settings.collectAsStateWithLifecycle()
                val isLocked by locked.collectAsStateWithLifecycle()
                val isDozing by dozing.collectAsStateWithLifecycle()
                val picture by wallpaper.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalSettings provides s,
                    LocalGlassStyle provides GlassStyle.from(s),
                ) {
                    HearthTheme(dark = true) {
                        LockStageContent(s, isLocked, isDozing, picture)
                    }
                }
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "ZENITH Lock Stage"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        if (runCatching { windowManager.addView(compose, p) }.isSuccess) {
            view = compose
            // The island stays on top of the stage.
            onShown()
        }
    }

    private fun remove() {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
    }
}
