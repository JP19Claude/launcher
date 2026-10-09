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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * The ZENITH mark, as in its logo: a slanted Z of dark, worn metal with cut ends, a silver bevel
 * and green light burning along its lower edges – "ZENITH" in wide chrome letters under it
 * ([caption]; a number after the name comes in green). Held down (like ColorOS's logo) it charges
 * up: the green burns brighter and floods the metal, a band of light runs over it – a knock and
 * a green shock wave when it's full – and it settles back with a bounce when let go. [onTap]
 * counts quick taps (the Illuminati's secret). [size] is the Z's width; [light] dims the green
 * (for its entrance).
 */
@Composable
fun ZenithLogo(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    @Suppress("UNUSED_PARAMETER") number: String = "19",
    caption: String? = null,
    interactive: Boolean = true,
    light: Float = 1f,
    onTap: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val charge = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
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
                        charge.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        burst.snapTo(0f)
                        burst.animateTo(1f, tween(800, easing = LinearEasing))
                    }
                } else {
                    null
                }
                waitForUpOrCancellation()?.consume()
                job?.cancel()
                val quick = System.currentTimeMillis() - start < 280
                if (quick) onTap?.invoke()
                if (interactive) {
                    scope.launch { burst.snapTo(1f) }
                    scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 240f)) }
                }
            }
        }
    }
    val density = LocalDensity.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width = size, height = size * 0.64f)
                .then(holdModifier)
                .graphicsLayer {
                    val c = charge.value
                    scaleX = 1f + 0.05f * c
                    scaleY = 1f + 0.05f * c
                },
        ) {
            val w = this.size.width * 0.9f
            val left = (this.size.width - w) / 2f
            val top = (this.size.height - w * ZenithZAspect) / 2f
            val c = charge.value
            val mid = Offset(left + w / 2f, top + w * ZenithZAspect / 2f)
            if (c > 0.01f) {
                // Held: green light pooling behind it.
                drawCircle(
                    Brush.radialGradient(listOf(ZenithGreen.copy(alpha = 0.3f * c), Color.Transparent), center = mid, radius = w * 0.65f),
                    w * 0.65f,
                    mid,
                )
            }
            drawZenithZ(left, top, w, light = light, charge = c)
            // Full: a green shock wave in the Z's shape.
            val b = burst.value
            if (b < 1f) {
                scale(1f + 0.3f * b, pivot = mid) {
                    drawPath(zenithZPath(left, top, w), ZenithGreen.copy(alpha = (1f - b) * 0.8f), style = Stroke(w * 0.012f * (1f - b) + 1f))
                }
            }
        }
        if (caption != null) {
            // "ZENITH 19": the chrome lettering, the number after it in green.
            val words = caption.split(' ')
            val named = words.first().equals("ZENITH", ignoreCase = true)
            val letters = size * 0.058f
            if (named) {
                Row(Modifier.padding(top = size * 0.03f), verticalAlignment = Alignment.CenterVertically) {
                    ZenithLettering(letters)
                    val rest = words.drop(1).joinToString(" ")
                    if (rest.isNotEmpty()) {
                        Spacer(Modifier.width(letters * 1.4f))
                        Text(
                            rest,
                            color = ZenithGreen,
                            fontSize = with(density) { (letters * 1.55f).toSp() },
                            fontWeight = FontWeight.Light,
                            letterSpacing = with(density) { (letters * 0.3f).toSp() },
                        )
                    }
                }
            } else {
                Text(
                    caption,
                    color = Color.White,
                    fontSize = with(density) { (size * 0.08f).toSp() },
                    fontWeight = FontWeight.Medium,
                    letterSpacing = with(density) { (size * 0.025f).toSp() },
                    modifier = Modifier.padding(top = size * 0.02f),
                )
            }
        }
    }
}

/**
 * Once, after updating: the move from OMEGA UI to ZENITH. Night; the Ω of the old name fades; a
 * green light rises from the horizon along its arc up to the zenith while the dark turns deep
 * green – and there the ZENITH Z forms out of the dark, its green edges lighting up one after
 * the other. A tap (once it's done) goes on.
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
                        lerp(Color(0xFF02030A), Color(0xFF040A07), climb),
                        lerp(Color(0xFF070B1C), Color(0xFF071A11), climb),
                        lerp(Color(0xFF0B0F22), Color(0xFF0E3320), climb),
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
            val fade = 1f - form
            drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = (0.55f * climb + 0.1f) * fade), Color.Transparent), center = sun, radius = glow), radius = glow, center = sun)
            drawCircle(Color.White.copy(alpha = (0.4f + 0.6f * climb) * fade), radius = w * 0.03f, center = sun)
            // The horizon: a line of green light, fading as ZENITH forms.
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, ZenithGreen.copy(alpha = 0.7f * fade), Color.Transparent)),
                Offset(0f, horizon),
                Offset(w, horizon),
                strokeWidth = 1.5.dp.toPx(),
            )
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
                ZenithLogo(
                    size = 260.dp,
                    caption = "ZENITH ${version.substringBefore('.')}",
                    interactive = false,
                    // Its green edges light up once the metal is there.
                    light = ((form - 0.35f) / 0.5f).coerceIn(0f, 1f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                )
            }
        }
        if (p >= 0.8f) {
            Text(
                "OMEGA UI heißt jetzt ZENITH – ganz oben, im Zenit.\nAntippen, um loszulegen",
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
