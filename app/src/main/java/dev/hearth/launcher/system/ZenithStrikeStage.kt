package dev.hearth.launcher.system

import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hearth.launcher.ui.ZenithStrikeScreen

/**
 * ZENITH 19, the Z-Angriff everywhere: a window of its own over every app, for a moment – the
 * giant Z drawn in green beams across the screen and bursting, like Zygarde's Core Enforcer.
 * It never takes a touch, and it's gone when the burst is.
 */
class ZenithStrikeStage(private val service: GlimmerService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun play() {
        if (view != null) return
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setContent { ZenithStrikeScreen(onDone = { view?.post { remove() } }) }
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
            title = "ZENITH Z-Angriff"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        if (runCatching { windowManager.addView(compose, p) }.isSuccess) view = compose
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
    }
}
