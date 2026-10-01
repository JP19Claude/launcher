package dev.hearth.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
        LaunchedEffect(current) {
            appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
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
                LiquidGlass(
                    cornerRadius = 24.dp,
                    refraction = 20.dp,
                    blur = 22.dp,
                    tint = Color.Black.copy(alpha = 0.18f),
                    modifier = Modifier
                        .width(250.dp)
                        .graphicsLayer {
                            val v = appear.value
                            alpha = v.coerceIn(0f, 1f)
                            scaleX = 0.75f + 0.25f * v
                            scaleY = 0.75f + 0.25f * v
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
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
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
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
