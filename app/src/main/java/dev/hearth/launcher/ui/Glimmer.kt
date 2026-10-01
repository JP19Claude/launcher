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

/** Height of the pill when not expanded: as tall as the status bar row, like the camera ring. */
const val ISLAND_HEIGHT_DP = 30f

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
    val corner = if (expanded) 38.dp else height / 2

    // Something new arrives: a little hop and a shimmer in its color, like a drop landing.
    val animations = LocalSettings.current.animations
    val hop = remember { Animatable(1f) }
    // An app flowing in: wider and flatter for a moment, then wobbling back like a drop.
    val squash = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    val key = islandKey(content)
    val lastPulse = remember { intArrayOf(pulse) }
    LaunchedEffect(key, pulse) {
        if (!animations || content is IslandContent.Hidden) return@LaunchedEffect
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
        if (content !is IslandContent.Idle) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (tapOpens && !expanded) onOpen(content) else onToggle()
        }
    }
    val hold: () -> Unit = {
        if (content !is IslandContent.Idle) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (tapOpens && !expanded) onExpand() else onOpen(content)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (content is IslandContent.Hidden) return@Box
        Row(
            Modifier
                .padding(top = topInset)
                .graphicsLayer {
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
                Spacer(Modifier.width(6.dp))
                IslandShape(
                    style = style,
                    corner = 15.dp,
                    modifier = Modifier
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

/** The color Glimmer shimmers in for an activity. */
private fun glowColorOf(content: IslandContent, accent: Color): Color = when (content) {
    is IslandContent.Alert -> content.color
    is IslandContent.Message -> accent
    is IslandContent.Media -> content.playing.artColor ?: Color.White
    is IslandContent.Live -> noticeColor(content.notice.kind)
    else -> Color.White
}

internal fun islandKey(content: IslandContent): String = when (content) {
    is IslandContent.Media -> "media"
    is IslandContent.Live -> "live-${content.notice.key}"
    is IslandContent.Message -> "msg-${content.notice.key}"
    is IslandContent.Alert -> "alert-${content.title}"
    IslandContent.Idle -> "idle"
    IslandContent.Hidden -> "hidden"
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
    else -> Color.White
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
            NoticeKind.Timer -> GlyphIcon(Glyph.Alarm, Orange, Modifier.size(size))
            else -> AppBadge(content.notice, size)
        }
        is IslandContent.Message -> AppBadge(content.notice, size)
        is IslandContent.Alert -> GlyphIcon(content.glyph, content.color, Modifier.size(size))
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
                    NoticeKind.Call, NoticeKind.Timer -> {
                        val now = rememberNowMillis()
                        Text(
                            formatDuration(if (notice.countDown) notice.chronometerBase - now else now - notice.chronometerBase),
                            color = noticeColor(notice.kind),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    NoticeKind.Progress -> ProgressRing(notice, Modifier.size(18.dp))
                    else -> PulseDot(Color.White)
                }
            }
            is IslandContent.Message -> PulseDot(LocalSettings.current.accent.color)
            is IslandContent.Alert -> Text(
                content.value,
                color = content.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
            else -> Unit
        }
        Spacer(Modifier.width(2.dp))
    }
}

/** A small dot that breathes: something new is waiting. */
@Composable
private fun PulseDot(color: Color) {
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
            is IslandContent.Media -> {
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
                    if (notice.kind == NoticeKind.Call || notice.kind == NoticeKind.Timer) {
                        val now = rememberNowMillis()
                        Text(
                            formatDuration(if (notice.countDown) notice.chronometerBase - now else now - notice.chronometerBase),
                            color = noticeColor(notice.kind),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Light,
                        )
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
                                    .background(
                                        if (notice.kind == NoticeKind.Call && action.title.contains("leg", ignoreCase = true)) {
                                            Color(0xFFFF453A)
                                        } else {
                                            Color.White.copy(alpha = 0.16f)
                                        },
                                    )
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
            else -> Unit
        }
    }
}

/** Progress you can drag or tap to jump; grows a little under the finger, like on iOS. */
@Composable
private fun SeekBar(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
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
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Color.White))
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

/** Four bars dancing to the music (still while paused), in the cover's color. */
@Composable
private fun Equalizer(color: Color, playing: Boolean, modifier: Modifier) {
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
