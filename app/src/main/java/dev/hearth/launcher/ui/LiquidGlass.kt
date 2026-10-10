package dev.hearth.launcher.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hearth.launcher.data.GlassTint
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.liquidOmega
import dev.hearth.launcher.data.zenithMetal
import dev.hearth.launcher.data.WallpaperBackdrop
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The wallpaper copy that glass surfaces look through. Null = frosted fallback. */
val LocalBackdrop = compositionLocalOf<WallpaperBackdrop?> { null }

/** How strong the glass is, from the settings. Factors of 1 are the defaults. */
@Immutable
data class GlassStyle(
    val blur: Float = 1f,
    val refraction: Float = 1f,
    val dispersion: Float = 0.6f,
    val specular: Float = 1f,
    val tint: Color = Color.White.copy(alpha = 0.04f),
    val interactive: Boolean = true,
    /** Dark mode: every glass surface is tinted dark, like a dark widget. */
    val dark: Boolean = false,
    /** Smallest glass (dp) that gets the lens shader; "Glas-Qualität" in the settings. */
    val lensMinDp: Float = 40f,
    /** OMEGA UI 18.5: Liquid Glass like Apple's – clear, with a bright inner edge. */
    val liquid: Boolean = false,
    /** ZENITH 19, "ZENITH Metall": dark metal with silver on top and green light at the foot. */
    val zenith: Boolean = false,
    /** ZENITH 19, "Projekt Zenith": holograms – dark glass, scan lines, a green frame with brackets. */
    val hud: Boolean = false,
    /** ZENITH 19.1, "Projekt Zenith Glas": Projekt Zenith in real liquid glass – a lens, green light. */
    val hudGlass: Boolean = false,
) {
    companion object {
        fun from(s: LauncherSettings) = GlassStyle(
            blur = s.glassBlur,
            // OMEGA UI 17.5, Clawd Illumination "Hyperglas": glass that bends, splits and shines
            // far more than any setting allows.
            // OMEGA UI 18.3 "Liquid Glass": clear like Apple's – a deeper lens, more shine.
            refraction = s.glassRefraction * (if (s.hyperGlass) 1.9f else 1f) * (if (s.liquidOmega) 1.35f else 1f),
            dispersion = if (s.hyperGlass) (s.glassDispersion * 2.4f).coerceAtLeast(1.4f) else if (s.liquidOmega) maxOf(s.glassDispersion, 0.8f) else s.glassDispersion,
            specular = s.glassSpecular * (if (s.hyperGlass) 1.5f else 1f) * (if (s.liquidOmega) 1.4f else 1f),
            // OMEGA Glass: ruby (or Galaxy Omega) glass through the whole system, clearly tinted
            // (and deeper still with the Illumination's Ω-Overdrive).
            tint = if (s.liquidOmega) {
                // Hardly any color at all: the world behind it, bent and lit.
                Color.White.copy(alpha = (0.05f * s.glassTintStrength.coerceAtLeast(1f)).coerceAtMost(0.12f))
            } else if (s.omegaGlass) {
                val t = when (s.omegaColor) {
                    dev.hearth.launcher.data.OmegaColor.GalaxyOmega -> GlassTint.GalaxyOmega.color
                    dev.hearth.launcher.data.OmegaColor.Zenith -> GlassTint.Zenith.color
                    else -> GlassTint.Ruby.color
                }
                val boost = if (s.omegaOverdrive) 2.4f else 1.6f
                t.copy(alpha = (t.alpha * boost * s.glassTintStrength.coerceAtLeast(1f)).coerceIn(0f, if (s.omegaOverdrive) 0.7f else 0.6f))
            } else {
                s.glassTint.color.copy(alpha = (s.glassTint.color.alpha * s.glassTintStrength).coerceIn(0f, 1f))
            },
            interactive = s.glassInteractive,
            dark = s.darkGlass,
            liquid = s.liquidOmega,
            // Flüssig-Modus: the lens only on big glass; small glass gets the drawn edge (much lighter).
            lensMinDp = if (s.smoothMode) maxOf(s.glassQuality.lensMinDp, 96f) else s.glassQuality.lensMinDp,
        ).let { glass ->
            // Projekt Zenith Glas: real liquid glass, tinted green, bending more and catching
            // the light at its rim like Apple's, with Projekt Zenith's green frame on top.
            if (s.projectZenith && s.projectZenithGlass) {
                glass.copy(
                    tint = Color(0xFF0A3A22).copy(alpha = if (s.darkGlass) 0.42f else 0.26f),
                    refraction = glass.refraction * 1.3f,
                    dispersion = maxOf(glass.dispersion, 0.9f),
                    specular = glass.specular * 1.3f,
                    liquid = true,
                    zenith = true,
                    hud = true,
                    hudGlass = true,
                )
            } else if (s.zenithMetal) {
                // ZENITH Metall: smoked dark metal over the blurred wallpaper – no lens at all.
                glass.copy(
                    tint = Color(0xFF14161A).copy(alpha = if (s.darkGlass) 0.8f else 0.68f),
                    refraction = 0.3f,
                    dispersion = 0f,
                    liquid = false,
                    zenith = true,
                    hud = s.projectZenith,
                    lensMinDp = 100_000f,
                )
            } else {
                glass
            }
        }
    }
}

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

/** The glass in dark mode: a deep night blue that still lets the wallpaper through a little. */
val DarkGlassTint = Color(0xFF15122B).copy(alpha = 0.68f)

/** Light from the top left, slightly tilted, when the phone lies still or motion is off. */
private val DefaultLight = Offset(-0.45f, -0.89f)

/** Direction the light comes from (screen coordinates, unit length). */
val LocalGlassLight = compositionLocalOf<State<Offset>> { mutableStateOf(DefaultLight) }

/**
 * Follows the phone's tilt, so the highlights on the glass wander
 * like light on real glass. Only listens while the launcher is in front.
 */
@Composable
fun rememberGlassLight(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val light = remember { mutableStateOf(DefaultLight) }

    DisposableEffect(enabled, lifecycle) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensorManager == null || sensor == null) {
            light.value = DefaultLight
            return@DisposableEffect onDispose { }
        }

        var x = DefaultLight.x
        var y = DefaultLight.y
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                // Tilting sideways swings the light left and right, tilting back moves it down.
                var tx = DefaultLight.x + gx * 2.2f
                var ty = DefaultLight.y + (0.75f - gy) * 1.4f
                val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
                tx /= len
                ty /= len
                // Smooth out sensor jitter.
                x += (tx - x) * 0.2f
                y += (ty - y) * 0.2f
                val old = light.value
                // Only redraw for a visible change, not for sensor jitter.
                if (abs(old.x - x) + abs(old.y - y) > 0.07f) light.value = Offset(x, y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }
    return light
}

/** Glass boosts the colors behind it a little, like real glass catching light. */
private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) })

/**
 * Liquid glass on the GPU (Android 13+), drawn over a slightly larger piece of the backdrop
 * than the glass itself ([inset] on every side), because a real lens shows what lies beyond
 * its edge:
 * - the rim is a rounded slab: the steeper it gets, the more it pulls in what's outside,
 *   so lines behind it bend around the edge; the middle magnifies a little
 * - colors split where the bend is strongest
 * - Fresnel: the rim reflects more light the flatter you look at it
 * - a thin bright line where the edge faces the light, a softer one on the far side,
 *   and a little shade on the inside of the bevel for depth
 * - a soft glow where the finger touches
 */
private const val GLASS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 inset;
uniform float cornerRadius;
uniform float depth;
uniform float dispersion;
uniform float specular;
uniform float2 light;
uniform float2 touch;
uniform float glow;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 inner = size - 2.0 * inset;
    float2 halfSize = inner * 0.5;
    float2 center = inset + halfSize;
    float2 p = coord - center;
    float d = sdRoundRect(p, halfSize, cornerRadius);
    if (d > 1.0) return half4(0.0);
    float edgeAlpha = clamp(0.5 - d, 0.0, 1.0);
    float inside = max(-d, 0.0);

    float2 e = float2(1.0, 0.0);
    float2 grad = float2(
        sdRoundRect(p + e.xy, halfSize, cornerRadius) - sdRoundRect(p - e.xy, halfSize, cornerRadius),
        sdRoundRect(p + e.yx, halfSize, cornerRadius) - sdRoundRect(p - e.yx, halfSize, cornerRadius)
    );
    float2 n = grad / max(length(grad), 0.0001);

    // Rounded slab: x is 0 at the rim and 1 where the glass turns flat.
    float x = clamp(inside / depth, 0.0, 1.0);
    float h = sqrt(max(1.0 - (1.0 - x) * (1.0 - x), 0.0));
    float slope = (1.0 - x) / max(h, 0.12);
    float maxBend = min(depth, max(inset.x, 1.0)) * 0.92;
    float bend = min(slope * depth * 0.30, maxBend);

    // What's beyond the edge bends into view; the middle is magnified a little.
    float zoom = 0.055 * clamp(depth / 40.0, 0.0, 1.0);
    float2 src = center + p * (1.0 - zoom) + n * bend;

    float4 base = float4(content.eval(src));
    float split = min(bend * dispersion * 0.22, maxBend - bend + 1.0);
    float red = float4(content.eval(src + n * split)).r;
    float blue = float4(content.eval(src - n * split)).b;
    float3 col = float3(red, base.g, blue);

    // Fresnel: the steep rim mirrors more of the light.
    float fresnel = pow(1.0 - h, 3.0);
    col = mix(col, float3(1.0), fresnel * 0.22 * specular);

    // Light on the edge: a thin bright line facing the light, a softer one on the far side.
    float facing = max(dot(n, light), 0.0);
    float away = max(dot(n, -light), 0.0);
    float rimLine = 1.0 - smoothstep(0.0, 2.2, inside);
    float band = 1.0 - smoothstep(0.0, max(depth * 0.55, 3.0), inside);
    float spec = rimLine * (pow(facing, 1.4) * 0.95 + pow(away, 2.0) * 0.55)
        + band * (pow(facing, 5.0) * 0.30 + pow(away, 5.0) * 0.14);
    col += float3(spec) * specular;
    // A touch of shade inside the bevel, away from the light: the glass has thickness.
    col *= 1.0 - band * 0.10 * (1.0 - facing);
    // Brighter towards the light across the whole face.
    col += float3(0.05 * specular * clamp(dot(normalize(p + float2(0.001)), light) * (1.0 - x * 0.5), 0.0, 1.0));

    // Glow under the finger.
    float radius = max(min(inner.x, inner.y) * 0.75, 1.0);
    float dist = length(coord - touch) / radius;
    col += float3(glow * 0.25 * exp(-dist * dist * 2.5));

    return half4(half3(col * edgeAlpha), half(edgeAlpha));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassEffect {
    private val shader = RuntimeShader(GLASS_SHADER)

    fun create(
        width: Float,
        height: Float,
        inset: Float,
        radius: Float,
        depth: Float,
        blur: Float,
        dispersion: Float,
        specular: Float,
        light: Offset,
        touch: Offset,
        glow: Float,
    ): RenderEffect? = runCatching {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("inset", inset, inset)
        shader.setFloatUniform("cornerRadius", radius)
        shader.setFloatUniform("depth", depth.coerceAtLeast(1f))
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("specular", specular)
        shader.setFloatUniform("light", light.x, light.y)
        shader.setFloatUniform("touch", touch.x, touch.y)
        shader.setFloatUniform("glow", glow)
        val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blur < 0.5f) {
            glass
        } else {
            val blurEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
            // Inner effect runs first: blur, then bend and light.
            RenderEffect.createChainEffect(glass, blurEffect)
        }
    }.getOrNull()
}

/**
 * Compiling the shader is the slow part, so all glass shares one. Safe: every effect takes
 * its own copy of the uniforms when it is created.
 */
private val SharedGlassEffect: GlassEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { GlassEffect() }.getOrNull() else null
}

/** A rounded rectangle [inset] in from every side (the lens canvas is bigger than the glass). */
private class InsetRoundedShape(private val inset: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius)),
        )
}

/** Which pre-blurred copy of the wallpaper glass looks through. */
private enum class Frost { None, Soft, Strong }

private fun frostFor(blurFactor: Float) = when {
    blurFactor < 0.15f -> Frost.None
    blurFactor > 1.7f -> Frost.Strong
    else -> Frost.Soft
}

private fun DrawScope.drawBackdrop(
    backdrop: WallpaperBackdrop,
    origin: Offset,
    frost: Frost = Frost.Soft,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    val image = when (frost) {
        Frost.None -> backdrop.image
        Frost.Soft -> backdrop.soft
        Frost.Strong -> backdrop.strong
    }
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
        dstSize = IntSize(backdrop.screenWidth, backdrop.screenHeight),
        colorFilter = Vibrancy,
        blendMode = blendMode,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * OMEGA UI 17.4: the frosted wallpaper behind [origin], for glass of any shape – with
 * [BlendMode.SrcIn] it fills only what was drawn before (the glass numerals).
 */
internal fun DrawScope.drawFrostedWallpaper(backdrop: WallpaperBackdrop, origin: Offset, blendMode: BlendMode) =
    drawBackdrop(backdrop, origin, Frost.Strong, blendMode)

/** Tracks the finger without consuming anything, so buttons inside still get their clicks. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackPress(
    onPress: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress(down.position)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull()?.let { onMove(it.position) }
            } while (event.changes.any { it.pressed })
        } finally {
            onRelease()
        }
    }
}

private val PullFollow = spring<Offset>(dampingRatio = 0.7f, stiffness = 700f)
private val PullBack = spring<Offset>(dampingRatio = 0.3f, stiffness = 320f)

/**
 * A liquid glass surface: blurs and bends the wallpaper behind it, catches light
 * on its rim and, if [interactive], swells and glows under the finger.
 *
 * - Android 13+: blur, lens refraction, color split, moving highlights
 * - Android 12: blur
 * - Older: pre-softened wallpaper
 * - No wallpaper access: frosted tint only
 */
@Composable
fun LiquidGlass(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    refraction: Dp = 18.dp,
    blur: Dp = 14.dp,
    tint: Color? = null,
    interactive: Boolean = false,
    /** One UI 10 Fluid: Claude's colors along the rim (off where the caller draws its own). */
    fluidEdge: Boolean = true,
    /** Dark mode tints this glass dark (app icons stay as they are). */
    darkens: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val fluid = LocalSettings.current.fluidDesign
    val style = LocalGlassStyle.current
    val light = LocalGlassLight.current
    val backdrop = LocalBackdrop.current
    // ZENITH Metall: ZENITH's own shape – two corners cut off like the ends of the Z's bars.
    // Projekt Zenith Glas keeps the round shape: the lens bends it at its rounded rim.
    val glassShape: androidx.compose.ui.graphics.Shape = if (style.zenith && !style.hudGlass) ZenithCutShape(cornerRadius * 0.7f) else RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val refractionPx = with(density) { refraction.toPx() } * style.refraction
    // blur only picks how frosted the glass is; the actual blur is precomputed.
    val frost = frostFor(style.blur * (blur.value / 14f).coerceIn(0.5f, 1.6f))
    val rimPx = with(density) { 1.dp.toPx() }
    val shaderMinPx = with(density) { style.lensMinDp.dp.toPx() }
    val maxPadPx = with(density) { 28.dp.toPx() }
    // One compiled shader for every glass surface, and none at all without the wallpaper.
    val glassEffect = if (backdrop != null) SharedGlassEffect else null
    var origin by remember { mutableStateOf(Offset.Zero) }
    // While the glass moves (paging, scrolling, opening animations) the lens pauses and the
    // glass is drawn the cheap way; it comes back a moment after everything stands still.
    // Running the lens on every moving frame of every surface was what made swiping stutter.
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { origin }.collectLatest {
            moving = true
            delay(170)
            moving = false
        }
    }

    val isInteractive = interactive && style.interactive
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 120 else 500),
        label = "glassGlow",
    )
    // Liquid: width and height spring on their own, a little out of step, so on letting go
    // the glass wobbles like a drop of jelly settling instead of just shrinking back.
    val swellX by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 300f),
        label = "glassSwellX",
    )
    val swellY by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.34f, stiffness = 460f),
        label = "glassSwellY",
    )
    // Liquid: the glass is pulled along with the finger and wobbles back when let go. Driven
    // straight from the touch, so a moving finger doesn't recompose the glass on every move.
    var pressStart by remember { mutableStateOf(Offset.Zero) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val maxPullPx = with(density) { 9.dp.toPx() }

    // Dark mode: deep night-blue glass under whatever tint the caller asks for.
    val baseTint = if (style.dark && darkens) tint?.compositeOver(DarkGlassTint) ?: DarkGlassTint else tint ?: style.tint
    val shaderActive = backdrop != null && glassEffect != null
    // Without the wallpaper the glass needs more body to read as a surface.
    val fill = if (backdrop != null) baseTint else baseTint.copy(alpha = (baseTint.alpha + 0.14f).coerceAtMost(1f))
    val specular = style.specular

    Box(
        Modifier
            .graphicsLayer {
                if (isInteractive) {
                    // Liquid: a bit wider than tall while pressed, stretched towards where the
                    // finger pulls (thinner the other way, like a drop), then wobbles back.
                    val w = size.width.coerceAtLeast(1f)
                    val h = size.height.coerceAtLeast(1f)
                    val pulled = pull.value
                    val px = (pulled.x / w).coerceIn(-1f, 1f)
                    val py = (pulled.y / h).coerceIn(-1f, 1f)
                    // Smaller glass gives more, big sheets only a little.
                    val give = (120.dp.toPx() / maxOf(w, h)).coerceIn(0.35f, 1f)
                    scaleX = 1f + (0.09f * swellX - 0.025f * swellY + abs(px) * 0.14f - abs(py) * 0.05f) * give
                    scaleY = 1f + (0.07f * swellY - 0.02f * swellX + abs(py) * 0.14f - abs(px) * 0.05f) * give
                    translationX = (pulled.x * 0.16f).coerceIn(-maxPullPx, maxPullPx)
                    translationY = (pulled.y * 0.16f).coerceIn(-maxPullPx, maxPullPx)
                }
            }
            .then(modifier)
            // One UI 10 Fluid on every glass surface: Claude's colors along the rim, and a
            // touch spreading like liquid light where it can be pressed.
            // (The glass glows under the finger itself, so no second touch layer on top.)
            .aiFluidEdge(cornerRadius, strength = if (LocalSettings.current.omegaGlass) 0.55f else 0.3f, width = 1.dp, enabled = fluid && fluidEdge && !style.zenith, flowing = false)
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        trackPress(
                            onPress = {
                                pressed = true
                                touch = it
                                pressStart = it
                                scope.launch { pull.snapTo(Offset.Zero) }
                            },
                            onMove = {
                                touch = it
                                scope.launch { pull.animateTo(it - pressStart, PullFollow) }
                            },
                            onRelease = {
                                pressed = false
                                scope.launch { pull.animateTo(Offset.Zero, PullBack) }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        if (backdrop != null) {
            val lensAvailable = glassEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val lensPad = refractionPx.coerceIn(2f, maxPadPx).roundToInt()
            // The lens draws a little more of the backdrop than its own size, because the rim
            // shows what lies just beyond it. Small glass (below "Glas-Qualität") skips it.
            fun padFor(innerW: Float, innerH: Float): Int =
                if (lensAvailable && min(innerW, innerH) >= shaderMinPx) lensPad else 0
            Canvas(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val pad = padFor(w.toFloat(), h.toFloat())
                        val placeable = measurable.measure(Constraints.fixed(w + 2 * pad, h + 2 * pad))
                        layout(w, h) { placeable.place(-pad, -pad) }
                    }
                    .graphicsLayer {
                        val padded = lensPad * 2f
                        val lens = lensAvailable && min(size.width - padded, size.height - padded) >= shaderMinPx
                        if (lens && !moving && glassEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val innerMin = min(size.width, size.height) - padded
                            // The shader cuts the shape out itself, with soft edges.
                            clip = false
                            renderEffect = glassEffect.create(
                                width = size.width,
                                height = size.height,
                                inset = lensPad.toFloat(),
                                radius = min(radiusPx, innerMin / 2f),
                                depth = min(refractionPx, innerMin / 2f),
                                blur = 0f,
                                dispersion = style.dispersion,
                                specular = specular,
                                light = light.value,
                                touch = touch + Offset(lensPad.toFloat(), lensPad.toFloat()),
                                glow = glow,
                            )?.asComposeRenderEffect()
                        } else if (lens) {
                            // Bigger canvas than the glass: cut out just the glass.
                            val innerMin = min(size.width, size.height) - padded
                            clip = true
                            shape = InsetRoundedShape(lensPad.toFloat(), min(radiusPx, innerMin / 2f))
                            renderEffect = null
                        } else {
                            clip = true
                            shape = glassShape
                            renderEffect = null
                        }
                    },
            ) {
                val padded = lensPad * 2f
                val pad = if (lensAvailable && min(size.width - padded, size.height - padded) >= shaderMinPx) lensPad else 0
                drawBackdrop(backdrop, origin - Offset(pad.toFloat(), pad.toFloat()), frost)
            }
        }

        // Tint, sheen towards the light, the light-catching rim and (without shader) the touch glow.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    // ZENITH Metall: the logo's metal and light instead of glass.
                    if (style.hud && !style.hudGlass) {
                        drawHudSurface(glassShape.createOutline(size, layoutDirection, this), glow, touch)
                        return@drawBehind
                    }
                    if (style.zenith && !style.hudGlass) {
                        drawZenithSurface(glassShape.createOutline(size, layoutDirection, this), fill, glow, touch)
                        return@drawBehind
                    }
                    val outline = glassShape.createOutline(size, layoutDirection, this)
                    val l = light.value
                    val reach = size.maxDimension / 2f
                    val lit = center + Offset(l.x, l.y) * reach
                    val shade = center - Offset(l.x, l.y) * reach

                    drawOutline(outline, fill)
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = (0.14f * specular).coerceIn(0f, 1f)),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.07f),
                            start = lit,
                            end = shade,
                        ),
                    )
                    // Does the lens shader do the edge here? Otherwise draw a thick, curved edge.
                    val lensOn = shaderActive && size.minDimension >= shaderMinPx
                    if (!lensOn) {
                        val r = min(radiusPx, size.minDimension / 2f)
                        val path = Path().apply {
                            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                        }
                        val bevel = (refractionPx * 0.45f).coerceIn(rimPx * 2f, size.minDimension * 0.22f)
                        clipPath(path) {
                            // The rounded edge: light where it faces the light, a darker
                            // fold on the far side, clear in between.
                            drawPath(
                                path,
                                Brush.linearGradient(
                                    0f to Color.White.copy(alpha = (0.30f * specular).coerceIn(0f, 1f)),
                                    0.45f to Color.White.copy(alpha = 0.03f),
                                    0.55f to Color.Transparent,
                                    1f to Color.White.copy(alpha = (0.14f * specular).coerceIn(0f, 1f)),
                                    start = lit,
                                    end = shade,
                                ),
                                style = Stroke(width = bevel * 2f),
                            )
                            // A highlight pooled near the lit corner, like light inside a drop.
                            drawCircle(
                                Brush.radialGradient(
                                    0f to Color.White.copy(alpha = (0.20f * specular).coerceIn(0f, 1f)),
                                    1f to Color.Transparent,
                                    center = center + Offset(l.x, l.y) * reach * 0.7f,
                                    radius = size.minDimension * 0.55f,
                                ),
                                radius = size.minDimension * 0.55f,
                                center = center + Offset(l.x, l.y) * reach * 0.7f,
                            )
                            // Thickness: a soft shade along the far edge.
                            drawPath(
                                path,
                                Brush.linearGradient(
                                    0f to Color.Transparent,
                                    0.7f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.10f),
                                    start = lit,
                                    end = shade,
                                ),
                                style = Stroke(width = bevel * 3f),
                            )
                        }
                    }
                    val rimStrong = (if (lensOn) 0.40f else 0.85f) * specular
                    val rimWeak = (if (lensOn) 0.18f else 0.45f) * specular
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = rimStrong.coerceIn(0f, 1f)),
                            0.4f to Color.White.copy(alpha = 0.06f),
                            0.6f to Color.White.copy(alpha = 0.04f),
                            1f to Color.White.copy(alpha = rimWeak.coerceIn(0f, 1f)),
                            start = lit,
                            end = shade,
                        ),
                        style = Stroke(width = rimPx),
                    )
                    // OMEGA UI 18.5, Liquid Glass: Apple's bright inner edge – light caught all
                    // round the glass, strongest where it faces the light and opposite.
                    if (style.liquid) {
                        val inset = rimPx * 1.6f
                        val r = (min(radiusPx, size.minDimension / 2f) - inset).coerceAtLeast(0f)
                        drawRoundRect(
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = (0.75f * specular).coerceIn(0f, 1f)),
                                0.3f to Color.White.copy(alpha = 0.12f),
                                0.7f to Color.White.copy(alpha = 0.1f),
                                1f to Color.White.copy(alpha = (0.45f * specular).coerceIn(0f, 1f)),
                                start = lit,
                                end = shade,
                            ),
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - 2 * inset, size.height - 2 * inset),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(width = rimPx * 1.3f),
                        )
                    }
                    // Projekt Zenith Glas: the hologram's green frame and scan lines in the glass.
                    if (style.hudGlass) drawHudGlass(outline, glow)
                    if (!lensOn && glow > 0.01f) {
                        drawOutline(
                            outline,
                            Brush.radialGradient(
                                0f to Color.White.copy(alpha = 0.28f * glow),
                                1f to Color.Transparent,
                                center = touch,
                                radius = size.minDimension.coerceAtLeast(1f),
                            ),
                        )
                    }
                },
        )

        content()
    }
}

/** Full-screen frosted glass, used behind search, menus and settings. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun GlassBackdropFill(blur: Dp, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { clip = true },
    ) {
        drawBackdrop(backdrop, origin, Frost.Strong)
    }
}

/** Softly fades scrolling content out at the top and bottom instead of cutting it off hard. */
fun Modifier.fadingEdges(top: Dp = 16.dp, bottom: Dp = 40.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.45f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.45f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                t to Color.Black,
                (1f - b) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
