package dev.hearth.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    /**
     * ZENITH 21's own picture instead of the sky with arcs and fireworks: a clear sky over a sun on the
     * horizon and three ridges ("style": "horizon" in the update file; older apps ignore it).
     */
    val horizon: Boolean = false,
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
            glowB = Color(0xFF8FE3FF),
            accent = Color(0xFF62C8FF),
            label = "H O R I Z O N T",
            os = "${ClaudeOs.NAME} 8.0",
            aurora = AuroraLook.Eis,
            stars = false,
            fireworks = false,
            horizon = true,
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
                horizon = o.optString("style").equals("horizon", ignoreCase = true),
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
    if (spec.horizon) {
        drawHorizonCard(spec, charge)
        return
    }
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

/**
 * ZENITH 21's card: a clear sky – night overhead, the light of the day on the horizon – a sun just
 * rising behind three ridges, a few faint stars. Quiet where the 20 had arcs and fireworks.
 * [charge] (0..1, the Z held) brightens the sun.
 */
internal fun DrawScope.drawHorizonCard(spec: ZenithCardSpec, charge: Float) {
    val w = size.width
    val h = size.height
    val night = spec.bg.first()
    val mid = spec.bg[spec.bg.size / 2]
    val light = lerp(spec.bg.last(), spec.glowB, 0.6f)
    val horizonY = h * 0.80f
    // The sky: night overhead, the light of the day lying on the horizon.
    drawRect(Brush.verticalGradient(listOf(night, mid, light), startY = 0f, endY = horizonY))
    // A few faint stars in the dark of the upper sky.
    val r = Random(21)
    repeat(22) {
        val x = r.nextFloat() * w
        val y = r.nextFloat() * horizonY * 0.5f
        drawCircle(Color.White.copy(alpha = 0.16f + 0.4f * r.nextFloat()), (0.5f + r.nextFloat() * 1.1f) * density, Offset(x, y))
    }
    // The sun, low on the right, and the haze around it.
    val sun = Offset(w * 0.76f, horizonY - w * 0.006f)
    val haze = w * 0.62f
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.5f + 0.25f * charge), spec.glowB.copy(alpha = 0.32f + 0.12f * charge), Color.Transparent),
            center = sun,
            radius = haze,
        ),
        radius = haze,
        center = sun,
    )
    drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.036f, center = sun)
    // A band of light along the horizon.
    val band = h * 0.11f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent),
            startY = horizonY - band,
            endY = horizonY + band * 0.4f,
        ),
        topLeft = Offset(0f, horizonY - band),
        size = Size(w, band * 1.4f),
    )
    // Three ridges, each darker than the one behind it.
    fun ridge(base: Float, amp: Float, phase: Float, top: Color, bottom: Color) {
        val path = Path()
        path.moveTo(0f, h)
        val steps = 72
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val twoPi = (2.0 * PI).toFloat()
            val y = base - amp * (
                0.55f * sin((t * 1.3f + phase) * twoPi) +
                    0.30f * sin((t * 2.9f + phase * 1.7f) * twoPi) +
                    0.15f * sin((t * 6.1f + phase * 0.6f) * twoPi)
                )
            path.lineTo(w * t, y)
        }
        path.lineTo(w, h)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = base - amp, endY = h))
    }
    ridge(horizonY + h * 0.012f, h * 0.045f, 0.10f, lerp(light, mid, 0.55f), lerp(mid, night, 0.55f))
    ridge(h * 0.87f, h * 0.05f, 0.55f, lerp(mid, night, 0.5f), night)
    ridge(h * 0.94f, h * 0.035f, 0.90f, lerp(night, Color.Black, 0.3f), Color.Black.copy(alpha = 0.95f))
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
