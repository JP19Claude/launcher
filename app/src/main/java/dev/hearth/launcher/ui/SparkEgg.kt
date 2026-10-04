package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class DropSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val born: Long)

/**
 * Hearth UI 15.5's easter egg – the update of Glimmer Drop: two Glimmer islands. Pull the lower
 * one up against the other, like two phones held together, and sparks fly. Five times, and
 * Clawd dances.
 */
@Composable
internal fun SparkEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    var area by remember { mutableStateOf(Size.Zero) }
    var pos by remember { mutableStateOf(Offset.Unspecified) }
    val sparks = remember { mutableStateListOf<DropSpark>() }
    var hits by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(0L) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pillW = with(density) { 150.dp.toPx() }
    val pillH = with(density) { 44.dp.toPx() }

    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last < 0) 0.016f else (t - last).coerceIn(1L, 40L) / 1000f
                last = t
                sparks.forEach { s ->
                    s.vy += 900f * dt
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                }
                sparks.removeAll { t - it.born > 1400 }
                now = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF04050D), Color(0xFF101433), Color(0xFF030306))))
            .onSizeChanged {
                area = Size(it.width.toFloat(), it.height.toFloat())
                if (pos == Offset.Unspecified) pos = Offset(it.width / 2f, it.height * 0.72f)
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (pos == Offset.Unspecified) return@detectDragGestures
                    pos += drag
                    val top = Offset(area.width / 2f, area.height * 0.28f)
                    val meet = abs(pos.x - top.x) < pillW * 0.8f && abs(pos.y - top.y) < pillH * 1.05f
                    if (meet && !touching) {
                        touching = true
                        hits++
                        val at = Offset((pos.x + top.x) / 2f, (pos.y + top.y) / 2f)
                        repeat(46) { i ->
                            val a = Random.nextFloat() * 6.283f
                            val speed = 250f + Random.nextFloat() * 650f
                            sparks += DropSpark(
                                at.x, at.y,
                                cos(a) * speed, sin(a) * speed - 200f,
                                AiFluidColors[i % AiFluidColors.size], now,
                            )
                        }
                    } else if (!meet && abs(pos.y - top.y) > pillH * 2f) {
                        touching = false
                    }
                }
            },
    ) {
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.05f),
            fontSize = 170.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Canvas(Modifier.fillMaxSize()) {
            if (now < 0L || area == Size.Zero) return@Canvas
            val top = Offset(size.width / 2f, size.height * 0.28f)
            pill(top, pillW, pillH, 0.6f + 0.4f * sin(now / 400f))
            if (pos != Offset.Unspecified) pill(pos, pillW, pillH, if (touching) 1f else 0.6f)
            sparks.forEach { s ->
                val age = ((now - s.born) / 1400f).coerceIn(0f, 1f)
                drawCircle(s.color.copy(alpha = 1f - age), radius = 4.dp.toPx() * (1f - age * 0.6f), center = Offset(s.x, s.y))
            }
        }
        if (hits >= 5) {
            Clawd(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 100.dp, height = 86.dp),
                mood = ClawdMood.Dance,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version · Funken", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (hits >= 5) "Fünfmal Funken! So fühlt sich Glimmer Drop an. ✨" else "Zieh die untere Insel an die obere · ${hits.coerceAtMost(5)}/5",
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

/** A Glimmer island: black, with Claude's colors glowing along its rim. */
private fun DrawScope.pill(center: Offset, w: Float, h: Float, glow: Float) {
    val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
    val r = CornerRadius(h / 2f)
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFF8E6BFF).copy(alpha = 0.35f * glow), Color.Transparent), center = center, radius = w),
        topLeft = Offset(center.x - w, center.y - w),
        size = Size(w * 2f, w * 2f),
        cornerRadius = CornerRadius(w),
    )
    drawRoundRect(Color.Black, topLeft = topLeft, size = Size(w, h), cornerRadius = r)
    drawRoundRect(
        Brush.linearGradient(AiFluidColors, start = topLeft, end = Offset(topLeft.x + w, topLeft.y + h)),
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = r,
        alpha = glow,
        style = Stroke(2.5.dp.toPx()),
    )
}
