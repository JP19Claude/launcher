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
            Text("ClaudeOS-Ruhmeshalle", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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

/** A big version of the system (a whole one or a .5): what it was called then, its name, its egg. */
internal class SystemEra(val version: String, val system: String, val title: String, val egg: String)

/** Every big version, oldest first – Hearth, Hearth UI, OMEGA UI, ZENITH. */
internal val SystemEras = listOf(
    SystemEra("11.0", "Hearth", "Alles in einer App", "Der Planet: die Version als leuchtender Planet, Clawd kreist um ihn – antippen, und er springt."),
    SystemEra("12.0", "Hearth", "ClaudeOS 2 „Blaze“", "Der Planet: die Version als leuchtender Planet unter funkelnden Sternen."),
    SystemEra("12.5", "Hearth", "Fluid-Ozean", "Die Version schwimmt auf flüssigen Farben – Tippen macht Wellen, und Clawd schwimmt hin."),
    SystemEra("13.0", "Hearth", "Butterweich", "Seide: Bänder in Claudes Farben folgen deinem Finger, bis Clawd auf ihnen reitet."),
    SystemEra("13.5", "Hearth", "Flüssiges Glas", "Tropfen: Tropfen aus Glas fallen, springen und fließen ineinander."),
    SystemEra("14.0", "Hearth UI", "Hearth UI 14", "Prisma: einen Lichtstrahl durchs Glasprisma in Claudes Farben fächern."),
    SystemEra("14.5", "Hearth UI", "Power-Update", "Kometen: mit der Schleuder fünf Kometen um den Hearth-Stern kreisen lassen."),
    SystemEra("15.0", "Hearth UI", "Menüs & Geheimnisse", "Der Tresor: das Zahlenschloss auf 15, 3, 1 drehen – Clawd sitzt drin."),
    SystemEra("15.5", "Hearth UI", "Glimmer Drop", "Funken: zwei Glimmer-Inseln aneinanderhalten, bis die Funken fliegen und Clawd tanzt."),
    SystemEra("16.0", "Hearth UI", "Hearth UI 16", "Glühwürmchen: den Finger hinhalten und sechzehn Glühwürmchen um ihn sammeln."),
    SystemEra("17.0", "OMEGA UI", "OMEGA UI 17", "Rubin: den rohen Stein in siebzehn Schnitten zum Rubin schleifen."),
    SystemEra("17.5", "OMEGA UI", "Illumination", "Laterne: mit Clawds Licht fünf versteckte Ω im Dunkeln finden."),
    SystemEra("18.0", "OMEGA UI", "Mythos", "Schmiede: mit achtzehn Hammerschlägen wird der Stein zum Ω-Rubin."),
    SystemEra("18.5", "OMEGA UI", "Liquid", "Lupe: eine Linse aus Liquid Glass über das Lichterfeld schieben und sechs winzige Clawds finden."),
    SystemEra("19.0", "ZENITH", "ZENITH", "Sonnenlauf: die Sonne bis in den Zenit schieben und neunzehn Clawds wecken."),
    SystemEra("19.5", "ZENITH", "Z-Sturm", "Z-Sturm: fünf Kerne antippen, jeder schlägt in seiner Farbe zu – bis der weiße Z-Angriff kommt."),
)

/** The big version a [version] belongs to (the newest one not newer than it). */
internal fun systemEra(version: String): SystemEra {
    val number = versionNumber(version)
    return SystemEras.lastOrNull { versionNumber(it.version) <= number } ?: SystemEras.first()
}

/** A version's badge in the look its system had: Hearth's warmth, Hearth UI's colors, OMEGA's ruby, ZENITH's metal. */
@Composable
private fun EraBadge(era: SystemEra) {
    val shape = RoundedCornerShape(16.dp)
    val face = when (era.system) {
        "ZENITH" -> Brush.verticalGradient(listOf(Color(0xFF45494C), Color(0xFF2A2D30), Color(0xFF151719)))
        "OMEGA UI" -> Brush.linearGradient(listOf(Color(0xFFFF6B7D), Color(0xFFE5243F), Color(0xFF7A0A1E)))
        "Hearth UI" -> Brush.linearGradient(listOf(Color(0xFFFFB494), Color(0xFFFF6FB5), Color(0xFF8E6BFF), Color(0xFF3E91FF)))
        else -> Brush.linearGradient(listOf(Color(0xFFFFD2BC), Color(0xFFD97757), Color(0xFF8C3B22)))
    }
    val number = HearthUi.major(era.version)
    Box(
        Modifier
            .size(54.dp)
            .clip(shape)
            .background(face)
            .glassSheen(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (era.system == "ZENITH") {
            // ZENITH's metal with its green light along the foot.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, ZenithGreen, Color(0xFFD2FFD7), ZenithGreen, Color.Transparent))),
            )
        }
        Text(
            number,
            color = if (era.system == "ZENITH") ZenithGreen else Color.White,
            fontSize = if (number.length > 2) 17.sp else 22.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/**
 * The Ruhmeshalle: every big version of the system – the whole ones and the .5s, from Hearth
 * through Hearth UI and OMEGA UI to ZENITH – each with its badge, its name and its egg; a tap
 * plays that egg again ([onPick] gets its version). [onClaudeOs] opens the ClaudeOS one.
 * Opened by holding the system's version.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SystemHall(onPick: (String) -> Unit, onClaudeOs: () -> Unit, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(onBack = onClose)
    val current = remember { SystemEras.last().version }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF060807), Color(0xFF0C1410), Color(0xFF050605))))
            // Nothing underneath gets a touch while the hall is open.
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
            Text("Ruhmeshalle", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Jede große Version – von Hearth über Hearth UI und OMEGA UI bis ZENITH – mit ihrem eigenen Easter Egg. Tippe auf eine, um es noch einmal zu spielen – und drück eine lange: Jede Zeit versteckt etwas.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            // The way to the ClaudeOS hall.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onClaudeOs)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClaudeMark(ClaudeMarkStyle.Helios, Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("ClaudeOS-Ruhmeshalle", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp)
            }
            var group = ""
            SystemEras.reversed().forEach { era ->
                if (era.system != group) {
                    group = era.system
                    Spacer(Modifier.height(18.dp))
                    Text(
                        era.system,
                        color = if (era.system == "ZENITH") ZenithGreen else Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                val now = era.version == current
                Row(
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = if (now) 0.11f else 0.06f))
                        .glassSheen(22.dp)
                        // ZENITH 19.6: held, an era shows what hides in it – Hearth's fire, OMEGA's fluid, the zenith.
                        .combinedClickable(
                            onLongClick = {
                                val hidden = when {
                                    era.system.startsWith("Hearth") -> SecretMenu.Hearth
                                    era.system.startsWith("OMEGA") -> SecretMenu.Fluid
                                    era.system.startsWith("ZENITH") -> SecretMenu.Zenith
                                    else -> null
                                }
                                hidden?.let { SecretRoutes.open(context, it) }
                            },
                            onClick = { onPick(era.version) },
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EraBadge(era)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${era.system} ${HearthUi.major(era.version)} · ${era.title}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(era.egg, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (now) {
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
