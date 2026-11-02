package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 22 and ClaudeOS 9, the easter eggs behind their version numbers – each its own, as every big version has had:
 * ZENITH 22 "Meridian" (22 rays carry the sun up to the zenith), ClaudeOS 9 "Meridian" (Claude's mark carried up the
 * meridian, from the ground to the stars).
 */

private val MeridianInk = Color(0xFF030B0E)
private val MeridianDeep = Color(0xFF07322F)
private val MeridianSky = Color(0xFF1B7F8F)
private val MeridianMint = Color(0xFF5CF2C1)
private val MeridianSun = Color(0xFFFFE9A8)

/** A ray of light from the sun to where a finger touched the sky. */
private class MdRay(val to: Offset, val born: Long)

/**
 * ZENITH 22's easter egg, "Meridian": the sun stands low over the dark. Every tap into the sky sends a ray of light from it to that
 * place – twenty-two rays, and the sun has climbed to the zenith, on the thin line of the meridian.
 */
@Composable
internal fun MeridianEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val rays = remember { mutableStateListOf<MdRay>() }
    var now by remember { mutableLongStateOf(0L) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val count = rays.size
    val done = count >= 22
    val rise by animateFloatAsState(count / 22f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "mdRise")
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = it }
    }
    val stars = remember {
        val r = Random(22)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.7f, 0.6f + r.nextFloat() * 1.3f) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(MeridianInk, MeridianSky, rise.coerceIn(0f, 1f) * 0.8f),
                        lerp(MeridianDeep, Color(0xFF9CE8D2), rise.coerceIn(0f, 1f) * 0.75f),
                    ),
                ),
            ),
    ) {
        Text(
            version,
            color = Color.White.copy(alpha = if (done) 0.35f else 0.07f),
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
                        if (rays.size < 22) {
                            rays.add(MdRay(at, now))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (rays.size >= 22) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val p = rise.coerceIn(0f, 1f)
            // The stars go out as the day comes.
            val night = (1f - p * 1.4f).coerceIn(0f, 1f)
            if (night > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.75f * night), s.dp.toPx(), Offset(x * w, y * h)) }
            }
            val sun = Offset(w / 2f, h * (0.82f - 0.52f * p))
            val r = min(w, h) * (0.09f + 0.05f * p)
            // The meridian: a thin line of light through the sun, from the top of the sky to the ground.
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, MeridianSun.copy(alpha = 0.4f), Color.Transparent)),
                Offset(sun.x, 0f),
                Offset(sun.x, h),
                strokeWidth = 1.5.dp.toPx(),
            )
            val glow = r * 5f
            drawCircle(
                Brush.radialGradient(listOf(MeridianSun.copy(alpha = 0.55f), MeridianMint.copy(alpha = 0.14f), Color.Transparent), center = sun, radius = glow),
                glow,
                sun,
            )
            rays.forEachIndexed { i, ray ->
                val age = ((now - ray.born) / 1000f).coerceAtLeast(0f)
                val pulse = 0.55f + 0.45f * sin(age * 3f + i)
                drawLine(
                    Brush.linearGradient(listOf(MeridianSun.copy(alpha = 0.9f * pulse), MeridianMint.copy(alpha = 0.25f * pulse)), start = sun, end = ray.to),
                    sun,
                    ray.to,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(MeridianSun.copy(alpha = pulse), 4.dp.toPx(), ray.to)
            }
            drawCircle(Brush.radialGradient(listOf(Color.White, MeridianSun), center = sun, radius = r), r, sun)
            // The ground.
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)), startY = h * 0.84f, endY = h))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version „Meridian“", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Zweiundzwanzig Strahlen – die Sonne steht im Zenit." else "Tippe in den Himmel · $count von 22",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

/** The sky of the ClaudeOS 9 egg at [p] (0 = the ground at dusk … 1 = space), at its [top] or its foot. */
private fun mdSky(p: Float, top: Boolean): Color {
    val stops = if (top) {
        listOf(Color(0xFF1B3A4B), Color(0xFF2B86B5), Color(0xFF0A1A38), Color(0xFF020409))
    } else {
        listOf(Color(0xFFE59A62), Color(0xFF8ED0E8), Color(0xFF1B3A6B), Color(0xFF070B1C))
    }
    val x = p.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], x - i)
}

/**
 * ClaudeOS 9.0's egg, "Meridian": the ground at dusk. Carry Claude's mark up the meridian with a finger – through the blue of the day into
 * the black of space, where the stars come out. ClaudeOS 9's own mark comes in. (It can be carried back down, too.)
 */
@Composable
internal fun MeridianOsEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    var carried by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(carried) {
        if (carried >= 0.995f && !done) {
            done = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val shown by animateFloatAsState(carried, spring(dampingRatio = 0.85f, stiffness = 220f), label = "mdOsCarry")
    val spin by rememberInfiniteTransition(label = "mdOs").animateFloat(0f, 360f, infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "mdOsSpin")
    val stars = remember {
        val r = Random(9)
        List(90) { Triple(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.2f) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(mdSky(shown, true), mdSky(shown, false))))
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    // (dragging up carries it up)
                    carried = (carried - drag / size.height * 1.25f).coerceIn(0f, 1f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val space = ((shown - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (space > 0f) {
                stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.8f * space), s.dp.toPx(), Offset(x * w, y * h)) }
            }
            // The meridian with a tick for every fifteenth of the way.
            drawLine(Color.White.copy(alpha = 0.25f), Offset(w / 2f, h * 0.06f), Offset(w / 2f, h * 0.92f), strokeWidth = 1.dp.toPx())
            for (i in 0..15) {
                val y = h * (0.92f - 0.86f * i / 15f)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(w / 2f - 6.dp.toPx(), y), Offset(w / 2f + 6.dp.toPx(), y), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        val markPx = 84.dp
        ClaudeMark(
            ClaudeMarkStyle.Meridian,
            Modifier
                .size(markPx)
                .offset {
                    IntOffset(
                        (width / 2f - markPx.toPx() / 2f).roundToInt(),
                        (height * (0.92f - 0.86f * shown) - markPx.toPx() / 2f).roundToInt(),
                    )
                }
                .graphicsLayer { rotationZ = spin },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
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
                if (done) "Vom Boden bis zu den Sternen – und darüber hinaus geht es weiter. ClaudeOS 9 ◐" else "Zieh das Zeichen den Meridian hinauf · ${(shown * 90).toInt()}° Höhe",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClaudeOsFinale(done, ClaudeMarkStyle.Meridian)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
