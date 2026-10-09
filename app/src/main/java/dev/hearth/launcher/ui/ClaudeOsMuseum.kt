package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs

/*
 * ZENITH 19 · ClaudeOS 6: the ClaudeOS museum – every ClaudeOS so far, each with its own egg.
 */

/** A ClaudeOS, the running one or one before it: version, codename, its mark and its egg in a sentence. */
internal class ClaudeOsEra(val version: String, val codename: String, val mark: ClaudeMarkStyle, val egg: String) {
    /** "ClaudeOS 5.0" */
    val full: String get() = "${ClaudeOs.NAME} $version"
}

/** Every ClaudeOS so far, oldest first. */
internal val ClaudeOsEras = listOf(
    ClaudeOsEra("1.0", "Ember", ClaudeMarkStyle.Ember, "Den Claude-Stern im Kreis drehen, bis Funken fliegen und die Clawds herauspurzeln."),
    ClaudeOsEra("2.0", "Blaze", ClaudeMarkStyle.Blaze, "Übers Display wischen und das Feuer anfachen, bis die Clawds ums Feuer tanzen."),
    ClaudeOsEra("3.0", "Nova", ClaudeMarkStyle.Nova, "Den jungen Stern füttern und aufladen, bis er zur Supernova wird."),
    ClaudeOsEra("4.0", "Aurora", ClaudeMarkStyle.Aurora, "Nordlichter mit dem Finger an den Polarhimmel malen."),
    ClaudeOsEra("5.0", "Mythos", ClaudeMarkStyle.Mythos, "Den Finger aufs Dunkel halten, bis der Rat der sechs Clawds erscheint."),
    ClaudeOsEra("6.0", "Helios", ClaudeMarkStyle.Helios, "Sonnenfinsternis: den Mond von der Sonne schieben."),
)

/** The ClaudeOS for [version] (the newest one not newer than it). */
internal fun claudeOsEra(version: String): ClaudeOsEra {
    val number = versionNumber(version)
    return ClaudeOsEras.lastOrNull { versionNumber(it.version) <= number } ?: ClaudeOsEras.first()
}

/** The ClaudeOS an egg is about: the running one, unless the museum opened an older one. */
internal val LocalClaudeOsEra = compositionLocalOf { claudeOsEra(ClaudeOs.VERSION) }

/** Tapped: [onTap]; held: [onHold] – for the version rows that hide an egg and a museum. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tapOrHold(interaction: MutableInteractionSource, onTap: () -> Unit, onHold: () -> Unit): Modifier =
    combinedClickable(interactionSource = interaction, indication = null, onLongClick = onHold, onClick = onTap)

/**
 * The ClaudeOS museum: every ClaudeOS with its mark and its egg; a tap plays that egg again
 * ([onPick] gets its version). Opened by holding the ClaudeOS version.
 */
@Composable
internal fun ClaudeOsMuseum(onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07090F), Color(0xFF0E1420), Color(0xFF050608))))
            // Nothing underneath gets a touch while the museum is open.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(64.dp))
            Text("ClaudeOS-Museum", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Jede ClaudeOS hat ihr eigenes Easter Egg. Tippe auf eine, um es noch einmal zu spielen.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            ClaudeOsEras.reversed().forEach { era ->
                val current = era.version == ClaudeOs.VERSION
                Row(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = if (current) 0.11f else 0.06f))
                        .glassSheen(22.dp)
                        .clickable { onPick(era.version) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClaudeMark(era.mark, Modifier.size(46.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${era.full} „${era.codename}“", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(era.egg, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (current) {
                        Spacer(Modifier.width(8.dp))
                        Text("Aktuell", color = ZenithGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), size = 44.dp) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
