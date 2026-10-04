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

// The stone's light colors: pale rose glass with a warm glow inside.
private val GemPale = Color(0xFFFFC9CE)
private val GemRose = Color(0xFFF58C9B)
private val GemCoral = Color(0xFFE8697C)
private val GemDeep = Color(0xFFB24560)
private val GemGlow = Color(0xFFFF8A3D)

/**
 * The ruby of OMEGA UI, as in Omega Ruby: a pointed top, sloping crown facets, a broad band
 * round the middle and a faceted, flattened base – pale rose glass you can see into, with an
 * orange ring of light glowing inside around the Ω. Rays of light burst out behind it.
 */
private fun DrawScope.drawRuby(shine: Float, glow: Float, rays: Boolean) {
    val c = at(0.5f, 0.52f)
    // Rays bursting out behind the stone, slowly turning, in soft rainbow colors.
    if (rays) {
        val rayColors = listOf(Color(0xFFFFB0A0), Color(0xFFA8E6FF), Color(0xFFC8FFB0), Color(0xFFFFE3A0), Color(0xFFE0B8FF))
        val turn = shine * 0.6f
        for (i in 0 until 48) {
            val a = (i / 48f + turn / 48f) * 2f * PI.toFloat()
            val len = size.width * (0.9f + 0.45f * ((i * 37) % 10) / 10f)
            drawLine(
                Brush.linearGradient(
                    listOf(rayColors[i % rayColors.size].copy(alpha = 0.32f * glow), Color.Transparent),
                    start = c,
                    end = Offset(c.x + cos(a) * len, c.y + sin(a) * len),
                ),
                c,
                Offset(c.x + cos(a) * len, c.y + sin(a) * len),
                strokeWidth = (if (i % 3 == 0) 2.2f else 1.2f).dp.toPx(),
            )
        }
        // Warm light pooling under it.
        drawCircle(
            Brush.radialGradient(listOf(GemGlow.copy(alpha = 0.35f * glow), Color.Transparent), center = at(0.5f, 1.05f), radius = size.width * 0.8f),
            radius = size.width * 0.8f,
            center = at(0.5f, 1.05f),
        )
    }
    // The outline: apex, shoulders, the broad band, the lower facets, the flat base.
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
    val outline = polygon(listOf(apex, ur, r1, r2, lr, br, bottom, bl, ll, l2, l1, ul))
    // Inside first: rose glass, and the orange ring of light around the middle.
    drawPath(outline, Brush.radialGradient(listOf(GemRose, GemCoral, GemDeep), center = c, radius = size.width * 0.7f))
    val ringR = size.width * 0.3f
    drawCircle(
        Brush.radialGradient(
            0.0f to Color(0xFFFF5A1F).copy(alpha = 0.55f),
            0.45f to GemGlow.copy(alpha = 0.25f),
            0.7f to Color(0xFFFFB070).copy(alpha = 0.75f * glow),
            0.86f to GemGlow.copy(alpha = 0.3f),
            1f to Color.Transparent,
            center = c,
            radius = ringR,
        ),
        radius = ringR,
        center = c,
    )
    // The facets over it, see-through, each catching the light a little differently.
    val m1 = at(0.38f, 0.2f)
    val m2 = at(0.62f, 0.2f)
    val b1 = at(0.3f, 0.42f)
    val b2 = at(0.7f, 0.42f)
    val c1 = at(0.3f, 0.6f)
    val c2 = at(0.7f, 0.6f)
    val d1 = at(0.36f, 0.8f)
    val d2 = at(0.64f, 0.8f)
    val wobble = 0.06f * sin(shine * 2f * PI.toFloat())
    fun glass(points: List<Offset>, color: Color, alpha: Float) = drawPath(polygon(points), color.copy(alpha = (alpha + wobble).coerceIn(0f, 1f)))
    // Crown.
    glass(listOf(apex, ul, m1), GemRose, 0.55f)
    glass(listOf(apex, m1, m2), GemPale, 0.45f)
    glass(listOf(apex, m2, ur), GemPale, 0.7f)
    glass(listOf(ul, m1, b1, l1), GemCoral, 0.4f)
    glass(listOf(m1, m2, b2, b1), GemRose, 0.22f)
    glass(listOf(m2, ur, r1, b2), GemPale, 0.5f)
    // The broad band round the middle.
    glass(listOf(l1, b1, c1, l2), GemDeep, 0.45f)
    glass(listOf(b1, b2, c2, c1), GemRose, 0.12f)
    glass(listOf(b2, r1, r2, c2), GemCoral, 0.4f)
    // Lower facets and the base.
    glass(listOf(l2, c1, d1, ll), GemCoral, 0.5f)
    glass(listOf(c1, c2, d2, d1), GemRose, 0.35f)
    glass(listOf(c2, r2, lr, d2), GemDeep, 0.45f)
    glass(listOf(ll, d1, bl), GemDeep, 0.5f)
    glass(listOf(d1, d2, br, bottom, bl), GemRose, 0.45f)
    glass(listOf(d2, lr, br), GemCoral, 0.55f)
    // Fine bright edges.
    val edge = Color(0xFFFFE3E6).copy(alpha = 0.55f)
    val hair = 1.dp.toPx()
    listOf(
        apex to m1, apex to m2, ul to ur, m1 to b1, m2 to b2, l1 to r1, b1 to c1, b2 to c2, l2 to r2,
        c1 to d1, c2 to d2, ll to lr, d1 to bl, d2 to br,
    ).forEach { (a, b) -> drawLine(edge, a, b, hair) }
    drawPath(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), GemPale, GemCoral), start = apex, end = bottom), style = Stroke(1.6.dp.toPx()))
    // Light on the glass: streaks across, a glare at the upper right, a wandering gleam.
    clipPath(outline) {
        val sweep = (-0.3f + 1.6f * shine) * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent),
                start = Offset(sweep - size.width * 0.3f, 0f),
                end = Offset(sweep + size.width * 0.1f, size.height * 0.8f),
            ),
        )
        for (k in 0 until 7) {
            val y = size.height * (0.15f + k * 0.11f)
            drawLine(
                Color.White.copy(alpha = 0.12f + 0.06f * (k % 2)),
                Offset(size.width * 0.05f, y + size.height * 0.08f),
                Offset(size.width * 0.95f, y - size.height * 0.08f),
                (if (k % 3 == 0) 1.6f else 0.8f).dp.toPx(),
            )
        }
    }
    val glare = at(0.78f, 0.44f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.2f), Color.Transparent), center = glare, radius = size.width * 0.16f),
        radius = size.width * 0.16f,
        center = glare,
    )
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f + shine * 0.4f
        val len = size.width * (if (i % 2 == 0) 0.32f else 0.16f)
        drawLine(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), start = glare, end = Offset(glare.x + cos(a) * len, glare.y + sin(a) * len)),
            glare,
            Offset(glare.x + cos(a) * len, glare.y + sin(a) * len),
            strokeWidth = 1.4.dp.toPx(),
        )
    }
    // A bright dot on the left side, as on the real stone.
    drawCircle(Color.White.copy(alpha = 0.95f), 3.dp.toPx(), at(0.14f, 0.47f))
}

/**
 * OMEGA UI's logo: the ruby with a clear, dark Ω glowing orange inside – and, if given, the
 * system version under it (as on a phone's "about" screen).
 */
@Composable
fun OmegaRuby(modifier: Modifier = Modifier, size: Dp = 150.dp, version: String? = null, animate: Boolean = LocalSettings.current.animations) {
    val shine by if (animate) {
        rememberInfiniteTransition(label = "ruby").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
            label = "shine",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val glowPx = with(density) { (size * 0.12f).toPx() }
    // Big logos burst with rays; small ones (a card, a list) stay calm.
    val rays = size >= 100.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = size, height = size * 1.18f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRuby(shine, 1f, rays) }
            // The Ω inside: dark and clear, glowing orange round its edges.
            Text(
                "Ω",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFF6A1A12), Color(0xFF3A0A08), Color(0xFF240404))),
                    fontSize = (size.value * 0.46f).sp,
                    fontWeight = FontWeight.Black,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFFF6A1F), Offset.Zero, glowPx),
                ),
                modifier = Modifier.padding(top = size * 0.05f),
            )
        }
        if (version != null) {
            Spacer(Modifier.height(size * 0.08f))
            Text(
                version,
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, GemPale, GemCoral)),
                    fontSize = (size.value * 0.15f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (size.value * 0.004f).sp,
                    textAlign = TextAlign.Center,
                    shadow = androidx.compose.ui.graphics.Shadow(GemDeep.copy(alpha = 0.8f), Offset(0f, 2f), glowPx * 0.5f),
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
