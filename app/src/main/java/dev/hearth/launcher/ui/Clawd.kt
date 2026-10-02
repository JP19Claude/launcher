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
 * when dancing, flips when tickled, and floats hearts when loved. Without animations (or on
 * the always-on display) he simply stands.
 */
@Composable
fun Clawd(
    modifier: Modifier = Modifier,
    mood: ClawdMood = ClawdMood.Idle,
    color: Color = ClawdColor,
    animate: Boolean = LocalSettings.current.animations,
) {
    if (!animate) {
        Canvas(modifier) { drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f) }
        return
    }
    val t = rememberInfiniteTransition(label = "clawd")
    // One clock for everything: 0..1 over 2.4 s.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "clawdClock")
    Canvas(modifier) {
        val phase = clock * 2f * Math.PI.toFloat()
        // A blink now and then: the eyes close for a moment near the end of each loop.
        val blink = mood != ClawdMood.Love && clock in 0.92f..0.97f
        when (mood) {
            ClawdMood.Idle -> drawClawd(color, blink, bob = sin(phase) * 0.25f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f)
            ClawdMood.Thinking -> drawClawd(color, blink, bob = abs(sin(phase * 4f)) * -0.4f, legPhase = clock * 8f, armLift = 0f, spin = 0f, hearts = 0f)
            ClawdMood.Dance -> drawClawd(color, blink = false, bob = abs(sin(phase * 3f)) * -1.4f, legPhase = clock * 12f, armLift = sin(phase * 3f), spin = 0f, hearts = 0f)
            ClawdMood.Flip -> drawClawd(color, blink = true, bob = -abs(sin(phase)) * 1.2f, legPhase = 0f, armLift = 1f, spin = clock * 360f, hearts = 0f)
            ClawdMood.Love -> drawClawd(color, blink = true, bob = sin(phase) * 0.3f, legPhase = 0f, armLift = 0.5f, spin = 0f, hearts = clock)
            ClawdMood.Wave -> drawClawd(color, blink, bob = 0f, legPhase = 0f, armLift = sin(phase * 4f), spin = 0f, hearts = 0f)
        }
    }
}

/**
 * Draws Clawd centered in the canvas, keeping his proportions.
 * [bob] moves him up and down (in pixels of his grid), [legPhase] makes the legs step,
 * [armLift] raises the right arm (−1..1), [spin] turns him, [hearts] (0..1) lets a heart rise.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawd(
    color: Color,
    blink: Boolean,
    bob: Float,
    legPhase: Float,
    armLift: Float,
    spin: Float,
    hearts: Float,
) {
    // Room above for a jump or a heart, so he never leaves the canvas.
    val px = minOf(size.width / COLS, size.height / (ROWS + 2))
    val left = (size.width - px * COLS) / 2f
    val top = (size.height - px * ROWS) / 2f + px * 0.5f
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
        }
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
