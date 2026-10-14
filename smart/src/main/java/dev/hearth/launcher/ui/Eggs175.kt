package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs

/**
 * OMEGA UI 17.5's easter egg – the lantern. It's dark; Clawd holds up a lantern, and where
 * your finger goes its light goes. Five Ω are hidden in the dark: light each one up, and once
 * all five glow, the darkness gives way, Clawd gets his halo and the Ω crystal comes in.
 */
@Composable
internal fun LanternEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val settings = LocalSettings.current
    val palette = omegaPalette(settings)
    val measurer = rememberTextMeasurer()
    val glyph = remember(measurer) { measurer.measure("Ω", TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black)) }
    // Where the five Ω hide: spread over the middle of the screen, never under Clawd or the text.
    val spots = remember {
        val random = kotlin.random.Random(System.currentTimeMillis())
        val picked = mutableListOf<Offset>()
        var tries = 0
        while (picked.size < 5 && tries < 400) {
            tries++
            val p = Offset(0.14f + random.nextFloat() * 0.72f, 0.14f + random.nextFloat() * 0.5f)
            if (picked.none { (it - p).getDistance() < 0.2f }) picked += p
        }
        while (picked.size < 5) picked += Offset(0.2f + 0.15f * picked.size, 0.3f)
        picked.toList()
    }
    val found = remember { mutableStateListOf<Int>() }
    val done = found.size >= spots.size
    val dawn by animateFloatAsState(if (done) 1f else 0f, tween(2200), label = "dawn")
    val density = LocalDensity.current
    val reach = with(density) { 46.dp.toPx() }
    val lightRadius = with(density) { 120.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0812), Color(0xFF1A0E1E), Color(0xFF07060B)))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        var light by remember { mutableStateOf(Offset(w / 2f, h * 0.72f)) }
        // Light reaching a hidden Ω finds it.
        fun shine(at: Offset) {
            light = at
            spots.forEachIndexed { i, s ->
                if (i !in found && (Offset(s.x * w, s.y * h) - at).getDistance() < reach) found += i
            }
        }
        FluidBackdrop(palette, strength = 0.25f + 0.75f * dawn)
        Text(
            HearthUi.major(version),
            color = Color.White.copy(alpha = 0.05f + 0.05f * dawn),
            fontSize = 180.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        // The hidden Ω: faint gold in the dark, glowing once the lantern found them.
        Canvas(Modifier.fillMaxSize()) {
            spots.forEachIndexed { i, s ->
                val c = Offset(s.x * size.width, s.y * size.height)
                val lit = i in found
                if (lit) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFFFFD86B).copy(alpha = 0.5f), Color.Transparent), center = c, radius = reach * 1.4f),
                        radius = reach * 1.4f,
                        center = c,
                    )
                }
                drawText(
                    glyph,
                    color = if (lit) Color(0xFFFFEDB0) else palette[i % palette.size],
                    topLeft = Offset(c.x - glyph.size.width / 2f, c.y - glyph.size.height / 2f),
                    alpha = if (lit) 1f else 0.55f,
                )
            }
        }
        // The dark, with the lantern's light cut out of it (and gone at dawn).
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { shine(it) }) { change, _ -> shine(change.position) }
                }
                .pointerInput(Unit) { detectTapGestures { shine(it) } },
        ) {
            drawRect(Color(0xFF040308).copy(alpha = 0.97f * (1f - dawn)))
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Black,
                    0.55f to Color.Black.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    center = light,
                    radius = lightRadius,
                ),
                radius = lightRadius,
                center = light,
                blendMode = BlendMode.DstOut,
            )
            // A warm tint where the lantern shines.
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFC46B).copy(alpha = 0.18f * (1f - dawn)), Color.Transparent), center = light, radius = lightRadius),
                radius = lightRadius,
                center = light,
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 30.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Clawd with his lantern (his halo comes with the light).
            Box(contentAlignment = Alignment.TopEnd) {
                CompositionLocalProvider(LocalSettings provides settings.copy(clawdHalo = done)) {
                    Clawd(Modifier.size(width = 92.dp, height = 80.dp), mood = if (done) ClawdMood.Dance else ClawdMood.Wave)
                }
                Canvas(Modifier.size(22.dp)) {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8), Color(0xFFFFB347).copy(alpha = 0.6f), Color.Transparent)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("$name $version · Laterne", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done) {
                    "Alles erleuchtet! Clawd hat seinen Heiligenschein. ✨"
                } else {
                    "Wisch mit Clawds Laterne durchs Dunkel und finde die fünf versteckten Ω · ${found.size}/5"
                },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        OmegaFinale(done)
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
