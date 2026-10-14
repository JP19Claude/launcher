package dev.hearth.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.PageTransition
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Fades and floats content in, a little after the item before it, so cards and folders
 * appear one after another instead of all at once.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier {
    if (!LocalSettings.current.animations) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 12) * 45L)
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 26.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Text whose characters roll up when they change, like a flip clock (used for the time). */
@Composable
fun RollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = TextStyle.Default,
) {
    if (!LocalSettings.current.animations) {
        Text(
            text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
            fontStyle = fontStyle, letterSpacing = letterSpacing, lineHeight = lineHeight, style = style,
        )
        return
    }
    Row(modifier) {
        text.forEachIndexed { index, char ->
            androidx.compose.runtime.key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(220)))
                            .togetherWith(slideOutVertically(tween(260)) { -it } + fadeOut(tween(180)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "rollingChar",
                ) { c ->
                    Text(
                        c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
                        fontStyle = fontStyle, letterSpacing = letterSpacing, lineHeight = lineHeight, style = style,
                    )
                }
            }
        }
    }
}

/**
 * Liquid glass sheen on an icon: a highlight on the side facing the light (which follows
 * the phone's tilt) and a little shade on the far side. Drawn only on the icon's own pixels.
 */
fun Modifier.glassSheen(light: State<Offset>, strength: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val l = light.value
        val reach = size.maxDimension / 2f
        val lit = center + Offset(l.x, l.y) * reach
        val shade = center - Offset(l.x, l.y) * reach
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = (0.42f * strength).coerceIn(0f, 1f)),
                0.38f to Color.White.copy(alpha = (0.06f * strength).coerceIn(0f, 1f)),
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = (0.12f * strength).coerceIn(0f, 1f)),
                start = lit,
                end = shade,
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

/**
 * Moves a pager page by how far it is from the middle ([offset] 0 = centered):
 * depth (shrinks and fades away), cube (turns around the edge) or flat.
 */
fun Modifier.pageTransition(transition: PageTransition, offset: () -> Float): Modifier = graphicsLayer {
    val o = offset().coerceIn(-1f, 1f)
    when (transition) {
        PageTransition.Depth -> {
            val s = 1f - 0.12f * abs(o)
            scaleX = s
            scaleY = s
            alpha = 1f - 0.45f * abs(o)
        }
        PageTransition.Cube -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (o < 0f) 0f else 1f, 0.5f)
            rotationY = -o * 38f
            alpha = 1f - 0.25f * abs(o)
        }
        PageTransition.Flat -> Unit
    }
}

/** A short message on glass at the bottom, optionally with an action like "Rückgängig". */
@Immutable
class GlassToast(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null) {
    val id: Long = System.nanoTime()
}

@Composable
fun GlassToastHost(toast: GlassToast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(toast) }
    if (toast != null) shown = toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(4000)
            onDismiss()
        }
    }
    Box(modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.8f) +
                slideInVertically(spring(dampingRatio = 0.75f)) { it / 2 },
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.9f),
        ) {
            val current = shown ?: return@AnimatedVisibility
            LiquidGlass(
                cornerRadius = 24.dp,
                refraction = 16.dp,
                tint = Color.Black.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 120.dp),
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current.message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (current.action != null && current.onAction != null) {
                        Spacer(Modifier.width(10.dp))
                        GlassChip(current.action) {
                            current.onAction.invoke()
                            onDismiss()
                        }
                    } else {
                        Spacer(Modifier.width(10.dp))
                    }
                }
            }
        }
    }
}
