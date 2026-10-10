package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.LauncherSettings
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.random.Random

/*
 * ZENITH 19, "Projekt Zenith": the whole system futuristic – every surface a hologram (dark glass,
 * scan lines, a green frame with brackets), the icons green holograms, a computer typeface, a
 * grid horizon with the Z floating over it as the background, a HUD clock and a HUD frame round
 * the home screen.
 */

/** Projekt Zenith's light green, for brackets and readouts. */
internal val HudGreen = Color(0xFF7DFFA8)

/** The HUD's typeface: a computer's. */
internal val HudFont = FontFamily.Monospace

/**
 * Projekt Zenith's background: black into deep green, a glowing horizon with a grid floor running
 * towards it, the Z over it as a wireframe hologram, a few points of light, scan lines and a
 * vignette.
 */
internal fun DrawScope.drawProjectWallpaper() {
    val w = size.width
    val h = size.height
    val u = w / 400f
    drawRect(Brush.verticalGradient(0f to Color(0xFF010302), 0.55f to Color(0xFF031109), 1f to Color(0xFF020604)))
    // Points of light in the sky.
    val r = Random(7)
    repeat(90) {
        drawCircle(HudGreen.copy(alpha = 0.08f + r.nextFloat() * 0.3f), (0.5f + r.nextFloat()) * u, Offset(r.nextFloat() * w, r.nextFloat() * h * 0.55f))
    }
    val horizon = h * 0.62f
    val vp = Offset(w / 2f, horizon)
    // The horizon, glowing.
    scale(1f, 0.22f, pivot = vp) {
        drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.4f), Color.Transparent), center = vp, radius = w), w, vp)
    }
    // The grid floor running towards it.
    for (i in -14..14) {
        drawLine(ZenithGreen.copy(alpha = 0.2f), vp, Offset(w / 2f + i * w * 0.13f, h), strokeWidth = u)
    }
    for (j in 1..16) {
        val t = j / 16f
        val y = horizon + (h - horizon) * t * t
        drawLine(ZenithGreen.copy(alpha = 0.05f + 0.22f * t), Offset(0f, y), Offset(w, y), strokeWidth = u)
    }
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF031109), 1f to Color.Transparent, startY = horizon, endY = horizon + (h - horizon) * 0.3f),
        topLeft = Offset(0f, horizon),
        size = androidx.compose.ui.geometry.Size(w, (h - horizon) * 0.3f),
    )
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, HudGreen.copy(alpha = 0.9f), Color.Transparent)),
        Offset(0f, horizon),
        Offset(w, horizon),
        strokeWidth = 1.5f * u,
    )
    // The Z over the horizon, a wireframe hologram.
    val zw = w * 0.8f
    val z = zenithZPath((w - zw) / 2f, horizon - zw * ZenithZAspect - h * 0.07f, zw)
    drawPath(z, ZenithGreen.copy(alpha = 0.06f))
    drawPath(z, ZenithGreen.copy(alpha = 0.22f), style = Stroke(7f * u))
    drawPath(z, HudGreen.copy(alpha = 0.85f), style = Stroke(1.5f * u))
    // Scan lines and a vignette.
    var y = 0f
    while (y < h) {
        drawLine(Color.Black.copy(alpha = 0.14f), Offset(0f, y), Offset(w, y), strokeWidth = u)
        y += 4f * u
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2f, h * 0.45f), radius = h * 0.75f))
}

/**
 * Projekt Zenith's frame round the home screen: a readout along the top, and now and then a scan
 * line sweeping down over everything (no brackets in the corners any more).
 */
@Composable
internal fun ProjectHudFrame(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val phase = flowPhase(9000, s.animations)
    val now = rememberTime(everySecond = false)
    val (level, charging) = rememberBatteryLevel()
    val locale = Locale.getDefault()
    androidx.compose.foundation.layout.Box(modifier) {
        Canvas(androidx.compose.ui.Modifier.matchParentSize()) {
            // The scan line: a third of the time it sweeps down, otherwise it rests.
            val p = phase?.invoke()?.let { (it / (2f * PI.toFloat())) % 1f } ?: -1f
            if (p in 0f..0.33f) {
                val y = size.height * (p / 0.33f)
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, ZenithGreen.copy(alpha = 0.1f), HudGreen.copy(alpha = 0.35f)), startY = y - 60.dp.toPx(), endY = y),
                    topLeft = Offset(0f, y - 60.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 60.dp.toPx()),
                )
            }
        }
        Text(
            "PROJEKT ZENITH ▸ " + now.format(DateTimeFormatter.ofPattern("EEE dd.MM.", locale)).uppercase(locale) + " ▸ AKKU $level%" + if (charging) " ▲" else "",
            color = HudGreen.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontFamily = HudFont,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 20.dp, top = top + 8.dp),
        )
    }
}

/**
 * Projekt Zenith's clock: a label, the time big in a computer's digits glowing green, the seconds
 * small beside it, and a readout under it – the date, the battery, the sun – in brackets.
 */
@Composable
internal fun ProjectHudClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now = rememberTime(everySecond = settings.animations)
    val (level, _) = rememberBatteryLevel()
    val locale = Locale.getDefault()
    val words = remember(now.hour, now.minute) { zenithWords(now.hour, now.minute) }
    Column(
        modifier
            .drawBehind {
                val l = 18.dp.toPx()
                val sw = 2.dp.toPx()
                drawLine(HudGreen, Offset.Zero, Offset(l, 0f), strokeWidth = sw)
                drawLine(HudGreen, Offset.Zero, Offset(0f, l), strokeWidth = sw)
                drawLine(HudGreen, Offset(size.width, size.height), Offset(size.width - l, size.height), strokeWidth = sw)
                drawLine(HudGreen, Offset(size.width, size.height), Offset(size.width, size.height - l), strokeWidth = sw)
                drawLine(ZenithGreen.copy(alpha = 0.4f), Offset(l + 8.dp.toPx(), 0f), Offset(size.width * 0.45f, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("ZENITH // ZEIT", color = HudGreen.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = HudFont, letterSpacing = 2.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm")), settings.omegaZeros),
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, HudGreen, ZenithGreen)),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = HudFont,
                    lineHeight = 74.sp,
                    shadow = Shadow(ZenithGreen.copy(alpha = 0.8f), Offset.Zero, 30f),
                ),
            )
            if (settings.animations) {
                Text(
                    ":" + now.format(DateTimeFormatter.ofPattern("ss")),
                    color = HudGreen.copy(alpha = 0.8f),
                    fontSize = 22.sp,
                    fontFamily = HudFont,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
        }
        Text(
            now.format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", locale)).uppercase(locale) + " · AKKU $level% · " + words.uppercase(locale),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = HudFont,
            letterSpacing = 1.sp,
        )
    }
}
