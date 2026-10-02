package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.ControlsSettingsScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * The control center app's own screen: setting it up (accessibility service, permissions)
 * and the glass control center over other apps with its look.
 */
class ControlsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val repo = SettingsRepository(this)
        val media = MediaRepository(this)
        setContent {
            HearthTheme(dark = true) {
                ControlsSettingsScreen(repo, media)
            }
        }
    }
}
