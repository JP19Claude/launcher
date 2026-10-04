package dev.hearth.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo
import kotlin.math.roundToInt

@Immutable
class GlassMenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    /** App shortcuts bring their own picture, shown instead of [icon]. */
    val image: ImageBitmap? = null,
    val onClick: () -> Unit,
)

/** A context menu request: where it was opened, what it shows, and (optionally) which app. */
@Immutable
class GlassMenuRequest(
    val anchor: Rect,
    val items: List<GlassMenuItem>,
    val app: AppInfo? = null,
    val title: String? = null,
)

/**
 * iOS-style context menu: the touched icon lifts out of the blurred home screen
 * and a liquid glass panel with the actions springs open next to it.
 */
@Composable
fun GlassMenuOverlay(request: GlassMenuRequest?, onDismiss: () -> Unit) {
    // Keep the last request while the exit animation runs.
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request

    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(160)),
    ) {
        val current = shown ?: return@AnimatedVisibility
        val appear = remember(current) { Animatable(0f) }
        // Springs open, and on closing folds back into the icon instead of just fading.
        val leaving = transition.targetState != EnterExitState.Visible
        LaunchedEffect(current, leaving) {
            if (leaving) {
                appear.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1500f))
            } else {
                appear.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 520f))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            MenuLayout(anchor = current.anchor) {
                // Child 0: the lifted icon, exactly where and as big as the original.
                val previewSize = with(LocalDensity.current) { current.anchor.width.toDp() }
                Box(Modifier.graphicsLayer {
                    val s = 1f + 0.08f * appear.value
                    scaleX = s
                    scaleY = s
                }) {
                    current.app?.let { app -> AppIconImage(app, previewSize) }
                }
                // Child 1: the glass panel.
                val oneUi = LocalSettings.current.galaxyClaude
                // OMEGA UI 17.5: OMEGA glass, deepened like before.
                val menuTint = if (LocalSettings.current.omegaGlass) {
                    LocalGlassStyle.current.tint.compositeOver(Color.Black.copy(alpha = 0.18f))
                } else {
                    Color.Black.copy(alpha = 0.18f)
                }
                LiquidGlass(
                    cornerRadius = if (oneUi) 28.dp else 24.dp,
                    refraction = 20.dp,
                    blur = 22.dp,
                    tint = menuTint,
                    modifier = Modifier
                        .width(if (oneUi) 284.dp else 250.dp)
                        .graphicsLayer {
                            val v = appear.value
                            alpha = (v * 1.4f).coerceIn(0f, 1f)
                            scaleX = 0.62f + 0.38f * v
                            scaleY = 0.5f + 0.5f * v
                            translationY = (1f - v) * -10.dp.toPx()
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .clip(RoundedCornerShape(if (oneUi) 28.dp else 24.dp)),
                ) {
                    if (oneUi) {
                        OneUIMenuContent(current, onDismiss)
                    } else Column(Modifier.padding(vertical = 6.dp)) {
                        val title = current.title ?: current.app?.label
                        if (title != null) {
                            Text(
                                text = title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                            MenuDivider()
                        }
                        current.items.forEachIndexed { index, item ->
                            if (index > 0) MenuDivider()
                            MenuRow(item) {
                                onDismiss()
                                item.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One UI's app popup: the app's name, its shortcuts as a list, and the actions as a row of
 * round icons with small names underneath (as on a Galaxy), all on liquid glass.
 */
@Composable
private fun OneUIMenuContent(request: GlassMenuRequest, onDismiss: () -> Unit) {
    val shortcuts = request.items.filter { it.image != null }
    val actions = request.items.filter { it.image == null }
    Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
        val title = request.title ?: request.app?.label
        val accent = LocalSettings.current.accent.color
        val omega = LocalSettings.current.omegaGlass
        if (title != null) {
            // OMEGA UI 17: a header with the app's icon, its name and a line of the accent.
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                request.app?.let { app ->
                    AppIconImage(app, 34.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        if (request.app != null) "App · ${request.items.size} Aktionen" else "${request.items.size} Aktionen",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                // Claude Mythos: the Clawds sit in the header too.
                MythosRow(22.dp)
                // OMEGA UI 17.5: the Ω in the header.
                if (omega) OmegaSign(20.sp, Modifier.padding(start = 8.dp))
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (omega) {
                            Brush.horizontalGradient(fluidPalette().take(3) + Color.Transparent)
                        } else {
                            Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0f)))
                        },
                    ),
            )
        }
        shortcuts.forEach { item ->
            MenuRow(item) {
                onDismiss()
                item.onClick()
            }
        }
        if (actions.isNotEmpty()) {
            if (shortcuts.isNotEmpty() || title != null) {
                Spacer(Modifier.height(4.dp))
                MenuDivider()
            }
            actions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    row.forEach { item ->
                        OneUIAction(item, Modifier.weight(1f)) {
                            onDismiss()
                            item.onClick()
                        }
                    }
                    // Keep the icons in their columns on a short last row.
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OneUIAction(item: GlassMenuItem, modifier: Modifier, onClick: () -> Unit) {
    val color = if (item.destructive) Color(0xFFFF8A80) else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // OMEGA UI 17: a tile of glass for each action, tinted with the accent.
        val accent = LocalSettings.current.accent.color
        LiquidGlass(
            cornerRadius = 14.dp,
            refraction = 9.dp,
            tint = if (item.destructive) Color(0xFFFF453A).copy(alpha = 0.18f) else accent.copy(alpha = 0.16f),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            item.label,
            color = color.copy(alpha = 0.9f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

@Composable
private fun MenuRow(item: GlassMenuItem, onClick: () -> Unit) {
    val color = if (item.destructive) Color(0xFFFF8A80) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        val image = item.image
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
        } else {
            Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** Places the lifted icon on its anchor and the panel below it, or above if there is no room. */
@Composable
private fun MenuLayout(anchor: Rect, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val preview = measurables[0].measure(Constraints.fixedWidth(anchor.width.roundToInt().coerceAtLeast(0)))
        val panel = measurables[1].measure(loose)
        val margin = 12.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        layout(width, height) {
            preview.place(anchor.left.roundToInt(), anchor.top.roundToInt())

            val x = (anchor.center.x - panel.width / 2f).roundToInt()
                .coerceIn(margin, (width - panel.width - margin).coerceAtLeast(margin))
            val below = anchor.bottom.roundToInt() + gap
            val y = if (below + panel.height <= height - margin) {
                below
            } else {
                (anchor.top.roundToInt() - gap - panel.height).coerceAtLeast(margin)
            }
            panel.place(x, y)
        }
    }
}
