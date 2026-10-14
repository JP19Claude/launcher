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
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.PerfumeScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/** ZENITH 19.7: Clawd's perfume shelf – your perfumes, and which one to wear today. */
class PerfumeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalGlassStyle provides GlassStyle.from(settings),
            ) {
                HearthTheme(dark = true) {
                    PerfumeScreen(onClose = { finish() })
                }
            }
        }
    }
}
