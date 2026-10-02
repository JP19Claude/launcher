package dev.hearth.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.SystemControls
import dev.hearth.launcher.ui.ClaudeAssistantSheet
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * Claude over any app: what opens on a long press on home (with Hearth as the phone's digital
 * assistant), from the quick settings tile, from Glimmer and from the control center. It shares
 * the conversation with the launcher's sheet.
 *
 * Questions passed in are only taken through [ClaudeAssistant.ASK_ALIAS], which only the
 * family's apps may open, so no other app can make Claude do things on the phone.
 */
class ClaudeAssistActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClaudeAssistant.init(this)
        if (!ClaudeAssistant.settings.value.enabled) {
            // Switched off: the Claude app (or claude.ai) as before.
            val controls = SystemControls(applicationContext)
            controls.openClaudeApp(question(intent))
            controls.close()
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) question(intent)?.let { ClaudeAssistant.ask(it) }
        setContent {
            HearthTheme(dark = true) {
                ClaudeAssistantSheet(
                    onDismiss = { finish() },
                    onOpenSettings = {
                        runCatching {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        question(intent)?.let { ClaudeAssistant.ask(it) }
    }

    /** A question, only when it came through the family's protected door. */
    private fun question(intent: Intent?): String? {
        if (intent?.component?.className != ClaudeAssistant.ASK_ALIAS) return null
        return intent.getStringExtra(ClaudeAssistant.EXTRA_PROMPT)?.takeIf { it.isNotBlank() }
    }
}
