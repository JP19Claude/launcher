package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/**
 * OMEGA UI 17.3: the rim of a search bar. With OMEGA Glass, OMEGA Fluid flows round it the
 * whole time; while you type it gets brighter. Without OMEGA it flows only while typing.
 */
fun Modifier.searchFluidRim(corner: Dp, active: Boolean = false): Modifier = composed {
    val s = LocalSettings.current
    val omega = s.omegaGlass
    this.aiFluidEdge(
        corner,
        strength = when {
            active -> if (omega) 1.2f else 0.85f
            omega -> 0.95f
            else -> 0.45f
        },
        width = Dp(if (omega || active) 2f else 1.5f),
        enabled = s.fluidDesign,
        flowing = omega || active,
    )
}

/** OMEGA UI 17.3: the fluid colors drifting inside a search bar's glass, cut to its shape. */
@Composable
internal fun BoxScope.SearchFluidFill(corner: Dp, active: Boolean = false) {
    val s = LocalSettings.current
    if (!s.fluidDesign) return
    Box(
        Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(corner))
            .fluidGlow(
                strength = when {
                    active -> 1.25f
                    s.omegaGlass -> 1f
                    else -> 0.55f
                },
                flowing = s.omegaGlass || active,
            ),
    )
}
