package dev.hearth.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Each ClaudeOS gets its own Claude mark at the end of its easter egg. */
enum class ClaudeMarkStyle(val title: String, val colors: List<Color>) {
    Ember("ClaudeOS 1 „Ember“", listOf(Color(0xFFFFD2BC), Color(0xFFFFB494), Color(0xFFD97757))),
    Blaze("ClaudeOS 2 „Blaze“", listOf(Color(0xFFFFE27A), Color(0xFFFF8A1F), Color(0xFFD7263D))),
    Nova("ClaudeOS 3 „Nova“", listOf(Color(0xFFFFFFFF), Color(0xFFFFE7A0), Color(0xFFFFB494))),
    Aurora("ClaudeOS 4 „Aurora“", listOf(Color(0xFF6CFFB0), Color(0xFF3EE0C8), Color(0xFF8E6BFF))),
    Mythos("ClaudeOS 5 „Mythos“", listOf(Color(0xFFFFF1C2), Color(0xFFD4AF37), Color(0xFF8C1C13))),
}

/**
 * Claude's mark – a burst of rounded rays round a center – in the colors of [style]: twelve
 * rays of two lengths, each fading from the center outwards.
 */
@Composable
fun ClaudeMark(style: ClaudeMarkStyle, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (i in 0 until 12) {
            val a = (i * 30.0 - 90.0) * PI / 180.0
            val len = r * if (i % 2 == 0) 0.95f else 0.72f
            val end = Offset(c.x + cos(a).toFloat() * len, c.y + sin(a).toFloat() * len)
            val start = Offset(c.x + cos(a).toFloat() * r * 0.16f, c.y + sin(a).toFloat() * r * 0.16f)
            drawLine(
                Brush.linearGradient(style.colors, start = start, end = end),
                start,
                end,
                strokeWidth = r * 0.17f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(style.colors.first(), r * 0.16f, c)
    }
}

/** The end of an easter egg: a mark and its name come in at the top once it's done. */
@Composable
private fun BoxScope.EggFinale(show: Boolean, title: String, mark: @Composable () -> Unit) {
    val p by animateFloatAsState(if (show) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 180f), label = "finale")
    if (p <= 0.01f) return
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 64.dp)
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        mark()
        Spacer(Modifier.height(8.dp))
        Text(title, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** A ClaudeOS egg is done: its own Claude mark. */
@Composable
fun BoxScope.ClaudeOsFinale(show: Boolean, style: ClaudeMarkStyle) =
    EggFinale(show, style.title) { ClaudeMark(style, Modifier.size(84.dp)) }

/** An OMEGA UI egg is done: the ruby with the Ω. */
@Composable
fun BoxScope.OmegaFinale(show: Boolean) =
    EggFinale(show, "OMEGA UI") { OmegaRuby(size = 70.dp) }

/** A ZENITH egg is done: ZENITH's mark, the sun over the Z. */
@Composable
fun BoxScope.ZenithFinale(show: Boolean) =
    EggFinale(show, "ZENITH") { ZenithLogo(size = 96.dp, interactive = false) }
