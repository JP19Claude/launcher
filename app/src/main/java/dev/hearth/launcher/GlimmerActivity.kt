package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.GlimmerSettingsScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * The Glimmer app's own screen: setting it up (accessibility service, notification access)
 * and all of Glimmer's options, the control center over other apps and the lock screen.
 */
class GlimmerActivity : ComponentActivity() {

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
                GlimmerSettingsScreen(repo, media)
            }
        }
    }
}
