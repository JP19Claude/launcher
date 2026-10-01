package dev.hearth.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AccentColor
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.IconKind
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.IconStyle
import dev.hearth.launcher.data.LauncherSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Current launcher settings for every composable below the screen. */
val LocalSettings = compositionLocalOf { LauncherSettings() }

/** Superellipse (n = 5): the soft-cornered "squircle" known from iOS icons. */
val SquircleShape: Shape = GenericShape { size, _ ->
    val n = 5.0
    val steps = 96
    val a = size.width / 2f
    val b = size.height / 2f
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (sign(c) * abs(c).pow(2.0 / n)).toFloat()
        val y = b + b * (sign(s) * abs(s).pow(2.0 / n)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val RoundedIconShape = RoundedCornerShape(percent = 22)

fun IconShape.clipShape(): Shape = when (this) {
    IconShape.Squircle -> SquircleShape
    IconShape.Circle -> CircleShape
    IconShape.Rounded -> RoundedIconShape
}

/** Corner radius for glass tiles; the glass shader works with rounded rectangles. */
fun IconShape.glassCorner(size: Dp): Dp = when (this) {
    IconShape.Squircle -> size * 0.3f
    IconShape.Circle -> size / 2
    IconShape.Rounded -> size * 0.22f
}

/** Text drawn on the wallpaper gets a soft shadow so it stays readable on bright images. */
internal val OnWallpaperText = TextStyle(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.35f),
        offset = Offset(0f, 2f),
        blurRadius = 8f,
    ),
)

@Immutable
class AppActions(
    val launch: (AppInfo) -> Unit,
    /** Long press: opens the context menu next to the icon (bounds in root coordinates). */
    val menu: (AppInfo, Rect) -> Unit,
)

/** Turns a colored symbol into a single color, keeping its shading. */
private fun monoTint(color: Color, gain: Float = 1.25f): ColorFilter {
    val r = color.red * gain
    val g = color.green * gain
    val b = color.blue * gain
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.30f * r, 0.59f * r, 0.11f * r, 0f, 40f * color.red,
                0.30f * g, 0.59f * g, 0.11f * g, 0f, 40f * color.green,
                0.30f * b, 0.59f * b, 0.11f * b, 0f, 40f * color.blue,
                0f, 0f, 0f, 0.95f, 0f,
            ),
        ),
    )
}

private fun glyphFilter(style: IconStyle, kind: IconKind, accent: AccentColor): ColorFilter? = when (style) {
    IconStyle.Original, IconStyle.Glass -> null
    IconStyle.Clear ->
        if (kind == IconKind.MonoGlyph) ColorFilter.tint(Color.White.copy(alpha = 0.95f), BlendMode.SrcIn)
        else monoTint(Color.White)
    IconStyle.Tinted ->
        if (kind == IconKind.MonoGlyph) ColorFilter.tint(accent.color, BlendMode.SrcIn)
        else monoTint(accent.color)
}

/** The icon itself, framed according to the icon style: plain, cut to shape, or on a glass tile. */
@Composable
fun AppIconImage(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    when (app.iconKind) {
        IconKind.Shaped -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = modifier
                .size(size)
                .shadow(elevation = 6.dp, shape = settings.iconShape.clipShape(), clip = true),
        )

        IconKind.Free -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = modifier.size(size),
        )

        IconKind.Glyph, IconKind.MonoGlyph -> LiquidGlass(
            cornerRadius = settings.iconShape.glassCorner(size),
            refraction = size * 0.2f,
            blur = 10.dp,
            modifier = modifier.size(size),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                colorFilter = glyphFilter(settings.iconStyle, app.iconKind, settings.accent),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    actions: AppActions,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onWallpaper: Boolean = true,
    fillCell: Boolean = false,
    onLaunch: (AppInfo) -> Unit = actions.launch,
) {
    val settings = LocalSettings.current
    val iconSize = settings.iconSize.dp
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "iconPress",
    )
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .then(if (fillCell) Modifier.fillMaxWidth() else Modifier.width(iconSize + 18.dp))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.menu(app, bounds)
                    },
                )
                .padding(vertical = 6.dp),
        ) {
            AppIconImage(
                app = app,
                size = iconSize,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
            if (showLabel && settings.showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = if (onWallpaper) OnWallpaperText else TextStyle.Default,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
