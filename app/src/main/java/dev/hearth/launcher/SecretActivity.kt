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
import dev.hearth.launcher.ui.SecretMenu
import dev.hearth.launcher.ui.SecretMenuScreen
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * ZENITH 19.6: a hidden menu, opened by touching the system – a long press on a tile in the
 * ZENITH menu, quick taps on a line in "Über das Telefon", the clock at the right minute.
 * Which one comes in the intent ([EXTRA]).
 */
class SecretActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val menu = runCatching { SecretMenu.valueOf(intent.getStringExtra(EXTRA).orEmpty()) }.getOrNull()
        if (menu == null) {
            finish()
            return
        }
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalGlassStyle provides GlassStyle.from(settings),
            ) {
                HearthTheme(dark = true) {
                    SecretMenuScreen(menu) { finish() }
                }
            }
        }
    }

    companion object {
        const val EXTRA = "menu"
    }
}
