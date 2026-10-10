package dev.hearth.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.Perfume
import dev.hearth.launcher.data.PerfumeLook
import dev.hearth.launcher.data.PerfumeShelf

/*
 * ZENITH 19.7: a picture for every perfume – the bottle from the owner's photo, or, when there's
 * none, a drawn bottle in the colour of its scent. Next to today's choice, in the widget and in
 * the collection.
 */

/** The colour of a perfume's scent family and notes, for its drawn bottle. */
internal fun perfumeTone(p: Perfume): Color {
    val text = (p.family + " " + p.notes.joinToString(" ")).lowercase()
    fun has(vararg words: String) = words.any { it in text }
    return when {
        has("orient", "vanille", "süß", "gourmand", "amber") -> Color(0xFFD9902F)
        has("blumig", "blüte", "rose", "jasmin", "pfingstrose", "veilchen") -> Color(0xFFE0709A)
        has("holz", "würzig", "leder", "zeder", "sandel", "tabak", "oud") -> Color(0xFF9A6B3F)
        has("frisch", "zitrus", "aquatisch", "marin", "bergamotte", "minze") -> Color(0xFF3FB6C9)
        has("aromatisch", "grün", "lavendel", "kräuter") -> Color(0xFF4FA36A)
        has("fruchtig", "apfel", "beere", "pfirsich") -> Color(0xFFE8765A)
        else -> Color(0xFF8E6BFF)
    }
}

/** A flacon, drawn: glass in the scent's colour, a light streak, a label and a silver cap. */
@Composable
internal fun PerfumeBottle(perfume: Perfume, modifier: Modifier = Modifier) {
    val tone = perfumeTone(perfume)
    Canvas(modifier.background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.35f), Color(0xFF0B0F12))))) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.5f
        val bodyH = h * 0.46f
        val left = (w - bodyW) / 2f
        val top = h * 0.4f
        // The neck and the cap.
        drawRect(tone.copy(alpha = 0.75f), Offset(w * 0.43f, top - h * 0.07f), Size(w * 0.14f, h * 0.08f))
        drawRoundRect(Color(0xFFD8DDE0), Offset(w * 0.37f, top - h * 0.2f), Size(w * 0.26f, h * 0.14f), CornerRadius(w * 0.03f))
        // The glass.
        drawRoundRect(
            Brush.verticalGradient(listOf(tone.copy(alpha = 0.95f), tone.copy(alpha = 0.55f)), startY = top, endY = top + bodyH),
            Offset(left, top),
            Size(bodyW, bodyH),
            CornerRadius(w * 0.08f),
        )
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(left + bodyW * 0.1f, top + bodyH * 0.07f), Size(bodyW * 0.1f, bodyH * 0.78f), CornerRadius(w * 0.03f))
        // The label.
        val labelTop = top + bodyH * 0.36f
        drawRoundRect(Color.White.copy(alpha = 0.88f), Offset(left + bodyW * 0.22f, labelTop), Size(bodyW * 0.6f, bodyH * 0.3f), CornerRadius(w * 0.02f))
        drawRect(Color(0xFF2B2A27).copy(alpha = 0.55f), Offset(left + bodyW * 0.3f, labelTop + bodyH * 0.08f), Size(bodyW * 0.44f, bodyH * 0.03f))
        drawRect(Color(0xFF2B2A27).copy(alpha = 0.35f), Offset(left + bodyW * 0.36f, labelTop + bodyH * 0.16f), Size(bodyW * 0.32f, bodyH * 0.025f))
    }
}

/**
 * The perfume's picture: its photo (the bottle cut out of it) if there is one – a drawn bottle
 * in the colour of its scent if not, so there's always something to look at.
 */
@Composable
internal fun PerfumePicture(perfume: Perfume, modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val bitmap = remember(perfume.id, perfume.photo, perfume.photoAt) {
        if (perfume.photo) PerfumeShelf.loadPhoto(perfume.id)?.asImageBitmap() else null
    }
    // A product shot from the web is shown whole on light, like in a shop; a crop of a photo fills the frame.
    Box(modifier.clip(RoundedCornerShape(corner)).background(if (perfume.photoWeb) Color(0xFFF4F1EC) else Color(0xFF0B0F12))) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = perfume.title,
                contentScale = if (perfume.photoWeb) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PerfumeBottle(perfume, Modifier.fillMaxSize())
        }
    }
}

/**
 * Who stands beside the advice: Clawd in his mood, the ZENITH Z (it dims while it thinks), both side
 * by side – or nobody, when both are switched off.
 */
@Composable
internal fun PerfumeHelperFigure(look: PerfumeLook, mood: ClawdMood, modifier: Modifier = Modifier) {
    if (!look.clawd && !look.zenith) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (look.clawd) Clawd(Modifier.weight(1f).fillMaxHeight(), mood = mood)
        if (look.zenith) {
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width * 0.9f
                drawZenithZ((size.width - w) / 2f, (size.height - w * ZenithZAspect) / 2f, w, light = if (mood == ClawdMood.Thinking) 0.55f else 1f)
            }
        }
    }
}
