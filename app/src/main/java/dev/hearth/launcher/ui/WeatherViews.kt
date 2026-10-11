package dev.hearth.launcher.ui

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.ZentraleActivity
import dev.hearth.launcher.data.WeatherDay
import dev.hearth.launcher.data.WeatherHour
import dev.hearth.launcher.data.WeatherReport
import dev.hearth.launcher.data.ZenithWeather
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The kinds of sky the weather glyph can draw. */
internal enum class SkyKind { Clear, Partly, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

internal fun skyKindOf(code: Int): SkyKind = when (code) {
    0, 1 -> SkyKind.Clear
    2 -> SkyKind.Partly
    3 -> SkyKind.Cloudy
    45, 48 -> SkyKind.Fog
    in 51..57 -> SkyKind.Drizzle
    in 61..67, in 80..82 -> SkyKind.Rain
    in 71..77, 85, 86 -> SkyKind.Snow
    in 95..99 -> SkyKind.Storm
    else -> SkyKind.Cloudy
}

private val RainBlue = Color(0xFF6EC8FF)

/** The weather where the owner lives, kept fresh while the screen shows it (null until it's there). */
@Composable
internal fun rememberWeather(): WeatherReport? {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ZenithWeather.init(context)
        while (true) {
            ZenithWeather.refresh()
            delay(20 * 60_000L)
        }
    }
    val report by ZenithWeather.report.collectAsStateWithLifecycle()
    return report
}

private fun DrawScope.drawSun(c: Offset, r: Float, p: Float) {
    drawCircle(
        Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r * 2.1f),
        radius = r * 2.1f,
        center = c,
    )
    val turn = p * 60.0
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0 + turn)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            ZenithSun.copy(alpha = 0.85f),
            Offset(c.x + dx * r * 1.3f, c.y + dy * r * 1.3f),
            Offset(c.x + dx * r * 1.75f, c.y + dy * r * 1.75f),
            strokeWidth = r * 0.16f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF1A8), ZenithSun, Color(0xFFFFA726)),
            center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = c,
    )
}

private fun DrawScope.drawMoon(c: Offset, r: Float, p: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFBFD4FF).copy(alpha = 0.28f), Color.Transparent), center = c, radius = r * 2f),
        radius = r * 2f,
        center = c,
    )
    val hole = Path().apply { addOval(Rect(Offset(c.x + r * 0.5f, c.y - r * 0.28f), r * 0.92f)) }
    clipPath(hole, clipOp = ClipOp.Difference) {
        drawCircle(
            Brush.linearGradient(
                listOf(Color(0xFFFFFFF0), Color(0xFFD6D2B8)),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r),
            ),
            radius = r,
            center = c,
        )
    }
    for (k in 0 until 3) {
        val twinkle = 0.4f + 0.6f * kotlin.math.abs(sin((p + k * 0.33f) * 2.0 * PI)).toFloat()
        drawCircle(
            Color.White.copy(alpha = 0.7f * twinkle),
            r * 0.07f,
            Offset(c.x + r * (1.1f + k * 0.35f), c.y - r * (0.9f - k * 0.55f)),
        )
    }
}

/** A cloud whose bounding box starts at ([x], [y]) and is [w] wide. */
private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, dark: Boolean) {
    val top = if (dark) Color(0xFFB4BFCB) else Color(0xFFFFFFFF)
    val bottom = if (dark) Color(0xFF6E7B89) else Color(0xFFD5DEE8)
    val brush = Brush.verticalGradient(listOf(top, bottom), startY = y, endY = y + w * 0.6f)
    drawCircle(brush, w * 0.22f, Offset(x + w * 0.3f, y + w * 0.38f))
    drawCircle(brush, w * 0.28f, Offset(x + w * 0.55f, y + w * 0.3f))
    drawCircle(brush, w * 0.2f, Offset(x + w * 0.78f, y + w * 0.42f))
    drawRoundRect(brush, Offset(x + w * 0.1f, y + w * 0.38f), Size(w * 0.8f, w * 0.22f), CornerRadius(w * 0.11f))
}

/**
 * ZENITH 20, the weather as a picture: a sun or moon, clouds, rain, snow, fog or a storm, drawn in
 * the shapes of the glass widgets and moving gently (still, with [animate] off or animations off).
 */
@Composable
internal fun WeatherGlyph(code: Int, night: Boolean, modifier: Modifier = Modifier, animate: Boolean = true) {
    val kind = skyKindOf(code)
    val moving = animate && LocalSettings.current.animations
    val loop = rememberInfiniteTransition(label = "glyph")
    val phase = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val p = if (moving) phase.value else 0.25f
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        val drift = sin(p * 2.0 * PI).toFloat() * s * 0.02f
        // Everything is laid out in a square of side s, centered in the canvas.
        translate(ox, oy) {
            when (kind) {
                SkyKind.Clear -> if (night) drawMoon(Offset(s * 0.5f, s * 0.5f), s * 0.26f, p) else drawSun(Offset(s * 0.5f, s * 0.5f), s * 0.2f, p)
                SkyKind.Partly -> {
                    if (night) drawMoon(Offset(s * 0.38f, s * 0.36f), s * 0.2f, p) else drawSun(Offset(s * 0.38f, s * 0.36f), s * 0.15f, p)
                    drawCloud(s * 0.2f + drift, s * 0.36f, s * 0.7f, dark = false)
                }
                SkyKind.Cloudy -> {
                    drawCloud(s * 0.34f - drift, s * 0.14f, s * 0.5f, dark = true)
                    drawCloud(s * 0.1f + drift, s * 0.3f, s * 0.8f, dark = false)
                }
                SkyKind.Fog -> {
                    drawCloud(s * 0.12f + drift, s * 0.14f, s * 0.76f, dark = false)
                    for (i in 0 until 3) {
                        val y = s * (0.66f + i * 0.11f)
                        val shift = sin((p + i * 0.3f) * 2.0 * PI).toFloat() * s * 0.05f
                        drawLine(
                            Color.White.copy(alpha = 0.65f),
                            Offset(s * 0.2f + shift, y),
                            Offset(s * 0.8f + shift, y),
                            strokeWidth = s * 0.045f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                SkyKind.Drizzle, SkyKind.Rain -> {
                    drawCloud(s * 0.1f + drift, s * 0.1f, s * 0.8f, dark = true)
                    val drops = if (kind == SkyKind.Rain) 5 else 3
                    for (i in 0 until drops) {
                        val x = s * (0.24f + i * (if (drops == 5) 0.13f else 0.2f))
                        val y = s * (0.6f + ((p + i * 0.21f) % 1f) * 0.3f)
                        drawLine(RainBlue, Offset(x, y), Offset(x - s * 0.035f, y + s * 0.1f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
                    }
                }
                SkyKind.Snow -> {
                    drawCloud(s * 0.1f + drift, s * 0.1f, s * 0.8f, dark = true)
                    for (i in 0 until 5) {
                        val fall = (p + i * 0.19f) % 1f
                        val x = s * (0.24f + i * 0.13f) + sin((fall + i) * 2.0 * PI).toFloat() * s * 0.03f
                        drawCircle(Color.White, s * 0.032f, Offset(x, s * (0.62f + fall * 0.3f)))
                    }
                }
                SkyKind.Storm -> {
                    drawCloud(s * 0.1f + drift, s * 0.08f, s * 0.8f, dark = true)
                    val flash = if ((p * 2f) % 1f < 0.18f) 1f else 0.7f
                    val bolt = Path().apply {
                        moveTo(s * 0.54f, s * 0.52f)
                        lineTo(s * 0.42f, s * 0.74f)
                        lineTo(s * 0.51f, s * 0.74f)
                        lineTo(s * 0.45f, s * 0.95f)
                        lineTo(s * 0.64f, s * 0.67f)
                        lineTo(s * 0.54f, s * 0.67f)
                        lineTo(s * 0.62f, s * 0.52f)
                        close()
                    }
                    drawPath(bolt, ZenithSun.copy(alpha = flash))
                }
            }
        }
    }
}

/** "klar" → "Klar". */
internal fun skyTitle(text: String): String = text.replaceFirstChar { it.uppercase() }

/** A row of the coming hours: the time, a glyph and the temperature (rain chance under it when it matters). */
@Composable
internal fun WeatherHourStrip(
    hours: List<WeatherHour>,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
) {
    Row(modifier, horizontalArrangement = arrangement) {
        hours.forEachIndexed { i, h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (i == 0) "JETZT" else "%02d".format(h.hour),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = HudFont,
                )
                WeatherGlyph(h.code, night = h.hour < 6 || h.hour >= 21, modifier = Modifier.size(28.dp), animate = false)
                Text("${h.temp}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (h.rain >= 30) "${h.rain} %" else " ",
                    color = RainBlue,
                    fontSize = 9.sp,
                    fontFamily = HudFont,
                )
            }
        }
    }
}

/** The days ahead: name, glyph, rain chance and a bar from the day's low to its high. */
@Composable
internal fun WeatherDays(days: List<WeatherDay>, modifier: Modifier = Modifier) {
    val low = days.minOfOrNull { it.min } ?: 0
    val high = days.maxOfOrNull { it.max } ?: 1
    val span = (high - low).coerceAtLeast(1).toFloat()
    Column(modifier) {
        days.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.name.trimEnd('.'), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(54.dp))
                WeatherGlyph(d.code, night = false, modifier = Modifier.size(28.dp), animate = false)
                Text(
                    if (d.rain >= 30) "${d.rain} %" else "",
                    color = RainBlue,
                    fontSize = 10.sp,
                    fontFamily = HudFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp),
                )
                Text("${d.min}°", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 8.dp)) {
                    val x0 = size.width * (d.min - low) / span
                    val x1 = (size.width * (d.max - low) / span).coerceAtLeast(x0 + size.height)
                    drawRoundRect(Color.White.copy(alpha = 0.1f), Offset.Zero, size, CornerRadius(size.height / 2f))
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(RainBlue, ZenithSun), startX = x0, endX = x1),
                        Offset(x0, 0f),
                        Size(x1 - x0, size.height),
                        CornerRadius(size.height / 2f),
                    )
                }
                Text("${d.max}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp))
            }
        }
    }
}

/**
 * ZENITH 20, the weather widget: the temperature big with the sky as a picture, today's high and
 * low and the chance of rain, and the next hours underneath. Tapped, the Zentrale opens (where
 * the city is set and the days ahead are).
 */
@Composable
internal fun ZenithWeatherWidget(modifier: Modifier) {
    val context = LocalContext.current
    val report = rememberWeather()
    val city by ZenithWeather.city.collectAsStateWithLifecycle()
    val open: () -> Unit = {
        runCatching { context.startActivity(Intent(context, ZentraleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val r = report
    if (r == null) {
        Column(
            modifier.clickable(onClick = open).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZenithMark(color = ZenithGreen, width = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("ZENITH // WETTER", color = HudGreen, fontSize = 11.sp, fontFamily = HudFont, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (city.isBlank()) "Tippe hier und sag mir, wo du wohnst." else "Das Wetter für ${city.substringBefore(',')} wird geholt …",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
        }
        return
    }
    Column(
        modifier.clickable(onClick = open).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.city.uppercase(), color = HudGreen, fontSize = 10.sp, fontFamily = HudFont, letterSpacing = 2.sp, maxLines = 1)
                Text("${r.now.roundToInt()}°", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
                Text(skyTitle(r.sky), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1)
                Text(
                    "H ${r.max}°  T ${r.min}°  ·  ${r.rain} % Regen",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = HudFont,
                    maxLines = 1,
                )
            }
            WeatherGlyph(r.code, r.night, Modifier.size(74.dp))
        }
        WeatherHourStrip(r.hours.filterIndexed { i, _ -> i % 2 == 0 }.take(6), Modifier.fillMaxWidth())
    }
}
