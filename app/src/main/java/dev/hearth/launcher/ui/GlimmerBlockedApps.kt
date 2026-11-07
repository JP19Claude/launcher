package dev.hearth.launcher.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * ZENITH 22.1: the apps Glimmer keeps quiet about. A row in Glimmer's settings ("Was Glimmer zeigt") opens a list of the apps on the
 * phone; the ones switched on there show nothing in the island – no messages, no codes, no calls, timers or updates. (The notification
 * itself still comes to the phone as always: only the island leaves it out.)
 */

/** The row: how many apps are muted, and the list behind it. */
@Composable
internal fun GlimmerBlockedRow(blocked: Set<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ActionRow(
        label = "Apps ohne Meldung in Glimmer",
        description = if (blocked.isEmpty()) {
            "Von diesen Apps zeigt Glimmer nichts – keine Nachrichten, Codes, Anrufe, Timer. Noch keine gewählt"
        } else {
            "${blocked.size} ${if (blocked.size == 1) "App" else "Apps"} stumm in Glimmer"
        },
    ) { open = true }
    if (open) GlimmerAppsDialog(blocked, onChange) { open = false }
}

@Composable
private fun GlimmerAppsDialog(blocked: Set<String>, onChange: (Set<String>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // Every app with a launcher icon (name and package), read once off the main thread.
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }
                    .filter { it.first != context.packageName }
                    .sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    // (Apps chosen earlier and gone from the list since – uninstalled, or without an icon – stay listed by their package name.)
    val rows = remember(apps, blocked) {
        val known = apps.orEmpty()
        val extra = blocked.filter { pkg -> known.none { it.first == pkg } }.map { it to it }
        (known + extra).sortedBy { it.second.lowercase() }
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF16141C))
                .padding(vertical = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Apps ohne Meldung in Glimmer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (blocked.isEmpty()) "Tippe die Apps an, die in Glimmer schweigen sollen" else "${blocked.size} stumm",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "Fertig",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            if (apps == null) {
                Text("Die Apps werden gelesen …", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 8.dp)) {
                    items(rows, key = { it.first }) { (pkg, label) ->
                        val muted = pkg in blocked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChange(if (muted) blocked - pkg else blocked + pkg) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GlassSwitch(checked = muted, onCheckedChange = { on -> onChange(if (on) blocked + pkg else blocked - pkg) })
                        }
                    }
                }
            }
        }
    }
}
