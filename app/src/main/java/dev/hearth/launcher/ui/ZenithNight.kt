package dev.hearth.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.NightstandActivity
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val NightClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val NightDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

private class NightBattery(val level: Int, val charging: Boolean)

/** "06:30", "morgen 06:30" or "Mo. 06:30": when the next alarm rings (null: none is set). */
internal fun nextAlarmText(context: Context): String? {
    val at = runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull() ?: return null
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (time.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "morgen "
        else -> time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN) + " "
    }
    return day + time.format(NightClock)
}

/**
 * ZENITH 20, the polarlicht widget: the aurora as a living picture with the time and the date.
 * Tapped, the Z-Nacht opens.
 */
@Composable
internal fun ZenithAuroraWidget(modifier: Modifier) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val now = rememberTime(everySecond = false)
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF03050A))
            .clickable {
                runCatching { context.startActivity(Intent(context, NightstandActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
    ) {
        ZenithAurora(settings.auroraLook, Modifier.matchParentSize(), strength = 1f)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
            Text("Z-NACHT", color = HudGreen, fontSize = 10.sp, fontFamily = HudFont, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text(now.format(NightClock), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraLight)
            Text(now.format(NightDate), color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        }
    }
}

/**
 * ZENITH 20, the Z-Nacht: the phone on the nightstand. A big thin clock under the aurora, the
 * date, the weather, the next alarm and the battery – dim, so it doesn't light the room, and
 * drifting a little each minute so no pixel burns in. A tap makes it brighter and dim again; the
 * screen stays on while it's open. Seven taps on the moon are a find.
 */
@Composable
fun NightstandScreen(onClose: () -> Unit, onBrightness: (Boolean) -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val report = rememberWeather()
    val now = rememberTime(everySecond = false)
    var dim by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf(true) }
    val moonTaps = remember { longArrayOf(0L, 0L) }
    LaunchedEffect(dim) { onBrightness(dim) }
    LaunchedEffect(Unit) {
        delay(7000)
        hint = false
    }
    BackHandler { onClose() }

    val minute = now.hour * 60 + now.minute
    val driftX by animateDpAsState((sin(minute * 0.9) * 22).dp, tween(20_000), label = "driftX")
    val driftY by animateDpAsState((cos(minute * 1.3) * 16).dp, tween(20_000), label = "driftY")
    val battery by produceState(NightBattery(-1, false), minute) {
        val intent = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        value = NightBattery(if (level < 0) -1 else level * 100 / scale, plugged)
    }
    val alarm = remember(minute) { nextAlarmText(context) }
    // ZENITH 20: the night's eggs – at three in the morning, twenty minutes of watch, twenty percent.
    LaunchedEffect(Unit) {
        if (LocalTime.now().hour == 3) EasterEggs.find(context, "nightowl")
        delay(20 * 60_000L)
        EasterEggs.find(context, "nightwatch")
    }
    LaunchedEffect(battery.level) { if (battery.level == 20) EasterEggs.find(context, "battery20") }
    val ink = Color.White.copy(alpha = if (dim) 0.55f else 0.95f)
    val soft = Color.White.copy(alpha = if (dim) 0.35f else 0.7f)
    val hintAlpha by animateFloatAsState(if (hint) 0.6f else 0f, tween(800), label = "hint")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { dim = !dim } },
    ) {
        ZenithAurora(settings.auroraLook, Modifier.fillMaxSize(), strength = if (dim) 0.3f else 0.85f)
        Column(
            Modifier.align(Alignment.Center).offset(driftX, driftY),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer { alpha = if (dim) 0.55f else 1f }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        val at = System.currentTimeMillis()
                        moonTaps[0] = if (at - moonTaps[1] < 700) moonTaps[0] + 1 else 1
                        moonTaps[1] = at
                        if (moonTaps[0] >= 7) {
                            moonTaps[0] = 0
                            EasterEggs.find(context, "nightmoon")
                            Toast.makeText(context, "Der Mond sagt gute Nacht. 🌙", Toast.LENGTH_SHORT).show()
                        }
                    },
            ) {
                WeatherGlyph(0, night = true, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(10.dp))
            Text(now.format(NightClock), color = ink, fontSize = 108.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 2.sp)
            Text(now.format(NightDate), color = soft, fontSize = 17.sp)
            val r = report
            if (r != null) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(r.code, r.night, Modifier.size(30.dp), animate = false)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${r.now.roundToInt()}° · ${r.sky} · H ${r.max}° T ${r.min}°",
                        color = soft,
                        fontSize = 15.sp,
                    )
                }
            }
            if (alarm != null) {
                Spacer(Modifier.height(8.dp))
                Text("⏰ $alarm", color = soft, fontSize = 15.sp)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val b = battery
            if (b.level >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        "${b.level} %" + if (b.charging) " ▲" else "",
                        color = if (b.level <= 15 && !b.charging) Color(0xFFFF8A80).copy(alpha = 0.8f) else soft,
                        fontSize = 14.sp,
                        fontFamily = HudFont,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .width(110.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(b.level / 100f)
                                .height(3.dp)
                                .background(if (b.charging) ZenithGreen else soft),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tippen: heller und dunkler · Zurück: beenden",
                color = Color.White.copy(alpha = hintAlpha),
                fontSize = 12.sp,
            )
        }
    }
}
