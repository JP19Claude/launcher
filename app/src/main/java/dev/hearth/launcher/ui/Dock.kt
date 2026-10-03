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
            // One UI 10 Fluid: Claude's colors flowing round the dock.
            modifier = modifier.fillMaxWidth().aiFluidEdge(34.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign),
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
                modifier = Modifier.aiFluidEdge(30.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign),
                fluidEdge = false,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { icons() }
            }
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
