package dev.hearth.launcher.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.PerfumeShelf

/**
 * ZENITH 19.7, the perfume widget: Clawd tells you which perfume to wear today, and why – for the
 * weather. Tapped, Clawd's perfume shelf opens, where you add perfumes (written down or as a
 * photo) and say which you like.
 */
@Composable
internal fun ClawdPerfumeWidget(modifier: Modifier) {
    val context = LocalContext.current
    remember { PerfumeShelf.init(context) }
    val shelf by PerfumeShelf.shelf.collectAsState()
    val pick by PerfumeShelf.pick.collectAsState()
    val choosing by PerfumeShelf.busy.collectAsState()
    val helper by PerfumeShelf.helper.collectAsState()
    LaunchedEffect(shelf.size) { PerfumeShelf.refreshToday() }
    val perfume = pick?.let { p -> shelf.firstOrNull { it.id == p.perfumeId } }
    Row(
        modifier
            .padding(12.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                runCatching {
                    context.startActivity(Intent(context, dev.hearth.launcher.PerfumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clawd – or, if you chose it, the ZENITH Z.
        PerfumeHelperFigure(
            helper,
            when {
                choosing -> ClawdMood.Thinking
                perfume != null -> ClawdMood.Love
                else -> ClawdMood.Idle
            },
            Modifier.fillMaxHeight(0.6f).width(60.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                when {
                    shelf.isEmpty() -> Text(
                        "Zeig mir deine Parfüms – tipp mich an und schreib sie auf oder schick ein Foto!",
                        color = Color(0xFF2B2A27),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    perfume != null -> {
                        Text(
                            "Heute: ${perfume.title}",
                            color = Color(0xFF2B2A27),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pick?.reason.orEmpty(),
                            color = Color(0xFF2B2A27),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> Text(
                        if (choosing) "Ich überlege, was heute passt …" else "Tipp mich an – ich such dir ein Parfüm aus.",
                        color = Color(0xFF2B2A27),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // The picture of the perfume beside it.
        if (perfume != null) {
            Spacer(Modifier.width(8.dp))
            PerfumePicture(perfume, Modifier.size(76.dp))
        }
    }
}
