package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.ZenithWeather
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.NightstandScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/** ZENITH 20: the Z-Nacht – the nightstand clock under the aurora; the screen stays on, dim. */
class NightstandActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        ZenithWeather.init(this)
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalGlassStyle provides GlassStyle.from(settings),
            ) {
                HearthTheme(dark = true) {
                    NightstandScreen(onClose = { finish() }, onBrightness = { on -> dim(on) })
                }
            }
        }
    }

    /** Dim: nearly dark, so it doesn't light the room; else the phone's own brightness. */
    private fun dim(on: Boolean) {
        val attributes = window.attributes
        attributes.screenBrightness = if (on) 0.03f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attributes
    }
}
