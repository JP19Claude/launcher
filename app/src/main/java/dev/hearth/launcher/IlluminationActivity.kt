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
import dev.hearth.launcher.ui.ClawdIlluminationScreen
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * OMEGA UI 17.5: the Clawd Illumination – the hidden menu behind the Ω crystal. Its own screen,
 * so it opens from the update screen, About phone and the settings alike; what it changes
 * reaches the home screen at once.
 */
class IlluminationActivity : ComponentActivity() {

    private lateinit var repo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        repo = SettingsRepository(this)
        // Found once: from now on it also has its own row in the settings.
        if (!repo.settings.value.illumination) repo.update { it.copy(illumination = true) }
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(LocalSettings provides settings) {
                HearthTheme(dark = true) {
                    ClawdIlluminationScreen(
                        settings = settings,
                        onChange = { repo.update(it) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}
