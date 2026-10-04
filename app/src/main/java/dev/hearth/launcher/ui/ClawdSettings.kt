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
            swatch = {
                when (it) {
                    ClawdSkin.Rainbow -> rainbow(0.75f)
                    ClawdSkin.Galaxy -> galaxy(0.45f)
                    else -> it.color
                }
            },
            onSelect = { v ->
                update { it.copy(clawdSkin = v) }
                if (v == ClawdSkin.Rainbow) dev.hearth.launcher.data.EasterEggs.find(context, "rainbowskin")
            },
        )
        // His wardrobe: every outfit and hat on a little Clawd, tap to put it on.
        Wardrobe(
            label = "Outfit",
            options = ClawdOutfit.entries,
            selected = s.clawdOutfit,
            optionLabel = { it.label },
            preview = { o -> o to s.clawdHat },
            onSelect = { v ->
                update { it.copy(clawdOutfit = v) }
                if (v != ClawdOutfit.None && s.clawdHat != ClawdHat.None) dev.hearth.launcher.data.EasterEggs.find(context, "fashion")
            },
        )
        // OMEGA UI 17.1: the color of his clothes, and the Ω on his chest.
        ChoiceRow(
            label = "Farbe der Kleidung",
            options = dev.hearth.launcher.data.ClawdCloth.entries,
            selected = s.clawdCloth,
            optionLabel = { it.label },
            swatch = { it.color ?: androidx.compose.ui.graphics.Color(0xFF8E8E93) },
            onSelect = { v -> update { it.copy(clawdCloth = v) } },
        )
        SwitchRow(
            label = "OMEGA-Abzeichen",
            description = "Ein kleiner Rubin mit dem Ω auf Clawds Brust",
            checked = s.clawdOmega,
        ) { v -> update { it.copy(clawdOmega = v) } }
        Wardrobe(
            label = "Hut & Accessoire",
            options = ClawdHat.entries,
            selected = s.clawdHat,
            optionLabel = { it.label },
            preview = { h -> s.clawdOutfit to h },
            onSelect = { v ->
                update { it.copy(clawdHat = v) }
                if (v != ClawdHat.None && s.clawdOutfit != ClawdOutfit.None) dev.hearth.launcher.data.EasterEggs.find(context, "fashion")
            },
        )
        RowDivider()
        ClawdBadgeGrid(s)
        RowDivider()
        ActionRow(
            label = "Clawd Jump spielen",
            description = "Clawd rennt, du tippst zum Springen: über Bugs hüpfen, Herzen sammeln. Rekord: ${dev.hearth.launcher.data.ClawdGameScore.best(context)}",
        ) { dev.hearth.launcher.data.ClawdGameScore.open(context) }
        if (inLauncher) {
            RowDivider()
            SwitchRow(
                label = "Clawd auf dem Startbildschirm",
                description = "Er spaziert über dem Dock, schläft nachts und tanzt beim Laden. Antippen: er reagiert, jedes Mal anders · ganz schnell tippen: ihm wird schwindelig · zur Seite ziehen: er läuft mit · gedrückt halten: mit ihm reden",
                checked = s.clawdCompanion,
            ) { v -> update { it.copy(clawdCompanion = v) } }
            SwitchRow(
                label = "Clawd in Glimmer",
                description = "Clawd ist bei allem dabei, was die Insel zeigt: Musik, Anrufe, Timer, Nachrichten, Laden, Face ID … (braucht die Glimmer-App; dort lässt er sich auch ein- und ausschalten)",
                checked = s.glimmerClawd,
            ) { v ->
                update { it.copy(glimmerClawd = v) }
                // Straight to Glimmer: there it's its own switch, so it's only handed over now.
                val app = context.applicationContext
                Thread {
                    dev.hearth.launcher.system.GlimmerLink.syncSettings(
                        app,
                        android.os.Bundle().apply { putBoolean(dev.hearth.launcher.data.SettingsRepository.KEY_CLAWD_GLIMMER, v) },
                    )
                }.start()
            }
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

/** Clawd's badges, three in a row: earned ones bright, the others grey with how to get them. */
@Composable
internal fun ClawdBadgeGrid(s: LauncherSettings) {
    val context = LocalContext.current
    val badges = remember(s) { dev.hearth.launcher.data.ClawdBadges.all(context, s) }
    val earned = badges.count { it.earned }
    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            "Abzeichen · $earned von ${badges.size}",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        badges.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (b.earned) Color(0xFFD97757).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (b.earned) b.emoji else "🔒", fontSize = 22.sp)
                        Text(
                            b.title,
                            color = if (b.earned) Color.White else Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            b.hint,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
