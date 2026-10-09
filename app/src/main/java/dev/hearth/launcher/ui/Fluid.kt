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
import dev.hearth.launcher.data.liquidOmega
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
    // OMEGA Glass: OMEGA Fluid drifts behind every screen instead.
    @Suppress("NAME_SHADOWING")
    val liquid = LocalSettings.current.liquidOmega
    val colors = if (LocalSettings.current.omegaGlass || liquid) omegaPalette(LocalSettings.current).take(colors.size.coerceAtLeast(3)) else colors
    @Suppress("NAME_SHADOWING")
    // Liquid Glass: only a breath of light behind the glass, not colors.
    val strength = strength * (if (liquid) 0.6f else if (LocalSettings.current.omegaGlass) 1.4f else 1f) * (if (LocalSettings.current.omegaOverdrive) 1.35f else 1f)
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

/** OMEGA Fluid (OMEGA UI 17.1): ruby flowing through rose, amber and coral. */
val OmegaFluidColors = listOf(
    Color(0xFFC8102E),
    Color(0xFFFF6B7D),
    Color(0xFFFF9A3D),
    Color(0xFFE5243F),
    Color(0xFFFFC2CA),
)

/** OMEGA Fluid, calm: glass-white with a breath of ruby and amber. */
val OmegaCalmColors = listOf(
    Color(0xFFF6D9DD),
    Color(0xFFF8E2D6),
    Color(0xFFF3D3DA),
    Color(0xFFF9E6E0),
    Color(0xFFF4DCE3),
)

/** OMEGA Fluid in Galaxy Omega: Galaxy blue and sky with OMEGA's ruby and an icy light. */
val GalaxyOmegaFluidColors = listOf(
    Color(0xFF2F6BFF),
    Color(0xFFD0103A),
    Color(0xFF5CC8FF),
    Color(0xFFFF5C72),
    Color(0xFFCFE3FF),
)

/** The OMEGA Fluid colors for these settings (Zenith, Rubin or Galaxy Omega). */
internal fun omegaPalette(s: dev.hearth.launcher.data.LauncherSettings): List<Color> =
    when {
        // ZENITH 19: clear glass in ZENITH's light keeps a glint of sun and green.
        s.liquidOmega -> if (s.omegaColor == dev.hearth.launcher.data.OmegaColor.Zenith) ZenithLiquidColors else LiquidFluidColors
        else -> when (s.omegaColor) {
        dev.hearth.launcher.data.OmegaColor.Zenith -> if (s.zenithDaylight) zenithDaylightColors() else ZenithFluidColors
        dev.hearth.launcher.data.OmegaColor.GalaxyOmega -> GalaxyOmegaFluidColors
        dev.hearth.launcher.data.OmegaColor.Liquid -> LiquidFluidColors
        else -> OmegaFluidColors
        }
    }

/** OMEGA UI 18.3 "Liquid Glass": light caught in clear glass – white with a breath of rainbow. */
val LiquidFluidColors = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFDDEBFF),
    Color(0xFFF6E6FF),
    Color(0xFFDDF7F0),
    Color(0xFFFFF3E2),
)

/** The fluid colors in use: OMEGA Fluid with OMEGA Glass on, else Claude's. */
@Composable
internal fun fluidPalette(): List<Color> =
    LocalSettings.current.let { if (it.omegaGlass || it.liquidOmega) omegaPalette(it) else AiFluidColors }

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
    // ZENITH Metall: no colors running round – silver on top, green light along the foot.
    if (LocalGlassStyle.current.zenith) {
        @Suppress("NAME_SHADOWING")
        val strength = (strength * 1.6f).coerceIn(0.45f, 1.3f)
        return@composed this.drawWithContent {
            drawContent()
            drawZenithEdge(corner.toPx(), strength)
        }
    }
    // OMEGA UI 17.5, Ω-Overdrive: every rim flows, and brighter.
    val overdrive = LocalSettings.current.omegaOverdrive
    @Suppress("NAME_SHADOWING")
    val strength = if (overdrive) strength * 1.3f else strength
    val phase = flowPhase(4200, LocalSettings.current.animations && (flowing || overdrive))
    // Hearth UI 14 "Dezent": rims that stand still are calm glass with a hint of Claude's
    // colors; flowing ones (something running, something new) keep their full colors.
    val omega = LocalSettings.current.omegaGlass || LocalSettings.current.liquidOmega
    // OMEGA Glass: OMEGA Fluid shows everywhere in full color, never the calm white.
    val calm = !omega && phase == null && LocalSettings.current.fluidRims == FluidRims.Calm
    val colors = when {
        omega -> omegaPalette(LocalSettings.current)
        calm -> CalmFluidColors
        else -> AiFluidColors
    }
    val fade = if (calm) 0.7f else if (omega) 1.35f else 1f
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
    // ZENITH Metall: no colors drifting – green light rising from the foot, as in the logo.
    if (LocalGlassStyle.current.zenith) {
        return@composed this.drawWithContent {
            val at = Offset(size.width * 0.62f, size.height * 1.05f)
            val r = size.maxDimension * 0.75f
            drawCircle(Brush.radialGradient(listOf(ZenithGreen.copy(alpha = (0.18f * strength).coerceIn(0f, 0.4f)), Color.Transparent), center = at, radius = r), r, at)
            drawContent()
        }
    }
    val phase = flowPhase(14_000, LocalSettings.current.animations && (flowing || LocalSettings.current.omegaOverdrive))
    val palette = fluidPalette()
    val boost = if (LocalSettings.current.omegaGlass) 1.5f else 1f
    this.drawWithCache {
        fun blobs(t: Float) = palette.take(3).mapIndexed { i, c ->
            val p = i * 2.1f
            val at = Offset(size.width * (0.5f + 0.45f * cos(t + p)), size.height * (0.5f + 0.45f * sin(t * 0.8f + p)))
            at to Brush.radialGradient(listOf(c.copy(alpha = (0.16f * strength * boost).coerceAtMost(0.5f)), Color.Transparent), center = at, radius = size.maxDimension * 0.7f)
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
    val palette = fluidPalette()
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
                            palette[2].copy(alpha = 0.22f * (1f - p)),
                            palette[0].copy(alpha = 0.12f * (1f - p)),
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
                    Brush.sweepGradient(palette + palette.first(), center = w.at),
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
    // OMEGA UI 17.1 "Flüssig-Modus": slow color flows need no 120 frames a second – half the
    // work for every flowing rim, backdrop and glow, the rest of the screen stays at full speed.
    val smooth = LocalSettings.current.smoothMode
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var seen by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> seen = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(clock, smooth) {
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
                if (smooth) delay(28)
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
