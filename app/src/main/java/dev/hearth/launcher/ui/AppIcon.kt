package dev.hearth.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
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
import dev.hearth.launcher.data.BadgeStyle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
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

private val IconLight = Offset(-0.45f, -0.89f)

/** Current launcher settings for every composable below the screen. */
val LocalSettings = compositionLocalOf { LauncherSettings() }

/** Multi-select mode: tapping icons marks them instead of opening them. */
@Immutable
class SelectionState(
    val active: Boolean = false,
    val selected: Set<String> = emptySet(),
    val toggle: (AppInfo) -> Unit = {},
)

val LocalSelection = compositionLocalOf { SelectionState() }

/** Unread notifications per package, from the notification listener. */
val LocalBadges = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** Red badge with the number of unread notifications (or a dot), popping in with a spring. */
@Composable
fun NotificationBadge(app: AppInfo, modifier: Modifier = Modifier) {
    val style = LocalSettings.current.badgeStyle
    val count = LocalBadges.current[app.packageName] ?: 0
    if (style == BadgeStyle.Off) return
    val shown by animateFloatAsState(
        targetValue = if (count > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "badge",
    )
    if (shown <= 0.01f) return
    val dot = style == BadgeStyle.Dot
    Box(
        modifier
            .graphicsLayer {
                scaleX = shown
                scaleY = shown
            }
            .then(if (dot) Modifier.size(12.dp) else Modifier.height(20.dp).widthIn(min = 20.dp))
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFFF453A))
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .padding(horizontal = if (dot) 0.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!dot) {
            Text(
                text = if (count > 99) "99+" else count.coerceAtLeast(1).toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Gentle iOS-style wiggle while selecting. */
@Composable
fun Modifier.wiggle(active: Boolean, seed: Int): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "wiggle")
    val angle by transition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 130 + (seed and 0x3F)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wiggleAngle",
    )
    return graphicsLayer { rotationZ = angle }
}

/** Round check mark in the corner of a selectable icon. */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalSettings.current.accent.color
    Box(
        modifier
            .size(22.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) accent else Color.White.copy(alpha = 0.55f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Ausgewählt", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

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
    /** Starts the app, zooming out of the icon at these bounds (root coordinates). */
    val launch: (AppInfo, Rect?) -> Unit,
    /** Long press: opens the context menu next to the icon (bounds in root coordinates). */
    val menu: (AppInfo, Rect) -> Unit,
    /** Long press and move on a home screen or dock icon: start dragging (finger in root coordinates). */
    val dragStart: (AppInfo, Offset, Rect) -> Unit = { _, _, _ -> },
)

/**
 * Tap, long press (menu) and long press + move (drag) on one icon.
 * Nothing is consumed before the long press, so swiping pages still works from an icon.
 */
internal fun Modifier.tapMenuOrDrag(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onPressChange: (Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressChange(true)
        // 1 = lifted (tap), 2 = moved or taken over by scrolling, null = long press.
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var outcome = 0
            while (outcome == 0) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                outcome = when {
                    change == null -> 2
                    !change.pressed -> 1
                    change.isConsumed -> 2
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2
                    else -> 0
                }
            }
            outcome
        }
        when (early) {
            1 -> {
                onPressChange(false)
                onTap()
            }
            2 -> onPressChange(false)
            else -> {
                onLongPress()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop * 1.5f) {
                        onDragStart(change.position)
                        break
                    }
                }
                onPressChange(false)
            }
        }
    }
}

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
    // Icons keep a fixed highlight: redrawing every icon on each tilt made scrolling lag.
    val light = remember { mutableStateOf(IconLight) }
    val sheen = if (settings.iconGloss) Modifier.glassSheen(light, settings.glassSpecular) else Modifier
    when (app.iconKind) {
        IconKind.Shaped -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = modifier
                .size(size)
                .shadow(elevation = 6.dp, shape = settings.iconShape.clipShape(), clip = true)
                .then(sheen),
        )

        IconKind.Free -> Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = modifier
                .size(size)
                .then(sheen),
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
    /** Home screen and dock icons can be dragged to another place. */
    draggable: Boolean = false,
    onLaunch: (AppInfo, Rect?) -> Unit = actions.launch,
) {
    val settings = LocalSettings.current
    val iconSize = settings.iconSize.dp
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var pressedByHand by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed || pressedByHand) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "iconPress",
    )
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var cellOrigin by remember { mutableStateOf(Offset.Zero) }
    val selection = LocalSelection.current
    val isSelected = selection.active && app.key in selection.selected
    val launch by rememberUpdatedState(onLaunch)
    val currentActions by rememberUpdatedState(actions)
    val longPress = {
        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (selection.active) selection.toggle(app) else currentActions.menu(app, bounds)
    }
    val gestures = if (draggable && !selection.active) {
        Modifier
            .onGloballyPositioned { cellOrigin = it.positionInRoot() }
            .semantics(mergeDescendants = true) {
                onClick { launch(app, bounds); true }
                onLongClick { longPress(); true }
            }
            .tapMenuOrDrag(
                onTap = { launch(app, bounds) },
                onLongPress = longPress,
                onDragStart = { local -> currentActions.dragStart(app, cellOrigin + local, bounds) },
                onPressChange = { pressedByHand = it },
            )
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { if (selection.active) selection.toggle(app) else launch(app, bounds) },
            onLongClick = longPress,
        )
    }

    Box(modifier.wiggle(selection.active, app.key.hashCode())) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .then(if (fillCell) Modifier.fillMaxWidth() else Modifier.width(iconSize + 18.dp))
                .then(gestures)
                .padding(vertical = 6.dp),
        ) {
            Box {
                AppIconImage(
                    app = app,
                    size = iconSize,
                    // Bounds of the icon itself (before the press scale), for the launch zoom
                    // and the lifted icon in the context menu.
                    modifier = Modifier
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = if (selection.active && !isSelected) 0.75f else 1f
                        },
                )
                if (selection.active) {
                    SelectionBadge(isSelected, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
                } else {
                    NotificationBadge(app, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp))
                }
            }
            if (showLabel && settings.showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontSize = settings.labelSize.sp.sp,
                    color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = if (onWallpaper) OnWallpaperText else TextStyle.Default,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
