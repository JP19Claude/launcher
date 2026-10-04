package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.pow

/*
 * The easter eggs behind the versions change with every big version, like Android's do:
 * Hearth up to 12.0 had its planet, 12.5 the fluid ocean, 13 silk, 13.5 drops of liquid
 * glass; ClaudeOS 1 had "Ember" (the
 * spinning star), ClaudeOS 2 has "Blaze" (the fire). New big versions bring new ones.
 */

/** "12.5" → 12.5 (only the first two numbers count). */
internal fun versionNumber(version: String): Float =
    version.split('.').take(2).joinToString(".").toFloatOrNull() ?: 0f

/** ClaudeOS's egg for the ClaudeOS that's running. */
@Composable
internal fun ClaudeOsVersionEgg(onClose: () -> Unit) {
    if (versionNumber(ClaudeOs.VERSION) >= 2f) BlazeEgg(onClose) else ClaudeOsEgg(onClose)
}

/** A ring spreading on the water where a finger touched. */
private class Ripple(val at: Offset, val born: Long)

/**
 * Hearth 12.5's egg, "Fluid-Ozean": the version floats on liquid colors; a tap makes ripples
 * and Clawd swims to it, holding sends a big wave through everything.
 */
@Composable
internal fun FluidOceanEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<Ripple>() }
    var now by remember { mutableLongStateOf(0L) }
    var splashes by remember { mutableIntStateOf(0) }
    var wave by remember { mutableLongStateOf(-10_000L) }
    val clawd = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var mood by remember { mutableStateOf(ClawdMood.Wave) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                now = t
                ripples.removeAll { t - it.born > 2200 }
            }
        }
    }
    val drift by rememberInfiniteTransition(label = "ocean").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "drift",
    )
    var centre by remember { mutableStateOf(Offset.Zero) }

    fun splash(at: Offset) {
        ripples += Ripple(at, now)
        splashes++
        mood = if (splashes % 5 == 0) ClawdMood.Flip else ClawdMood.Jump
        // Clawd swims there (from the middle of the screen).
        scope.launch { clawd.animateTo(at - centre, spring(dampingRatio = 0.6f, stiffness = 60f)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF04102A), Color(0xFF0A1C4A), Color(0xFF050814))))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { splash(it) },
                    onLongPress = {
                        wave = now
                        repeat(6) { i -> ripples += Ripple(Offset(size.width * (i + 0.5f) / 6f, size.height * 0.75f), now + i * 90L) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            centre = Offset(size.width / 2f, size.height / 2f)
            // The liquid: Claude's colors as big soft blobs, a wave lifting them for a moment.
            val lift = ((now - wave) / 1400f).let { if (it in 0f..1f) sin(it * Math.PI.toFloat()) else 0f }
            AiFluidColors.forEachIndexed { i, c ->
                val phase = i * 1.3f
                val cx = size.width * (0.5f + 0.4f * cos(drift + phase))
                val cy = size.height * (0.55f + 0.3f * sin(drift * 1.4f + phase)) - lift * size.height * 0.2f
                val r = size.width * (0.45f + 0.1f * sin(drift * 2f + phase))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
            }
            ripples.forEach { rp ->
                val age = ((now - rp.born) / 2200f)
                if (age < 0f) return@forEach
                for (k in 0 until 3) {
                    val a = (age - k * 0.12f).coerceIn(0f, 1f)
                    if (a <= 0f) continue
                    drawCircle(
                        Color.White.copy(alpha = 0.5f * (1f - a)),
                        radius = 12.dp.toPx() + a * 160.dp.toPx(),
                        center = rp.at,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        // The version, floating.
        Text(
            version,
            style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFFBFE6FF), Color(0xFF8E6BFF), Color(0xFFFF6FB5))), fontSize = 110.sp, fontWeight = FontWeight.Light),
            modifier = Modifier.graphicsLayer { translationY = sin(drift * 3f) * 10.dp.toPx() },
        )
        // Clawd, swimming where you tap.
        Clawd(
            Modifier
                .size(width = 76.dp, height = 64.dp)
                .graphicsLayer {
                    translationX = clawd.value.x
                    translationY = clawd.value.y + 150.dp.toPx() + sin(drift * 4f) * 6.dp.toPx()
                },
            mood = mood,
        )
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name $version · Fluid-Ozean", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (splashes >= 15) "Clawd ist ein Profi-Schwimmer! 🌊" else "Antippen · gedrückt halten", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
        EggClose(onClose)
    }
}

/** A drop of liquid glass: where it is, how big, how fast. */
private class Drop(var x: Float, var y: Float, var r: Float, val hue: Int) {
    var vx = 0f
    var vy = 0f
}

/** The finger on the drops (not state: the frame loop reads it). */
private class DropHand {
    var drop: Drop? = null
    var at = Offset.Unspecified
    var area = Size.Zero
}

/**
 * Hearth 13.5's egg, "Tropfen": drops of liquid glass fall, bounce and roll. Tap to let a new
 * one fall, grab one and throw it – and when two touch, they flow together into one.
 */
@Composable
internal fun DropsEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val drops = remember { mutableListOf<Drop>() }
    val hand = remember { DropHand() }
    var now by remember { mutableLongStateOf(0L) }
    var merges by remember { mutableIntStateOf(0) }
    var hues by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0.016f else (t - last).coerceIn(1L, 40L) / 1000f
                last = t
                val w = hand.area.width
                val h = hand.area.height
                if (w > 0f) {
                    if (drops.isEmpty()) {
                        repeat(6) { i -> drops += Drop(w * (0.15f + 0.14f * i), h * (0.1f + 0.05f * (i % 3)), w * (0.05f + 0.012f * (i % 4)), hues++) }
                    }
                    drops.forEach { d ->
                        val held = d === hand.drop && hand.at.isSpecified
                        if (held) {
                            // Follows the finger like something heavy and soft.
                            d.vx = (hand.at.x - d.x) * 14f
                            d.vy = (hand.at.y - d.y) * 14f
                        } else {
                            d.vy += 1400f * dt
                        }
                        d.x += d.vx * dt
                        d.y += d.vy * dt
                        if (d.x - d.r < 0f) { d.x = d.r; d.vx = -d.vx * 0.55f }
                        if (d.x + d.r > w) { d.x = w - d.r; d.vx = -d.vx * 0.55f }
                        if (d.y - d.r < 0f) { d.y = d.r; d.vy = -d.vy * 0.5f }
                        if (d.y + d.r > h) {
                            d.y = h - d.r
                            d.vy = -d.vy * 0.45f
                            d.vx *= 0.97f
                        }
                    }
                    // Two drops touching flow together (keeping all their glass).
                    var i = 0
                    while (i < drops.size) {
                        var j = i + 1
                        while (j < drops.size) {
                            val a = drops[i]
                            val b = drops[j]
                            val dx = a.x - b.x
                            val dy = a.y - b.y
                            if (dx * dx + dy * dy < (a.r + b.r) * (a.r + b.r) * 0.7f) {
                                val ma = a.r * a.r
                                val mb = b.r * b.r
                                val m = ma + mb
                                a.x = (a.x * ma + b.x * mb) / m
                                a.y = (a.y * ma + b.y * mb) / m
                                a.vx = (a.vx * ma + b.vx * mb) / m
                                a.vy = (a.vy * ma + b.vy * mb) / m
                                a.r = kotlin.math.sqrt(m).coerceAtMost(w * 0.42f)
                                if (hand.drop === b) hand.drop = a
                                drops.removeAt(j)
                                merges++
                            } else {
                                j++
                            }
                        }
                        i++
                    }
                }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1630), Color(0xFF241243), Color(0xFF090611))))
            .onSizeChanged { hand.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = down.position
                    val grabbed = drops.minByOrNull { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) }
                        ?.takeIf { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) < (it.r * 1.4f) * (it.r * 1.4f) }
                    if (grabbed != null) {
                        hand.drop = grabbed
                        hand.at = p
                    } else if (drops.size < 24) {
                        // A new drop, falling from where you tapped.
                        drops += Drop(p.x, p.y, size.width * (0.04f + 0.03f * Math.random().toFloat()), hues++)
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { if (it.pressed) hand.at = it.position }
                    } while (event.changes.any { it.pressed })
                    hand.drop = null
                    hand.at = Offset.Unspecified
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The version, behind the glass.
        Text(
            version,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Color(0xFFBFE6FF), Color(0xFF8E6BFF), Color(0xFFFF6FB5))),
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
            ),
        )
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L) return@Canvas
            drops.forEach { d ->
                val c = Offset(d.x, d.y)
                val tint = AiFluidColors[d.hue % AiFluidColors.size]
                // The body: clear in the middle, a little color caught towards the bottom.
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.16f)), center = c, radius = d.r),
                    radius = d.r,
                    center = c,
                )
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent), center = c + Offset(d.r * 0.25f, d.r * 0.4f), radius = d.r * 0.9f),
                    radius = d.r,
                    center = c,
                )
                // The rim, and light caught at the top left.
                drawCircle(Color.White.copy(alpha = 0.55f), radius = d.r, center = c, style = Stroke(1.5.dp.toPx()))
                val spot = c + Offset(-d.r * 0.38f, -d.r * 0.42f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), center = spot, radius = d.r * 0.3f),
                    radius = d.r * 0.3f,
                    center = spot,
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name $version · Tropfen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    merges >= 20 -> "Ein Meer aus Glas! 💧"
                    merges > 0 -> "$merges Tropfen zusammengeflossen"
                    else -> "Antippen · Tropfen greifen und werfen"
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}

/** One silk ribbon: where its head is and the path it has left. */
private class Ribbon(val color: Color, val follow: Float, val width: Float) {
    var head = Offset.Unspecified
    val trail = ArrayDeque<Offset>()
}

/** Where the finger is on the silk (not state: the frame loop reads it). */
private class SilkTouch {
    var at = Offset.Unspecified
    var spun = 0f
    var area = Size.Zero
}

/**
 * Hearth 13's egg, "Seide": ribbons of silk in Claude's colors follow your finger, soft and
 * smooth – Hearth 13 is the butter-smooth one. Left alone they dance a figure of eight; spin
 * enough silk and Clawd comes to ride it.
 */
@Composable
internal fun SilkEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val ribbons = remember { AiFluidColors.mapIndexed { i, c -> Ribbon(c, 0.2f - i * 0.03f, 16f - i * 2f) } }
    val touch = remember { SilkTouch() }
    var now by remember { mutableLongStateOf(0L) }
    var riding by remember { mutableStateOf(false) }
    val clawd = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 16f else (t - last).coerceIn(1L, 50L).toFloat()
                last = t
                val w = touch.area.width
                val h = touch.area.height
                if (w > 0f) {
                    val sec = t / 1000f
                    ribbons.forEachIndexed { i, r ->
                        val target = if (touch.at.isSpecified) {
                            touch.at
                        } else {
                            Offset(
                                w / 2f + w * 0.32f * sin(sec * 0.9f - i * 0.35f),
                                h * 0.42f + h * 0.14f * sin(sec * 1.8f - i * 0.7f),
                            )
                        }
                        // Frame-rate independent easing: the same silk at 60 or 120 Hz.
                        val k = 1f - (1f - r.follow).pow(dt / 16f)
                        val head = if (r.head.isSpecified) r.head + (target - r.head) * k else target
                        if (i == 0 && r.head.isSpecified && touch.at.isSpecified) touch.spun += (head - r.head).getDistance()
                        r.head = head
                        r.trail.addLast(head)
                        while (r.trail.size > 36) r.trail.removeFirst()
                    }
                    if (!riding && touch.spun > h * 8f) {
                        riding = true
                        scope.launch { clawd.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 180f)) }
                    }
                }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF120A2A), Color(0xFF1C0F3A), Color(0xFF07040F))))
            .onSizeChanged { touch.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touch.at = down.position
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { if (it.pressed) touch.at = it.position }
                    } while (event.changes.any { it.pressed })
                    touch.at = Offset.Unspecified
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The version, breathing softly behind the silk.
        Text(
            version,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB494), Color(0xFFFF6FB5), Color(0xFF8E6BFF), Color(0xFF3E91FF))),
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
            ),
            modifier = Modifier.graphicsLayer {
                val b = 1f + 0.03f * sin(now / 900f)
                scaleX = b
                scaleY = b
                alpha = 0.9f
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L) return@Canvas
            ribbons.forEach { r ->
                val pts = r.trail
                val n = pts.size
                for (j in 1 until n) {
                    val f = j / n.toFloat()
                    drawLine(
                        r.color.copy(alpha = 0.9f * f),
                        pts[j - 1],
                        pts[j],
                        strokeWidth = r.width.dp.toPx() * (0.2f + 0.8f * f),
                        cap = StrokeCap.Round,
                    )
                }
                if (n > 0) {
                    val glow = 36.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(listOf(r.color.copy(alpha = 0.45f), Color.Transparent), center = pts[n - 1], radius = glow),
                        radius = glow,
                        center = pts[n - 1],
                    )
                }
            }
        }
        // Clawd, riding the first ribbon once there's silk enough.
        if (riding) {
            Clawd(
                Modifier
                    .align(Alignment.TopStart)
                    .size(width = 64.dp, height = 54.dp)
                    .graphicsLayer {
                        val t = now
                        val head = ribbons[0].head
                        if (head.isSpecified) {
                            translationX = head.x - 32.dp.toPx()
                            translationY = head.y - 64.dp.toPx() + sin(t / 160f) * 3.dp.toPx()
                        }
                        scaleX = clawd.value
                        scaleY = clawd.value
                    },
                mood = ClawdMood.Flip,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name $version · Seide", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (riding) "Butterweich – Clawd surft auf deiner Seide! 🧈" else "Zieh den Finger übers Display",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}

/** A flame rising off the Claude star. */
private class Flame(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val size: Float)

private val FireColors = listOf(Color(0xFFFFF3B0), Color(0xFFFFB347), Color(0xFFD97757), Color(0xFFFF6FB5), Color(0xFF8E6BFF))

/**
 * ClaudeOS 2.0's egg, "Blaze": the Claude star burns, flames rising off it. Swipe anywhere to
 * fan the fire; when it's hot enough, the Clawds come dancing round the blaze.
 */
@Composable
internal fun BlazeEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val flames = remember { mutableStateListOf<Flame>() }
    var now by remember { mutableLongStateOf(0L) }
    var heat by remember { mutableStateOf(0.15f) }
    var fanned by remember { mutableIntStateOf(0) }
    var blaze by remember { mutableStateOf(false) }
    var centre by remember { mutableStateOf(Offset.Zero) }

    fun spawn(x: Float, y: Float, count: Int, t: Long) {
        repeat(count) {
            flames += Flame(
                x + (Math.random().toFloat() - 0.5f) * 40f,
                y + (Math.random().toFloat() - 0.5f) * 20f,
                (Math.random().toFloat() - 0.5f) * 80f,
                -(160f + Math.random().toFloat() * 260f) * (0.6f + heat),
                t,
                10f + Math.random().toFloat() * 16f * (0.6f + heat),
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                now = t
                // The star keeps burning by itself, more the hotter it is.
                if (centre != Offset.Zero) spawn(centre.x, centre.y, (1 + heat * 4).toInt(), t)
                flames.removeAll { t - it.born > 1600 }
                while (flames.size > 420) flames.removeAt(0)
                heat = (heat * 0.996f).coerceAtLeast(0.15f)
                if (!blaze && fanned > 260) {
                    blaze = true
                    EasterEggs.find(context, "claudeos")
                }
            }
        }
    }
    val dance by rememberInfiniteTransition(label = "blaze").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "dance",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0605), Color(0xFF0B0302), Color(0xFF000000))))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    spawn(change.position.x, change.position.y, 3, now)
                    fanned += 1
                    heat = (heat + 0.012f).coerceAtMost(1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            centre = Offset(size.width / 2f, size.height / 2f + 40.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFF8A3D).copy(alpha = 0.25f + 0.45f * heat), Color.Transparent), center = centre, radius = size.minDimension * (0.4f + 0.3f * heat)),
                radius = size.minDimension * (0.4f + 0.3f * heat),
                center = centre,
            )
            flames.forEach { f ->
                val age = (now - f.born) / 1000f
                if (age < 0f) return@forEach
                val p = (age / 1.6f).coerceIn(0f, 1f)
                val color = FireColors[(p * (FireColors.size - 1)).roundToInt().coerceIn(0, FireColors.size - 1)]
                drawCircle(
                    color.copy(alpha = (1f - p) * 0.8f),
                    radius = f.size * (1f - p * 0.7f),
                    center = Offset(f.x + f.vx * age + sin(age * 9f + f.x) * 6f, f.y + f.vy * age),
                )
            }
        }
        ClaudeSpark(
            Color(0xFFFFB347),
            Modifier
                .padding(top = 80.dp)
                .size(150.dp)
                .graphicsLayer {
                    val s = 1f + 0.15f * heat + 0.04f * sin(now / 120f)
                    scaleX = s
                    scaleY = s
                },
        )
        if (blaze) {
            listOf(ClawdMood.Dance, ClawdMood.Flip, ClawdMood.Jump, ClawdMood.Love, ClawdMood.Dance, ClawdMood.Wave).forEachIndexed { i, m ->
                Clawd(
                    Modifier
                        .size(width = 52.dp, height = 44.dp)
                        .graphicsLayer {
                            val a = dance + i * (2 * Math.PI / 6).toFloat()
                            translationX = cos(a) * 140.dp.toPx()
                            translationY = sin(a) * 90.dp.toPx() + 80.dp.toPx()
                        },
                    mood = m,
                )
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ClaudeOs.full,
                style = TextStyle(brush = Brush.linearGradient(FireColors), fontSize = 40.sp, fontWeight = FontWeight.Bold),
            )
            Text("Codename „${ClaudeOs.CODENAME}“", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (blaze) "Blaze! Die Clawds tanzen ums Feuer 🔥" else "Wisch übers Display und fach das Feuer an",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EggClose(onClose: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(12.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
    }
}
