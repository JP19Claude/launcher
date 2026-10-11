package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.ZenithWeather
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.ZentraleScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/** ZENITH 20: the Zentrale – the day at a glance: time, brief, weather, the phone and the shortcuts. */
class ZentraleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        ZenithWeather.init(this)
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalGlassStyle provides GlassStyle.from(settings),
            ) {
                HearthTheme(dark = true) {
                    ZentraleScreen(onClose = { finish() }, onUpdate = { change -> repo.update(change) })
                }
            }
        }
    }
}
