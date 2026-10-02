package dev.hearth.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdOutfit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdSkin
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.system.ClawdLink

/**
 * Clawd's look (color, hat) and where he lives; in Hearth's settings and in the Clawd app.
 * [inLauncher]: also the companion on the home screen and the Clawd app.
 */
@Composable
internal fun ClawdSettings(
    s: LauncherSettings,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    inLauncher: Boolean = true,
) {
    val context = LocalContext.current
    Section("Clawd") {
        // Him, as he looks now; a tap shows the next pose.
        var pose by remember { mutableIntStateOf(0) }
        val poses = listOf(ClawdMood.Wave, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Idle, ClawdMood.Flip)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            Clawd(
                Modifier
                    .size(width = 120.dp, height = 104.dp)
                    .clickable { pose++ },
                mood = poses[pose % poses.size],
            )
        }
        ChoiceRow(
            label = "Farbe",
            options = ClawdSkin.entries,
            selected = s.clawdSkin,
            optionLabel = { it.label },
            swatch = { if (it == ClawdSkin.Rainbow) rainbow(0.75f) else it.color },
            onSelect = { v -> update { it.copy(clawdSkin = v) } },
        )
        // His wardrobe: every outfit and hat on a little Clawd, tap to put it on.
        Wardrobe(
            label = "Outfit",
            options = ClawdOutfit.entries,
            selected = s.clawdOutfit,
            optionLabel = { it.label },
            preview = { o -> o to s.clawdHat },
            onSelect = { v -> update { it.copy(clawdOutfit = v) } },
        )
        Wardrobe(
            label = "Hut & Accessoire",
            options = ClawdHat.entries,
            selected = s.clawdHat,
            optionLabel = { it.label },
            preview = { h -> s.clawdOutfit to h },
            onSelect = { v -> update { it.copy(clawdHat = v) } },
        )
        if (inLauncher) {
            RowDivider()
            SwitchRow(
                label = "Clawd auf dem Startbildschirm",
                description = "Er spaziert über dem Dock, schläft nachts und tanzt beim Laden. Antippen: er sagt was · zweimal: Salto · gedrückt halten: mit ihm reden",
                checked = s.clawdCompanion,
            ) { v -> update { it.copy(clawdCompanion = v) } }
            SwitchRow(
                label = "Clawd in Glimmer",
                description = "Er sitzt in der Insel neben der Kamera, schläft nachts und tanzt beim Laden (braucht die Glimmer-App)",
                checked = s.glimmerClawd,
            ) { v -> update { it.copy(glimmerClawd = v) } }
            Note("Clawd-Widgets: Startbildschirm gedrückt halten → Widget hinzufügen → Clawd. Farbe und Hut gelten überall: Suche, Chat, Widgets, Begleiter – und in der Clawd-App.")
            var installed by remember { mutableStateOf(ClawdLink.isInstalled(context)) }
            ActionRow(
                label = if (installed) "Clawd-App öffnen" else "Clawd-App holen",
                description = "Clawds Widgets auch für den Samsung-Launcher und jeden anderen – Hearth hat sie schon eingebaut",
            ) {
                installed = ClawdLink.isInstalled(context)
                if (installed) {
                    ClawdLink.openApp(context)
                } else {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ClawdLink.DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

/** A row of little Clawds, each wearing one choice; the chosen one is outlined. */
@Composable
private fun <T> Wardrobe(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    preview: (T) -> Pair<ClawdOutfit, ClawdHat>,
    onSelect: (T) -> Unit,
) {
    val accent = LocalSettings.current.accent.color.let { if (it == Color.White) Color(0xFFD97757) else it }
    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options.size) { i ->
                val option = options[i]
                val (outfit, hat) = preview(option)
                val chosen = option == selected
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (chosen) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) accent else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { onSelect(option) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Clawd(
                        Modifier.size(width = 56.dp, height = 52.dp),
                        mood = if (chosen) ClawdMood.Wave else ClawdMood.Idle,
                        animate = chosen && LocalSettings.current.animations,
                        hat = hat,
                        outfit = outfit,
                    )
                    Text(optionLabel(option), color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
