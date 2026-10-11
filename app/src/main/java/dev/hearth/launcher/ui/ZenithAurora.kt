package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import dev.hearth.launcher.data.AuroraLook
import kotlin.math.abs
import kotlin.math.sin

/**
 * ZENITH 20, Aurora: curtains of light drifting over a dark sky with a few stars twinkling –
 * the home screen's moving background (and the Zentrale's). Three curtains in the colors of [look],
 * each a soft wave with a bright middle that fades out above and below. Drawn at about thirty
 * frames a second (and still, with animations off). [strength] brightens or dims it, [speed] runs it faster or slower.
 */
@Composable
fun ZenithAurora(look: AuroraLook, modifier: Modifier = Modifier, strength: Float = 1f, speed: Float = 1f) {
    val animate = LocalSettings.current.animations
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (t - last >= 33L) {
                    last = t
                    time = t
                }
            }
        }
    }
    Canvas(modifier.graphicsLayer { }) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val t = time / 1000f * speed
        val colors = look.colors
        // A glow high on the left and low on the right, as if the light had a source.
        drawRect(Brush.radialGradient(listOf(colors[0].copy(alpha = 0.16f * strength), Color.Transparent), center = Offset(w * 0.2f, h * 0.15f), radius = w * 0.95f))
        drawRect(Brush.radialGradient(listOf(colors[2 % colors.size].copy(alpha = 0.12f * strength), Color.Transparent), center = Offset(w * 0.9f, h * 0.8f), radius = w))
        // Stars.
        for (k in 0 until 36) {
            val x = ((k * 7919) % 1000) / 1000f * w
            val y = ((k * 104729) % 1000) / 1000f * h * 0.7f
            val twinkle = 0.3f + 0.7f * abs(sin(t * 0.8f + k))
            drawCircle(Color.White.copy(alpha = 0.5f * twinkle * strength), (0.8f + (k % 3) * 0.5f) * density, Offset(x, y))
        }
        // The curtains.
        for (i in 0 until 3) {
            val color = colors[i % colors.size]
            val base = h * (0.16f + 0.2f * i)
            val amp = h * (0.05f + 0.015f * i)
            val band = h * (0.26f + 0.04f * i)
            val speed = 0.25f + 0.1f * i
            val steps = 28
            val path = Path()
            for (s in 0..steps) {
                val x = w * s / steps
                val y = base + sin(x / w * 5.5f + t * speed + i * 1.7f) * amp + sin(x / w * 11f - t * speed * 0.7f) * amp * 0.4f
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (s in steps downTo 0) {
                val x = w * s / steps
                val y = base + band * (0.8f + 0.2f * sin(x / w * 3f + t * 0.3f + i)) + sin(x / w * 4f + t * speed + i) * amp * 0.6f
                path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.34f * strength), color.copy(alpha = 0.1f * strength), Color.Transparent),
                    startY = base - amp,
                    endY = base + band + amp,
                ),
                blendMode = BlendMode.Plus,
            )
        }
    }
}
