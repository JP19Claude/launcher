package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.ClawdJumpGame
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.theme.HearthTheme

/** Clawd Jump, full screen: from the chat ("Lass uns spielen"), the widgets and the settings. */
class ClawdGameActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Clawd in his colors, hat and outfit.
        val look = SettingsRepository(this).settings.value
        setContent {
            CompositionLocalProvider(LocalSettings provides look) {
                HearthTheme(dark = true) {
                    ClawdJumpGame(onClose = { finish() })
                }
            }
        }
    }
}
