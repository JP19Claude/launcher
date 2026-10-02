package dev.hearth.launcher.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import dev.hearth.launcher.data.GlimmerGlowColor
import dev.hearth.launcher.data.GlimmerMotion
import dev.hearth.launcher.data.GlimmerOutline
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import android.os.SystemClock
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.GlimmerStyle
import dev.hearth.launcher.data.LiveNotice
import dev.hearth.launcher.data.MediaRepository
import dev.hearth.launcher.data.NoticeKind
import dev.hearth.launcher.data.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import dev.hearth.launcher.data.GlimmerMusicStyle
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode

/** What Glimmer shows right now. */
@Immutable
sealed interface IslandContent {
    /** Nothing going on and the idle pill is off (or the phone is sideways). */
    data object Hidden : IslandContent

    /** Nothing going on: a small black pill around the camera. */
    data object Idle : IslandContent

    /** On the lock screen: the pill with a padlock, which opens when the phone unlocks. */
    data class Lock(val open: Boolean) : IslandContent

    data class Media(val playing: NowPlaying) : IslandContent

    data class Live(val notice: LiveNotice) : IslandContent

    data class Message(val notice: LiveNotice) : IslandContent

    /** The flashlight is on; tap (or hold) turns it off. */
    data object Torch : IslandContent

    /** A one-time code from a notification; a tap copies it (like vivo's and OPPO's islands). */
    data class Code(val code: String, val appLabel: String, val icon: ImageBitmap?, val copied: Boolean = false) : IslandContent

    /** A screenshot just taken, as a little picture (like vivo's Origin Island). */
    data class Screenshot(val uri: android.net.Uri, val thumb: ImageBitmap?, val id: Long) : IslandContent

    /** Charging, kept in the island while plugged in (if chosen). */
    data class Charging(val level: Int) : IslandContent

    /** A closed app just landed (HyperOS fly-in): its icon sits in the island for a moment. */
    data class Arrival(val icon: ImageBitmap?, val color: Color, val id: Int) : IslandContent

    /**
     * Face ID moment: scanning while the phone checks, a green tick once it's unlocked,
     * red and a shake when it didn't recognize you ([attempt] counts tries, so each one shakes).
     */
    data class Unlock(
        val success: Boolean,
        val face: Boolean,
        val failed: Boolean = false,
        val attempt: Int = 0,
    ) : IslandContent

    /** Short system moments: charging, silent mode, low battery. */
    data class Alert(
        val glyph: Glyph,
        val title: String,
        val value: String,
        val color: Color,
        val level: Float? = null,
    ) : IslandContent
}

private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Yellow = Color(0xFFFFD60A)
private val RainbowColors = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF453A),
)

/** Height of the pill when not expanded: as tall as the status bar row, like the camera ring. */
const val ISLAND_HEIGHT_DP = 30f

private const val UNLOCK_SIZE_DP = 92f

/** True on the always-on display: everything holds still (no frames while the phone dozes). */
private val LocalGlimmerStill = compositionLocalOf { false }

/** Space between the pill and the second activity's bubble. */
private val SecondaryGap = 6.dp

/** Size of the secondary bubble next to the pill, plus the gap. */
private val SecondaryExtra = 36.dp

/**
 * Size of the pill for a content and state; the window around it follows this.
 * Compact stays narrow (about 45 % of the screen), so it fits between the clock and
 * notification icons on the left and the status icons on the right.
 */
fun islandSize(
    content: IslandContent,
    expanded: Boolean,
    screenWidthDp: Float,
    hasSecondary: Boolean,
    widthScale: Float = 1f,
    /** The iPhone's proportions: its pill is 3.4 times, a compact activity about 6.2 times as wide as tall. */
    dynamicIsland: Boolean = false,
    /** Unfolded content starts below the camera, so the card grows a little taller for it. */
    clearCamera: Boolean = false,
): DpSize {
    val full = if (dynamicIsland) (screenWidthDp - 22f).coerceAtMost(440f).dp else min(screenWidthDp - 16f, 420f).dp
    val compact = if (dynamicIsland) {
        (ISLAND_HEIGHT_DP * 6.2f).coerceAtMost(screenWidthDp - 60f).dp
    } else {
        ((screenWidthDp * 0.44f).coerceIn(150f, 190f) * widthScale).coerceAtMost(screenWidthDp - 60f).dp
    }
    val idle = if (dynamicIsland) ISLAND_HEIGHT_DP * 3.375f else (screenWidthDp * 0.27f).coerceIn(86f, 110f) * widthScale
    return when {
        content is IslandContent.Hidden -> DpSize(0.dp, 0.dp)
        content is IslandContent.Idle -> DpSize(idle.dp, ISLAND_HEIGHT_DP.dp)
        // A little wider than the idle pill, for the padlock on its left.
        content is IslandContent.Lock -> DpSize(idle.dp + 26.dp, ISLAND_HEIGHT_DP.dp)
        // Like Face ID on the iPhone: the island becomes a rounded square for the moment.
        content is IslandContent.Unlock -> DpSize(UNLOCK_SIZE_DP.dp, UNLOCK_SIZE_DP.dp)
        !expanded -> DpSize(compact, ISLAND_HEIGHT_DP.dp)
        else -> DpSize(
            full,
            (if (clearCamera) 16.dp else 0.dp) + when (content) {
                is IslandContent.Media -> 178.dp
                is IslandContent.Live -> if (content.notice.actions.isNotEmpty()) 158.dp else 116.dp
                is IslandContent.Message -> if (content.notice.actions.isNotEmpty()) 160.dp else 124.dp
                is IslandContent.Charging -> 96.dp
                is IslandContent.Code -> 112.dp
                is IslandContent.Screenshot -> 132.dp
                else -> 96.dp
            },
        )
    }.let { if (hasSecondary && !expanded && !isPassive(content)) DpSize(it.width + SecondaryExtra, it.height) else it }
}

/** Sends a notification's intent; Android 14+ wants the sender to vouch for opening a screen. */
fun sendIntent(context: Context, intent: PendingIntent?) {
    if (intent == null) return
    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    } else {
        null
    }
    runCatching { intent.send(context, 0, null, null, null, null, options) }
        .onFailure { runCatching { intent.send() } }
}

/**
 * Glimmer: Hearth's island around the front camera. It morphs between an idle pill,
 * a compact live activity and an expanded card, with a springy, liquid motion.
 */
@Composable
fun GlimmerIsland(
    content: IslandContent,
    secondary: IslandContent?,
    expanded: Boolean,
    style: GlimmerStyle,
    screenWidthDp: Float,
    topInset: Dp,
    media: MediaRepository,
    /** Tap opens the app (iPhone), holding unfolds; otherwise the other way round. */
    tapOpens: Boolean = false,
    /** Counts up for a hop and shimmer from outside (an app flying in). */
    pulse: Int = 0,
    /** Shimmer in the activity's color when something new shows up. */
    glow: Boolean = true,
    onToggle: () -> Unit,
    onExpand: () -> Unit = onToggle,
    /** Sideways swipe: true for the next activity, false for the previous one. */
    onSwap: (Boolean) -> Unit = {},
    /** "Share" in the unfolded island (screenshots). */
    onShare: (IslandContent) -> Unit = {},
    /** How many activities there are, for the little page dots when unfolded (Now Bar). */
    activityCount: Int = 1,
    onFocus: (IslandContent) -> Unit = {},
    onCollapse: () -> Unit,
    onOpen: (IslandContent) -> Unit,
    onTargetSize: (DpSize) -> Unit,
    /** Always-on display: dimmed and without motion. */
    dimmed: Boolean = false,
    /** Double tap, if something is set for it (otherwise taps aren't held back to wait for one). */
    onDoubleTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val systemHaptics = LocalHapticFeedback.current
    // Vibration can be switched off in the settings.
    val haptics = remember(systemHaptics, settings.glimmerHaptics) {
        object {
            fun performHapticFeedback(type: HapticFeedbackType) {
                if (settings.glimmerHaptics) systemHaptics.performHapticFeedback(type)
            }
        }
    }
    val hasSecondary = secondary != null && !expanded
    val target = islandSize(
        content,
        expanded,
        screenWidthDp,
        hasSecondary,
        settings.glimmerWidth,
        settings.glimmerIsDynamicIsland,
        clearCamera = settings.glimmerSmall,
    )
    LaunchedEffect(target) { onTargetSize(target) }
    val collapseAfter = settings.glimmerAutoCollapse
    // Keyed on which activity it is, not on its every update (a timer changes each second).
    LaunchedEffect(expanded, islandKey(content), collapseAfter) {
        if (expanded && collapseAfter > 0) {
            delay(collapseAfter * 1000L)
            onCollapse()
        }
    }
    // How springy everything is: calmer settles without bouncing, playful wobbles more.
    val springiness = when (settings.glimmerMotion) {
        GlimmerMotion.Calm -> 0.22f
        GlimmerMotion.Normal -> 0f
        GlimmerMotion.Playful -> -0.2f
    }
    fun damp(ratio: Float) = (ratio + springiness).coerceIn(0.25f, 1f)

    // Like the Dynamic Island: unfolding stretches out sideways first and then drops down with
    // a little bounce; folding pulls up first and is quicker; going away has no bounce at all.
    val hidden = content is IslandContent.Hidden
    val widthSpec = when {
        hidden -> spring<Dp>(dampingRatio = 1f, stiffness = 520f)
        expanded -> spring(dampingRatio = damp(0.68f), stiffness = 330f)
        else -> spring(dampingRatio = damp(0.78f), stiffness = 380f)
    }
    val heightSpec = when {
        hidden -> spring<Dp>(dampingRatio = 1f, stiffness = 520f)
        expanded -> spring(dampingRatio = damp(0.72f), stiffness = 240f)
        else -> spring(dampingRatio = damp(0.86f), stiffness = 520f)
    }
    val mainWidth = if (hasSecondary) target.width - SecondaryExtra else target.width
    val width = animateDpAsState(mainWidth, widthSpec, label = "islandWidth").value.coerceAtLeast(0.dp)
    val height = animateDpAsState(target.height, heightSpec, label = "islandHeight").value.coerceAtLeast(0.dp)
    // The corners follow the shape without jumping: never rounder than a pill, at most as
    // round as the iPhone's unfolded island (44) or the Face ID square (30).
    val cornerCap by animateDpAsState(
        when {
            expanded -> if (settings.glimmerIsDynamicIsland) 42.dp else 44.dp
            content is IslandContent.Unlock -> 30.dp
            else -> 60.dp
        },
        tween(320, easing = FastOutSlowInEasing),
        label = "islandCorner",
    )
    val corner = minOf(height / 2, cornerCap)
    // What is drawn while the island shrinks away: the last thing it showed.
    val lastShown = remember { arrayOf<IslandContent>(IslandContent.Hidden) }
    if (!hidden) lastShown[0] = content
    val drawn = if (hidden) lastShown[0] else content
    // Pressing it: it swells a little under the finger, like the iPhone's.
    val press = remember { Animatable(1f) }
    val pressScope = rememberCoroutineScope()

    // Something new arrives: a little hop and a shimmer in its color, like a drop landing.
    val animations = LocalSettings.current.animations
    val hop = remember { Animatable(1f) }
    // An app flowing in: wider and flatter for a moment, then wobbling back like a drop.
    val squash = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    val key = islandKey(content)
    val lastPulse = remember { intArrayOf(pulse) }
    LaunchedEffect(key, pulse) {
        if (!animations || dimmed || content is IslandContent.Hidden || content is IslandContent.Unlock) return@LaunchedEffect
        val pulsed = pulse != lastPulse[0]
        lastPulse[0] = pulse
        if ((content is IslandContent.Idle || content is IslandContent.Lock) && !pulsed) return@LaunchedEffect
        launch {
            if (pulsed) {
                squash.snapTo(0f)
                squash.animateTo(1f, tween(110))
                squash.animateTo(0f, spring(dampingRatio = damp(0.3f), stiffness = 380f))
            } else {
                // Like the Dynamic Island: a quick squeeze, then it springs open past its size.
                hop.snapTo(1f)
                hop.animateTo(0.93f, tween(90))
                hop.animateTo(1f, spring(dampingRatio = damp(0.42f), stiffness = 420f))
            }
        }
        if (glow) {
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, tween(260))
            shimmer.animateTo(0f, tween(1400))
        }
    }
    val glowColor = when (settings.glimmerGlowColor) {
        GlimmerGlowColor.Activity, GlimmerGlowColor.Rainbow -> glowColorOf(content, settings.accent.color)
        GlimmerGlowColor.Accent -> settings.accent.color
        GlimmerGlowColor.White -> Color.White
    }
    val rainbow = settings.glimmerGlowColor == GlimmerGlowColor.Rainbow
    // The tinted look follows the activity's color, softly.
    val islandTint by androidx.compose.animation.animateColorAsState(
        if (style == GlimmerStyle.Tinted) glowColorOf(drawn, settings.accent.color) else Color.Black,
        tween(500),
        label = "islandTint",
    )
    // Only animates when chosen and something is running, so it costs nothing otherwise.
    val breath = if (settings.glimmerBreathe && animations && !dimmed && !isPassive(drawn) && !hidden) {
        rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        null
    }
    // Plugging in: a streak of green light runs once around the island, like current flowing in.
    val chargeSweep = remember { Animatable(0f) }
    val chargeKey = (content as? IslandContent.Alert)?.takeIf { isChargingAlert(it) }?.let { islandKey(it) }
    LaunchedEffect(chargeKey) {
        if (chargeKey == null || !animations || dimmed) {
            chargeSweep.snapTo(0f)
            return@LaunchedEffect
        }
        chargeSweep.snapTo(0.001f)
        chargeSweep.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        chargeSweep.snapTo(0f)
    }
    // Swiping over music: the content leans the way the track goes.
    val nudge = remember { Animatable(0f) }
    val nudgeScope = rememberCoroutineScope()
    // Face or finger not recognized: the whole island shakes its head, like the iPhone's.
    val denied = remember { Animatable(0f) }
    val deniedKey = (content as? IslandContent.Unlock)?.takeIf { it.failed }?.attempt
    LaunchedEffect(deniedKey) {
        if (deniedKey == null || dimmed) {
            denied.snapTo(0f)
            return@LaunchedEffect
        }
        denied.animateTo(0f, keyframes {
            durationMillis = 560
            -1f at 55
            1f at 140
            -0.85f at 225
            0.6f at 310
            -0.35f at 395
            0.15f at 475
        })
    }
    // How sharp the content is: from a blur to clear whenever it changes or unfolds.
    val clarity = remember { Animatable(1f) }
    LaunchedEffect(key, expanded) {
        if (!animations || dimmed || isPassive(content)) {
            clarity.snapTo(1f)
            return@LaunchedEffect
        }
        clarity.snapTo(0f)
        clarity.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }
    val tap: () -> Unit = {
        if (!isPassive(content)) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // A code is copied with one tap, without unfolding first.
            if ((tapOpens || content is IslandContent.Code) && !expanded) onOpen(content) else onToggle()
        }
    }
    val hold: () -> Unit = {
        if (!isPassive(content)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (tapOpens && !expanded) onExpand() else onOpen(content)
        }
    }
    // Gestures read the latest state: restarting them whenever the content updates (a timer
    // every second, music as it plays) would cut taps and swipes off halfway.
    val tapNow by rememberUpdatedState(tap)
    val holdNow by rememberUpdatedState(hold)
    val contentNow by rememberUpdatedState(content)
    val expandedNow by rememberUpdatedState(expanded)
    val secondaryNow by rememberUpdatedState(secondary)
    val doubleTapNow by rememberUpdatedState(onDoubleTap)
    val swipeTracksNow by rememberUpdatedState(settings.glimmerSwipeTracks)
    val pressableNow by rememberUpdatedState(!dimmed && !isPassive(content) && animations)

    // A second activity splits off like a drop, as on the iPhone: the little bubble slides out
    // from under the pill, joined to it by a liquid neck that thins and lets go.
    val detach = remember { Animatable(0f) }
    val secondaryKey = secondary?.let { islandKey(it) }
    LaunchedEffect(hasSecondary, secondaryKey) {
        if (hasSecondary) {
            if (!animations || dimmed) {
                detach.snapTo(1f)
            } else {
                detach.snapTo(0f)
                detach.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
            }
        }
    }

    CompositionLocalProvider(LocalGlimmerStill provides dimmed) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Gone once it has shrunk away (it shrinks into the camera instead of vanishing).
        if (drawn is IslandContent.Hidden || (hidden && width < 1.dp)) return@Box
        Row(
            Modifier
                .padding(top = topInset)
                .graphicsLayer {
                    // Always-on display: softer, so it doesn't glare or burn in.
                    val fade = if (hidden) (width.value / 40f).coerceIn(0f, 1f) else 1f
                    alpha = (if (dimmed) 0.62f else 1f) * fade
                    val s = (if (expanded) 1f else hop.value) * press.value
                    val q = if (expanded) 0f else squash.value
                    scaleX = s * (1f + 0.14f * q)
                    scaleY = s * (1f - 0.12f * q)
                    translationX = denied.value * 7.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            verticalAlignment = Alignment.Top,
        ) {
            IslandShape(
                style = style,
                corner = corner,
                tint = islandTint,
                modifier = Modifier
                    .drawBehind {
                        // Breathing light around the island while something runs (Fluid Cloud).
                        val b = breath?.value ?: 0f
                        if (b > 0.01f && !expanded) {
                            for (i in 1..3) {
                                val spread = i * 2.2.dp.toPx() * (0.6f + 0.4f * b)
                                drawRoundRect(
                                    color = glowColor.copy(alpha = b * 0.22f / i),
                                    topLeft = Offset(-spread, -spread),
                                    size = Size(size.width + spread * 2, size.height + spread * 2),
                                    cornerRadius = CornerRadius(size.height / 2 + spread),
                                )
                            }
                        }
                        val a = shimmer.value
                        if (a > 0.01f && !expanded) {
                            // Soft rings growing outwards, fading: the "glimmer".
                            for (i in 1..4) {
                                val spread = i * 1.6.dp.toPx()
                                if (rainbow) {
                                    drawRoundRect(
                                        brush = Brush.sweepGradient(RainbowColors, center = center),
                                        topLeft = Offset(-spread, -spread),
                                        size = Size(size.width + spread * 2, size.height + spread * 2),
                                        cornerRadius = CornerRadius(size.height / 2 + spread),
                                        alpha = a * 0.4f / i,
                                    )
                                } else {
                                    drawRoundRect(
                                        color = glowColor.copy(alpha = a * 0.32f / i),
                                        topLeft = Offset(-spread, -spread),
                                        size = Size(size.width + spread * 2, size.height + spread * 2),
                                        cornerRadius = CornerRadius(size.height / 2 + spread),
                                    )
                                }
                            }
                        }
                    }
                    .size(width, height)
                    .then(
                        when (settings.glimmerOutline) {
                            GlimmerOutline.Off -> Modifier
                            GlimmerOutline.Subtle -> Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(corner))
                            GlimmerOutline.Colored -> Modifier.border(
                                1.5.dp,
                                if (rainbow) Brush.sweepGradient(RainbowColors) else SolidColor(glowColorOf(drawn, settings.accent.color).copy(alpha = 0.75f)),
                                RoundedCornerShape(corner),
                            )
                        },
                    )
                    .drawWithContent {
                        drawContent()
                        val p = chargeSweep.value
                        if (p > 0f) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val turn = p * 360f - 90f
                            val streak = object : ShaderBrush() {
                                override fun createShader(size: Size) = android.graphics.SweepGradient(
                                    cx,
                                    cy,
                                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, Green.toArgb(), android.graphics.Color.WHITE),
                                    floatArrayOf(0f, 0.72f, 0.95f, 1f),
                                ).apply { setLocalMatrix(android.graphics.Matrix().apply { setRotate(turn, cx, cy) }) }
                            }
                            // Fades in at the start and out at the end of its round.
                            val fade = (minOf(p, 1f - p) * 6f).coerceIn(0f, 1f)
                            drawRoundRect(
                                brush = streak,
                                cornerRadius = CornerRadius(corner.toPx()),
                                style = Stroke(2.dp.toPx()),
                                alpha = fade,
                            )
                        }
                    }
                    // Only whether a double tap is set restarts this (it changes how taps are told apart).
                    .pointerInput(onDoubleTap != null) {
                        detectTapGestures(
                            onPress = {
                                if (pressableNow) {
                                    pressScope.launch { press.animateTo(if (expandedNow) 1.015f else 1.06f, spring(dampingRatio = 0.7f, stiffness = 700f)) }
                                    tryAwaitRelease()
                                    pressScope.launch { press.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f)) }
                                }
                            },
                            onTap = { tapNow() },
                            onDoubleTap = if (onDoubleTap != null) {
                                { _: Offset ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    doubleTapNow?.invoke()
                                }
                            } else {
                                null
                            },
                            onLongPress = { holdNow() },
                        )
                    }
                    // Pull down to unfold, push up to fold, sideways to switch activities.
                    .pointerInput(Unit) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDragEnd = {
                                val threshold = 18.dp.toPx()
                                val vertical = abs(total.y) > abs(total.x)
                                val content = contentNow
                                val expanded = expandedNow
                                when {
                                    isPassive(content) -> Unit
                                    vertical && total.y > threshold && !expanded -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onExpand()
                                    }
                                    vertical && total.y < -threshold && expanded -> onCollapse()
                                    // Music alone in the island: sideways skips the track.
                                    !vertical && abs(total.x) > threshold * 2 && !expanded &&
                                        content is IslandContent.Media && secondaryNow == null && swipeTracksNow -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val forward = total.x < 0
                                        if (forward) media.next() else media.previous()
                                        nudgeScope.launch {
                                            nudge.snapTo(if (forward) -1f else 1f)
                                            nudge.animateTo(0f, spring(dampingRatio = damp(0.45f), stiffness = 380f))
                                        }
                                    }
                                    !vertical && abs(total.x) > threshold * 2 && !expanded -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSwap(total.x < 0)
                                    }
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            total += amount
                        }
                    },
            ) {
                AnimatedContent(
                    // New content surfaces out of a soft blur, as on the iPhone (Android 12+).
                    modifier = Modifier.graphicsLayer {
                        translationX = nudge.value * 14.dp.toPx()
                        val r = (1f - clarity.value) * 9.dp.toPx()
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) {
                            BlurEffect(r, r, TileMode.Decal)
                        } else {
                            null
                        }
                    },
                    targetState = drawn to expanded,
                    transitionSpec = {
                        val top = TransformOrigin(0.5f, 0f)
                        when {
                            // Unfolding: the small content melts away, the big one grows in from
                            // the top while the shape is still stretching.
                            targetState.second && !initialState.second ->
                                (fadeIn(tween(240, delayMillis = 110)) +
                                    scaleIn(spring(dampingRatio = 0.78f, stiffness = 300f), initialScale = 0.82f, transformOrigin = top))
                                    .togetherWith(fadeOut(tween(90)) + scaleOut(tween(140), targetScale = 1.12f))
                            // Folding: the big content sinks back into the camera, quickly.
                            !targetState.second && initialState.second ->
                                (fadeIn(tween(200, delayMillis = 150)) + scaleIn(tween(240, delayMillis = 120), initialScale = 0.9f))
                                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(200), targetScale = 0.8f, transformOrigin = top))
                            // Something else takes over the pill: a quick cross-fade with a little rise.
                            else ->
                                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.9f))
                                    .togetherWith(fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.94f))
                        }
                    },
                    contentKey = { (c, e) -> islandKey(c) to e },
                    label = "islandContent",
                ) { (c, e) ->
                    if (e) {
                        Box(Modifier.fillMaxSize()) {
                            ExpandedContent(
                                c,
                                media,
                                onOpen = { onOpen(c) },
                                onAction = { sendIntent(context, it); onCollapse() },
                                onShare = { onShare(c) },
                                clearCamera = settings.glimmerSmall,
                            )
                            // Several things running: little dots like Samsung's Now Bar; swipe for the next.
                            if (activityCount > 1) {
                                Row(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    repeat(activityCount.coerceAtMost(6)) { i ->
                                        Box(
                                            Modifier
                                                .size(width = if (i == 0) 12.dp else 5.dp, height = 5.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = if (i == 0) 0.9f else 0.35f)),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        CompactContent(c)
                    }
                }
            }
            if (hasSecondary && secondary != null) {
                Spacer(Modifier.width(SecondaryGap))
                IslandShape(
                    style = style,
                    corner = 15.dp,
                    tint = if (style == GlimmerStyle.Tinted) glowColorOf(secondary, settings.accent.color) else Color.Black,
                    modifier = Modifier
                        .graphicsLayer {
                            val d = detach.value
                            translationX = -(1f - d) * (ISLAND_HEIGHT_DP.dp + SecondaryGap).toPx()
                            val grow = 0.55f + 0.45f * d.coerceAtMost(1f)
                            scaleX = grow
                            scaleY = grow
                        }
                        .drawBehind {
                            // The neck back to the pill, thinning as the bubble pulls away.
                            if (style == GlimmerStyle.Black) {
                                val d = detach.value
                                val neck = size.height * 0.8f * (1f - ((d - 0.3f) / 0.6f).coerceIn(0f, 1f))
                                if (neck > 0.5f) {
                                    val reach = SecondaryGap.toPx() + size.height * 0.4f
                                    drawRoundRect(
                                        Color.Black,
                                        topLeft = Offset(-reach, (size.height - neck) / 2f),
                                        size = Size(reach + size.width / 2f, neck),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(neck / 2f),
                                    )
                                }
                            }
                        }
                        .size(ISLAND_HEIGHT_DP.dp)
                        // Tap: bring it to the front and unfold it; hold: open its app.
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    secondaryNow?.let { item ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onFocus(item)
                                    }
                                },
                                onLongPress = { secondaryNow?.let { onOpen(it) } },
                            )
                        },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LeadingBadge(secondary, 18.dp) }
                }
            }
        }
    }
    }
}

/** The color Glimmer shimmers in for an activity. */
private fun glowColorOf(content: IslandContent, accent: Color): Color = when (content) {
    is IslandContent.Alert -> content.color
    is IslandContent.Message -> accent
    is IslandContent.Media -> content.playing.artPalette.firstOrNull() ?: content.playing.artColor ?: Color.White
    is IslandContent.Live -> noticeColor(content.notice.kind)
    is IslandContent.Charging -> Green
    is IslandContent.Code -> Green
    is IslandContent.Screenshot -> Color.White
    is IslandContent.Arrival -> content.color
    is IslandContent.Unlock -> if (content.failed) Red else Color.White
    IslandContent.Torch -> Yellow
    else -> Color.White
}

/** Things that only sit there: no tapping, pulling or pressing. */
private fun isPassive(content: IslandContent): Boolean =
    content is IslandContent.Idle || content is IslandContent.Hidden ||
        content is IslandContent.Unlock || content is IslandContent.Lock || content is IslandContent.Arrival

internal fun islandKey(content: IslandContent): String = when (content) {
    is IslandContent.Media -> "media"
    is IslandContent.Live -> "live-${content.notice.key}"
    is IslandContent.Message -> "msg-${content.notice.key}"
    is IslandContent.Alert -> "alert-${content.title}"
    IslandContent.Idle -> "idle"
    // One key for closed and open, so the padlock opens in place.
    is IslandContent.Lock -> "lock"
    is IslandContent.Charging -> "charging"
    is IslandContent.Code -> "code-${content.code}"
    is IslandContent.Screenshot -> "screenshot-${content.id}"
    is IslandContent.Arrival -> "arrival-${content.id}"
    IslandContent.Hidden -> "hidden"
    // One key for scanning, done and failed, so the scan turns into the tick in place.
    is IslandContent.Unlock -> "unlock"
    IslandContent.Torch -> "torch"
}

@Composable
private fun IslandShape(
    style: GlimmerStyle,
    corner: Dp,
    modifier: Modifier,
    tint: Color = Color.Black,
    content: @Composable () -> Unit,
) {
    when (style) {
        // Dark, with the activity's color glowing through from the top (vivo, OPPO).
        GlimmerStyle.Tinted -> Box(
            modifier
                .clip(RoundedCornerShape(corner))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.42f),
                            androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.16f),
                        ),
                    ),
                ),
        ) { content() }
        GlimmerStyle.Black -> Box(
            modifier
                .clip(RoundedCornerShape(corner))
                .background(Color.Black),
        ) { content() }
        GlimmerStyle.Glass -> LiquidGlass(
            cornerRadius = corner,
            refraction = 14.dp,
            tint = Color.Black.copy(alpha = 0.45f),
            modifier = modifier.clip(RoundedCornerShape(corner)),
        ) { content() }
    }
}

/** Live time for calls and timers, updated every half second. */
@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return now
}

private fun formatDuration(ms: Long): String {
    val total = abs(ms) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun noticeColor(kind: NoticeKind): Color = when (kind) {
    NoticeKind.Call -> Green
    NoticeKind.Timer -> Orange
    NoticeKind.Recording -> Red
    NoticeKind.Alarm -> Orange
    NoticeKind.Hotspot -> Green
    else -> Color.White
}

/** Times with equally wide digits, so a running clock doesn't wobble (like the iPhone's). */
private val TimeStyle = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/** Answer in green, hang up / decline / stop in red, the rest in glass. */
private fun actionColor(notice: LiveNotice, title: String): Color {
    val t = title.lowercase()
    return when {
        AcceptWords.any { it in t } -> Green
        notice.kind == NoticeKind.Call && EndWords.any { it in t } -> Red
        notice.kind == NoticeKind.Alarm && EndWords.any { it in t } -> Orange
        else -> Color.White.copy(alpha = 0.16f)
    }
}

private val AcceptWords = listOf("annehmen", "accept", "answer", "abheben", "rangehen")
private val EndWords = listOf("ablehnen", "auflegen", "beenden", "decline", "hang up", "end call", "stopp", "stop", "dismiss", "schließen")

/** Stopwatch with tenths, like the iPhone's: "1:23,4". */
private fun formatStopwatch(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d,%d".format(m, sec, (ms % 1000) / 100)
}

/** The time a live activity shows right now, or null if it has none. */
private fun liveTimeText(notice: LiveNotice, now: Long): String? {
    val ms = notice.timeMs(now) ?: return null
    return if (notice.stopwatch) formatStopwatch(ms) else formatDuration(ms)
}

/** Ticks fast enough for a running stopwatch's tenths, otherwise twice a second. */
@Composable
private fun rememberLiveNow(notice: LiveNotice): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val fast = notice.stopwatch && !notice.paused
    val still = LocalGlimmerStill.current
    LaunchedEffect(fast, notice.paused, still) {
        while (true) {
            now = System.currentTimeMillis()
            if (notice.paused) break
            delay(if (still) 1000 else if (fast) 100 else 500)
        }
    }
    return now
}

/** Small picture on the left: cover, call or timer symbol, or the app's icon. */
@Composable
private fun LeadingBadge(content: IslandContent, size: Dp) {
    // Each new activity's icon pops in with a little overshoot, like on the iPhone.
    val still = LocalGlimmerStill.current
    val pop = remember(islandKey(content)) { Animatable(if (still) 1f else 0.4f) }
    LaunchedEffect(islandKey(content)) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
    Box(
        Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = ((pop.value - 0.4f) / 0.5f).coerceIn(0f, 1f)
        },
    ) { LeadingBadgeIcon(content, size) }
}

@Composable
private fun LeadingBadgeIcon(content: IslandContent, size: Dp) {
    when (content) {
        is IslandContent.Media -> {
            val art = content.playing.art
            // The iPhone shows the cover as a small rounded square, Glimmer as a circle.
            val artShape = if (LocalSettings.current.glimmerIsDynamicIsland) RoundedCornerShape(size * 0.24f) else CircleShape
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(artShape))
            } else {
                GlyphIcon(Glyph.Speaker, Color.White, Modifier.size(size))
            }
        }
        is IslandContent.Live -> when (content.notice.kind) {
            NoticeKind.Call -> Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(size))
            NoticeKind.Timer, NoticeKind.Alarm -> GlyphIcon(Glyph.Alarm, Orange, Modifier.size(size))
            NoticeKind.Recording -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { PulseDot(Red) }
            NoticeKind.Hotspot -> GlyphIcon(Glyph.Hotspot, Green, Modifier.size(size))
            else -> AppBadge(content.notice, size)
        }
        is IslandContent.Message -> AppBadge(content.notice, size)
        is IslandContent.Alert -> AlertGlyph(content, size)
        IslandContent.Torch -> TorchGlyph(size)
        is IslandContent.Charging -> BoltGlyph(Green, size, charging = true)
        is IslandContent.Code -> GlyphIcon(Glyph.Lock, Green, Modifier.size(size))
        is IslandContent.Screenshot -> {
            val thumb = content.thumb
            if (thumb != null) {
                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)))
            } else {
                GlyphIcon(Glyph.Screenshot, Color.White, Modifier.size(size))
            }
        }
        is IslandContent.Arrival -> {
            val icon = content.icon
            if (icon != null) {
                Image(icon, null, contentScale = ContentScale.Fit, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
            } else {
                Box(Modifier.size(size).clip(CircleShape).background(content.color))
            }
        }
        else -> Unit
    }
}

@Composable
private fun AppBadge(notice: LiveNotice, size: Dp) {
    val icon = notice.icon
    if (icon != null) {
        Image(icon, null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        GlyphIcon(Glyph.Bell, Color.White, Modifier.size(size))
    }
}

/**
 * Compact, like on the iPhone: something small on the left, the camera in the middle,
 * something small on the right. No text that would crowd the status bar.
 */
@Composable
private fun CompactContent(content: IslandContent) {
    if (content is IslandContent.Idle || content is IslandContent.Hidden) return
    if (content is IslandContent.Lock) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadlockGlyph(content.open, Modifier.size(width = 13.dp, height = 17.dp))
        }
        return
    }
    if (content is IslandContent.Unlock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UnlockGlyph(content, Modifier.size(50.dp))
        }
        return
    }
    if (content is IslandContent.Media && LocalSettings.current.glimmerMusicStyle == GlimmerMusicStyle.Hyper) {
        HyperCompact(content.playing)
        return
    }
    if (content is IslandContent.Media && LocalSettings.current.glimmerMusicStyle == GlimmerMusicStyle.FluidCloud) {
        FluidCloudCompact(content.playing)
        return
    }
    // A call in progress, like on the iPhone: its time in green on the left, the voice as a
    // green wave on the right.
    if (content is IslandContent.Live && content.notice.kind == NoticeKind.Call && content.notice.hasTime) {
        val now = rememberLiveNow(content.notice)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            RollingText(liveTimeText(content.notice, now) ?: "", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            HyperWave(listOf(Green, Color(0xFF7CF29A)), playing = true, modifier = Modifier.size(26.dp, 14.dp))
        }
        return
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingBadge(content, 20.dp)
        Spacer(Modifier.weight(1f))
        when (content) {
            is IslandContent.Media ->
                if (content.playing.playing) {
                    Equalizer(content.playing.artColor ?: Color.White, true, Modifier.size(20.dp, 14.dp))
                } else {
                    // Paused: a play sign, the island waits to be tapped.
                    GlyphIcon(Glyph.Play, Color.White.copy(alpha = 0.85f), Modifier.size(16.dp))
                }
            is IslandContent.Live -> {
                val notice = content.notice
                when (notice.kind) {
                    NoticeKind.Call, NoticeKind.Timer, NoticeKind.Recording -> {
                        val now = rememberLiveNow(notice)
                        val time = liveTimeText(notice, now)
                        if (time != null) {
                            RollingText(
                                time,
                                // On hold: the time stands, a little dimmer.
                                color = noticeColor(notice.kind).copy(alpha = if (notice.paused) 0.55f else 1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                down = countsDown(notice),
                            )
                        } else {
                            PulseDot(noticeColor(notice.kind))
                        }
                    }
                    NoticeKind.Progress -> ProgressRing(notice, Modifier.size(18.dp))
                    // Ringing alarm: its name (the time) in orange.
                    NoticeKind.Alarm -> Text(
                        notice.title.ifBlank { "Wecker" },
                        color = Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 90.dp),
                    )
                    // Hotspot: how many are connected.
                    NoticeKind.Hotspot -> Text(
                        notice.shortText ?: "An",
                        color = Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    // Android 16 live updates: the app's own short text, else its title.
                    NoticeKind.Update -> Text(
                        notice.shortText ?: notice.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TimeStyle,
                        modifier = Modifier.widthIn(max = 90.dp),
                    )
                    // Navigation: the next distance ("200 m").
                    NoticeKind.Navigation -> Text(
                        notice.title.ifBlank { notice.text },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 90.dp),
                    )
                    else -> PulseDot(Color.White)
                }
            }
            is IslandContent.Message -> PulseDot(LocalSettings.current.accent.color)
            IslandContent.Torch -> Text("An", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            is IslandContent.Charging -> Row(verticalAlignment = Alignment.CenterVertically) {
                RollingText("${content.level} %", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
                LevelBar(content.level / 100f, Green, Modifier.size(24.dp, 12.dp), charging = true)
            }
            // The code itself; after the tap a green "Kopiert".
            is IslandContent.Code -> if (content.copied) {
                Text("Kopiert", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(content.code, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = TimeStyle, letterSpacing = 1.sp)
            }
            is IslandContent.Screenshot -> GlyphIcon(Glyph.Screenshot, Color.White.copy(alpha = 0.85f), Modifier.size(16.dp))
            // Just landed: a soft light in the app's color.
            is IslandContent.Arrival -> PulseDot(content.color)
            is IslandContent.Alert -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    content.value,
                    color = content.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp),
                )
                // Charging and battery: a small battery filling up, like on the iPhone.
                content.level?.let { level ->
                    Spacer(Modifier.width(5.dp))
                    LevelBar(level, content.color, Modifier.size(24.dp, 12.dp), charging = isChargingAlert(content))
                }
            }
            else -> Unit
        }
        Spacer(Modifier.width(2.dp))
    }
}

/** A small dot that breathes: something new is waiting. */
@Composable
private fun PulseDot(color: Color) {
    if (LocalGlimmerStill.current) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val scale by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotScale")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ExpandedContent(
    content: IslandContent,
    media: MediaRepository,
    onOpen: () -> Unit,
    onAction: (PendingIntent) -> Unit,
    onShare: () -> Unit = {},
    /** Start below the camera, so nothing sits behind it. */
    clearCamera: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = if (clearCamera) 30.dp else 14.dp, bottom = 14.dp),
    ) {
        when (content) {
            is IslandContent.Media -> if (LocalSettings.current.glimmerMusicStyle == GlimmerMusicStyle.Hyper) {
                HyperExpanded(content.playing, media, onOpen)
            } else {
                val p = content.playing
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                    val art = p.art
                    if (art != null) {
                        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)))
                    } else {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title.ifBlank { p.appLabel }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.artist.ifBlank { p.appLabel }, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Equalizer(p.artColor ?: Color.White, p.playing, Modifier.size(28.dp, 22.dp))
                }
                Spacer(Modifier.weight(1f))
                if (p.durationMs > 0) {
                    // Ticks every half second, so the progress moves along.
                    val tick = rememberNowMillis()
                    val position = remember(tick, p) { p.currentPosition() }
                    val fraction = (position.toFloat() / p.durationMs).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        SeekBar(
                            fraction = fraction,
                            onSeek = { f -> media.seekTo((f * p.durationMs).toLong()) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("-" + formatDuration(p.durationMs - position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IslandButton(Glyph.Previous) { media.previous() }
                    IslandButton(if (p.playing) Glyph.Pause else Glyph.Play, big = true) { media.playPause() }
                    IslandButton(Glyph.Next) { media.next() }
                }
            }
            is IslandContent.Live, is IslandContent.Message -> {
                val notice = if (content is IslandContent.Live) content.notice else (content as IslandContent.Message).notice
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                    LeadingBadge(content, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(notice.title.ifBlank { notice.appLabel }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (notice.text.isNotBlank()) {
                            Text(notice.text, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (notice.kind == NoticeKind.Call || notice.kind == NoticeKind.Timer || notice.kind == NoticeKind.Recording) {
                        val now = rememberLiveNow(notice)
                        liveTimeText(notice, now)?.let { time ->
                            RollingText(
                                time,
                                color = noticeColor(notice.kind).copy(alpha = if (notice.paused) 0.6f else 1f),
                                // Big like the iPhone's timer, digits that don't wobble.
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Medium,
                                down = countsDown(notice),
                            )
                        }
                    } else if (notice.kind == NoticeKind.Progress) {
                        ProgressRing(notice, Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (notice.actions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        notice.actions.forEach { action ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(actionColor(notice, action.title))
                                    .clickable { onAction(action.intent) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(action.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
            is IslandContent.Alert -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(content.glyph, content.color, Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(content.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(content.value, color = content.color, fontSize = 15.sp)
                }
                content.level?.let { LevelBar(it, content.color, Modifier.size(52.dp, 24.dp), charging = isChargingAlert(content)) }
            }
            is IslandContent.Code -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Lock, Green, Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Code · ${content.appLabel}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(content.code, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, style = TimeStyle, letterSpacing = 3.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (content.copied) Color.White.copy(alpha = 0.16f) else Green)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(if (content.copied) "Kopiert" else "Kopieren", color = if (content.copied) Green else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            is IslandContent.Screenshot -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val thumb = content.thumb
                Box(
                    Modifier
                        .size(width = 62.dp, height = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onOpen),
                ) {
                    if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bildschirmfoto", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Gespeichert", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Teilen" to onShare, "Ansehen" to onOpen).forEach { (label, action) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = if (label == "Ansehen") 0.9f else 0.16f))
                                    .clickable(onClick = action)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(label, color = if (label == "Ansehen") Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            is IslandContent.Charging -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Spark, Green, Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lädt", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    RollingText("${content.level} %", color = Green, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                LevelBar(content.level / 100f, Green, Modifier.size(52.dp, 24.dp), charging = true)
            }
            IslandContent.Torch -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Torch, Yellow, Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Text("Taschenlampe", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Yellow)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Ausschalten", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> Unit
        }
    }
}

/** Progress you can drag or tap to jump; grows a little under the finger, like on iOS. */
@Composable
private fun SeekBar(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = Color.White) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }
    val shown = if (dragging) dragFraction else fraction
    val thickness by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "seekThickness")
    Box(
        modifier
            .height(22.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragFraction)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    dragFraction = (dragFraction + amount / size.width).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(RoundedCornerShape(thickness / 2))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(color))
        }
    }
}

/** The cover's colors for the Hyper Island look (at least two). */
private fun paletteOf(p: NowPlaying): List<Color> {
    val colors = p.artPalette.ifEmpty { listOfNotNull(p.artColor) }.ifEmpty { listOf(Color.White) }
    return if (colors.size == 1) colors + colors.first() else colors
}

/**
 * Hyper Island, compact: the cover on the left, colored waves on the right, and the
 * cover's colors glowing softly through the black from both ends.
 */
@Composable
private fun HyperCompact(p: NowPlaying) {
    val colors = paletteOf(p)
    val still = LocalGlimmerStill.current
    val breath = if (still) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "hyperGlow")
        transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath").value
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val a = if (p.playing) breath else 0.45f
                drawRect(
                    Brush.horizontalGradient(
                        0f to colors.first().copy(alpha = 0.34f * a),
                        0.38f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to colors.last().copy(alpha = 0.30f * a),
                    ),
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 5.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = p.art
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(21.dp).clip(RoundedCornerShape(7.dp)))
            } else {
                Box(
                    Modifier
                        .size(21.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.linearGradient(colors)),
                )
            }
            Spacer(Modifier.weight(1f))
            if (p.playing) {
                HyperWave(colors, playing = true, modifier = Modifier.size(30.dp, 15.dp))
            } else {
                GlyphIcon(Glyph.Play, colors.first(), Modifier.size(15.dp))
            }
        }
    }
}

/** Seven slim bars rising and falling like a sound wave, painted across the cover's colors. */
@Composable
private fun HyperWave(colors: List<Color>, playing: Boolean, modifier: Modifier) {
    if (LocalGlimmerStill.current) {
        StillBars(colors, 7, modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "hyperWave")
    val phase by transition.animateFloat(
        0f,
        (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val bars = 7
        val gap = size.width * 0.07f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.horizontalGradient(colors)
        for (i in 0 until bars) {
            val level = if (playing) {
                val a = abs(sin(phase * (1f + i * 0.17f) + i * 0.9f))
                val b = 0.6f + 0.4f * sin(phase * 0.5f + i * 1.3f)
                (0.22f + 0.78f * a * b).coerceIn(0.18f, 1f)
            } else {
                0.2f
            }
            val h = size.height * level
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Hyper Island, unfolded: the cover's colors drift as soft light behind everything,
 * a big cover with a glow, title and artist, a progress line in the cover's color to
 * drag, and the controls.
 */
@Composable
private fun HyperExpanded(p: NowPlaying, media: MediaRepository, onOpen: () -> Unit) {
    val colors = paletteOf(p)
    val transition = rememberInfiniteTransition(label = "hyperAura")
    val drift by transition.animateFloat(
        0f,
        (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "drift",
    )
    Box(Modifier.fillMaxSize()) {
        // Drifting color light, like the album art spilling into the island.
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val strength = if (p.playing) 0.5f else 0.3f
            listOf(
                Offset(w * (0.18f + 0.08f * sin(drift)), h * (0.3f + 0.2f * cos(drift))) to colors[0],
                Offset(w * (0.82f + 0.08f * cos(drift * 1.3f)), h * (0.7f + 0.2f * sin(drift * 0.8f))) to colors[1 % colors.size],
                Offset(w * (0.5f + 0.25f * sin(drift * 0.7f)), h * (0.15f + 0.1f * cos(drift * 1.1f))) to colors[2 % colors.size],
            ).forEach { (center, color) ->
                drawCircle(
                    Brush.radialGradient(
                        0f to color.copy(alpha = strength),
                        1f to Color.Transparent,
                        center = center,
                        radius = w * 0.55f,
                    ),
                    radius = w * 0.55f,
                    center = center,
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                val art = p.art
                Box(
                    Modifier
                        .size(60.dp)
                        .drawBehind {
                            // Soft glow of the cover under it.
                            drawCircle(
                                Brush.radialGradient(
                                    0f to colors.first().copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = size.minDimension * 0.9f,
                                ),
                                radius = size.minDimension * 0.9f,
                            )
                        },
                ) {
                    if (art != null) {
                        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                    } else {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors)))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title.ifBlank { p.appLabel }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.artist.ifBlank { p.appLabel }, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HyperWave(colors, p.playing, Modifier.size(34.dp, 20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (p.durationMs > 0) {
                val tick = rememberNowMillis()
                val position = remember(tick, p) { p.currentPosition() }
                val fraction = (position.toFloat() / p.durationMs).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(position), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    SeekBar(
                        fraction = fraction,
                        onSeek = { f -> media.seekTo((f * p.durationMs).toLong()) },
                        modifier = Modifier.weight(1f),
                        color = colors.first(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("-" + formatDuration(p.durationMs - position), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IslandButton(Glyph.Previous) { media.previous() }
                IslandButton(if (p.playing) Glyph.Pause else Glyph.Play, big = true) { media.playPause() }
                IslandButton(Glyph.Next) { media.next() }
            }
        }
    }
}

@Composable
private fun IslandButton(glyph: Glyph, big: Boolean = false, onClick: () -> Unit) {
    // Gives under the finger and springs back, like the iPhone's island buttons.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        if (pressed) 0.84f else 1f,
        spring(dampingRatio = if (pressed) 0.8f else 0.4f, stiffness = 600f),
        label = "islandButton",
    )
    Box(
        Modifier
            .size(if (big) 52.dp else 44.dp)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = (if (big) 0.16f else 0f) + if (pressed) 0.12f else 0f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Color.White, Modifier.size(if (big) 26.dp else 22.dp))
    }
}

/**
 * Face ID (or the fingerprint) like on the iPhone: the frame and face breathe and look around
 * while the phone checks, then everything folds into a tick once it's unlocked. If it
 * didn't recognize you, the whole symbol turns red while the island shakes, like the iPhone.
 */
@Composable
private fun UnlockGlyph(content: IslandContent.Unlock, modifier: Modifier) {
    val done = remember { Animatable(if (content.success) 1f else 0f) }
    LaunchedEffect(content.success) {
        if (content.success) done.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) else done.snapTo(0f)
    }
    val still = LocalGlimmerStill.current
    // Red at once, the moment the try fails, fading back only if the island stays.
    val miss = remember { Animatable(if (content.failed) 1f else 0f) }
    LaunchedEffect(content.failed, content.attempt) {
        if (content.failed) miss.animateTo(1f, tween(120)) else miss.animateTo(0f, tween(240))
    }
    val transition = rememberInfiniteTransition(label = "unlockScan")
    val breath by transition.animateFloat(0.93f, 1.03f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "breath")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "scan")
    Canvas(modifier) {
        val d = done.value
        val m = miss.value
        val w = size.minDimension
        val stroke = w * 0.07f
        val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val checking = if (still || m > 0f) 1f else breath
        // The frame: four rounded corners, pulling in a little once done.
        scale(checking * (1f - 0.14f * d), center) {
            val inset = w * 0.06f
            val len = w * 0.24f
            val r = w * 0.12f
            val corner = Path().apply {
                moveTo(inset, inset + len)
                lineTo(inset, inset + r)
                quadraticBezierTo(inset, inset, inset + r, inset)
                lineTo(inset + len, inset)
            }
            // Unlocked: the frame turns green with the tick.
            // Not recognized: it flushes red while it shakes.
            val frameColor = androidx.compose.ui.graphics.lerp(
                androidx.compose.ui.graphics.lerp(Color.White, Red, m),
                Green,
                d,
            ).copy(alpha = 1f - 0.35f * d)
            for ((sx, sy) in listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)) {
                scale(sx, sy, center) { drawPath(corner, frameColor, style = lineStyle) }
            }
        }
        val features = 1f - ((d - 0f) / 0.45f).coerceIn(0f, 1f)
        if (features > 0f) {
            val look = if (still || m > 0f) 0f else sin(scan * 2f * Math.PI.toFloat()) * w * 0.035f
            if (content.face) {
                translate(left = look) {
                    val c = androidx.compose.ui.graphics.lerp(Color.White, Red, m).copy(alpha = features)
                    // Eyes, nose, smile.
                    drawLine(c, Offset(w * 0.36f, w * 0.37f), Offset(w * 0.36f, w * 0.46f), stroke, StrokeCap.Round)
                    drawLine(c, Offset(w * 0.64f, w * 0.37f), Offset(w * 0.64f, w * 0.46f), stroke, StrokeCap.Round)
                    val nose = Path().apply {
                        moveTo(w * 0.52f, w * 0.37f)
                        lineTo(w * 0.52f, w * 0.56f)
                        lineTo(w * 0.46f, w * 0.56f)
                    }
                    drawPath(nose, c, style = lineStyle)
                    drawArc(
                        c,
                        startAngle = 25f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(w * 0.33f, w * 0.44f),
                        size = Size(w * 0.34f, w * 0.26f),
                        style = lineStyle,
                    )
                }
            } else {
                // Fingerprint ridges, lit up one after another while checking.
                val c = Offset(w * 0.5f, w * 0.56f)
                for (i in 0 until 4) {
                    val radius = w * (0.1f + i * 0.075f)
                    val lit = if (still) 1f else ((scan * 4f - i).coerceIn(0f, 1f))
                    drawArc(
                        androidx.compose.ui.graphics.lerp(Color.White, Red, m)
                            .copy(alpha = features * (0.35f + 0.65f * (if (m > 0f) 1f else lit))),
                        startAngle = 200f - i * 6f,
                        sweepAngle = 220f + i * 12f,
                        useCenter = false,
                        topLeft = Offset(c.x - radius, c.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = lineStyle,
                    )
                }
            }
        }
        // The tick, drawn on as the face fades.
        val tickProgress = ((d - 0.3f) / 0.7f).coerceIn(0f, 1f)
        if (tickProgress > 0f) {
            val tick = Path().apply {
                moveTo(w * 0.30f, w * 0.52f)
                lineTo(w * 0.45f, w * 0.66f)
                lineTo(w * 0.72f, w * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(tick, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * tickProgress, part, true)
            // The green tick, like Face ID's.
            drawPath(part, Green, style = Stroke(width = stroke * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Timers count down, everything else (calls, recordings, stopwatches) counts up. */
private fun countsDown(notice: LiveNotice): Boolean = notice.countDown || (notice.kind == NoticeKind.Timer && !notice.stopwatch)

/**
 * Numbers that roll like the iPhone's: a digit that changes slides out and the new one slides
 * in after it (up when counting up, down when counting down). Still on the always-on display.
 */
@Composable
private fun RollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    down: Boolean = false,
) {
    if (LocalGlimmerStill.current || !LocalSettings.current.animations) {
        Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, style = TimeStyle, modifier = modifier)
        return
    }
    Row(modifier) {
        val count = text.length
        text.forEachIndexed { i, ch ->
            // Counted from the right, so "9:59" to "10:00" keeps the seconds in place.
            key(count - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (initialState.isDigit() && targetState.isDigit()) {
                            val sign = if (down) -1 else 1
                            (slideInVertically(tween(240, easing = FastOutSlowInEasing)) { sign * it } + fadeIn(tween(180)))
                                .togetherWith(slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -sign * it } + fadeOut(tween(140)))
                                .using(SizeTransform(clip = true))
                        } else {
                            fadeIn(tween(150)).togetherWith(fadeOut(tween(120)))
                        }
                    },
                    label = "rollingDigit",
                ) { c ->
                    Text(c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, style = TimeStyle)
                }
            }
        }
    }
}

/**
 * The lock screen's padlock in the pill: closed while locked; on unlocking the shackle springs
 * up and swings open, like the iPhone's lock in the Dynamic Island.
 */
@Composable
private fun PadlockGlyph(open: Boolean, modifier: Modifier) {
    val still = LocalGlimmerStill.current
    val o = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        if (still) o.snapTo(if (open) 1f else 0f)
        else o.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 380f))
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.16f
        val bodyTop = h * 0.46f
        // The body.
        drawRoundRect(
            Color.White,
            topLeft = Offset(0f, bodyTop),
            size = Size(w, h - bodyTop),
            cornerRadius = CornerRadius(w * 0.2f),
        )
        // The shackle: lifts up and tips open around its left leg.
        val lift = o.value * h * 0.16f
        val swing = 0f
        val left = w * 0.22f
        val right = w * 0.78f
        val r = (right - left) / 2f
        val top = h * 0.08f - lift
        val shackle = Path().apply {
            moveTo(left + swing, bodyTop + stroke * 0.3f - lift * 0.6f)
            lineTo(left + swing, top + r)
            arcTo(
                androidx.compose.ui.geometry.Rect(left + swing, top, right + swing, top + 2 * r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            // Open: the right leg comes out of the body.
            lineTo(right + swing, (bodyTop + stroke * 0.3f) - lift - o.value * h * 0.12f)
        }
        rotate(degrees = -14f * o.value, pivot = Offset(left, bodyTop)) {
            drawPath(shackle, Color.White, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * Music like OPPO's and OnePlus' Fluid Cloud: the cover spins like a record while it plays,
 * ringed by how far the song has got, and the time left counts down on the right.
 */
@Composable
private fun FluidCloudCompact(p: NowPlaying) {
    val still = LocalGlimmerStill.current
    val now by produceState(SystemClock.elapsedRealtime(), p.playing, p.positionMs) {
        while (p.playing && !still) {
            value = SystemClock.elapsedRealtime()
            delay(1000)
        }
        value = SystemClock.elapsedRealtime()
    }
    val position = p.currentPosition(now)
    val fraction = if (p.durationMs > 0) (position.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
    val ringColor = p.artPalette.firstOrNull() ?: p.artColor ?: Color.White
    // The record turns while playing and stays put when paused.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(p.playing, still) {
        if (p.playing && !still) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(8000, easing = LinearEasing))
            }
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(stroke))
                drawArc(ringColor, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val art = p.art
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = spin.value % 360f }
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2E)),
            ) {
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.weight(1f))
        if (p.durationMs > 0) {
            RollingText(
                "-" + formatDuration(p.durationMs - position),
                color = Color.White.copy(alpha = if (p.playing) 0.9f else 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                down = true,
            )
        } else {
            Equalizer(ringColor, p.playing, Modifier.size(20.dp, 14.dp))
        }
    }
}

/** Plugging in: the charging alert in green (not "battery low", which uses the same bolt). */
private fun isChargingAlert(content: IslandContent.Alert): Boolean =
    content.glyph == Glyph.Spark && content.color == Green

/**
 * Each little system moment gets its own motion: the bolt zaps in, the moon rises, the plane
 * flies through, headphones pop with a ring, the bell rings, a full battery sparkles.
 */
@Composable
private fun AlertGlyph(content: IslandContent.Alert, size: Dp) {
    val still = LocalGlimmerStill.current || !LocalSettings.current.animations
    if (content.glyph == Glyph.Spark) {
        BoltGlyph(content.color, size, charging = isChargingAlert(content))
        return
    }
    val rotate = remember(content) { Animatable(0f) }
    val shiftX = remember(content) { Animatable(0f) }
    val shiftY = remember(content) { Animatable(0f) }
    val grow = remember(content) { Animatable(if (still) 1f else 0.6f) }
    val ring = remember(content) { Animatable(0f) }
    LaunchedEffect(content, still) {
        if (still) {
            grow.snapTo(1f)
            return@LaunchedEffect
        }
        when (content.glyph) {
            // Silent and vibrate: the bell wobbles, like the iPhone's ring switch.
            Glyph.Bell, Glyph.Vibrate -> {
                grow.snapTo(1f)
                rotate.animateTo(0f, keyframes {
                    durationMillis = 720
                    18f at 70
                    -16f at 170
                    13f at 270
                    -10f at 370
                    6f at 470
                    -3f at 570
                })
            }
            // Do not disturb: the moon rises and settles with a little turn.
            Glyph.Moon -> {
                grow.snapTo(1f)
                shiftY.snapTo(1f)
                rotate.snapTo(-50f)
                launch { shiftY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f)) }
                rotate.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 180f))
            }
            // Airplane mode: the plane flies in from the left and lands in place.
            Glyph.Airplane -> {
                grow.snapTo(1f)
                shiftX.snapTo(-1.6f)
                rotate.snapTo(-20f)
                launch { shiftX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 160f)) }
                rotate.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 200f))
            }
            // Headphones: a pop, and a ring spreading out like they just found the phone.
            Glyph.Headphones -> {
                launch { grow.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f)) }
                ring.snapTo(0f)
                ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            // Fully charged: a double sparkle.
            Glyph.Battery -> {
                grow.animateTo(1.18f, tween(140))
                grow.animateTo(0.95f, tween(120))
                grow.animateTo(1.12f, tween(120))
                grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
            }
            else -> grow.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (ring.value in 0.01f..0.99f) {
            Canvas(Modifier.size(size)) {
                val rr = this.size.minDimension / 2f * (0.7f + ring.value * 0.9f)
                drawCircle(content.color.copy(alpha = (1f - ring.value) * 0.6f), rr, style = Stroke(1.5.dp.toPx()))
            }
        }
        GlyphIcon(
            content.glyph,
            content.color,
            Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = rotate.value
                    translationX = shiftX.value * size.toPx()
                    translationY = shiftY.value * size.toPx()
                    scaleX = grow.value
                    scaleY = grow.value
                    alpha = (1f - kotlin.math.abs(shiftX.value) / 1.6f).coerceIn(0.2f, 1f)
                },
        )
    }
}

/** The charging bolt: zaps in big with a flash, then pulses softly while it charges. */
@Composable
private fun BoltGlyph(color: Color, size: Dp, charging: Boolean) {
    val still = LocalGlimmerStill.current || !LocalSettings.current.animations
    val zap = remember { Animatable(if (still) 1f else 0f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(600))
        }
        zap.animateTo(1.35f, tween(150))
        zap.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
    }
    val pulse = if (charging && !still) {
        rememberInfiniteTransition(label = "bolt").animateFloat(
            0.75f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "boltPulse",
        )
    } else {
        null
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            // A flash of light behind the bolt as the power comes in.
            val f = flash.value
            if (f > 0.01f) drawCircle(color.copy(alpha = f * 0.45f), this.size.minDimension * (0.35f + 0.4f * (1f - f)))
        }
        GlyphIcon(
            Glyph.Spark,
            color,
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = zap.value
                    scaleY = zap.value
                    alpha = pulse?.value ?: 1f
                },
        )
    }
}

/** The flashlight: a soft beam of light breathing in front of it. */
@Composable
private fun TorchGlyph(size: Dp) {
    val still = LocalGlimmerStill.current || !LocalSettings.current.animations
    val beam = if (!still) {
        rememberInfiniteTransition(label = "torch").animateFloat(
            0.35f, 0.8f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beam",
        )
    } else {
        null
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val b = beam?.value ?: 0.5f
            drawCircle(
                Brush.radialGradient(listOf(Yellow.copy(alpha = b * 0.55f), Color.Transparent), center = center, radius = this.size.minDimension * 0.75f),
                radius = this.size.minDimension * 0.75f,
            )
        }
        GlyphIcon(Glyph.Torch, Yellow, Modifier.size(size))
    }
}

/** Bars standing still at gentle heights (always-on display). */
@Composable
private fun StillBars(colors: List<Color>, count: Int, modifier: Modifier) {
    Canvas(modifier) {
        val gap = size.width * 0.08f
        val barWidth = (size.width - gap * (count - 1)) / count
        val brush = Brush.horizontalGradient(if (colors.size > 1) colors else colors + colors)
        for (i in 0 until count) {
            val level = 0.35f + 0.4f * ((i * 37) % 10) / 10f
            val h = size.height * level
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Four bars dancing to the music (still while paused), in the cover's color. */
@Composable
private fun Equalizer(color: Color, playing: Boolean, modifier: Modifier) {
    if (LocalGlimmerStill.current) {
        StillBars(listOf(if (color.luminanceIsDark()) Color.White else color), 4, modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = listOf(380, 520, 300, 450).mapIndexed { i, duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration, delayMillis = i * 40), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    val tint = if (color.luminanceIsDark()) Color.White else color
    Canvas(modifier) {
        val gap = size.width * 0.12f
        val barWidth = (size.width - gap * 3) / 4
        bars.forEachIndexed { i, bar ->
            val level = if (playing) bar.value else 0.25f
            val h = size.height * level
            drawRoundRect(
                color = tint,
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.3f

@Composable
private fun ProgressRing(notice: LiveNotice, modifier: Modifier) {
    val fraction = if (notice.progressMax > 0) notice.progress.toFloat() / notice.progressMax else 0f
    val spin = rememberInfiniteTransition(label = "progressSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100)), label = "spinAngle")
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (notice.indeterminate || notice.progressMax <= 0) {
            drawArc(Color.White, angle, 90f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            drawArc(Color.White, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun LevelBar(level: Float, color: Color, modifier: Modifier, charging: Boolean = false) {
    val still = LocalGlimmerStill.current || !LocalSettings.current.animations
    val target = level.coerceIn(0f, 1f)
    // Fills up from empty when it appears, like the iPhone's battery when you plug in.
    val fill = remember { Animatable(if (still) target else 0f) }
    LaunchedEffect(target, still) {
        if (still) fill.snapTo(target) else fill.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 90f))
    }
    val low = !charging && target <= 0.2f
    val moving = !still && (charging || low)
    val transition = if (moving) rememberInfiniteTransition(label = "battery") else null
    // The liquid's edge rocks, a gleam runs through it, and a low battery blinks.
    val wave = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
    val gleam = transition?.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing)), label = "gleam")
    val blink = transition?.animateFloat(0.45f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "blink")
    Canvas(modifier) {
        val nub = size.width * 0.08f
        val body = Size(size.width - nub, size.height)
        val r = CornerRadius(size.height * 0.3f)
        drawRoundRect(Color.White.copy(alpha = 0.35f), size = body, cornerRadius = r, style = Stroke(size.height * 0.1f))
        // The little cap on the right.
        drawRoundRect(
            Color.White.copy(alpha = 0.35f),
            topLeft = Offset(body.width + nub * 0.25f, size.height * 0.32f),
            size = Size(nub * 0.75f, size.height * 0.36f),
            cornerRadius = CornerRadius(nub),
        )
        val pad = size.height * 0.18f
        val inner = Size(body.width - 2 * pad, size.height - 2 * pad)
        val w = inner.width * fill.value.coerceIn(0f, 1f)
        if (w > 0.5f) {
            val fillColor = if (low) color.copy(alpha = blink?.value ?: 1f) else color
            val path = Path().apply {
                val top = pad
                val bottom = pad + inner.height
                val left = pad
                // The right edge is a gentle wave while charging.
                val amp = if (wave != null && charging) inner.height * 0.12f else 0f
                val phase = (wave?.value ?: 0f) * 2f * Math.PI.toFloat()
                moveTo(left, top)
                val steps = 8
                for (i in 0..steps) {
                    val y = top + (bottom - top) * i / steps
                    val x = left + w + amp * sin(phase + i * 0.9f)
                    lineTo(x.coerceAtLeast(left), y)
                }
                lineTo(left, bottom)
                close()
            }
            clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(pad, pad, pad + inner.width, pad + inner.height, r)) }) {
                drawPath(path, fillColor)
                // A soft gleam running through the charge.
                val g = gleam?.value
                if (g != null && charging) {
                    val gx = pad + inner.width * g
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startX = gx - inner.width * 0.25f,
                            endX = gx + inner.width * 0.25f,
                        ),
                        topLeft = Offset(pad, pad),
                        size = Size(w, inner.height),
                    )
                }
            }
        }
        // A bolt over the battery while charging.
        if (charging) {
            val cx = body.width / 2f
            val cy = size.height / 2f
            val h = size.height * 0.62f
            val bolt = Path().apply {
                moveTo(cx + h * 0.12f, cy - h / 2f)
                lineTo(cx - h * 0.22f, cy + h * 0.06f)
                lineTo(cx + h * 0.02f, cy + h * 0.06f)
                lineTo(cx - h * 0.12f, cy + h / 2f)
                lineTo(cx + h * 0.22f, cy - h * 0.06f)
                lineTo(cx - h * 0.02f, cy - h * 0.06f)
                close()
            }
            drawPath(bolt, Color.Black.copy(alpha = 0.55f), style = Stroke(size.height * 0.08f, join = StrokeJoin.Round))
            drawPath(bolt, Color.White)
        }
    }
}
