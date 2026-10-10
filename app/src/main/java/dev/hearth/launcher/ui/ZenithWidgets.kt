package dev.hearth.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ZenithShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One reading of the phone for the ZENITH-Monitor. */
private class MonitorReading(
    val battery: Int,
    val charging: Boolean,
    val temperature: Float,
    val voltage: Float,
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val uptimeMinutes: Long,
)

private fun readMonitor(context: Context): MonitorReading {
    val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
    val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val temperature = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val voltage = (battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
    val memory = ActivityManager.MemoryInfo()
    runCatching { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory) }
    val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
    val total = stat?.totalBytes ?: 0L
    val free = stat?.availableBytes ?: 0L
    return MonitorReading(
        battery = if (level < 0) -1 else level * 100 / scale,
        charging = plugged,
        temperature = temperature,
        voltage = voltage,
        ramUsed = (memory.totalMem - memory.availMem).coerceAtLeast(0L),
        ramTotal = memory.totalMem,
        storageUsed = (total - free).coerceAtLeast(0L),
        storageTotal = total,
        uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L,
    )
}

private fun gigabytes(bytes: Long): String = "%.1f".format(bytes / 1_073_741_824.0)

/**
 * ZENITH 19.1, the ZENITH-Monitor: the phone's insides as a HUD – battery with temperature and
 * voltage, working memory, storage and how long it's been running, live, each with a bar of
 * green light. Tapped, the Schutzschild opens.
 */
@Composable
internal fun ZenithMonitorWidget(modifier: Modifier) {
    val context = LocalContext.current
    val reading by produceState<MonitorReading?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { readMonitor(context) }
            delay(3000)
        }
    }
    Column(
        modifier
            .clickable {
                runCatching {
                    context.startActivity(Intent(context, dev.hearth.launcher.ShieldActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZenithMark(color = ZenithGreen, width = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("ZENITH // MONITOR", color = HudGreen, fontSize = 11.sp, fontFamily = HudFont, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val r = reading
            if (r != null) {
                val h = r.uptimeMinutes / 60
                val m = r.uptimeMinutes % 60
                Text(
                    "LÄUFT ${if (h > 0) "${h}h " else ""}${m}m",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontFamily = HudFont,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val r = reading
        if (r == null) {
            Text("Wird gemessen …", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontFamily = HudFont)
        } else {
            MonitorBar(
                "AKKU",
                "${r.battery}%" + (if (r.charging) " ▲" else "") + " · ${"%.1f".format(r.temperature)}°C · ${"%.2f".format(r.voltage)} V",
                r.battery / 100f,
                if (r.temperature >= 42f) VaultRed else if (r.battery in 0..15) ZenithSun else ZenithGreen,
            )
            MonitorBar(
                "RAM",
                "${gigabytes(r.ramUsed)} / ${gigabytes(r.ramTotal)} GB",
                if (r.ramTotal > 0) r.ramUsed.toFloat() / r.ramTotal else 0f,
                ZenithGreen,
            )
            MonitorBar(
                "SPEICHER",
                "${gigabytes(r.storageUsed)} / ${gigabytes(r.storageTotal)} GB",
                if (r.storageTotal > 0) r.storageUsed.toFloat() / r.storageTotal else 0f,
                if (r.storageTotal > 0 && r.storageUsed.toFloat() / r.storageTotal > 0.9f) ZenithSun else ZenithGreen,
            )
        }
    }
}

@Composable
private fun MonitorBar(label: String, value: String, fill: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HudGreen.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = HudFont, letterSpacing = 1.5.sp, modifier = Modifier.width(76.dp))
            Text(value, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontFamily = HudFont, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(ZenithCutShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0.01f, 1f))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color))),
            )
        }
    }
}

/**
 * ZENITH 19.1, the Schutzschild widget: the protection score as an arc round the metal Z, checked
 * again every few minutes. Tapped, the Schutzschild opens with all the details.
 */
@Composable
internal fun ShieldWidget(modifier: Modifier) {
    val context = LocalContext.current
    val score by produceState<Int?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { ZenithShield.score(ZenithShield.checks(context)) }.getOrNull() }
            delay(10 * 60_000L)
        }
    }
    val s = score
    val color = when {
        s == null -> Color.White.copy(alpha = 0.4f)
        s >= 85 -> ZenithGreen
        s >= 60 -> ZenithSun
        else -> VaultRed
    }
    BoxWithConstraints(
        modifier
            .clickable {
                runCatching {
                    context.startActivity(Intent(context, dev.hearth.launcher.ShieldActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight * 0.8f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(side)) {
                val c = center
                val r = size.minDimension / 2f * 0.84f
                val stroke = size.minDimension * 0.06f
                val box = Size(r * 2f, r * 2f)
                val at = Offset(c.x - r, c.y - r)
                val fill = (s ?: 0) / 100f
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.2f * fill), Color.Transparent), center = c, radius = r * 1.15f), radius = r * 1.15f, center = c)
                drawArc(Color.White.copy(alpha = 0.1f), 135f, 270f, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fill > 0f) drawArc(color, 135f, 270f * fill, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
                val zw = r * 0.95f
                drawZenithZ(c.x - zw / 2f, c.y - zw * ZenithZAspect / 2f, zw, light = 0.3f + 0.7f * fill)
            }
            Text(
                if (s == null) "Schutz …" else "Schutz $s",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}
