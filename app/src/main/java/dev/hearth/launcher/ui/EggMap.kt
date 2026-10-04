package dev.hearth.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs

/** One easter egg: its id, a name, and where it hides. */
private class EggSpot(val id: String, val name: String, val where: String)

/** All of Hearth's easter eggs and how to find them. */
private val EggSpots = listOf(
    EggSpot("version", "Hearth UI-Version", "Software-Update oder Über das Telefon: fünfmal schnell auf die Hearth UI-Version tippen. Jede große Version hat ein eigenes Egg (15: Tresor, 14.5: Kometen, 14: Prisma, 13.5: Tropfen, 13: Seide …)."),
    EggSpot("claudeos", "ClaudeOS", "Software-Update oder Über das Telefon: fünfmal schnell auf die ClaudeOS-Version tippen und den Stern zur Supernova bringen (ClaudeOS 3 „Nova“)."),
    EggSpot("footer", "Gebaut mit Claude", "Einstellungen ganz unten: siebenmal auf „Hearth · gebaut mit Claude“ tippen."),
    EggSpot("clock", "Uhr", "Startbildschirm: fünfmal schnell auf die Uhr tippen."),
    EggSpot("spin", "Glas-Uhr", "Das Glas-Uhr-Widget dreimal schnell antippen – es dreht sich."),
    EggSpot("rainbow", "Regenbogen", "Im Finder nach „Regenbogen“ suchen."),
    EggSpot("walk", "Clawd suchen", "Im Finder nach „Clawd“ suchen."),
    EggSpot("wave", "Clawd winkt", "Lange auf Clawd in einer Suchleiste drücken (Startbildschirm oder App-Drawer)."),
    EggSpot("dizzy", "Schwindelig", "Clawd auf dem Startbildschirm sechsmal schnell antippen."),
    EggSpot("drag", "Ausflug", "Clawd auf dem Startbildschirm von einem Rand zum anderen ziehen."),
    EggSpot("wakeup", "Aufwecken", "Clawd antippen, während er schläft."),
    EggSpot("tickle", "Kitzeln", "Im Clawd-Chat fünfmal auf Clawd oben im Kopf tippen."),
    EggSpot("42", "Sinn des Lebens", "Clawd nach dem Sinn des Lebens fragen."),
    EggSpot("love", "Liebe", "Clawd sagen, dass du ihn lieb hast."),
    EggSpot("dance", "Tanz", "Clawd bitten zu tanzen."),
    EggSpot("best", "Der Beste", "Clawd fragen, wer der Beste ist."),
    EggSpot("midnight", "Nachteule", "Nach Mitternacht mit Clawd reden."),
    EggSpot("rainbowskin", "Regenbogen-Clawd", "Clawd die Farbe „Regenbogen“ geben (Einstellungen → Clawd)."),
    EggSpot("fashion", "Modenschau", "Clawd gleichzeitig ein Outfit und einen Hut anziehen."),
    EggSpot("jump100", "Clawd Jump", "100 Punkte in Clawd Jump schaffen."),
    EggSpot("water", "Wasser", "An einem Tag acht Gläser Wasser mit Clawd trinken."),
    EggSpot("petlove", "Streicheleinheit", "Clawd im Tamagotchi fünfmal hintereinander streicheln."),
    EggSpot("breathe", "Atmen", "Eine ganze Minute mit Clawd atmen."),
    EggSpot("diary", "Tagebuch", "Clawds Stimmungstagebuch sieben Tage am Stück führen."),
    EggSpot("coin", "Auf der Kante", "Clawds Münze werfen, bis sie auf der Kante landet (1 zu 250)."),
    EggSpot("rps", "Unschlagbar", "Fünfmal hintereinander gegen Clawd Schnick-Schnack-Schnuck gewinnen."),
    EggSpot("bigday", "Der große Tag", "Clawds Countdown genau am großen Tag ansehen."),
    EggSpot("riddles", "Rätselmeister", "Alle Rätsel von Clawd lösen."),
    EggSpot("codes", "Codeknacker", "Im Finder *#0000# eintippen – die Liste aller Geheimcodes. (Oder lange auf den großen Titel „Einstellungen“ drücken.)"),
    EggSpot("labs", "Hearth Labs", "Über das Telefon: siebenmal auf die Build-Nummer tippen."),
)

/**
 * The hidden easter egg map: where all of Hearth's eggs are, and which you've found.
 * Opened by holding the "made by" line in Über das Telefon.
 */
@Composable
internal fun EggMap(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.init(context) }
    val found by EasterEggs.found.collectAsState()
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF120C22), Color(0xFF07060E)))),
    ) {
        FluidBackdrop(AiFluidColors.take(3), strength = 0.5f)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
        ) {
            item {
                Column(Modifier.padding(top = 50.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Clawd(Modifier.size(width = 90.dp, height = 76.dp), mood = ClawdMood.Love)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Easter-Egg-Karte",
                        style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    )
                    val count = EggSpots.count { it.id in found }
                    Text(
                        "$count von ${EggSpots.size} gefunden · psst, das ist geheim",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
            }
            items(EggSpots, key = { it.id }) { egg ->
                val done = egg.id in found
                Row(
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = if (done) 0.09f else 0.05f))
                        .glassSheen(20.dp)
                        .padding(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (done) Color(0xFF34C759) else Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (done) "✓" else "🥚", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(egg.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(egg.where, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        GlassCircle(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}
