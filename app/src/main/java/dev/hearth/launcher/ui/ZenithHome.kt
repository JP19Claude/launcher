package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.LauncherSettings
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * ZENITH 19's home: the sun's arc as the clock.
 */

/** ZENITH's day, the same everywhere (no location needed): sunrise at 6, sunset at 8 pm. */
private const val SunRise = 6f
private const val SunSet = 20f
private const val Noon = (SunRise + SunSet) / 2f

/** ZENITH's clear glass: white light with a glint of sun and green. */
val ZenithLiquidColors = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFFFF4D2),
    Color(0xFFDDF8E8),
    Color(0xFFDDEEFF),
    Color(0xFFFFFBEF),
)

private val ZenithDawnColors = listOf(ZenithSun, Color(0xFFFF9A5A), ZenithGreen, Color(0xFFFFD6B0), Color(0xFFFFF6D6))
private val ZenithDuskColors = listOf(Color(0xFFFF8A4C), ZenithSun, Color(0xFFFF6F91), ZenithGreen, Color(0xFFFFE0B8))
private val ZenithNightColors = listOf(ZenithSky, Color(0xFF7F8CFF), ZenithGreen, Color(0xFFB8F5C8), Color(0xFFDDEEFF))

/**
 * "Farben nach Sonnenstand": Zenith's colors for the hour – gold and peach in the morning, green
 * and sky by day, glow at dusk, moon blue at night (fixed lists, so nothing is rebuilt).
 */
internal fun zenithDaylightColors(hour: Int = java.time.LocalTime.now().hour): List<Color> = when (hour) {
    in 5..8 -> ZenithDawnColors
    in 9..16 -> ZenithFluidColors
    in 17..20 -> ZenithDuskColors
    else -> ZenithNightColors
}

/** "2 Std. 10 Min.", "45 Min." */
private fun span(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m Min."
        m == 0 -> "$h Std."
        else -> "$h Std. $m Min."
    }
}

/** Where the sun (by day) or the moon (by night) stands on its arc, 0 (rising) to 1 (setting). */
internal fun zenithAlong(hour: Float): Pair<Boolean, Float> {
    val day = hour in SunRise..SunSet
    return if (day) {
        true to (hour - SunRise) / (SunSet - SunRise)
    } else {
        val night = 24f - (SunSet - SunRise)
        false to ((if (hour > SunSet) hour - SunSet else hour + 24f - SunSet) / night).coerceIn(0f, 1f)
    }
}

/** What the sun is doing, in a few words: "Zenit in 2 Std.", "Sonne im Zenit", "Nadir". */
internal fun zenithWords(hour: Int, minute: Int): String {
    val now = hour * 60 + minute
    val noon = (Noon * 60).toInt()
    val rise = (SunRise * 60).toInt()
    val set = (SunSet * 60).toInt()
    return when {
        abs(now - noon) < 30 -> "Sonne im Zenit"
        now in rise until noon -> "Zenit in ${span(noon - now)}"
        now in noon..set -> "Sonnenuntergang in ${span(set - now)}"
        hour in 0..1 -> "Nadir – tiefste Nacht"
        now > set -> "Sonnenaufgang in ${span(24 * 60 - now + rise)}"
        else -> "Sonnenaufgang in ${span(rise - now)}"
    }
}

/**
 * "Sonnenbogen", ZENITH 19's home clock: the sun's arc over the time. By day the sun stands
 * where it is in the sky, the way it has come lit in gold and green; at its highest point it is
 * "im Zenit". By night the moon goes the same way, in blue.
 */
@Composable
internal fun ZenithHomeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now = rememberTime(everySecond = false)
    val (day, along) = zenithAlong(now.hour + now.minute / 60f)
    val words = remember(now.hour, now.minute) { zenithWords(now.hour, now.minute) }
    val locale = Locale.getDefault()
    val date = remember(now.dayOfYear) {
        now.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val time = omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm")), settings.omegaZeros)
    Column(modifier) {
        Box(Modifier.width(300.dp).height(150.dp)) {
            ZenithArc(day, along, Modifier.fillMaxSize())
            val numerals = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            if (settings.glassClocks) {
                GlassNumerals(time, fontSize = 62.sp, modifier = numerals, fontWeight = FontWeight.Light, lineHeight = 64.sp)
            } else {
                Text(
                    time,
                    modifier = numerals,
                    style = TextStyle(
                        brush = Brush.verticalGradient(listOf(Color.White, Color.White, lerp(Color.White, if (day) ZenithGreen else ZenithSky, 0.45f))),
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 64.sp,
                        shadow = OnWallpaperText.shadow,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (day) "☀" else "☾", color = if (day) ZenithSun else ZenithSky, fontSize = 16.sp, style = OnWallpaperText)
            Spacer(Modifier.width(8.dp))
            Text("$date · $words", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, style = OnWallpaperText)
        }
    }
}

/**
 * The sun's arc: the whole way dotted, the way come so far lit (gold and green by day, blue by
 * night), the zenith marked at the top – and the sun, or the moon, where it stands now.
 */
@Composable
internal fun ZenithArc(day: Boolean, along: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = h * 0.94f
        val rx = w * 0.47f
        val ry = h * 0.82f
        val cx = w / 2f
        val box = Offset(cx - rx, base - ry)
        val oval = Size(rx * 2f, ry * 2f)
        fun at(f: Float): Offset {
            val a = PI * (1f - f)
            return Offset(cx + cos(a).toFloat() * rx, base - sin(a).toFloat() * ry)
        }
        // The whole way, dotted.
        drawArc(
            Color.White.copy(alpha = 0.4f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = box,
            size = oval,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 8.dp.toPx()))),
        )
        // The way come so far: gold to green by day, blue by night.
        drawArc(
            Brush.horizontalGradient(
                if (day) listOf(ZenithSun, ZenithGreen, ZenithSun) else listOf(ZenithSky, Color(0xFF9AA8FF), ZenithSky),
                startX = cx - rx,
                endX = cx + rx,
            ),
            startAngle = 180f,
            sweepAngle = 180f * along.coerceAtLeast(0.005f),
            useCenter = false,
            topLeft = box,
            size = oval,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
        // The horizon, and the zenith marked at the top.
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)),
            Offset(0f, base),
            Offset(w, base),
            strokeWidth = 1.dp.toPx(),
        )
        val top = at(0.5f)
        drawLine(ZenithGreen, top - Offset(0f, 7.dp.toPx()), top + Offset(0f, 7.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val p = at(along)
        if (day) {
            // The sun: a glow, a white-hot core.
            val glow = 30.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.65f), ZenithSun.copy(alpha = 0.12f), Color.Transparent), center = p, radius = glow), glow, p)
            drawCircle(Brush.radialGradient(listOf(Color.White, ZenithSun), center = p, radius = 9.dp.toPx()), 8.dp.toPx(), p)
        } else {
            // The moon: a pale crescent with a cool glow.
            val r = 8.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(ZenithSky.copy(alpha = 0.35f), Color.Transparent), center = p, radius = r * 3f), r * 3f, p)
            val disc = Path().apply { addOval(Rect(p, r)) }
            val bite = Path().apply { addOval(Rect(p + Offset(r * 0.5f, -r * 0.35f), r * 0.85f)) }
            val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
            drawPath(crescent, Color(0xFFEAF2FF))
        }
    }
}
