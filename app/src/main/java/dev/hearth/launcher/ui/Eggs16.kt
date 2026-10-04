package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Firefly(var x: Float, var y: Float, var vx: Float, var vy: Float, val phase: Float, val color: Color)

/**
 * Hearth UI 16's easter egg, "Glühwürmchen": fireflies glimmer in the dark. Hold a finger on
 * the screen and they come to it – gather sixteen around it and Clawd lights up.
 */
@Composable
internal fun FireflyEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val density = LocalDensity.current
    val reach = with(density) { 90.dp.toPx() }
    var area by remember { mutableStateOf(Size.Zero) }
    var finger by remember { mutableStateOf(Offset.Unspecified) }
    val flies = remember { mutableStateListOf<Firefly>() }
    var near by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(0L) }

    LaunchedEffect(area) {
        if (area == Size.Zero || flies.isNotEmpty()) return@LaunchedEffect
        val colors = listOf(Color(0xFFFFE27A), Color(0xFFB8FF7A), Color(0xFFFFB494), Color(0xFF9FE8FF))
        repeat(40) { i ->
            flies += Firefly(
                Random.nextFloat() * area.width,
                Random.nextFloat() * area.height,
                (Random.nextFloat() - 0.5f) * 60f,
                (Random.nextFloat() - 0.5f) * 60f,
                Random.nextFloat() * 6.28f,
                colors[i % colors.size],
            )
        }
    }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0.016f else (t - last).coerceIn(1L, 40L) / 1000f
                last = t
                val f = finger
                var count = 0
                flies.forEach { fly ->
                    // A little wandering of its own …
                    fly.vx += (Random.nextFloat() - 0.5f) * 140f * dt
                    fly.vy += (Random.nextFloat() - 0.5f) * 140f * dt
                    // … and drawn to a finger held still.
                    if (f.isSpecified) {
                        val dx = f.x - fly.x
                        val dy = f.y - fly.y
                        val d = hypot(dx, dy).coerceAtLeast(1f)
                        val pull = if (d > reach * 0.5f) 260f else -80f
                        fly.vx += dx / d * pull * dt
                        fly.vy += dy / d * pull * dt
                        if (d < reach) count++
                    }
                    fly.vx *= 0.985f
                    fly.vy *= 0.985f
                    fly.x += fly.vx * dt
                    fly.y += fly.vy * dt
                    if (area.width > 0f) {
                        if (fly.x < 0f || fly.x > area.width) fly.vx = -fly.vx
                        if (fly.y < 0f || fly.y > area.height) fly.vy = -fly.vy
                        fly.x = fly.x.coerceIn(0f, area.width)
                        fly.y = fly.y.coerceIn(0f, area.height)
                    }
                }
                near = count
                if (count > best) best = count
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050814), Color(0xFF0B1A1A), Color(0xFF030405))))
            .onSizeChanged { area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    finger = down.position
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { if (it.pressed) finger = it.position }
                    } while (event.changes.any { it.pressed })
                    finger = Offset.Unspecified
                }
            },
    ) {
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.05f),
            fontSize = 200.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L) return@Canvas
            flies.forEach { fly ->
                val glow = 0.45f + 0.55f * ((sin(now / 380f + fly.phase) + 1f) / 2f)
                val c = Offset(fly.x, fly.y)
                drawCircle(
                    Brush.radialGradient(listOf(fly.color.copy(alpha = 0.55f * glow), Color.Transparent), center = c, radius = 18.dp.toPx()),
                    radius = 18.dp.toPx(),
                    center = c,
                )
                drawCircle(fly.color.copy(alpha = glow), radius = 2.6.dp.toPx(), center = c)
            }
            if (finger.isSpecified) {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = reach, center = finger)
            }
        }
        if (best >= 16) {
            Clawd(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 110.dp, height = 94.dp),
                mood = ClawdMood.Love,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Glühwürmchen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (best >= 16) "Sechzehn Glühwürmchen – Hearth UI 16 leuchtet. ✨" else "Finger auflegen und warten · ${near.coerceAtMost(16)}/16 bei dir",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        EggCloseButton(onClose)
    }
}

/** One stroke of northern light, painted by a finger. */
private class AuroraRibbon(val points: MutableList<Offset>, val hue: Int)

/**
 * ClaudeOS 4.0's egg, "Aurora": a polar night over the sea of Claude's colors. Paint with a
 * finger across the sky and northern lights hang where you drew, swaying – five of them and
 * the sky is full.
 */
@Composable
internal fun AuroraEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    val ribbons = remember { mutableStateListOf<AuroraRibbon>() }
    var drawing by remember { mutableStateOf<AuroraRibbon?>(null) }
    var now by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }
    val stars = remember { List(70) { Offset(Random.nextFloat(), Random.nextFloat() * 0.7f) to Random.nextFloat() } }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = it }
    }
    val palettes = remember {
        listOf(
            listOf(Color(0xFF6CFFB0), Color(0xFF3EE0C8), Color(0xFF8E6BFF)),
            listOf(Color(0xFF8E6BFF), Color(0xFFFF6FB5), Color(0xFFD97757)),
            listOf(Color(0xFF3E91FF), Color(0xFF6CFFB0), Color(0xFFFFE27A)),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF071226), Color(0xFF0B0F1E))))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val ribbon = AuroraRibbon(mutableListOf(down.position), ribbons.size)
                    drawing = ribbon
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let {
                            if (it.pressed) {
                                ribbon.points += it.position
                                tick++
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (ribbon.points.size > 4) ribbons += ribbon
                    drawing = null
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L || tick < 0) return@Canvas
            stars.forEach { (p, twinkle) ->
                val a = 0.3f + 0.5f * ((sin(now / 600f + twinkle * 10f) + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = a), radius = 1.2.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
            }
            (ribbons + listOfNotNull(drawing)).forEach { ribbon ->
                val colors = palettes[ribbon.hue % palettes.size]
                val height = size.height * 0.28f
                ribbon.points.forEachIndexed { i, p ->
                    if (i % 2 != 0) return@forEachIndexed
                    // Curtains of light hanging up from the stroke, swaying slowly.
                    val sway = sin(now / 900f + i * 0.12f) * 10.dp.toPx()
                    val top = Offset(p.x + sway, p.y - height * (0.6f + 0.4f * ((cos(now / 1300f + i * 0.2f) + 1f) / 2f)))
                    drawLine(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors[2].copy(alpha = 0.18f), colors[1].copy(alpha = 0.35f), colors[0].copy(alpha = 0.55f)),
                            startY = top.y,
                            endY = p.y,
                        ),
                        top,
                        p,
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            // The sea, catching the light.
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF0B1A2E), Color(0xFF02040A)), startY = size.height * 0.82f),
                topLeft = Offset(0f, size.height * 0.82f),
                size = Size(size.width, size.height * 0.18f),
            )
        }
        if (ribbons.size >= 5) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .size(width = 90.dp, height = 78.dp),
                mood = ClawdMood.Surprised,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${ClaudeOs.full} „${ClaudeOs.CODENAME}“", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (ribbons.size >= 5) "Der Himmel ist voller Nordlicht. 🌌" else "Mal mit dem Finger Nordlichter an den Himmel · ${ribbons.size}/5",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        EggCloseButton(onClose)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EggCloseButton(onClose: () -> Unit) {
    GlassCircle(
        onClick = onClose,
        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        size = 44.dp,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
    }
}
