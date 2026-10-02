package dev.hearth.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdSkin
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs
import kotlin.math.sin

/**
 * Clawd in a search bar (where Galaxy AI sits on a Galaxy): a tap does what the bar does,
 * holding him makes him wave (easter egg).
 */
@Composable
fun ClawdInBar(size: androidx.compose.ui.unit.Dp, onTap: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mood by dev.hearth.launcher.data.ClaudeAssistant.clawdMood.collectAsStateWithLifecycle()
    Clawd(
        Modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        dev.hearth.launcher.data.ClaudeAssistant.react(ClawdMood.Wave)
                        dev.hearth.launcher.data.EasterEggs.find(context, "wave")
                    },
                )
            },
        mood = mood,
    )
}

/** Clawd's terracotta. */
val ClawdColor = Color(0xFFD97757)

/**
 * Clawd, Claude's little pixel creature: a square body with two eye slits, arms out to the
 * sides and four legs. Drawn from pixels, so it stays crisp at any size.
 *
 * ```
 * ..##########..
 * ..#o######o#..
 * ..#o######o#..
 * ##############
 * ##############
 * ..##########..
 * ..##########..
 * ...#.#..#.#...
 * ...#.#..#.#...
 * ```
 */
private const val COLS = 14
private const val ROWS = 9

/**
 * Clawd, alive: he blinks and bobs when idle, trots when thinking, jumps and waves his arms
 * when dancing, flips when tickled, floats hearts when loved and dreams in z's when asleep.
 * Without animations (or on the always-on display) he simply stands. His color and hat come
 * from the settings unless given.
 */
@Composable
fun Clawd(
    modifier: Modifier = Modifier,
    mood: ClawdMood = ClawdMood.Idle,
    color: Color? = null,
    animate: Boolean = LocalSettings.current.animations,
    hat: ClawdHat = LocalSettings.current.clawdHat,
) {
    val skin = LocalSettings.current.clawdSkin
    if (!animate) {
        val body = color ?: skin.color
        Canvas(modifier) { drawClawdPose(mood, body, hat) }
        return
    }
    val t = rememberInfiniteTransition(label = "clawd")
    // One clock for everything: 0..1 over 2.4 s.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "clawdClock")
    Canvas(modifier) {
        val body = color ?: if (skin == ClawdSkin.Rainbow) rainbow(clock) else skin.color
        val phase = clock * 2f * Math.PI.toFloat()
        // A blink now and then: the eyes close for a moment near the end of each loop.
        val blink = mood != ClawdMood.Love && clock in 0.92f..0.97f
        when (mood) {
            ClawdMood.Idle -> drawClawd(body, blink, bob = sin(phase) * 0.25f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat)
            ClawdMood.Thinking -> drawClawd(body, blink, bob = abs(sin(phase * 4f)) * -0.4f, legPhase = clock * 8f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat)
            ClawdMood.Dance -> drawClawd(body, blink = false, bob = abs(sin(phase * 3f)) * -1.4f, legPhase = clock * 12f, armLift = sin(phase * 3f), spin = 0f, hearts = 0f, hat = hat)
            ClawdMood.Flip -> drawClawd(body, blink = true, bob = -abs(sin(phase)) * 1.2f, legPhase = 0f, armLift = 1f, spin = clock * 360f, hearts = 0f, hat = hat)
            ClawdMood.Love -> drawClawd(body, blink = true, bob = sin(phase) * 0.3f, legPhase = 0f, armLift = 0.5f, spin = 0f, hearts = clock, hat = hat)
            ClawdMood.Wave -> drawClawd(body, blink, bob = 0f, legPhase = 0f, armLift = sin(phase * 4f), spin = 0f, hearts = 0f, hat = hat)
            ClawdMood.Sleep -> drawClawd(body, blink = true, bob = sin(phase) * 0.15f, legPhase = 0f, armLift = -0.4f, spin = 0f, hearts = 0f, zzz = clock, hat = hat)
        }
    }
}

/** The rainbow skin's color at [t] (0..1): soft, so the eyes stay visible. */
fun rainbow(t: Float): Color = Color.hsv((t * 360f) % 360f, 0.55f, 0.95f)

/** Clawd standing still in a pose that shows his [mood] (for still pictures and widgets). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawdPose(mood: ClawdMood, color: Color, hat: ClawdHat) {
    when (mood) {
        ClawdMood.Idle -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat)
        ClawdMood.Thinking -> drawClawd(color, blink = false, bob = -0.2f, legPhase = 2f, armLift = 0.3f, spin = 0f, hearts = 0f, hat = hat)
        ClawdMood.Dance -> drawClawd(color, blink = false, bob = -0.9f, legPhase = 1f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat)
        ClawdMood.Flip -> drawClawd(color, blink = true, bob = -0.6f, legPhase = 0f, armLift = 1f, spin = 18f, hearts = 0f, hat = hat)
        ClawdMood.Love -> drawClawd(color, blink = true, bob = 0f, legPhase = 0f, armLift = 0.5f, spin = 0f, hearts = 0.35f, hat = hat)
        ClawdMood.Wave -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat)
        ClawdMood.Sleep -> drawClawd(color, blink = true, bob = 0f, legPhase = 0f, armLift = -0.4f, spin = 0f, hearts = 0f, zzz = 0.45f, hat = hat)
    }
}

/**
 * Clawd as a picture, for places without Compose (home screen widgets of other launchers):
 * [background] is drawn first, then Clawd in his pose filling [clawdScale] of the picture.
 */
fun renderClawd(
    width: Int,
    height: Int,
    mood: ClawdMood,
    color: Color,
    hat: ClawdHat,
    clawdScale: Float = 1f,
    background: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null,
): android.graphics.Bitmap {
    val image = androidx.compose.ui.graphics.ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    val full = Size(width.toFloat(), height.toFloat())
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image),
        full,
    ) {
        background?.invoke(this)
        val w = full.width * clawdScale
        val h = full.height * clawdScale
        translate(left = (full.width - w) / 2f, top = (full.height - h) / 2f) {
            // drawClawd fits him into the scope's size: shrink it to his part for a moment.
            drawContext.size = Size(w, h)
            drawClawdPose(mood, color, hat)
            drawContext.size = full
        }
    }
    return image.asAndroidBitmap()
}

/**
 * Draws Clawd centered in the canvas, keeping his proportions.
 * [bob] moves him up and down (in pixels of his grid), [legPhase] makes the legs step,
 * [armLift] raises the right arm (−1..1), [spin] turns him, [hearts] (0..1) lets a heart rise.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawd(
    color: Color,
    blink: Boolean,
    bob: Float,
    legPhase: Float,
    armLift: Float,
    spin: Float,
    hearts: Float,
    zzz: Float = 0f,
    hat: ClawdHat = ClawdHat.None,
) {
    // Room above for a jump or a heart (and a hat), so he never leaves the canvas.
    val headroom = if (hat.tall) 2f else 0f
    val px = minOf(size.width / COLS, size.height / (ROWS + 2 + headroom))
    val left = (size.width - px * COLS) / 2f
    val top = (size.height - px * (ROWS + headroom)) / 2f + px * (0.5f + headroom)
    fun cell(c: Int, r: Int, w: Int = 1, h: Int = 1, dy: Float = 0f) {
        drawRect(color, Offset(left + c * px, top + (r + dy) * px), Size(w * px + 0.5f, h * px + 0.5f))
    }
    val center = Offset(left + COLS * px / 2f, top + ROWS * px / 2f + bob * px)
    rotate(spin, center) {
        translate(top = bob * px) {
            // Body.
            cell(2, 0, 10, 7)
            // Arms: the left one stays, the right one waves.
            cell(0, 3, 2, 2)
            cell(12, 3, 2, 2, dy = -armLift.coerceIn(-1f, 1f) * 1.2f)
            // Legs: the pairs step in turn while walking or dancing.
            val walking = legPhase != 0f
            val first = walking && legPhase.toInt() % 2 == 0
            val liftA = if (walking && first) 0.5f else 0f
            val liftB = if (walking && !first) 0.5f else 0f
            cell(3, 7, 1, 2, dy = -liftA)
            cell(5, 7, 1, 2, dy = -liftB)
            cell(8, 7, 1, 2, dy = -liftA)
            cell(10, 7, 1, 2, dy = -liftB)
        }
        // Eyes: two dark slits (when blinking, just a thin line).
        translate(top = bob * px) {
            val eye = Color(0xFF1B1A1F)
            if (blink) {
                drawRect(eye, Offset(left + 3 * px, top + 2.1f * px), Size(px, px * 0.35f))
                drawRect(eye, Offset(left + 10 * px, top + 2.1f * px), Size(px, px * 0.35f))
            } else {
                drawRect(eye, Offset(left + 3 * px, top + 1 * px), Size(px, px * 2))
                drawRect(eye, Offset(left + 10 * px, top + 1 * px), Size(px, px * 2))
            }
            if (hat != ClawdHat.None) drawHat(hat, left, top, px)
        }
    }
    // Little pixel z's drifting up while he sleeps.
    if (zzz > 0f) {
        val z = Color(0xFFB8C4D6).copy(alpha = (1f - zzz).coerceIn(0f, 1f) * 0.9f)
        fun letter(x: Float, y: Float, s: Float) {
            drawRect(z, Offset(x, y), Size(s * 3, s))
            drawRect(z, Offset(x + s, y + s), Size(s, s))
            drawRect(z, Offset(x, y + s * 2), Size(s * 3, s))
        }
        val s = px * 0.45f
        letter(left + COLS * px * 0.78f, top - zzz * px * 2f, s)
        letter(left + COLS * px * 0.9f, top - zzz * px * 2f - px * 1.6f, s * 0.75f)
    }
    // A heart floating up when loved.
    if (hearts > 0f) {
        val h = px * 0.9f
        val hx = left + COLS * px * 0.75f
        val hy = top - hearts * px * 2.5f
        val heart = Color(0xFFFF5A7A).copy(alpha = (1f - hearts).coerceIn(0f, 1f))
        drawRect(heart, Offset(hx, hy), Size(h, h))
        drawRect(heart, Offset(hx + h * 1.2f, hy), Size(h, h))
        drawRect(heart, Offset(hx - h * 0.1f, hy + h * 0.8f), Size(h * 2.4f, h))
        drawRect(heart, Offset(hx + h * 0.6f, hy + h * 1.6f), Size(h, h))
    }
}

/** Clawd's hats and glasses, in his grid's pixels (the body spans columns 2–11, rows 0–6). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHat(hat: ClawdHat, left: Float, top: Float, px: Float) {
    fun r(x: Float, y: Float, w: Float, h: Float, c: Color) =
        drawRect(c, Offset(left + x * px, top + y * px), Size(w * px + 0.5f, h * px + 0.5f))
    when (hat) {
        ClawdHat.None -> Unit
        ClawdHat.Party -> {
            val pink = Color(0xFFE84F9B)
            val yellow = Color(0xFFFFD54A)
            r(4.5f, -1f, 5f, 1f, pink)
            r(5.5f, -2f, 3f, 1f, pink)
            r(6.5f, -3f, 1f, 1f, pink)
            r(5.5f, -1f, 1f, 1f, yellow)
            r(7.5f, -1f, 1f, 1f, yellow)
            r(6.5f, -2f, 1f, 1f, yellow)
            r(6.3f, -3.9f, 1.4f, 1f, yellow)
        }
        ClawdHat.Crown -> {
            val gold = Color(0xFFF5C542)
            r(3.5f, -1f, 7f, 1f, gold)
            r(3.5f, -2f, 1f, 1f, gold)
            r(6.5f, -2.3f, 1f, 1.3f, gold)
            r(9.5f, -2f, 1f, 1f, gold)
            r(6.5f, -1f, 1f, 1f, Color(0xFFE53950))
        }
        ClawdHat.Beanie -> {
            val blue = Color(0xFF4A7BD0)
            r(2f, -0.3f, 10f, 1.1f, Color(0xFF3A63A8))
            r(3f, -1.6f, 8f, 1.3f, blue)
            r(4.5f, -2.4f, 5f, 0.8f, blue)
            r(6.4f, -3.3f, 1.2f, 1f, Color.White)
        }
        ClawdHat.Cap -> {
            val red = Color(0xFFE0443E)
            r(2.5f, -1.4f, 9f, 1.6f, red)
            r(4f, -2.1f, 6f, 0.7f, red)
            r(10f, -0.2f, 3.6f, 0.6f, Color(0xFFB2332E))
        }
        ClawdHat.Bow -> {
            val pink = Color(0xFFFF6FA8)
            r(8f, -1.7f, 1.6f, 1.6f, pink)
            r(10.4f, -1.7f, 1.6f, 1.6f, pink)
            r(9.6f, -1.3f, 0.8f, 0.8f, Color(0xFFD94785))
        }
        ClawdHat.Headphones -> {
            val band = Color(0xFF2E2E36)
            r(1.6f, -1.3f, 10.8f, 0.6f, band)
            r(1.6f, -1.3f, 0.6f, 2f, band)
            r(11.8f, -1.3f, 0.6f, 2f, band)
            r(1.1f, 0.5f, 1.4f, 2.2f, band)
            r(11.5f, 0.5f, 1.4f, 2.2f, band)
            r(1.5f, 1.2f, 0.5f, 0.8f, Color(0xFF6FD3FF))
            r(12f, 1.2f, 0.5f, 0.8f, Color(0xFF6FD3FF))
        }
        ClawdHat.Sunglasses -> {
            val black = Color(0xFF111114)
            r(2.6f, 0.8f, 2.2f, 1.8f, black)
            r(9.2f, 0.8f, 2.2f, 1.8f, black)
            r(4.8f, 1.2f, 4.4f, 0.45f, black)
            r(2.9f, 1f, 0.5f, 0.5f, Color.White.copy(alpha = 0.7f))
            r(9.5f, 1f, 0.5f, 0.5f, Color.White.copy(alpha = 0.7f))
        }
    }
}
