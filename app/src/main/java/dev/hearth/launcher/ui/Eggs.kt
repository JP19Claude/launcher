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
 * glass, Hearth UI 14 the prism; ClaudeOS 1 had "Ember" (the spinning star), ClaudeOS 2
 * "Blaze" (the fire), ClaudeOS 3 has "Nova" (the supernova). New big versions bring new ones.
 */

/** "12.5" → 12.5 (only the first two numbers count). */
internal fun versionNumber(version: String): Float =
    version.split('.').take(2).joinToString(".").toFloatOrNull() ?: 0f

/** ClaudeOS's egg for the ClaudeOS that's running. */
@Composable
internal fun ClaudeOsVersionEgg(onClose: () -> Unit) {
    val number = versionNumber(ClaudeOs.VERSION)
    when {
        number >= 3f -> NovaEgg(onClose)
        number >= 2f -> BlazeEgg(onClose)
        else -> ClaudeOsEgg(onClose)
    }
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

/** Where the light comes from and what the prism makes of it, worked out once per frame. */
private class PrismScene {
    var area = Size.Zero
    /** The finger's (or last finger's) spot; unspecified until the first touch. */
    var finger = Offset.Unspecified
    var source = Offset.Zero
    var center = Offset.Zero
    var entry = Offset.Zero
    var exit = Offset.Zero
    val rays = FloatArray(5)
    var reach = 0f
}

/** Where the five glass orbs hang, as fractions of the screen. */
private val PrismOrbs = listOf(
    Offset(0.18f, 0.13f),
    Offset(0.52f, 0.08f),
    Offset(0.86f, 0.17f),
    Offset(0.9f, 0.5f),
    Offset(0.78f, 0.8f),
)

/**
 * Hearth UI 14's egg, "Prisma": a beam of light runs from your finger into a glass prism and
 * comes out fanned into Claude's colors. Drag to aim, tap the prism to turn it – light up all
 * five glass orbs for the full spectrum.
 */
@Composable
internal fun PrismEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val scene = remember { PrismScene() }
    val turn = remember { Animatable(0f) }
    val lit = remember { mutableStateListOf<Color?>().apply { repeat(PrismOrbs.size) { add(null) } } }
    var now by remember { mutableLongStateOf(0L) }
    val full = lit.all { it != null }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                val w = scene.area.width
                val h = scene.area.height
                if (w > 0f) {
                    val sec = t / 1000f
                    val c = Offset(w / 2f, h * 0.46f)
                    val inner = w * 0.36f / (2f * kotlin.math.sqrt(3f))
                    // Before the first touch the light wanders along the left edge.
                    val s = if (scene.finger.isSpecified) scene.finger else Offset(w * 0.06f, h * 0.46f + sin(sec * 0.6f) * h * 0.2f)
                    val dx = c.x - s.x
                    val dy = c.y - s.y
                    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val inAngle = kotlin.math.atan2(dy, dx)
                    // How far the light bends depends on how the prism stands to it.
                    val bend = 0.42f + 0.22f * sin(3f * (turn.value - inAngle))
                    val out = inAngle + bend
                    scene.center = c
                    scene.source = s
                    scene.entry = c - Offset(dx / len, dy / len) * (inner * 0.6f)
                    scene.exit = c + Offset(cos(out), sin(out)) * (inner * 0.6f)
                    for (i in 0 until 5) scene.rays[i] = out + (i - 2) * 0.075f
                    scene.reach = kotlin.math.hypot(w, h) * 1.2f
                    // Which orbs the colored beams hit.
                    val orbR = 26.dp.toPx(context)
                    PrismOrbs.forEachIndexed { o, frac ->
                        val at = Offset(frac.x * w, frac.y * h)
                        // The first color that reaches an orb lights it (and it keeps glowing).
                        var hit: Color? = null
                        for (i in 0 until 5) {
                            val dir = Offset(cos(scene.rays[i]), sin(scene.rays[i]))
                            val v = at - scene.exit
                            val along = v.x * dir.x + v.y * dir.y
                            val across = kotlin.math.abs(v.x * dir.y - v.y * dir.x)
                            if (hit == null && along > 0f && across < orbR) hit = AiFluidColors[i]
                        }
                        if (hit != null && lit[o] != hit) lit[o] = hit
                    }
                }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070A1A), Color(0xFF110A26), Color(0xFF050508))))
            .onSizeChanged { scene.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val r = size.width * 0.36f / kotlin.math.sqrt(3f)
                    if ((down.position - scene.center).getDistance() < r) {
                        // A tap on the prism turns it by a sixth.
                        scope.launch { turn.animateTo(turn.targetValue + (Math.PI / 3).toFloat(), spring(dampingRatio = 0.55f, stiffness = 260f)) }
                    } else {
                        scene.finger = down.position
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed && (change.position - scene.center).getDistance() >= r) scene.finger = change.position
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The version, faint behind everything.
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.06f),
            fontSize = 220.sp,
            fontWeight = FontWeight.Black,
        )
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L || scene.area.width <= 0f) return@Canvas
            val beam = 6.dp.toPx()
            // The white light coming in.
            drawLine(Color.White.copy(alpha = 0.16f), scene.source, scene.entry, strokeWidth = beam * 3.2f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.92f), scene.source, scene.entry, strokeWidth = beam, cap = StrokeCap.Round)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), center = scene.source, radius = 30.dp.toPx()),
                radius = 30.dp.toPx(),
                center = scene.source,
            )
            // The colors going out, fading with the distance.
            for (i in 0 until 5) {
                val c = AiFluidColors[i]
                val end = scene.exit + Offset(cos(scene.rays[i]), sin(scene.rays[i])) * scene.reach
                drawLine(
                    Brush.linearGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.04f)), start = scene.exit, end = end),
                    scene.exit,
                    end,
                    strokeWidth = beam * 3f,
                )
                drawLine(
                    Brush.linearGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.3f)), start = scene.exit, end = end),
                    scene.exit,
                    end,
                    strokeWidth = beam,
                )
            }
            // The prism: a triangle of glass with a bright rim.
            val circum = size.width * 0.36f / kotlin.math.sqrt(3f)
            val path = androidx.compose.ui.graphics.Path()
            for (k in 0 until 3) {
                val a = turn.value - (Math.PI / 2).toFloat() + k * (2 * Math.PI / 3).toFloat()
                val p = scene.center + Offset(cos(a), sin(a)) * circum
                if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.26f), Color(0xFF8E6BFF).copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f)),
                    start = scene.center - Offset(circum, circum),
                    end = scene.center + Offset(circum, circum),
                ),
            )
            drawPath(path, Color.White.copy(alpha = 0.75f), style = Stroke(2.dp.toPx()))
            // The orbs, glowing in the color that reached them.
            PrismOrbs.forEachIndexed { o, frac ->
                val at = Offset(frac.x * size.width, frac.y * size.height)
                val r = 22.dp.toPx()
                val color = lit[o]
                if (color != null) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center = at, radius = r * 2.6f), radius = r * 2.6f, center = at)
                }
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), (color ?: Color.White).copy(alpha = if (color != null) 0.55f else 0.14f)), center = at, radius = r),
                    radius = r,
                    center = at,
                )
                drawCircle(Color.White.copy(alpha = 0.6f), radius = r, center = at, style = Stroke(1.5.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.18f, center = at + Offset(-r * 0.35f, -r * 0.4f))
            }
        }
        if (full) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
                    .size(width = 80.dp, height = 68.dp),
                mood = ClawdMood.Love,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name $version · Prisma", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (full) "Volles Spektrum! 🌈" else "Zieh das Licht · tippe aufs Prisma, um es zu drehen · ${lit.count { it != null }}/${PrismOrbs.size}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}

/** dp → px outside a draw scope. */
private fun androidx.compose.ui.unit.Dp.toPx(context: android.content.Context): Float = value * context.resources.displayMetrics.density

/** One spark of the supernova: where it is, where it flies, and where it settles in the star. */
private class NovaBit(var x: Float, var y: Float, var vx: Float, var vy: Float, val tx: Float, val ty: Float, val color: Color)

/** A little flare off the star when it's tapped. */
private class NovaFlare(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val color: Color)

/** The state of the star between frames. */
private class NovaSky {
    var area = Size.Zero
    var boom = -1L
    var pressed = false
    val bits = ArrayList<NovaBit>()
    val flares = ArrayList<NovaFlare>()
}

/**
 * ClaudeOS 3.0's egg, "Nova": a young star in the dark. Tap it to feed it, hold to charge it –
 * at full energy it goes supernova: a flash, a shock wave, sparks in all of Claude's colors,
 * which then come back together as Claude's own star.
 */
@Composable
internal fun NovaEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val sky = remember { NovaSky() }
    val energy = remember { Animatable(0.12f) }
    var now by remember { mutableLongStateOf(0L) }
    var formed by remember { mutableStateOf(false) }
    var boomed by remember { mutableStateOf(false) }
    val stars = remember { List(90) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat() * 6f) } }

    fun explode(t: Long) {
        val w = sky.area.width
        val h = sky.area.height
        val cx = w / 2f
        val cy = h * 0.45f
        sky.boom = t
        boomed = true
        sky.bits.clear()
        val rays = 10
        val count = 180
        val logo = w * 0.3f
        for (k in 0 until count) {
            val angle = (Math.random() * 2 * Math.PI).toFloat()
            val speed = 260f + Math.random().toFloat() * 900f
            // Where it settles: along one of the star's rays, the rays a little uneven like Claude's.
            val ray = k % rays
            val along = (k / rays + 1).toFloat() / (count / rays)
            val rayAngle = ray * (2 * Math.PI / rays).toFloat() - (Math.PI / 2).toFloat()
            val rayLength = logo * (0.75f + 0.25f * ((ray * 7) % 4) / 3f)
            val color = if (k % 5 == 0) Color.White else AiFluidColors[k % AiFluidColors.size]
            sky.bits += NovaBit(cx, cy, cos(angle) * speed, sin(angle) * speed, cx + cos(rayAngle) * rayLength * along, cy + sin(rayAngle) * rayLength * along, color)
        }
    }

    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0.016f else (t - last).coerceIn(1L, 40L) / 1000f
                last = t
                if (sky.area.width > 0f) {
                    if (sky.boom < 0) {
                        // Holding charges it; left alone it cools down a little.
                        if (sky.pressed) {
                            scope.launch { energy.snapTo((energy.value + 0.35f * dt).coerceAtMost(1f)) }
                        } else if (!energy.isRunning) {
                            scope.launch { energy.snapTo((energy.value - 0.03f * dt).coerceAtLeast(0.12f)) }
                        }
                        if (energy.value >= 0.995f) explode(t)
                    } else {
                        val age = (t - sky.boom) / 1000f
                        val drag = kotlin.math.exp(-1.8f * dt)
                        val pull = 1f - kotlin.math.exp(-3.6f * dt)
                        sky.bits.forEach { b ->
                            if (age < 1.2f) {
                                b.vx *= drag
                                b.vy *= drag
                                b.x += b.vx * dt
                                b.y += b.vy * dt
                            } else {
                                b.x += (b.tx - b.x) * pull
                                b.y += (b.ty - b.y) * pull
                            }
                        }
                        if (!formed && age > 3.2f) {
                            formed = true
                            EasterEggs.find(context, "claudeos")
                        }
                    }
                    sky.flares.removeAll { t - it.born > 800 }
                }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02020A), Color(0xFF0A0618), Color(0xFF000000))))
            .onSizeChanged { sky.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val t = now
                    if (sky.boom >= 0 && formed) {
                        // A new star.
                        sky.boom = -1L
                        sky.bits.clear()
                        formed = false
                        boomed = false
                        scope.launch { energy.snapTo(0.12f) }
                    } else if (sky.boom < 0) {
                        val cx = size.width / 2f
                        val cy = size.height * 0.45f
                        repeat(10) {
                            val a = (Math.random() * 2 * Math.PI).toFloat()
                            val v = 120f + Math.random().toFloat() * 260f
                            sky.flares += NovaFlare(cx, cy, cos(a) * v, sin(a) * v, t, AiFluidColors[(Math.random() * AiFluidColors.size).toInt().coerceIn(0, AiFluidColors.size - 1)])
                        }
                        scope.launch { energy.animateTo((energy.value + 0.14f).coerceAtMost(1f), spring(dampingRatio = 0.5f, stiffness = 300f)) }
                    }
                    sky.pressed = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    sky.pressed = false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = now
            // Twinkling stars.
            val px = 2.5.dp.toPx()
            stars.forEach { (x, y, phase) ->
                val a = 0.2f + 0.7f * ((sin(t / 700f + phase) + 1f) / 2f)
                drawRect(Color.White.copy(alpha = a), Offset(x * size.width, y * size.height), Size(px, px))
            }
            val c = Offset(size.width / 2f, size.height * 0.45f)
            if (sky.boom < 0) {
                // The young star: from Claude's orange through gold to blue-white as it fills.
                val e = energy.value.coerceIn(0f, 1f)
                val color = if (e < 0.5f) {
                    androidx.compose.ui.graphics.lerp(Color(0xFFD97757), Color(0xFFFFD27A), e * 2f)
                } else {
                    androidx.compose.ui.graphics.lerp(Color(0xFFFFD27A), Color(0xFFE8F1FF), (e - 0.5f) * 2f)
                }
                val pulse = 1f + 0.06f * sin(t / (260f - 150f * e))
                val r = size.width * (0.06f + 0.1f * e) * pulse
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 3.4f), radius = r * 3.4f, center = c)
                drawCircle(Brush.radialGradient(listOf(Color.White, color), center = c, radius = r), radius = r, center = c)
                // A four-pointed sparkle turning slowly.
                val spin = t / 2400f
                for (k in 0 until 4) {
                    val a = spin + k * (Math.PI / 2).toFloat()
                    drawLine(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), start = c, end = c + Offset(cos(a), sin(a)) * r * 2.6f),
                        c,
                        c + Offset(cos(a), sin(a)) * r * 2.6f,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // The flares from taps.
                sky.flares.forEach { f ->
                    val age = (t - f.born) / 1000f
                    if (age >= 0f) {
                        val at = Offset(f.x + f.vx * age, f.y + f.vy * age)
                        drawCircle(f.color.copy(alpha = (1f - age / 0.8f).coerceIn(0f, 1f)), radius = 3.dp.toPx(), center = at)
                    }
                }
            } else {
                val age = (t - sky.boom) / 1000f
                // The flash and the shock wave.
                if (age < 0.35f) drawRect(Color.White.copy(alpha = (1f - age / 0.35f).coerceIn(0f, 1f) * 0.9f))
                if (age < 1.1f) {
                    val p = age / 1.1f
                    drawCircle(
                        Color(0xFFFFE3D3).copy(alpha = (1f - p) * 0.7f),
                        radius = kotlin.math.hypot(size.width, size.height) * p,
                        center = c,
                        style = Stroke((14.dp.toPx() * (1f - p)).coerceAtLeast(1f)),
                    )
                }
                // The sparks, and once they've settled, the glow of the new star.
                if (formed) {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFD97757).copy(alpha = 0.35f), Color.Transparent), center = c, radius = size.width * 0.42f), radius = size.width * 0.42f, center = c)
                }
                val dot = 3.dp.toPx()
                sky.bits.forEach { b -> drawCircle(b.color, radius = dot, center = Offset(b.x, b.y)) }
            }
        }
        if (formed) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
                    .size(width = 80.dp, height = 68.dp),
                mood = ClawdMood.Jump,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${ClaudeOs.full} · ${ClaudeOs.CODENAME}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    formed -> "Ein neuer Stern: Claudes Stern ✨ · Tippen für eine neue Nova"
                    boomed -> "Supernova!"
                    else -> "Tippe den Stern an oder halte ihn, bis er zur Supernova wird"
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}

/** A comet: where it is, how fast, and the trail it leaves. */
private class Comet(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color) {
    val trail = ArrayDeque<Offset>()
}

/** The finger's slingshot between frames. */
private class Sling {
    var area = Size.Zero
    var from = Offset.Unspecified
    var to = Offset.Unspecified
}

/**
 * Hearth UI 14.5's egg, "Kometen": pull back like a slingshot and let go – a comet flies off
 * and the Hearth star in the middle pulls it into orbit. Keep five comets circling at once.
 */
@Composable
internal fun CometEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val comets = remember { mutableListOf<Comet>() }
    val sling = remember { Sling() }
    var now by remember { mutableLongStateOf(0L) }
    var orbiting by remember { mutableIntStateOf(0) }
    var thrown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0.016f else (t - last).coerceIn(1L, 40L) / 1000f
                last = t
                val w = sling.area.width
                val h = sling.area.height
                if (w > 0f) {
                    val c = Offset(w / 2f, h * 0.45f)
                    val pull = 0.12f * w * w * w
                    comets.forEach { k ->
                        val dx = c.x - k.x
                        val dy = c.y - k.y
                        val d2 = (dx * dx + dy * dy).coerceAtLeast(900f)
                        val d = kotlin.math.sqrt(d2)
                        val a = pull / d2
                        k.vx += dx / d * a * dt
                        k.vy += dy / d * a * dt
                        k.x += k.vx * dt
                        k.y += k.vy * dt
                        k.trail.addLast(Offset(k.x, k.y))
                        while (k.trail.size > 40) k.trail.removeFirst()
                    }
                    // Comets that fell into the star or flew far away are gone.
                    comets.removeAll { k ->
                        val d = kotlin.math.hypot(k.x - c.x, k.y - c.y)
                        d < w * 0.05f || d > kotlin.math.hypot(w, h) * 1.5f
                    }
                    val circling = comets.count { k -> kotlin.math.hypot(k.x - c.x, k.y - c.y) < w * 0.7f }
                    if (circling != orbiting) orbiting = circling
                }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF03030C), Color(0xFF0D0820), Color(0xFF020205))))
            .onSizeChanged { sling.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    sling.from = down.position
                    sling.to = down.position
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { if (it.pressed) sling.to = it.position }
                    } while (event.changes.any { it.pressed })
                    // Let go: the comet flies the other way, faster the farther it was pulled.
                    val from = sling.from
                    val to = sling.to
                    if (comets.size < 12) {
                        comets += Comet(from.x, from.y, (from.x - to.x) * 2.4f, (from.y - to.y) * 2.4f, AiFluidColors[thrown % AiFluidColors.size])
                        thrown++
                    }
                    sling.from = Offset.Unspecified
                    sling.to = Offset.Unspecified
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(HearthUi.major(version), color = Color.White.copy(alpha = 0.05f), fontSize = 200.sp, fontWeight = FontWeight.Black)
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L) return@Canvas
            val c = Offset(size.width / 2f, size.height * 0.45f)
            // The Hearth star.
            val r = size.width * 0.06f * (1f + 0.05f * sin(now / 300f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB494).copy(alpha = 0.5f), Color.Transparent), center = c, radius = r * 4f), radius = r * 4f, center = c)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD97757)), center = c, radius = r), radius = r, center = c)
            // The comets and their tails.
            comets.forEach { k ->
                val n = k.trail.size
                for (j in 1 until n) {
                    val f = j / n.toFloat()
                    drawLine(k.color.copy(alpha = 0.8f * f), k.trail.elementAt(j - 1), k.trail.elementAt(j), strokeWidth = 6.dp.toPx() * f, cap = StrokeCap.Round)
                }
                drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(k.x, k.y))
            }
            // The slingshot while pulling.
            if (sling.from.isSpecified && sling.to.isSpecified) {
                drawLine(Color.White.copy(alpha = 0.5f), sling.from, sling.to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 6.dp.toPx(), center = sling.from)
            }
        }
        if (orbiting >= 5) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
                    .size(width = 80.dp, height = 68.dp),
                mood = ClawdMood.Dance,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$name $version · Kometen", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (orbiting >= 5) "Fünf Kometen auf der Bahn! ☄️" else "Zurückziehen und loslassen · $orbiting/5 auf der Bahn",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        EggClose(onClose)
    }
}
