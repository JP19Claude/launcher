package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
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
import dev.hearth.launcher.data.has
import dev.hearth.launcher.data.liquidOmega
import dev.hearth.launcher.data.hologram
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
import kotlin.math.cos
import kotlin.math.sin

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
    // OMEGA UI 17.5 hotfix: dark mode reaches here too – much darker, the colors only glowing.
    val dark = LocalGlassStyle.current.dark
    // OMEGA UI 18.5, Liquid Glass: neutral and light, like Apple's – no tint over the screen.
    val liquid = s.liquidOmega
    val shade = when {
        !hasGlass -> noGlassDim
        dark -> maxOf(if (omega) omegaDim else dim, 0.6f)
        liquid -> omegaDim * 0.8f
        omega -> omegaDim
        else -> dim
    }
    val colorDepth = if (dark) 0.45f else 1f
    Box(modifier) {
        GlassBackdropFill(blur = blur, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color(if (dark) 0xFF05040C else 0xFF06060C).copy(alpha = shade)),
        )
        // OMEGA Glass: tinted like OMEGA glass – deepest at the top and once more at the bottom.
        if (omega && !liquid) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette[0].copy(alpha = 0.28f * colorDepth),
                            0.4f to palette[0].copy(alpha = 0.05f * colorDepth),
                            1f to palette[1].copy(alpha = 0.2f * colorDepth),
                        ),
                    ),
            )
        }
        // The colors drift behind everything (and hold still while something scrolls).
        FluidBackdrop(palette, modifier = Modifier.matchParentSize(), strength = (if (omega) fluid * 1.2f else fluid) * (if (dark) 0.6f else 1f))
    }
}

/**
 * OMEGA UI 17.5: the home screen's OMEGA light, between the wallpaper and the apps and
 * widgets – OMEGA Fluid glowing from the top corner behind the clock, pooling at the bottom
 * behind the dock and drifting softly through the middle. Dimmer in dark mode.
 */
@Composable
internal fun OmegaHomeLight(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    // ZENITH: its own light – dark like the logo's ground, green rising from below.
    if (s.omegaColor == dev.hearth.launcher.data.OmegaColor.Zenith && s.omegaLight && !s.has(dev.hearth.launcher.data.Perk.DayLight)) {
        // (Projekt Zenith brings its own light with its horizon.)
        if (!s.hologram) ZenithHomeLight(modifier)
        return
    }
    if (!s.omegaGlass || !s.omegaLight || !s.fluidDesign) return
    // OMEGA UI 18, Tageszeit-Licht: the light follows the day.
    val hour = if (s.has(dev.hearth.launcher.data.Perk.DayLight)) java.time.LocalTime.now().hour else -1
    val palette = when (hour) {
        -1 -> fluidPalette()
        in 5..10 -> listOf(Color(0xFFFFC46B), Color(0xFFFF9F45), Color(0xFFFFE0A3), Color(0xFFFFB86B), Color(0xFFFFF1D0))
        in 11..16 -> listOf(Color(0xFF9CD8FF), Color(0xFFFFF2B3), Color(0xFFBDE8FF), Color(0xFFFFFFFF), Color(0xFFE6F6FF))
        in 17..20 -> listOf(Color(0xFFFF5A3C), Color(0xFFC8102E), Color(0xFFFF8A5C), Color(0xFFFF4F7A), Color(0xFFFFC2A8))
        else -> listOf(Color(0xFF2F4BFF), Color(0xFF14206B), Color(0xFF5C7CFF), Color(0xFF8E6BFF), Color(0xFFBFD0FF))
    }
    val dark = LocalGlassStyle.current.dark
    val power = (if (dark) 0.6f else 1f) * (if (s.omegaOverdrive) 1.3f else 1f) * (if (s.liquidOmega) 0.5f else 1f)
    // OMEGA UI 18.3: the light stands still (only Ω-Overdrive lets it drift) – drifting, it
    // redrew the whole home screen and every glass on it, frame after frame.
    val flow = flowPhase(30_000, s.animations && s.omegaOverdrive)
    Canvas(modifier) {
        val t = flow?.invoke() ?: 1f
        val w = size.width
        val h = size.height
        fun glow(color: Color, at: Offset, radius: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = (alpha * power).coerceIn(0f, 1f)), Color.Transparent), center = at, radius = radius),
                radius = radius,
                center = at,
            )
        }
        // From the top corner, behind the clock.
        glow(palette[0], Offset(w * (0.12f + 0.08f * sin(t)), h * (0.08f + 0.04f * cos(t * 1.3f))), w * 0.95f, 0.3f)
        // Drifting through the middle, behind the apps and widgets.
        glow(palette[2], Offset(w * (0.5f + 0.32f * cos(t + 1.7f)), h * (0.46f + 0.12f * sin(t * 0.8f))), w * 0.7f, 0.2f)
        glow(palette[3 % palette.size], Offset(w * (0.5f + 0.3f * cos(t * 0.7f + 4f)), h * (0.6f + 0.1f * sin(t + 2f))), w * 0.6f, 0.16f)
        // Pooling at the bottom, behind the dock.
        glow(palette[1], Offset(w * (0.75f - 0.15f * sin(t * 0.9f)), h * 1.02f), w * 0.9f, 0.34f)
        drawRect(
            Brush.verticalGradient(
                0.78f to Color.Transparent,
                1f to palette[1].copy(alpha = (0.22f * power).coerceIn(0f, 1f)),
            ),
        )
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
    // Dark mode: a deeper sheet (and LiquidGlass lays its night tint over it as well).
    val dark = LocalGlassStyle.current.dark
    val tint = LocalGlassStyle.current.tint.compositeOver(Color.Black.copy(alpha = if (dark) 0.5f else 0.3f))
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
                    .fluidGlow(strength = if (omega) 0.9f else 0.6f, flowing = s.omegaOverdrive),
            )
        }
        // OMEGA UI 17.5: a big sign etched into the glass, low in its corner – ZENITH's Z now.
        if (omega) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(corner)),
            ) {
                ZenithMark(
                    Color.White.copy(alpha = 0.05f),
                    210.dp,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 40.dp, y = 30.dp),
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
        // Claude Mythos: more Clawds on the rim, big and small.
        if (perch > 0.dp) MythosPerch(seed = 18, biggest = perch + 8.dp)
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
