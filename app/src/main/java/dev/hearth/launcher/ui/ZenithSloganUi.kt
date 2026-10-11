package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    alpha: Float = 0.42f,
    size: TextUnit = 11.sp,
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
