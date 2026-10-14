package dev.hearth.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Little pieces of liquid glass for everywhere in the system: chips, pills and round buttons
 * that used to be plain see-through shapes are glass now, and give like liquid under the finger.
 */

/** A small liquid glass capsule: a chip, a pill, a little text button. */
@Composable
fun GlassCapsule(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    LiquidGlass(
        cornerRadius = 100.dp,
        refraction = 10.dp,
        tint = tint,
        interactive = true,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(padding), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A round liquid glass button. */
@Composable
fun GlassCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlass(
        cornerRadius = size / 2,
        refraction = size * 0.26f,
        tint = tint,
        interactive = true,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) { content() }
    }
}
