package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * One UI 10 "Fluid": touches answer like liquid – a soft wave of light spreads from the
 * finger and the row gives a little under it – and behind the screens colors drift slowly.
 * Switchable in Hearth's design settings ("One UI 10 Fluid").
 */

/**
 * A touch makes a soft wave of [color] spread from the finger, and the element yields a
 * little and springs back. Never takes the touch: clicks and swipes still work as before.
 */
fun Modifier.fluidTouch(color: Color = Color.White): Modifier = composed {
    if (!LocalSettings.current.fluidDesign || !LocalSettings.current.animations) return@composed this
    val scope = rememberCoroutineScope()
    val wave = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val give = remember { Animatable(1f) }
    var at by remember { mutableStateOf(Offset.Zero) }
    this
        .graphicsLayer {
            scaleX = give.value
            scaleY = give.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                at = down.position
                scope.launch {
                    fade.snapTo(1f)
                    wave.snapTo(0f)
                    wave.animateTo(1f, tween(520))
                }
                scope.launch { give.animateTo(0.975f, spring(stiffness = 900f)) }
                waitForUpOrCancellation()
                scope.launch { give.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                scope.launch { fade.animateTo(0f, tween(420)) }
            }
        }
        .drawWithContent {
            drawContent()
            val a = fade.value
            if (a > 0.01f) {
                val far = hypot(size.width, size.height)
                val r = (far * (0.15f + 0.85f * wave.value)).coerceAtLeast(1f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.22f * a), color.copy(alpha = 0.08f * a), Color.Transparent),
                        center = at,
                        radius = r,
                    ),
                    radius = r,
                    center = at,
                )
            }
        }
}

/**
 * Slowly drifting blobs of color behind a screen (One UI 10 Fluid): [colors] wander on their
 * own paths, soft and dim, so the content stays easy to read.
 */
@Composable
fun FluidBackdrop(colors: List<Color>, modifier: Modifier = Modifier, strength: Float = 1f) {
    if (!LocalSettings.current.fluidDesign) return
    val t by rememberInfiniteTransition(label = "fluid").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Canvas(modifier.fillMaxSize()) {
        colors.forEachIndexed { i, c ->
            val phase = i * 2.1f
            val cx = size.width * (0.5f + 0.38f * cos(t + phase))
            val cy = size.height * (0.32f + 0.26f * sin(t * 0.8f + phase * 1.3f) + 0.22f * i / colors.size.coerceAtLeast(1))
            val r = size.width * (0.55f + 0.12f * sin(t * 1.3f + phase))
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.26f * strength), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}
