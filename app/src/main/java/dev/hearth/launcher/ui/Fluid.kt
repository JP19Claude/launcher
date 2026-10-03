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
import androidx.compose.ui.unit.dp
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
fun Modifier.fluidTouch(color: Color = Color.White, yields: Boolean = true): Modifier = composed {
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
                if (yields) scope.launch { give.animateTo(0.975f, spring(stiffness = 900f)) }
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

/** The AI Fluid colors: Claude's terracotta flowing through violet, blue and pink. */
val AiFluidColors = listOf(
    Color(0xFFD97757),
    Color(0xFFFF6FB5),
    Color(0xFF8E6BFF),
    Color(0xFF3E91FF),
    Color(0xFFFFB494),
)

/**
 * AI Fluid: a gradient in Claude's colors flows round the edge of an element, with a soft
 * glow on its inner side – like the light around an AI at work. [strength] dims or brightens
 * it (above 1 for a moment when something new comes).
 */
fun Modifier.aiFluidEdge(
    corner: androidx.compose.ui.unit.Dp,
    strength: Float = 1f,
    width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(1.5f),
    enabled: Boolean = true,
    /** False: the colors stand still (for the many small glass surfaces of the home screen). */
    flowing: Boolean = true,
): Modifier = composed {
    if (!enabled || strength <= 0.01f) return@composed this
    val moving = LocalSettings.current.animations && flowing
    val turn = if (moving) {
        rememberInfiniteTransition(label = "aiFluid").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
            label = "aiTurn",
        )
    } else {
        null
    }
    this.drawWithContent {
        drawContent()
        val w = width.toPx()
        val a = turn?.value ?: 0.8f
        val half = maxOf(size.width, size.height) / 2f
        val c = center
        val start = Offset(c.x + cos(a) * half, c.y + sin(a) * half)
        val end = Offset(2 * c.x - start.x, 2 * c.y - start.y)
        val brush = Brush.linearGradient(AiFluidColors, start, end, androidx.compose.ui.graphics.TileMode.Mirror)
        val r = corner.toPx().coerceAtMost(size.minDimension / 2f)
        // A soft glow inside the edge, then the line itself.
        drawRoundRect(
            brush,
            topLeft = Offset(w * 1.5f, w * 1.5f),
            size = androidx.compose.ui.geometry.Size(size.width - w * 3f, size.height - w * 3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((r - w * 1.5f).coerceAtLeast(0f)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w * 3f),
            alpha = (0.16f * strength).coerceIn(0f, 0.5f),
        )
        drawRoundRect(
            brush,
            topLeft = Offset(w / 2f, w / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((r - w / 2f).coerceAtLeast(0f)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
            alpha = (0.85f * strength).coerceIn(0f, 1f),
        )
    }
}

/**
 * Liquid glass without a wallpaper behind: a frosted fill, light caught along the top and a
 * rim that's bright above and fades below – so cards look like glass on any background.
 */
fun Modifier.glassSheen(corner: androidx.compose.ui.unit.Dp, tint: Color = Color.White): Modifier = this.drawWithContent {
    val r = androidx.compose.ui.geometry.CornerRadius(corner.toPx().coerceAtMost(size.minDimension / 2f))
    drawRoundRect(tint.copy(alpha = 0.055f), cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), endY = size.height * 0.45f),
        cornerRadius = r,
    )
    drawContent()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.12f))),
        cornerRadius = r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
    )
}
