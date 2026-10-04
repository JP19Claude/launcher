package dev.hearth.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ColorWorld
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.Perk
import dev.hearth.launcher.data.has
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** OMEGA UI 17.5: opens the Clawd Illumination (its own screen, from anywhere in the app). */
fun openClawdIllumination(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, dev.hearth.launcher.IlluminationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * The Ω crystal's secret: tapped as often as OMEGA UI's number (17 for OMEGA UI 17), it opens
 * the Clawd Illumination – the last taps count down, like Android's developer options.
 * Returns what to call on every tap.
 */
@Composable
fun rememberCrystalTaps(version: String): () -> Unit {
    val context = LocalContext.current
    val needed = remember(version) { version.substringBefore('.').filter { it.isDigit() }.toIntOrNull()?.coerceIn(3, 99) ?: 17 }
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    val toast = remember { arrayOfNulls<Toast>(1) }
    return remember(needed) {
        val tap: () -> Unit = {
            val now = System.currentTimeMillis()
            taps = if (now - last < 1200) taps + 1 else 1
            last = now
            val left = needed - taps
            toast[0]?.cancel()
            toast[0] = null
            if (left <= 0) {
                taps = 0
                EasterEggs.find(context, "illumination")
                openClawdIllumination(context)
            } else if (left <= 7) {
                val text = if (left == 1) "△ Noch 1 Mal bis zu den Clawd Illuminati" else "△ Noch $left Mal bis zu den Clawd Illuminati"
                val shown = Toast.makeText(context, text, Toast.LENGTH_SHORT)
                shown.show()
                toast[0] = shown
            }
            Unit
        }
        tap
    }
}

/**
 * The Ω, set like a jewel: in OMEGA Fluid's colors (with OMEGA Glass), white otherwise.
 */
@Composable
fun OmegaSign(size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val palette = fluidPalette()
    val brush = if (s.omegaGlass) {
        Brush.linearGradient(listOf(Color.White, palette[0], palette[1], Color.White))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.75f)))
    }
    Text(
        "Ω",
        modifier = modifier,
        style = TextStyle(brush = brush, fontSize = size, fontWeight = FontWeight.Black, lineHeight = size, shadow = OnWallpaperText.shadow),
        maxLines = 1,
    )
}

/**
 * OMEGA UI 17.5 – the Clawd Illumination: Clawd, lit up with a halo in a burst of golden
 * light, and the big, wild switches for the whole system. Everything here can be switched
 * back, and "Alles zurück" turns all of it off at once.
 */
@Composable
fun ClawdIlluminationScreen(
    settings: LauncherSettings,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val animate = settings.animations
    val turn = flowPhase(36_000, animate)
    val anyOn = settings.omegaOverdrive || settings.hyperGlass || settings.colorWorld != ColorWorld.Off ||
        settings.omegaRain || settings.weightless || settings.clawdParade || settings.omegaZeros ||
        settings.clawdHalo || settings.giantClawd || settings.allSeeingEye || settings.perks.isNotEmpty() || settings.mythos
    val glow by animateFloatAsState(if (anyOn) 1f else 0.6f, spring(stiffness = 120f), label = "illuminationGlow")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050305), Color(0xFF0D0608), Color(0xFF030203)))),
    ) {
        // OMEGA UI 17.6: hidden symbols in the dark – faint eye-pyramids all over the wall.
        Canvas(Modifier.fillMaxSize()) {
            val step = 120.dp.toPx()
            var row = 0
            var y = -step / 3f
            while (y < size.height) {
                var x = if (row % 2 == 0) step / 4f else step * 0.75f
                while (x < size.width + step) {
                    drawEyePyramid(Offset(x, y), step * 0.5f, IlluminatiGold.copy(alpha = 0.06f), glow = 0.3f)
                    x += step
                }
                y += step * 0.8f
                row++
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            // The secret council: six robed Clawds at a triangular table, under the eye.
            IlluminatiCouncil(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, IlluminatiGold.copy(alpha = 0.35f * glow + 0.1f), RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "CLAWD ILLUMINATI",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFEDB0), IlluminatiGold, Color(0xFF9C7A1E))),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "△  Novus Ordo Clawdorum  △",
                color = IlluminatiGold.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Der geheime Rat der sechs Clawds hat getagt. Was er beschließt, verändert ${HearthUi.NAME} – und alles lässt sich hier wieder aufheben.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(20.dp))

            // OMEGA UI 18: Claude Mythos – the Clawds take over the whole system.
            MythosCard(settings.mythos) { v -> onChange { it.copy(mythos = v) } }
            Spacer(Modifier.height(18.dp))

            IlluminationSection("Ω System") {
                IlluminationSwitch(
                    "⚡", "Ω-Overdrive",
                    "Jeder Rand fließt, das OMEGA-Glas wird tiefer, OMEGA Fluid überall stärker.",
                    settings.omegaOverdrive,
                ) { v -> onChange { it.copy(omegaOverdrive = v) } }
                IlluminationSwitch(
                    "💎", "Hyperglas",
                    "Glas, das viel stärker bricht, Farben spaltet und glänzt als jede Einstellung erlaubt.",
                    settings.hyperGlass,
                ) { v -> onChange { it.copy(hyperGlass = v) } }
                ColorWorldPicker(settings.colorWorld) { w -> onChange { it.copy(colorWorld = w) } }
            }
            IlluminationSection("Startbildschirm") {
                IlluminationSwitch(
                    "Ω", "Ω-Regen",
                    "Ω-Zeichen in den OMEGA-Farben steigen langsam hinter dem Startbildschirm auf.",
                    settings.omegaRain,
                ) { v -> onChange { it.copy(omegaRain = v) } }
                IlluminationSwitch(
                    "🪐", "Schwerelos",
                    "Die Apps auf dem Startbildschirm schweben sanft, jede in ihrem eigenen Takt.",
                    settings.weightless,
                ) { v -> onChange { it.copy(weightless = v) } }
                IlluminationSwitch(
                    "🎺", "Clawd-Parade",
                    "Ab und zu marschiert eine bunte Parade aus Clawds über das Dock – vorneweg einer mit Ω.",
                    settings.clawdParade,
                ) { v -> onChange { it.copy(clawdParade = v) } }
                IlluminationSwitch(
                    "🦖", "Riesen-Clawd",
                    "Clawd auf dem Startbildschirm, zweieinhalbmal so groß.",
                    settings.giantClawd,
                ) { v -> onChange { it.copy(giantClawd = v) } }
            }
            // OMEGA UI 18: the perks, by group.
            listOf("Atmosphäre", "Licht", "Bewegung", "Farbe", "Clawd").forEach { group ->
                IlluminationSection("Vorteile · $group") {
                    Perk.entries.filter { it.group == group }.forEach { perk ->
                        IlluminationSwitch(perk.emoji, perk.title, perk.text, settings.has(perk)) { v ->
                            onChange { it.copy(perks = if (v) it.perks + perk.name else it.perks - perk.name) }
                        }
                    }
                }
            }
            IlluminationSection("Uhr & Clawd") {
                IlluminationSwitch(
                    "🕛", "Ω-Ziffern",
                    "Jede Null in den Uhren wird zum Ω – 1Ω:Ω5.",
                    settings.omegaZeros,
                ) { v -> onChange { it.copy(omegaZeros = v) } }
                IlluminationSwitch(
                    "😇", "Heiligenschein",
                    "Jeder Clawd im System – auf dem Startbildschirm, in Suchleisten, in Glimmer – bekommt einen leuchtenden Heiligenschein.",
                    settings.clawdHalo,
                ) { v -> onChange { it.copy(clawdHalo = v) } }
                IlluminationSwitch(
                    "👁", "Allsehendes Auge",
                    "Oben auf dem Startbildschirm wacht das Auge in seiner Pyramide – es blinzelt und schaut sich um.",
                    settings.allSeeingEye,
                ) { v -> onChange { it.copy(allSeeingEye = v) } }
            }

            // Everything back to normal at once.
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A0B0E).copy(alpha = if (anyOn) 0.9f else 0.5f))
                    .border(1.dp, IlluminatiGold.copy(alpha = if (anyOn) 0.7f else 0.25f), CircleShape)
                    .clickable(enabled = anyOn) {
                        onChange {
                            it.copy(
                                omegaOverdrive = false,
                                hyperGlass = false,
                                colorWorld = ColorWorld.Off,
                                omegaRain = false,
                                weightless = false,
                                clawdParade = false,
                                omegaZeros = false,
                                clawdHalo = false,
                                giantClawd = false,
                                allSeeingEye = false,
                                perks = emptySet(),
                                mythos = false,
                            )
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    "△  Den Rat auflösen – alles zurück",
                    color = IlluminatiGold.copy(alpha = if (anyOn) 1f else 0.4f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "MDCCLXXVI · Ω · gefunden am Kristall",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(28.dp))
        }
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

/**
 * OMEGA UI 18: Claude Mythos – the big switch. While it's on, the Clawds take over: everything
 * at its strongest, a giant golden Clawd over the home screen, Clawds everywhere.
 */
@Composable
private fun MythosCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "mythos")
    val shine by t.animateFloat(
        0f,
        1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "mythosShine",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A1206), Color(0xFF5A2A08), Color(0xFF2A1206)),
                    start = Offset(shine * 1200f - 400f, 0f),
                    end = Offset(shine * 1200f + 200f, 400f),
                ),
            )
            .border(if (on) 2.dp else 1.dp, IlluminatiGold.copy(alpha = if (on) 1f else 0.6f), CutCornerShape(22.dp))
            .clickable { onToggle(!on) }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Clawd(Modifier.size(width = 46.dp, height = 40.dp), mood = if (on) ClawdMood.Dance else ClawdMood.Idle, color = IlluminatiGold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CLAUDE MYTHOS",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFFFF1C2), IlluminatiGold)),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    if (on) "Die Clawds haben übernommen." else "Lass die Clawds das System übernehmen.",
                    color = Color(0xFFFFE08A).copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFB8860B),
                    checkedBorderColor = Color(0xFFFFF1C2),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                    uncheckedBorderColor = IlluminatiGold.copy(alpha = 0.5f),
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Solange Mythos an ist, läuft alles auf Höchststufe: OMEGA Glass und Fluid im Overdrive, Hyperglas, Heiligenscheine, " +
                "die Parade, das allsehende Auge, Sternenhimmel, Aurora, Glühwürmchen, Sternschnuppen, Parallax, Funkenspur, " +
                "Leuchtfarben, Riesen-Uhr, Clawd-Schwarm und -Gruß – und ein riesiger goldener Clawd wacht über dem Startbildschirm. " +
                "Aus: alles wie vorher.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A group of switches on a card of glass with OMEGA Fluid along its rim. */
@Composable
private fun IlluminationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        // The Illuminati's way: an eye in its pyramid before every heading, in old gold.
        Row(Modifier.padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            EyeTriangle(Modifier.size(width = 22.dp, height = 20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                color = IlluminatiGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                letterSpacing = 2.sp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1C0D10).copy(alpha = 0.92f), Color(0xFF0B0507).copy(alpha = 0.92f))))
                .border(1.dp, IlluminatiGold.copy(alpha = 0.4f), CutCornerShape(18.dp))
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun IlluminationSwitch(icon: String, title: String, text: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!on) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every switch in its own little pyramid.
        Box(Modifier.size(width = 38.dp, height = 34.dp).padding(end = 4.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.matchParentSize()) {
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(tri, IlluminatiGold.copy(alpha = if (on) 0.22f else 0.07f))
                drawPath(tri, IlluminatiGold.copy(alpha = if (on) 0.9f else 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
            Text(icon, fontSize = 13.sp, color = Color(0xFFFFE3A0), fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Color(0xFFF3E6C8), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = on,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8C1C13),
                checkedBorderColor = IlluminatiGold,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

/** Farbwelt: the whole system in another color world, or running through all of them. */
@Composable
private fun ColorWorldPicker(selected: ColorWorld, onPick: (ColorWorld) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌈", fontSize = 24.sp, modifier = Modifier.width(36.dp))
            Column {
                Text("Farbwelt", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Das ganze System in einer anderen Farbwelt – oder mit Spektrum durch alle hindurch.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Row(
            Modifier
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorWorld.entries.forEach { world ->
                val on = world == selected
                val swatch = when (world) {
                    ColorWorld.Off -> Brush.linearGradient(listOf(Color(0xFF5E5E66), Color(0xFF2A2A30)))
                    ColorWorld.Spectrum -> Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    else -> Brush.linearGradient(listOf(Color.hsv((350f + world.degrees) % 360f, 0.75f, 0.95f), Color.hsv((10f + world.degrees) % 360f, 0.6f, 0.6f)))
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (on) 0.2f else 0.06f))
                        .border(1.dp, if (on) Color(0xFFFFD86B) else Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { onPick(world) }
                        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatch),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(world.label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
