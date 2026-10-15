package dev.hearth.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.crash.CrashGuard

/**
 * After a crash (the report of it), and while ZENITH runs in the safe mode: what happened, the report to copy or share,
 * and the way back to the normal start. Shown over everything at the start.
 */
@Composable
internal fun CrashNotice(onClose: () -> Unit) {
    val context = LocalContext.current
    val report = remember { CrashGuard.report(context) }
    var safe by remember { mutableStateOf(CrashGuard.safe) }
    var ended by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { CrashGuard.shown(context) }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF14171C))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Text("ZENITH ist abgestürzt", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (safe && !ended) {
                    "ZENITH läuft jetzt abgesichert: „Be Smarter“, „Be Comet“, der ZENITH-Hintergrund und die Zeremonien nach einem Update sind aus, " +
                        "und ein Super-Wallpaper wurde vom Telefon genommen. Deine Einstellungen sind unverändert."
                } else if (ended) {
                    "Beim nächsten Start läuft ZENITH wieder normal."
                } else {
                    "ZENITH ist beim letzten Mal abgestürzt. Der Bericht unten hilft, den Fehler zu beheben."
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            if (report != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1E25))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    SelectionContainer {
                        Text(report.take(2400), color = Color(0xFFC9D1D9), fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    NoticeButton("Bericht kopieren", Modifier.weight(1f)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ZENITH-Absturzbericht", report))
                        Toast.makeText(context, "Kopiert", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.width(10.dp))
                    NoticeButton("Teilen", Modifier.weight(1f)) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Absturzbericht teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            if (safe && !ended) {
                Spacer(Modifier.height(10.dp))
                NoticeButton("Abgesicherten Modus beenden", Modifier.fillMaxWidth()) {
                    CrashGuard.setSafe(context, false)
                    ended = true
                    safe = false
                }
            }
            Spacer(Modifier.height(10.dp))
            NoticeButton("Weiter", Modifier.fillMaxWidth(), primary = true, onClick = onClose)
        }
    }
}

@Composable
private fun NoticeButton(label: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Color(0xFF62C8FF) else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) Color(0xFF101418) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
