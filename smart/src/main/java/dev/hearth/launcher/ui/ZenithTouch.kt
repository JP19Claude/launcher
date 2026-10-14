package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * ZENITH 19: the Z you can touch. Wherever ZENITH's Z sits in the system it answers: pressed it
 * glows green and swells, a tap does one thing; held until it's charged – a knock and a green
 * wave in the Z's shape – it does another.
 */

/**
 * ZENITH's Z, [width] wide, in [brush] – and, with [onTap] or [onHold], a button: pressed, green
 * light gathers behind it and it grows; let go early, [onTap]; held until full, a knock, a green
 * wave in its shape and [onHold].
 */
@Composable
fun ZenithSign(
    width: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onHold: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val charge = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val active = onTap != null || onHold != null
    // The newest actions, without starting the touch over each time they're made anew.
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val strikes by rememberUpdatedState(LocalSettings.current.zenithStrike)
    var striking by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (striking) ZenithStrikeOverlay { striking = false }
    val touch = if (!active) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                var full = false
                val job = scope.launch {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val held = hold
                    charge.animateTo(1f, tween(if (held != null) 650 else 180, easing = FastOutSlowInEasing))
                    if (held != null) {
                        full = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            burst.snapTo(0f)
                            burst.animateTo(1f, tween(650, easing = LinearEasing))
                        }
                        if (strikes) striking = true
                        held()
                    }
                }
                val up = waitForUpOrCancellation()
                up?.consume()
                job.cancel()
                if (!full && up != null) tap?.invoke()
                scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
            }
        }
    }
    Canvas(
        modifier
            .then(touch)
            // A bigger place to touch than the little Z itself.
            .padding(if (active) 6.dp else 0.dp)
            .size(width = width, height = width * ZenithZAspect),
    ) {
        val c = charge.value
        val w = size.width
        val mid = Offset(w / 2f, size.height / 2f)
        if (c > 0.01f) {
            val r = w * (0.8f + 0.6f * c)
            drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.5f * c), Color.Transparent), center = mid, radius = r), r, mid)
        }
        scale(1f + 0.25f * c, pivot = mid) {
            drawZenithMark(w * 0.02f, size.height * 0.06f, w, SolidColor(Color.Black), alpha = 0.3f)
            drawZenithMark(0f, 0f, w, brush)
            if (c > 0.01f) drawZenithMark(0f, 0f, w, SolidColor(ZenithGreen), alpha = 0.85f * c)
        }
        val b = burst.value
        if (b < 1f) {
            scale(1f + 1.4f * b, pivot = mid) {
                drawPath(zenithZPath(0f, 0f, w), ZenithGreen.copy(alpha = (1f - b) * 0.85f), style = Stroke(2.dp.toPx() * (1f - b) + 0.5f))
            }
        }
    }
}

/**
 * The ZENITH-Z widget: the logo's metal Z, big. Its green light burns as strong as the battery is
 * full (pulsing gently while it charges); the battery under it. Tapped, it charges up like the
 * logo; tapped as often as ZENITH's number, the Illuminati; held until full, Claude.
 */
@Composable
internal fun ZenithZWidget(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (level, charging) = rememberBatteryLevel()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val crystal = rememberCrystalTaps(version)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.padding(10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val z = minOf(maxWidth * 0.95f, maxHeight * 1.25f)
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            ZenithLogo(
                size = z,
                light = 0.2f + 0.8f * (level / 100f).coerceIn(0f, 1f),
                onTap = crystal,
                onCharged = { dev.hearth.launcher.data.ClaudeAssistant.openAssistant(context) },
            )
            androidx.compose.material3.Text(
                (if (charging) "⚡ " else "") + "$level %",
                color = if (charging) ZenithGreen else Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
        }
    }
}
