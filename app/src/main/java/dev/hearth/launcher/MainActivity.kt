package dev.hearth.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.ThemeMode
import dev.hearth.launcher.ui.LauncherScreen
import dev.hearth.launcher.ui.theme.HearthTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system bar icons: wallpapers are usually dark enough behind them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) viewModel.requestSettings()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            HearthTheme(dark = dark) {
                LauncherScreen(viewModel)
            }
        }
    }

    /** True between leaving the launcher (onStop) and showing it again (onResume). */
    private var wasInBackground = false

    override fun onStop() {
        super.onStop()
        wasInBackground = true
    }

    override fun onResume() {
        super.onResume()
        wasInBackground = false
        viewModel.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            viewModel.requestSettings()
            return
        }
        // Home button: either pressed on the home screen itself, or on the way back from an app.
        viewModel.onHomePressed(alreadyInFront = !wasInBackground)
    }

    companion object {
        /** Opens the launcher settings right away (used by the control center overlay). */
        const val EXTRA_OPEN_SETTINGS = "dev.hearth.launcher.OPEN_SETTINGS"
    }
}
