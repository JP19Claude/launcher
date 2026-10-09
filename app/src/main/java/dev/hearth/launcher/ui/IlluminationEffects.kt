package dev.hearth.launcher.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdOutfit
import dev.hearth.launcher.data.ColorWorld
import dev.hearth.launcher.data.Perk
import dev.hearth.launcher.data.has
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * OMEGA UI 17.5 – what the Clawd Illumination does to the system: another color world,
 * Ω rain behind the home screen, weightless icons, a Clawd parade on the dock, Ω for zeros.
 */

internal val LocalColorWorldApplied = staticCompositionLocalOf { false }

/** The hue of everything turned by [degrees] (luminance kept), as a 4×5 color matrix. */
private fun hueMatrix(degrees: Float): FloatArray {
    val a = Math.toRadians(degrees.toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val r = 0.213f
    val g = 0.715f
    val b = 0.072f
    return floatArrayOf(
        r + c * (1 - r) - s * r, g - c * g - s * g, b - c * b + s * (1 - b), 0f, 0f,
        r - c * r + s * 0.143f, g + c * (1 - g) + s * 0.140f, b - c * b - s * 0.283f, 0f, 0f,
        r - c * r - s * (1 - r), g - c * g + s * g, b + c * (1 - b) + s * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun hueEffect(degrees: Float): androidx.compose.ui.graphics.RenderEffect? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.graphics.RenderEffect
            .createColorFilterEffect(android.graphics.ColorMatrixColorFilter(hueMatrix(degrees)))
            .asComposeRenderEffect()
    } else {
        null
    }

/**
 * Farbwelt: the whole screen in another color world – its hue turned (Smaragd, Saphir, …) or,
 * with Spektrum, slowly running through all of them. Once per window (nested themes skip it).
 */
@Composable
internal fun ProvideColorWorld(content: @Composable () -> Unit) {
    val nested = LocalColorWorldApplied.current
    CompositionLocalProvider(LocalColorWorldApplied provides true) {
        Box(
            if (nested) Modifier else Modifier.colorWorld(),
            propagateMinConstraints = true,
        ) { content() }
    }
}

/**
 * The color world on whatever it's put on (the root of a screen): Farbwelt's hue (or Spektrum),
 * Noir, Sepia, Leuchtfarben – and Augenschutz's warm light over it. Put it on once per window
 * and provide [LocalColorWorldApplied] below it, so nested themes don't apply it twice.
 */
internal fun Modifier.colorWorld(): Modifier = composed {
    val settings = LocalSettings.current
    val world = settings.colorWorld
    val canFilter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // OMEGA UI 18: Noir, Sepia and Leuchtfarben join the color world.
    val noir = settings.has(Perk.Noir)
    val sepia = settings.has(Perk.Sepia)
    val vivid = settings.has(Perk.Vivid)
    val on = canFilter && (world != ColorWorld.Off || noir || sepia || vivid)
    val warm = settings.has(Perk.EyeCare)
    val spectrum = on && world == ColorWorld.Spectrum
    val turn = flowPhase(40_000, spectrum && settings.animations)
    val hue: Float? = if (world == ColorWorld.Off || world == ColorWorld.Spectrum) null else world.degrees
    val still = remember(world, on, noir, sepia, vivid) { if (on && !spectrum) worldEffect(hue, noir, sepia, vivid) else null }
    this
        .graphicsLayer {
            renderEffect = when {
                !on -> null
                spectrum -> worldEffect(Math.toDegrees((turn?.invoke() ?: 1f).toDouble()).toFloat(), noir, sepia, vivid)
                else -> still
            }
        }
        // Augenschutz: warm light over everything.
        .then(if (warm) Modifier.drawWithContent { drawContent(); drawRect(Color(0x2EFF8A2A)) } else Modifier)
}

/** The color world's filter: the hue turned by [hue] (if any), then black-and-white, sepia or vivid. */
private fun worldEffect(hue: Float?, noir: Boolean, sepia: Boolean, vivid: Boolean): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val m = android.graphics.ColorMatrix()
    if (hue != null) m.postConcat(android.graphics.ColorMatrix(hueMatrix(hue)))
    if (noir) {
        m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
    } else if (vivid) {
        m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.65f) })
    }
    if (sepia) {
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    return android.graphics.RenderEffect
        .createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m))
        .asComposeRenderEffect()
}

/** One Ω of the rain: where it rises, how fast (whole rounds per cycle), how big, which color. */
private class OmegaDrop(val x: Float, val speed: Int, val offset: Float, val size: Int, val color: Int, val sway: Float)

/**
 * Ω-Regen: Ω symbols in the OMEGA colors rise slowly behind the home screen, swaying a little,
 * fading in at the bottom and out at the top.
 */
@Composable
internal fun OmegaRain(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val phase = flowPhase(90_000, s.animations)
    val measurer = rememberTextMeasurer()
    val palette = fluidPalette()
    // ZENITH: Z marks instead of Ω, in four sizes.
    val glyphs = listOf(18, 26, 38, 52)
    val drops = remember {
        val random = kotlin.random.Random(17)
        List(18) {
            OmegaDrop(
                x = random.nextFloat() * 0.9f + 0.02f,
                speed = 2 + random.nextInt(3),
                offset = random.nextFloat(),
                size = random.nextInt(4),
                color = random.nextInt(5),
                sway = 1f + random.nextFloat() * 2f,
            )
        }
    }
    Canvas(modifier) {
        val t = (phase?.invoke() ?: 2f) / (2f * PI.toFloat())
        val swing = 16.dp.toPx()
        drops.forEach { d ->
            val p = (t * d.speed + d.offset) % 1f
            val mark = glyphs[d.size].dp.toPx()
            val y = size.height * 1.05f - p * (size.height * 1.15f)
            val x = size.width * d.x + sin(p * 2f * PI.toFloat() * d.sway + d.offset * 9f) * swing
            val fade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            drawZenithMarkAt(Offset(x, y), mark, palette[d.color % palette.size], alpha = 0.16f + 0.3f * fade * (0.5f + 0.15f * d.size))
        }
    }
}

/**
 * Schwerelos: the home screen's icons float gently, each on its own rhythm ([seed]); only the
 * layer moves, nothing is laid out or drawn again.
 */
internal fun Modifier.weightless(seed: Int): Modifier = composed {
    val s = LocalSettings.current
    if (!s.weightless) return@composed this
    val phase = flowPhase(7_000, s.animations) ?: return@composed this
    val offset = (seed * 1.37f) % (2f * PI.toFloat())
    this.graphicsLayer {
        val t = phase() + offset
        translationY = sin(t) * 5.dp.toPx()
        translationX = cos(t * 0.7f) * 2.dp.toPx()
        rotationZ = sin(t * 0.9f) * 2.5f
    }
}

/** The parade's Clawds: their colors, the OMEGA ruby leading. */
private val ParadeColors = listOf(
    Color(0xFFE5243F),
    Color(0xFFD97757),
    Color(0xFF3D7BFF),
    Color(0xFFFFC94D),
    Color(0xFF8FE3C0),
    Color(0xFFC6A8FF),
)

/**
 * Clawd-Parade: every so often a line of Clawds in many colors marches over the dock, the
 * first one carrying an Ω. Drawn on one canvas; between parades it draws nothing at all.
 */
@Composable
internal fun ClawdParade(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val march = remember { Animatable(0f) }
    LaunchedEffect(s.animations) {
        if (!s.animations) return@LaunchedEffect
        while (true) {
            delay(4_000)
            march.snapTo(0f)
            march.animateTo(1f, tween(9_000, easing = LinearEasing))
            delay(kotlin.random.Random.nextLong(25_000, 45_000))
        }
    }
    Canvas(modifier) {
        val p = march.value
        if (p <= 0f || p >= 1f) return@Canvas
        val h = size.height
        val w = h * (14f / 11f)
        val gap = w * 0.35f
        val line = ParadeColors.size * (w + gap)
        // From off the left edge to off the right edge.
        val lead = -w + p * (size.width + line + w)
        val full = size
        ParadeColors.forEachIndexed { i, color ->
            val x = lead - i * (w + gap)
            if (x < -w || x > size.width) return@forEachIndexed
            val step = p * 70f + i * 0.5f
            val hop = -kotlin.math.abs(sin(p * 90f + i)) * h * 0.06f
            translate(left = x, top = hop) {
                drawContext.size = Size(w, h)
                drawClawd(
                    color,
                    blink = false,
                    bob = 0f,
                    legPhase = step,
                    armLift = if (i == 0) 1f else sin(step * 0.7f) * 0.5f,
                    spin = 0f,
                    hearts = 0f,
                    hat = ClawdHat.None,
                    outfit = ClawdOutfit.None,
                )
                drawContext.size = full
            }
            // The leader holds ZENITH's Z up high.
            if (i == 0) {
                drawZenithMarkAt(Offset(x + w * 0.95f, hop - h * 0.12f), w * 0.5f, ZenithGreen)
            }
        }
    }
}

/** Sonnen-Ziffern (once Ω-Ziffern): the zeros of a clock as suns. */
internal fun omegaDigits(text: String, on: Boolean): String = if (on) text.replace('0', '☉') else text
