package dev.hearth.launcher.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.ZenithCrypto
import kotlinx.coroutines.launch

/**
 * ZENITH 19: saving the settings into a file and bringing them back – now with a password if
 * wanted (the file is then encrypted with AES-256 and opens only with it). Used in the settings
 * and in ZENITH Cloud.
 */
@Composable
internal fun SecureBackupRows(
    vm: LauncherViewModel,
    fileName: String,
    saveLabel: String,
    saveDescription: String,
    restoreLabel: String,
    restoreDescription: String,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var sealWith by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(CreateTyped()) { uri ->
        val password = sealWith
        sealWith = null
        if (uri != null) {
            status = "Sichere …"
            scope.launch {
                val ok = vm.exportSettings(uri, password)
                status = when {
                    !ok -> "Sichern ging nicht."
                    password != null -> "Gesichert – verschlüsselt. Ohne das Passwort lässt sich die Datei nicht öffnen."
                    else -> "Gesichert."
                }
            }
        }
    }

    fun restoreFrom(uri: Uri, password: String?) {
        busy = true
        wrong = false
        scope.launch {
            val result = vm.importSettings(uri, password)
            busy = false
            when (result) {
                LauncherViewModel.Restore.Ok -> {
                    locked = null
                    status = "Wiederhergestellt."
                }
                LauncherViewModel.Restore.NeedsPassword -> locked = uri
                LauncherViewModel.Restore.WrongPassword -> wrong = true
                LauncherViewModel.Restore.Invalid -> {
                    locked = null
                    status = "Diese Datei ist keine ${dev.hearth.launcher.data.Brand.name}-Sicherung."
                }
            }
        }
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            status = null
            restoreFrom(uri, null)
        }
    }

    ActionRow(label = saveLabel, description = saveDescription) {
        status = null
        asking = true
    }
    RowDivider()
    ActionRow(label = restoreLabel, description = restoreDescription) {
        runCatching { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
    }
    status?.let { Note(it) }

    if (asking) {
        var pw by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        BackupDialog(onDismiss = { asking = false }) {
            Text("Sicherung schützen?", color = VaultText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mit Passwort wird die Datei verschlüsselt (AES-256): Wer sie findet, kann nichts darin lesen. Vergisst du das Passwort, lässt sie sich nicht wiederherstellen.",
                color = VaultDim,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            PasswordField(pw, { pw = it }, "Passwort (mind. ${ZenithCrypto.MIN_PASSWORD} Zeichen)")
            StrengthBar(pw)
            Spacer(Modifier.height(10.dp))
            PasswordField(again, { again = it }, "Passwort wiederholen", last = true)
            if (again.isNotEmpty() && again != pw) {
                Text("Die Passwörter sind nicht gleich.", color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            VaultButton(
                "Mit Passwort sichern",
                Modifier.fillMaxWidth(),
                enabled = pw.length >= ZenithCrypto.MIN_PASSWORD && pw == again,
                icon = Icons.Rounded.Lock,
            ) {
                asking = false
                sealWith = pw
                runCatching { save.launch("application/octet-stream" to "$fileName.zenith") }
            }
            Spacer(Modifier.height(8.dp))
            VaultButton("Ohne Passwort sichern", Modifier.fillMaxWidth(), primary = false) {
                asking = false
                sealWith = null
                runCatching { save.launch("application/json" to "$fileName.json") }
            }
        }
    }

    locked?.let { uri ->
        var pw by remember(uri) { mutableStateOf("") }
        BackupDialog(onDismiss = {
            locked = null
            wrong = false
        }) {
            Text("Passwort der Sicherung", color = VaultText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Diese Sicherung ist verschlüsselt. Gib das Passwort ein, mit dem sie gesichert wurde.", color = VaultDim, fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            PasswordField(pw, { pw = it }, "Passwort", last = true, onDone = { if (pw.isNotEmpty() && !busy) restoreFrom(uri, pw) })
            if (wrong) {
                Text("Falsches Passwort.", color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            VaultButton(if (busy) "Entschlüssele …" else "Wiederherstellen", Modifier.fillMaxWidth(), enabled = pw.isNotEmpty() && !busy) {
                restoreFrom(uri, pw)
            }
            Spacer(Modifier.height(8.dp))
            VaultButton("Abbrechen", Modifier.fillMaxWidth(), primary = false) {
                locked = null
                wrong = false
            }
        }
    }
}

@Composable
private fun BackupDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val shape = ZenithCutShape(18.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(VaultPanelHigh, VaultPanel)))
                .drawBehind { drawZenithOutlineEdge(shape.createOutline(size, layoutDirection, this)) }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            content()
        }
    }
}
