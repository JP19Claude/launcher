package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AuroraLook
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * ClaudeOS 7.0's egg, "Apex": the summit. Claude's mark waits in a valley at night; pull it up the
 * mountains with a finger. The higher it climbs, the more the mountains sink away, the sky
 * grows light and the aurora wakes – on top, the mark shines over the clouds, and ClaudeOS 7's
 * own mark comes in.
 */
@Composable
internal fun ApexEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    val colors = ClaudeMarkStyle.Apex.colors
    var climb by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(climb) {
        if (climb >= 0.995f && !done) {
            done = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val shown by animateFloatAsState(if (done) 1f else climb, spring(dampingRatio = 0.8f, stiffness = 180f), label = "apexClimb")
    val loop = rememberInfiniteTransition(label = "apex")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "spin")
    // Three ridges of peaks, far to near: how high each point stands (0..1).
    val ridges = remember {
        val r = Random(7)
        List(3) { List(16) { 0.25f + 0.75f * r.nextFloat() } }
    }
    val stars = remember {
        val r = Random(70)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 1.2f) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(Color(0xFF03040B), Color(0xFF2B1B5E), shown),
                        lerp(Color(0xFF080A1A), Color(0xFFB0587A), shown),
                        lerp(Color(0xFF0C0C1C), Color(0xFFFFD9A8), shown),
                    ),
                ),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    if (!done) climb = (climb - drag / size.height * 1.5f).coerceIn(0f, 1f)
                }
            },
    ) {
        // The aurora wakes with the climb.
        ZenithAurora(AuroraLook.Violett, Modifier.fillMaxSize().graphicsLayer { alpha = shown }, strength = 0.9f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val starAlpha = (1f - shown * 1.6f).coerceIn(0f, 1f)
            if (starAlpha > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.7f * starAlpha), s.dp.toPx(), Offset(x * w, y * h)) }
            }
            // The mark, rising from the valley to above the clouds.
            val r = min(w, h) * 0.11f
            val sun = Offset(w / 2f, h * (0.8f - 0.5f * shown))
            val glow = r * (3f + 1.5f * shown)
            drawCircle(
                Brush.radialGradient(listOf(colors[1].copy(alpha = 0.5f), colors[2].copy(alpha = 0.18f), Color.Transparent), center = sun, radius = glow),
                glow,
                sun,
            )
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
            // The mountains sink as the camera climbs; the nearer, the further.
            val bases = floatArrayOf(0.74f, 0.82f, 0.92f)
            val heights = floatArrayOf(0.2f, 0.26f, 0.32f)
            val sinks = floatArrayOf(0.12f, 0.3f, 0.55f)
            val far = lerp(Color(0xFF0B0F1E), Color(0xFF6A4A8E), shown)
            val mid = lerp(Color(0xFF080B16), Color(0xFF3E2C66), shown)
            val near = lerp(Color(0xFF05060C), Color(0xFF1C1238), shown)
            val tints = listOf(far, mid, near)
            for (layer in 0 until 3) {
                val ridge = ridges[layer]
                val base = h * (bases[layer] + sinks[layer] * shown)
                val path = Path()
                path.moveTo(0f, h * 2f)
                ridge.forEachIndexed { i, v ->
                    path.lineTo(w * i / (ridge.size - 1), base - h * heights[layer] * v)
                }
                path.lineTo(w, h * 2f)
                path.close()
                drawPath(path, tints[layer])
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${LocalClaudeOsEra.current.full} „${LocalClaudeOsEra.current.codename}“",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Der Gipfel. Höher geht es nicht – ClaudeOS 7 ist oben. ▲" else "Zieh das Zeichen nach oben, auf den Gipfel · ${(shown * 8848).toInt()} m",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClaudeOsFinale(done, ClaudeMarkStyle.Apex)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
