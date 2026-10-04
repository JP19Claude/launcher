package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** OMEGA UI's ruby: deep red through bright red to a pink light. */
internal val RubyColors = listOf(Color(0xFF5A0012), Color(0xFFB0102C), Color(0xFFE5243F), Color(0xFFFF6B7D), Color(0xFFFFC2CA))

/** A point on a ring of the cut: [i] of twelve, turned by [turn] steps, at [r] of the size. */
private fun DrawScope.ring(i: Int, r: Float, turn: Float = 0f): Offset {
    val a = (-90.0 + (i + turn) * 30.0) * PI / 180.0
    // A cushion: a little taller than wide, like the game's stone.
    return Offset(size.width / 2f + cos(a).toFloat() * size.width * 0.46f * r, size.height / 2f + sin(a).toFloat() * size.height * 0.48f * r)
}

private fun polygon(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

/** How bright a facet facing [angle] is with the light coming from [light] (both in radians). */
private fun lit(angle: Double, light: Double): Float = (0.5 + 0.5 * cos(angle - light)).toFloat()

private val RubyDeep = Color(0xFF3C000C)
private val RubyDark = Color(0xFF7A0618)
private val RubyMid = Color(0xFFC40F2E)
private val RubyBright = Color(0xFFFF3A50)
private val RubyLight = Color(0xFFFF9AA8)

/**
 * The ruby, seen from above like a cut brilliant (in the spirit of the great red stone of
 * Omega Ruby): a table in the middle, star and bezel facets around it, girdle facets at the
 * rim. Each facet catches the light by where it faces – and the light wanders, so the stone
 * sparkles. Inside it glows like lava.
 */
private fun DrawScope.drawRuby(shine: Float, glow: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val light = (-135.0 + 28.0 * sin(shine * 2.0 * PI)) * PI / 180.0
    val edge = Color(0xFFFFC2CA).copy(alpha = 0.28f)
    val hair = 0.8.dp.toPx()
    // The red light it throws around itself, and its shadow.
    drawCircle(
        Brush.radialGradient(listOf(RubyBright.copy(alpha = 0.42f * glow), Color.Transparent), center = c, radius = size.width * 0.75f),
        radius = size.width * 0.75f,
        center = c,
    )
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = Offset(c.x, size.height * 0.98f), radius = size.width * 0.4f),
        topLeft = Offset(size.width * 0.12f, size.height * 0.9f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.14f),
    )
    val outer = (0 until 12).map { ring(it, 1f) }
    val middle = (0 until 12).map { ring(it, 0.74f, 0.5f) }
    val table = (0 until 12).map { ring(it, 0.42f) }
    // The body: deep red, darker at the rim.
    drawPath(polygon(outer), Brush.radialGradient(listOf(RubyMid, RubyDark, RubyDeep), center = c, radius = size.width * 0.55f))
    for (i in 0 until 12) {
        val next = (i + 1) % 12
        val prev = (i + 11) % 12
        val at = (-90.0 + i * 30.0) * PI / 180.0
        val between = (-90.0 + (i + 0.5) * 30.0) * PI / 180.0
        // Girdle facets at the rim.
        val g = lit(between, light)
        drawPath(polygon(listOf(middle[i], outer[i], outer[next])), lerp(RubyDeep, RubyMid, g * 0.9f).copy(alpha = 0.95f))
        // Bezel facets (kites) between table and rim.
        val b = lit(at, light)
        drawPath(polygon(listOf(table[i], middle[prev], outer[i], middle[i])), lerp(RubyDark, RubyBright, b).copy(alpha = 0.92f))
        // Star facets around the table, the other way round – that's what makes it sparkle.
        val st = lit(between + PI, light)
        drawPath(polygon(listOf(table[i], table[next], middle[i])), lerp(RubyMid, RubyLight, st * 0.85f).copy(alpha = 0.9f))
    }
    // The table: the window into the stone, with the lava glow deep inside.
    drawPath(
        polygon(table),
        Brush.radialGradient(
            listOf(Color(0xFFFF7A3A).copy(alpha = 0.9f), RubyBright, RubyMid, RubyDark),
            center = Offset(c.x, c.y + size.height * 0.04f),
            radius = size.width * 0.24f,
        ),
    )
    // Fine lines along every facet edge.
    for (i in 0 until 12) {
        val next = (i + 1) % 12
        drawLine(edge, table[i], table[next], hair)
        drawLine(edge, table[i], middle[i], hair)
        drawLine(edge, table[next], middle[i], hair)
        drawLine(edge, middle[i], outer[i], hair)
        drawLine(edge, middle[i], outer[next], hair)
    }
    drawPath(
        polygon(outer),
        Brush.linearGradient(listOf(RubyLight, RubyBright, RubyDark), start = Offset(0f, 0f), end = Offset(size.width, size.height)),
        style = Stroke(1.6.dp.toPx()),
    )
    // A broad gleam across the upper left, following the light.
    clipPath(polygon(outer)) {
        val sweep = (-0.3f + 1.6f * shine) * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(sweep - size.width * 0.3f, 0f),
                end = Offset(sweep + size.width * 0.1f, size.height * 0.7f),
            ),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.32f, size.height * 0.24f), radius = size.width * 0.22f),
            radius = size.width * 0.22f,
            center = Offset(size.width * 0.32f, size.height * 0.24f),
        )
    }
    // Sparkles at two corners, twinkling in turn.
    fun sparkle(at: Offset, strength: Float) {
        val l = 11.dp.toPx() * strength
        val a = (0.9f * strength).coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = a), Offset(at.x - l, at.y), Offset(at.x + l, at.y), 1.6.dp.toPx())
        drawLine(Color.White.copy(alpha = a), Offset(at.x, at.y - l), Offset(at.x, at.y + l), 1.6.dp.toPx())
        drawCircle(Color.White.copy(alpha = a), 2.2.dp.toPx() * strength, at)
    }
    val tw = sin(shine * 2f * PI.toFloat())
    sparkle(table[10], (0.5f + 0.5f * tw).coerceAtLeast(0f))
    sparkle(outer[2], (0.5f - 0.5f * tw).coerceAtLeast(0f))
}

/**
 * OMEGA UI's logo: the ruby with the Ω glowing in it like lava – and, if given, the system
 * version under it (as on a phone's "about" screen).
 */
@Composable
fun OmegaRuby(modifier: Modifier = Modifier, size: Dp = 150.dp, version: String? = null, animate: Boolean = LocalSettings.current.animations) {
    val shine by if (animate) {
        rememberInfiniteTransition(label = "ruby").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "shine",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val glowPx = with(density) { (size * 0.16f).toPx() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = size, height = size * 1.04f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRuby(shine, 1f) }
            // The Ω inside, glowing like molten rock.
            Text(
                "Ω",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFC04A), Color(0xFFFF6A1F), Color(0xFFE5243F))),
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Black,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFFF5A1A), Offset.Zero, glowPx),
                ),
                modifier = Modifier.padding(bottom = size * 0.03f),
            )
        }
        if (version != null) {
            Spacer(Modifier.height(size * 0.07f))
            Text(
                version,
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFC2CA), RubyBright)),
                    fontSize = (size.value * 0.15f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (size.value * 0.004f).sp,
                    textAlign = TextAlign.Center,
                    shadow = androidx.compose.ui.graphics.Shadow(RubyMid.copy(alpha = 0.8f), Offset(0f, 2f), glowPx * 0.4f),
                ),
            )
        }
    }
}

/** A grain of light in the transition. */
private class Grain(val x: Float, val y: Float, val spin: Float, val speed: Float, val color: Color)

/**
 * The update from Hearth UI to OMEGA UI, like a phone's big system upgrade: the old name glows,
 * comes apart into grains of light, the grains swirl together into the ruby, a wave of red
 * light rolls out – and OMEGA UI with its version rises underneath.
 */
@Composable
fun OmegaUpgrade(version: String, onDone: () -> Unit) {
    val animate = LocalSettings.current.animations
    val t = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(7600, easing = LinearEasing)) }
    BackHandler { if (t.value >= 0.75f) onDone() }
    val grains = remember {
        val colors = listOf(Color(0xFFFFB494), Color(0xFFD97757), RubyColors[3], RubyColors[4], Color.White)
        List(160) {
            Grain(
                x = (Random.nextFloat() - 0.5f) * 2f,
                y = (Random.nextFloat() - 0.5f) * 2f,
                spin = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.6f + Random.nextFloat() * 0.8f,
                color = colors[it % colors.size],
            )
        }
    }
    fun phase(from: Float, to: Float): Float = ((t.value - from) / (to - from)).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        // The night turning ruby red once the stone is there.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = phase(0.6f, 0.8f) }
                .background(Brush.verticalGradient(listOf(Color(0xFF12020A), Color(0xFF3A0716), Color(0xFF07020A)))),
        )
        // The old name, glowing – then coming apart.
        val old = phase(0f, 0.12f) * (1f - phase(0.18f, 0.3f))
        Text(
            "Hearth UI",
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Color(0xFFFFB494), Color(0xFFD97757), Color(0xFF8E6BFF))),
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.graphicsLayer {
                alpha = old
                val s = 1f + 0.08f * phase(0.12f, 0.3f)
                scaleX = s
                scaleY = s
            },
        )
        // Grains of light: out of the name, swirling, into the stone.
        Canvas(Modifier.fillMaxSize()) {
            val p = phase(0.16f, 0.62f)
            if (p <= 0f || p >= 1f) return@Canvas
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val band = Offset(150.dp.toPx(), 26.dp.toPx())
            grains.forEach { g ->
                // Start: where the letters were; then out a little, round, and in.
                val start = Offset(size.width / 2f + g.x * band.x, size.height / 2f + g.y * band.y)
                val burst = sin(p * PI.toFloat()) * 90.dp.toPx() * g.speed
                val angle = g.spin + p * 3f * PI.toFloat()
                val drift = Offset(cos(angle) * burst, sin(angle) * burst)
                val pull = p * p
                val at = Offset(
                    start.x + (c.x - start.x) * pull + drift.x,
                    start.y + (c.y - start.y) * pull + drift.y,
                )
                val a = (1f - pull * 0.7f) * (0.5f + 0.5f * sin(p * 6f + g.spin))
                drawCircle(g.color.copy(alpha = a.coerceIn(0f, 1f)), radius = (1.5f + 2f * g.speed).dp.toPx(), center = at)
            }
        }
        // The wave of red light as the stone appears.
        Canvas(Modifier.fillMaxSize()) {
            val w = phase(0.6f, 0.78f)
            if (w <= 0f || w >= 1f) return@Canvas
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val radius = size.height * w
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, RubyColors[3].copy(alpha = 0.5f * (1f - w)), Color.Transparent),
                    center = c,
                    radius = radius.coerceAtLeast(1f),
                ),
                radius = radius.coerceAtLeast(1f),
                center = c,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            // The ruby, turning towards you as it forms.
            OmegaRuby(
                Modifier.graphicsLayer {
                    val p = phase(0.5f, 0.7f)
                    alpha = p
                    val turn = cos((1f - p) * PI.toFloat() / 2f)
                    scaleX = (0.4f + 0.6f * p) * turn.coerceAtLeast(0.05f)
                    scaleY = 0.4f + 0.6f * p
                },
                size = 170.dp,
                animate = animate,
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.graphicsLayer {
                    val p = phase(0.72f, 0.86f)
                    alpha = p
                    translationY = (1f - p) * 40.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "OMEGA UI",
                    style = TextStyle(brush = Brush.linearGradient(listOf(Color.White, RubyColors[4], RubyColors[3])), fontSize = 42.sp, fontWeight = FontWeight.Black),
                )
                Text(
                    version,
                    style = TextStyle(brush = Brush.linearGradient(listOf(RubyColors[4], RubyColors[2])), fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(4.dp))
                Text("auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Hearth UI ist jetzt OMEGA UI – mit neuem Startbildschirm, neuen Uhren, Docks, Menüs, Fenstern, OMEGA Cloud und vielen neuen Designs.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = phase(0.82f, 0.94f) },
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(bottom = 18.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer { alpha = phase(0.9f, 1f) }
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.horizontalGradient(listOf(RubyColors[1], RubyColors[2], RubyColors[3])))
                    .glassSheen(27.dp)
                    .clickable(enabled = t.value >= 0.9f, onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("Weiter", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Ruby red into rose, for the OMEGA design's accents. */
internal fun rubyBlend(f: Float): Color = lerp(RubyColors[1], RubyColors[3], f.coerceIn(0f, 1f))

/**
 * OMEGA UI 17's easter egg, "Rubin schleifen": a rough red stone. Every tap cuts a facet – a
 * flash of light where the cut goes – and after seventeen cuts it's the ruby of OMEGA UI,
 * shining with the Ω inside.
 */
@Composable
internal fun RubyEgg(version: String, name: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { dev.hearth.launcher.data.EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    var cuts by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val done = cuts >= 17
    val lines = remember { List(17) { Offset(Random.nextFloat(), Random.nextFloat()) to Offset(Random.nextFloat(), Random.nextFloat()) } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0306), Color(0xFF2A0610), Color(0xFF050203))))
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (!done) {
                    cuts++
                    scope.launch {
                        flash.snapTo(1f)
                        flash.animateTo(0f, tween(450))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(HearthUi.major(version), color = Color.White.copy(alpha = 0.05f), fontSize = 200.sp, fontWeight = FontWeight.Black)
        Box(contentAlignment = Alignment.Center) {
            // Rough at first (dull, grey-red), clearer with every cut.
            val clear = (cuts / 17f).coerceIn(0f, 1f)
            OmegaRuby(
                Modifier.graphicsLayer {
                    alpha = 0.35f + 0.65f * clear
                    val s = 0.9f + 0.1f * clear + 0.04f * flash.value
                    scaleX = s
                    scaleY = s
                },
                size = 220.dp,
                version = if (done) "$name $version" else null,
            )
            Canvas(Modifier.size(220.dp)) {
                lines.take(cuts).forEachIndexed { i, (a, b) ->
                    val fresh = if (i == cuts - 1) flash.value else 0f
                    drawLine(
                        Color.White.copy(alpha = 0.15f + 0.7f * fresh),
                        Offset(size.width * (0.2f + 0.6f * a.x), size.height * (0.15f + 0.5f * a.y)),
                        Offset(size.width * (0.2f + 0.6f * b.x), size.height * (0.15f + 0.5f * b.y)),
                        strokeWidth = (1f + 2f * fresh).dp.toPx(),
                    )
                }
            }
        }
        if (done) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .size(width = 90.dp, height = 78.dp),
                mood = dev.hearth.launcher.data.ClawdMood.Love,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Rubin", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (done) "Geschliffen – der Rubin von OMEGA UI. 💎" else "Tippen zum Schleifen · $cuts/17 Facetten",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
