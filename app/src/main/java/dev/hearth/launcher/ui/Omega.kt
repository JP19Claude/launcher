package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** OMEGA UI's colors: Claude's warmth into deep violet and ice blue. */
internal val OmegaColors = listOf(Color(0xFFFFB494), Color(0xFFFF6FB5), Color(0xFF8E6BFF), Color(0xFF6FD3FF))

/** The Ω, big, in OMEGA UI's colors with light across its top. */
@Composable
internal fun OmegaMark(size: Int, modifier: Modifier = Modifier, fade: Float = 1f) {
    Text(
        "Ω",
        style = TextStyle(
            brush = Brush.verticalGradient(listOf(Color.White, OmegaColors[1], OmegaColors[2], OmegaColors[3])),
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
        ),
        modifier = modifier.graphicsLayer { alpha = fade },
    )
}

/**
 * Once, after the update: "Hearth UI is now OMEGA UI" – the Ω rising in the dark, the old name
 * fading into the new one.
 */
@Composable
fun OmegaWelcome(onDone: () -> Unit) {
    val animate = LocalSettings.current.animations
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    val swap = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 90f))
        swap.animateTo(1f, tween(900))
    }
    BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07060F), Color(0xFF150D2A), Color(0xFF040406))))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        FluidBackdrop(OmegaColors.take(3), strength = 0.8f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            OmegaMark(
                190,
                Modifier.graphicsLayer {
                    val p = rise.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 120.dp.toPx()
                    scaleX = 0.7f + 0.3f * p
                    scaleY = 0.7f + 0.3f * p
                },
            )
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "Hearth UI",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - swap.value
                        translationY = -swap.value * 20.dp.toPx()
                    },
                )
                Text(
                    "OMEGA UI",
                    style = TextStyle(brush = Brush.linearGradient(OmegaColors), fontSize = 40.sp, fontWeight = FontWeight.Black),
                    modifier = Modifier.graphicsLayer {
                        alpha = swap.value
                        translationY = (1f - swap.value) * 20.dp.toPx()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "auf ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Hearth UI heißt jetzt OMEGA UI. Gleiches System, neuer Name – deine Einstellungen, Widgets, Apps und Easter Eggs bleiben, wie sie sind.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(bottom = 18.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.horizontalGradient(OmegaColors))
                    .glassSheen(27.dp)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("Los geht's", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val Greek = "αβγδεζηθικλμνξοπρστυφχψ".toList()

/**
 * The Ω easter egg (look for "Omega" in the finder): from Alpha to Omega – each tap sends the
 * next Greek letter into orbit around the Ω; when all 23 circle it, the Ω takes them in.
 */
@Composable
internal fun OmegaEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    var count by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(0L) }
    val full = count >= Greek.size
    val radius by animateFloatAsState(if (full) 0f else 1f, tween(1400), label = "orbit")
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = it } }
    LaunchedEffect(full) { if (full) EasterEggs.find(context, "omega") }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05040C), Color(0xFF120A26), Color(0xFF030305))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (!full) count++ },
        contentAlignment = Alignment.Center,
    ) {
        FluidBackdrop(OmegaColors.take(3), strength = if (full) 1f else 0.4f)
        OmegaMark(if (full) 200 else 150)
        val r = 140f * radius
        Greek.take(count).forEachIndexed { i, letter ->
            val a = now / 1800.0 + i * 2 * Math.PI / Greek.size
            Text(
                letter.toString(),
                style = TextStyle(brush = Brush.linearGradient(OmegaColors), fontSize = 26.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .offset { IntOffset((cos(a) * r * density).roundToInt(), (sin(a) * r * density * 0.9).roundToInt()) }
                    .graphicsLayer { alpha = radius.coerceIn(0f, 1f) },
            )
        }
        if (full) {
            Clawd(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .size(width = 90.dp, height = 78.dp),
                mood = ClawdMood.Dance,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("OMEGA UI · Von Alpha bis Omega", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (full) "Alles drin. Ω" else "Tippen – ${Greek.getOrNull(count) ?: 'ω'} kommt dazu · $count/${Greek.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
