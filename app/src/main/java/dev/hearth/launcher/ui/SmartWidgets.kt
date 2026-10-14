package dev.hearth.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * ZENITH 21, the Smart widgets: the battery as a bar that fills (as Smart Launcher draws it) and the next events of
 * the calendar on a card. Both sit on the liquid glass of the widget page like ZENITH's own.
 */

/** A bar with the battery's percent big in it; the bar's fill is the battery – blue, green while charging, red when nearly empty. */
@Composable
internal fun SmartBatteryWidget(modifier: Modifier) {
    val (level, charging) = rememberBatteryLevel()
    val fill = when {
        charging -> Color(0xFF34C759)
        level in 0..20 -> Color(0xFFFF453A)
        else -> Color(0xFF2F8CFF)
    }
    val fraction = if (level < 0) 0f else (level / 100f).coerceIn(0f, 1f)
    Box(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(fill.copy(alpha = 0.75f)),
        )
        Text(
            if (level >= 0) "$level%" else "–",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp),
        )
    }
}

/** The next three events: when, and what – or the way to the calendar's permission. */
@Composable
internal fun SmartEventsWidget(modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberTime(everySecond = false)
    val allowed = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val events by produceState<List<SmartEvent>>(emptyList(), allowed, now.hour * 12 + now.minute / 5) {
        value = if (allowed) withContext(Dispatchers.IO) { upcomingCalendarEvents(context, 3) } else emptyList()
    }
    val locale = Locale.getDefault()
    Column(
        modifier
            .padding(16.dp)
            .clickable {
                runCatching {
                    if (allowed) {
                        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Termine", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(now.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale)), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        when {
            !allowed -> Text("Kalenderzugriff erlauben – hier antippen", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            events.isEmpty() -> Text("Keine Termine in den nächsten Tagen", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            else -> events.forEach { event ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2F8CFF)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(event.timeLabel(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp), maxLines = 1)
                    Text(event.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
