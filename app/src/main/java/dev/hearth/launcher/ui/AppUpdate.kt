package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppUpdater
import kotlinx.coroutines.launch
import java.io.File

/** Where an update stands. */
private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    class Newer(val release: AppUpdater.Release) : UpdateState
    class Downloading(val release: AppUpdater.Release, val progress: Float) : UpdateState
    class Ready(val release: AppUpdater.Release, val apk: File) : UpdateState
    class Failed(val reason: String) : UpdateState
}

private val UpdateAccent = Color(0xFFD97757)

/**
 * "Updates" for whichever app of the family this is: looks for a newer version as soon as it's
 * shown, downloads it and hands it to Android's installer – all without leaving the app.
 */
@Composable
fun AppUpdateContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = remember { AppUpdater.appName(context) }
    val current = remember { AppUpdater.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            state = when (val result = AppUpdater.check(context)) {
                is AppUpdater.Check.Newer -> UpdateState.Newer(result.release)
                AppUpdater.Check.UpToDate -> UpdateState.UpToDate
                is AppUpdater.Check.Failed -> UpdateState.Failed(result.reason)
            }
        }
    }

    fun install(release: AppUpdater.Release, apk: File) {
        // Android lets an app install updates only once it's allowed to (asked once).
        if (!AppUpdater.canInstall(context)) {
            AppUpdater.askInstallPermission(context)
            state = UpdateState.Ready(release, apk)
            return
        }
        if (!AppUpdater.install(context, apk)) state = UpdateState.Failed("Die Installation ließ sich nicht starten")
    }

    fun download(release: AppUpdater.Release) {
        state = UpdateState.Downloading(release, 0f)
        scope.launch {
            val apk = AppUpdater.download(context, release) { p ->
                scope.launch { state = UpdateState.Downloading(release, p) }
            }
            if (apk == null) {
                state = UpdateState.Failed("Download fehlgeschlagen – bist du online?")
                return@launch
            }
            state = UpdateState.Ready(release, apk)
            install(release, apk)
        }
    }

    // Looks right away, once.
    LaunchedEffect(Unit) { check() }

    val s = state
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("$name $current", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            when (s) {
                UpdateState.Checking -> "Suche nach einer neuen Version …"
                UpdateState.UpToDate -> "Du hast die neueste Version ✓"
                is UpdateState.Newer -> "Neue Version ${s.release.version} ist da (${megabytes(s.release.size)})"
                is UpdateState.Downloading -> "Lade ${s.release.version} … ${(s.progress * 100).toInt()} %"
                is UpdateState.Ready -> if (AppUpdater.canInstall(context)) {
                    "Version ${s.release.version} ist geladen – Android fragt gleich, ob du sie installieren willst"
                } else {
                    "Erlaube $name einmal „Unbekannte Apps installieren“, dann auf „Installieren“ tippen"
                }
                is UpdateState.Failed -> s.reason
            },
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        if (s is UpdateState.Downloading) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(s.progress.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(UpdateAccent),
                )
            }
        }
        val action: Pair<String, () -> Unit>? = when (s) {
            UpdateState.Checking, is UpdateState.Downloading -> null
            UpdateState.UpToDate, is UpdateState.Failed -> "Nach Updates suchen" to { check() }
            is UpdateState.Newer -> "Jetzt aktualisieren" to { download(s.release) }
            is UpdateState.Ready -> "Installieren" to { install(s.release, s.apk) }
        }
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (s is UpdateState.Newer || s is UpdateState.Ready) UpdateAccent else Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = action.second)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(action.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "?" else String.format(java.util.Locale.GERMAN, "%.1f MB", bytes / 1_048_576.0)
