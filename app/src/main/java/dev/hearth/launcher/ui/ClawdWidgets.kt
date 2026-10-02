package dev.hearth.launcher.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.ChatItem
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdTalk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/*
 * Clawd's widgets: pictures of him, a little world he walks through, a clock he lives in,
 * a button to talk to him, his battery mood, his jokes – and him alone as a sticker.
 * Taps are plain clicks without a ripple, so holding a widget still moves or edits it.
 */

private fun clawdPrefs(context: Context) = context.getSharedPreferences("hearth_clawd", Context.MODE_PRIVATE)

/** A tap without the ripple; long presses stay with the home screen (move, edit, remove). */
private fun Modifier.tap(onTap: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
}

/** A mood Clawd plays for a moment after a tap; null when he's back to his usual self. */
private class MoodPlayer(private val scope: CoroutineScope) {
    var mood by mutableStateOf<ClawdMood?>(null)
        private set
    private var job: Job? = null

    fun play(mood: ClawdMood, millis: Long = 2600) {
        this.mood = mood
        job?.cancel()
        job = scope.launch {
            delay(millis)
            this@MoodPlayer.mood = null
        }
    }
}

@Composable
private fun rememberMoodPlayer(): MoodPlayer {
    val scope = rememberCoroutineScope()
    return remember { MoodPlayer(scope) }
}

/** What a tap makes him do, in turn. */
private val TapMoods = listOf(ClawdMood.Wave, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Flip)

// Clawd-Bild: a picture of Clawd

/** The backgrounds of Clawd's picture. */
private enum class ClawdScene(val label: String) {
    Light("Hell"), Terracotta("Terrakotta"), Night("Nacht"), Sunset("Abend"), Accent("Akzent"), Glass("Glas"),
}

private val Ivory = Color(0xFFFAF9F5)

/** Clawd as a picture: big on a background of your choice; a tap makes him move. */
@Composable
internal fun ClawdPictureWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    var scene by remember(id) {
        mutableStateOf(ClawdScene.entries.firstOrNull { it.name == prefs.getString("scene_$id", null) } ?: ClawdScene.Light)
    }
    var taps by remember { mutableIntStateOf(0) }
    val player = rememberMoodPlayer()
    val accent = LocalSettings.current.accent.color
    val background: Brush = when (scene) {
        ClawdScene.Light -> Brush.verticalGradient(listOf(Ivory, Color(0xFFF0EEE6)))
        ClawdScene.Terracotta -> Brush.verticalGradient(listOf(Color(0xFFE08A6B), ClawdColor))
        ClawdScene.Night -> Brush.verticalGradient(listOf(Color(0xFF0F1630), Color(0xFF242B4D)))
        ClawdScene.Sunset -> Brush.verticalGradient(listOf(Color(0xFF5B4B8A), Color(0xFFE0837A), Color(0xFFF6C27A)))
        ClawdScene.Accent -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)))
        ClawdScene.Glass -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val clawd = if (scene == ClawdScene.Terracotta || scene == ClawdScene.Accent) Ivory else ClawdColor
    val dark = scene == ClawdScene.Light
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .tap {
                player.play(TapMoods[taps % TapMoods.size])
                taps++
            },
    ) {
        if (scene == ClawdScene.Night) PixelStars(Modifier.fillMaxSize())
        // A soft shadow under his feet.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.42f
            drawOval(
                Color.Black.copy(alpha = if (dark) 0.08f else 0.18f),
                Offset((size.width - w) / 2f, size.height * 0.775f),
                Size(w, w * 0.12f),
            )
        }
        Clawd(
            Modifier.fillMaxSize(0.66f).align(Alignment.Center),
            mood = player.mood ?: ClawdMood.Idle,
            color = clawd,
        )
        // The scene button: a small dot in the corner, a tap shows the next background.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background((if (dark) Color.Black else Color.White).copy(alpha = 0.14f))
                .clickable {
                    scene = ClawdScene.entries[(scene.ordinal + 1) % ClawdScene.entries.size]
                    prefs.edit().putString("scene_$id", scene.name).apply()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", color = if (dark) Color(0xFF6B6A66) else Color.White, fontSize = 12.sp)
        }
    }
}

/** A few pixel stars that twinkle. */
@Composable
private fun PixelStars(modifier: Modifier) {
    val animate = LocalSettings.current.animations
    val twinkle = if (animate) {
        val t = rememberInfiniteTransition(label = "stars")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
        v
    } else 0.5f
    Canvas(modifier) {
        val px = size.minDimension / 60f
        val stars = listOf(0.12f to 0.14f, 0.3f to 0.3f, 0.52f to 0.1f, 0.74f to 0.24f, 0.88f to 0.12f, 0.2f to 0.5f, 0.84f to 0.46f, 0.64f to 0.4f)
        stars.forEachIndexed { i, (x, y) ->
            val a = 0.35f + 0.65f * abs(sin((twinkle + i * 0.17f) * Math.PI.toFloat()))
            drawRect(Color.White.copy(alpha = a), Offset(size.width * x, size.height * y), Size(px * (if (i % 3 == 0) 2f else 1.4f), px * (if (i % 3 == 0) 2f else 1.4f)))
        }
    }
}

// Clawd-Welt: Clawd walking through a little pixel world

/** A wide picture: Clawd strolls through a pixel landscape that follows the time of day. */
@Composable
internal fun ClawdWorldWidget(modifier: Modifier) {
    val now = rememberTime(everySecond = false)
    val hour = now.hour
    val night = hour >= 21 || hour < 6
    val evening = hour in 17..20
    val player = rememberMoodPlayer()
    val animate = LocalSettings.current.animations
    val walk = if (animate) {
        val t = rememberInfiniteTransition(label = "walk")
        val v by t.animateFloat(0f, 2f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "walkPos")
        v
    } else 0.5f
    val drift = if (animate) {
        val t = rememberInfiniteTransition(label = "clouds")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
        v
    } else 0.3f
    val sky = when {
        night -> listOf(Color(0xFF0B1028), Color(0xFF1E2550))
        evening -> listOf(Color(0xFF4E3D7A), Color(0xFFE07F6F), Color(0xFFF5BE7A))
        else -> listOf(Color(0xFF5CA9F0), Color(0xFFA9D8FA))
    }
    val sleeping = night && player.mood == null
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(sky))
            .tap { player.play(if (night) ClawdMood.Wave else ClawdMood.Dance) },
    ) {
        if (night) PixelStars(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val px = size.height / 40f
            // Sun or moon, as a pixel block.
            val orb = if (night) Color(0xFFF2F0E6) else if (evening) Color(0xFFFFD27A) else Color(0xFFFFE07A)
            val ox = size.width * 0.8f
            val oy = size.height * 0.14f
            drawRect(orb, Offset(ox, oy), Size(px * 6, px * 6))
            drawRect(orb, Offset(ox - px, oy + px), Size(px * 8, px * 4))
            drawRect(orb, Offset(ox + px, oy - px), Size(px * 4, px * 8))
            if (night) drawRect(Color(0xFF1E2550), Offset(ox + px * 3, oy), Size(px * 4, px * 4))
            // Two pixel clouds drifting by (by day and in the evening).
            if (!night) {
                val cloud = Color.White.copy(alpha = if (evening) 0.55f else 0.9f)
                listOf(0f to 0.18f, 0.55f to 0.32f).forEach { (start, y) ->
                    val x = ((drift + start) % 1f) * (size.width + px * 20) - px * 14
                    val cy = size.height * y
                    drawRect(cloud, Offset(x, cy), Size(px * 12, px * 3))
                    drawRect(cloud, Offset(x + px * 3, cy - px * 2), Size(px * 5, px * 2))
                }
            }
            // The ground: pixel grass on top of earth.
            val ground = size.height * 0.8f
            drawRect(if (night) Color(0xFF2D4A3A) else Color(0xFF6CC070), Offset(0f, ground), Size(size.width, px * 2))
            drawRect(if (night) Color(0xFF3B2F2A) else Color(0xFFB07A55), Offset(0f, ground + px * 2), Size(size.width, size.height - ground))
            var gx = px
            while (gx < size.width) {
                drawRect(if (night) Color(0xFF3F6450) else Color(0xFF8ED68E), Offset(gx, ground - px), Size(px, px))
                gx += px * 7
            }
        }
        // Clawd walks to the right, then back to the left; asleep at night, in the middle.
        val clawdH = maxHeight * 0.5f
        val clawdW = clawdH * (14f / 11f)
        val goingRight = walk < 1f
        val pos = if (sleeping) 0.5f else if (goingRight) walk else 2f - walk
        val x = (maxWidth - clawdW) * pos
        Clawd(
            Modifier
                .size(width = clawdW, height = clawdH)
                .offset(x = x, y = maxHeight * 0.8f - clawdH * 0.955f)
                .graphicsLayer { scaleX = if (goingRight || sleeping) 1f else -1f },
            mood = player.mood ?: if (sleeping) ClawdMood.Sleep else if (animate) ClawdMood.Thinking else ClawdMood.Idle,
        )
    }
}

// Clawd-Uhr: the time, with Clawd greeting you

private fun greeting(hour: Int): String = when (hour) {
    in 5..10 -> "Guten Morgen!"
    in 11..13 -> "Mahlzeit!"
    in 14..17 -> "Schönen Nachmittag!"
    in 18..21 -> "Guten Abend!"
    else -> "Gute Nacht … zzz"
}

/** The time big, a greeting, and Clawd beside it (asleep at night). */
@Composable
internal fun ClawdClockWidget(modifier: Modifier) {
    val now = rememberTime(everySecond = false)
    val locale = Locale.getDefault()
    val player = rememberMoodPlayer()
    val asleep = now.hour >= 23 || now.hour < 6
    val accent = LocalSettings.current.accent.color
    BoxWithConstraints(modifier.tap { player.play(ClawdMood.Wave) }) {
        val narrow = maxWidth < 220.dp
        val mood = player.mood ?: if (asleep) ClawdMood.Sleep else ClawdMood.Idle
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val date = now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
        if (narrow) {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Clawd(Modifier.fillMaxWidth(0.55f).height(maxHeight * 0.38f), mood = mood)
                Text(time, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Text(greeting(now.hour), color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Clawd(Modifier.fillMaxHeight(0.75f).width(maxWidth * 0.36f), mood = mood)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(greeting(now.hour), color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(time, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 56.sp)
                    Text(date, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// Frag Clawd: one tap to talk to him

/** Clawd and a glass pill: a tap opens the chat. Shows his last answer, if there is one. */
@Composable
internal fun ClawdAskWidget(modifier: Modifier) {
    val context = LocalContext.current
    val mood by ClaudeAssistant.clawdMood.collectAsStateWithLifecycle()
    val items by ClaudeAssistant.items.collectAsStateWithLifecycle()
    val last = remember(items) { items.lastOrNull { it is ChatItem.Reply } as? ChatItem.Reply }
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .tap { ClaudeAssistant.openAssistant(context) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clawd(Modifier.size(52.dp), mood = mood)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text("Frag Clawd …", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                last?.text ?: "Frag mich was oder sag mir, was ich tun soll",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Akku: Clawd shows how the battery feels

/** Clawd dances while charging, is tired when the battery is low, a pixel bar shows the level. */
@Composable
internal fun ClawdBatteryWidget(modifier: Modifier) {
    val (level, charging) = rememberBattery()
    val player = rememberMoodPlayer()
    val low = level in 0..15
    val mood = player.mood ?: when {
        charging -> ClawdMood.Dance
        low -> ClawdMood.Sleep
        else -> ClawdMood.Idle
    }
    val color = when {
        charging -> Color(0xFF34C759)
        level in 0..20 -> Color(0xFFFF453A)
        else -> Color.White
    }
    BoxWithConstraints(modifier.tap { player.play(ClawdMood.Love) }) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(Modifier.fillMaxWidth(0.62f).height(maxHeight * 0.4f), mood = mood)
            Spacer(Modifier.height(6.dp))
            // Ten pixel cells, filled up to the level.
            Canvas(Modifier.fillMaxWidth(0.7f).height(12.dp)) {
                val gap = size.width * 0.02f
                val cell = (size.width - gap * 9) / 10f
                for (i in 0 until 10) {
                    val filled = level >= 0 && i < (level + 5) / 10
                    drawRect(
                        if (filled) color else Color.White.copy(alpha = 0.16f),
                        Offset(i * (cell + gap), 0f),
                        Size(cell, size.height),
                    )
                }
            }
            Text(
                if (level >= 0) "$level %" else "–",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                when {
                    charging -> "Ich tanke auf! ⚡"
                    low -> "Bin müde … Ladekabel?"
                    else -> "Voll fit"
                },
                color = color.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

// Clawd-Witz: a joke a day, more on tap

/** Clawd tells the joke of the day in a speech bubble; a tap brings the next one. */
@Composable
internal fun ClawdJokeWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    val day = rememberTime(everySecond = false).dayOfYear
    var extra by remember(id) { mutableIntStateOf(prefs.getInt("joke_$id", 0)) }
    val player = rememberMoodPlayer()
    Row(
        modifier
            .padding(12.dp)
            .tap {
                extra++
                prefs.edit().putInt("joke_$id", extra).apply()
                player.play(ClawdMood.Dance)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clawd(Modifier.fillMaxHeight(0.6f).width(72.dp), mood = player.mood ?: ClawdMood.Idle)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                ClawdTalk.joke(day + extra),
                color = Color(0xFF2B2A27),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Sticker: just him

/** Clawd alone, small, with the same mood as everywhere else; a tap makes him react. */
@Composable
internal fun ClawdStickerWidget(modifier: Modifier) {
    val mood by ClaudeAssistant.clawdMood.collectAsStateWithLifecycle()
    var taps by remember { mutableIntStateOf(0) }
    Box(
        modifier.tap {
            ClaudeAssistant.react(TapMoods[taps % TapMoods.size], 2600)
            taps++
        },
        contentAlignment = Alignment.Center,
    ) {
        Clawd(Modifier.fillMaxSize(0.8f), mood = mood)
    }
}
