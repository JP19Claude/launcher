package dev.hearth.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ZenithSlogan

/*
 * ZENITH 20.5: the saying "Aim Higher." is part of the system's fabric – the same quiet lettering
 * wherever there is room for it: on the home screen and its background, the lock screen, the
 * settings, the nightstand, the Z-Angriff. One place for how it looks.
 */

/** The saying as a line of spaced capitals, quiet enough to sit under anything. */
@Composable
internal fun SloganLine(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.62f,
    size: TextUnit = 12.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        ZenithSlogan.UPPER,
        color = color.copy(alpha = alpha),
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (size.value * 0.55f).sp,
        textAlign = textAlign,
        maxLines = 1,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * ZENITH 20.5: the saying big – "Aim Higher." in heavy slanted letters like the Z's own, in the
 * [accent] with a band of light running over it, and a thin line under it. For the cards that show
 * the system (the settings' system card, the version card).
 */
@Composable
internal fun SloganBanner(
    modifier: Modifier = Modifier,
    accent: Color = ZenithGreen,
    size: TextUnit = 34.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    val density = LocalDensity.current
    val shift = if (LocalSettings.current.animations) {
        rememberInfiniteTransition(label = "sloganShine").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(4600, easing = LinearEasing)),
            label = "sloganShine",
        ).value
    } else {
        0.45f
    }
    val width = with(density) { size.toPx() } * 6.2f
    val x = -width * 0.5f + width * 2f * shift
    val bright = lerp(accent, Color.White, 0.5f)
    val brush = Brush.linearGradient(
        listOf(accent, bright, Color.White, bright, accent),
        start = Offset(x, 0f),
        end = Offset(x + width * 0.8f, 0f),
    )
    val start = textAlign == TextAlign.Start
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally) {
        Text(
            ZenithSlogan.MAIN,
            style = TextStyle(
                brush = brush,
                fontSize = size,
                fontWeight = FontWeight.Black,
                letterSpacing = (size.value * 0.01f).sp,
                textGeometricTransform = TextGeometricTransform(skewX = -0.18f),
                shadow = Shadow(accent.copy(alpha = 0.55f), Offset.Zero, 26f),
            ),
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(64.dp)
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(accent, Color.Transparent))),
        )
    }
}

/** The saying drawn as lettering centered on [centerX], its baseline at [baselineY] (for backgrounds and the Z-Angriff). */
internal fun DrawScope.drawSloganText(centerX: Float, baselineY: Float, textSizePx: Float, tint: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
            textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
            // Spaced wide, like the lettering of the Z's name.
            letterSpacing = 0.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.nativeCanvas.drawText(ZenithSlogan.UPPER, centerX, baselineY, paint)
    }
}
