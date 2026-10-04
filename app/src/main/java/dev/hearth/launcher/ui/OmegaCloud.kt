package dev.hearth.launcher.ui

import android.app.backup.BackupManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.ShizukuBridge
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** A cloud of ruby light, with the Ω in it. */
@Composable
private fun CloudMark() {
    Box(Modifier.size(width = 150.dp, height = 96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 150.dp, height = 96.dp)) {
            val face = Brush.verticalGradient(listOf(Color(0xFFFFC2CA), Color(0xFFE5243F), Color(0xFF7A0A1E)))
            val w = size.width
            val h = size.height
            drawCircle(face, h * 0.34f, Offset(w * 0.34f, h * 0.58f), alpha = 0.9f)
            drawCircle(face, h * 0.42f, Offset(w * 0.55f, h * 0.45f), alpha = 0.9f)
            drawCircle(face, h * 0.3f, Offset(w * 0.74f, h * 0.62f), alpha = 0.9f)
            drawRoundRect(face, topLeft = Offset(w * 0.2f, h * 0.6f), size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.16f), alpha = 0.9f)
        }
        Text(
            "Ω",
            style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFD6DC))), fontSize = 34.sp, fontWeight = FontWeight.Black),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * OMEGA Cloud: the settings, widgets, Clawd and easter eggs, kept with your Google account
 * (Android's own backup, end-to-end encrypted with the screen lock) – sent right away with
 * Shizuku – and as a file to take anywhere.
 */
@Composable
internal fun OmegaCloudSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("omega_cloud", Context.MODE_PRIVATE) }
    var last by remember { mutableLongStateOf(prefs.getLong("last", 0L)) }
    var status by remember { mutableStateOf<String?>(null) }
    var cloudOn by remember { mutableStateOf<Boolean?>(null) }
    var where by remember { mutableStateOf<String?>(null) }
    val shizuku = remember { ShizukuBridge.isReady(context) }
    LaunchedEffect(Unit) {
        if (!shizuku) return@LaunchedEffect
        cloudOn = ShizukuBridge.output("bmgr enabled")?.contains("currently enabled", ignoreCase = true)
        where = ShizukuBridge.output("bmgr list transports")
            ?.lines()
            ?.firstOrNull { it.trim().startsWith("*") }
            ?.let { if ("google" in it.lowercase()) "Google-Konto (Google Drive)" else it.trim().removePrefix("*").trim() }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) status = if (vm.exportSettings(uri)) "Als Datei gesichert." else "Sichern ging nicht."
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) status = if (vm.importSettings(uri)) "Wiederhergestellt." else "Diese Datei ist keine OMEGA-Sicherung."
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CloudMark()
        Spacer(Modifier.height(6.dp))
        Text(
            "OMEGA Cloud",
            style = TextStyle(brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFF8A98), Color(0xFFE5243F))), fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            when (cloudOn) {
                true -> "Aktiv · ${where ?: "Google-Konto"}"
                false -> "Android-Sicherung ist aus"
                null -> "Über dein Google-Konto"
            },
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
        )
        if (last > 0L) {
            Text(
                "Zuletzt gesichert: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
    Section("Cloud") {
        ActionRow(
            label = "Jetzt in die Cloud sichern",
            description = if (shizuku) "Sofort, über dein Google-Konto" else "Android sichert beim nächsten Laden im WLAN (mit Shizuku sofort)",
        ) {
            if (shizuku) {
                status = "Sichere …"
                scope.launch {
                    val out = ShizukuBridge.output("bmgr backupnow ${context.packageName}").orEmpty()
                    val ok = "success" in out.lowercase()
                    if (ok) {
                        last = System.currentTimeMillis()
                        prefs.edit().putLong("last", last).apply()
                    }
                    status = if (ok) "In der Cloud gesichert. ☁️" else if (cloudOn == false) "Erst die Android-Sicherung einschalten (unten)." else "Hat nicht geklappt – später nochmal."
                }
            } else {
                BackupManager(context).dataChanged()
                last = System.currentTimeMillis()
                prefs.edit().putLong("last", last).apply()
                status = "Vorgemerkt – Android sichert beim nächsten Laden im WLAN."
            }
        }
        RowDivider()
        ActionRow(
            label = "Android-Sicherung",
            description = "Ein- oder ausschalten und das Google-Konto wählen",
        ) {
            runCatching {
                context.startActivity(
                    android.content.Intent("android.settings.PRIVACY_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        Note("Gesichert werden Einstellungen, Startbildschirm, Widgets, Clawd, Glimmer und die Easter Eggs – verschlüsselt mit deiner Bildschirmsperre. Auf einem neuen Handy mit deinem Google-Konto holt Android sie beim Installieren zurück.")
    }
    Section("Als Datei") {
        ActionRow(
            label = "In Datei sichern",
            description = "Alle Einstellungen als Datei (ohne API-Schlüssel)",
        ) { runCatching { save.launch("OMEGA-UI-Sicherung.json") } }
        RowDivider()
        ActionRow(
            label = "Aus Datei wiederherstellen",
            description = "Auch auf einem neuen Handy",
        ) { runCatching { restore.launch(arrayOf("application/json", "text/plain", "*/*")) } }
    }
    status?.let { Note(it) }
}
