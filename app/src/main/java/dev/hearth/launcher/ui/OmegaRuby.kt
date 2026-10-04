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

/**
 * A ruby cut like a brilliant, of red glass you can see into, with the Ω inside it.
 * A facet corner (x, y) is given as parts of the gem's width and height.
 */
private val Girdle = 0.38f
private val GirdleX = listOf(0.06f, 0.28f, 0.5f, 0.72f, 0.94f)
private val TableX = listOf(0.3f, 0.5f, 0.7f)
private const val TableY = 0.12f
private const val Culet = 0.96f

private fun DrawScope.facet(points: List<Pair<Float, Float>>, color: Color) {
    val path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
        }
        close()
    }
    drawPath(path, color)
    drawPath(path, Color(0xFFFFD6DC).copy(alpha = 0.35f), style = Stroke(1.2.dp.toPx()))
}

/** The gem itself: crown and table above the girdle, the pavilion running down to its point. */
private fun DrawScope.drawRuby(shine: Float, glow: Float) {
    val r = RubyColors
    // Its own red light around it.
    drawCircle(
        Brush.radialGradient(listOf(r[2].copy(alpha = 0.45f * glow), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.45f), radius = size.width * 0.8f),
        radius = size.width * 0.8f,
        center = Offset(size.width / 2f, size.height * 0.45f),
    )
    // Pavilion: four facets down to the culet, darker at the sides – red glass with depth.
    val pavilion = listOf(r[0].copy(alpha = 0.85f), r[1].copy(alpha = 0.75f), r[2].copy(alpha = 0.7f), r[0].copy(alpha = 0.85f))
    for (i in 0 until 4) {
        facet(listOf(GirdleX[i] to Girdle, GirdleX[i + 1] to Girdle, 0.5f to Culet), pavilion[i])
    }
    // Inner fire: light caught inside the stone.
    drawCircle(
        Brush.radialGradient(listOf(r[4].copy(alpha = 0.35f), r[3].copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.55f), radius = size.width * 0.3f),
        radius = size.width * 0.3f,
        center = Offset(size.width * 0.5f, size.height * 0.55f),
    )
    // Crown: the slopes from the table out to the girdle.
    facet(listOf(TableX[0] to TableY, GirdleX[0] to Girdle, GirdleX[1] to Girdle), r[1].copy(alpha = 0.7f))
    facet(listOf(TableX[0] to TableY, GirdleX[1] to Girdle, GirdleX[2] to Girdle, TableX[1] to TableY), r[3].copy(alpha = 0.55f))
    facet(listOf(TableX[1] to TableY, GirdleX[2] to Girdle, GirdleX[3] to Girdle, TableX[2] to TableY), r[2].copy(alpha = 0.6f))
    facet(listOf(TableX[2] to TableY, GirdleX[3] to Girdle, GirdleX[4] to Girdle), r[1].copy(alpha = 0.75f))
    // The light running across the facets (only on the stone).
    val x = (-0.4f + 1.8f * shine) * size.width
    val outline = Path().apply {
        moveTo(TableX[0] * size.width, TableY * size.height)
        lineTo(TableX[2] * size.width, TableY * size.height)
        lineTo(GirdleX[4] * size.width, Girdle * size.height)
        lineTo(0.5f * size.width, Culet * size.height)
        lineTo(GirdleX[0] * size.width, Girdle * size.height)
        close()
    }
    clipPath(outline) {
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height * 0.6f),
            ),
        )
    }
    // A sparkle at the top right of the table.
    val s = Offset(size.width * 0.68f, size.height * 0.15f)
    val tw = 0.5f + 0.5f * sin(shine * 2f * PI.toFloat())
    drawLine(Color.White.copy(alpha = 0.8f * tw), Offset(s.x - 9.dp.toPx(), s.y), Offset(s.x + 9.dp.toPx(), s.y), strokeWidth = 1.5.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.8f * tw), Offset(s.x, s.y - 9.dp.toPx()), Offset(s.x, s.y + 9.dp.toPx()), strokeWidth = 1.5.dp.toPx())
}

/**
 * OMEGA UI's logo: the red glass ruby with the Ω set into it – and, if given, the system
 * version under it (as on a phone's "about" screen).
 */
@Composable
fun OmegaRuby(modifier: Modifier = Modifier, size: Dp = 150.dp, version: String? = null, animate: Boolean = LocalSettings.current.animations) {
    val shine by if (animate) {
        rememberInfiniteTransition(label = "ruby").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
            label = "shine",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.35f) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = size, height = size * 1.05f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRuby(shine, 1f) }
            // The Ω, set into the stone.
            Text(
                "Ω",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFD6DC), Color(0xFFFF8A98))),
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.Black,
                ),
                modifier = Modifier
                    .padding(bottom = size * 0.12f)
                    .graphicsLayer { alpha = 0.92f },
            )
        }
        if (version != null) {
            Spacer(Modifier.height(size * 0.06f))
            Text(
                version,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color.White, RubyColors[4], RubyColors[3])),
                    fontSize = (size.value * 0.16f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
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
