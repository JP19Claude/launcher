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
import dev.hearth.launcher.data.GlimmerMusicStyle
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** What Glimmer shows right now. */
@Immutable
sealed interface IslandContent {
    /** Nothing going on and the idle pill is off (or the phone is sideways). */
    data object Hidden : IslandContent

    /** Nothing going on: a small black pill around the camera. */
    data object Idle : IslandContent

    data class Media(val playing: NowPlaying) : IslandContent

    data class Live(val notice: LiveNotice) : IslandContent

    data class Message(val notice: LiveNotice) : IslandContent

    /** The flashlight is on; tap (or hold) turns it off. */
    data object Torch : IslandContent

    /** Face ID moment: scanning while the phone checks, then a tick once it's unlocked. */
    data class Unlock(val success: Boolean, val face: Boolean) : IslandContent

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
fun islandSize(content: IslandContent, expanded: Boolean, screenWidthDp: Float, hasSecondary: Boolean): DpSize {
    val full = min(screenWidthDp - 16f, 420f).dp
    val compact = (screenWidthDp * 0.44f).coerceIn(150f, 190f).dp
    return when {
        content is IslandContent.Hidden -> DpSize(0.dp, 0.dp)
        content is IslandContent.Idle -> DpSize((screenWidthDp * 0.27f).coerceIn(86f, 110f).dp, ISLAND_HEIGHT_DP.dp)
        // Like Face ID on the iPhone: the island becomes a rounded square for the moment.
        content is IslandContent.Unlock -> DpSize(UNLOCK_SIZE_DP.dp, UNLOCK_SIZE_DP.dp)
        !expanded -> DpSize(compact, ISLAND_HEIGHT_DP.dp)
        else -> DpSize(
            full,
            when (content) {
                is IslandContent.Media -> 178.dp
                is IslandContent.Live -> if (content.notice.actions.isNotEmpty()) 158.dp else 116.dp
                is IslandContent.Message -> if (content.notice.actions.isNotEmpty()) 160.dp else 124.dp
                else -> 96.dp
            },
        )
    }.let { if (hasSecondary && !expanded && content !is IslandContent.Idle) DpSize(it.width + SecondaryExtra, it.height) else it }
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
    onSwap: () -> Unit = {},
    onFocus: (IslandContent) -> Unit = {},
    onCollapse: () -> Unit,
    onOpen: (IslandContent) -> Unit,
    onTargetSize: (DpSize) -> Unit,
    /** Always-on display: dimmed and without motion. */
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val hasSecondary = secondary != null && !expanded
    val target = islandSize(content, expanded, screenWidthDp, hasSecondary)
    LaunchedEffect(target) { onTargetSize(target) }
    LaunchedEffect(expanded, content) {
        if (expanded) {
            delay(9000)
            onCollapse()
        }
    }

    val morph = spring<Dp>(dampingRatio = 0.66f, stiffness = 380f)
    val mainWidth = if (hasSecondary) target.width - SecondaryExtra else target.width
    val width by animateDpAsState(mainWidth, morph, label = "islandWidth")
    val height by animateDpAsState(target.height, morph, label = "islandHeight")
    val corner = when {
        expanded -> 38.dp
        content is IslandContent.Unlock -> 30.dp
        else -> height / 2
    }

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
        if (content is IslandContent.Idle && !pulsed) return@LaunchedEffect
        launch {
            if (pulsed) {
                squash.snapTo(0f)
                squash.animateTo(1f, tween(110))
                squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f))
            } else {
                hop.snapTo(1f)
                hop.animateTo(1.07f, tween(120))
                hop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f))
            }
        }
        if (glow) {
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, tween(260))
            shimmer.animateTo(0f, tween(1400))
        }
    }
    val glowColor = glowColorOf(content, LocalSettings.current.accent.color)
    val tap: () -> Unit = {
        if (content !is IslandContent.Idle && content !is IslandContent.Unlock) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (tapOpens && !expanded) onOpen(content) else onToggle()
        }
    }
    val hold: () -> Unit = {
        if (content !is IslandContent.Idle && content !is IslandContent.Unlock) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (tapOpens && !expanded) onExpand() else onOpen(content)
        }
    }

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
        if (content is IslandContent.Hidden) return@Box
        Row(
            Modifier
                .padding(top = topInset)
                .graphicsLayer {
                    // Always-on display: softer, so it doesn't glare or burn in.
                    alpha = if (dimmed) 0.62f else 1f
                    val s = if (expanded) 1f else hop.value
                    val q = if (expanded) 0f else squash.value
                    scaleX = s * (1f + 0.14f * q)
                    scaleY = s * (1f - 0.12f * q)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            verticalAlignment = Alignment.Top,
        ) {
            IslandShape(
                style = style,
                corner = corner,
                modifier = Modifier
                    .drawBehind {
                        val a = shimmer.value
                        if (a > 0.01f && !expanded) {
                            // Soft rings growing outwards, fading: the "glimmer".
                            for (i in 1..4) {
                                val spread = i * 1.6.dp.toPx()
                                drawRoundRect(
                                    color = glowColor.copy(alpha = a * 0.32f / i),
                                    topLeft = Offset(-spread, -spread),
                                    size = Size(size.width + spread * 2, size.height + spread * 2),
                                    cornerRadius = CornerRadius(size.height / 2 + spread),
                                )
                            }
                        }
                    }
                    .size(width, height)
                    .pointerInput(content, expanded, tapOpens) {
                        detectTapGestures(onTap = { tap() }, onLongPress = { hold() })
                    }
                    // Pull down to unfold, push up to fold, sideways to switch activities.
                    .pointerInput(content, expanded) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDragEnd = {
                                val threshold = 18.dp.toPx()
                                val vertical = abs(total.y) > abs(total.x)
                                when {
                                    content is IslandContent.Idle -> Unit
                                    vertical && total.y > threshold && !expanded -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onExpand()
                                    }
                                    vertical && total.y < -threshold && expanded -> onCollapse()
                                    !vertical && abs(total.x) > threshold * 2 && !expanded -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSwap()
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
                    targetState = content to expanded,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.92f))
                            .togetherWith(fadeOut(tween(90)))
                    },
                    contentKey = { (c, e) -> islandKey(c) to e },
                    label = "islandContent",
                ) { (c, e) ->
                    if (e) {
                        ExpandedContent(c, media, onOpen = { onOpen(c) }, onAction = { sendIntent(context, it); onCollapse() })
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
                        .pointerInput(secondary) {
                            detectTapGestures(
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFocus(secondary)
                                },
                                onLongPress = { onOpen(secondary) },
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
    is IslandContent.Unlock -> Color.White
    IslandContent.Torch -> Yellow
    else -> Color.White
}

internal fun islandKey(content: IslandContent): String = when (content) {
    is IslandContent.Media -> "media"
    is IslandContent.Live -> "live-${content.notice.key}"
    is IslandContent.Message -> "msg-${content.notice.key}"
    is IslandContent.Alert -> "alert-${content.title}"
    IslandContent.Idle -> "idle"
    IslandContent.Hidden -> "hidden"
    // One key for scanning and done, so the scan turns into the tick in place.
    is IslandContent.Unlock -> "unlock"
    IslandContent.Torch -> "torch"
}

@Composable
private fun IslandShape(style: GlimmerStyle, corner: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    when (style) {
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
    when (content) {
        is IslandContent.Media -> {
            val art = content.playing.art
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
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
        is IslandContent.Alert -> GlyphIcon(content.glyph, content.color, Modifier.size(size))
        IslandContent.Torch -> GlyphIcon(Glyph.Torch, Yellow, Modifier.size(size))
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
            Text(liveTimeText(content.notice, now) ?: "", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TimeStyle)
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
                            Text(
                                time,
                                // On hold: the time stands, a little dimmer.
                                color = noticeColor(notice.kind).copy(alpha = if (notice.paused) 0.55f else 1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                style = TimeStyle,
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
                    LevelBar(level, content.color, Modifier.size(24.dp, 12.dp))
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
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
                            Text(
                                time,
                                color = noticeColor(notice.kind).copy(alpha = if (notice.paused) 0.6f else 1f),
                                // Big like the iPhone's timer, digits that don't wobble.
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Medium,
                                style = TimeStyle,
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
                content.level?.let { LevelBar(it, content.color, Modifier.size(52.dp, 24.dp)) }
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
    Box(
        Modifier
            .size(if (big) 52.dp else 44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (big) 0.16f else 0f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Color.White, Modifier.size(if (big) 26.dp else 22.dp))
    }
}

/**
 * Face ID (or the fingerprint) like on the iPhone: the frame and face breathe and look around
 * while the phone checks, then everything folds into a tick once it's unlocked.
 */
@Composable
private fun UnlockGlyph(content: IslandContent.Unlock, modifier: Modifier) {
    val done = remember { Animatable(if (content.success) 1f else 0f) }
    LaunchedEffect(content.success) {
        if (content.success) done.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) else done.snapTo(0f)
    }
    val still = LocalGlimmerStill.current
    val transition = rememberInfiniteTransition(label = "unlockScan")
    val breath by transition.animateFloat(0.93f, 1.03f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "breath")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "scan")
    Canvas(modifier) {
        val d = done.value
        val w = size.minDimension
        val stroke = w * 0.07f
        val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val checking = if (still) 1f else breath
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
            val frameColor = androidx.compose.ui.graphics.lerp(Color.White, Green, d).copy(alpha = 1f - 0.35f * d)
            for ((sx, sy) in listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)) {
                scale(sx, sy, center) { drawPath(corner, frameColor, style = lineStyle) }
            }
        }
        val features = 1f - ((d - 0f) / 0.45f).coerceIn(0f, 1f)
        if (features > 0f) {
            val look = if (still) 0f else sin(scan * 2f * Math.PI.toFloat()) * w * 0.035f
            if (content.face) {
                translate(left = look) {
                    val c = Color.White.copy(alpha = features)
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
                        Color.White.copy(alpha = features * (0.35f + 0.65f * lit)),
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
private fun LevelBar(level: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height * 0.3f)
        drawRoundRect(Color.White.copy(alpha = 0.35f), size = size, cornerRadius = r, style = Stroke(size.height * 0.1f))
        val pad = size.height * 0.18f
        drawRoundRect(
            color,
            topLeft = Offset(pad, pad),
            size = Size((size.width - 2 * pad) * level.coerceIn(0f, 1f), size.height - 2 * pad),
            cornerRadius = r,
        )
    }
}
