package dev.hearth.launcher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.hearth.launcher.data.GlimmerDrop
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.ui.GlimmerDropScreen
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * Glimmer Drop: hold two phones together and share. Opened from the Hearth menu, the settings,
 * Android's share sheet ("Glimmer Drop") – or by Glimmer itself, when a phone touches this one.
 */
class DropActivity : ComponentActivity() {

    /** A picker is open in front: leaving for it doesn't end Glimmer Drop. */
    private var picking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        GlimmerDrop.open(this)
        if (savedInstanceState == null) takeShared(intent)
        val look = SettingsRepository(this).settings.value
        setContent {
            CompositionLocalProvider(LocalSettings provides look) {
                HearthTheme(dark = true) {
                    GlimmerDropScreen(
                        onPicking = { picking = true },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShared(intent)
    }

    override fun onStart() {
        super.onStart()
        picking = false
        GlimmerDrop.open(this)
    }

    override fun onStop() {
        super.onStop()
        // Going away (home, another app) ends Glimmer Drop; a picker in front doesn't.
        if (!picking && !isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) GlimmerDrop.close()
    }

    /** Shared from another app: text and links, or files. */
    private fun takeShared(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { GlimmerDrop.addText(it) }
                stream(intent)?.let { GlimmerDrop.addFiles(this, listOf(it)) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = streams(intent)
                if (uris.isNotEmpty()) GlimmerDrop.addFiles(this, uris)
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { GlimmerDrop.addText(it) }
            }
        }
    }

    private fun stream(intent: Intent): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        }
        @Suppress("DEPRECATION")
        val legacy: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        return legacy
    }

    private fun streams(intent: Intent): List<Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        }
        @Suppress("DEPRECATION")
        val legacy: ArrayList<Uri>? = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        return legacy.orEmpty()
    }
}
