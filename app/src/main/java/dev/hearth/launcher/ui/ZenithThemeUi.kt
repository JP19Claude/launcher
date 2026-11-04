package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ZenithTheme

/*
 * ZENITH 21: the system's theme – "Horizont", the new one, or the jubilee's gold of ZENITH 20 kept. The card of the
 * version (About phone, the software update), the system card of the settings and the Z take its colors.
 */

/** A theme as a small card: its ground, its label, its number. */
@Composable
internal fun ZenithThemeTile(theme: ZenithTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val card = themeCard(theme)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(card.bg))
            .border(if (selected) 3.dp else 1.dp, if (selected) card.accent else Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(card.label, color = card.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.height(26.dp))
        Text(
            when (theme) {
                ZenithTheme.Jubilee -> "20"
                ZenithTheme.Horizon -> "21"
                ZenithTheme.Meridian -> "22"
            },
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
        )
        Text(theme.label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 2)
    }
}

/** The card a theme has of its own. */
internal fun themeCard(theme: ZenithTheme): ZenithCardSpec = when (theme) {
    ZenithTheme.Meridian -> ZenithCardSpec.Meridian
    ZenithTheme.Horizon -> ZenithCardSpec.Horizon
    ZenithTheme.Jubilee -> ZenithCardSpec.Jubilee
}

/** The three tiles side by side: the newest first. */
@Composable
internal fun ZenithThemeTiles(selected: ZenithTheme, onPick: (ZenithTheme) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        ZenithTheme.entries.forEachIndexed { i, theme ->
            if (i > 0) Spacer(Modifier.width(10.dp))
            ZenithThemeTile(theme, selected == theme, Modifier.weight(1f)) { onPick(theme) }
        }
    }
}

/** Once, after the update to ZENITH 22: Meridian, the new theme – or the one of ZENITH 21 or the jubilee's kept? (Changeable later in the settings.) */
@Composable
internal fun ZenithThemeChoice(onChoose: (ZenithTheme) -> Unit) {
    var picked by remember { mutableStateOf(ZenithTheme.Meridian) }
    BackHandler { onChoose(picked) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(22.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF14171C))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ZENITH 22", color = ZenithCardSpec.Meridian.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ein neues Theme – oder ein älteres behalten?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            ZenithThemeTiles(picked) { picked = it }
            Spacer(Modifier.height(14.dp))
            Text(picked.note, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            val accent = themeCard(picked).accent
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(accent)
                    .clickable { onChoose(picked) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Übernehmen", color = Color(0xFF101418), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Später unter Einstellungen → ZENITH → Theme änderbar.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
