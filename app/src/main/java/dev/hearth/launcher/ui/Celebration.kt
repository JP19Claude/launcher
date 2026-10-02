package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private val Confetti = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFD97757), Color.White,
)

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/**
 * The party when an easter egg is found: confetti raining down over everything and a glass
 * message on top. Draws nothing (and costs nothing) in between.
 */
@Composable
fun CelebrationOverlay() {
    var message by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        EasterEggs.party.collect { text ->
            message = text
            round++
            launch {
                fall.snapTo(0f)
                fall.animateTo(1f, tween(2600, easing = LinearEasing))
            }
            delay(3200)
            message = null
        }
    }
    val text = message ?: return
    val pieces = remember(round) {
        val r = Random(round)
        List(90) {
            Piece(
                x = r.nextFloat(),
                delay = r.nextFloat() * 0.35f,
                speed = 0.8f + r.nextFloat() * 0.6f,
                sway = 0.02f + r.nextFloat() * 0.05f,
                spin = 180f + r.nextFloat() * 540f,
                color = Confetti[r.nextInt(Confetti.size)],
                w = 6f + r.nextFloat() * 6f,
                h = 10f + r.nextFloat() * 8f,
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = fall.value
            pieces.forEach { p ->
                val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * p.speed
                if (local <= 0f || local > 1.2f) return@forEach
                val x = (p.x + sin(local * 12f) * p.sway) * size.width
                val y = -40f + local * (size.height + 80f)
                rotate(local * p.spin, Offset(x, y)) {
                    drawRect(
                        p.color.copy(alpha = (1.2f - local).coerceIn(0f, 1f)),
                        topLeft = Offset(x - p.w / 2, y - p.h / 2),
                        size = Size(p.w * density / 2.5f, p.h * density / 2.5f),
                    )
                }
            }
        }
        LiquidGlass(
            cornerRadius = 24.dp,
            refraction = 16.dp,
            tint = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Clawd dances along.
                Clawd(Modifier.size(40.dp), mood = dev.hearth.launcher.data.ClawdMood.Dance, animate = true)
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                Text(
                    text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
