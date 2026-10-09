package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * ClaudeOS 6.0's egg, "Helios": a total eclipse. The sun – Claude's mark in Helios' white, gold
 * and green – hides behind a black moon, only its corona showing, stars out in the day. Push the
 * moon away: a diamond of light flashes at the edge, the sky turns blue again, and once the sun
 * is free, ClaudeOS 6's own mark comes in.
 */
@Composable
internal fun HeliosEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    val colors = ClaudeMarkStyle.Helios.colors
    val stars = remember {
        val r = Random(6)
        List(80) { Triple(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.3f) }
    }
    val loop = rememberInfiniteTransition(label = "helios")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val sun = Offset(w / 2f, h * 0.44f)
        val r = min(w, h) * 0.2f
        var moon by remember { mutableStateOf(sun) }
        // 0 = the sun all hidden, 1 = all free.
        val free = ((moon - sun).getDistance() / (r * 2.05f)).coerceIn(0f, 1f)
        val freed = free >= 1f
        LaunchedEffect(freed) { if (freed) done = true }
        val day = if (done) 1f else free
        Canvas(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(Color(0xFF020308), Color(0xFF2A74D4), day),
                            lerp(Color(0xFF080A14), Color(0xFF8FD3FF), day),
                            lerp(Color(0xFF0E0D16), Color(0xFFFFF1C9), day),
                        ),
                    ),
                )
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); moon += drag } },
        ) {
            // Stars in the middle of the day, while the sun is hidden.
            val starAlpha = (1f - day * 1.8f).coerceIn(0f, 1f)
            if (starAlpha > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.75f * starAlpha), s.dp.toPx(), Offset(x * size.width, y * size.height)) }
            }
            // The corona round the hidden sun; daylight round the free one.
            val corona = r * (2.6f - 0.6f * day)
            drawCircle(
                Brush.radialGradient(
                    listOf(colors[1].copy(alpha = 0.55f), colors[2].copy(alpha = 0.18f + 0.1f * day), Color.Transparent),
                    center = sun,
                    radius = corona,
                ),
                corona,
                sun,
            )
            // The sun: Claude's mark, turning slowly.
            rotate(spin, sun) {
                for (i in 0 until 12) {
                    val a = (i * 30.0 - 90.0) * PI / 180.0
                    val len = r * if (i % 2 == 0) 0.95f else 0.72f
                    val start = sun + Offset(cos(a).toFloat() * r * 0.16f, sin(a).toFloat() * r * 0.16f)
                    val end = sun + Offset(cos(a).toFloat() * len, sin(a).toFloat() * len)
                    drawLine(Brush.linearGradient(colors, start = start, end = end), start, end, strokeWidth = r * 0.17f, cap = StrokeCap.Round)
                }
            }
            drawCircle(colors.first(), r * 0.16f, sun)
            if (!done) {
                // The moon, black, with the thin bright rim of an eclipse.
                val moonR = r * 1.02f
                drawCircle(Color(0xFF04050A), moonR, moon)
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - free)), moonR, moon, style = Stroke(1.5.dp.toPx()))
                // The diamond ring: the first light breaking out at the moon's edge.
                val ring = (1f - kotlin.math.abs(free - 0.18f) / 0.18f).coerceIn(0f, 1f)
                if (ring > 0f && free > 0f) {
                    val away = (sun - moon) / (moon - sun).getDistance().coerceAtLeast(1f)
                    val spot = sun + away * r * 0.9f
                    val glow = r * (0.25f + 0.5f * ring)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f), Color.Transparent), center = spot, radius = glow), glow, spot)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${ClaudeOs.full} „${ClaudeOs.CODENAME}“", color = if (day > 0.6f) Color(0xFF0B2A1A) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Helios ist frei – die Sonne von ClaudeOS 6 scheint wieder. ☀" else "Sonnenfinsternis. Schieb den Mond von der Sonne.",
                color = (if (day > 0.6f) Color(0xFF0B2A1A) else Color.White).copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClaudeOsFinale(done, ClaudeMarkStyle.Helios)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
