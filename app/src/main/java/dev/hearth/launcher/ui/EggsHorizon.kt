package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 21 and ClaudeOS 8, the easter eggs behind their version numbers – each its own, as every big version
 * has had: ZENITH 21 "Horizont" (21 lights in the mountains), ZENITH 21.5 "Comet" (21 bubbles of Liquid
 * Glass), ClaudeOS 8 "Horizon" (Claude's mark carried over the horizon, a whole day).
 */

// ---------------------------------------------------------------------------------------------
// ZENITH 21 · Horizont
// ---------------------------------------------------------------------------------------------

/** A light hanging in the dark: on which of the three layers of mountains it travels, how far along (0..1) and how high (fraction of the screen). */
private class HzLight(val layer: Int, val u: Float, val y: Float)

/** One layer of mountains: the polyline of [heights] across [worldWidth], moved left by [shift], standing on [base] with [amp] its highest peak. */
private fun DrawScope.hzRidge(heights: List<Float>, worldWidth: Float, shift: Float, base: Float, amp: Float, color: Color) {
    val path = Path()
    path.moveTo(-shift, size.height * 2f)
    heights.forEachIndexed { i, v ->
        path.lineTo(worldWidth * i / (heights.size - 1) - shift, base - amp * v)
    }
    path.lineTo(worldWidth - shift, size.height * 2f)
    path.close()
    drawPath(path, color)
}

/**
 * ZENITH 21's easter egg, "Horizont": night over three layers of mountains, and 21 faint lights hang in the
 * dark. The horizon can be pulled sideways with a finger – the nearer layers move further – and every light
 * that is tapped lights up and brings the dawn a little closer. With the 21st the sun rises behind the
 * ridges and ZENITH's mark comes in.
 */
@Composable
internal fun HorizonEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val factors = remember { floatArrayOf(0.35f, 0.65f, 1f) }
    val bases = remember { floatArrayOf(0.64f, 0.76f, 0.88f) }
    val amps = remember { floatArrayOf(0.17f, 0.2f, 0.24f) }
    val ridges = remember {
        val r = Random(21)
        List(3) { List(28) { 0.2f + 0.8f * r.nextFloat() } }
    }
    val lights = remember {
        val r = Random(2100)
        List(21) { i -> HzLight(i % 3, 0.04f + 0.92f * r.nextFloat(), 0.28f + 0.42f * r.nextFloat()) }
    }
    val stars = remember {
        val r = Random(210)
        List(60) { Triple(r.nextFloat(), r.nextFloat() * 0.55f, 0.6f + r.nextFloat() * 1.2f) }
    }
    var scroll by remember { mutableFloatStateOf(0f) }
    val found = remember { mutableStateListOf<Int>() }
    val done = found.size >= 21
    val dawn by animateFloatAsState(if (done) 1f else found.size / 21f * 0.3f, tween(900), label = "hzDawn")
    val rise by animateFloatAsState(if (done) 1f else 0f, tween(3200), label = "hzRise")
    LaunchedEffect(done) { if (done) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val pulse by rememberInfiniteTransition(label = "hzPulse").animateFloat(
        0.25f, 0.75f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "hzPulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(Color(0xFF02040C), Color(0xFF1B4FBF), dawn),
                        lerp(Color(0xFF0A1236), Color(0xFF4FA8F0), dawn),
                        lerp(Color(0xFF14204A), Color(0xFFFFD9A0), dawn),
                    ),
                ),
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Pulled sideways: the horizon moves, the nearer the further.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, drag ->
                        change.consume()
                        scroll = (scroll - drag / size.width * 1.3f).coerceIn(0f, 1f)
                    }
                }
                // A light that is tapped lights up.
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var best = -1
                        var bestDistance = 44.dp.toPx()
                        lights.forEachIndexed { i, l ->
                            if (i in found) return@forEachIndexed
                            val span = w * (1f + 0.8f * factors[l.layer])
                            val x = l.u * span - scroll * 0.8f * w * factors[l.layer]
                            val d = hypot(at.x - x, at.y - h * l.y)
                            if (d < bestDistance) {
                                best = i
                                bestDistance = d
                            }
                        }
                        if (best >= 0) {
                            found.add(best)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val starAlpha = (1f - dawn * 2.4f).coerceIn(0f, 1f)
            if (starAlpha > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.7f * starAlpha), s.dp.toPx(), Offset(x * w, y * h)) }
            }
            // The sun, behind the ridges, once the last light is lit.
            if (rise > 0.01f) {
                val sun = Offset(w * 0.7f, h * (1.02f - 0.5f * rise))
                val glow = w * (0.5f + 0.2f * rise)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * rise), Color(0xFFFFD98A).copy(alpha = 0.35f * rise), Color.Transparent), center = sun, radius = glow),
                    glow,
                    sun,
                )
                drawCircle(Color.White.copy(alpha = 0.95f * rise), w * 0.05f, sun)
            }
            val tints = listOf(
                lerp(Color(0xFF0A1330), Color(0xFF2E5C9E), dawn),
                lerp(Color(0xFF070E24), Color(0xFF1D3F78), dawn),
                lerp(Color(0xFF04091A), Color(0xFF0E2350), dawn),
            )
            for (layer in 0 until 3) {
                val span = w * (1f + 0.8f * factors[layer])
                hzRidge(ridges[layer], span, scroll * 0.8f * w * factors[layer], h * bases[layer], h * amps[layer], tints[layer])
            }
            // The lights, on top: faint where they hang unseen, warm where they were found.
            lights.forEachIndexed { i, l ->
                val span = w * (1f + 0.8f * factors[l.layer])
                val at = Offset(l.u * span - scroll * 0.8f * w * factors[l.layer], h * l.y)
                if (at.x < -40f || at.x > w + 40f) return@forEachIndexed
                if (i in found) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFFFFE9B0).copy(alpha = 0.75f), Color(0xFFFFB84D).copy(alpha = 0.25f), Color.Transparent), center = at, radius = 26.dp.toPx()),
                        26.dp.toPx(),
                        at,
                    )
                    drawCircle(Color.White, 3.5.dp.toPx(), at)
                } else {
                    drawCircle(Color(0xFFFFE9B0).copy(alpha = pulse * 0.6f), 3.dp.toPx(), at)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Horizont", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Einundzwanzig Lichter – und der Tag bricht an. ☀" else "Zieh den Horizont zur Seite und tipp die Lichter an · ${found.size}/21",
                color = Color.White.copy(alpha = 0.8f),
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

// ---------------------------------------------------------------------------------------------
// ZENITH 21.5 · Comet
// ---------------------------------------------------------------------------------------------

/** A bubble of glass: where it was blown ([x0], [y0], at [born]), how big, how fast it rises (px per ms), its wobble's phase, its tint. */
private class HzBubble(val x0: Float, val y0: Float, val r: Float, val speed: Float, val seed: Float, val born: Long, val tint: Color)

/** What is left of a popped bubble: droplets flying out of [at] for a moment. */
private class HzDroplet(val at: Offset, val born: Long, val tint: Color, val seed: Int)

/** Where [b] is at [now]. */
private fun hzBubbleAt(b: HzBubble, now: Long): Offset {
    val age = (now - b.born).coerceAtLeast(0L)
    return Offset(b.x0 + sin(age / 900f + b.seed) * b.r * 0.3f, b.y0 - age * b.speed)
}

/**
 * ZENITH 21.5's easter egg, "Comet": bubbles of Liquid Glass rise from the dark – clear, with a bright rim and a
 * glint. Tap one and it pops; tap the empty dark and a new one is blown there. Twenty-one bubbles for
 * ZENITH 21 – the .5 is the glass itself – and ZENITH's mark comes in.
 */
@Composable
internal fun GlassBubbleEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val bubbles = remember { mutableStateListOf<HzBubble>() }
    val drops = remember { mutableStateListOf<HzDroplet>() }
    var now by remember { mutableLongStateOf(0L) }
    var popped by remember { mutableIntStateOf(0) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val done = popped >= 21
    val tints = remember { listOf(CometBlue, Color(0xFF9A6BFF), Color(0xFF3FD0FF), Color(0xFFFF6FB5), Color(0xFF62E0B0)) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                now = t
                if (last == 0L) last = t
                // One bubble after the other rises from the bottom, until the 21st has popped.
                if (popped < 21 && width > 0f && t - last > 650L && bubbles.size < 8) {
                    last = t
                    val r = Random(t)
                    bubbles.add(
                        HzBubble(
                            x0 = width * (0.12f + 0.76f * r.nextFloat()),
                            y0 = height + 80f,
                            r = width * (0.07f + 0.06f * r.nextFloat()),
                            speed = height * (0.00007f + 0.00005f * r.nextFloat()),
                            seed = r.nextFloat() * 6f,
                            born = t,
                            tint = tints[r.nextInt(tints.size)],
                        ),
                    )
                }
                bubbles.removeAll { hzBubbleAt(it, t).y < -it.r * 2f }
                drops.removeAll { t - it.born > 700L }
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CometBlack, CometDeep, CometNavy))),
    ) {
        Text(
            version,
            color = Color.White.copy(alpha = if (done) 0.3f else 0.07f),
            fontSize = 150.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    width = it.width.toFloat()
                    height = it.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val hit = bubbles.lastOrNull { b -> hypot(at.x - hzBubbleAt(b, now).x, at.y - hzBubbleAt(b, now).y) <= b.r * 1.15f }
                        if (hit != null) {
                            val where = hzBubbleAt(hit, now)
                            bubbles.remove(hit)
                            drops.add(HzDroplet(where, now, hit.tint, popped * 13 + 5))
                            popped += 1
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (!done) {
                            val r = Random(now + at.x.toLong())
                            bubbles.add(
                                HzBubble(
                                    x0 = at.x,
                                    y0 = at.y,
                                    r = width * (0.06f + 0.05f * r.nextFloat()),
                                    speed = height * (0.00007f + 0.00004f * r.nextFloat()),
                                    seed = r.nextFloat() * 6f,
                                    born = now,
                                    tint = tints[r.nextInt(tints.size)],
                                ),
                            )
                        }
                    }
                },
        ) {
            bubbles.forEach { b ->
                val c = hzBubbleAt(b, now)
                // Clear in the middle, the tint gathering at the rim, a bright edge, a glint and a faint reflection.
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.03f), b.tint.copy(alpha = 0.12f), b.tint.copy(alpha = 0.36f)), center = c, radius = b.r),
                    b.r,
                    c,
                )
                drawCircle(Color.White.copy(alpha = 0.55f), b.r, c, style = Stroke(width = 2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.6f), b.r * 0.13f, c + Offset(-b.r * 0.42f, -b.r * 0.46f))
                drawCircle(Color.White.copy(alpha = 0.18f), b.r * 0.34f, c + Offset(b.r * 0.3f, b.r * 0.36f), style = Stroke(width = 3.dp.toPx()))
            }
            drops.forEach { d ->
                val t = ((now - d.born) / 700f).coerceIn(0f, 1f)
                val rnd = Random(d.seed)
                for (i in 0 until 10) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val reach = (30f + 60f * rnd.nextFloat()) * density * t
                    drawCircle(
                        lerp(Color.White, d.tint, 0.4f).copy(alpha = 1f - t),
                        (3f - 2f * t) * density,
                        d.at + Offset(cos(a) * reach, sin(a) * reach + 12f * density * t * t),
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Comet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Einundzwanzig Blasen, klar wie Glas. Das Comet-Glas ist da. ✨" else "Tipp die Blasen an – ins Leere tippen bläst eine neue · ${popped.coerceAtMost(21)}/21",
                color = Color.White.copy(alpha = 0.8f),
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

// ---------------------------------------------------------------------------------------------
// ClaudeOS 8 · Horizon
// ---------------------------------------------------------------------------------------------

/** The sky at [p] (0 night, 0.25 dawn, 0.5 day, 0.75 dusk, 1 night again): its top, or its foot at the horizon. */
private fun hzSky(p: Float, top: Boolean): Color {
    val tops = listOf(Color(0xFF04050F), Color(0xFF3A2A6E), Color(0xFF2B6FD6), Color(0xFF4A2A6A), Color(0xFF03040C))
    val feet = listOf(Color(0xFF14183A), Color(0xFFFF9A5B), Color(0xFF9FD6FF), Color(0xFFFF7A6B), Color(0xFF0B1030))
    val list = if (top) tops else feet
    val x = p.coerceIn(0f, 1f) * (list.size - 1)
    val i = x.toInt().coerceAtMost(list.size - 2)
    return lerp(list[i], list[i + 1], x - i)
}

/**
 * ClaudeOS 8.0's egg, "Horizon": night at the edge of the world. Carry Claude's mark over the horizon
 * with a finger, from left to right – the sky goes through dawn, day and dusk, and at the far end the
 * stars come out again. ClaudeOS 8's own mark comes in. (It can be carried back, too.)
 */
@Composable
internal fun HorizonOsEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    val colors = ClaudeMarkStyle.Horizon.colors
    var carried by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(carried) {
        if (carried >= 0.995f && !done) {
            done = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val shown by animateFloatAsState(carried, spring(dampingRatio = 0.85f, stiffness = 220f), label = "hzOsCarry")
    val spin by rememberInfiniteTransition(label = "hzOs").animateFloat(0f, 360f, infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "hzOsSpin")
    val stars = remember {
        val r = Random(80)
        List(80) { Triple(r.nextFloat(), r.nextFloat() * 0.55f, 0.6f + r.nextFloat() * 1.2f) }
    }
    val ridge = remember {
        val r = Random(8)
        List(22) { 0.25f + 0.75f * r.nextFloat() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(hzSky(shown, true), hzSky(shown, false))))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, drag ->
                    change.consume()
                    carried = (carried + drag / size.width * 1.25f).coerceIn(0f, 1f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Night is where the sky is at its ends.
            val night = (max(1f - shown * 4f, 0f) + max(shown * 4f - 3f, 0f)).coerceIn(0f, 1f)
            if (night > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.75f * night), s.dp.toPx(), Offset(x * w, y * h)) }
            }
            // The mark, carried along the arc of the sky.
            val r = min(w, h) * 0.1f
            val mark = Offset(w * (0.1f + 0.8f * shown), h * (0.64f - 0.34f * sin(PI.toFloat() * shown)))
            val glow = r * 3.4f
            drawCircle(
                Brush.radialGradient(listOf(colors[1].copy(alpha = 0.55f), colors[2].copy(alpha = 0.16f), Color.Transparent), center = mark, radius = glow),
                glow,
                mark,
            )
            rotate(spin, mark) {
                for (i in 0 until 12) {
                    val a = (i * 30.0 - 90.0) * PI / 180.0
                    val len = r * if (i % 2 == 0) 0.95f else 0.72f
                    val start = mark + Offset(cos(a).toFloat() * r * 0.16f, sin(a).toFloat() * r * 0.16f)
                    val end = mark + Offset(cos(a).toFloat() * len, sin(a).toFloat() * len)
                    drawLine(Brush.linearGradient(colors, start = start, end = end), start, end, strokeWidth = r * 0.17f, cap = StrokeCap.Round)
                }
            }
            drawCircle(colors.first(), r * 0.16f, mark)
            // The edge of the world: one ridge, dark by night, a little lit by day.
            val day = sin(PI.toFloat() * shown)
            hzRidge(ridge, w, 0f, h * 0.9f, h * 0.2f, lerp(Color(0xFF05060F), Color(0xFF1A2548), day))
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
                if (done) "Ein ganzer Tag über dem Horizont – und dahinter geht es weiter. ClaudeOS 8 ◐" else "Trag das Zeichen über den Horizont · ${(shown * 24).toInt()}:00 Uhr",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClaudeOsFinale(done, ClaudeMarkStyle.Horizon)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
