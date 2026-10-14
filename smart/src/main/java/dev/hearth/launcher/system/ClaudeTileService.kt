package dev.hearth.launcher.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.hearth.launcher.ClaudeAssistActivity

/** "Claude" in the system's quick settings: one tap, and Claude is there, over any app. */
class ClaudeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        runCatching {
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                label = "Claude"
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openClaude() } else openClaude()
    }

    private fun openClaude() {
        val intent = Intent(this, ClaudeAssistActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure {
            runCatching { startActivity(intent) }
        }
    }
}
