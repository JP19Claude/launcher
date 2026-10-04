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
import androidx.compose.ui.graphics.drawscope.scale
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdOutfit
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
    outfit: ClawdOutfit = LocalSettings.current.clawdOutfit,
) {
    val skin = LocalSettings.current.clawdSkin
    // OMEGA UI 17.1: the color of his clothes and the Ω on his chest, for every Clawd drawn.
    ClawdWardrobe.cloth = LocalSettings.current.clawdCloth.color
    ClawdWardrobe.omega = LocalSettings.current.clawdOmega
    ClawdWardrobe.stars = color == null && skin == ClawdSkin.Galaxy
    // OMEGA UI 17.5, Clawd Illumination: a halo over every Clawd.
    val halo = LocalSettings.current.clawdHalo
    if (!animate) {
        val body = color ?: skin.color
        Canvas(modifier) {
            drawClawdPose(mood, body, hat, outfit)
            if (halo) drawClawdHalo(hat, outfit, 0f)
        }
        return
    }
    val t = rememberInfiniteTransition(label = "clawd")
    // One clock for everything: 0..1 over 2.4 s.
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "clawdClock")
    Canvas(modifier) {
        val body = color ?: when (skin) {
            ClawdSkin.Rainbow -> rainbow(clock)
            ClawdSkin.Galaxy -> galaxy(clock)
            else -> skin.color
        }
        val phase = clock * 2f * Math.PI.toFloat()
        // A blink now and then: the eyes close for a moment near the end of each loop.
        val blink = mood != ClawdMood.Love && clock in 0.92f..0.97f
        when (mood) {
            ClawdMood.Idle -> drawClawd(body, blink, bob = sin(phase) * 0.25f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Thinking -> drawClawd(body, blink, bob = abs(sin(phase * 4f)) * -0.4f, legPhase = clock * 8f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Dance -> drawClawd(body, blink = false, bob = abs(sin(phase * 3f)) * -1.4f, legPhase = clock * 12f, armLift = sin(phase * 3f), spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Flip -> drawClawd(body, blink = true, bob = -abs(sin(phase)) * 1.2f, legPhase = 0f, armLift = 1f, spin = clock * 360f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Love -> drawClawd(body, blink = true, bob = sin(phase) * 0.3f, legPhase = 0f, armLift = 0.5f, spin = 0f, hearts = clock, hat = hat, outfit = outfit)
            ClawdMood.Wave -> drawClawd(body, blink, bob = 0f, legPhase = 0f, armLift = sin(phase * 4f), spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Sleep -> drawClawd(body, blink = true, bob = sin(phase) * 0.15f, legPhase = 0f, armLift = -0.4f, spin = 0f, hearts = 0f, zzz = clock, hat = hat, outfit = outfit)
            ClawdMood.Jump -> drawClawd(body, blink = false, bob = -abs(sin(phase * 2f)) * 2.4f, legPhase = if (abs(sin(phase * 2f)) > 0.3f) 1f else 0f, armLift = abs(sin(phase * 2f)), spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
            ClawdMood.Blush -> drawClawd(body, blink = clock in 0.5f..0.56f, bob = sin(phase) * 0.2f, legPhase = 0f, armLift = -0.2f, spin = sin(phase) * 4f, hearts = 0f, hat = hat, outfit = outfit, blush = 1f)
            ClawdMood.Dizzy -> drawClawd(body, blink = false, bob = sin(phase * 2f) * 0.3f, legPhase = 0f, armLift = sin(phase * 3f) * 0.6f, spin = sin(phase * 3f) * 14f, hearts = 0f, hat = hat, outfit = outfit, dizzy = true)
            ClawdMood.Surprised -> drawClawd(body, blink = false, bob = -abs(sin(phase * 4f)) * 0.6f, legPhase = 0f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit, wide = true)
        }
        if (halo) drawClawdHalo(hat, outfit, sin(phase) * 0.3f - 0.2f)
    }
}

/**
 * OMEGA UI 17.5, Clawd Illumination: a glowing golden halo floating over Clawd's head (over
 * his hat, if he wears one), [bob] in pixels of his grid.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawdHalo(hat: ClawdHat, outfit: ClawdOutfit, bob: Float) {
    val headroom = if (hat.tall || outfit.tall) 2f else 0f
    val px = minOf(size.width / COLS, size.height / (ROWS + 2 + headroom))
    val left = (size.width - px * COLS) / 2f
    val top = (size.height - px * (ROWS + headroom)) / 2f + px * (0.5f + headroom)
    val cx = left + COLS * px / 2f
    val cy = top - px * (1.1f + headroom) + bob * px
    val w = px * 6.4f
    val h = px * 1.4f
    // A soft glow, then the ring itself.
    drawOval(
        Color(0xFFFFD86B).copy(alpha = 0.3f),
        topLeft = Offset(cx - w / 2f - px * 0.4f, cy - h / 2f - px * 0.3f),
        size = Size(w + px * 0.8f, h + px * 0.6f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(px * 1.2f),
    )
    drawOval(
        Color(0xFFFFEDB0),
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        style = androidx.compose.ui.graphics.drawscope.Stroke(px * 0.5f),
    )
}

/**
 * What Clawd wears beyond the outfit itself, chosen in the settings: the color of his clothes
 * (null: each outfit's own) and whether the OMEGA Ω sits on his chest.
 */
object ClawdWardrobe {
    @Volatile var cloth: Color? = null
    @Volatile var omega: Boolean = false
    /** The Galaxy skin: little stars on his body. */
    @Volatile var stars: Boolean = false
}

/** The Galaxy skin's color at [t] (0..1): indigo, violet and magenta drifting into each other. */
fun galaxy(t: Float): Color {
    val stops = listOf(Color(0xFF3B3FC4), Color(0xFF6A4BE0), Color(0xFFB04CC8), Color(0xFF4B5FE8), Color(0xFF3B3FC4))
    val x = (t.coerceIn(0f, 1f)) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[i], stops[i + 1], x - i)
}

/** The rainbow skin's color at [t] (0..1): soft, so the eyes stay visible. */
fun rainbow(t: Float): Color = Color.hsv((t * 360f) % 360f, 0.55f, 0.95f)

/** Clawd standing still in a pose that shows his [mood] (for still pictures and widgets). */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawdPose(mood: ClawdMood, color: Color, hat: ClawdHat, outfit: ClawdOutfit = ClawdOutfit.None) {
    when (mood) {
        ClawdMood.Idle -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 0f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Thinking -> drawClawd(color, blink = false, bob = -0.2f, legPhase = 2f, armLift = 0.3f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Dance -> drawClawd(color, blink = false, bob = -0.9f, legPhase = 1f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Flip -> drawClawd(color, blink = true, bob = -0.6f, legPhase = 0f, armLift = 1f, spin = 18f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Love -> drawClawd(color, blink = true, bob = 0f, legPhase = 0f, armLift = 0.5f, spin = 0f, hearts = 0.35f, hat = hat, outfit = outfit)
        ClawdMood.Wave -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Sleep -> drawClawd(color, blink = true, bob = 0f, legPhase = 0f, armLift = -0.4f, spin = 0f, hearts = 0f, zzz = 0.45f, hat = hat, outfit = outfit)
        ClawdMood.Jump -> drawClawd(color, blink = false, bob = -1.8f, legPhase = 1f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit)
        ClawdMood.Blush -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = -0.2f, spin = 3f, hearts = 0f, hat = hat, outfit = outfit, blush = 1f)
        ClawdMood.Dizzy -> drawClawd(color, blink = false, bob = 0f, legPhase = 0f, armLift = 0.4f, spin = -10f, hearts = 0f, hat = hat, outfit = outfit, dizzy = true)
        ClawdMood.Surprised -> drawClawd(color, blink = false, bob = -0.4f, legPhase = 0f, armLift = 1f, spin = 0f, hearts = 0f, hat = hat, outfit = outfit, wide = true)
    }
}

/** Clawd in his pose inside the box at [topLeft] of [w] × [h], facing left when [mirrored]. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClawdAt(
    topLeft: Offset,
    w: Float,
    h: Float,
    mood: ClawdMood,
    color: Color,
    hat: ClawdHat,
    outfit: ClawdOutfit,
    mirrored: Boolean = false,
) {
    val full = size
    translate(left = topLeft.x, top = topLeft.y) {
        scale(
            scaleX = if (mirrored) -1f else 1f,
            scaleY = 1f,
            pivot = Offset(w / 2f, h / 2f),
        ) {
            // drawClawd fits him into the scope's size: shrink it to his box for a moment.
            drawContext.size = Size(w, h)
            drawClawdPose(mood, color, hat, outfit)
            drawContext.size = full
        }
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
    outfit: ClawdOutfit = ClawdOutfit.None,
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
            drawClawdPose(mood, color, hat, outfit)
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
    outfit: ClawdOutfit = ClawdOutfit.None,
    /** Pink cheeks (0..1). */
    blush: Float = 0f,
    /** Swirly "+" eyes. */
    dizzy: Boolean = false,
    /** Big round eyes. */
    wide: Boolean = false,
) {
    // Room above for a jump or a heart (and a hat), so he never leaves the canvas.
    val headroom = if (hat.tall || outfit.tall) 2f else 0f
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
            // Galaxy: a few stars twinkling on him.
            if (ClawdWardrobe.stars) {
                val star = Color.White.copy(alpha = 0.9f)
                listOf(4.2f to 4.4f, 8.6f to 3.6f, 6.4f to 5.9f, 10.5f to 5.4f, 2.8f to 6.2f, 5.6f to 0.5f, 9.4f to 1.0f).forEach { (x, y) ->
                    drawRect(star, Offset(left + x * px, top + y * px), Size(px * 0.35f, px * 0.35f))
                }
            }
            if (outfit != ClawdOutfit.None) drawOutfit(outfit, color, left, top, px, armDy = -armLift.coerceIn(-1f, 1f) * 1.2f, face = false)
            if (ClawdWardrobe.omega) drawOmegaBadge(left, top, px)
        }
        // Eyes: two dark slits (when blinking, just a thin line).
        translate(top = bob * px) {
            val eye = Color(0xFF1B1A1F)
            when {
                dizzy -> for (c in listOf(3f, 10f)) {
                    // A little "+" for each eye.
                    drawRect(eye, Offset(left + (c - 0.3f) * px, top + 1.6f * px), Size(px * 1.6f, px * 0.45f))
                    drawRect(eye, Offset(left + (c + 0.28f) * px, top + 1.0f * px), Size(px * 0.45f, px * 1.6f))
                }
                blink -> {
                    drawRect(eye, Offset(left + 3 * px, top + 2.1f * px), Size(px, px * 0.35f))
                    drawRect(eye, Offset(left + 10 * px, top + 2.1f * px), Size(px, px * 0.35f))
                }
                wide -> {
                    drawRect(eye, Offset(left + 2.85f * px, top + 0.6f * px), Size(px * 1.3f, px * 2.6f))
                    drawRect(eye, Offset(left + 9.85f * px, top + 0.6f * px), Size(px * 1.3f, px * 2.6f))
                    // A glint, so they look round and surprised.
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(left + 3.15f * px, top + 0.9f * px), Size(px * 0.4f, px * 0.4f))
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(left + 10.15f * px, top + 0.9f * px), Size(px * 0.4f, px * 0.4f))
                }
                else -> {
                    drawRect(eye, Offset(left + 3 * px, top + 1 * px), Size(px, px * 2))
                    drawRect(eye, Offset(left + 10 * px, top + 1 * px), Size(px, px * 2))
                }
            }
            if (blush > 0f) {
                val cheek = Color(0xFFFF7A9A).copy(alpha = 0.75f * blush.coerceIn(0f, 1f))
                drawRect(cheek, Offset(left + 2.3f * px, top + 3.3f * px), Size(px * 1.5f, px * 0.7f))
                drawRect(cheek, Offset(left + 10.2f * px, top + 3.3f * px), Size(px * 1.5f, px * 0.7f))
            }
            if (outfit != ClawdOutfit.None) drawOutfit(outfit, color, left, top, px, armDy = 0f, face = true)
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
        ClawdHat.Wizard -> {
            val purple = Color(0xFF6A4BC4)
            r(3.5f, -1f, 7f, 1f, purple)
            r(4.5f, -2f, 5f, 1f, purple)
            r(5.5f, -3f, 3f, 1f, purple)
            r(6.5f, -3.9f, 1.2f, 0.9f, purple)
            r(5.6f, -1.9f, 0.7f, 0.7f, Color(0xFFFFD54A))
            r(8f, -1f, 0.6f, 0.6f, Color(0xFFFFD54A))
        }
        ClawdHat.Chef -> {
            val white = Color(0xFFF7F7F7)
            r(4f, -1.4f, 6f, 1.5f, white)
            r(3.4f, -2.7f, 2.6f, 1.5f, white)
            r(5.7f, -3.2f, 2.6f, 1.8f, white)
            r(8f, -2.7f, 2.6f, 1.5f, white)
            r(4f, -0.4f, 6f, 0.3f, Color(0xFFDADADA))
        }
        ClawdHat.Halo -> {
            val gold = Color(0xFFFFD86B)
            r(4.5f, -2f, 5f, 0.45f, gold)
            r(3.8f, -1.6f, 0.75f, 0.45f, gold)
            r(9.45f, -1.6f, 0.75f, 0.45f, gold)
            r(4.5f, -1.2f, 5f, 0.45f, gold)
        }
        ClawdHat.Horns -> {
            val red = Color(0xFFD7263D)
            r(2.4f, -1.1f, 1.1f, 1.1f, red)
            r(2f, -2f, 0.8f, 0.9f, red)
            r(10.5f, -1.1f, 1.1f, 1.1f, red)
            r(11.2f, -2f, 0.8f, 0.9f, red)
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

/**
 * Clawd's outfits, in his grid's pixels: [face] = false for what goes on his body (drawn over
 * it, the right sleeve following his arm by [armDy]), true for what goes over his eyes.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutfit(
    outfit: ClawdOutfit,
    body: Color,
    left: Float,
    top: Float,
    px: Float,
    armDy: Float,
    face: Boolean,
) {
    fun r(x: Float, y: Float, w: Float, h: Float, c: Color) =
        drawRect(c, Offset(left + x * px, top + y * px), Size(w * px + 0.5f, h * px + 0.5f))
    fun sleeves(c: Color) {
        r(0f, 3f, 2f, 2f, c)
        r(12f, 3f + armDy, 2f, 2f, c)
    }
    val white = Color(0xFFF7F7F7)
    // The chosen color for the clothes, else the outfit's own.
    fun cloth(own: Color): Color = ClawdWardrobe.cloth ?: own
    if (face) {
        when (outfit) {
            ClawdOutfit.Pirate -> {
                r(2f, 0.6f, 10f, 0.35f, Color(0xFF111114))
                r(9.5f, 0.6f, 2f, 2.7f, Color(0xFF111114))
            }
            ClawdOutfit.Ninja -> {
                val red = Color(0xFFE0443E)
                r(2f, 0.1f, 10f, 0.6f, red)
                r(12f, 0.1f, 1.5f, 0.45f, red)
                r(12.6f, 0.55f, 1.1f, 0.45f, red)
            }
            ClawdOutfit.Astronaut -> {
                // The helmet's glass over his head, with a glint.
                r(1.3f, -1.2f, 11.4f, 4.6f, Color(0x4D8FD3FF))
                r(1.3f, -1.2f, 11.4f, 0.35f, Color(0x99E6F6FF))
                r(2.2f, -0.6f, 0.6f, 1.4f, Color(0x80FFFFFF))
            }
            else -> Unit
        }
        return
    }
    when (outfit) {
        ClawdOutfit.None -> Unit
        ClawdOutfit.Suit -> {
            val navy = cloth(Color(0xFF2B3A55))
            r(2f, 3.2f, 10f, 3.8f, navy)
            sleeves(navy)
            r(5.8f, 3.2f, 2.4f, 2.2f, white)
            r(6.6f, 3.4f, 0.8f, 2.8f, Color(0xFFD0342C))
            r(5.2f, 3.2f, 0.6f, 2.6f, Color(0xFF22304A))
            r(8.2f, 3.2f, 0.6f, 2.6f, Color(0xFF22304A))
            r(0f, 4.6f, 0.5f, 0.4f, white)
        }
        ClawdOutfit.Tuxedo -> {
            val black = cloth(Color(0xFF1A1A1E))
            r(2f, 3.2f, 10f, 3.8f, black)
            sleeves(black)
            r(5.6f, 3.2f, 2.8f, 3.8f, white)
            r(5.9f, 3.3f, 1f, 0.8f, black)
            r(7.1f, 3.3f, 1f, 0.8f, black)
            r(6.8f, 3.5f, 0.4f, 0.4f, black)
            r(6.85f, 4.8f, 0.3f, 0.3f, black)
            r(6.85f, 5.6f, 0.3f, 0.3f, black)
        }
        ClawdOutfit.Hero -> {
            val red = Color(0xFFD7263D)
            val yellow = Color(0xFFFFD54A)
            r(1.1f, 4.8f, 0.9f, 3.6f, red)
            r(12f, 4.8f, 0.9f, 3.6f, red)
            r(5.4f, 3.4f, 3.2f, 2.6f, yellow)
            r(6.2f, 3.8f, 1.6f, 0.4f, red)
            r(6.2f, 3.8f, 0.4f, 1f, red)
            r(6.2f, 4.5f, 1.6f, 0.4f, red)
            r(7.4f, 4.5f, 0.4f, 1f, red)
            r(6.2f, 5.2f, 1.6f, 0.4f, red)
            r(2f, 6.2f, 10f, 0.5f, yellow)
        }
        ClawdOutfit.Astronaut -> {
            val suit = Color(0xFFECECF2)
            r(2f, 3.4f, 10f, 3.6f, suit)
            sleeves(suit)
            r(4.8f, 4f, 4.4f, 1.8f, Color(0xFF3F6FD8))
            r(5.3f, 4.5f, 0.7f, 0.7f, Color(0xFFE0443E))
            r(6.6f, 4.5f, 0.7f, 0.7f, Color(0xFF4CD964))
            r(7.9f, 4.5f, 0.7f, 0.7f, Color(0xFFFFD54A))
        }
        ClawdOutfit.Doctor -> {
            r(2f, 3.3f, 10f, 3.7f, white)
            sleeves(white)
            r(6.3f, 3.3f, 1.4f, 3.7f, body)
            val gray = Color(0xFF4A4D55)
            r(4.4f, 3.3f, 0.35f, 1.7f, gray)
            r(9.25f, 3.3f, 0.35f, 1.7f, gray)
            r(4.4f, 5f, 2f, 0.35f, gray)
            r(6.1f, 4.8f, 0.8f, 0.8f, Color(0xFFB8BCC6))
            r(9.8f, 4.2f, 0.3f, 0.9f, Color(0xFF3A6FD8))
        }
        ClawdOutfit.Chef -> {
            r(3f, 3.6f, 8f, 3.4f, white)
            r(3f, 3.3f, 8f, 0.3f, Color(0xFFDADADA))
            r(5.4f, 2.9f, 3.2f, 0.6f, Color(0xFFD7263D))
            r(6.4f, 5f, 1.2f, 0.8f, Color(0xFFE5E5E5))
        }
        ClawdOutfit.Pirate -> {
            val red = cloth(Color(0xFFC8342F))
            r(2f, 3.3f, 10f, 3.7f, red)
            r(2f, 4f, 10f, 0.5f, white)
            r(2f, 5.1f, 10f, 0.5f, white)
            r(2f, 6.2f, 10f, 0.5f, white)
            r(2f, 3.3f, 10f, 0.35f, Color(0xFF3B2A20))
        }
        ClawdOutfit.Ninja -> {
            val dark = cloth(Color(0xFF24252C))
            r(2f, 3f, 10f, 4f, dark)
            sleeves(dark)
            r(2f, 5.4f, 10f, 0.45f, Color(0xFFE0443E))
        }
        ClawdOutfit.Sweater -> {
            val green = cloth(Color(0xFF2E7D4F))
            r(2f, 3.2f, 10f, 3.8f, green)
            sleeves(green)
            var x = 2f
            var up = true
            while (x < 12f) {
                r(x, if (up) 4.1f else 4.5f, 0.5f, 0.4f, white)
                x += 0.5f
                up = !up
            }
            r(3.6f, 5.4f, 0.6f, 0.6f, Color(0xFFD7263D))
            r(6.7f, 5.4f, 0.6f, 0.6f, Color(0xFFD7263D))
            r(9.8f, 5.4f, 0.6f, 0.6f, Color(0xFFD7263D))
        }
        ClawdOutfit.Scarf -> {
            val red = cloth(Color(0xFFD9433B))
            r(2f, 2.9f, 10f, 0.9f, red)
            r(9f, 3.8f, 1.3f, 2.6f, red)
            r(9f, 4.8f, 1.3f, 0.35f, white)
            r(9f, 5.6f, 1.3f, 0.35f, white)
            r(4f, 2.9f, 0.5f, 0.9f, white)
            r(7f, 2.9f, 0.5f, 0.9f, white)
        }
        ClawdOutfit.Coat -> {
            // A long coat over his body and down over the tops of his legs, collar and buttons.
            val coat = cloth(Color(0xFFB98A55))
            val shade = coat.copy(red = coat.red * 0.78f, green = coat.green * 0.78f, blue = coat.blue * 0.78f)
            r(2f, 3f, 10f, 4.6f, coat)
            sleeves(coat)
            r(2f, 7f, 2.4f, 1.1f, coat)
            r(9.6f, 7f, 2.4f, 1.1f, coat)
            r(6.8f, 3f, 0.4f, 4.6f, shade)
            r(4.6f, 2.8f, 2.2f, 0.9f, shade)
            r(7.2f, 2.8f, 2.2f, 0.9f, shade)
            r(5.9f, 4.2f, 0.5f, 0.5f, Color(0xFF2A2018))
            r(5.9f, 5.4f, 0.5f, 0.5f, Color(0xFF2A2018))
            r(7.6f, 4.2f, 0.5f, 0.5f, Color(0xFF2A2018))
            r(7.6f, 5.4f, 0.5f, 0.5f, Color(0xFF2A2018))
            r(2f, 6.1f, 10f, 0.45f, shade)
        }
        ClawdOutfit.Hoodie -> {
            val hood = cloth(Color(0xFF5B6270))
            r(2f, 3.1f, 10f, 3.9f, hood)
            sleeves(hood)
            r(5.2f, 5f, 3.6f, 1.4f, hood.copy(alpha = 0.85f))
            r(5.2f, 5f, 3.6f, 0.25f, Color.Black.copy(alpha = 0.25f))
            r(6.2f, 3.1f, 0.3f, 1.5f, white)
            r(7.5f, 3.1f, 0.3f, 1.5f, white)
            r(2f, 2.6f, 10f, 0.6f, hood)
        }
        ClawdOutfit.Raincoat -> {
            val rain = cloth(Color(0xFFFFC928))
            r(2f, 3f, 10f, 4.2f, rain)
            sleeves(rain)
            r(1.6f, -0.6f, 10.8f, 0.9f, rain)
            r(6.8f, 3f, 0.35f, 4.2f, Color.Black.copy(alpha = 0.2f))
            r(4f, 5.6f, 1.6f, 0.9f, Color.Black.copy(alpha = 0.15f))
            r(8.4f, 5.6f, 1.6f, 0.9f, Color.Black.copy(alpha = 0.15f))
        }
        ClawdOutfit.Overalls -> {
            val denim = cloth(Color(0xFF3F67A8))
            r(3.4f, 4.4f, 7.2f, 2.6f, denim)
            r(4f, 3f, 0.7f, 1.6f, denim)
            r(9.3f, 3f, 0.7f, 1.6f, denim)
            r(4.1f, 4.6f, 0.5f, 0.5f, Color(0xFFFFD54A))
            r(9.4f, 4.6f, 0.5f, 0.5f, Color(0xFFFFD54A))
            r(5.8f, 5f, 2.4f, 1.2f, denim.copy(red = denim.red * 0.85f, green = denim.green * 0.85f, blue = denim.blue * 0.85f))
            r(3.2f, 7f, 1.6f, 1.2f, denim)
            r(9.2f, 7f, 1.6f, 1.2f, denim)
        }
    }
}

/** OMEGA UI 17.1: a little ruby with a white Ω on his chest, in his grid's pixels. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOmegaBadge(left: Float, top: Float, px: Float) {
    fun r(x: Float, y: Float, w: Float, h: Float, c: Color) =
        drawRect(c, Offset(left + x * px, top + y * px), Size(w * px + 0.5f, h * px + 0.5f))
    val ruby = Color(0xFFC8102E)
    val light = Color(0xFFFF6B7D)
    // The stone: a small cut gem.
    r(5.6f, 3.5f, 2.8f, 2.4f, ruby)
    r(5.9f, 3.2f, 2.2f, 0.3f, light)
    r(5.9f, 5.9f, 2.2f, 0.3f, ruby)
    r(5.6f, 3.5f, 0.6f, 0.6f, light)
    // The Ω in white.
    val w = Color.White
    r(6.3f, 3.8f, 1.4f, 0.35f, w)
    r(6.1f, 4.1f, 0.35f, 1f, w)
    r(7.55f, 4.1f, 0.35f, 1f, w)
    r(5.9f, 5.1f, 0.65f, 0.35f, w)
    r(7.45f, 5.1f, 0.65f, 0.35f, w)
}
