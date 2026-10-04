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

/** A corner of the stone, as parts of its width and height. */
private fun DrawScope.at(x: Float, y: Float): Offset = Offset(x * size.width, y * size.height)

private fun polygon(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

// OMEGA UI's ruby, as a clean mark: four reds, no glow.
private val GemLight = Color(0xFFFF5C72)
private val GemMid = Color(0xFFE0203F)
private val GemDark = Color(0xFFB30F2E)
private val GemDeep = Color(0xFF7E0820)
private val GemPale = Color(0xFFFFC9CE)

/**
 * The ruby of OMEGA UI as a logo: the shape of Omega Ruby's stone – a pointed top, sloping
 * crown facets, a broad band round the middle, a faceted base – drawn flat and crisp in four
 * reds, light from the upper right. No glow, no rays: a mark, not a rendering.
 */
private fun DrawScope.drawRuby() {
    val apex = at(0.5f, 0f)
    val ul = at(0.17f, 0.2f)
    val ur = at(0.83f, 0.2f)
    val l1 = at(0f, 0.42f)
    val r1 = at(1f, 0.42f)
    val l2 = at(0f, 0.6f)
    val r2 = at(1f, 0.6f)
    val ll = at(0.13f, 0.8f)
    val lr = at(0.87f, 0.8f)
    val bl = at(0.33f, 0.97f)
    val br = at(0.67f, 0.97f)
    val bottom = at(0.5f, 1f)
    val m1 = at(0.38f, 0.2f)
    val m2 = at(0.62f, 0.2f)
    val b1 = at(0.3f, 0.42f)
    val b2 = at(0.7f, 0.42f)
    val c1 = at(0.3f, 0.6f)
    val c2 = at(0.7f, 0.6f)
    val d1 = at(0.36f, 0.8f)
    val d2 = at(0.64f, 0.8f)
    fun facet(color: Color, points: List<Offset>) = drawPath(polygon(points), color)
    // Crown: lighter towards the upper right.
    facet(GemMid, listOf(apex, ul, m1))
    facet(GemLight, listOf(apex, m1, m2))
    facet(GemPale, listOf(apex, m2, ur))
    facet(GemDark, listOf(ul, m1, b1, l1))
    facet(GemMid, listOf(m1, m2, b2, b1))
    facet(GemLight, listOf(m2, ur, r1, b2))
    // The band round the middle.
    facet(GemDeep, listOf(l1, b1, c1, l2))
    facet(GemMid, listOf(b1, b2, c2, c1))
    facet(GemDark, listOf(b2, r1, r2, c2))
    // The base.
    facet(GemDark, listOf(l2, c1, d1, ll))
    facet(GemDark, listOf(c1, c2, d2, d1))
    facet(GemDeep, listOf(c2, r2, lr, d2))
    facet(GemDeep, listOf(ll, d1, bl))
    facet(GemDark, listOf(d1, d2, br, bottom, bl))
    facet(GemDeep, listOf(d2, lr, br))
    // Thin light lines between the facets, a crisp edge round it.
    val line = GemPale.copy(alpha = 0.45f)
    val hair = (size.width * 0.008f).coerceAtLeast(1f)
    listOf(
        apex to m1, apex to m2, ul to ur, m1 to b1, m2 to b2, l1 to r1, b1 to c1, b2 to c2, l2 to r2,
        c1 to d1, c2 to d2, ll to lr, d1 to bl, d2 to br,
    ).forEach { (a, b) -> drawLine(line, a, b, hair) }
    drawPath(
        polygon(listOf(apex, ur, r1, r2, lr, br, bottom, bl, ll, l2, l1, ul)),
        GemDeep,
        style = Stroke((size.width * 0.018f).coerceAtLeast(1.2f)),
    )
}

/**
 * OMEGA UI's logo: the ruby with a big white Ω on it – and, if given, the system version
 * under it (as on a phone's "about" screen). Still, so it costs nothing to show.
 */
@Composable
fun OmegaRuby(modifier: Modifier = Modifier, size: Dp = 150.dp, version: String? = null, @Suppress("UNUSED_PARAMETER") animate: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = size, height = size * 1.18f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRuby() }
            // The Ω, big and clear.
            Text(
                "Ω",
                style = TextStyle(
                    color = Color.White,
                    fontSize = (size.value * 0.62f).sp,
                    fontWeight = FontWeight.Black,
                    shadow = androidx.compose.ui.graphics.Shadow(GemDeep.copy(alpha = 0.55f), Offset(0f, size.value * 0.02f), 0f),
                ),
                modifier = Modifier.padding(top = size * 0.06f),
            )
        }
        if (version != null) {
            Spacer(Modifier.height(size * 0.08f))
            Text(
                version,
                style = TextStyle(
                    color = Color.White,
                    fontSize = (size.value * 0.15f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (size.value * 0.006f).sp,
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
