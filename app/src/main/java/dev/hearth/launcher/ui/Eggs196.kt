package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ZENITH 19.6: the hidden screens you only get to by looking for the right word in the finder
 * ("hearth", "konami", "fluid", "zenith", "sudo su", "hello world", "π") – Hearth's fireplace,
 * the old code, OMEGA Fluid and a few jokes. They all stand on the same stage.
 */

/** What every hidden screen stands on: its background, a title and a hint at the foot, a way out. */
@Composable
internal fun SecretStage(
    onClose: () -> Unit,
    background: Brush,
    title: String,
    hint: String,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            // Takes every touch: nothing below reacts while a hidden screen is open.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        content()
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(hint, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center)
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

// ---------------------------------------------------------------------------------------------
// Hearth: the fireplace ("hearth", "kamin")
// ---------------------------------------------------------------------------------------------

private fun DrawScope.flame(x: Float, base: Float, width: Float, height: Float, sway: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - width / 2f, base)
        cubicTo(x - width / 2f, base - height * 0.35f, x - width * 0.25f + sway, base - height * 0.7f, x + sway, base - height)
        cubicTo(x + width * 0.25f + sway, base - height * 0.7f, x + width / 2f, base - height * 0.35f, x + width / 2f, base)
        close()
    }
    drawPath(path, color, blendMode = BlendMode.Plus)
}

/**
 * Hearth's easter egg – where the name comes from. A fireplace in the dark, the fire small and
 * low; every tap lays a log on, and the flames climb. After five logs it burns properly.
 */
@Composable
internal fun HearthKaminEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(0L) }
    var logs by remember { mutableIntStateOf(0) }
    var boost by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last == 0L) 16L else t - last
                last = t
                now = t
                boost = (boost - dt / 2500f).coerceAtLeast(0f)
            }
        }
    }
    val done = logs >= 5
    LaunchedEffect(done) { if (done) EasterEggs.find(context, "kamin") }
    val power = (0.35f + 0.12f * logs.coerceAtMost(8) + 0.35f * boost).coerceIn(0.2f, 1.25f)
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF0A0504), Color(0xFF1B0C07), Color(0xFF0A0504))),
        "Hearth · Der Kamin",
        if (done) "Es brennt. So hat alles angefangen – mit einem Feuer. 🔥" else "Tippe, um ein Scheit nachzulegen · $logs/5",
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        logs++
                        boost = 1f
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val t = now / 1000f
            val floorY = h * 0.74f
            val fw = w * 0.74f
            val fh = h * 0.34f
            val left = (w - fw) / 2f
            val top = floorY - fh
            // The glow it throws on the wall.
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFFF8A3D).copy(alpha = 0.32f * power), Color.Transparent),
                    center = Offset(w / 2f, floorY - fh * 0.3f),
                    radius = w * 1.05f,
                ),
            )
            // The stones round the opening, and the dark inside.
            drawRoundRect(
                Color(0xFF2A1B15),
                Offset(left - 22.dp.toPx(), top - 22.dp.toPx()),
                Size(fw + 44.dp.toPx(), fh + 22.dp.toPx()),
                CornerRadius(28.dp.toPx()),
            )
            drawRoundRect(Color(0xFF070302), Offset(left, top), Size(fw, fh), CornerRadius(18.dp.toPx()))
            // Logs: two lying crosswise, one more for every tap.
            val base = floorY - 10.dp.toPx()
            for (k in 0 until (2 + logs.coerceAtMost(6))) {
                val x = left + fw * (0.22f + 0.56f * ((k * 37) % 100) / 100f)
                val tilt = if (k % 2 == 0) 8f else -8f
                rotate(tilt, Offset(x, base)) {
                    drawRoundRect(
                        Color(0xFF3B2416),
                        Offset(x - 46.dp.toPx(), base - 9.dp.toPx() - (k / 2) * 6.dp.toPx()),
                        Size(92.dp.toPx(), 18.dp.toPx()),
                        CornerRadius(9.dp.toPx()),
                    )
                }
            }
            // Embers glowing under the flames.
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFFFF6A1F).copy(alpha = 0.5f * power), Color.Transparent),
                    startX = left,
                    endX = left + fw,
                ),
                Offset(left, base - 4.dp.toPx()),
                Size(fw, 12.dp.toPx()),
                CornerRadius(6.dp.toPx()),
            )
            // The flames: seven tongues, each swaying on its own.
            for (i in 0 until 7) {
                val fx = left + fw * (0.14f + 0.72f * i / 6f)
                val sway = sin(t * (2.2f + i * 0.35f) + i * 1.3f)
                val tall = fh * (0.5f + 0.42f * (0.5f + 0.5f * sin(t * (3.1f + i * 0.5f) + i * 2.1f))) * power
                val fwid = fw * 0.17f
                flame(fx, base, fwid, tall, sway * 10.dp.toPx(), Color(0xFFFF5A1F).copy(alpha = 0.55f))
                flame(fx, base, fwid * 0.7f, tall * 0.8f, sway * 7.dp.toPx(), Color(0xFFFFB03A).copy(alpha = 0.6f))
                flame(fx, base, fwid * 0.4f, tall * 0.55f, sway * 4.dp.toPx(), Color(0xFFFFF1B0).copy(alpha = 0.7f))
            }
            // Sparks rising out of it.
            for (k in 0 until 36) {
                val life = (t * (0.22f + (k % 5) * 0.07f) + k * 0.618f) % 1f
                val sx = left + fw * (0.1f + 0.8f * ((k * 37) % 100) / 100f) + sin(t * 2f + k) * 16.dp.toPx() * life
                val sy = base - fh * 0.2f - life * h * 0.5f * power
                val a = ((1f - life) * power).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFB347).copy(alpha = a), (1.2f + (k % 3)).dp.toPx() * (1f - life * 0.6f), Offset(sx, sy))
            }
            // The floor in front.
            drawRect(Color(0xFF120B08), Offset(0f, floorY), Size(w, h - floorY))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The old code ("konami")
// ---------------------------------------------------------------------------------------------

private val KonamiCode = listOf('U', 'U', 'D', 'D', 'L', 'R', 'L', 'R', 'B', 'A')

@Composable
private fun PadKey(label: String, round: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(66.dp)
            .clip(if (round) CircleShape else RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The old code: ↑ ↑ ↓ ↓ ← → ← → B A. A pad with its keys, ten dots that fill as you get it right;
 * a wrong key starts over. Done, Clawd gets thirty lives and the colors rain.
 */
@Composable
internal fun KonamiEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = it } }
    val done = step >= KonamiCode.size
    LaunchedEffect(done) { if (done) EasterEggs.find(context, "konami") }
    fun press(key: Char) {
        if (done) return
        step = if (KonamiCode[step] == key) step + 1 else if (key == KonamiCode[0]) 1 else 0
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF080A18), Color(0xFF140C2A), Color(0xFF05050C))),
        "Der alte Code",
        if (done) "+30 Leben. Clawd bedankt sich." else "Du weißt, welcher. ${KonamiCode.size - step} Tasten noch",
    ) {
        if (done) {
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0 until 70) {
                    val speed = 0.00018f + (k % 7) * 0.00004f
                    val y = ((now * speed + k * 0.137f) % 1.1f - 0.05f) * size.height
                    val x = ((k * 53) % 100) / 100f * size.width + sin(now / 700f + k) * 14.dp.toPx()
                    val color = AiFluidColors[k % AiFluidColors.size]
                    rotate(now / 6f + k * 30f, Offset(x, y)) {
                        drawRect(color.copy(alpha = 0.9f), Offset(x, y), Size(10.dp.toPx(), 6.dp.toPx()))
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KonamiCode.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < step) ZenithGreen else Color.White.copy(alpha = 0.15f)),
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            if (done) {
                Clawd(Modifier.size(width = 110.dp, height = 94.dp), mood = ClawdMood.Dance)
                Spacer(Modifier.height(10.dp))
                Text("30 Leben", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PadKey("▲", false, Color(0xFF8E6BFF)) { press('U') }
                        Row {
                            PadKey("◀", false, Color(0xFF8E6BFF)) { press('L') }
                            Spacer(Modifier.width(66.dp))
                            PadKey("▶", false, Color(0xFF8E6BFF)) { press('R') }
                        }
                        PadKey("▼", false, Color(0xFF8E6BFF)) { press('D') }
                    }
                    Spacer(Modifier.width(36.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PadKey("B", true, Color(0xFFFF6FB5)) { press('B') }
                        PadKey("A", true, Color(0xFFFF6FB5)) { press('A') }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// OMEGA Fluid ("fluid")
// ---------------------------------------------------------------------------------------------

/**
 * OMEGA UI's easter egg – the fluid. Sixteen blobs of light drift in the dark; put a finger on
 * the screen and they come to it, swirling. Hold it there for six seconds and it follows you.
 */
@Composable
internal fun OmegaFluidEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val n = 16
    val px = remember { FloatArray(n) { (it * 0.37f + 0.1f) % 1f } }
    val py = remember { FloatArray(n) { (it * 0.61f + 0.2f) % 1f } }
    val vx = remember { FloatArray(n) { ((it * 7) % 5 - 2) * 0.02f } }
    val vy = remember { FloatArray(n) { ((it * 3) % 5 - 2) * 0.02f } }
    var tick by remember { mutableIntStateOf(0) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var held by remember { mutableFloatStateOf(0f) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1_000_000_000f).coerceAtMost(0.033f)
                last = t
                val at = touch
                val sec = t / 1_000_000_000f
                for (i in 0 until n) {
                    if (at != null) {
                        val dx = at.x - px[i]
                        val dy = at.y - py[i]
                        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
                        vx[i] += (dx / d) * 0.9f * dt - (dy / d) * 0.5f * dt
                        vy[i] += (dy / d) * 0.9f * dt + (dx / d) * 0.5f * dt
                    } else {
                        vx[i] += sin(sec + i) * 0.03f * dt
                        vy[i] += cos(sec * 0.8f + i * 1.7f) * 0.03f * dt
                    }
                    vx[i] *= 0.985f
                    vy[i] *= 0.985f
                    px[i] += vx[i] * dt * 2f
                    py[i] += vy[i] * dt * 2f
                    if (px[i] < 0f) { px[i] = 0f; vx[i] = -vx[i] }
                    if (px[i] > 1f) { px[i] = 1f; vx[i] = -vx[i] }
                    if (py[i] < 0f) { py[i] = 0f; vy[i] = -vy[i] }
                    if (py[i] > 1f) { py[i] = 1f; vy[i] = -vy[i] }
                }
                if (at != null) held += dt
                tick++
            }
        }
    }
    val done = held >= 6f
    LaunchedEffect(done) { if (done) EasterEggs.find(context, "fluid") }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF05040C), Color(0xFF0E0A22), Color(0xFF030305))),
        "OMEGA Fluid",
        if (done) "Es folgt dir. Das war OMEGA UI." else "Finger aufs Licht und halten · ${held.toInt().coerceAtMost(6)}/6 s",
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { area = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull { it.pressed }
                            touch = if (press == null || area.width == 0 || area.height == 0) {
                                null
                            } else {
                                Offset(press.position.x / area.width, press.position.y / area.height)
                            }
                        }
                    }
                },
        ) {
            val frame = tick
            val unit = minOf(size.width, size.height)
            for (i in 0 until n) {
                val c = Offset(px[i] * size.width, py[i] * size.height)
                val r = (0.2f + (i % 4) * 0.035f) * unit * (if (done) 1.25f else 1f)
                val color = OmegaColors[(i + frame / 600) % OmegaColors.size]
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0f)), center = c, radius = r),
                    r,
                    c,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// "zenith"
// ---------------------------------------------------------------------------------------------

/** The word itself: the Z-Angriff, once, and then the Z – and nothing else. */
@Composable
internal fun ZenithWordEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val strike = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    LaunchedEffect(Unit) {
        EasterEggs.find(context, "zenithword")
        strike.animateTo(1f, tween(2400, easing = LinearEasing))
        shown = true
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color.Black, Color(0xFF031009))),
        "Zenith",
        if (shown) "Du hast den höchsten Punkt gefunden." else "…",
    ) {
        if (shown) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ZenithLogo(size = 260.dp, number = HearthUi.major(version), caption = "ZENITH")
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (strike.value < 1f) drawZenithStrike(strike.value)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// "sudo su"
// ---------------------------------------------------------------------------------------------

/** A terminal that says no, once, and tells Clawd. */
@Composable
internal fun SudoEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val script = remember {
        listOf(
            "$ sudo make me a sandwich",
            "[sudo] Passwort für du: ••••••••",
            "du ist nicht in der sudoers-Datei.",
            "Dieser Vorfall wird gemeldet.",
            "",
            "→ Empfänger: Clawd",
            "→ Betreff: „Schon wieder jemand“",
            "→ Clawd antwortet: 🥪 (aber nur mit „bitte“)",
        )
    }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        EasterEggs.find(context, "sudo")
        for (i in 1..script.size) {
            delay(650)
            shown = i
        }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF0C0204), Color(0xFF1A0508), Color(0xFF060102))),
        "Sudo",
        "Nette Idee.",
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 22.dp)) {
            script.take(shown).forEach { line ->
                Text(
                    line,
                    color = if (line.startsWith("$")) HudGreen else Color(0xFFFF8A80),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (shown >= script.size) {
                Spacer(Modifier.height(22.dp))
                Clawd(Modifier.size(width = 90.dp, height = 78.dp), mood = ClawdMood.Surprised)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// "hello world"
// ---------------------------------------------------------------------------------------------

/** The first program of them all, in a few languages, typed out one letter at a time. Tap for the next. */
@Composable
internal fun HelloWorldEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    val samples = remember {
        listOf(
            "Python" to "print(\"Hello, World!\")",
            "Kotlin" to "fun main() = println(\"Hello, World!\")",
            "C" to "printf(\"Hello, World!\\n\");",
            "JavaScript" to "console.log(\"Hello, World!\");",
            "ZENITH" to "Z.strike(\"Hallo, Welt!\")",
            "Clawd" to "*winkt* Hallo, Welt!",
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var chars by remember { mutableIntStateOf(0) }
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        EasterEggs.find(context, "hello")
        while (true) {
            delay(500)
            blink = !blink
        }
    }
    val (language, code) = samples[index]
    LaunchedEffect(index) {
        chars = 0
        while (chars < code.length) {
            delay(55)
            chars++
        }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF05080F), Color(0xFF0A1424), Color(0xFF03050A))),
        "Hallo, Welt",
        "Tippe für die nächste Sprache · ${index + 1}/${samples.size}",
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) { index = (index + 1) % samples.size },
        ) {
            Text(language, color = ZenithGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            Text(
                "> " + code.take(chars) + if (chars < code.length || blink) "▌" else " ",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
            )
            if (chars >= code.length) {
                Spacer(Modifier.height(14.dp))
                Text("Hello, World!", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 17.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// "π"
// ---------------------------------------------------------------------------------------------

private const val PiDigits = "3141592653589793238462643383279502884197169399375105820974944592"
private const val EDigits = "2718281828459045235360287471352662497757247093699959574966967627"

/** π, with its digits in a ring that turns – and e circling the other way inside. Tap and it spins faster. */
@Composable
internal fun PiEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(0L) }
    var angle by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(6f) }
    LaunchedEffect(Unit) { EasterEggs.find(context, "pi") }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last == 0L) 16L else t - last
                last = t
                now = t
                angle += speed * dt / 1000f
                speed = (speed - dt / 4000f * (speed - 6f)).coerceAtLeast(6f)
            }
        }
    }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    SecretStage(
        onClose,
        Brush.verticalGradient(listOf(Color(0xFF060814), Color(0xFF0D1330), Color(0xFF03040A))),
        "π",
        "Tippe – es dreht schneller. 3,14159 …",
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { speed = (speed + 40f).coerceAtMost(240f) } },
        ) {
            val c = center
            val unit = minOf(size.width, size.height)
            drawIntoCanvas { canvas ->
                paint.textSize = unit * 0.05f
                val outer = unit * 0.4f
                for (i in PiDigits.indices) {
                    val a = Math.toRadians((angle + i * 360.0 / PiDigits.length)) - PI / 2
                    val lit = ((i * 360f / PiDigits.length + angle) % 360f) < 30f
                    paint.color = (if (lit) Color.White else Color(0xFF8E9BFF)).copy(alpha = if (lit) 1f else 0.7f).toArgb()
                    canvas.nativeCanvas.drawText(PiDigits[i].toString(), c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat() + paint.textSize / 3f, paint)
                }
                paint.textSize = unit * 0.04f
                val inner = unit * 0.27f
                for (i in EDigits.indices) {
                    val a = Math.toRadians((-angle * 0.6 + i * 360.0 / EDigits.length)) - PI / 2
                    paint.color = Color(0xFFFF9EC8).copy(alpha = 0.55f).toArgb()
                    canvas.nativeCanvas.drawText(EDigits[i].toString(), c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat() + paint.textSize / 3f, paint)
                }
                paint.textSize = unit * 0.2f
                paint.color = Color.White.toArgb()
                canvas.nativeCanvas.drawText("π", c.x, c.y + paint.textSize / 3f, paint)
            }
        }
    }
}
