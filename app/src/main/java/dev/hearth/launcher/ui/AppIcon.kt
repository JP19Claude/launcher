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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

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
    val info: (AppInfo) -> Unit,
    val uninstall: (AppInfo) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    actions: AppActions,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onWallpaper: Boolean = true,
    onLaunch: (AppInfo) -> Unit = actions.launch,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "iconPress",
    )
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(76.dp)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = 6.dp),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(elevation = 6.dp, shape = SquircleShape, clip = true),
            )
            if (showLabel) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = if (onWallpaper) OnWallpaperText else TextStyle.Default,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("App-Info") },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.info(app)
                },
            )
            DropdownMenuItem(
                text = { Text("Deinstallieren") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.uninstall(app)
                },
            )
        }
    }
}
