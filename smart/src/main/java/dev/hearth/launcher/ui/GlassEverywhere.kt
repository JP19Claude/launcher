package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.liquidOmega

/*
 * OMEGA UI 18.5 – more glass in every place: what isn't part of a single screen.
 */

/**
 * "Statusleiste aus Glas": a strip of frosted glass behind the status bar, like Apple's – the
 * wallpaper blurred and lit a little, fading out softly downwards (no hard edge). Over the
 * home screen, the drawer and the settings; whatever scrolls up slides under it.
 */
@Composable
internal fun BoxScope.GlassStatusBar() {
    val s = LocalSettings.current
    if (!s.glassStatusBar) return
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (top.value <= 0f) return
    val liquid = s.liquidOmega
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top + 18.dp)
            // Soft at the bottom: the glass fades out instead of ending in a line.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        GlassBackdropFill(blur = 24.dp, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (liquid) 0.14f else 0.08f),
                            Color.Black.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        )
    }
}
