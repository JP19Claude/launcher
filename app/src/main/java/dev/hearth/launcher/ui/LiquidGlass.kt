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
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.WallpaperBackdrop
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
) {
    companion object {
        fun from(s: LauncherSettings) = GlassStyle(
            blur = s.glassBlur,
            refraction = s.glassRefraction,
            dispersion = s.glassDispersion,
            specular = s.glassSpecular,
            tint = s.glassTint.color.copy(alpha = (s.glassTint.color.alpha * s.glassTintStrength).coerceIn(0f, 1f)),
            interactive = s.glassInteractive,
        )
    }
}

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

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
                if (abs(old.x - x) + abs(old.y - y) > 0.01f) light.value = Offset(x, y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
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
 * Liquid glass on the GPU (Android 13+):
 * - bends the image at the rim like the rounded edge of a thick lens, and magnifies the middle a little
 * - splits colors where the bend is strongest
 * - specular highlights on the rim that face the light (and a weaker one on the opposite side)
 * - a soft glow where the finger touches
 */
private const val GLASS_SHADER = """
uniform shader content;
uniform float2 size;
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
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, cornerRadius);
    float inside = max(-d, 0.0);

    float2 e = float2(1.0, 0.0);
    float2 grad = float2(
        sdRoundRect(p + e.xy, halfSize, cornerRadius) - sdRoundRect(p - e.xy, halfSize, cornerRadius),
        sdRoundRect(p + e.yx, halfSize, cornerRadius) - sdRoundRect(p - e.yx, halfSize, cornerRadius)
    );
    float2 n = grad / max(length(grad), 0.0001);

    // Bevel: flat in the middle, curving down steeply towards the rim like a droplet.
    float t = clamp(1.0 - inside / depth, 0.0, 1.0);
    float bevel = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    float bend = bevel * depth;

    float zoom = 0.07 * clamp(depth / 40.0, 0.0, 1.0);
    float2 src = halfSize + p * (1.0 - zoom) - n * bend;

    float4 base = float4(content.eval(src));
    float split = bend * dispersion * 0.4;
    float red = float4(content.eval(src - n * split)).r;
    float blue = float4(content.eval(src + n * split)).b;
    float3 col = float3(red, base.g, blue);

    // Rim light: strong where the edge faces the light, weaker on the far side.
    float rim = 1.0 - smoothstep(0.0, max(depth * 0.5, 2.0), inside);
    float facing = max(dot(n, light), 0.0);
    float away = max(dot(n, -light), 0.0);
    float spec = rim * (pow(facing, 2.0) * 0.9 + pow(away, 3.0) * 0.4);
    float sheen = t * t * facing * 0.2;
    col += float3(spec + sheen) * specular;

    // Glow under the finger.
    float radius = max(min(size.x, size.y) * 0.75, 1.0);
    float dist = length(coord - touch) / radius;
    col += float3(glow * 0.25 * exp(-dist * dist * 2.5));

    return half4(half3(col), half(base.a));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassEffect {
    private val shader = RuntimeShader(GLASS_SHADER)

    fun create(
        width: Float,
        height: Float,
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

private fun DrawScope.drawBackdrop(backdrop: WallpaperBackdrop, origin: Offset) {
    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(backdrop.image.width, backdrop.image.height),
        dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
        dstSize = IntSize(backdrop.screenWidth, backdrop.screenHeight),
        colorFilter = Vibrancy,
        filterQuality = FilterQuality.Low,
    )
}

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
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalGlassStyle.current
    val light = LocalGlassLight.current
    val backdrop = LocalBackdrop.current
    val glassShape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val refractionPx = with(density) { refraction.toPx() } * style.refraction
    val blurPx = with(density) { blur.toPx() } * style.blur
    val rimPx = with(density) { 1.dp.toPx() }
    val glassEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { GlassEffect() }.getOrNull() else null
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    val isInteractive = interactive && style.interactive
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 120 else 500),
        label = "glassGlow",
    )
    val swell by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "glassSwell",
    )

    val baseTint = tint ?: style.tint
    val shaderActive = backdrop != null && glassEffect != null
    // Without the wallpaper the glass needs more body to read as a surface.
    val fill = if (backdrop != null) baseTint else baseTint.copy(alpha = (baseTint.alpha + 0.14f).coerceAtMost(1f))
    val specular = style.specular

    Box(
        Modifier
            .graphicsLayer {
                if (isInteractive) {
                    // Liquid: a bit wider than tall while pressed, then wobbles back.
                    scaleX = 1f + 0.08f * swell
                    scaleY = 1f + 0.05f * swell
                }
            }
            .then(modifier)
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        trackPress(
                            onPress = { pressed = true; touch = it },
                            onMove = { touch = it },
                            onRelease = { pressed = false },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        if (backdrop != null) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = glassShape
                        renderEffect = when {
                            glassEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                glassEffect.create(
                                    width = size.width,
                                    height = size.height,
                                    radius = min(radiusPx, size.minDimension / 2f),
                                    depth = min(refractionPx, size.minDimension / 2f),
                                    blur = blurPx,
                                    dispersion = style.dispersion,
                                    specular = specular,
                                    light = light.value,
                                    touch = touch,
                                    glow = glow,
                                )?.asComposeRenderEffect()
                                    ?: if (blurPx >= 0.5f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null

                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx >= 0.5f ->
                                BlurEffect(blurPx, blurPx, TileMode.Clamp)

                            else -> null
                        }
                    },
            ) {
                drawBackdrop(backdrop, origin)
            }
        }

        // Tint, sheen towards the light, the light-catching rim and (without shader) the touch glow.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
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
                    val rimStrong = (if (shaderActive) 0.40f else 0.70f) * specular
                    val rimWeak = (if (shaderActive) 0.18f else 0.35f) * specular
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
                    if (!shaderActive && glow > 0.01f) {
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
fun GlassBackdropFill(blur: Dp, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current ?: return
    val blurPx = with(LocalDensity.current) { blur.toPx() } * LocalGlassStyle.current.blur.coerceAtLeast(0.3f)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer {
                clip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx >= 0.5f) {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                }
            },
    ) {
        drawBackdrop(backdrop, origin)
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
