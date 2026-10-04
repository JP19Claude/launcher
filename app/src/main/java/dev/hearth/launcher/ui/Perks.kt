package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.Perk
import dev.hearth.launcher.data.has
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * OMEGA UI 18 – the perks of the Clawd Illuminati, and Claude Mythos's takeover: what lies
 * between the wallpaper and the home screen (back layers), what lies over it (front layers),
 * and what moves the home screen itself.
 */

private const val TAU = (2 * PI).toFloat()

/** One small thing drifting through a layer: its place, speed (whole rounds a cycle), size. */
private class Mote(val x: Float, val speed: Int, val offset: Float, val size: Float, val sway: Float, val tone: Int)

private fun motes(n: Int, seed: Int): List<Mote> {
    val r = Random(seed)
    return List(n) { Mote(r.nextFloat(), 1 + r.nextInt(3), r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 2f, r.nextInt(6)) }
}

private fun frac(v: Float) = v - kotlin.math.floor(v)

/** Draws Clawd into the box at [at] ([w] × [h]), walking with [step], mirrored when [left]. */
private fun DrawScope.clawdAt(at: Offset, w: Float, h: Float, color: Color, step: Float, arm: Float, left: Boolean) {
    val full = size
    translate(at.x, at.y) {
        scale(if (left) -1f else 1f, 1f, pivot = Offset(w / 2f, h / 2f)) {
            drawContext.size = Size(w, h)
            drawClawd(color, blink = false, bob = 0f, legPhase = step, armLift = arm, spin = 0f, hearts = 0f)
            drawContext.size = full
        }
    }
}

private val AutumnColors = listOf(Color(0xFFD9502F), Color(0xFFE8A33D), Color(0xFFB8362A), Color(0xFFF2C14E), Color(0xFF9E4A1F), Color(0xFFE57A3A))
private val SwarmColors = listOf(Color(0xFF3D7BFF), Color(0xFFFFC94D), Color(0xFF8FE3C0))

/**
 * Behind the home screen's apps and widgets: stars, aurora, the Ω watermark, the drifting
 * perks (snow, fireflies, bubbles, leaves, matrix, rubies), shooting stars, the battery's
 * aura – and with Claude Mythos a giant golden Clawd watching over everything.
 */
@Composable
internal fun PerkBackLayers(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val any = Perk.entries.any { settings.has(it) && it.group != "Farbe" } || settings.mythos
    if (!any) return
    val flow = flowPhase(60_000, settings.animations)
    val measurer = rememberTextMeasurer()
    val omegaBig = remember(measurer) { measurer.measure("Ω", TextStyle(fontSize = 300.sp, fontWeight = FontWeight.Black)) }
    val matrixGlyphs = remember(measurer) {
        "ｱｲｳｴｵｶｷｸｹｺｻｼ01Ω".map { measurer.measure(it.toString(), TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) }
    }
    val snow = remember { motes(70, 1) }
    val flies = remember { motes(22, 2) }
    val bubbles = remember { motes(16, 3) }
    val leaves = remember { motes(18, 4) }
    val stars = remember { motes(90, 5) }
    val rain = remember { motes(16, 6) }
    val rubies = remember { motes(20, 7) }
    val (battery, charging) = if (settings.has(Perk.BatteryAura)) rememberBatteryLevel() else (100 to false)
    // Shooting stars: now and then one streak across the sky.
    val comet = remember { Animatable(0f) }
    var cometSeed by remember { mutableStateOf(0) }
    LaunchedEffect(settings.has(Perk.Comets), settings.animations) {
        if (!settings.has(Perk.Comets) || !settings.animations) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(5_000, 13_000))
            cometSeed++
            comet.snapTo(0f)
            comet.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
            comet.snapTo(0f)
        }
    }
    Canvas(modifier) {
        val t = (flow?.invoke() ?: 1f) / TAU
        val w = size.width
        val h = size.height
        if (settings.mythos) {
            // Claude Mythos: a giant golden Clawd watches over everything.
            val cw = w * 0.9f
            val ch = cw * (11f / 14f)
            clawdAt(Offset((w - cw) / 2f, h * 0.36f - ch / 2f), cw, ch, IlluminatiGold.copy(alpha = 0.08f), 0f, sin(t * TAU * 4f) * 0.3f, false)
        }
        if (settings.has(Perk.Stars)) {
            stars.forEachIndexed { i, m ->
                val tw = 0.5f + 0.5f * sin(t * TAU * (2 + m.speed) + i)
                drawCircle(Color.White.copy(alpha = 0.15f + 0.6f * tw * m.size), radius = (0.6f + 1.4f * m.size).dp.toPx(), center = Offset(m.x * w, m.offset * h * 0.75f))
            }
        }
        if (settings.has(Perk.Aurora)) {
            val bands = listOf(Color(0xFF6CFFB0), Color(0xFF3EE0C8), Color(0xFF8E6BFF))
            bands.forEachIndexed { i, c ->
                val path = Path()
                val base = h * (0.12f + 0.06f * i)
                val amp = h * 0.05f
                path.moveTo(0f, 0f)
                var x = 0f
                while (x <= w) {
                    path.lineTo(x, base + amp * sin(x / w * TAU * 1.3f + t * TAU * (1 + i) + i * 2f))
                    x += w / 24f
                }
                path.lineTo(w, 0f)
                path.close()
                drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.22f), Color.Transparent), startY = base - amp * 3f, endY = base + amp * 1.5f))
            }
        }
        if (settings.has(Perk.OmegaMark)) {
            drawText(omegaBig, color = Color.White, topLeft = Offset((w - omegaBig.size.width) / 2f, (h - omegaBig.size.height) / 2.4f), alpha = 0.06f)
        }
        if (settings.has(Perk.Snow)) {
            snow.forEach { m ->
                val p = frac(t * m.speed + m.offset)
                val at = Offset(m.x * w + sin(p * TAU * m.sway + m.offset * 9f) * 12.dp.toPx(), p * (h + 20f) - 10f)
                drawCircle(Color.White.copy(alpha = 0.75f), radius = (1.4f + 2.2f * m.size).dp.toPx(), center = at)
            }
        }
        if (settings.has(Perk.Fireflies)) {
            flies.forEach { m ->
                val a = t * TAU * m.speed + m.offset * 10f
                val at = Offset(w * (m.x + 0.08f * sin(a)), h * (0.2f + 0.65f * m.size + 0.06f * cos(a * 1.3f)))
                val glow = 0.3f + 0.5f * (0.5f + 0.5f * sin(a * 3f + m.offset * 20f))
                val r = 10.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF5FF9C).copy(alpha = glow), Color.Transparent), center = at, radius = r), radius = r, center = at)
                drawCircle(Color(0xFFFFFFE0).copy(alpha = glow), radius = 1.6.dp.toPx(), center = at)
            }
        }
        if (settings.has(Perk.Bubbles)) {
            bubbles.forEach { m ->
                val p = frac(t * m.speed + m.offset)
                val r = (8f + 16f * m.size).dp.toPx()
                val at = Offset(m.x * w + sin(p * TAU * m.sway) * 14.dp.toPx(), h * 1.05f - p * h * 1.2f)
                val fade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                drawCircle(
                    Brush.sweepGradient(listOf(Color(0xFF9CF0FF), Color(0xFFFF9CE6), Color(0xFFFFF59C), Color(0xFF9CF0FF)), center = at),
                    radius = r,
                    center = at,
                    alpha = 0.5f * fade,
                    style = Stroke(1.4.dp.toPx()),
                )
                drawCircle(Color.White.copy(alpha = 0.5f * fade), radius = r * 0.18f, center = at + Offset(-r * 0.4f, -r * 0.4f))
            }
        }
        if (settings.has(Perk.Leaves)) {
            leaves.forEach { m ->
                val p = frac(t * m.speed + m.offset)
                val at = Offset(m.x * w + sin(p * TAU * m.sway + m.offset * 7f) * 30.dp.toPx(), p * (h + 40f) - 20f)
                val lw = (10f + 8f * m.size).dp.toPx()
                rotate(p * 720f * (if (m.tone % 2 == 0) 1f else -1f), at) {
                    drawOval(AutumnColors[m.tone % AutumnColors.size].copy(alpha = 0.85f), at - Offset(lw / 2f, lw / 4f), Size(lw, lw / 2f))
                }
            }
        }
        if (settings.has(Perk.Matrix)) {
            val gh = matrixGlyphs.first().size.height.toFloat()
            rain.forEachIndexed { i, m ->
                val p = frac(t * (m.speed + 1) + m.offset)
                val x = (i + 0.5f) / rain.size * w
                val head = p * (h + gh * 12f)
                for (k in 0 until 12) {
                    val y = head - k * gh
                    if (y < -gh || y > h) continue
                    val g = matrixGlyphs[(i * 7 + k * 3 + (head / gh).toInt()) % matrixGlyphs.size]
                    drawText(g, color = if (k == 0) Color(0xFFD8FFD8) else Color(0xFF39FF6A), topLeft = Offset(x, y), alpha = (1f - k / 12f) * 0.55f)
                }
            }
        }
        if (settings.has(Perk.Rubies)) {
            rubies.forEach { m ->
                val p = frac(t * m.speed + m.offset)
                val at = Offset(m.x * w + sin(p * TAU * m.sway) * 10.dp.toPx(), p * (h + 30f) - 15f)
                val r = (4f + 5f * m.size).dp.toPx()
                val twinkle = 0.6f + 0.4f * abs(sin(p * TAU * 3f + m.offset * 5f))
                val gem = Path().apply {
                    moveTo(at.x, at.y - r)
                    lineTo(at.x + r * 0.7f * twinkle, at.y)
                    lineTo(at.x, at.y + r)
                    lineTo(at.x - r * 0.7f * twinkle, at.y)
                    close()
                }
                drawPath(gem, Brush.linearGradient(listOf(Color(0xFFFF8A9A), Color(0xFFC8102E), Color(0xFF5A0010)), at - Offset(r, r), at + Offset(r, r)), alpha = 0.85f)
                drawCircle(Color.White.copy(alpha = 0.7f * twinkle), radius = r * 0.15f, center = at - Offset(r * 0.15f, r * 0.4f))
            }
        }
        if (settings.has(Perk.Comets) && comet.value > 0f) {
            val r = Random(cometSeed)
            val start = Offset(w * (0.1f + 0.5f * r.nextFloat()), h * (0.05f + 0.2f * r.nextFloat()))
            val dir = Offset(w * 0.5f, h * 0.22f)
            val head = start + dir * comet.value
            val tail = start + dir * (comet.value - 0.25f).coerceAtLeast(0f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White), tail, head), tail, head, strokeWidth = 2.5.dp.toPx())
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = head)
        }
        if (settings.has(Perk.BatteryAura)) {
            val level = (battery.coerceIn(0, 100)) / 100f
            val c = lerp(Color(0xFFFF3B30), Color(0xFF34C759), level)
            val pulse = if (charging) 0.7f + 0.3f * sin(t * TAU * 30f) else 1f
            val at = Offset(w / 2f, h * 1.04f)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.42f * pulse), Color.Transparent), center = at, radius = w * 0.85f), radius = w * 0.85f, center = at)
        }
    }
}

/** Where fingers touched the home screen, for the Funkenspur. */
internal class SparkField {
    class Spark(val at: Offset, val born: Long, val dx: Float, val dy: Float, val tone: Int)
    val sparks = mutableStateListOf<Spark>()
    fun touch(at: Offset) {
        val now = android.os.SystemClock.uptimeMillis()
        repeat(3) {
            val a = Random.nextFloat() * TAU
            val v = 0.05f + Random.nextFloat() * 0.18f
            sparks += Spark(at, now, cos(a) * v, sin(a) * v - 0.06f, Random.nextInt(5))
        }
        while (sparks.size > 150) sparks.removeAt(0)
    }
}

/** Feeds the Funkenspur from every touch on the home screen – without taking any touch away. */
internal fun Modifier.sparkSource(field: SparkField?): Modifier = if (field == null) this else pointerInput(field) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { if (it.pressed) field.touch(it.position) }
        }
    }
}

/**
 * Over the home screen: vignette, film grain, the night's rest, the Clawd swarm and greeting,
 * the Funkenspur – and Claude Mythos's banner. [ground] is where the dock's top edge is.
 */
@Composable
internal fun BoxScope.PerkFrontLayers(settings: LauncherSettings, sparks: SparkField?, ground: Float) {
    val palette = fluidPalette()
    val flow = flowPhase(40_000, settings.animations && settings.has(Perk.Swarm))
    // Night's rest: the hour, checked every minute.
    val hour by produceState(java.time.LocalTime.now().hour, settings.has(Perk.NightRest)) {
        while (true) {
            value = java.time.LocalTime.now().hour
            delay(60_000)
        }
    }
    val grain = remember(settings.has(Perk.Grain)) {
        if (!settings.has(Perk.Grain)) {
            null
        } else {
            val n = 96
            val r = Random(42)
            val px = IntArray(n * n) {
                val v = r.nextInt(256)
                android.graphics.Color.argb(r.nextInt(70), v, v, v)
            }
            val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
            ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
        }
    }
    // The greeting Clawd: every so often he looks in from the left edge and waves.
    val greet = remember { Animatable(0f) }
    LaunchedEffect(settings.has(Perk.Greeting), settings.animations) {
        if (!settings.has(Perk.Greeting) || !settings.animations) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(18_000, 40_000))
            greet.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            delay(2_600)
            greet.animateTo(0f, tween(600))
        }
    }
    var now by remember { mutableLongStateOf(0L) }
    val sparking = sparks != null && sparks.sparks.isNotEmpty()
    LaunchedEffect(sparking) {
        if (!sparking || sparks == null) return@LaunchedEffect
        while (sparks.sparks.isNotEmpty()) {
            withFrameMillis { now = android.os.SystemClock.uptimeMillis() }
            val cutoff = now - 750
            sparks.sparks.removeAll { it.born < cutoff }
        }
    }
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        if (settings.has(Perk.NightRest) && (hour >= 23 || hour < 6)) drawRect(Color.Black.copy(alpha = 0.38f))
        if (settings.has(Perk.Vignette)) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f))
        }
        grain?.let { drawRect(it, alpha = 0.5f) }
        if (settings.has(Perk.Swarm)) {
            val t = (flow?.invoke() ?: 1f) / TAU
            val ch = 30.dp.toPx()
            val cw = ch * (14f / 11f)
            SwarmColors.forEachIndexed { i, c ->
                val a = t * TAU * (i + 1) + i * 2.1f
                val x = w * (0.5f + 0.4f * sin(a)) - cw / 2f
                val goingLeft = cos(a) < 0f
                clawdAt(Offset(x, ground - ch * 0.92f), cw, ch, c, t * 400f + i, sin(a * 3f) * 0.4f, goingLeft)
            }
        }
        if (greet.value > 0.01f) {
            val ch = 64.dp.toPx()
            val cw = ch * (14f / 11f)
            val x = -cw + greet.value * (cw * 0.85f)
            clawdAt(Offset(x, h * 0.42f), cw, ch, ClawdColor, 0f, sin(now / 90f) , false)
        }
        if (sparks != null) {
            sparks.sparks.forEach { s ->
                val age = (now - s.born).coerceAtLeast(0L).toFloat()
                if (age > 750f) return@forEach
                val k = 1f - age / 750f
                val at = s.at + Offset(s.dx * age, s.dy * age + 0.00025f * age * age)
                val c = palette[s.tone % palette.size]
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = k), Color.Transparent), center = at, radius = 9.dp.toPx() * k + 1f), radius = 9.dp.toPx() * k + 1f, center = at)
                drawCircle(Color.White.copy(alpha = k), radius = 1.6.dp.toPx() * k, center = at)
            }
        }
    }
    if (settings.mythos) MythosBanner(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))
}

/** Claude Mythos: a golden banner – the Clawds have taken over – that fades after a moment. */
@Composable
private fun MythosBanner(modifier: Modifier) {
    val show = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        show.animateTo(1f, tween(600))
        delay(5_000)
        show.animateTo(0f, tween(900))
    }
    if (show.value <= 0.01f) return
    Box(
        modifier
            .graphicsLayer { alpha = show.value; translationY = (1f - show.value) * -20.dp.toPx() }
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A0A06), Color(0xFF3A1A08), Color(0xFF1A0A06))))
            .border(1.dp, IlluminatiGold, CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            "◆ CLAUDE MYTHOS · Die Clawds haben übernommen",
            color = Color(0xFFFFE08A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 1.sp,
        )
    }
}

/** Parallax: the home screen shifts against the wallpaper as the phone tilts. */
internal fun Modifier.parallax(on: Boolean): Modifier = if (!on) this else composed {
    val light = LocalGlassLight.current
    graphicsLayer {
        val l = light.value
        translationX = (l.x + 0.45f) * 16.dp.toPx()
        translationY = (l.y + 0.89f) * 16.dp.toPx()
    }
}

/** Icon-Puls: every icon breathes, each a little out of step ([seed]). */
internal fun Modifier.perkPulse(seed: Int): Modifier = composed {
    val s = LocalSettings.current
    if (!s.has(Perk.Pulse)) return@composed this
    val phase = flowPhase(3_400, s.animations) ?: return@composed this
    val off = seed * 0.83f
    graphicsLayer {
        val k = 1f + 0.04f * sin(phase() + off)
        scaleX = k
        scaleY = k
    }
}

/** Riesen-Uhr: grows what it is put on by [factor], keeping the room it needs. */
internal fun Modifier.grow(factor: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, (p.height * factor).roundToInt()) {
        p.placeWithLayer(0, 0) {
            scaleX = factor
            scaleY = factor
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
