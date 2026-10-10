package dev.hearth.launcher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.ZenithVault
import dev.hearth.launcher.ui.GlassStyle
import dev.hearth.launcher.ui.LocalGlassStyle
import dev.hearth.launcher.ui.LocalSettings
import dev.hearth.launcher.ui.VaultHost
import dev.hearth.launcher.ui.VaultScreen
import dev.hearth.launcher.ui.theme.HearthTheme
import javax.crypto.Cipher

/**
 * ZENITH 19: the ZENITH-Tresor's screen. Never in screenshots, screen recordings or the recent
 * apps; it locks as soon as it's left (except for a moment while a file is being picked or
 * looked at in another app – and even then after three minutes).
 */
class VaultActivity : ComponentActivity() {

    /** What was shared into the vault from another app, waiting for it to be unlocked. */
    private var incoming by mutableStateOf<List<Uri>>(emptyList())
    private var keepOpen = false
    private var leftAt = 0L
    private var cancel: CancellationSignal? = null

    private val host = object : VaultHost {
        override fun stayOpen() {
            keepOpen = true
        }

        override fun fingerprintAvailable(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                runCatching {
                    getSystemService(BiometricManager::class.java)
                        ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                }.getOrDefault(false)

        override fun askFingerprint(cipher: Cipher, title: String, done: (Cipher?, String?) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                prompt(cipher, title, done)
            } else {
                done(null, "Fingerabdruck geht hier nicht.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // What was opened in another app last time goes now at the latest.
        if (!ZenithVault.unlocked.value) ZenithVault.wipeOpened(this)
        if (savedInstanceState == null) take(intent)
        val repo = SettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsState()
            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalGlassStyle provides GlassStyle.from(settings),
            ) {
                HearthTheme(dark = true) {
                    VaultScreen(
                        host = host,
                        incoming = incoming,
                        onIncomingTaken = { incoming = emptyList() },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    /** Photos and files shared into the vault ("Teilen" → "ZENITH-Tresor"). */
    private fun take(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.ifEmpty {
            val clip = intent?.clipData
            if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                (0 until (clip?.itemCount ?: 0)).mapNotNull { clip?.getItemAt(it)?.uri }
            } else {
                emptyList()
            }
        }
        if (uris.isNotEmpty()) incoming = uris
    }

    override fun onStart() {
        super.onStart()
        if (leftAt > 0L && SystemClock.elapsedRealtime() - leftAt > 3 * 60_000L) ZenithVault.lock(this)
        leftAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (keepOpen) {
            keepOpen = false
            leftAt = SystemClock.elapsedRealtime()
        } else {
            ZenithVault.lock(this)
        }
    }

    override fun onDestroy() {
        runCatching { cancel?.cancel() }
        if (!isChangingConfigurations) ZenithVault.lock(this)
        super.onDestroy()
    }

    private companion object {
        /** "Passwort verwenden" was tapped (the platform has no public name for it). */
        const val ERROR_NEGATIVE_BUTTON = 13
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun prompt(cipher: Cipher, title: String, done: (Cipher?, String?) -> Unit) {
        var answered = false
        fun answer(result: Cipher?, why: String?) {
            if (answered) return
            answered = true
            done(result, why)
        }
        val signal = CancellationSignal().also { cancel = it }
        try {
            val dialog = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle("Mit deinem Fingerabdruck")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Passwort verwenden", mainExecutor) { _, _ -> answer(null, null) }
                .build()
            dialog.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        answer(result.cryptoObject?.cipher, null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val quiet = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                            errorCode == ERROR_NEGATIVE_BUTTON
                        answer(null, if (quiet) null else errString.toString())
                    }
                },
            )
        } catch (e: Exception) {
            answer(null, "Fingerabdruck geht gerade nicht.")
        }
    }
}
