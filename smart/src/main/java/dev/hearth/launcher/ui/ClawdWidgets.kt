package dev.hearth.launcher.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.hearth.launcher.data.ClawdFocus
import dev.hearth.launcher.data.ClawdOracle
import dev.hearth.launcher.data.ClawdPet
import dev.hearth.launcher.data.clawdAsleep
import dev.hearth.launcher.data.clawdGreeting
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
private val TapMoods = listOf(ClawdMood.Wave, ClawdMood.Jump, ClawdMood.Dance, ClawdMood.Blush, ClawdMood.Love, ClawdMood.Surprised, ClawdMood.Flip)

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
    val clawd: Color? = if (scene == ClawdScene.Terracotta || scene == ClawdScene.Accent) Ivory else null
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
        Canvas(Modifier.fillMaxSize()) { drawClawdWorldScenery(night, evening, drift) }
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

/** Clawd's little world: sun or moon, clouds by day, grass and earth (also for the Clawd app). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawdWorldScenery(night: Boolean, evening: Boolean, drift: Float) {
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

// Clawd-Uhr: the time, with Clawd greeting you

/** The time big, a greeting, and Clawd beside it (asleep at night). */
@Composable
internal fun ClawdClockWidget(modifier: Modifier) {
    val now = rememberTime(everySecond = false)
    val locale = Locale.getDefault()
    val player = rememberMoodPlayer()
    val asleep = clawdAsleep(now.hour)
    val accent = LocalSettings.current.accent.color
    BoxWithConstraints(modifier.tap { player.play(ClawdMood.Wave) }) {
        val narrow = maxWidth < 220.dp
        val boxW = maxWidth
        val boxH = maxHeight
        val mood = player.mood ?: if (asleep) ClawdMood.Sleep else ClawdMood.Idle
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val date = now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
        if (narrow) {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Clawd(Modifier.fillMaxWidth(0.55f).height(boxH * 0.38f), mood = mood)
                Text(time, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Text(clawdGreeting(now.hour), color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Clawd(Modifier.fillMaxHeight(0.75f).width(boxW * 0.36f), mood = mood)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(clawdGreeting(now.hour), color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    val (level, charging) = rememberBatteryLevel()
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
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(Modifier.fillMaxWidth(0.62f).height(boxH * 0.4f), mood = mood)
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

// Clawd-Tamagotchi: feed him, play with him

/** Clawd as a pet: he gets hungry and bored; feeding, playing and petting make him happy. */
@Composable
internal fun ClawdPetWidget(modifier: Modifier) {
    val context = LocalContext.current
    val minute = rememberTime(everySecond = false)
    var state by remember { mutableStateOf(ClawdPet.state(context)) }
    // Hunger grows by itself: read again every minute.
    androidx.compose.runtime.LaunchedEffect(minute.minute) { state = ClawdPet.state(context) }
    val player = rememberMoodPlayer()
    val petCounter = remember { dev.hearth.launcher.data.ClawdTapCounter(windowMs = 2500) }
    val accent = LocalSettings.current.accent.color
    Row(modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Clawd(
            Modifier
                .fillMaxHeight(0.8f)
                .width(86.dp)
                .tap {
                    state = ClawdPet.pet(context)
                    val n = petCounter.tap()
                    player.play(if (n >= 3) ClawdMood.Blush else ClawdMood.Love)
                    if (n >= 5) dev.hearth.launcher.data.EasterEggs.find(context, "petlove")
                },
            mood = player.mood ?: state.mood,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Lv. ${state.level} · ${state.status}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PetBar("Satt", state.food, Color(0xFFFFB04A))
            PetBar("Laune", state.joy, Color(0xFFFF6FA8))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PetButton("🍕 Füttern", accent, Modifier.weight(1f)) {
                    state = ClawdPet.feed(context)
                    player.play(ClawdMood.Dance)
                }
                PetButton("⚽ Spielen", accent, Modifier.weight(1f)) {
                    state = ClawdPet.play(context)
                    player.play(ClawdMood.Flip)
                }
            }
        }
    }
}

@Composable
private fun PetBar(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.width(42.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0, 100) / 100f)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PetButton(label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.28f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

// Clawd-Orakel: ask a yes/no question, tap him

/** Clawd as a magic 8-ball: think of a question, tap, he ponders and answers. */
@Composable
internal fun ClawdOracleWidget(modifier: Modifier) {
    var answer by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.tap {
            if (thinking) return@tap
            thinking = true
            scope.launch {
                delay(1300)
                answer = ClawdOracle.ask()
                thinking = false
            }
        },
    ) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier.fillMaxWidth(0.6f).height(boxH * 0.42f),
                mood = when {
                    thinking -> ClawdMood.Thinking
                    answer != null -> ClawdMood.Wave
                    else -> ClawdMood.Idle
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    thinking -> "Hmm …"
                    answer != null -> answer!!
                    else -> "Denk an eine Ja/Nein-Frage und tipp mich an"
                },
                color = Color.White,
                fontSize = if (answer != null && !thinking) 14.sp else 12.sp,
                fontWeight = if (answer != null && !thinking) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Fokus: 25 minutes of work, Clawd works along

/** A focus timer: tap to start 25 minutes, Clawd works along and dances when it's done. */
@Composable
internal fun ClawdFocusWidget(modifier: Modifier) {
    val context = LocalContext.current
    var end by remember { mutableStateOf(ClawdFocus.endsAt(context)) }
    var celebrate by remember { mutableStateOf(false) }
    val running = end > 0L
    // Ticks every second while it runs, so the minutes count down.
    rememberTime(everySecond = running)
    val left = if (running) (end - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
    // Time's up: count it and let Clawd dance.
    androidx.compose.runtime.LaunchedEffect(running, left == 0L) {
        if (running && left == 0L) {
            ClawdFocus.finished(context)
            end = 0L
            celebrate = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(celebrate) {
        if (celebrate) {
            delay(6000)
            celebrate = false
        }
    }
    val accent = LocalSettings.current.accent.color
    BoxWithConstraints(
        modifier.tap {
            celebrate = false
            if (running) ClawdFocus.stop(context) else ClawdFocus.start(context)
            end = ClawdFocus.endsAt(context)
        },
    ) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier.fillMaxWidth(0.55f).height(boxH * 0.36f),
                mood = when {
                    celebrate -> ClawdMood.Dance
                    running -> ClawdMood.Thinking
                    else -> ClawdMood.Idle
                },
            )
            val minutes = left / 60_000
            val seconds = (left / 1000) % 60
            Text(
                when {
                    celebrate -> "Geschafft! 🎉"
                    running -> "%d:%02d".format(minutes, seconds)
                    else -> "${ClawdFocus.MINUTES}:00"
                },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                when {
                    celebrate -> "Pause verdient"
                    running -> "Wir arbeiten · tippen stoppt"
                    else -> "Fokus · tippen startet" + (ClawdFocus.done(context).takeIf { it > 0 }?.let { " · $it ✓" } ?: "")
                },
                color = accent.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Begleiter: Clawd walking around on the home screen

/**
 * Clawd living on the home screen, just above the dock: he strolls back and forth, sleeps at
 * night and when the battery is nearly empty, dances when the charger goes in. Tap him and he
 * says something (greetings, tips, jokes), double tap and he does a flip, hold him to talk.
 */
@Composable
fun ClawdCompanion(onTalk: () -> Unit, modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    val globalMood by ClaudeAssistant.clawdMood.collectAsStateWithLifecycle()
    val (battery, charging) = rememberBatteryLevel()
    val hour = rememberTime(everySecond = false).hour
    var bubble by remember { mutableStateOf<String?>(null) }
    val player = rememberMoodPlayer()
    var taps by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val counter = remember { dev.hearth.launcher.data.ClawdTapCounter() }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The charger goes in: a little dance.
    var wasCharging by remember { mutableStateOf(charging) }
    androidx.compose.runtime.LaunchedEffect(charging) {
        if (charging && !wasCharging) {
            player.play(ClawdMood.Dance, 4000)
            bubble = "Strom! Ich tanke mit. ⚡"
        }
        wasCharging = charging
    }
    androidx.compose.runtime.LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(5000)
            bubble = null
        }
    }

    val asleep = clawdAsleep(hour) || (battery in 0..10 && !charging)
    val busy = player.mood != null || globalMood != ClawdMood.Idle
    val walking = settings.animations && !asleep && !busy && !dragging
    // The touch handlers live on; they read these fresh.
    val asleepNow by androidx.compose.runtime.rememberUpdatedState(asleep && player.mood == null)
    val chargingNow by androidx.compose.runtime.rememberUpdatedState(charging)
    val batteryNow by androidx.compose.runtime.rememberUpdatedState(battery)
    val hourNow by androidx.compose.runtime.rememberUpdatedState(hour)
    val pos = remember { androidx.compose.animation.core.Animatable(0.5f) }
    var facingRight by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(walking) {
        if (!walking) return@LaunchedEffect
        while (true) {
            delay(kotlin.random.Random.nextLong(2500, 7000))
            val target = kotlin.random.Random.nextFloat()
            facingRight = target > pos.value
            val distance = abs(target - pos.value)
            pos.animateTo(target, tween((distance * 9000).toInt().coerceAtLeast(500), easing = LinearEasing))
        }
    }
    val mood = player.mood ?: when {
        globalMood != ClawdMood.Idle -> globalMood
        dragging -> ClawdMood.Thinking
        asleep -> ClawdMood.Sleep
        pos.isRunning -> ClawdMood.Thinking
        else -> ClawdMood.Idle
    }

    // OMEGA UI 17.5, Clawd Illumination "Riesen-Clawd": two and a half times as big.
    val giant = settings.giantClawd
    BoxWithConstraints(modifier.fillMaxWidth().height(if (giant) 92.dp else 40.dp).padding(horizontal = 22.dp)) {
        val h = if (giant) 86.dp else 34.dp
        val w = h * (14f / 11f)
        val x = (maxWidth - w) * pos.value
        Clawd(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = x)
                .size(width = w, height = h)
                .graphicsLayer { scaleX = if (facingRight) 1f else -1f }
                // Drag him along: he trots after your finger.
                .pointerInput(maxWidth) {
                    val travel = (maxWidth - w).toPx().coerceAtLeast(1f)
                    var from = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            from = pos.value
                        },
                        onDragEnd = {
                            dragging = false
                            if (abs(pos.value - from) > 0.85f) {
                                bubble = "Wiiiee! 🎢"
                                dev.hearth.launcher.data.EasterEggs.find(context, "drag")
                            }
                            player.play(ClawdMood.Jump, 1200)
                        },
                        onDragCancel = { dragging = false },
                    ) { change, dx ->
                        change.consume()
                        if (dx != 0f) facingRight = dx > 0f
                        scope.launch { pos.snapTo((pos.value + dx / travel).coerceIn(0f, 1f)) }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            taps++
                            val n = counter.tap()
                            val reaction = when {
                                asleepNow -> {
                                    dev.hearth.launcher.data.EasterEggs.find(context, "wakeup")
                                    dev.hearth.launcher.data.ClawdReactions.wokenUp
                                }
                                n >= 6 -> {
                                    dev.hearth.launcher.data.EasterEggs.find(context, "dizzy")
                                    dev.hearth.launcher.data.ClawdReactions.dizzy
                                }
                                else -> dev.hearth.launcher.data.ClawdReactions.tap()
                            }
                            player.play(reaction.mood, if (reaction.mood == ClawdMood.Dizzy) 2600 else 1800)
                            // Now and then a tip or a joke instead of a reaction.
                            bubble = if (n < 6 && !asleepNow && taps % 4 == 0) {
                                dev.hearth.launcher.data.ClawdLines.next(hourNow, batteryNow, chargingNow)
                            } else {
                                reaction.line
                            }
                        },
                        onLongPress = { onTalk() },
                    )
                },
            mood = mood,
        )
        val text = bubble
        if (text != null) {
            val bubbleW = 230.dp
            Text(
                text,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (x - 20.dp).coerceIn(0.dp, (maxWidth - bubbleW).coerceAtLeast(0.dp)), y = -(h + 6.dp))
                    .widthIn(max = bubbleW)
                    .clip(RoundedCornerShape(14.dp))
                    // OMEGA UI 18.5: Clawd speaks on a bubble of glass.
                    .background(Color.Black.copy(alpha = 0.34f))
                    .glassSheen(14.dp)
                    .tap { bubble = null }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

// Clawd-Wasser: drinking water with Clawd

/** Glasses of water today: tap for one more, Clawd cheers you on. */
@Composable
internal fun ClawdWaterWidget(modifier: Modifier) {
    val context = LocalContext.current
    val day = rememberTime(everySecond = false).dayOfYear
    var count by remember(day) { mutableIntStateOf(dev.hearth.launcher.data.ClawdWater.glasses(context)) }
    val player = rememberMoodPlayer()
    val goal = dev.hearth.launcher.data.ClawdWater.GOAL
    BoxWithConstraints(
        modifier.tap {
            count = dev.hearth.launcher.data.ClawdWater.add(context)
            player.play(if (count == goal) ClawdMood.Dance else ClawdMood.Love, 1800)
        },
    ) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier.fillMaxWidth(0.5f).height(boxH * 0.32f),
                mood = player.mood ?: if (count >= goal) ClawdMood.Wave else ClawdMood.Idle,
            )
            Text("$count / $goal 💧", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            // One drop per glass, filled up to today's count.
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                repeat(goal) { i ->
                    Box(
                        Modifier
                            .size(width = 8.dp, height = 11.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                            .background(if (i < count) Color(0xFF5AC8FA) else Color.White.copy(alpha = 0.16f)),
                    )
                }
            }
            Text(
                dev.hearth.launcher.data.ClawdWater.line(count),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Würfel: Clawd rolls a die

/** Tap and Clawd rolls a pixel die: he flips while it tumbles. */
@Composable
internal fun ClawdDiceWidget(modifier: Modifier) {
    var value by remember { mutableIntStateOf(6) }
    var rolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.tap {
            if (rolling) return@tap
            rolling = true
            scope.launch {
                // A few quick faces, then the real one.
                repeat(7) {
                    value = dev.hearth.launcher.data.ClawdDice.roll()
                    delay(90L + it * 25L)
                }
                value = dev.hearth.launcher.data.ClawdDice.roll()
                rolling = false
            }
        },
    ) {
        val boxH = maxHeight
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clawd(Modifier.size(width = boxH * 0.42f, height = boxH * 0.36f), mood = if (rolling) ClawdMood.Flip else ClawdMood.Wave)
            PixelDie(value, Modifier.size(boxH * 0.38f))
        }
    }
}

/** A die face in pixels. */
@Composable
private fun PixelDie(value: Int, modifier: Modifier) {
    Canvas(modifier) { drawDie(value) }
}

/** A white die showing [value], filling the scope (also drawn into the Clawd app's widgets). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDie(value: Int) {
    run {
        val corner = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.18f)
        drawRoundRect(Color.White, cornerRadius = corner)
        val pip = size.minDimension * 0.18f
        fun dot(x: Float, y: Float) = drawRect(
            Color(0xFF1B1A1F),
            Offset(size.width * x - pip / 2, size.height * y - pip / 2),
            Size(pip, pip),
        )
        val l = 0.27f
        val m = 0.5f
        val r = 0.73f
        when (value) {
            1 -> dot(m, m)
            2 -> { dot(l, l); dot(r, r) }
            3 -> { dot(l, l); dot(m, m); dot(r, r) }
            4 -> { dot(l, l); dot(r, l); dot(l, r); dot(r, r) }
            5 -> { dot(l, l); dot(r, l); dot(m, m); dot(l, r); dot(r, r) }
            else -> { dot(l, l); dot(r, l); dot(l, m); dot(r, m); dot(l, r); dot(r, r) }
        }
    }
}

// Clawd-Motivation: a kind word each day

/** A kind word for today in a speech bubble; tap for another. */
@Composable
internal fun ClawdMotivationWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    val day = rememberTime(everySecond = false).dayOfYear
    var extra by remember(id) { mutableIntStateOf(prefs.getInt("motivation_$id", 0)) }
    val player = rememberMoodPlayer()
    Row(
        modifier
            .padding(12.dp)
            .tap {
                extra++
                prefs.edit().putInt("motivation_$id", extra).apply()
                player.play(ClawdMood.Love)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFE3D3), Color(0xFFFAF9F5))))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                dev.hearth.launcher.data.ClawdMotivation.of(day, extra),
                color = Color(0xFF2B2A27),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Clawd(Modifier.fillMaxHeight(0.6f).width(72.dp), mood = player.mood ?: ClawdMood.Wave)
    }
}

// Clawd-Wochenende: how long until the weekend

/** The days until the weekend, big, with Clawd dancing once it's there. */
@Composable
internal fun ClawdWeekendWidget(modifier: Modifier) {
    val now = rememberTime(everySecond = false)
    val (big, line) = dev.hearth.launcher.data.clawdWeekend(now)
    val weekend = big == "🎉"
    val accent = LocalSettings.current.accent.color
    BoxWithConstraints(modifier) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(Modifier.fillMaxWidth(0.5f).height(boxH * 0.34f), mood = if (weekend) ClawdMood.Dance else ClawdMood.Thinking)
            Text(big, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(line, color = accent.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// Clawd-Modenschau: Clawd tries on his wardrobe

/** Clawd on the catwalk: each tap, the next outfit and hat (only in this widget). */
@Composable
internal fun ClawdFashionWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    var look by remember(id) { mutableIntStateOf(prefs.getInt("fashion_$id", 1)) }
    val outfits = dev.hearth.launcher.data.ClawdOutfit.entries
    val hats = dev.hearth.launcher.data.ClawdHat.entries
    val outfit = outfits[Math.floorMod(look, outfits.size)]
    val hat = hats[Math.floorMod(look * 3, hats.size)]
    val player = rememberMoodPlayer()
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2B4F), Color(0xFF1E1A2B))))
            .tap {
                look++
                prefs.edit().putInt("fashion_$id", look).apply()
                player.play(ClawdMood.Flip, 900)
            },
    ) {
        val boxH = maxHeight
        // A spotlight on the catwalk.
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Color.White.copy(alpha = 0.10f), Offset(size.width * 0.18f, size.height * 0.62f), Size(size.width * 0.64f, size.height * 0.14f))
        }
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier.fillMaxWidth(0.68f).height(boxH * 0.56f),
                mood = player.mood ?: ClawdMood.Wave,
                hat = hat,
                outfit = outfit,
            )
            Text(
                listOfNotNull(
                    outfit.takeIf { it != dev.hearth.launcher.data.ClawdOutfit.None }?.label,
                    hat.takeIf { it != dev.hearth.launcher.data.ClawdHat.None }?.label,
                ).joinToString(" + ").ifEmpty { "Ganz natürlich" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Antippen: nächster Look", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

// Clawd-Spiel: Clawd Jump from the home screen

/** Clawd Jump on the home screen: the record, and a tap starts the game. */
@Composable
internal fun ClawdGameWidget(modifier: Modifier) {
    val context = LocalContext.current
    // The record changes while playing: read it again now and then.
    val best by androidx.compose.runtime.produceState(dev.hearth.launcher.data.ClawdGameScore.best(context)) {
        while (true) {
            delay(3000)
            value = dev.hearth.launcher.data.ClawdGameScore.best(context)
        }
    }
    BoxWithConstraints(modifier.tap { dev.hearth.launcher.data.ClawdGameScore.open(context) }) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(Modifier.fillMaxWidth(0.55f).height(boxH * 0.4f), mood = ClawdMood.Dance)
            Text("Clawd Jump", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (best > 0) "Rekord $best · tippen zum Spielen" else "Tippen zum Spielen",
                color = LocalSettings.current.accent.color.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Abzeichen: how many badges, and the next one to get

/** Clawd's badges at a glance: how many, and what's next. */
@Composable
internal fun ClawdBadgesWidget(modifier: Modifier) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val minute = rememberTime(everySecond = false).minute
    val badges = remember(settings, minute) { dev.hearth.launcher.data.ClawdBadges.all(context, settings) }
    val earned = badges.count { it.earned }
    val next = badges.firstOrNull { !it.earned }
    val player = rememberMoodPlayer()
    BoxWithConstraints(modifier.tap { player.play(ClawdMood.Love) }) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier.fillMaxWidth(0.5f).height(boxH * 0.34f),
                mood = player.mood ?: if (next == null) ClawdMood.Dance else ClawdMood.Wave,
            )
            Text("🏅 $earned / ${badges.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (next == null) "Alle Abzeichen! 🎉" else "Nächstes: ${next.hint}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// Clawd-Tagebuch: one face a day

/** How you feel today, with Clawd feeling along; the week as a row of faces. */
@Composable
internal fun ClawdDiaryWidget(modifier: Modifier) {
    val context = LocalContext.current
    val day = rememberTime(everySecond = false).dayOfYear
    var face by remember(day) { mutableStateOf(dev.hearth.launcher.data.ClawdDiary.of(context)) }
    var week by remember(day, face) { mutableStateOf(dev.hearth.launcher.data.ClawdDiary.week(context)) }
    val player = rememberMoodPlayer()
    val faces = dev.hearth.launcher.data.ClawdDiary.Faces
    Row(
        modifier
            .padding(12.dp)
            .tap {
                val next = dev.hearth.launcher.data.ClawdDiary.next(context)
                face = next
                week = dev.hearth.launcher.data.ClawdDiary.week(context)
                player.play(dev.hearth.launcher.data.ClawdDiary.moodFor(next), 2200)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clawd(
            Modifier.fillMaxHeight(0.7f).width(76.dp),
            mood = player.mood ?: dev.hearth.launcher.data.ClawdDiary.moodFor(face),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(face?.let { faces[it] } ?: "❔", fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    dev.hearth.launcher.data.ClawdDiary.line(face),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            // The week: one face per day, today last.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEachIndexed { i, f ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == week.lastIndex) 0.22f else 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(f?.let { faces[it] } ?: "·", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// Clawd-Münze: heads or tails

/** Tap and Clawd flips a coin: it spins, he flips along, then it lands (rarely on its edge). */
@Composable
internal fun ClawdCoinWidget(modifier: Modifier) {
    val context = LocalContext.current
    var side by remember { mutableIntStateOf(dev.hearth.launcher.data.ClawdCoin.HEADS) }
    var flipped by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.tap {
            if (spin.isRunning) return@tap
            scope.launch {
                spin.snapTo(0f)
                val result = dev.hearth.launcher.data.ClawdCoin.flip(context)
                // Turns over a few times, then shows the side it fell on.
                spin.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                side = result
                flipped = true
            }
        },
    ) {
        val boxH = maxHeight
        val turning = spin.isRunning
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Clawd(
                    Modifier.size(width = boxH * 0.4f, height = boxH * 0.34f),
                    mood = when {
                        turning -> ClawdMood.Flip
                        side == dev.hearth.launcher.data.ClawdCoin.EDGE -> ClawdMood.Surprised
                        flipped -> ClawdMood.Wave
                        else -> ClawdMood.Idle
                    },
                )
                Canvas(
                    Modifier
                        .size(boxH * 0.36f)
                        .graphicsLayer { translationY = -size.height * 0.5f * sin(spin.value * Math.PI).toFloat() },
                ) {
                    // Edge-on every half turn while it spins.
                    val squeeze = if (turning) abs(kotlin.math.cos(spin.value * Math.PI * 7)).toFloat() else 1f
                    drawCoin(if (turning) (spin.value * 7).toInt() % 2 else side, squeeze)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (turning) "…" else if (flipped) dev.hearth.launcher.data.ClawdCoin.label(side) else "Kopf oder Zahl?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text("Antippen zum Werfen", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

/**
 * A gold coin in pixels: Clawd's face on heads, a heart on tails, a thin upright bar on its
 * edge. [squeeze] narrows it while it turns (also drawn into the Clawd app's widgets).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoin(side: Int, squeeze: Float = 1f) {
    val d = size.minDimension
    val gold = Color(0xFFF2C14E)
    val rim = Color(0xFFB7832F)
    val top = (size.height - d) / 2f
    if (side == dev.hearth.launcher.data.ClawdCoin.EDGE) {
        drawRoundRect(rim, Offset(size.width / 2f - d * 0.08f, top), Size(d * 0.16f, d), androidx.compose.ui.geometry.CornerRadius(d * 0.06f))
        drawRect(gold, Offset(size.width / 2f - d * 0.03f, top + d * 0.06f), Size(d * 0.06f, d * 0.88f))
        return
    }
    val w = d * squeeze.coerceIn(0.06f, 1f)
    val left = (size.width - w) / 2f
    drawOval(rim, Offset(left, top), Size(w, d))
    drawOval(gold, Offset(left + w * 0.08f, top + d * 0.08f), Size(w * 0.84f, d * 0.84f))
    if (squeeze < 0.35f) return
    val px = d * 0.09f
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun pixel(x: Float, y: Float) = drawRect(rim, Offset(cx + (x - 0.5f) * px * squeeze, cy + (y - 0.5f) * px), Size(px * squeeze, px))
    if (side == dev.hearth.launcher.data.ClawdCoin.HEADS) {
        // Clawd's face: two eyes and a little smile.
        pixel(-1.5f, -1f); pixel(1.5f, -1f)
        pixel(-1.5f, 1.2f); pixel(-0.5f, 1.8f); pixel(0.5f, 1.8f); pixel(1.5f, 1.2f)
    } else {
        // A pixel heart.
        pixel(-1f, -1f); pixel(1f, -1f)
        pixel(-1.5f, 0f); pixel(-0.5f, 0f); pixel(0.5f, 0f); pixel(1.5f, 0f)
        pixel(-1f, 1f); pixel(0f, 1f); pixel(1f, 1f)
        pixel(0f, 2f)
    }
}

// Clawd-Atmen: a calm minute

/** One calm minute: Clawd swells as you breathe in and settles as you breathe out. */
@Composable
internal fun ClawdBreathWidget(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val grow = remember { Animatable(0f) }
    var phase by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var finished by remember { mutableStateOf(false) }
    val calm = Color(0xFF7FD1C7)
    BoxWithConstraints(
        modifier.tap {
            val running = job
            if (running != null) {
                // Stopped halfway: back to rest.
                running.cancel()
                job = null
                phase = null
                scope.launch { grow.animateTo(0f, tween(600)) }
                return@tap
            }
            finished = false
            job = scope.launch {
                // Timed by the clock too, so it's a real minute even with animations off.
                suspend fun breathe(to: Float, millis: Long) {
                    val start = android.os.SystemClock.uptimeMillis()
                    grow.animateTo(to, tween(millis.toInt(), easing = FastOutSlowInEasing))
                    val left = millis - (android.os.SystemClock.uptimeMillis() - start)
                    if (left > 0) delay(left)
                }
                for (r in 1..dev.hearth.launcher.data.ClawdBreath.ROUNDS) {
                    round = r
                    phase = "Einatmen …"
                    breathe(1f, dev.hearth.launcher.data.ClawdBreath.IN_MS)
                    phase = "Ausatmen …"
                    breathe(0f, dev.hearth.launcher.data.ClawdBreath.OUT_MS)
                }
                phase = null
                finished = true
                job = null
                dev.hearth.launcher.data.ClawdBreath.finished(context)
            }
        },
    ) {
        val boxH = maxHeight
        // A soft light breathing behind him.
        Canvas(Modifier.fillMaxSize()) {
            val g = grow.value
            drawCircle(
                calm.copy(alpha = 0.10f + 0.14f * g),
                radius = size.minDimension * (0.2f + 0.2f * g),
                center = Offset(size.width / 2f, size.height * 0.4f),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Clawd(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(boxH * 0.34f)
                    .graphicsLayer {
                        val s = 0.82f + 0.24f * grow.value
                        scaleX = s
                        scaleY = s
                    },
                mood = when {
                    finished -> ClawdMood.Love
                    phase != null -> ClawdMood.Idle
                    else -> ClawdMood.Wave
                },
                // Still while breathing (only the breath moves him), lively again after.
                animate = phase == null && LocalSettings.current.animations,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                phase ?: if (finished) "Gut gemacht 🧡" else "Atmen",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                if (phase != null) "Runde $round von ${dev.hearth.launcher.data.ClawdBreath.ROUNDS} · tippen stoppt" else "1 Minute · antippen",
                color = calm.copy(alpha = 0.9f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Countdown: the next big day

/** Days until the next big day (Christmas, New Year's Eve, Easter …); a tap shows the one after. */
@Composable
internal fun ClawdCountdownWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    val today = rememberTime(everySecond = false).toLocalDate()
    val days = remember(today) { dev.hearth.launcher.data.ClawdCountdown.upcoming(today) }
    var pick by remember(id) { mutableIntStateOf(prefs.getInt("countdown_$id", 0)) }
    val day = days[Math.floorMod(pick, days.size)]
    val left = dev.hearth.launcher.data.ClawdCountdown.daysUntil(day, today)
    val accent = LocalSettings.current.accent.color
    val player = rememberMoodPlayer()
    // The big day itself: Clawd dances (and that's an easter egg).
    androidx.compose.runtime.LaunchedEffect(left) {
        if (left == 0L) dev.hearth.launcher.data.EasterEggs.find(context, "bigday")
    }
    BoxWithConstraints(
        modifier.tap {
            pick++
            prefs.edit().putInt("countdown_$id", pick).apply()
            player.play(ClawdMood.Jump, 900)
        },
    ) {
        val boxH = maxHeight
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Clawd(
                    Modifier.size(width = boxH * 0.34f, height = boxH * 0.3f),
                    mood = player.mood ?: if (left == 0L) ClawdMood.Dance else ClawdMood.Wave,
                )
                Text(day.emoji, fontSize = 30.sp)
            }
            Text(
                if (left == 0L) "Heute!" else if (left == 1L) "Morgen!" else "$left Tage",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "bis ${day.name}",
                color = accent.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd-Rätsel: a riddle, then its answer

/** A riddle in Clawd's speech bubble: a tap shows the answer, the next tap the next riddle. */
@Composable
internal fun ClawdRiddleWidget(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { clawdPrefs(context) }
    var n by remember(id) { mutableIntStateOf(prefs.getInt("riddle_$id", 0)) }
    var shown by remember(id) { mutableStateOf(prefs.getBoolean("riddleShown_$id", false)) }
    val riddle = dev.hearth.launcher.data.ClawdRiddles.of(n)
    Row(
        modifier
            .padding(12.dp)
            .tap {
                if (shown) {
                    n++
                    shown = false
                } else {
                    shown = true
                    dev.hearth.launcher.data.ClawdRiddles.solve(context, n)
                }
                prefs.edit().putInt("riddle_$id", n).putBoolean("riddleShown_$id", shown).apply()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clawd(Modifier.fillMaxHeight(0.6f).width(72.dp), mood = if (shown) ClawdMood.Surprised else ClawdMood.Thinking)
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE6E0FF), Color(0xFFFAF9F5))))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                riddle.question,
                color = Color(0xFF2B2A27),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (shown) "💡 ${riddle.answer}" else "Antippen für die Lösung",
                color = if (shown) Color(0xFFB35A3A) else Color(0xFF8A8780),
                fontSize = 12.sp,
                fontWeight = if (shown) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Clawd: Schnick-Schnack-Schnuck

/** Rock, paper, scissors against Clawd: three hands to pick from, his hand, the score. */
@Composable
internal fun ClawdRpsWidget(modifier: Modifier) {
    val context = LocalContext.current
    var round by remember { mutableStateOf(dev.hearth.launcher.data.ClawdRps.last(context)) }
    var shaking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hands = dev.hearth.launcher.data.ClawdRps.Hands
    Row(modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Clawd(
                Modifier.fillMaxWidth().height(58.dp),
                mood = if (shaking) ClawdMood.Jump else dev.hearth.launcher.data.ClawdRps.moodFor(round),
            )
            Text(
                if (shaking) "…" else round?.let { hands[it.clawd] } ?: "❔",
                fontSize = 24.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    shaking -> "Schnick, Schnack …"
                    round != null -> dev.hearth.launcher.data.ClawdRps.line(round!!)
                    else -> "Schnick-Schnack-Schnuck!"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            round?.let {
                Text("Du ${it.wins} : ${it.losses} Clawd", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hands.forEachIndexed { i, hand ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (!shaking && round?.you == i) LocalSettings.current.accent.color.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.12f),
                            )
                            .tap {
                                if (shaking) return@tap
                                shaking = true
                                scope.launch {
                                    delay(450)
                                    round = dev.hearth.launcher.data.ClawdRps.play(context, i)
                                    shaking = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(hand, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}
