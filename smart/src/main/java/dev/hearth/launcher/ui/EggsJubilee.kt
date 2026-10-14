package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AuroraLook
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.StrikeColor
import dev.hearth.launcher.data.ZenithSlogan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 20, the jubilee: twenty versions of ZENITH (and Hearth and OMEGA before it). A firework
 * for each – the ceremony that opens the update, and the egg behind the version number.
 */

private val GoldLight = Color(0xFFFFD98A)

/**
 * A firework: [t] runs from 0 (the flash) to 1 (the last ember), [reach] is how far the sparks
 * fly in pixels and [seed] makes each burst its own. Gravity pulls the embers down at the end.
 */
internal fun DrawScope.drawBurst(origin: Offset, t: Float, color: Color, reach: Float, seed: Int) {
    if (t <= 0f || t >= 1f) return
    val rnd = Random(seed)
    val unit = reach / 150f
    val ease = 1f - (1f - t) * (1f - t) * (1f - t)
    val fade = (1f - t).coerceIn(0f, 1f)
    // The flash at the start.
    if (t < 0.2f) {
        val f = 1f - t / 0.2f
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * f), color.copy(alpha = 0.35f * f), Color.Transparent), center = origin, radius = reach * 0.6f),
            radius = reach * 0.6f,
            center = origin,
        )
    }
    for (i in 0 until 44) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val speed = 0.4f + rnd.nextFloat() * 0.6f
        val d = reach * speed * ease
        val x = origin.x + cos(a) * d
        val y = origin.y + sin(a) * d + reach * 0.4f * t * t
        val tail = reach * speed * 0.1f * (1f - t)
        val spark = if (i % 6 == 0) Color.White else lerp(color, Color.White, 0.3f * rnd.nextFloat())
        drawLine(
            spark.copy(alpha = fade),
            Offset(x - cos(a) * tail, y - sin(a) * tail - reach * 0.05f * t),
            Offset(x, y),
            strokeWidth = 2.2f * unit,
            cap = StrokeCap.Round,
        )
        drawCircle(spark.copy(alpha = fade), (1.6f + 1.6f * fade) * unit, Offset(x, y))
    }
}

/** Where the ceremony's fireworks go, as fractions of the screen, and in which color. */
private val CeremonyBursts = listOf(
    Triple(0.25f, 0.28f, StrikeColor.Gold),
    Triple(0.76f, 0.22f, StrikeColor.Green),
    Triple(0.5f, 0.14f, StrikeColor.White),
    Triple(0.2f, 0.62f, StrikeColor.Blue),
    Triple(0.8f, 0.6f, StrikeColor.Violet),
    Triple(0.36f, 0.84f, StrikeColor.Red),
    Triple(0.68f, 0.82f, StrikeColor.Gold),
    Triple(0.5f, 0.42f, StrikeColor.White),
)

/**
 * ZENITH 20's ceremony, once after the update: the numbers run up from 1 to 20 in the dark, the
 * 20 locks in with a shockwave of gold, and the fireworks start – ZENITH, Jubiläum. A tap goes on
 * (after the first moments); left alone it ends by itself.
 */
@Composable
internal fun ZenithJubilee(onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(0L) }
    var start by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                if (start < 0L) start = t
                now = t
            }
        }
    }
    val time = if (start < 0L) 0L else now - start
    val clock = rememberUpdatedState(time)
    val finish = rememberUpdatedState(onDone)
    val lockAt = 2400L
    val locked = time >= lockAt
    LaunchedEffect(locked) { if (locked) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(time > 18_000L) { if (time > 18_000L) finish.value() }
    BackHandler { finish.value() }

    val ramp = (time / lockAt.toFloat()).coerceIn(0f, 1f)
    val number = if (locked) 20 else 1 + (19 * ramp * ramp * (3f - 2f * ramp)).toInt()
    val sinceLock = (time - lockAt).coerceAtLeast(0L)
    val pulse = if (locked) 1f + 0.18f * max(0f, 1f - sinceLock / 500f) else 1f

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF010203), Color(0xFF0A0810), Color(0xFF021008))))
            .pointerInput(Unit) { detectTapGestures { if (clock.value > 3000L) finish.value() } },
    ) {
        ZenithAurora(
            AuroraLook.Gold,
            Modifier.fillMaxSize().graphicsLayer { alpha = ((sinceLock - 200L) / 1800f).coerceIn(0f, 1f) },
            strength = 0.9f,
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h * 0.4f)
            // The numbers running up: a ring that grows with them.
            if (!locked) {
                drawCircle(GoldLight.copy(alpha = 0.12f + 0.2f * ramp), size.minDimension * (0.18f + 0.1f * ramp), center, style = Stroke(2.dp.toPx()))
            } else {
                // The shockwave, three rings one after the other.
                for (k in 0 until 3) {
                    val p = ((sinceLock - k * 220L) / 1600f)
                    if (p in 0f..1f) {
                        drawCircle(GoldLight.copy(alpha = 0.7f * (1f - p)), size.maxDimension * 0.75f * p, center, style = Stroke((6f - 4f * p).dp.toPx()))
                    }
                }
            }
            // Fireworks, one every 650 milliseconds, the first few on a fixed plan and then on and on.
            if (locked) {
                val newest = ((time - lockAt - 100L) / 650L).toInt()
                for (k in max(0, newest - 3)..newest) {
                    val t = (time - lockAt - 100L - k * 650L) / 2000f
                    val plan = CeremonyBursts[k % CeremonyBursts.size]
                    val jitter = Random(k * 31 + 7)
                    val at = Offset(
                        (plan.first + (jitter.nextFloat() - 0.5f) * 0.08f) * w,
                        (plan.second + (jitter.nextFloat() - 0.5f) * 0.06f) * h,
                    )
                    drawBurst(at, t, plan.third.color, size.minDimension * 0.42f, k * 977 + 13)
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                number.toString(),
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, GoldLight, Color(0xFFE0A63A))),
                    fontSize = 170.sp,
                    fontWeight = FontWeight.Black,
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
            )
            if (time > 3000L) {
                Text(
                    "ZENITH",
                    color = Color.White.copy(alpha = ((time - 3000L) / 700f).coerceIn(0f, 1f)),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 14.sp,
                )
            }
            if (time > 4300L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "JUBILÄUM",
                    color = GoldLight.copy(alpha = ((time - 4300L) / 700f).coerceIn(0f, 1f)),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 10.sp,
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp, start = 28.dp, end = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (time > 6000L) {
                Text(
                    "Zwanzig Versionen. Danke, dass du von Anfang an dabei bist.",
                    color = Color.White.copy(alpha = ((time - 6000L) / 900f).coerceIn(0f, 0.85f)),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (time > 7000L) {
                // ZENITH's saying, to close.
                Spacer(Modifier.height(14.dp))
                Text(
                    ZenithSlogan.UPPER,
                    color = GoldLight.copy(alpha = ((time - 7000L) / 900f).coerceIn(0f, 1f)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                )
            }
            if (time > 8000L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tippen zum Weitermachen",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

/** The finder's word "Jubiläum" (a hidden menu), or 20:20 on the clock: the ceremony once more. */
@Composable
internal fun JubileeWordEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "jubileeword") }
    ZenithJubilee(onClose)
}

/** A firework the finger asked for. */
private class Firework(val at: Offset, val born: Long, val color: Color, val seed: Int)

/**
 * ZENITH 20's easter egg, "Feuerwerk": the 20 stands dim in the dark; every tap sets off a
 * firework in the next of the Z-Angriff's colors. Twenty fireworks for twenty versions – then the
 * 20 lights up gold and ZENITH's mark comes in.
 */
@Composable
internal fun JubileeEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val works = remember { mutableStateListOf<Firework>() }
    var now by remember { mutableLongStateOf(0L) }
    var count by remember { mutableIntStateOf(0) }
    val done = count >= 20
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                now = t
                works.removeAll { t - it.born > 2000L }
            }
        }
    }
    val colors = remember { StrikeColor.entries.map { it.color } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF010203), Color(0xFF0A0810), Color(0xFF021008)))),
    ) {
        ZenithAurora(AuroraLook.Gold, Modifier.fillMaxSize(), strength = if (done) 0.9f else 0.45f)
        Text(
            HearthUi.major(version),
            color = if (done) GoldLight.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
            fontSize = 220.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val n = count
                        works.add(Firework(at, now, colors[n % colors.size], n * 7 + 3))
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        count = n + 1
                    }
                },
        ) {
            val reach = size.minDimension * 0.4f
            works.forEach { f -> drawBurst(f.at, (now - f.born) / 2000f, f.color, reach, f.seed) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Jubiläum", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Zwanzig Feuerwerke für zwanzig Versionen. Danke für alles. ✨" else "Tippe – für jede Version ein Feuerwerk · ${count.coerceAtMost(20)}/20",
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
