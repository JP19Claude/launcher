package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
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
 * The saying big – "Aim Higher." for the cards that show the system (the settings' system card,
 * the version card). Plain: one solid color (the text's own, with a breath of the [accent] in it),
 * a light weight, no glow, no shine, no rule – it's meant to read like printed type, not like
 * something a machine lit up.
 */
@Composable
internal fun SloganBanner(
    modifier: Modifier = Modifier,
    accent: Color = ZenithGreen,
    size: TextUnit = 34.sp,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = Color.White,
) {
    val start = textAlign == TextAlign.Start
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally) {
        Text(
            ZenithSlogan.MAIN,
            style = TextStyle(
                color = lerp(color, accent, 0.14f).copy(alpha = 0.96f),
                fontSize = size,
                fontWeight = FontWeight.Light,
                letterSpacing = (size.value * -0.012f).sp,
            ),
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
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
