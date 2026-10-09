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
 * The Z of ZENITH's mark in a box [s] wide starting at [left]: three sharp bars, the diagonal
 * as thick as the other two.
 */
private fun zenithZ(s: Float, left: Float): Path {
    val l = left + s * 0.2f
    val r = left + s * 0.8f
    val t = s * 0.3f
    val b = s * 0.86f
    val bar = s * 0.12f
    val k = s * 0.2f
    return Path().apply {
        moveTo(l, t)
        lineTo(r, t)
        lineTo(r, t + bar)
        lineTo(l + k, b - bar)
        lineTo(r, b - bar)
        lineTo(r, b)
        lineTo(l, b)
        lineTo(l, b - bar)
        lineTo(r - k, t + bar)
        lineTo(l, t + bar)
        close()
    }
}

/**
 * The ZENITH mark: a sharp white Z lit from above, the version set through it in glossy green
 * with a clean cut round it, and over it the sun's arc with the sun at its top – the zenith.
 * Held down (like ColorOS's logo), it charges up: the arc fills with light from the horizon to
 * the sun, a gold glint runs over the Z, the sun swells – a knock and a ring of light when it's
 * full – and it settles back with a bounce when let go. [onTap] counts quick taps (the
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
                        burst.animateTo(1f, tween(750, easing = LinearEasing))
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
    val measurer = rememberTextMeasurer()
    val digits = remember(number, size, density) {
        measurer.measure(
            number,
            TextStyle(
                fontSize = with(density) { (size * 0.32f).toSp() },
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = with(density) { (size * -0.02f).toSp() },
                // Leaning with the Z's diagonal.
                textGeometricTransform = TextGeometricTransform(skewX = -0.2f),
            ),
        )
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .then(holdModifier)
                .graphicsLayer {
                    val c = charge.value
                    scaleX = 1f + 0.06f * c
                    scaleY = 1f + 0.06f * c
                    // Its own layer, so the cut round the number only cuts the Z.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            val s = this.size.minDimension
            val x0 = (this.size.width - s) / 2f
            val cx = x0 + s * 0.5f
            val c = charge.value
            // The sun's way: an arc over the Z, its top the zenith.
            val arcC = Offset(cx, s * 0.62f)
            val arcR = s * 0.46f
            val arcBox = Offset(arcC.x - arcR, arcC.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            val sun = Offset(cx, arcC.y - arcR)
            val glowR = s * (0.2f + 0.14f * c)
            drawCircle(
                Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.4f + 0.4f * c), ZenithSun.copy(alpha = 0.1f), Color.Transparent), center = sun, radius = glowR),
                glowR,
                sun,
            )
            drawArc(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), ZenithSun, Color.White.copy(alpha = 0.5f), Color.Transparent),
                    startX = arcC.x - arcR,
                    endX = arcC.x + arcR,
                ),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = arcBox,
                size = arcSize,
                style = Stroke(s * 0.016f, cap = StrokeCap.Round),
            )
            if (c > 0.01f) {
                // Held: the way lights up, from the horizon over the zenith.
                drawArc(ZenithSun.copy(alpha = 0.3f), 205f, 130f * c, false, arcBox, arcSize, style = Stroke(s * 0.055f, cap = StrokeCap.Round))
                drawArc(Color.White, 205f, 130f * c, false, arcBox, arcSize, style = Stroke(s * 0.02f, cap = StrokeCap.Round))
            }
            // The sun at the zenith: white-hot, gold at its edge.
            val sunR = s * 0.05f * (1f + 0.4f * c)
            drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFFFFF1B8), ZenithSun), center = sun - Offset(sunR * 0.3f, sunR * 0.3f), radius = sunR * 1.4f),
                sunR,
                sun,
            )
            // The Z: sharp, white, lit from above.
            val z = zenithZ(s, x0)
            drawPath(z, Brush.verticalGradient(listOf(Color.White, Color(0xFFF3F7FB), Color(0xFFC7D2E0)), startY = s * 0.3f, endY = s * 0.86f))
            if (c > 0.01f) {
                // A gold glint running over it while it charges.
                clipPath(z) {
                    val band = x0 + s * (-0.2f + 1.4f * c)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, ZenithSun.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(band - s * 0.16f, s * 0.3f),
                            end = Offset(band + s * 0.16f, s * 0.9f),
                        ),
                    )
                }
            }
            // The number, set through the Z: a clean cut round it, then glossy green.
            val fontPx = s * 0.32f
            val glyph = fontPx * 0.71f
            val baseline = s * 0.58f + glyph / 2f
            val tl = Offset(cx - digits.size.width / 2f, baseline - digits.firstBaseline)
            // (The text's gradients are in its own space: from the top of the digits to their foot.)
            val foot = digits.firstBaseline
            val head = foot - glyph
            drawText(digits, color = Color.Black, topLeft = tl, drawStyle = Stroke(s * 0.04f, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            drawText(
                digits,
                brush = Brush.verticalGradient(listOf(Color(0xFFC8FFE0), ZenithGreen, ZenithGreenDeep), startY = head, endY = foot),
                topLeft = tl,
            )
            drawText(
                digits,
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), startY = head, endY = head + glyph * 0.55f),
                topLeft = tl,
            )
            drawText(digits, color = Color.White.copy(alpha = 0.3f), topLeft = tl, drawStyle = Stroke(s * 0.006f))
            // Full: a ring of light going out from the sun.
            val b = burst.value
            if (b < 1f) {
                drawCircle(ZenithSun.copy(alpha = (1f - b) * 0.85f), s * (0.06f + 0.55f * b), sun, style = Stroke(s * 0.014f * (1f - b) + 1f))
            }
        }
        if (caption != null) {
            // "ZENITH 19": the name in white, the number in ZENITH's green.
            val words = caption.split(' ')
            val last = words.last()
            val text = buildAnnotatedString {
                if (words.size > 1 && last.firstOrNull()?.isDigit() == true) {
                    append(words.dropLast(1).joinToString(" "))
                    append(" ")
                    withStyle(SpanStyle(color = ZenithGreen)) { append(last) }
                } else {
                    append(caption)
                }
            }
            Text(
                text,
                color = Color.White,
                fontSize = with(density) { (size * 0.1f).toSp() },
                fontWeight = FontWeight.Medium,
                letterSpacing = with(density) { (size * 0.025f).toSp() },
                modifier = Modifier.padding(top = size * 0.02f),
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
