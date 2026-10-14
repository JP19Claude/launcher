package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.EasterEggs
import kotlin.random.Random

/** A dot of the field: where (0..1), how big, which color. */
private class LensDot(val x: Float, val y: Float, val r: Float, val tone: Int)

/** Clawd drawn into a box at [at], [h] high. */
private fun DrawScope.tinyClawd(at: Offset, h: Float, color: Color) {
    val full = size
    val w = h * (14f / 11f)
    translate(at.x - w / 2f, at.y - h / 2f) {
        drawContext.size = Size(w, h)
        drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f)
        drawContext.size = full
    }
}

/**
 * OMEGA UI 18.5's easter egg – the lens. A field of tiny lights, and somewhere in it six Clawds,
 * far too small to see. Move the big lens of liquid glass over the field: what lies under it is
 * magnified. Found under the lens, a Clawd jumps out; once all six are out, the crystal comes in.
 */
@Composable
internal fun LensEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val palette = omegaPalette(LocalSettings.current)
    val dots = remember {
        val r = Random(185)
        List(320) { LensDot(r.nextFloat(), 0.08f + r.nextFloat() * 0.7f, 0.6f + r.nextFloat() * 1.6f, r.nextInt(5)) }
    }
    val hidden = remember {
        val r = Random(System.currentTimeMillis())
        List(6) { Offset(0.1f + r.nextFloat() * 0.8f, 0.14f + r.nextFloat() * 0.6f) }
    }
    val found = remember { mutableStateListOf<Int>() }
    val done = found.size >= hidden.size
    val density = LocalDensity.current
    val lensR = with(density) { 82.dp.toPx() }
    val tiny = with(density) { 7.dp.toPx() }
    val zoom = 3f
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070912), Color(0xFF101626), Color(0xFF06070C)))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        var lens by remember { mutableStateOf(Offset(w / 2f, h * 0.42f)) }
        fun move(to: Offset) {
            lens = to
            hidden.forEachIndexed { i, p ->
                if (i !in found && (Offset(p.x * w, p.y * h) - to).getDistance() < lensR * 0.45f) found += i
            }
        }
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.04f),
            fontSize = 180.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectDragGestures(onDragStart = { move(it) }) { change, _ -> move(change.position) } }
                .pointerInput(Unit) { detectTapGestures { move(it) } },
        ) {
            fun field() {
                dots.forEach { d ->
                    drawCircle(palette[d.tone % palette.size].copy(alpha = 0.55f), radius = d.r.dp.toPx(), center = Offset(d.x * size.width, d.y * size.height))
                }
                hidden.forEachIndexed { i, p ->
                    if (i !in found) tinyClawd(Offset(p.x * size.width, p.y * size.height), tiny, ClawdColor)
                }
            }
            field()
            // The lens: the field under it, magnified, inside a drop of clear glass.
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(lens, lensR)) }
            clipPath(circle) {
                drawCircle(Color(0xFF0B1020), radius = lensR, center = lens)
                scale(zoom, pivot = lens) { field() }
                drawCircle(
                    Brush.radialGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f)), center = lens, radius = lensR),
                    radius = lensR,
                    center = lens,
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = lens + Offset(-lensR * 0.4f, -lensR * 0.45f), radius = lensR * 0.5f),
                    radius = lensR * 0.5f,
                    center = lens + Offset(-lensR * 0.4f, -lensR * 0.45f),
                )
            }
            drawCircle(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.6f)),
                    start = lens - Offset(lensR, lensR),
                    end = lens + Offset(lensR, lensR),
                ),
                radius = lensR,
                center = lens,
                style = Stroke(2.5.dp.toPx()),
            )
            // The ones found jump out, big, where they hid.
            found.forEach { i ->
                val p = hidden[i]
                tinyClawd(Offset(p.x * size.width, p.y * size.height - 14.dp.toPx()), 30.dp.toPx(), ClawdColor)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Lupe", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) "Alle sechs gefunden – durch Liquid Glass sieht man alles. 🔍" else "Schieb die Lupe aus Glas über das Feld: Sechs winzige Clawds verstecken sich · ${found.size}/6",
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
