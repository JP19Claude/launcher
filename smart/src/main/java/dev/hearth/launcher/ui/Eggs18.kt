package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * OMEGA UI 18's easter egg – the forge. A grey stone lies on the anvil; every tap is a hammer
 * blow: it shakes, sparks fly, and it glows a little redder. After 18 blows it is the Ω ruby,
 * glowing – and the crystal comes in.
 */
@Composable
internal fun ForgeEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val blows = 18
    var hits by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val sparks = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val heat = (hits / blows.toFloat()).coerceIn(0f, 1f)
    val done = hits >= blows
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0606), Color(0xFF1E0A06), Color(0xFF070404))))
            .pointerInput(Unit) {
                detectTapGestures {
                    if (hits < blows) {
                        hits++
                        scope.launch {
                            shake.snapTo(1f)
                            shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 900f))
                        }
                        scope.launch {
                            sparks.snapTo(0f)
                            sparks.animateTo(1f, tween(650))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(HearthUi.major(version), color = Color.White.copy(alpha = 0.05f), fontSize = 200.sp, fontWeight = FontWeight.Black)
        // The forge's glow, hotter with every blow.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.46f)
            val r = size.minDimension * (0.35f + 0.3f * heat)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFF6A1A).copy(alpha = 0.15f + 0.45f * heat), Color(0xFFC8102E).copy(alpha = 0.12f * heat), Color.Transparent), center = c, radius = r),
                radius = r,
                center = c,
            )
            // The anvil.
            val aw = size.width * 0.5f
            val ah = size.height * 0.05f
            drawRect(Color(0xFF2B2B30), Offset(c.x - aw / 2f, c.y + 110.dp.toPx()), androidx.compose.ui.geometry.Size(aw, ah))
            drawRect(Color(0xFF1A1A1E), Offset(c.x - aw / 5f, c.y + 110.dp.toPx() + ah), androidx.compose.ui.geometry.Size(aw / 2.5f, ah * 1.6f))
            // Sparks flying from the last blow.
            if (sparks.value < 1f) {
                for (i in 0 until 14) {
                    val a = -PI / 2 + (i - 7) * 0.2 + hits * 0.7
                    val d = sparks.value * 160.dp.toPx() * (0.6f + (i % 3) * 0.2f)
                    val at = c + Offset(cos(a).toFloat() * d, sin(a).toFloat() * d + sparks.value * sparks.value * 60.dp.toPx())
                    drawCircle(Color(0xFFFFD27A).copy(alpha = 1f - sparks.value), radius = 2.5.dp.toPx(), center = at)
                }
            }
        }
        // The stone turning into the ruby: grey at first, the crystal's colors coming through.
        Box(
            Modifier
                .padding(bottom = 60.dp)
                .graphicsLayer {
                    translationX = shake.value * 10.dp.toPx() * (if (hits % 2 == 0) 1f else -1f)
                    val k = 0.75f + 0.25f * heat
                    scaleX = k
                    scaleY = k
                },
        ) {
            OmegaRuby(size = 170.dp, version = if (done) "OMEGA UI ${HearthUi.major(version)}" else null)
            if (!done) {
                Box(
                    Modifier
                        .size(170.dp)
                        .graphicsLayer { alpha = 0.85f * (1f - heat) }
                        .background(Brush.radialGradient(listOf(Color(0xFF6E6A66), Color(0xFF3A3734), Color.Transparent))),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Schmiede", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Geschmiedet! Der Ω-Rubin glüht. 🔨" else "Tipp, um zu hämmern – $blows Schläge machen aus dem Stein den Ω-Rubin · $hits/$blows",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        OmegaFinale(done)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

/**
 * ClaudeOS 5.0's egg, "Mythos": hold your finger on the dark and the golden Claude mark grows
 * and turns; let go and it fades. Hold long enough and the six robed Clawds of the council
 * appear round it – and ClaudeOS 5's own mark comes in.
 */
@Composable
internal fun MythosEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "claudeos") }
    BackHandler(onBack = onClose)
    val power = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(holding, done) {
        if (done) {
            power.animateTo(1f, tween(400))
        } else if (holding) {
            power.animateTo(1f, tween(((1f - power.value) * 3200).toInt().coerceAtLeast(1), easing = LinearEasing))
            done = true
        } else {
            power.animateTo(0f, tween((power.value * 1800).toInt().coerceAtLeast(1)))
        }
    }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) spin.animateTo(spin.value + 360f, tween(9000, easing = LinearEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050305), Color(0xFF140A04), Color(0xFF050305))))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    waitForUpOrCancellation()
                    holding = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val p = power.value
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val r = size.minDimension * (0.2f + 0.35f * p)
            drawCircle(
                Brush.radialGradient(listOf(IlluminatiGold.copy(alpha = 0.1f + 0.4f * p), Color.Transparent), center = c, radius = r * 1.8f),
                radius = r * 1.8f,
                center = c,
            )
            // The council appears round the mark.
            if (p > 0.6f) {
                val k = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                for (i in 0 until 6) {
                    val a = i * (2 * PI / 6) - PI / 2
                    val ring = size.minDimension * 0.38f
                    val ch = 54.dp.toPx() * k
                    val cw = ch * (14f / 9f)
                    if (ch < 2f) continue
                    val at = c + Offset(cos(a).toFloat() * ring, sin(a).toFloat() * ring)
                    drawRobedClawd(at - Offset(cw / 2f, ch / 2f), cw, ch, ClawdColor, IlluminatiGold, false)
                }
            }
        }
        Box(
            Modifier
                .padding(bottom = 120.dp)
                .size(140.dp)
                .graphicsLayer {
                    val k = 0.5f + 0.7f * p
                    scaleX = k
                    scaleY = k
                    rotationZ = spin.value
                    alpha = 0.35f + 0.65f * p
                },
        ) {
            ClaudeMark(ClaudeMarkStyle.Mythos, Modifier.fillMaxSize())
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${LocalClaudeOsEra.current.full} „${LocalClaudeOsEra.current.codename}“", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Der Rat ist versammelt. Mythos ist erwacht. ✦" else "Halte den Finger auf das Dunkel und lass den Mythos erwachen.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClaudeOsFinale(done, ClaudeMarkStyle.Mythos)
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
