package dev.hearth.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * OMEGA UI 18.5 – the controls made of liquid glass, like Apple's: a switch whose knob turns
 * into a clear lens while it's held and stretches like a drop as it slides over, and a slider
 * with the same glass knob. Drawn on one small canvas each – no lens shader – so a page full
 * of them stays light.
 */

/** The knob: solid white at rest; while held, a clear glass lens with a bright rim. */
private fun DrawScope.glassKnob(topLeft: Offset, knob: Size, lift: Float) {
    val r = CornerRadius(knob.height / 2f)
    // A soft shadow under the resting knob.
    drawRoundRect(Color.Black.copy(alpha = 0.18f * (1f - lift)), topLeft + Offset(0f, 1.5.dp.toPx()), knob, r)
    // The body: white, clearing to glass while held.
    drawRoundRect(Color.White.copy(alpha = 1f - 0.8f * lift), topLeft, knob, r)
    if (lift > 0.01f) {
        // Light caught along the top of the lens, and its bright rim.
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.55f * lift), Color.Transparent, Color.White.copy(alpha = 0.18f * lift)),
                startY = topLeft.y,
                endY = topLeft.y + knob.height,
            ),
            topLeft,
            knob,
            r,
        )
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.7f)),
                start = topLeft,
                end = topLeft + Offset(knob.width, knob.height),
            ),
            topLeft,
            knob,
            r,
            style = Stroke(1.5.dp.toPx() * lift.coerceAtLeast(0.4f)),
        )
    }
}

/**
 * A switch of liquid glass. [onColor] fills the track when it's on (OMEGA's accent by default);
 * held, the knob turns into a clear lens and grows; sliding over, it stretches like a drop.
 */
@Composable
internal fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    onColor: Color = Color(0xFF34C759),
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "glassSwitch")
    val lift by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "glassSwitchLift")
    val dim = if (enabled) 1f else 0.4f
    Canvas(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(h / 2f)
        // The track: frosted when off, filled when on.
        drawRoundRect(lerp(Color.White.copy(alpha = 0.16f), onColor, pos).copy(alpha = (0.16f + 0.84f * pos) * dim), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f * dim), Color.White.copy(alpha = 0.06f * dim))),
            cornerRadius = r,
            style = Stroke(1.dp.toPx()),
        )
        // The knob: stretched in the middle of its way, bigger while held.
        val pad = 2.5.dp.toPx()
        val stretch = 1f - abs(pos * 2f - 1f)
        val kh = h - 2 * pad + lift * 8.dp.toPx()
        val kw = kh * 1.25f + stretch * 9.dp.toPx() + lift * 8.dp.toPx()
        val left = pad + (w - 2 * pad - kw) * pos
        glassKnob(Offset(left, (h - kh) / 2f), Size(kw, kh), lift)
    }
}

/**
 * A slider of liquid glass: a thin track, filled up to the knob; the knob turns into a clear
 * lens while it's dragged. [steps] like Material's (points between the ends).
 */
@Composable
internal fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    activeColor: Color = Color.White,
) {
    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "glassSliderLift")
    val change by rememberUpdatedState(onValueChange)
    val range by rememberUpdatedState(valueRange)
    val stepCount by rememberUpdatedState(steps)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    fun valueAt(x: Float, width: Float, pad: Float): Float {
        val f = ((x - pad) / (width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val snapped = if (stepCount > 0) (f * (stepCount + 1)).roundToInt() / (stepCount + 1).toFloat() else f
        return range.start + snapped * (range.endInclusive - range.start)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                val pad = 16.dp.toPx()
                detectTapGestures(
                    onPress = {
                        dragging = true
                        tryAwaitRelease()
                        dragging = false
                    },
                    onTap = { change(valueAt(it.x, size.width.toFloat(), pad)) },
                )
            }
            .pointerInput(Unit) {
                val pad = 16.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        change(valueAt(it.x, size.width.toFloat(), pad))
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { pointer, _ ->
                    pointer.consume()
                    change(valueAt(pointer.position.x, size.width.toFloat(), pad))
                }
            },
    ) {
        val pad = 16.dp.toPx()
        val cy = size.height / 2f
        val th = 6.dp.toPx()
        val tw = size.width - 2 * pad
        val tr = CornerRadius(th / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(pad, cy - th / 2f), Size(tw, th), tr)
        drawRoundRect(activeColor.copy(alpha = 0.85f), Offset(pad, cy - th / 2f), Size(tw * fraction, th), tr)
        val kh = 22.dp.toPx() + lift * 10.dp.toPx()
        val kw = 34.dp.toPx() + lift * 14.dp.toPx()
        val x = pad + tw * fraction
        glassKnob(Offset(x - kw / 2f, cy - kh / 2f), Size(kw, kh), lift)
    }
}
