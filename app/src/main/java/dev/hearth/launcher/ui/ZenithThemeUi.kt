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
    val card = if (theme == ZenithTheme.Jubilee) ZenithCardSpec.Jubilee else ZenithCardSpec.Horizon
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
        Text(if (theme == ZenithTheme.Jubilee) "20" else "21", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text(theme.label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 2)
    }
}

/** The two tiles side by side. */
@Composable
internal fun ZenithThemeTiles(selected: ZenithTheme, onPick: (ZenithTheme) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        ZenithThemeTile(ZenithTheme.Horizon, selected == ZenithTheme.Horizon, Modifier.weight(1f)) { onPick(ZenithTheme.Horizon) }
        Spacer(Modifier.width(12.dp))
        ZenithThemeTile(ZenithTheme.Jubilee, selected == ZenithTheme.Jubilee, Modifier.weight(1f)) { onPick(ZenithTheme.Jubilee) }
    }
}

/** Once, after the update to ZENITH 21: the new theme, or the jubilee's kept? (Changeable later in the settings.) */
@Composable
internal fun ZenithThemeChoice(onChoose: (ZenithTheme) -> Unit) {
    var picked by remember { mutableStateOf(ZenithTheme.Horizon) }
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
            Text("ZENITH 21", color = ZenithCardSpec.Horizon.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ein neues Theme – oder das Jubiläum behalten?",
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
            val accent = if (picked == ZenithTheme.Jubilee) ZenithCardSpec.Jubilee.accent else ZenithCardSpec.Horizon.accent
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
