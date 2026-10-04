package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.AodClock
import dev.hearth.launcher.data.AodTint
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.NotificationHub
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin

/*
 * OMEGA UI 18.1 – what Glimmer's stage shows: the full Always On Display, and what lies on the
 * lock screen. Nothing here can be touched; it only lies over the system's own screens.
 */

/** What the stage shows right now: the full AOD while dozing, the lock screen's extras while locked. */
@Composable
fun LockStageContent(s: LauncherSettings, locked: Boolean, dozing: Boolean, wallpaper: ImageBitmap?) {
    when {
        dozing && s.fullAod -> FullAod(s, wallpaper)
        dozing -> Unit
        locked -> LockExtras(s)
    }
}

/** The clock's color: white, OMEGA Fluid or gold. */
@Composable
private fun clockBrush(s: LauncherSettings): Brush = when (s.aodTint) {
    AodTint.White -> Brush.verticalGradient(listOf(Color.White, Color.White))
    AodTint.Omega -> Brush.linearGradient(listOf(Color.White) + omegaPalette(s).take(3))
    AodTint.Gold -> Brush.linearGradient(listOf(Color(0xFFFFF1C2), Color(0xFFD4AF37), Color(0xFF9C7A1E)))
}

/** "Viertel nach acht": the time in words, to five minutes. */
private fun timeInWords(time: LocalDateTime): String {
    val names = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf")
    val rounded = ((time.minute + 2) / 5) * 5
    val hour = names[time.hour % 12]
    val next = names[(time.hour + 1) % 12]
    val text = when (rounded) {
        0 -> "${if (hour == "eins") "ein" else hour} Uhr"
        5 -> "fünf nach $hour"
        10 -> "zehn nach $hour"
        15 -> "Viertel nach $hour"
        20 -> "zwanzig nach $hour"
        25 -> "fünf vor halb $next"
        30 -> "halb $next"
        35 -> "fünf nach halb $next"
        40 -> "zwanzig vor $next"
        45 -> "Viertel vor $next"
        50 -> "zehn vor $next"
        55 -> "fünf vor $next"
        else -> "${if (next == "eins") "ein" else next} Uhr"
    }
    return text.replaceFirstChar { it.titlecase(Locale.GERMANY) }
}

/**
 * The full Always On Display, like a Galaxy S24 Ultra's: the wallpaper, dimmed, fills the
 * screen behind a big clock, the date, the battery and what's waiting; a sleeping Clawd and a
 * thin OMEGA rim. Everything moves a little every minute, so nothing burns into the screen.
 */
@Composable
private fun FullAod(s: LauncherSettings, wallpaper: ImageBitmap?) {
    val now = rememberTime(everySecond = false)
    val (battery, charging) = rememberBatteryLevel()
    val badges by NotificationHub.badges.collectAsStateWithLifecycle()
    val locale = Locale.getDefault()
    val palette = omegaPalette(s)
    // Burn-in: a small step every minute, round a little square.
    val dx = ((now.minute * 37) % 17 - 8).dp
    val dy = ((now.minute * 53) % 17 - 8).dp
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (wallpaper != null) {
            Image(
                wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = s.aodBrightness.light,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(palette[0].copy(alpha = s.aodBrightness.light * 0.6f), Color.Black, palette[1].copy(alpha = s.aodBrightness.light * 0.5f)),
                        ),
                    ),
            )
        }
        // Darker at the top and bottom, so the clock stays clear.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )
        if (s.aodEdge) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    Brush.sweepGradient(palette + palette.first(), center = center),
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(44.dp.toPx()),
                    style = Stroke(w),
                    alpha = 0.6f,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().offset(x = dx, y = dy)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = maxHeight * 0.22f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val brush = clockBrush(s)
                val time = omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", locale)), s.omegaZeros)
                when (s.aodClock) {
                    AodClock.Omega -> Text(time, style = TextStyle(brush = brush, fontSize = 92.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp))
                    AodClock.Thin -> Text(time, style = TextStyle(brush = brush, fontSize = 84.sp, fontWeight = FontWeight.Thin))
                    AodClock.Big -> {
                        Text(omegaDigits(now.format(DateTimeFormatter.ofPattern("HH", locale)), s.omegaZeros), style = TextStyle(brush = brush, fontSize = 128.sp, fontWeight = FontWeight.Black, lineHeight = 120.sp))
                        Text(omegaDigits(now.format(DateTimeFormatter.ofPattern("mm", locale)), s.omegaZeros), style = TextStyle(brush = brush, fontSize = 128.sp, fontWeight = FontWeight.Black, lineHeight = 120.sp))
                    }
                    AodClock.Words -> Text(
                        timeInWords(now),
                        style = TextStyle(brush = brush, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, lineHeight = 46.sp, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
                if (s.aodDate) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.aodBattery) AodChip((if (charging) "⚡ " else "🔋 ") + "$battery %")
                    val waiting = badges.values.sum()
                    if (s.aodNotifications && waiting > 0) AodChip("🔔 $waiting")
                }
            }
            if (s.aodClawd) {
                Clawd(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = maxHeight * 0.12f)
                        .size(width = 64.dp, height = 55.dp),
                    mood = ClawdMood.Sleep,
                    animate = false,
                )
            }
        }
    }
}

@Composable
private fun AodChip(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
    }
}

/** A greeting for the time of day. */
private fun lockGreeting(hour: Int) = when (hour) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Guten Tag"
    in 18..22 -> "Guten Abend"
    else -> "Gute Nacht"
}

/**
 * On the lock screen: the chosen extras – the perks' atmosphere, the OMEGA rim, the Ω, a
 * greeting and a message, Clawd, the battery and, while charging, a rising light.
 */
@Composable
private fun LockExtras(s: LauncherSettings) {
    val now = rememberTime(everySecond = false)
    val (battery, charging) = rememberBatteryLevel()
    val palette = omegaPalette(s)
    val flow = flowPhase(7_000, s.animations && (s.lockEdge || (s.lockCharge && charging)))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (s.lockAtmosphere) PerkBackLayers(s, Modifier.fillMaxSize())
        if (s.lockCharge && charging) {
            Canvas(Modifier.fillMaxSize()) {
                val t = flow?.invoke() ?: 1f
                val rise = size.height * (0.12f + 0.25f * battery / 100f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, palette[0].copy(alpha = 0.35f), palette[1].copy(alpha = 0.5f)),
                        startY = size.height - rise - 40.dp.toPx() * (1f + 0.2f * sin(t)),
                        endY = size.height,
                    ),
                )
            }
            Text(
                "⚡ $battery %",
                style = TextStyle(brush = Brush.linearGradient(listOf(Color.White) + palette.take(2)), fontSize = 44.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.56f),
            )
        }
        if (s.lockEdge) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 3.dp.toPx()
                val t = flow?.invoke() ?: 0f
                drawRoundRect(
                    Brush.sweepGradient(palette + palette.first(), center = center),
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(44.dp.toPx()),
                    style = Stroke(w),
                    alpha = 0.6f + 0.3f * sin(t),
                )
            }
        }
        if (s.lockOmega) {
            OmegaSign(30.sp, Modifier.align(Alignment.TopCenter).padding(top = 52.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = maxHeight * 0.34f, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s.lockGreeting) {
                Text(
                    lockGreeting(now.hour),
                    style = TextStyle(brush = Brush.linearGradient(listOf(Color.White) + palette.take(2)), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
                )
                Spacer(Modifier.height(6.dp))
            }
            if (s.lockMessage.isNotBlank()) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(s.lockMessage, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
        if (s.lockBattery && !(s.lockCharge && charging)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.72f)) {
                AodChip((if (charging) "⚡ " else "🔋 ") + "$battery %")
            }
        }
        if (s.lockClawd) {
            Clawd(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = maxHeight * 0.6f, end = 18.dp)
                    .size(width = 66.dp, height = 57.dp),
                mood = if (charging) ClawdMood.Dance else ClawdMood.Wave,
            )
        }
    }
}
