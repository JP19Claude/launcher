package dev.hearth.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.hearth.launcher.data.FluidRims
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * One UI 10 "Fluid": touches answer like liquid – a soft wave of light spreads from the
 * finger and the row gives a little under it – and behind the screens colors drift slowly.
 * Switchable in Hearth's design settings ("One UI 10 Fluid").
 */

/**
 * A touch makes a soft wave of [color] spread from the finger, and the element yields a
 * little and springs back. Never takes the touch: clicks and swipes still work as before.
 */
fun Modifier.fluidTouch(color: Color = Color.White, yields: Boolean = true): Modifier = composed {
    if (!LocalSettings.current.fluidDesign || !LocalSettings.current.animations) return@composed this
    val scope = rememberCoroutineScope()
    val wave = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val give = remember { Animatable(1f) }
    var at by remember { mutableStateOf(Offset.Zero) }
    this
        // Only what gives under the finger needs a layer of its own.
        .then(
            if (yields) {
                Modifier.graphicsLayer {
                    scaleX = give.value
                    scaleY = give.value
                }
            } else {
                Modifier
            },
        )
        .pointerInput(yields) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                at = down.position
                scope.launch {
                    fade.snapTo(1f)
                    wave.snapTo(0f)
                    wave.animateTo(1f, tween(520))
                }
                if (yields) scope.launch { give.animateTo(0.975f, spring(stiffness = 900f)) }
                waitForUpOrCancellation()
                scope.launch { give.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                scope.launch { fade.animateTo(0f, tween(420)) }
            }
        }
        .drawWithContent {
            drawContent()
            val a = fade.value
            if (a > 0.01f) {
                val far = hypot(size.width, size.height)
                val r = (far * (0.15f + 0.85f * wave.value)).coerceAtLeast(1f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.22f * a), color.copy(alpha = 0.08f * a), Color.Transparent),
                        center = at,
                        radius = r,
                    ),
                    radius = r,
                    center = at,
                )
            }
        }
}

/**
 * Slowly drifting blobs of color behind a screen (One UI 10 Fluid): [colors] wander on their
 * own paths, soft and dim, so the content stays easy to read.
 */
@Composable
fun FluidBackdrop(colors: List<Color>, modifier: Modifier = Modifier, strength: Float = 1f) {
    if (!LocalSettings.current.fluidDesign) return
    val flow = flowPhase(24_000, LocalSettings.current.animations)
    Canvas(modifier.fillMaxSize()) {
        val t = flow?.invoke() ?: 1.2f
        colors.forEachIndexed { i, c ->
            val phase = i * 2.1f
            val cx = size.width * (0.5f + 0.38f * cos(t + phase))
            val cy = size.height * (0.32f + 0.26f * sin(t * 0.8f + phase * 1.3f) + 0.22f * i / colors.size.coerceAtLeast(1))
            val r = size.width * (0.55f + 0.12f * sin(t * 1.3f + phase))
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.26f * strength), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

/** Hearth UI 14 "Dezent": the AI Fluid colors, mostly glass-white with a hint of each. */
val CalmFluidColors = listOf(
    Color(0xFFF3DCD3),
    Color(0xFFF4E0EC),
    Color(0xFFE2DBF7),
    Color(0xFFD9E6F7),
    Color(0xFFF6E7DE),
)

/** The AI Fluid colors: Claude's terracotta flowing through violet, blue and pink. */
val AiFluidColors = listOf(
    Color(0xFFD97757),
    Color(0xFFFF6FB5),
    Color(0xFF8E6BFF),
    Color(0xFF3E91FF),
    Color(0xFFFFB494),
)

/**
 * AI Fluid: a gradient in Claude's colors flows round the edge of an element, with a soft
 * glow on its inner side – like the light around an AI at work. [strength] dims or brightens
 * it (above 1 for a moment when something new comes).
 */
fun Modifier.aiFluidEdge(
    corner: Dp,
    strength: Float = 1f,
    width: Dp = Dp(1.5f),
    enabled: Boolean = true,
    /** False: the colors stand still (most surfaces – flowing costs a frame every frame). */
    flowing: Boolean = true,
): Modifier = composed {
    if (!enabled || strength <= 0.01f) return@composed this
    val phase = flowPhase(4200, LocalSettings.current.animations && flowing)
    // Hearth UI 14 "Dezent": rims that stand still are calm glass with a hint of Claude's
    // colors; flowing ones (something running, something new) keep their full colors.
    val calm = phase == null && LocalSettings.current.fluidRims == FluidRims.Calm
    val colors = if (calm) CalmFluidColors else AiFluidColors
    val fade = if (calm) 0.7f else 1f
    // Sizes, strokes and (when still) the gradient are made once per size, not every frame.
    this.drawWithCache {
        val w = width.toPx()
        val r = corner.toPx().coerceAtMost(size.minDimension / 2f)
        val half = maxOf(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun brushAt(a: Float): Brush {
            val start = Offset(c.x + cos(a) * half, c.y + sin(a) * half)
            val end = Offset(2 * c.x - start.x, 2 * c.y - start.y)
            return Brush.linearGradient(colors, start, end, TileMode.Mirror)
        }
        val still = if (phase == null) brushAt(0.8f) else null
        val glowAt = Offset(w * 1.5f, w * 1.5f)
        val glowSize = Size(size.width - w * 3f, size.height - w * 3f)
        val glowCorner = CornerRadius((r - w * 1.5f).coerceAtLeast(0f))
        val glowStroke = Stroke(w * 3f)
        val glowAlpha = (0.16f * strength * fade).coerceIn(0f, 0.5f)
        val lineAt = Offset(w / 2f, w / 2f)
        val lineSize = Size(size.width - w, size.height - w)
        val lineCorner = CornerRadius((r - w / 2f).coerceAtLeast(0f))
        val lineStroke = Stroke(w)
        val lineAlpha = (0.85f * strength * fade).coerceIn(0f, 1f)
        onDrawWithContent {
            drawContent()
            val brush = still ?: brushAt(phase?.invoke() ?: 0.8f)
            // A soft glow inside the edge, then the line itself.
            drawRoundRect(brush, topLeft = glowAt, size = glowSize, cornerRadius = glowCorner, style = glowStroke, alpha = glowAlpha)
            drawRoundRect(brush, topLeft = lineAt, size = lineSize, cornerRadius = lineCorner, style = lineStroke, alpha = lineAlpha)
        }
    }
}

/**
 * Liquid glass without a wallpaper behind: a frosted fill, light caught along the top and a
 * rim that's bright above and fades below – so cards look like glass on any background.
 */
fun Modifier.glassSheen(corner: androidx.compose.ui.unit.Dp, tint: Color = Color.White): Modifier = this.drawWithContent {
    val r = androidx.compose.ui.geometry.CornerRadius(corner.toPx().coerceAtMost(size.minDimension / 2f))
    drawRoundRect(tint.copy(alpha = 0.055f), cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), endY = size.height * 0.45f),
        cornerRadius = r,
    )
    drawContent()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.12f))),
        cornerRadius = r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
    )
}

/**
 * Fluid glow: Claude's colors drift slowly inside an element, soft and dim, behind its
 * content – for widgets and cards that should feel alive.
 */
fun Modifier.fluidGlow(strength: Float = 1f, enabled: Boolean = true, flowing: Boolean = false): Modifier = composed {
    if (!enabled || !LocalSettings.current.fluidDesign) return@composed this
    val phase = flowPhase(14_000, LocalSettings.current.animations && flowing)
    this.drawWithCache {
        fun blobs(t: Float) = AiFluidColors.take(3).mapIndexed { i, c ->
            val p = i * 2.1f
            val at = Offset(size.width * (0.5f + 0.45f * cos(t + p)), size.height * (0.5f + 0.45f * sin(t * 0.8f + p)))
            at to Brush.radialGradient(listOf(c.copy(alpha = 0.16f * strength), Color.Transparent), center = at, radius = size.maxDimension * 0.7f)
        }
        // Standing still, the glow is painted from the same three brushes every time.
        val still = if (phase == null) blobs(1f) else null
        val r = size.maxDimension * 0.7f
        onDrawBehind {
            (still ?: blobs(phase?.invoke() ?: 1f)).forEach { (at, brush) -> drawCircle(brush, radius = r, center = at) }
        }
    }
}

/**
 * The whole fluid treatment for a widget: colors drifting inside, a flowing rim and a touch
 * that spreads like liquid light.
 */
fun Modifier.fluidWidget(corner: Dp): Modifier = composed {
    val on = LocalSettings.current.fluidDesign
    // Standing still: widgets sit on the home screen all the time, and colors moving on every
    // one of them kept the whole screen drawing – that was a good part of the lag.
    this
        .fluidGlow(enabled = on)
        .aiFluidEdge(corner, strength = 0.6f, enabled = on, flowing = false)
        .then(if (on) Modifier.fluidTouch(yields = false) else Modifier)
}

/** One fluid wave: where it starts, when, and whether it blooms up from the bottom. */
class FluidWave(val at: Offset, val start: Long, val bloom: Boolean)

/**
 * Fluid waves over a whole screen (One UI 10 Fluid): opening an app sends a ring of Claude's
 * colors out from its icon; coming home, a soft light blooms up from the bottom edge.
 */
class FluidWaves {
    val waves = androidx.compose.runtime.mutableStateListOf<FluidWave>()

    fun splash(at: Offset) {
        waves += FluidWave(at, android.os.SystemClock.uptimeMillis(), bloom = false)
    }

    fun bloom() {
        waves += FluidWave(Offset.Unspecified, android.os.SystemClock.uptimeMillis(), bloom = true)
    }
}

@Composable
fun rememberFluidWaves(): FluidWaves = remember { FluidWaves() }

/** Draws the waves (never takes a touch); nothing at all while there are none. */
@Composable
fun FluidWaveLayer(state: FluidWaves) {
    val on = LocalSettings.current.fluidDesign && LocalSettings.current.animations
    // A wave belongs to its moment: leaving the screen (an app opening) drops them all, so
    // none can wait half-drawn for the way back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.waves.clear()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!on) {
        if (state.waves.isNotEmpty()) SideEffect { state.waves.clear() }
        return
    }
    if (state.waves.isEmpty()) return
    var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        // Runs for as long as the layer is there (it leaves by itself once no wave is left).
        // The old loop stopped as soon as the list was empty – a wave added just before the
        // layer went then stood still forever: the colored dots in the icons and the orange
        // glow at the dock.
        while (true) {
            withFrameMillis { }
            now = android.os.SystemClock.uptimeMillis()
            state.waves.removeAll { now - it.start > (if (it.bloom) 620 else 560) }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val far = hypot(size.width, size.height)
        state.waves.forEach { w ->
            if (w.bloom) {
                val p = ((now - w.start) / 620f).coerceIn(0f, 1f)
                val c = Offset(size.width / 2f, size.height * 1.05f)
                val r = far * (0.2f + 0.8f * p)
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            AiFluidColors[2].copy(alpha = 0.22f * (1f - p)),
                            AiFluidColors[0].copy(alpha = 0.12f * (1f - p)),
                            Color.Transparent,
                        ),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            } else {
                val p = ((now - w.start) / 560f).coerceIn(0f, 1f)
                val ease = 1f - (1f - p) * (1f - p)
                val r = (far * ease).coerceAtLeast(1f)
                val fade = 1f - p
                // Just the ring in Claude's colors (a full-screen fill behind it cost the
                // opening app its first frames).
                drawCircle(
                    Brush.sweepGradient(AiFluidColors + AiFluidColors.first(), center = w.at),
                    radius = r,
                    center = w.at,
                    alpha = 0.45f * fade,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (18.dp.toPx() * fade).coerceAtLeast(1f)),
                )
            }
        }
    }
}


/**
 * One clock for every flowing effect, instead of an animation of their own each. It runs only
 * while something flows and the screen can be seen, and holds still while anything scrolls
 * or swipes – so the fluid never takes a frame away from the finger.
 */
@Stable
class FluidClock {
    /** Seconds of flow so far. */
    var seconds by mutableFloatStateOf(0f)
        internal set
    internal var flowing by mutableIntStateOf(0)
    @Volatile internal var calmUntil = 0L

    /** Hold still for [ms] (scrolling, swiping, opening something). */
    fun calm(ms: Long = 280) {
        calmUntil = maxOf(calmUntil, android.os.SystemClock.uptimeMillis() + ms)
    }
}

val LocalFluidClock = staticCompositionLocalOf<FluidClock?> { null }

/** Gives everything inside one [FluidClock] (part of HearthTheme, so every screen has it). */
@Composable
fun ProvideFluidClock(content: @Composable () -> Unit) {
    val clock = remember { FluidClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var seen by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> seen = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(clock) {
        snapshotFlow { seen && clock.flowing > 0 }.collectLatest { run ->
            if (!run) return@collectLatest
            var last = -1L
            while (true) {
                val wait = clock.calmUntil - android.os.SystemClock.uptimeMillis()
                if (wait > 0) {
                    delay(wait)
                    last = -1L
                    continue
                }
                withFrameMillis { now ->
                    if (last >= 0) clock.seconds = (clock.seconds + (now - last).coerceIn(0L, 50L) / 1000f) % 50_400f
                    last = now
                }
            }
        }
    }
    val calmOnScroll = remember(clock) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                clock.calm()
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                clock.calm(650)
                return Velocity.Zero
            }
        }
    }
    CompositionLocalProvider(LocalFluidClock provides clock) {
        Box(Modifier.nestedScroll(calmOnScroll), propagateMinConstraints = true) { content() }
    }
}

/**
 * The phase (0 to 2π) of a flow that takes [periodMs] a round, from the shared clock – or
 * null when it should stand still. Read it only while drawing, so it never recomposes.
 */
@Composable
internal fun flowPhase(periodMs: Int, moving: Boolean): (() -> Float)? {
    if (!moving) return null
    val clock = LocalFluidClock.current
    if (clock == null) {
        // No clock above (a window of its own): a little animation of its own.
        val t = rememberInfiniteTransition(label = "flow").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
        return { t.value }
    }
    DisposableEffect(clock) {
        clock.flowing++
        onDispose { clock.flowing-- }
    }
    return remember(clock, periodMs) {
        val perSecond = (2 * Math.PI * 1000.0 / periodMs).toFloat()
        val round = (2 * Math.PI).toFloat()
        val phase: () -> Float = { (clock.seconds * perSecond) % round }
        phase
    }
}
