package dev.hearth.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.BuildConfig
import dev.hearth.launcher.CoreEnforcerActivity
import dev.hearth.launcher.NightstandActivity
import dev.hearth.launcher.PerfumeActivity
import dev.hearth.launcher.ShieldActivity
import dev.hearth.launcher.VaultActivity
import dev.hearth.launcher.data.AuroraLook
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.CoreProfiles
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.PerfumeShelf
import dev.hearth.launcher.data.ZenithBrief
import dev.hearth.launcher.data.ZenithShield
import dev.hearth.launcher.data.ZenithSlogan
import dev.hearth.launcher.data.ZenithTint
import dev.hearth.launcher.data.ZenithWeather
import dev.hearth.launcher.data.withZenithTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HubClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HubDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** The skies the weather picture runs through when it's asked to show off. */
private val SkyShow = listOf(0, 2, 3, 45, 53, 63, 73, 95)

private fun startScreen(context: Context, target: Class<*>) {
    runCatching { context.startActivity(Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * ZENITH 20, the Zentrale: the day at a glance on one screen, under the aurora. The time and a
 * greeting; the brief – what the weather means for today, today's perfume, the next alarm, the
 * battery and the protection, worded by Claude when asked; the weather with the next hours and
 * days; the phone's insides; the aurora's colors; and the shortcuts to everything of ZENITH's.
 */
@Composable
fun ZentraleScreen(onClose: () -> Unit, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val report = rememberWeather()
    val city by ZenithWeather.city.collectAsStateWithLifecycle()
    val loading by ZenithWeather.loading.collectAsStateWithLifecycle()
    val shelf by PerfumeShelf.shelf.collectAsStateWithLifecycle()
    val pick by PerfumeShelf.pick.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        PerfumeShelf.init(context)
        runCatching { PerfumeShelf.refreshToday() }
    }
    val monitor by produceState<MonitorReading?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { readMonitor(context) }
            delay(3000)
        }
    }
    val shield by produceState<Int?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { ZenithShield.score(ZenithShield.checks(context)) }.getOrNull() }
    }
    val alarm = remember { nextAlarmText(context) }
    // ZENITH 20: the Zentrale's eggs – at dawn, on a 20th, at 20 °C, at 20 % battery.
    LaunchedEffect(Unit) {
        if (LocalDate.now().dayOfMonth == 20) EasterEggs.find(context, "twentyth")
        if (LocalTime.now().hour == 5) EasterEggs.find(context, "earlybird")
    }
    val degrees = report?.now?.roundToInt()
    LaunchedEffect(degrees) { if (degrees == 20) EasterEggs.find(context, "degrees20") }
    val chargeNow = monitor?.battery
    LaunchedEffect(chargeNow) { if (chargeNow == 20) EasterEggs.find(context, "battery20") }

    var editing by remember { mutableStateOf(false) }
    var cityText by remember { mutableStateOf("") }
    var cityNote by remember { mutableStateOf<String?>(null) }
    var demo by remember { mutableStateOf<Int?>(null) }
    var brief by remember { mutableStateOf<String?>(null) }
    var briefBusy by remember { mutableStateOf(false) }
    var briefError by remember { mutableStateOf<String?>(null) }
    var strike by remember { mutableStateOf(false) }
    var jubilee by remember { mutableStateOf(false) }
    var galleryEgg by remember { mutableStateOf<String?>(null) }
    var galleryOs by remember { mutableStateOf<String?>(null) }
    val glyphTaps = remember { longArrayOf(0L, 0L) }
    BackHandler { onClose() }

    val perfume = pick?.let { p -> shelf.firstOrNull { it.id == p.perfumeId } }
    val perfumeLine = if (perfume != null) "${perfume.title} – ${pick?.reason.orEmpty()}" else null

    fun saveCity() {
        val q = cityText.trim()
        scope.launch {
            cityNote = null
            val problem = ZenithWeather.setCity(q)
            if (problem != null) {
                cityNote = problem
            } else {
                editing = false
                cityText = ""
                ZenithWeather.refresh(force = true)
            }
        }
    }

    fun writeBrief() {
        if (briefBusy) return
        briefBusy = true
        briefError = null
        scope.launch {
            try {
                ClaudeAssistant.init(context)
                val facts = ZenithBrief.facts(
                    hour = LocalTime.now().hour,
                    city = city,
                    report = report,
                    battery = monitor?.battery ?: -1,
                    charging = monitor?.charging ?: false,
                    shield = shield,
                    perfume = perfumeLine,
                    alarm = alarm,
                )
                brief = ClaudeAssistant.complete(ZenithBrief.AI_SYSTEM, facts, maxTokens = 400).trim()
            } catch (e: Exception) {
                briefError = e.message ?: "Das hat nicht geklappt."
            } finally {
                briefBusy = false
            }
        }
    }

    fun showOff() {
        val at = System.currentTimeMillis()
        glyphTaps[0] = if (at - glyphTaps[1] < 600) glyphTaps[0] + 1 else 1
        glyphTaps[1] = at
        if (glyphTaps[0] >= 5 && demo == null) {
            glyphTaps[0] = 0
            EasterEggs.find(context, "weathershow")
            scope.launch {
                SkyShow.forEach { code ->
                    demo = code
                    delay(1100)
                }
                demo = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(VaultBg)) {
        ZenithAurora(settings.auroraLook, Modifier.fillMaxSize(), strength = 0.7f)
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { VaultTopBar(onClose, title = "ZENTRALE") }
            item {
                ZentraleHeader(
                    onTwenty = {
                        EasterEggs.find(context, "twenty")
                        Toast.makeText(context, "Zwanzig. Für jede Version eine.", Toast.LENGTH_SHORT).show()
                    },
                    onGreeting = {
                        EasterEggs.find(context, "polyglot")
                        Toast.makeText(context, "Hallo · Hello · Bonjour · Hola · Ciao · Olá · Hej · こんにちは", Toast.LENGTH_LONG).show()
                    },
                )
            }

            // ---- The brief ----
            item {
                StudioPanel("Tagesbrief", "Aus dem, was das Telefon weiß. Claude kann ihn in eigene Worte fassen – dafür braucht er seinen Schlüssel (Frag Clawd).") {
                    val r = report
                    BriefRow(if (r != null) ZenithBrief.weatherTip(r) else "Sag mir unten, wo du wohnst – dann kenne ich das Wetter für dich.")
                    BriefRow(
                        when {
                            perfumeLine != null -> "Parfüm heute: $perfumeLine"
                            shelf.isEmpty() -> "Dein Duftschrank ist noch leer – tippe hier und zeig mir deine Parfüms."
                            else -> "Noch kein Parfüm für heute – tippe hier."
                        },
                        onClick = { startScreen(context, PerfumeActivity::class.java) },
                    )
                    if (alarm != null) BriefRow("Nächster Wecker: $alarm")
                    val m = monitor
                    if (m != null && m.battery >= 0) {
                        BriefRow("Akku ${m.battery} %" + (if (m.charging) ", lädt" else "") + (shield?.let { " · Schutz $it von 100" } ?: ""))
                    }
                    val written = brief
                    if (written != null) {
                        Text(
                            written,
                            color = VaultText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                    val problem = briefError
                    if (problem != null) {
                        Text(problem, color = VaultRed, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        VaultButton(
                            text = when {
                                briefBusy -> "Claude schreibt …"
                                written == null -> "Brief von Claude"
                                else -> "Neu schreiben"
                            },
                            modifier = Modifier.weight(1f),
                            primary = written == null,
                            enabled = !briefBusy,
                        ) { writeBrief() }
                    }
                }
            }

            // ---- The weather ----
            item {
                StudioPanel("Wetter", if (report != null) "Vom Wetterdienst Open-Meteo, ohne Anmeldung. Gesendet werden nur die Koordinaten deines Ortes." else null) {
                    val r = report
                    if (r != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.city.uppercase(), color = HudGreen, fontSize = 11.sp, fontFamily = HudFont, letterSpacing = 3.sp, maxLines = 1)
                                Text("${r.now.roundToInt()}°", color = VaultText, fontSize = 66.sp, fontWeight = FontWeight.Light)
                                Text("${skyTitle(r.sky)} · gefühlt ${r.feels.roundToInt()}°", color = VaultText, fontSize = 15.sp)
                                Text(
                                    "H ${r.max}° · T ${r.min}° · ${r.rain} % Regen\nWind ${r.wind.roundToInt()} km/h · ${r.humidity} % Luftfeuchte",
                                    color = VaultDim,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Box(Modifier.size(104.dp).clickable(remember { MutableInteractionSource() }, indication = null) { showOff() }) {
                                WeatherGlyph(demo ?: r.code, r.night, Modifier.fillMaxSize())
                            }
                        }
                        WeatherHourStrip(
                            r.hours,
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                            Arrangement.spacedBy(18.dp),
                        )
                        WeatherDays(r.days, Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    } else if (!editing) {
                        Text(
                            if (city.isBlank()) "Wo wohnst du? Dann zeigt die Zentrale das Wetter, das Widget auch – und der Duftschrank richtet sich danach." else "Das Wetter für ${city.substringBefore(',')} wird geholt …",
                            color = VaultText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                    if (editing || city.isBlank()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CityField(cityText, { cityText = it }, Modifier.weight(1f)) { saveCity() }
                            Spacer(Modifier.width(8.dp))
                            VaultButton("Setzen", enabled = cityText.isNotBlank()) { saveCity() }
                        }
                    }
                    val note = cityNote
                    if (note != null) {
                        Text(note, color = VaultRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                    }
                    if (city.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                            LinkText(if (editing) "Abbrechen" else "Ort ändern") {
                                editing = !editing
                                cityNote = null
                            }
                            Spacer(Modifier.width(20.dp))
                            LinkText(if (loading) "Lädt …" else "Aktualisieren") {
                                scope.launch { ZenithWeather.refresh(force = true) }
                            }
                        }
                    }
                }
            }

            // ---- The phone ----
            item {
                StudioPanel("System") {
                    val m = monitor
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        if (m == null) {
                            Text("Wird gemessen …", color = VaultDim, fontSize = 13.sp, fontFamily = HudFont)
                        } else {
                            MonitorBar(
                                "AKKU",
                                "${m.battery}%" + (if (m.charging) " ▲" else "") + " · ${"%.1f".format(m.temperature)}°C · ${"%.2f".format(m.voltage)} V",
                                m.battery / 100f,
                                if (m.temperature >= 42f) VaultRed else if (m.battery in 0..15) ZenithSun else ZenithGreen,
                            )
                            MonitorBar(
                                "RAM",
                                "${gigabytes(m.ramUsed)} / ${gigabytes(m.ramTotal)} GB",
                                if (m.ramTotal > 0) m.ramUsed.toFloat() / m.ramTotal else 0f,
                                ZenithGreen,
                            )
                            MonitorBar(
                                "SPEICHER",
                                "${gigabytes(m.storageUsed)} / ${gigabytes(m.storageTotal)} GB",
                                if (m.storageTotal > 0) m.storageUsed.toFloat() / m.storageTotal else 0f,
                                if (m.storageTotal > 0 && m.storageUsed.toFloat() / m.storageTotal > 0.9f) ZenithSun else ZenithGreen,
                            )
                            val hours = m.uptimeMinutes / 60
                            Text(
                                "LÄUFT SEIT ${if (hours >= 24) "${hours / 24} T " else ""}${hours % 24} H ${m.uptimeMinutes % 60} M",
                                color = VaultDim,
                                fontSize = 10.sp,
                                fontFamily = HudFont,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // ---- The aurora ----
            item {
                StudioPanel("Polarlicht", "Lichtvorhänge in den Farben des Looks – auf dem Startbildschirm, hier und in der Z-Nacht.") {
                    SwitchRow(
                        label = "Polarlicht auf dem Startbildschirm",
                        description = "Bewegt sich hinter den Apps und Widgets",
                        checked = settings.zenithAurora,
                    ) { v -> onUpdate { it.copy(zenithAurora = v) } }
                    // ZENITH 20.1: ZENITH's color, system-wide.
                    ChoiceRow(
                        label = "ZENITH-Farbe (ganzes System)",
                        options = ZenithTint.entries,
                        selected = settings.zenithTint,
                        optionLabel = { it.label },
                        swatch = { it.main },
                        onSelect = { v -> onUpdate { it.withZenithTint(v) } },
                    )
                    ChoiceRow(
                        label = "Farben des Polarlichts",
                        options = AuroraLook.entries,
                        selected = settings.auroraLook,
                        optionLabel = { it.label },
                        swatch = { it.colors.first() },
                        onSelect = { v -> onUpdate { it.copy(auroraLook = v) } },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        VaultButton("Jubiläums-Look", Modifier.weight(1f), primary = false) { onUpdate { CoreProfiles.jubileeLook(it) } }
                    }
                }
            }

            // ---- The gallery: every version's egg, in its jubilee edition ----
            item {
                StudioPanel("Jubiläums-Galerie", "Jede Version hat ihr eigenes Egg – hier alle auf einmal, in der Jubiläums-Edition. Antippen spielt es.") {
                    Text("ZENITH, HEARTH, OMEGA", color = VaultDim, fontSize = 10.sp, fontFamily = HudFont, letterSpacing = 2.sp, modifier = Modifier.padding(start = 18.dp, top = 8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SystemEras.forEach { era -> EraChip(HearthUi.major(era.version)) { galleryEgg = era.version } }
                    }
                    Text("CLAUDEOS", color = VaultDim, fontSize = 10.sp, fontFamily = HudFont, letterSpacing = 2.sp, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ClaudeOsEras.forEach { era -> EraChip("${era.version} ${era.codename}") { galleryOs = era.version } }
                    }
                }
            }

            // ---- The shortcuts ----
            item {
                StudioPanel("Schnellzugriff") {
                    val tiles = listOf(
                        HubShortcut("Z-Nacht", "Nachttisch-Uhr unter dem Polarlicht") { startScreen(context, NightstandActivity::class.java) },
                        HubShortcut("Tresor", "Fotos und Dateien, verschlüsselt") { startScreen(context, VaultActivity::class.java) },
                        HubShortcut("Schutzschild", "Wie gut ist das Telefon geschützt?") { startScreen(context, ShieldActivity::class.java) },
                        HubShortcut("Core Enforcer", "Das ganze System umbauen") { startScreen(context, CoreEnforcerActivity::class.java) },
                        HubShortcut("Duftschrank", "Welches Parfüm heute?") { startScreen(context, PerfumeActivity::class.java) },
                        HubShortcut("Z-Angriff", "Der Angriff des Z, jetzt") { strike = true },
                        HubShortcut("Jubiläum", "Die Zeremonie zu ZENITH 20, noch einmal") { jubilee = true },
                    )
                    tiles.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                            pair.forEachIndexed { i, tile ->
                                if (i > 0) Spacer(Modifier.width(10.dp))
                                ZTile(tile.label, tile.sub, Modifier.weight(1f), tile.action)
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.width(10.dp))
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    ZenithSlogan.UPPER,
                    color = ZenithGreen,
                    fontSize = 17.sp,
                    fontFamily = HudFont,
                    letterSpacing = 5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                )
            }
            item {
                Text(
                    "ZENITH ${HearthUi.major(BuildConfig.VERSION_NAME)} · ${if (versionNumber(BuildConfig.VERSION_NAME) >= 21f && LocalSettings.current.zenithTheme != dev.hearth.launcher.data.ZenithTheme.Jubilee) "Horizont" else "Jubiläum"} · ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                    color = VaultDim,
                    fontSize = 11.sp,
                    fontFamily = HudFont,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        if (strike) ZenithStrikeOverlay { strike = false }
        if (jubilee) ZenithJubilee { jubilee = false }
        galleryEgg?.let { version -> HearthVersionEgg(version, systemEra(version).system) { galleryEgg = null } }
        galleryOs?.let { version -> ClaudeOsVersionEgg(version) { galleryOs = null } }
    }
}

/** One of the Zentrale's shortcuts. */
private class HubShortcut(val label: String, val sub: String, val action: () -> Unit)

/** The greeting, the time big and the date; [onTwenty] when the time has been tapped twenty times. */
@Composable
private fun ZentraleHeader(onTwenty: () -> Unit, onGreeting: () -> Unit) {
    val now = rememberTime(everySecond = true)
    val taps = remember { longArrayOf(0L, 0L) }
    val greetTaps = remember { longArrayOf(0L, 0L) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp)) {
        Text(
            ZenithBrief.greeting(now.hour).uppercase(),
            color = ZenithGreen,
            fontSize = 12.sp,
            fontFamily = HudFont,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                val at = System.currentTimeMillis()
                greetTaps[0] = if (at - greetTaps[1] < 600) greetTaps[0] + 1 else 1
                greetTaps[1] = at
                if (greetTaps[0] >= 5) {
                    greetTaps[0] = 0
                    onGreeting()
                }
            },
        )
        Text(
            now.format(HubClock),
            color = VaultText,
            fontSize = 80.sp,
            fontWeight = FontWeight.ExtraLight,
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                val at = System.currentTimeMillis()
                taps[0] = if (at - taps[1] < 1500) taps[0] + 1 else 1
                taps[1] = at
                if (taps[0] >= 20) {
                    taps[0] = 0
                    onTwenty()
                }
            },
        )
        Text(
            now.format(HubDate) + " · " + "%02d".format(now.second),
            color = VaultDim,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun BriefRow(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("›", color = ZenithGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(text, color = VaultText, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** A version in the gallery: its number in gold, in a cut frame. */
@Composable
private fun EraChip(label: String, onClick: () -> Unit) {
    val shape = ZenithCutShape(6.dp)
    Text(
        label,
        color = ZenithGold,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .border(0.8.dp, ZenithGold.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = ZenithGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun ZTile(label: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = ZenithCutShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(VaultPanelHigh)
            .border(0.8.dp, ZenithGreen.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, color = VaultText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = VaultDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CityField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val shape = ZenithCutShape(9.dp)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(60)) },
        singleLine = true,
        textStyle = TextStyle(color = VaultText, fontSize = 15.sp),
        cursorBrush = SolidColor(ZenithGreen),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(shape)
                    .background(VaultBg)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text("Stadt, zum Beispiel Köln", color = VaultDim.copy(alpha = 0.7f), fontSize = 15.sp)
                inner()
            }
        },
    )
}
