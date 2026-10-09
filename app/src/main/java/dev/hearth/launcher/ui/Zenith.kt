package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ZENITH 19 – the system's new name: the sun at its highest point. Its mark is a white Z with
 * the version, green, written through it; above it, at the zenith, the sun.
 */

/** ZENITH's colors: the green of its number, the sun, the sky. */
internal val ZenithGreen = Color(0xFF2FD27A)
internal val ZenithGreenDeep = Color(0xFF15965A)
internal val ZenithSun = Color(0xFFFFD34D)
internal val ZenithSky = Color(0xFF5CC8FF)

/** ZENITH's fluid: green, sun, sky, mint and white light. */
val ZenithFluidColors = listOf(
    ZenithGreen,
    ZenithSun,
    ZenithSky,
    Color(0xFFB8F5C8),
    Color(0xFFFFF6D6),
)

/**
 * The ZENITH mark: a white Z of light, the version written through it in green, and the sun
 * resting above it. Held down (like ColorOS's logo), it charges up: the sun climbs to the
 * zenith, its rays turn and stretch, the mark lifts and glows – with a little knock when it's
 * full – and settles back with a bounce when let go. [onTap] counts quick taps (the
 * Illuminati's secret).
 */
@Composable
fun ZenithLogo(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    number: String = "19",
    caption: String? = null,
    interactive: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val charge = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    var full by remember { mutableStateOf(false) }
    val holdModifier = if (!interactive && onTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(onTap, interactive) {
            awaitEachGesture {
                // The logo keeps its touches: a card it sits on doesn't open as well.
                awaitFirstDown().consume()
                val start = System.currentTimeMillis()
                val job = if (interactive) {
                    scope.launch {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        launch { spin.animateTo(spin.value + 720f, tween(2600, easing = LinearEasing)) }
                        charge.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                        full = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } else {
                    null
                }
                waitForUpOrCancellation()?.consume()
                job?.cancel()
                val quick = System.currentTimeMillis() - start < 280
                full = false
                if (quick) onTap?.invoke()
                if (interactive) scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f)) }
            }
        }
    }
    val density = LocalDensity.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .then(holdModifier)
                .graphicsLayer {
                    val c = charge.value
                    scaleX = 1f + 0.12f * c
                    scaleY = 1f + 0.12f * c
                    translationY = -c * size.toPx() * 0.04f
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val s = this.size.minDimension
                val c = charge.value
                // The sun: resting just above the Z; held, it climbs to the very top.
                val sun = Offset(this.size.width / 2f, s * (0.13f - 0.08f * c))
                val glowR = s * (0.32f + 0.25f * c)
                drawCircle(
                    Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.45f + 0.35f * c), ZenithSun.copy(alpha = 0.08f), Color.Transparent), center = sun, radius = glowR),
                    radius = glowR,
                    center = sun,
                )
                // Its rays: turning and stretching while held.
                rotate(spin.value, sun) {
                    val rays = 12
                    for (i in 0 until rays) {
                        val a = i * (2 * PI / rays)
                        val inner = s * 0.075f
                        val outer = s * (0.11f + 0.09f * c) * (if (i % 2 == 0) 1f else 0.75f)
                        drawLine(
                            ZenithSun.copy(alpha = 0.75f),
                            sun + Offset(cos(a).toFloat() * inner, sin(a).toFloat() * inner),
                            sun + Offset(cos(a).toFloat() * outer, sin(a).toFloat() * outer),
                            strokeWidth = s * 0.018f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                drawCircle(
                    Brush.radialGradient(listOf(Color.White, ZenithSun), center = sun - Offset(s * 0.015f, s * 0.015f), radius = s * 0.06f),
                    radius = s * 0.055f,
                    center = sun,
                )
                // The Z: a stroke of white light with a soft glow behind it.
                val z = Path().apply {
                    moveTo(s * 0.2f, s * 0.3f)
                    lineTo(s * 0.8f, s * 0.3f)
                    lineTo(s * 0.18f, s * 0.8f)
                    lineTo(s * 0.84f, s * 0.8f)
                }
                val zx = (this.size.width - s) / 2f
                translate(zx, 0f) {
                    drawPath(z, Color.White.copy(alpha = 0.18f + 0.25f * c), style = Stroke(s * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(
                        z,
                        Brush.linearGradient(listOf(Color.White, Color(0xFFE6F2FF), Color.White), Offset(0f, s * 0.3f), Offset(s, s * 0.8f)),
                        style = Stroke(s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            // The version, green, written through the Z.
            Text(
                number,
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(lerp(ZenithGreen, Color.White, 0.25f), ZenithGreen, ZenithGreenDeep)),
                    fontSize = with(density) { (size * 0.42f).toSp() },
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = with(density) { (size * -0.02f).toSp() },
                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 4f), 14f),
                ),
                modifier = Modifier
                    .padding(top = size * 0.1f)
                    .graphicsLayer {
                        val c = charge.value
                        scaleX = 1f + 0.08f * c
                        scaleY = 1f + 0.08f * c
                    },
            )
        }
        if (caption != null) {
            Text(
                caption,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = with(density) { (size * 0.1f).toSp() },
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = size * 0.04f),
            )
        }
    }
}

/**
 * Once, after updating: the move from OMEGA UI to ZENITH. Night; the Ω of the old name fades;
 * the sun rises from the horizon along its arc up to the zenith while the sky turns to day –
 * and up there the ZENITH mark forms. A tap (once it's done) goes on.
 */
@Composable
fun ZenithUpgrade(version: String, onDone: () -> Unit) {
    val animate = LocalSettings.current.animations
    val t = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(6200, easing = LinearEasing)) }
    BackHandler { if (t.value >= 0.8f) onDone() }
    val p = t.value
    // 0..0.15: OMEGA fades; 0.1..0.7: the sun climbs; 0.6..1: ZENITH forms.
    val old = (1f - p / 0.15f).coerceIn(0f, 1f)
    val climb = ((p - 0.1f) / 0.6f).coerceIn(0f, 1f)
    val form = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(Color(0xFF02030A), Color(0xFF2E7BD6), climb),
                        lerp(Color(0xFF070B1C), Color(0xFF8FD3FF), climb),
                        lerp(Color(0xFF0B0F22), Color(0xFFFFE9B0), climb * 0.8f),
                    ),
                ),
            )
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (t.value >= 0.8f) onDone() },
        contentAlignment = Alignment.Center,
    ) {
        // The sun's way across the sky, and the sun on it.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * 0.78f
            val top = h * 0.16f
            val f = 0.05f + 0.45f * FastOutSlowInEasing.transform(climb)
            val sun = Offset(w * f * 2f * 0.9f + w * 0.05f, horizon - (horizon - top) * sin(f * PI).toFloat())
            val glow = w * (0.25f + 0.25f * climb)
            drawCircle(Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.7f * climb + 0.1f), Color.Transparent), center = sun, radius = glow), radius = glow, center = sun)
            drawCircle(Color.White.copy(alpha = (0.4f + 0.6f * climb) * (1f - form)), radius = w * 0.045f, center = sun)
            // The horizon line, fading as the day comes.
            drawLine(Color.White.copy(alpha = 0.25f * (1f - form)), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.dp.toPx())
        }
        // The old name going.
        if (old > 0.01f) {
            Column(Modifier.graphicsLayer { alpha = old; scaleX = 1f + 0.2f * (1f - old); scaleY = 1f + 0.2f * (1f - old) }, horizontalAlignment = Alignment.CenterHorizontally) {
                OmegaRuby(size = 120.dp)
                Text("OMEGA UI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // ZENITH forming at the top of the sky.
        if (form > 0.01f) {
            Column(
                Modifier.graphicsLayer {
                    alpha = form
                    val k = 0.7f + 0.3f * form
                    scaleX = k
                    scaleY = k
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ZenithLogo(size = 190.dp, number = version.substringBefore('.'), interactive = false)
                Spacer(Modifier.height(10.dp))
                Text("ZENITH ${version.substringBefore('.')}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(
                    "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                )
            }
        }
        if (p >= 0.8f) {
            Text(
                "OMEGA UI heißt jetzt ZENITH – die Sonne im höchsten Punkt.\nAntippen, um loszulegen",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                    .graphicsLayer { alpha = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f) },
            )
        }
    }
}
