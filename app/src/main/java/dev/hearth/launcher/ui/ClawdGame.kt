package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdGameScore
import dev.hearth.launcher.data.ClawdMood
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Clawd Jump: Clawd runs, bugs come towards him, a tap makes him jump. Hearts in the air give
 * extra points. It gets faster the longer it goes; the record stays on the phone.
 */

private enum class Phase { Ready, Running, Over }

/** Something coming towards Clawd: a bug to jump over, or a heart to catch. */
private class Thing(var x: Float, val w: Float, val h: Float, val heart: Boolean, var taken: Boolean = false)

@Composable
fun ClawdJumpGame(onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var best by remember { mutableIntStateOf(ClawdGameScore.best(context)) }
    var record by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF5CA9F0), Color(0xFFBFE3FB)))),
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val ground = hPx * 0.72f
        val clawdH = with(density) { 58.dp.toPx() }
        val clawdW = clawdH * 14f / 11f
        val clawdX = wPx * 0.12f
        val gravity = clawdH * 30f
        val jumpV = sqrt(2f * gravity * clawdH * 2.7f)

        var phase by remember { mutableStateOf(Phase.Ready) }
        var y by remember { mutableFloatStateOf(0f) }
        var vy by remember { mutableFloatStateOf(0f) }
        var distance by remember { mutableFloatStateOf(0f) }
        var bonus by remember { mutableIntStateOf(0) }
        var tick by remember { mutableLongStateOf(0L) }
        val things = remember { ArrayList<Thing>() }
        val score = (distance / clawdW).toInt() + bonus

        fun start() {
            things.clear()
            y = 0f
            vy = 0f
            distance = 0f
            bonus = 0
            record = false
            phase = Phase.Running
        }

        fun tap() {
            when (phase) {
                Phase.Ready, Phase.Over -> {
                    start()
                    vy = jumpV
                }
                Phase.Running -> if (y <= 1f) vy = jumpV
            }
        }

        LaunchedEffect(phase) {
            if (phase != Phase.Running) return@LaunchedEffect
            var last = -1L
            var spawnIn = 0.9f
            while (phase == Phase.Running) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                    last = now
                    // Faster the further he runs.
                    val pace = (1f + distance / (wPx * 30f)).coerceAtMost(2.2f)
                    val speed = wPx * 0.5f * pace
                    distance += speed * dt
                    // Jumping and falling.
                    vy -= gravity * dt
                    y = (y + vy * dt).coerceAtLeast(0f)
                    if (y == 0f && vy < 0f) vy = 0f
                    // New bugs and hearts from the right.
                    spawnIn -= dt
                    if (spawnIn <= 0f) {
                        val heart = Random.nextFloat() < 0.25f
                        things += if (heart) {
                            Thing(wPx + 20f, clawdH * 0.45f, clawdH * 0.45f, heart = true)
                        } else {
                            Thing(wPx + 20f, clawdH * (0.42f + Random.nextFloat() * 0.25f), clawdH * (0.45f + Random.nextFloat() * 0.4f), heart = false)
                        }
                        spawnIn = (0.85f + Random.nextFloat() * 0.9f) / pace.coerceAtMost(1.6f)
                    }
                    val iter = things.iterator()
                    while (iter.hasNext()) {
                        val t = iter.next()
                        t.x -= speed * dt
                        if (t.x + t.w < 0f) iter.remove()
                    }
                    // Did he touch something? (a little forgiving at the edges)
                    val left = clawdX + clawdW * 0.2f
                    val right = clawdX + clawdW * 0.8f
                    val bottom = ground - y
                    val top = bottom - clawdH * 0.7f
                    for (t in things) {
                        if (t.taken) continue
                        val tTop = if (t.heart) ground - clawdH * 2.1f else ground - t.h
                        val tBottom = if (t.heart) tTop + t.h else ground
                        val hit = t.x < right && t.x + t.w > left && tTop < bottom && tBottom > top
                        if (!hit) continue
                        if (t.heart) {
                            t.taken = true
                            bonus += 10
                        } else {
                            phase = Phase.Over
                            val total = (distance / clawdW).toInt() + bonus
                            if (ClawdGameScore.submit(context, total)) {
                                best = total
                                record = true
                            }
                        }
                    }
                    tick++
                }
            }
        }

        // The world: clouds, ground, bugs and hearts.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onPress = { tap() }) },
        ) {
            tick // redraw every frame
            drawWorld(ground, distance, clawdH)
            for (t in things) {
                if (t.taken) continue
                if (t.heart) drawHeart(t.x, ground - clawdH * 2.1f, t.w) else drawBug(t.x, ground, t.w, t.h)
            }
        }

        // Clawd himself.
        Clawd(
            Modifier
                .offset { IntOffset(clawdX.toInt(), (ground - y - clawdH * 0.958f).toInt()) }
                .size(with(density) { clawdW.toDp() }, with(density) { clawdH.toDp() }),
            mood = when (phase) {
                Phase.Ready -> ClawdMood.Wave
                Phase.Over -> ClawdMood.Flip
                Phase.Running -> if (y > 1f) ClawdMood.Dance else ClawdMood.Thinking
            },
        )

        // Score, record, close.
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                Text("$score", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Rekord $best", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            if (phase != Phase.Running) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (phase == Phase.Ready) "Clawd Jump" else if (record) "Neuer Rekord! 🎉" else "Autsch! 🐞",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (phase == Phase.Ready) {
                            "Tippen zum Springen.\nSpring über die Bugs, schnapp dir die Herzen."
                        } else {
                            "$score Punkte · Tippen für nochmal"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Clouds drifting by, a sun, and the ground scrolling under Clawd's feet. */
private fun DrawScope.drawWorld(ground: Float, distance: Float, unit: Float) {
    val px = unit / 11f
    // Sun.
    drawRect(Color(0xFFFFE07A), Offset(size.width * 0.78f, size.height * 0.1f), Size(px * 7, px * 7))
    // Clouds, slower than the ground.
    val cloud = Color.White.copy(alpha = 0.9f)
    listOf(0.15f to 0.2f, 0.6f to 0.32f, 0.95f to 0.14f).forEach { (start, yy) ->
        val span = size.width + px * 30
        val x = ((start * span - distance * 0.15f) % span + span) % span - px * 15
        drawRect(cloud, Offset(x, size.height * yy), Size(px * 14, px * 3))
        drawRect(cloud, Offset(x + px * 4, size.height * yy - px * 2), Size(px * 6, px * 2))
    }
    // Ground: grass, earth, and little marks that scroll.
    drawRect(Color(0xFF6CC070), Offset(0f, ground), Size(size.width, px * 3))
    drawRect(Color(0xFFB07A55), Offset(0f, ground + px * 3), Size(size.width, size.height - ground))
    val step = px * 9
    var x = -(distance % step)
    while (x < size.width) {
        drawRect(Color(0xFF8ED68E), Offset(x, ground - px), Size(px, px))
        drawRect(Color(0xFF94633F), Offset(x + px * 4, ground + px * 6), Size(px * 2, px))
        x += step
    }
}

/** A pixel bug: purple body, red eyes, little legs. */
private fun DrawScope.drawBug(x: Float, ground: Float, w: Float, h: Float) {
    val body = Color(0xFF6B4BA8)
    val dark = Color(0xFF4A3478)
    val legH = h * 0.15f
    drawRect(body, Offset(x, ground - h + h * 0.2f), Size(w, h * 0.65f))
    drawRect(dark, Offset(x + w * 0.1f, ground - h), Size(w * 0.8f, h * 0.25f))
    // Antennae.
    drawRect(dark, Offset(x + w * 0.2f, ground - h - h * 0.12f), Size(w * 0.1f, h * 0.14f))
    drawRect(dark, Offset(x + w * 0.7f, ground - h - h * 0.12f), Size(w * 0.1f, h * 0.14f))
    // Eyes.
    drawRect(Color(0xFFFF453A), Offset(x + w * 0.2f, ground - h + h * 0.3f), Size(w * 0.18f, h * 0.12f))
    drawRect(Color(0xFFFF453A), Offset(x + w * 0.62f, ground - h + h * 0.3f), Size(w * 0.18f, h * 0.12f))
    // Legs.
    for (i in 0 until 3) {
        drawRect(dark, Offset(x + w * (0.12f + i * 0.32f), ground - legH), Size(w * 0.12f, legH))
    }
}

/** A pixel heart to catch. */
private fun DrawScope.drawHeart(x: Float, y: Float, s: Float) {
    val c = Color(0xFFFF5A7A)
    val u = s / 4f
    drawRect(c, Offset(x, y), Size(u * 1.6f, u * 1.4f))
    drawRect(c, Offset(x + u * 2.4f, y), Size(u * 1.6f, u * 1.4f))
    drawRect(c, Offset(x, y + u), Size(u * 4f, u * 1.4f))
    drawRect(c, Offset(x + u * 0.8f, y + u * 2.2f), Size(u * 2.4f, u))
    drawRect(c, Offset(x + u * 1.5f, y + u * 3f), Size(u, u))
}
