package dev.hearth.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import dev.hearth.launcher.data.AuroraLook
import dev.hearth.launcher.data.ClaudeOs
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A version's card as data – the way ColorOS shows what you are about to install: the newest
 * release carries its card in the update file (versions.json, "card"), and the Software-Update of
 * any installed ZENITH from 20.0 on draws it before the update is installed. The colors are "#RRGGBB".
 */
class ZenithCardSpec(
    /** The card's ground, from the top left to the bottom right (two to four colors). */
    val bg: List<Color>,
    /** A glow high on the left and one low on the right. */
    val glowA: Color,
    val glowB: Color,
    /** The number, the Z's edge light, the arcs and the label. */
    val accent: Color,
    /** A word under the version ("J U B I L Ä U M"); "" for none. */
    val label: String = "",
    /** The ClaudeOS under the version ("ClaudeOS 7.0"); "" for the running one. */
    val os: String = "",
    /** The aurora moving in the card (null for none). */
    val aurora: AuroraLook? = null,
    val stars: Boolean = true,
    val fireworks: Boolean = true,
) {
    companion object {
        /** ZENITH 20's own card, built in – it also shows when the update file carries none. */
        val Jubilee = ZenithCardSpec(
            bg = listOf(Color(0xFF080612), Color(0xFF241506), Color(0xFFB8780F)),
            glowA = Color(0xFF7C5CFF),
            glowB = ZenithGold,
            accent = ZenithGold,
            label = "J U B I L Ä U M",
            os = "${ClaudeOs.NAME} 7.0",
            aurora = AuroraLook.Gold,
        )

        /** ZENITH 21's own card, built in – deep blue into the light of the horizon. */
        val Horizon = ZenithCardSpec(
            bg = listOf(Color(0xFF040A1C), Color(0xFF0B2468), Color(0xFF2F7BFF)),
            glowA = Color(0xFF9A6BFF),
            glowB = Color(0xFF3FD0FF),
            accent = Color(0xFF62C8FF),
            label = "H O R I Z O N T",
            os = "${ClaudeOs.NAME} 8.0",
            aurora = AuroraLook.Eis,
        )

        private fun color(text: String): Color = Color(android.graphics.Color.parseColor(text.trim()))

        /** The card an update file carries, or null if there is none or it can't be read. */
        fun parse(json: String): ZenithCardSpec? = runCatching {
            if (json.isBlank()) return@runCatching null
            val o = JSONObject(json)
            val list = o.getJSONArray("bg")
            val bg = (0 until list.length()).map { color(list.getString(it)) }
            if (bg.size < 2) return@runCatching null
            val accent = o.optString("accent").takeIf { it.isNotBlank() }?.let(::color) ?: lerp(bg.last(), Color.White, 0.4f)
            ZenithCardSpec(
                bg = bg,
                glowA = o.optString("glowA").takeIf { it.isNotBlank() }?.let(::color) ?: lerp(bg.first(), Color.White, 0.35f),
                glowB = o.optString("glowB").takeIf { it.isNotBlank() }?.let(::color) ?: accent,
                accent = accent,
                label = o.optString("label"),
                os = o.optString("os"),
                aurora = AuroraLook.entries.firstOrNull { it.name.equals(o.optString("aurora"), ignoreCase = true) },
                stars = o.optBoolean("stars", true),
                fireworks = o.optBoolean("fireworks", true),
            )
        }.getOrNull()
    }
}

/**
 * Draws [spec]'s card: the ground, a glow high left and low right, a pale disc rising from below,
 * arcs of light, stars and three little fireworks frozen in the sky. [charge] (0..1, the Z held)
 * brightens it.
 */
internal fun DrawScope.drawZenithSpecCard(spec: ZenithCardSpec, charge: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(spec.bg, start = Offset.Zero, end = Offset(w, h)))
    drawRect(Brush.radialGradient(listOf(spec.glowA.copy(alpha = 0.26f + 0.1f * charge), Color.Transparent), center = Offset(0f, 0f), radius = w * 0.95f))
    drawRect(Brush.radialGradient(listOf(spec.glowB.copy(alpha = 0.38f + 0.12f * charge), Color.Transparent), center = Offset(w, h), radius = w * 1.05f))
    val pale = lerp(spec.accent, Color.White, 0.7f)
    drawCircle(
        Brush.verticalGradient(listOf(pale.copy(alpha = 0.2f + 0.08f * charge), spec.accent.copy(alpha = 0.05f)), startY = h * 0.55f, endY = h),
        radius = w * 0.8f,
        center = Offset(w * 0.62f, h * 1.1f),
    )
    val arc = Stroke(width = w * 0.004f)
    drawCircle(lerp(spec.accent, Color.White, 0.6f).copy(alpha = 0.22f + 0.2f * charge), radius = w * 1.05f, center = Offset(-w * 0.32f, h * 0.62f), style = arc)
    drawCircle(Color.White.copy(alpha = 0.12f + 0.15f * charge), radius = w * 0.82f, center = Offset(w * 1.12f, h * 0.98f), style = arc)
    drawCircle(lerp(spec.glowA, Color.White, 0.4f).copy(alpha = 0.2f + 0.3f * charge), radius = w * 0.62f, center = Offset(w * 0.06f, h * 0.1f), style = arc)
    if (spec.stars) {
        val r = Random(20)
        for (i in 0 until 46) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h * 0.8f
            val dot = 0.6f + r.nextFloat() * 1.6f
            drawCircle(Color.White.copy(alpha = 0.25f + 0.5f * r.nextFloat()), dot * density, Offset(x, y))
        }
    }
    if (spec.fireworks) {
        listOf(
            Triple(Offset(w * 0.16f, h * 0.2f), 0.07f, spec.accent),
            Triple(Offset(w * 0.86f, h * 0.14f), 0.09f, pale),
            Triple(Offset(w * 0.82f, h * 0.55f), 0.05f, lerp(spec.glowA, Color.White, 0.5f)),
        ).forEach { (c, k, color) ->
            for (i in 0 until 12) {
                val a = i * 30.0 * PI / 180.0
                val len = w * k
                drawLine(
                    color.copy(alpha = 0.55f + 0.3f * charge),
                    c + Offset(cos(a).toFloat() * len * 0.45f, sin(a).toFloat() * len * 0.45f),
                    c + Offset(cos(a).toFloat() * len, sin(a).toFloat() * len),
                    strokeWidth = w * 0.004f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The card a version has of its own, built in: ZENITH 21's, ZENITH 20's (the jubilee), none before. */
internal fun zenithBuiltInCard(version: String): ZenithCardSpec? = when {
    versionNumber(version) >= 21f -> ZenithCardSpec.Horizon
    versionNumber(version) >= 20f -> ZenithCardSpec.Jubilee
    else -> null
}

/**
 * The card to draw for [version]: with the jubilee's theme kept, the jubilee's whatever the version; else the one the
 * update file carries ([spec]), else the version's own built in.
 */
internal fun cardFor(version: String, theme: dev.hearth.launcher.data.ZenithTheme, spec: ZenithCardSpec?): ZenithCardSpec? =
    if (theme == dev.hearth.launcher.data.ZenithTheme.Jubilee && versionNumber(version) >= 20f) ZenithCardSpec.Jubilee else spec ?: zenithBuiltInCard(version)

/** The color of the system cards and the Z for [version] in [theme]. */
internal fun zenithAccent(version: String, theme: dev.hearth.launcher.data.ZenithTheme): Color =
    cardFor(version, theme, null)?.accent ?: ZenithGreen
