package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * OMEGA UI 17.4: behind a whole screen of glass (finder, drawer) – the frosted wallpaper,
 * dimmed by [dim] ([omegaDim] with OMEGA Glass, [noGlassDim] without the wallpaper), tinted
 * like OMEGA glass and with the fluid colors drifting.
 */
@Composable
internal fun OmegaScreenBackdrop(
    modifier: Modifier = Modifier,
    blur: Dp = 40.dp,
    dim: Float = 0.34f,
    omegaDim: Float = 0.24f,
    noGlassDim: Float = 0.94f,
    fluid: Float = 0.7f,
) {
    val s = LocalSettings.current
    val omega = s.omegaGlass
    val hasGlass = LocalBackdrop.current != null
    val palette = fluidPalette()
    Box(modifier) {
        GlassBackdropFill(blur = blur, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xFF06060C).copy(alpha = if (!hasGlass) noGlassDim else if (omega) omegaDim else dim)),
        )
        // OMEGA Glass: tinted like OMEGA glass – deepest at the top and once more at the bottom.
        if (omega) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette[0].copy(alpha = 0.28f),
                            0.4f to palette[0].copy(alpha = 0.05f),
                            1f to palette[1].copy(alpha = 0.2f),
                        ),
                    ),
            )
        }
        // The colors drift behind everything (and hold still while something scrolls).
        FluidBackdrop(palette, modifier = Modifier.matchParentSize(), strength = if (omega) fluid * 1.2f else fluid)
    }
}

/**
 * OMEGA UI 17.4: one big sheet of glass for a screen's content (the finder's results, the
 * drawer's apps): OMEGA glass, a little deeper so labels stay clear, the fluid colors drifting
 * inside and lying along its rim. Its size comes from [modifier].
 */
@Composable
internal fun OmegaGlassSheet(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    /** OMEGA UI 17.5: Clawd sitting on the sheet's top rim (this big; 0 for none). */
    perch: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalSettings.current
    val omega = s.omegaGlass
    val tint = LocalGlassStyle.current.tint.compositeOver(Color.Black.copy(alpha = 0.3f))
    LiquidGlass(
        cornerRadius = corner,
        refraction = 22.dp,
        tint = tint,
        fluidEdge = false,
        modifier = modifier.aiFluidEdge(
            corner,
            strength = if (omega) 0.85f else 0.5f,
            width = Dp(1.5f),
            enabled = s.fluidDesign,
            flowing = false,
        ),
    ) {
        if (s.fluidDesign) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(corner))
                    .fluidGlow(strength = if (omega) 0.9f else 0.6f, flowing = omega),
            )
        }
        // OMEGA UI 17.5: a big Ω etched into the glass, low in its corner.
        if (omega) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(corner)),
            ) {
                Text(
                    "Ω",
                    color = Color.White.copy(alpha = 0.05f),
                    fontSize = 190.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 26.dp, y = 56.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner)),
            content = content,
        )
        if (perch > 0.dp) PerchedClawd(size = perch)
    }
}

/**
 * OMEGA UI 17.5: Clawd sitting on the top rim of a panel, his legs over its edge – with OMEGA
 * Glass on. The panel must not clip (he sits just outside it).
 */
@Composable
internal fun BoxScope.PerchedClawd(
    alignment: Alignment = Alignment.TopEnd,
    size: Dp = 28.dp,
    mood: ClawdMood = ClawdMood.Idle,
    inset: Dp = 28.dp,
) {
    if (!LocalSettings.current.omegaGlass) return
    Clawd(
        Modifier
            .align(alignment)
            .padding(horizontal = inset)
            .offset(y = -size * 0.8f)
            .size(width = size * (14f / 11f), height = size),
        mood = mood,
    )
}

/**
 * OMEGA UI 17.4: numerals made of glass, like a lock screen clock of liquid glass. Inside the
 * digits the frosted wallpaper shows through, lighter and tinted like OMEGA glass, with light
 * along the top; a rim of OMEGA Fluid (white light without OMEGA) runs round every digit, and
 * a soft dark edge outside gives them thickness. They still roll like the plain clock.
 */
@Composable
internal fun GlassNumerals(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val s = LocalSettings.current
    val omega = s.omegaGlass
    val fluidRim = omega && s.fluidDesign
    val backdrop = LocalBackdrop.current
    val glassTint = LocalGlassStyle.current.tint
    val palette = fluidPalette()
    val density = LocalDensity.current
    val rimPx = with(density) { 1.6.dp.toPx() }
    val edgePx = with(density) { 4.dp.toPx() }
    val edgeStyle = remember(edgePx) {
        TextStyle(
            drawStyle = Stroke(width = edgePx, join = StrokeJoin.Round),
            shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 3f), 14f),
        )
    }
    val rimStyle = remember(rimPx) { TextStyle(drawStyle = Stroke(width = rimPx, join = StrokeJoin.Round)) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        // Thickness: a soft dark edge just outside the glass, with a shadow under it.
        RollingText(
            text = text,
            color = Color.Black.copy(alpha = 0.2f),
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            style = edgeStyle,
        )
        // The glass itself: the digits cut the frosted wallpaper out, a little lighter, tinted,
        // and catching light along the top.
        RollingText(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (backdrop != null) drawFrostedWallpaper(backdrop, origin, BlendMode.SrcIn)
                    drawRect(Color.White.copy(alpha = if (backdrop != null) 0.24f else 0.5f), blendMode = BlendMode.SrcAtop)
                    drawRect(if (omega) glassTint else Color.White.copy(alpha = 0.06f), blendMode = BlendMode.SrcAtop)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.5f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.1f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
        // The rim: OMEGA Fluid round every digit, or white light, brightest at the top.
        RollingText(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            style = rimStyle,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val brush = if (fluidRim) {
                        Brush.linearGradient(
                            listOf(Color.White, palette[0], palette[2], palette[1], Color.White.copy(alpha = 0.85f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White,
                            0.5f to Color.White.copy(alpha = 0.45f),
                            1f to Color.White.copy(alpha = 0.8f),
                        )
                    }
                    drawRect(brush, blendMode = BlendMode.SrcIn)
                },
        )
    }
}
