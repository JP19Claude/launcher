package dev.hearth.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * "Hearth-Einstellungen" as an app of its own, on the home screen and in the app drawer (in
 * any launcher): it opens Hearth with its settings and is gone again at once.
 */
class SettingsShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        finish()
    }
}
