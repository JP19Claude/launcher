package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Clawd standing on the horizon at [feet], [h] high: asleep, or awake and hopping. */
private fun DrawScope.horizonClawd(feet: Offset, h: Float, awake: Boolean, hop: Float, sleep: Float) {
    val full = size
    val w = h * (14f / 11f)
    translate(feet.x - w / 2f, feet.y - h) {
        drawContext.size = Size(w, h)
        if (awake) {
            drawClawd(ClawdColor, blink = false, bob = -hop * 1.2f, legPhase = hop, armLift = 1f, spin = 0f, hearts = 0f)
        } else {
            drawClawd(ClawdColor, blink = true, bob = 0f, legPhase = 0f, armLift = -0.4f, spin = 0f, hearts = 0f, zzz = sleep)
        }
        drawContext.size = full
    }
}

/**
 * ZENITH 19's easter egg – the sun's run. Night; low on the left the sun waits under the
 * horizon, and on the horizon sleep nineteen Clawds. Push the sun up its arc: the sky turns to
 * dawn and then to day, and every Clawd the light reaches wakes up. Bring it to the zenith –
 * noon, all nineteen awake – and ZENITH's mark comes in.
 */
@Composable
internal fun SunRunEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    var along by remember { mutableFloatStateOf(0.03f) }
    var highest by remember { mutableFloatStateOf(0f) }
    val elevation = sin(along * PI).toFloat().coerceIn(0f, 1f)
    val count = 19
    val awake = (0 until count).count { highest >= (it + 1f) / count * 0.97f }
    val done = highest >= 0.97f
    LaunchedEffect(done) { if (done) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val stars = remember {
        val r = Random(19)
        List(90) { Triple(r.nextFloat(), r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 1.4f) }
    }
    val loop = rememberInfiniteTransition(label = "sunRun")
    val hop by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "hop")
    val sleep by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sleep")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "turn")
    // Night, dawn, day: the sky follows the sun.
    val dawn = (elevation / 0.3f).coerceIn(0f, 1f)
    val noon = ((elevation - 0.3f) / 0.7f).coerceIn(0f, 1f)
    val skyTop = lerp(Color(0xFF03050F), Color(0xFF2A74D4), elevation)
    val skyMid = lerp(Color(0xFF0B1230), Color(0xFF7CC6FF), elevation)
    val skyLow = lerp(lerp(Color(0xFF151B3C), Color(0xFFFF9A5A), dawn), Color(0xFFFFE9B0), noon)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skyTop, skyMid, skyLow))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val base = h * 0.76f
        val rx = w * 0.42f
        val ry = h * 0.38f
        fun moveTo(p: Offset) {
            val dx = (p.x - cx) / rx
            val dy = ((base - p.y) / ry).coerceAtLeast(0f)
            val a = atan2(dy, dx)
            along = (1f - a / PI.toFloat()).coerceIn(0f, 1f)
            highest = max(highest, sin(along * PI).toFloat())
        }
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.05f + 0.05f * elevation),
            fontSize = 200.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectDragGestures(onDragStart = { moveTo(it) }) { change, _ -> moveTo(change.position) } }
                .pointerInput(Unit) { detectTapGestures { moveTo(it) } },
        ) {
            // Stars, fading as the day comes.
            val starAlpha = (1f - elevation * 1.6f).coerceIn(0f, 1f)
            if (starAlpha > 0f) {
                stars.forEach { (x, y, r) -> drawCircle(Color.White.copy(alpha = 0.8f * starAlpha), r.dp.toPx(), Offset(x * size.width, y * size.height)) }
            }
            // The sun's way: dotted, the zenith marked in green.
            drawArc(
                Color.White.copy(alpha = 0.35f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - rx, base - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 9.dp.toPx()))),
            )
            val top = Offset(cx, base - ry)
            drawLine(ZenithGreen, top - Offset(0f, 10.dp.toPx()), top + Offset(0f, 10.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            // The sun.
            val a = PI * (1f - along)
            val sun = Offset(cx + cos(a).toFloat() * rx, base - sin(a).toFloat() * ry)
            val glow = (60f + 90f * elevation).dp.toPx()
            drawCircle(Brush.radialGradient(listOf(ZenithSun.copy(alpha = 0.55f + 0.35f * elevation), ZenithSun.copy(alpha = 0.1f), Color.Transparent), center = sun, radius = glow), glow, sun)
            rotate(turn, sun) {
                for (i in 0 until 12) {
                    val ra = i * (2 * PI / 12)
                    val inner = 26.dp.toPx()
                    val outer = (if (i % 2 == 0) 46f else 38f).dp.toPx() + 14.dp.toPx() * elevation
                    drawLine(
                        ZenithSun.copy(alpha = 0.8f),
                        sun + Offset(cos(ra).toFloat() * inner, sin(ra).toFloat() * inner),
                        sun + Offset(cos(ra).toFloat() * outer, sin(ra).toFloat() * outer),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            drawCircle(Brush.radialGradient(listOf(Color.White, ZenithSun), center = sun, radius = 22.dp.toPx()), 20.dp.toPx(), sun)
            // The land below the horizon (it hides the sun before it rises).
            drawRect(
                Brush.verticalGradient(
                    listOf(lerp(Color(0xFF0B2A1A), ZenithGreen, elevation * 0.8f), lerp(Color(0xFF04100A), ZenithGreenDeep, elevation * 0.6f)),
                    startY = base,
                    endY = size.height,
                ),
                topLeft = Offset(0f, base),
                size = Size(size.width, size.height - base),
            )
            // Nineteen Clawds on the horizon: asleep until the light reaches them.
            val ch = 22.dp.toPx()
            for (i in 0 until count) {
                val x = size.width * (0.05f + 0.9f * i / (count - 1))
                val up = highest >= (i + 1f) / count * 0.97f
                horizonClawd(Offset(x, base + 2.dp.toPx()), ch, up, if (i % 2 == 0) hop else 1f - hop, (sleep + i * 0.13f) % 1f)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Sonnenlauf", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Mittag. Die Sonne steht im Zenit – und alle 19 Clawds sind wach. ☀" else "Schieb die Sonne ihren Bogen hinauf bis zum Zenit · $awake/19 Clawds wach",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ZenithFinale(done)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
