package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hearth UI 15's easter egg – the update of hidden menus gets a hidden vault. Turn the dial
 * like a safe: 15, then 3, then 1 (Hearth UI 15 on ClaudeOS 3.1), holding each number a
 * moment. The door swings open and Clawd is inside.
 */
@Composable
internal fun VaultEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val combo = remember { listOf(15, 3, 1) }
    var angle by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableIntStateOf(0) }
    // The number under the marker at the top: the dial has 100 notches.
    val number = (((-angle / 3.6f) % 100f + 100f) % 100f).roundToInt() % 100
    LaunchedEffect(number, step) {
        if (step < combo.size && number == combo[step]) {
            delay(700)
            step++
        }
    }
    val opened = step >= combo.size
    val door by animateFloatAsState(if (opened) 1f else 0f, tween(1400), label = "door")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07070F), Color(0xFF14102A), Color(0xFF040406)))),
        contentAlignment = Alignment.Center,
    ) {
        FluidBackdrop(AiFluidColors.take(3), strength = 0.35f + 0.5f * door)
        Text(HearthUi.major(version), color = Color.White.copy(alpha = 0.05f), fontSize = 200.sp, fontWeight = FontWeight.Black)
        // Inside the vault, behind the door: Clawd.
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(250.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f * door), Color.Transparent)),
                    ),
            )
            Clawd(Modifier.size(width = 130.dp, height = 112.dp), mood = if (opened) ClawdMood.Dance else ClawdMood.Sleep)
            // The door with its dial: swings away to the side once open.
            Box(
                Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationY = -100f * door
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        alpha = 1f - door * 0.4f
                    }
                    .pointerInput(opened) {
                        if (opened) return@pointerInput
                        detectDragGestures { change, _ ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val before = change.previousPosition - c
                            val now = change.position - c
                            var d = Math.toDegrees(
                                (atan2(now.y, now.x) - atan2(before.y, before.x)).toDouble(),
                            ).toFloat()
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            angle += d
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3A44), Color(0xFF15151B))), radius = r)
                    drawCircle(Color(0xFF8E8E99), radius = r, style = Stroke(4.dp.toPx()))
                    drawCircle(Brush.sweepGradient(AiFluidColors + AiFluidColors.first()), radius = r - 6.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                // The dial turns with the finger.
                Canvas(
                    Modifier
                        .size(210.dp)
                        .graphicsLayer { rotationZ = angle },
                ) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF5A5A66), Color(0xFF26262E))), radius = r)
                    for (i in 0 until 100) {
                        val a = Math.toRadians(i * 3.6 - 90.0)
                        val long = i % 10 == 0
                        val inner = r - (if (long) 18.dp else 9.dp).toPx()
                        drawLine(
                            Color.White.copy(alpha = if (long) 0.9f else 0.45f),
                            Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                            Offset(c.x + cos(a).toFloat() * (r - 3.dp.toPx()), c.y + sin(a).toFloat() * (r - 3.dp.toPx())),
                            strokeWidth = (if (long) 3.dp else 1.5.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(Color(0xFF1C1C22), radius = r * 0.45f)
                }
                // The marker at the top.
                Canvas(Modifier.fillMaxSize()) {
                    val top = (size.height - 210.dp.toPx()) / 2f
                    val w = 9.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width / 2f - w, top - 14.dp.toPx())
                        lineTo(size.width / 2f + w, top - 14.dp.toPx())
                        lineTo(size.width / 2f, top + 2.dp.toPx())
                        close()
                    }
                    drawPath(path, Color(0xFFD97757))
                }
                Text("$number", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Tresor", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (opened) {
                    "Offen! Ganz hinten im Tresor: lauter versteckte Menüs. 🔓"
                } else {
                    "Kombination 15 · 3 · 1 – drehen und kurz halten · ${step}/3"
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
