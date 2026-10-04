package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.hearth.launcher.data.ClawdMood
import kotlin.random.Random

/*
 * OMEGA UI 18 hotfix – Claude Mythos: the Clawds take over. Giant ones behind the home screen,
 * small ones peeking in from its edges and sitting on the dock, little crowds on every widget,
 * sheet and menu.
 */

/** The colors of the Clawds that take over: Claude's, gold, ruby, Galaxy blue, mint, lilac. */
internal val MythosColors = listOf(
    ClawdColor,
    Color(0xFFD4AF37),
    Color(0xFFE5243F),
    Color(0xFF3D7BFF),
    Color(0xFF8FE3C0),
    Color(0xFFC6A8FF),
)

private val MythosMoods = listOf(ClawdMood.Idle, ClawdMood.Wave, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Jump, ClawdMood.Blush)

/** A Clawd of [height] (his width follows), in [color], doing [mood]. */
@Composable
private fun MythosClawd(height: Dp, color: Color, mood: ClawdMood, modifier: Modifier = Modifier, mirrored: Boolean = false, fade: Float = 1f) {
    Clawd(
        modifier
            .size(width = height * (14f / 11f), height = height)
            .graphicsLayer {
                if (mirrored) scaleX = -1f
                alpha = fade
            },
        mood = mood,
        color = color,
    )
}

/**
 * Claude Mythos, behind the home screen's apps and widgets: two giant Clawds – one rising from
 * the left edge, one leaning in from the right – and a big one over the middle.
 */
@Composable
internal fun BoxScope.MythosGiants() {
    if (!LocalSettings.current.mythos) return
    MythosClawd(
        210.dp,
        ClawdColor,
        ClawdMood.Wave,
        Modifier.align(Alignment.CenterStart).offset(x = (-95).dp, y = 40.dp),
        fade = 0.6f,
    )
    MythosClawd(
        170.dp,
        Color(0xFFE5243F),
        ClawdMood.Love,
        Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = 150.dp),
        mirrored = true,
        fade = 0.55f,
    )
    MythosClawd(
        120.dp,
        Color(0xFFD4AF37),
        ClawdMood.Dance,
        Modifier.align(Alignment.BottomCenter).offset(y = (-230).dp),
        fade = 0.45f,
    )
}

/**
 * Claude Mythos, over the home screen: small Clawds peeking in from both edges, a tiny one in
 * the corner up top, and a row of them sitting on the dock ([ground] is its top edge, in px).
 */
@Composable
internal fun BoxScope.MythosCrowd(ground: Float) {
    if (!LocalSettings.current.mythos) return
    val density = LocalDensity.current
    // Peeking in from the left and right edges, half outside.
    MythosClawd(34.dp, Color(0xFF3D7BFF), ClawdMood.Wave, Modifier.align(Alignment.CenterStart).offset(x = (-14).dp, y = (-40).dp).zIndex(3f))
    MythosClawd(28.dp, Color(0xFF8FE3C0), ClawdMood.Surprised, Modifier.align(Alignment.CenterEnd).offset(x = 12.dp, y = 90.dp).zIndex(3f), mirrored = true)
    MythosClawd(18.dp, Color(0xFFC6A8FF), ClawdMood.Blush, Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = 64.dp).zIndex(3f), mirrored = true)
    // Sitting on the dock, big and small.
    if (ground > 0f) {
        val top = with(density) { ground.toDp() }
        BoxWithConstraints(Modifier.matchParentSize()) {
            val seats = listOf(0.08f to 22.dp, 0.3f to 38.dp, 0.62f to 16.dp, 0.86f to 28.dp)
            seats.forEachIndexed { i, (f, h) ->
                MythosClawd(
                    h,
                    MythosColors[(i + 1) % MythosColors.size],
                    MythosMoods[i % MythosMoods.size],
                    Modifier.offset(x = maxWidth * f, y = top - h * 0.82f).zIndex(3f),
                    mirrored = i % 2 == 1,
                )
            }
        }
    }
}

/**
 * Claude Mythos: a little crowd of Clawds – big and small – sitting on the top rim of a panel
 * (a widget, a glass sheet). [seed] picks where they sit; the panel must not clip.
 */
@Composable
internal fun BoxScope.MythosPerch(seed: Int, biggest: Dp = 32.dp) {
    if (!LocalSettings.current.mythos) return
    val seats = remember(seed) {
        val r = Random(seed)
        val n = 2 + r.nextInt(2)
        List(n) { i ->
            val f = (i + 0.2f + r.nextFloat() * 0.6f) / n
            Triple(f.coerceIn(0.04f, 0.82f), 0.5f + r.nextFloat() * 0.5f, r.nextInt(100))
        }
    }
    BoxWithConstraints(Modifier.matchParentSize().zIndex(4f)) {
        seats.forEach { (f, scale, pick) ->
            val h = biggest * scale
            MythosClawd(
                h,
                MythosColors[pick % MythosColors.size],
                MythosMoods[pick % MythosMoods.size],
                Modifier.offset(x = maxWidth * f, y = -h * 0.8f),
                mirrored = pick % 2 == 0,
            )
        }
    }
}

/** Claude Mythos: a row of Clawds, big and small, for headers of menus and lists. */
@Composable
internal fun MythosRow(biggest: Dp = 26.dp) {
    if (!LocalSettings.current.mythos) return
    Row {
        listOf(1f, 0.6f, 0.85f).forEachIndexed { i, k ->
            if (i > 0) Spacer(Modifier.width(3.dp))
            Box(Modifier.align(Alignment.Bottom)) {
                MythosClawd(biggest * k, MythosColors[(i * 2 + 1) % MythosColors.size], MythosMoods[(i + 1) % MythosMoods.size])
            }
        }
    }
}
