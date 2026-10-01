package dev.hearth.launcher.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.WallpaperBackdrop
import dev.hearth.launcher.ui.theme.HearthColors
import kotlin.math.min
import kotlin.math.roundToInt

/** The wallpaper copy that glass surfaces look through. Null = frosted fallback. */
val LocalBackdrop = compositionLocalOf<WallpaperBackdrop?> { null }

/** Glass boosts the colors behind it a little, like real glass catching light. */
private val Vibrancy = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/**
 * Bends the image near the edges like a thick glass lens, with a slight
 * color split where the bend is strongest. Runs on the GPU (Android 13+).
 */
private const val REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float cornerRadius;
uniform float depth;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, cornerRadius);

    // 0 in the middle, rising to 1 at the rim
    float edge = clamp(1.0 + d / depth, 0.0, 1.0);

    float2 e = float2(1.0, 0.0);
    float2 grad = float2(
        sdRoundRect(p + e.xy, halfSize, cornerRadius) - sdRoundRect(p - e.xy, halfSize, cornerRadius),
        sdRoundRect(p + e.yx, halfSize, cornerRadius) - sdRoundRect(p - e.yx, halfSize, cornerRadius)
    );
    float2 n = grad / max(length(grad), 0.0001);

    float bend = edge * edge * depth;
    float2 src = coord - n * bend;

    half4 base = content.eval(src);
    half r = content.eval(src - n * bend * 0.12).r;
    half b = content.eval(src + n * bend * 0.12).b;
    return half4(r, base.g, b, base.a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RefractionEffect {
    private val shader = RuntimeShader(REFRACTION_SHADER)

    fun create(width: Float, height: Float, radius: Float, depth: Float, blur: Float): RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("cornerRadius", radius)
        shader.setFloatUniform("depth", depth.coerceAtLeast(1f))
        val refraction = RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blur <= 0f) return refraction
        val blurEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
        // Inner effect runs first: blur, then bend.
        return RenderEffect.createChainEffect(refraction, blurEffect)
    }
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

/**
 * A liquid glass surface: blurs and bends the wallpaper behind it, adds a soft
 * top sheen and a light-catching rim. Content is drawn on top.
 *
 * - Android 13+: blur + edge refraction + color split
 * - Android 12: blur
 * - Older: pre-softened wallpaper
 * - No wallpaper access: frosted tint only
 */
@Composable
fun LiquidGlass(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    refraction: Dp = 14.dp,
    blur: Dp = 16.dp,
    tint: Color = HearthColors.Limestone.copy(alpha = 0.10f),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val glassShape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val refractionPx = with(density) { refraction.toPx() }
    val blurPx = with(density) { blur.toPx() }
    val refractionEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RefractionEffect() else null
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        if (backdrop != null) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = glassShape
                        renderEffect = when {
                            refractionEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                refractionEffect.create(
                                    width = size.width,
                                    height = size.height,
                                    radius = min(radiusPx, size.minDimension / 2f),
                                    depth = refractionPx,
                                    blur = blurPx,
                                ).asComposeRenderEffect()

                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                                BlurEffect(blurPx, blurPx, TileMode.Clamp)

                            else -> null
                        }
                    },
            ) {
                drawBackdrop(backdrop, origin)
            }
        }

        // Tint, top sheen, bottom depth and the light-catching rim.
        val fill = if (backdrop != null) tint else tint.copy(alpha = (tint.alpha + 0.10f).coerceAtMost(1f))
        Box(
            Modifier
                .matchParentSize()
                .clip(glassShape)
                .background(fill)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.45f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.08f),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.60f),
                        0.35f to Color.White.copy(alpha = 0.08f),
                        0.65f to Color.White.copy(alpha = 0.04f),
                        1f to Color.White.copy(alpha = 0.35f),
                    ),
                    shape = glassShape,
                ),
        )

        content()
    }
}

/** Full-screen frosted glass, used behind the search screen. */
@Composable
fun GlassBackdropFill(blur: Dp, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current ?: return
    val blurPx = with(LocalDensity.current) { blur.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer {
                clip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                }
            },
    ) {
        drawBackdrop(backdrop, origin)
    }
}
