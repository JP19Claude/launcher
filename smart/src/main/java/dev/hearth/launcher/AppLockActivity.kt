package dev.hearth.launcher

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color as AndroidColor
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppLock
import dev.hearth.launcher.system.GlimmerLink
import dev.hearth.launcher.ui.Glyph
import dev.hearth.launcher.ui.GlyphIcon
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * The app lock's screen: it covers a locked app and asks for the phone's own PIN, pattern,
 * password, fingerprint or face (the system's dialog; Hearth never sees any of it). Only
 * after that does the app open (from Hearth) or show again (when Glimmer caught it opening).
 * Cancelled, the app stays covered, and leaving goes home.
 *
 * Only the family's apps may open this screen (signature permission in the manifest).
 */
class AppLockActivity : ComponentActivity() {

    private var target: String = ""
    private var component: ComponentName? = null
    private var user: UserHandle? = null
    /** Confirming that the lock may be taken off (instead of opening the app). */
    private var removing by mutableStateOf(false)
    private var authenticating = false
    /** The first question waits until the screen is really in front (see [onResume]). */
    private var askOnResume = false
    private var cancel: CancellationSignal? = null

    private var message by mutableStateOf<String?>(null)
    private var label by mutableStateOf("")
    private var noScreenLock by mutableStateOf(false)

    /** Android 9: the system's own PIN/pattern screen. */
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        if (result.resultCode == RESULT_OK) onUnlocked() else message = "Nicht entsperrt."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        if (!readTarget(intent)) {
            finish()
            return
        }
        val look = dev.hearth.launcher.data.SettingsRepository(this).settings.value
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(dev.hearth.launcher.ui.LocalSettings provides look) {
                HearthTheme(dark = true) {
                    BackHandler { leave() }
                    LockScreen()
                }
            }
        }
        // Asked once the screen is in front: some phones (Samsung among them) silently drop a
        // fingerprint dialog requested while the screen is still opening.
        askOnResume = savedInstanceState == null
    }

    override fun onResume() {
        super.onResume()
        if (askOnResume) {
            askOnResume = false
            window.decorView.postDelayed({ if (!isFinishing) authenticate() }, 250)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readTarget(intent)) {
            message = null
            askOnResume = true
        }
    }

    override fun onDestroy() {
        runCatching { cancel?.cancel() }
        super.onDestroy()
    }

    private fun readTarget(intent: Intent?): Boolean {
        val pkg = intent?.getStringExtra(AppLock.EXTRA_PACKAGE)?.takeIf { it.isNotBlank() } ?: return false
        target = pkg
        @Suppress("DEPRECATION")
        component = intent.getParcelableExtra(AppLock.EXTRA_COMPONENT)
        @Suppress("DEPRECATION")
        user = intent.getParcelableExtra(AppLock.EXTRA_USER)
        removing = intent.getBooleanExtra(AppLock.EXTRA_REMOVE, false)
        label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        return true
    }

    /**
     * Asks for the fingerprint, face or PIN. A tap on "Entsperren" always starts afresh, even
     * if an earlier dialog never showed up (so the button can never get stuck).
     */
    private fun authenticate(restart: Boolean = false) {
        if (isFinishing) return
        if (authenticating && !restart) return
        if (restart) {
            runCatching { cancel?.cancel() }
            cancel = null
            authenticating = false
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            // Without a screen lock there's nothing to ask for.
            noScreenLock = true
            message = "Auf diesem Handy ist keine Bildschirmsperre eingerichtet. Richte PIN, Muster oder Passwort ein, damit die App-Sperre schützen kann."
            return
        }
        noScreenLock = false
        authenticating = true
        message = null
        val title = if (removing) "App-Sperre für „$label“ aufheben" else "„$label“ entsperren"
        val subtitle = "Mit Fingerabdruck, Gesicht oder der PIN deines Handys"
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val prompt = BiometricPrompt.Builder(this)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                        .build()
                    showPrompt(prompt)
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    @Suppress("DEPRECATION")
                    val prompt = BiometricPrompt.Builder(this)
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setDeviceCredentialAllowed(true)
                        .build()
                    showPrompt(prompt)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    val confirm = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
                    if (confirm == null) {
                        authenticating = false
                        onUnlocked()
                    } else {
                        confirmCredential.launch(confirm)
                    }
                }
            }
        } catch (e: Exception) {
            authenticating = false
            // The fingerprint dialog didn't work out: the phone's own PIN screen instead.
            askWithPin()
        }
    }

    /** The phone's own PIN, pattern or password screen – works on every Android version. */
    private fun askWithPin() {
        if (isFinishing) return
        runCatching { cancel?.cancel() }
        cancel = null
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            authenticate()
            return
        }
        @Suppress("DEPRECATION")
        val confirm = runCatching {
            keyguard.createConfirmDeviceCredentialIntent(
                if (removing) "App-Sperre für „$label“ aufheben" else "„$label“ entsperren",
                "Gib die PIN, das Muster oder das Passwort deines Handys ein",
            )
        }.getOrNull()
        if (confirm == null) {
            message = "Die PIN-Abfrage ließ sich nicht öffnen."
            return
        }
        authenticating = true
        runCatching { confirmCredential.launch(confirm) }.onFailure {
            authenticating = false
            message = "Die PIN-Abfrage ließ sich nicht öffnen."
        }
    }

    private fun showPrompt(prompt: BiometricPrompt) {
        val signal = CancellationSignal()
        cancel = signal
        prompt.authenticate(
            signal,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    message = when (errorCode) {
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> "Nicht entsperrt."
                        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
                            "Zu viele Versuche. Versuch es gleich noch einmal."
                        else -> errString.toString().ifBlank { "Nicht entsperrt." }
                    }
                }

                // A wrong finger or face: the system dialog says so itself and stays open.
                override fun onAuthenticationFailed() = Unit
            },
        )
    }

    private fun onUnlocked() {
        val pkg = target
        if (removing) {
            AppLock.removeHandler?.invoke(pkg)
            finish()
            return
        }
        AppLock.markUnlocked(pkg)
        // Tell Glimmer first (it watches apps opening), then open the app.
        Thread {
            runCatching { GlimmerLink.appUnlocked(applicationContext, pkg) }
            runOnUiThread { proceed() }
        }.start()
    }

    private fun proceed() {
        val c = component
        if (c != null) {
            // Opened from Hearth: start the app now.
            val launcher = getSystemService(LauncherApps::class.java)
            val started = runCatching {
                launcher.startMainActivity(c, user ?: android.os.Process.myUserHandle(), null, null)
            }.isSuccess
            if (!started) {
                runCatching { packageManager.getLaunchIntentForPackage(target)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
        }
        // Caught by Glimmer: the app is right underneath.
        finish()
    }

    /** Not unlocked: away from the locked app, to the home screen. */
    private fun leave() {
        runCatching { cancel?.cancel() }
        if (component == null && !removing) {
            runCatching {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF00B0B0F)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Clawd guards the app, with a little lock at his side.
                Box(Modifier.size(104.dp)) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        dev.hearth.launcher.ui.Clawd(Modifier.size(width = 66.dp, height = 58.dp))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C30)),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Lock, Color.White, Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.size(20.dp))
                Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.size(6.dp))
                Text(if (removing) "App-Sperre aufheben?" else "ist gesperrt – Clawd passt auf", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                message?.let {
                    Spacer(Modifier.size(16.dp))
                    Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.size(28.dp))
                Pill(if (noScreenLock) "Bildschirmsperre einrichten" else "Entsperren", primary = true) {
                    if (noScreenLock) {
                        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } else {
                        authenticate(restart = true)
                    }
                }
                if (!noScreenLock) {
                    Spacer(Modifier.size(12.dp))
                    Pill("Mit PIN entsperren", primary = false) { askWithPin() }
                }
                Spacer(Modifier.size(12.dp))
                Pill("Abbrechen", primary = false) { leave() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
        Text(
            text,
            color = if (primary) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (primary) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 13.dp),
        )
    }
}
