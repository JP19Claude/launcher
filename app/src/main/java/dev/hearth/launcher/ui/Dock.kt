package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.DockStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Dock(apps: List<AppInfo>, actions: AppActions, modifier: Modifier = Modifier, draggingKey: String? = null) {
    if (apps.isEmpty()) return
    val style = LocalSettings.current.dockStyle
    val icons: @Composable () -> Unit = {
        apps.forEach { app ->
            key(app.key) {
                AppIcon(
                    app = app,
                    actions = actions,
                    showLabel = false,
                    draggable = true,
                    modifier = Modifier.graphicsLayer { alpha = if (app.key == draggingKey) 0f else 1f },
                )
            }
        }
    }
    when (style) {
        // The full-width glass bar.
        DockStyle.Glass -> LiquidGlass(
            cornerRadius = 34.dp,
            refraction = 22.dp,
            interactive = true,
            // One UI 10 Fluid: Claude's colors round the dock (standing still: it's always on screen).
            modifier = modifier.fillMaxWidth().aiFluidEdge(34.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign, flowing = false),
            fluidEdge = false,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { icons() }
        }
        // A glass capsule just around the icons, floating in the middle.
        DockStyle.Floating -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LiquidGlass(
                cornerRadius = 30.dp,
                refraction = 20.dp,
                interactive = true,
                modifier = Modifier.aiFluidEdge(30.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign, flowing = false),
                fluidEdge = false,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { icons() }
            }
        }
        // OMEGA UI 17: a dark glass island just around the icons, a ruby glow along its rim.
        DockStyle.Island -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val accent = LocalSettings.current.accent.color
            LiquidGlass(
                cornerRadius = 32.dp,
                refraction = 20.dp,
                interactive = true,
                tint = Color(0xFF120A12).copy(alpha = 0.55f),
                modifier = Modifier
                    .drawBehind {
                        // The glow underneath, as if the island floated on red light.
                        drawRoundRect(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height),
                                radius = size.width * 0.6f,
                            ),
                            topLeft = Offset(-12.dp.toPx(), 0f),
                            size = Size(size.width + 24.dp.toPx(), size.height + 18.dp.toPx()),
                            cornerRadius = CornerRadius(40.dp.toPx()),
                        )
                    }
                    .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0.8f), accent.copy(alpha = 0.2f))), RoundedCornerShape(32.dp)),
                fluidEdge = false,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { icons() }
            }
        }
        // OMEGA UI 17: the full bar, tinted ruby.
        DockStyle.Ruby -> LiquidGlass(
            cornerRadius = 36.dp,
            refraction = 22.dp,
            interactive = true,
            tint = Color(0xFFB0102C).copy(alpha = 0.28f),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFF8A98).copy(alpha = 0.7f), Color(0xFFB0102C).copy(alpha = 0.2f))), RoundedCornerShape(36.dp)),
            fluidEdge = false,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { icons() }
        }
        // Just the icons, as One UI does it.
        DockStyle.Clear -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { icons() }
    }
}
