package dev.hearth.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AuroraLook
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * ZENITH 20, the jubilee edition of every older egg: laid over the egg a golden aurora, gold dust
 * rising and a small badge – the egg itself stays as it was, and every touch goes through to it.
 */
@Composable
internal fun JubileeOverlay(label: String = "JUBILÄUMS-EDITION", strength: Float = 0.4f) {
    Box(Modifier.fillMaxSize()) {
        ZenithAurora(AuroraLook.Gold, Modifier.fillMaxSize(), strength = strength)
        GoldDust(Modifier.fillMaxSize())
        val shape = ZenithCutShape(6.dp)
        Text(
            "✦ $label",
            color = ZenithGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(0.8.dp, ZenithGold.copy(alpha = 0.6f), shape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Gold dust rising slowly, each speck twinkling. */
@Composable
private fun GoldDust(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "goldDust")
    val phase = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "phase")
    val specks = remember {
        val r = Random(2020)
        List(34) { Triple(r.nextFloat(), r.nextFloat(), 0.8f + r.nextFloat() * 1.8f) }
    }
    Canvas(modifier) {
        val p = phase.value
        specks.forEachIndexed { i, (x, offset, radius) ->
            val y = 1f - ((p + offset) % 1f)
            val twinkle = 0.3f + 0.7f * abs(sin((p * 6.0 + i).toDouble())).toFloat()
            drawCircle(ZenithGold.copy(alpha = 0.55f * twinkle), radius * density, Offset(x * size.width, y * size.height))
        }
    }
}
