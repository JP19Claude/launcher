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
import androidx.compose.ui.unit.Dp

@Composable
fun Dock(apps: List<AppInfo>, actions: AppActions, modifier: Modifier = Modifier, draggingKey: String? = null) {
    if (apps.isEmpty()) return
    val style = LocalSettings.current.dockStyle
    // Very big icons (up to 96 dp) on the home screen; the dock keeps them at a size that fits.
    val dockLook = LocalSettings.current.let { it.copy(iconSize = it.iconSize.coerceAtMost(68)) }
    val iconsSized: @Composable (Int) -> Unit = { iconSize ->
        androidx.compose.runtime.CompositionLocalProvider(LocalSettings provides dockLook.copy(iconSize = iconSize)) {
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
    }
    when (style) {
        // ZENITH 19: a slanted plate of the Z's dark metal, its ends cut like the Z's bars, green
        // light burning along its foot and pooling underneath.
        DockStyle.Zenith -> {
            val glassHud = LocalGlassStyle.current.hudGlass
            val hud = LocalGlassStyle.current.hud
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            // Projekt Zenith Glas: the dock is a slab of green liquid glass (below).
                            glassHud -> Modifier
                            // Projekt Zenith: the dock as a hologram.
                            hud -> Modifier.drawBehind { drawHudPlate(slant = size.height * 0.36f) }
                            else -> Modifier.drawBehind { drawZenithPlate(slant = size.height * 0.36f, unit = 190.dp.toPx()) }
                        },
                    ),
            ) {
                // Big icons, every one the same size, as big as fits (up to 78 dp): each takes its
                // icon plus 18 dp of cell.
                val inner = maxWidth - 28.dp
                val fit = ((inner / apps.size.coerceAtLeast(1)).value - 20f).toInt()
                val iconSize = fit.coerceIn(36, 78)
                val row: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { iconsSized(iconSize) }
                }
                if (glassHud) {
                    LiquidGlass(cornerRadius = 30.dp, refraction = 22.dp, modifier = Modifier.fillMaxWidth(), fluidEdge = false) { row() }
                } else {
                    row()
                }
            }
        }
        // The other docks: their icons are made to fit – each takes its icon plus 18 dp of cell, and
        // four big ones in a bar this wide would squeeze the last one small.
        else -> androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
            val room = maxWidth
            when (style) {
                // The full-width glass bar.
                DockStyle.Glass -> LiquidGlass(
                    cornerRadius = 34.dp,
                    refraction = 22.dp,
                    interactive = true,
                    // One UI 10 Fluid: Claude's colors round the dock (standing still: it's always on screen).
                    modifier = Modifier.fillMaxWidth().aiFluidEdge(34.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign, flowing = false),
                    fluidEdge = false,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                }
                // A glass capsule just around the icons, floating in the middle.
                DockStyle.Floating -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                        ) { iconsSized(fittingIconSize(room - 24.dp, apps.size, 10.dp, dockLook.iconSize)) }
                    }
                }
                // OMEGA UI 17: a dark glass island just around the icons, a ruby glow along its rim.
                DockStyle.Island -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                        ) { iconsSized(fittingIconSize(room - 28.dp, apps.size, 12.dp, dockLook.iconSize)) }
                    }
                }
                // OMEGA UI 17: the full bar, tinted ruby.
                DockStyle.Ruby -> LiquidGlass(
                    cornerRadius = 36.dp,
                    refraction = 22.dp,
                    interactive = true,
                    tint = Color(0xFFB0102C).copy(alpha = 0.28f),
                    modifier = Modifier
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
                    ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                }
                // Just the icons, as One UI does it.
                DockStyle.Clear -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                DockStyle.Zenith -> Unit
            }
        }
    }
}

/**
 * The icon size at which [count] icons – each with its 18 dp of cell – fit in [room], [gap] apart;
 * never more than [wanted], and not below 36 dp.
 */
private fun fittingIconSize(room: Dp, count: Int, gap: Dp, wanted: Int): Int {
    val n = count.coerceAtLeast(1)
    val cell = (room - gap * (n - 1)) / n
    val fit = (cell.value - 18f).toInt()
    return minOf(wanted, maxOf(fit, 36))
}
