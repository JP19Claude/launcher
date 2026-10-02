package dev.hearth.launcher.ui

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.SystemControls
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.hearth.launcher.data.HearthWidget
import dev.hearth.launcher.data.WidgetRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One of Hearth's own widgets, by what it is. They sit on liquid glass already. */
@Composable
fun InternalWidget(repo: WidgetRepository, id: Int, modifier: Modifier = Modifier) {
    when (remember(id) { repo.kindOf(id) }) {
        HearthWidget.Photos -> PhotoWidget(repo, id, modifier)
        HearthWidget.Clock -> GlassAnalogClock(modifier)
        HearthWidget.Battery -> BatteryWidget(modifier)
        HearthWidget.Date -> DateWidget(modifier)
        HearthWidget.Toggles -> TogglesWidget(modifier)
        HearthWidget.Note -> NoteWidget(id, modifier)
        HearthWidget.ClawdPicture -> ClawdPictureWidget(id, modifier)
        HearthWidget.ClawdWorld -> ClawdWorldWidget(modifier)
        HearthWidget.ClawdClock -> ClawdClockWidget(modifier)
        HearthWidget.ClawdAsk -> ClawdAskWidget(modifier)
        HearthWidget.ClawdBattery -> ClawdBatteryWidget(modifier)
        HearthWidget.ClawdJoke -> ClawdJokeWidget(id, modifier)
        HearthWidget.ClawdSticker -> ClawdStickerWidget(modifier)
        HearthWidget.ClawdPet -> ClawdPetWidget(modifier)
        HearthWidget.ClawdOracle -> ClawdOracleWidget(modifier)
        HearthWidget.ClawdFocus -> ClawdFocusWidget(modifier)
    }
}

/** The time, ticking each second while the screen shows it (not at all with animations off). */
@Composable
internal fun rememberTime(everySecond: Boolean): LocalDateTime {
    val time by produceState(LocalDateTime.now(), everySecond) {
        while (true) {
            value = LocalDateTime.now()
            delay(if (everySecond) 1000L - System.currentTimeMillis() % 1000 else 60_000L - System.currentTimeMillis() % 60_000)
        }
    }
    return time
}

/** An analog clock as if cut from glass: soft ticks, white hands, the second hand in the accent. */
@Composable
private fun GlassAnalogClock(modifier: Modifier) {
    val settings = LocalSettings.current
    val now = rememberTime(everySecond = settings.animations)
    val accent = settings.accent.color
    // Easter egg: three taps send the hands spinning round.
    val context = LocalContext.current
    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val taps = remember { longArrayOf(0L, 0L) }
    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures {
                    val t = System.currentTimeMillis()
                    taps[0] = if (t - taps[1] < 700) taps[0] + 1 else 1
                    taps[1] = t
                    if (taps[0] >= 3) {
                        taps[0] = 0
                        scope.launch {
                            spin.snapTo(0f)
                            spin.animateTo(720f, androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            spin.snapTo(0f)
                        }
                        dev.hearth.launcher.data.EasterEggs.find(context, "spin")
                    }
                }
            }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f
            val c = center
            // The dial: a faint ring and twelve ticks, the quarters longer.
            drawCircle(Color.White.copy(alpha = 0.10f), r, c)
            drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(r * 0.02f))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90)
                val long = i % 3 == 0
                val outer = r * 0.9f
                val inner = r * if (long) 0.74f else 0.8f
                drawLine(
                    Color.White.copy(alpha = if (long) 0.85f else 0.45f),
                    Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                    Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                    strokeWidth = r * if (long) 0.045f else 0.025f,
                    cap = StrokeCap.Round,
                )
            }
            val minutes = now.minute + now.second / 60f
            val hours = (now.hour % 12) + minutes / 60f
            val extra = spin.value
            rotate(hours * 30f + extra, c) {
                drawLine(Color.White, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
            }
            rotate(minutes * 6f + extra * 2f, c) {
                drawLine(Color.White, c, Offset(c.x, c.y - r * 0.76f), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
            }
            if (settings.animations) {
                rotate(now.second * 6f, c) {
                    drawLine(accent, Offset(c.x, c.y + r * 0.14f), Offset(c.x, c.y - r * 0.82f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
                }
            }
            drawCircle(accent, r * 0.05f, c)
        }
    }
}

/** Battery level, and whether it's charging, kept up to date while the widget is shown. */
@Composable
internal fun rememberBatteryLevel(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readBattery(context, null)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state = readBattery(c, intent)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun readBattery(context: Context, intent: Intent?): Pair<Int, Boolean> = runCatching {
    val i = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
    val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    (if (level < 0) -1 else level * 100 / scale) to plugged
}.getOrDefault(-1 to false)

/** The battery as a ring: green while charging, red when low, the percent in the middle. */
@Composable
private fun BatteryWidget(modifier: Modifier) {
    val (level, charging) = rememberBatteryLevel()
    val color = when {
        charging -> Color(0xFF34C759)
        level in 0..20 -> Color(0xFFFF453A)
        else -> Color.White
    }
    BoxWithConstraints(modifier.padding(12.dp), contentAlignment = Alignment.Center) {
        val ring = min(maxWidth.value, maxHeight.value).dp
        Canvas(Modifier.fillMaxSize()) {
            val stroke = min(size.width, size.height) * 0.08f
            val d = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = androidx.compose.ui.geometry.Size(d, d)
            drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (level > 0) {
                drawArc(color, -90f, 360f * level / 100f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (level >= 0) "$level %" else "–",
                color = Color.White,
                fontSize = (ring.value * 0.2f).coerceIn(14f, 34f).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (charging) "Lädt" else "Akku",
                color = color.copy(alpha = if (charging) 1f else 0.7f),
                fontSize = (ring.value * 0.09f).coerceIn(10f, 15f).sp,
            )
        }
    }
}

/** One UI's date widget on glass: the weekday, the day big, the month, and the next alarm. */
@Composable
private fun DateWidget(modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberTime(everySecond = false)
    val locale = Locale.getDefault()
    val accent = LocalSettings.current.accent.color
    val alarm = remember(now.hour, now.minute) {
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let { t ->
                java.text.SimpleDateFormat("EEE HH:mm", locale).format(java.util.Date(t))
            }
        }.getOrNull()
    }
    BoxWithConstraints(modifier.padding(14.dp), contentAlignment = Alignment.Center) {
        val big = (min(maxWidth.value, maxHeight.value) * 0.42f).coerceIn(28f, 72f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                color = accent,
                fontSize = (big * 0.26f).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                now.dayOfMonth.toString(),
                color = Color.White,
                fontSize = big.sp,
                fontWeight = FontWeight.Light,
                lineHeight = (big * 1.05f).sp,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = (big * 0.22f).sp,
            )
            if (alarm != null) {
                Text(
                    "⏰ $alarm",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = (big * 0.2f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Four switches on glass: flashlight, do not disturb, vibration, auto-rotate. */
@Composable
private fun TogglesWidget(modifier: Modifier) {
    val context = LocalContext.current
    val controls = remember { SystemControls(context.applicationContext) }
    DisposableEffect(controls) { onDispose { controls.close() } }
    val torch by controls.torchOn.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(runCatching { controls.state() }.getOrNull()) }
    fun refresh() {
        state = runCatching { controls.state() }.getOrNull()
    }
    // The switches can change elsewhere too: read again every few seconds while shown.
    LaunchedEffect(controls) {
        while (true) {
            delay(4000)
            refresh()
        }
    }
    val accent = LocalSettings.current.accent.color
    Row(
        modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(Glyph.Torch, "Licht", torch, accent, enabled = controls.hasTorch) { controls.setTorch(!torch) }
        ToggleButton(Glyph.Moon, "Nicht stören", state?.doNotDisturb == true, accent) {
            controls.toggleDoNotDisturb()
            refresh()
        }
        ToggleButton(Glyph.Vibrate, "Vibration", state?.vibrate == true, accent) {
            controls.toggleVibrate()
            refresh()
        }
        ToggleButton(Glyph.Rotate, "Drehen", state?.autoRotate == true, accent) {
            controls.setAutoRotate(state?.autoRotate != true)
            refresh()
        }
    }
}

@Composable
private fun ToggleButton(glyph: Glyph, label: String, on: Boolean, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (on) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, if (on) Color.White else Color.White.copy(alpha = if (enabled) 0.85f else 0.35f), Modifier.size(22.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A glass note: tap and write; it keeps itself (on this phone only). */
@Composable
private fun NoteWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hearth_notes", Context.MODE_PRIVATE) }
    var text by remember(id) { mutableStateOf(prefs.getString("note_$id", "").orEmpty()) }
    // Saved shortly after typing stops.
    LaunchedEffect(text) {
        delay(400)
        prefs.edit().putString("note_$id", text).apply()
    }
    Box(modifier.padding(14.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(2000) },
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(LocalSettings.current.accent.color),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Notiz …", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                inner()
            },
        )
    }
}
