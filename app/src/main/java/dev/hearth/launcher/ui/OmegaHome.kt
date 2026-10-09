package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.LauncherSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * OMEGA UI 17's home: new clocks for the top of the home screen and new clock widgets.
 */

private fun dateLine(now: LocalDateTime): String {
    val locale = Locale.getDefault()
    return now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
}

/** The accent as a ruby-like gradient: light at the top, the accent, deeper below. */
private fun accentFace(accent: Color): Brush = Brush.verticalGradient(listOf(Color.White, accent, accent.copy(red = accent.red * 0.7f, green = accent.green * 0.7f, blue = accent.blue * 0.7f)))

// ---------------------------------------------------------------------------------------------
// Home clocks
// ---------------------------------------------------------------------------------------------

/** "OMEGA": thin, tall numerals in the accent's colors, the seconds as a line under them. */
@Composable
internal fun OmegaHomeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    // The time itself changes once a minute; only the little seconds line ticks every second
    // (in its own small part, so the clock above isn't redrawn each second).
    val now = rememberTime(everySecond = false)
    val accent = settings.accent.color
    Column(modifier) {
        Text(
            omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm")), LocalSettings.current.omegaZeros),
            style = TextStyle(brush = accentFace(accent), fontSize = 86.sp, fontWeight = FontWeight.Thin, lineHeight = 90.sp, shadow = OnWallpaperText.shadow),
        )
        SecondsLine(accent, settings.animations, Modifier.width(220.dp).height(6.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZenithMark(accent, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(dateLine(now), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, style = OnWallpaperText)
        }
    }
}

/** The seconds as a line filling up – the only part that ticks every second. */
@Composable
private fun SecondsLine(accent: Color, ticking: Boolean, modifier: Modifier) {
    val now = rememberTime(everySecond = ticking)
    Canvas(modifier) {
        val p = now.second / 60f
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = size.height, cap = StrokeCap.Round)
        drawLine(accent, Offset(0f, size.height / 2f), Offset(size.width * p.coerceAtLeast(0.02f), size.height / 2f), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

/** An analog clock of glass on the home screen, the date beside it. */
@Composable
internal fun AnalogHomeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now = rememberTime(everySecond = settings.animations)
    val accent = settings.accent.color
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(132.dp)) { drawDial(now, accent, omega = true) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                style = OnWallpaperText,
            )
            Text(now.dayOfMonth.toString(), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 54.sp, style = OnWallpaperText)
            Text(now.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, style = OnWallpaperText)
        }
    }
}

private val HourWords = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf")

/** The time as one would say it: "Viertel nach drei", "fünf vor halb acht". */
internal fun timeInWords(hour24: Int, minute: Int): String {
    val rounded = ((minute + 2) / 5) * 5
    val hour = hour24 % 12
    val next = (hour + 1) % 12
    fun word(i: Int) = HourWords[i % 12]
    fun full(i: Int) = (if (i % 12 == 1) "ein" else word(i)) + " Uhr"
    return when (rounded) {
        0 -> full(hour)
        5 -> "fünf nach ${word(hour)}"
        10 -> "zehn nach ${word(hour)}"
        15 -> "Viertel nach ${word(hour)}"
        20 -> "zwanzig nach ${word(hour)}"
        25 -> "fünf vor halb ${word(next)}"
        30 -> "halb ${word(next)}"
        35 -> "fünf nach halb ${word(next)}"
        40 -> "zwanzig vor ${word(next)}"
        45 -> "Viertel vor ${word(next)}"
        50 -> "zehn vor ${word(next)}"
        55 -> "fünf vor ${word(next)}"
        else -> full(next)
    }
}

/** "In Worten": the time as a sentence, big. */
@Composable
internal fun WordsHomeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now = rememberTime(everySecond = false)
    val accent = settings.accent.color
    val words = remember(now.hour, now.minute) { timeInWords(now.hour, now.minute) }
    Column(modifier) {
        Text("Es ist", color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp, style = OnWallpaperText)
        Text(
            words.replaceFirstChar { it.uppercase() },
            style = TextStyle(brush = accentFace(accent), fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp, shadow = OnWallpaperText.shadow),
        )
        Spacer(Modifier.height(6.dp))
        Text(dateLine(now), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, style = OnWallpaperText)
    }
}

/** "Geteilt": the hours big on the left, minutes in the accent beside them over the date. */
@Composable
internal fun SplitHomeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now = rememberTime(everySecond = false)
    val accent = settings.accent.color
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            omegaDigits(now.format(DateTimeFormatter.ofPattern("HH")), LocalSettings.current.omegaZeros),
            color = Color.White,
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 96.sp,
            style = OnWallpaperText,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                omegaDigits(now.format(DateTimeFormatter.ofPattern("mm")), LocalSettings.current.omegaZeros),
                style = TextStyle(brush = accentFace(accent), fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp, shadow = OnWallpaperText.shadow),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.getDefault())),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                style = OnWallpaperText,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Clock widgets
// ---------------------------------------------------------------------------------------------

/** A glass dial: ticks, hands, the seconds in the accent; [omega] puts the Ω at twelve. */
private fun DrawScope.drawDial(now: LocalDateTime, accent: Color, omega: Boolean) {
    val r = min(size.width, size.height) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)), center = c, radius = r), r, c)
    drawCircle(Brush.sweepGradient(listOf(accent, Color.White.copy(alpha = 0.5f), accent), center = c), r, c, style = Stroke(r * 0.03f))
    for (i in 0 until 60) {
        val a = Math.toRadians(i * 6.0 - 90)
        val hour = i % 5 == 0
        if (omega && i == 0) continue
        val outer = r * 0.92f
        val inner = r * if (hour) 0.78f else 0.86f
        drawLine(
            Color.White.copy(alpha = if (hour) 0.85f else 0.3f),
            Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
            Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
            strokeWidth = r * if (hour) 0.035f else 0.015f,
            cap = StrokeCap.Round,
        )
    }
    if (omega) {
        // A small ruby at twelve.
        val top = Offset(c.x, c.y - r * 0.82f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A98), Color(0xFFB0102C)), center = top, radius = r * 0.07f), r * 0.07f, top)
    }
    fun hand(turns: Double, length: Float, width: Float, color: Color) {
        val a = Math.toRadians(turns * 360.0 - 90)
        drawLine(
            color,
            Offset(c.x - cos(a).toFloat() * r * 0.1f, c.y - sin(a).toFloat() * r * 0.1f),
            Offset(c.x + cos(a).toFloat() * r * length, c.y + sin(a).toFloat() * r * length),
            strokeWidth = r * width,
            cap = StrokeCap.Round,
        )
    }
    val seconds = now.second.toDouble()
    val minutes = now.minute + seconds / 60.0
    val hours = (now.hour % 12) + minutes / 60.0
    hand(hours / 12.0, 0.5f, 0.07f, Color.White)
    hand(minutes / 60.0, 0.74f, 0.05f, Color.White.copy(alpha = 0.95f))
    hand(seconds / 60.0, 0.82f, 0.02f, accent)
    drawCircle(accent, r * 0.05f, c)
}

/** OMEGA clock widget: big thin time in ruby light, the date and the seconds as an arc. */
@Composable
internal fun OmegaClockWidget(modifier: Modifier) {
    val settings = LocalSettings.current
    val now = rememberTime(everySecond = false)
    val accent = settings.accent.color
    BoxWithConstraints(modifier.padding(16.dp), contentAlignment = Alignment.CenterStart) {
        val big = (min(maxWidth.value * 0.28f, maxHeight.value * 0.55f)).coerceIn(30f, 80f)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZenithMark(accent, (big * 0.36f).dp)
                Spacer(Modifier.width(6.dp))
                Text(dateLine(now), color = Color.White.copy(alpha = 0.8f), fontSize = (big * 0.22f).sp)
            }
            Text(
                omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm")), LocalSettings.current.omegaZeros),
                style = TextStyle(brush = accentFace(accent), fontSize = big.sp, fontWeight = FontWeight.Thin, lineHeight = (big * 1.05f).sp),
            )
            SecondsLine(accent, settings.animations, Modifier.fillMaxWidth().height(5.dp))
        }
    }
}

/** Ruby analog widget: the glass dial with a ruby at twelve. */
@Composable
internal fun RubyAnalogWidget(modifier: Modifier) {
    val settings = LocalSettings.current
    val now = rememberTime(everySecond = settings.animations)
    val accent = settings.accent.color
    Box(modifier.padding(12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawDial(now, accent, omega = true) }
    }
}

private class City(val name: String, val zone: String)

private val Cities = listOf(City("New York", "America/New_York"), City("London", "Europe/London"), City("Tokio", "Asia/Tokyo"))

/** World clock: here and three cities, with how many days ahead or behind they are. */
@Composable
internal fun WorldClockWidget(modifier: Modifier) {
    val settings = LocalSettings.current
    val now = rememberTime(everySecond = false)
    val accent = settings.accent.color
    val here = remember(now.hour, now.minute) { ZonedDateTime.now() }
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorldCell("Hier", here, here, accent, Modifier.weight(1f))
        Cities.forEach { city ->
            val there = remember(now.hour, now.minute, city.zone) {
                runCatching { ZonedDateTime.now(ZoneId.of(city.zone)) }.getOrDefault(here)
            }
            WorldCell(city.name, there, here, accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorldCell(name: String, time: ZonedDateTime, here: ZonedDateTime, accent: Color, modifier: Modifier) {
    val days = time.toLocalDate().toEpochDay() - here.toLocalDate().toEpochDay()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Day or night there.
        Canvas(Modifier.size(22.dp)) {
            val day = time.hour in 7..18
            val col = if (day) Color(0xFFFFD27A) else Color(0xFF9FB4FF)
            drawCircle(col, size.minDimension / 2.6f, Offset(size.width / 2f, size.height / 2f))
            if (!day) drawCircle(Color(0xFF1A1A24), size.minDimension / 2.8f, Offset(size.width * 0.66f, size.height * 0.38f))
        }
        Spacer(Modifier.height(4.dp))
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(name, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            when {
                days > 0L -> "Morgen"
                days < 0L -> "Gestern"
                else -> "Heute"
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}
