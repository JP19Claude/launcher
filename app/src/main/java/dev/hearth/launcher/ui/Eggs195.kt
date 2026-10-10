package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.StrikeColor
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Where the five cores float, as fractions of the screen. */
private val StormCores = listOf(
    Offset(0.24f, 0.26f),
    Offset(0.76f, 0.22f),
    Offset(0.5f, 0.45f),
    Offset(0.26f, 0.63f),
    Offset(0.75f, 0.61f),
)

private val StormColors = listOf(StrikeColor.Green, StrikeColor.Blue, StrikeColor.Violet, StrikeColor.Red, StrikeColor.Gold)

/** A core's place at [drift] (0..1, once round its small loop). */
private fun corePlace(i: Int, drift: Float, w: Float, h: Float): Offset {
    val a = drift * 2f * PI.toFloat()
    val home = StormCores[i]
    return Offset(
        (home.x + 0.035f * sin(a + i * 1.7f)) * w,
        (home.y + 0.025f * cos(2f * a + i * 0.9f)) * h,
    )
}

/**
 * ZENITH 19.5's easter egg – the Z-Sturm. Five cores of light float in the dark, each in one of
 * the Z-Angriff's colors. Tap one and it strikes: the Z erupts in its color. When all five have
 * struck, the white one comes by itself – and ZENITH's mark comes in.
 */
@Composable
internal fun ZStormEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    var struck by remember { mutableStateOf(emptySet<Int>()) }
    var striking by remember { mutableStateOf<Color?>(null) }
    var done by remember { mutableStateOf(false) }
    val strike = remember { Animatable(0f) }
    val loop = rememberInfiniteTransition(label = "zStorm")
    val drift by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "drift")
    val pulse by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pulse")
    val dust = remember {
        val r = Random(195)
        List(70) { Triple(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.3f) }
    }

    fun fire(tint: Color, millis: Int, then: () -> Unit) {
        striking = tint
        scope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            strike.snapTo(0f)
            strike.animateTo(1f, tween(millis, easing = LinearEasing))
            striking = null
            then()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF010403), Color(0xFF041009), Color(0xFF010302)))),
    ) {
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.05f),
            fontSize = 200.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        if (striking != null || done) return@detectTapGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val reach = 56.dp.toPx()
                        val hit = StormColors.indices.firstOrNull { i ->
                            i !in struck && (corePlace(i, drift, w, h) - at).getDistance() < reach
                        } ?: return@detectTapGestures
                        struck = struck + hit
                        fire(StormColors[hit].color, 1500) {
                            if (struck.size == StormColors.size) {
                                fire(StrikeColor.White.color, 2600) { done = true }
                            }
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            // Dust of light, drifting up.
            dust.forEach { (x, y, r) ->
                val yy = ((y - drift * 0.2f) % 1f + 1f) % 1f
                drawCircle(ZenithGreen.copy(alpha = 0.25f), r.dp.toPx(), Offset(x * w, yy * h))
            }
            // The cores: a hexagon of light round a small metal Z; spent ones only glow faintly.
            StormColors.forEachIndexed { i, color ->
                val c = corePlace(i, drift, w, h)
                val spent = i in struck
                val r = 30.dp.toPx() * (if (spent) 0.8f else 1f + 0.06f * sin(pulse * 2f * PI.toFloat() + i))
                val tint = color.color
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = if (spent) 0.12f else 0.45f), Color.Transparent), center = c, radius = r * 2.6f),
                    radius = r * 2.6f,
                    center = c,
                )
                val hex = Path()
                for (k in 0 until 6) {
                    val a = Math.toRadians(60.0 * k - 90.0)
                    val x = c.x + (cos(a) * r).toFloat()
                    val y = c.y + (sin(a) * r).toFloat()
                    if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
                }
                hex.close()
                drawPath(hex, Color(0xFF020805).copy(alpha = 0.85f))
                drawPath(hex, tint.copy(alpha = if (spent) 0.35f else 1f), style = Stroke(2.5.dp.toPx()))
                val zw = r * 1.05f
                drawZenithZ(c.x - zw / 2f, c.y - zw * ZenithZAspect / 2f, zw, light = if (spent) 0.2f else 0.9f)
            }
        }
        // The strike over everything, in the color of the core that was tapped.
        Canvas(Modifier.fillMaxSize()) {
            val tint = striking
            if (tint != null) drawZenithStrike(strike.value, tint)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Z-Sturm", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Fünf Kerne, fünf Angriffe – und zum Schluss der weiße Z-Sturm. ⚡" else "Tippe die Kerne an – jeder schlägt in seiner Farbe zu · ${struck.size}/5",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ZenithFinale(done)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
