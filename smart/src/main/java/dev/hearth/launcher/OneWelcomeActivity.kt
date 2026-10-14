package dev.hearth.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.HearthOneWelcome
import dev.hearth.launcher.ui.theme.HearthTheme

/** Hearth One's welcome after the upgrade: what came over, the old apps to remove. */
class OneWelcomeActivity : ComponentActivity() {

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
                    HearthOneWelcome(onDone = { finish() })
                }
            }
        }
    }
}
