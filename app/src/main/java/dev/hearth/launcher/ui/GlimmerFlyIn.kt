package dev.hearth.launcher.ui

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FlyEasing = CubicBezierEasing(0.32f, 0f, 0.12f, 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Like on the iPhone: the app you just closed shrinks into a dark card with its icon and
 * flies up into Glimmer around the camera, which then hops. Draws only, never takes touches.
 */
@Composable
fun GlimmerFlyIn(app: AppInfo, onPulse: () -> Unit, onDone: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val progress = remember(app) { Animatable(0f) }
    val pulse by rememberUpdatedState(onPulse)
    val done by rememberUpdatedState(onDone)

    // Where Glimmer sits: centered on the camera cutout at the top, or the top middle.
    val camera = remember(view) {
        val rects: List<Rect> = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        rects.minByOrNull { it.top }?.takeIf { it.top < view.height / 4 }
    }

    LaunchedEffect(app) {
        launch {
            // Glimmer reacts just as the card arrives.
            delay(380)
            pulse()
        }
        progress.animateTo(1f, tween(520, easing = FlyEasing))
        done()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pillW = with(density) { (screenWidthDp * 0.27f).coerceIn(86f, 110f).dp.toPx() }
        val pillH = with(density) { ISLAND_HEIGHT_DP.dp.toPx() }
        val camX = camera?.exactCenterX() ?: (w / 2f)
        val camY = camera?.exactCenterY() ?: with(density) { 14.dp.toPx() }
        val p = progress.value

        // From a card the size of the closing app window to the pill.
        val startW = w * 0.72f
        val startH = h * 0.62f
        val cardW = lerp(startW, pillW, p)
        val cardH = lerp(startH, pillH, p)
        val centerX = lerp(w / 2f, camX, p)
        // A slight arc: up fast first, then settling into the island.
        val centerY = lerp(h * 0.5f, camY, p * p * (3f - 2f * p))
        val corner = with(density) { lerp(44.dp.toPx(), pillH / 2f, p).toDp() }
        val appear = (p / 0.12f).coerceIn(0f, 1f)
        val merge = 1f - ((p - 0.86f) / 0.14f).coerceIn(0f, 1f)
        val iconSize = with(density) { lerp(72.dp.toPx(), pillH * 0.62f, p).toDp() }

        Box(
            Modifier
                .offset { IntOffset((centerX - cardW / 2f).roundToInt(), (centerY - cardH / 2f).roundToInt()) }
                .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                .graphicsLayer { alpha = appear * merge }
                .clip(RoundedCornerShape(corner))
                .background(Color.Black.copy(alpha = lerp(0.72f, 1f, p)))
                .border(1.dp, Color.White.copy(alpha = 0.18f * (1f - p)), RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { alpha = 1f - ((p - 0.6f) / 0.3f).coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(iconSize * 0.24f)),
            )
        }
    }
}
