package dev.hearth.launcher.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hearth.launcher.data.ControlState
import dev.hearth.launcher.data.ShizukuBridge
import dev.hearth.launcher.data.SystemControls
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The phone's own settings, inside Hearth's: what Android lets an app change is changed right
 * here (brightness, rotation, screen timeout, volumes, ringer, flashlight, Do not disturb);
 * everything else – Wi-Fi, Bluetooth, battery, apps, security … – is a row that opens exactly
 * that page of the system's settings (or its quick panel), grouped as on One UI.
 */

/** One page of the system's settings: the first of its intents that opens wins. */
internal class SystemPage(
    val title: String,
    val subtitle: String,
    val color: Color,
    val intents: List<Intent>,
    val glyph: Glyph? = null,
    val icon: ImageVector? = null,
)

private fun action(name: String, data: Uri? = null): Intent = Intent(name).apply { if (data != null) setData(data) }

private fun appLaunch(context: Context, vararg packages: String): List<Intent> =
    packages.mapNotNull { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }

/** The system's settings, grouped like Samsung's. */
private fun systemGroups(context: Context): List<Pair<String, List<SystemPage>>> {
    val me = Uri.parse("package:${context.packageName}")
    val blue = Color(0xFF3E91FF)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9F0A)
    val violet = Color(0xFF8E6BFF)
    val red = Color(0xFFFF453A)
    val teal = Color(0xFF30B0C7)
    val gray = Color(0xFF8E8E93)
    val pink = Color(0xFFFF6FB5)
    return listOf(
        "Verbindungen" to listOf(
            SystemPage("WLAN", "Netze, Schalter", blue, listOf(action(Settings.Panel.ACTION_WIFI), action(Settings.ACTION_WIFI_SETTINGS)), glyph = Glyph.Wifi),
            SystemPage("Bluetooth", "Geräte koppeln", blue, listOf(action(Settings.ACTION_BLUETOOTH_SETTINGS)), glyph = Glyph.Bluetooth),
            SystemPage("Mobile Daten & Internet", "SIM, Roaming, Datenverbindung", green, listOf(action(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), action(Settings.ACTION_DATA_ROAMING_SETTINGS)), glyph = Glyph.Cellular),
            SystemPage("Datennutzung", "Verbrauch, Datensparen", green, listOf(action(Settings.ACTION_DATA_USAGE_SETTINGS), action(Settings.ACTION_WIRELESS_SETTINGS)), glyph = Glyph.Cellular),
            SystemPage("Flugmodus", "Alle Funkverbindungen", orange, listOf(action(Settings.ACTION_AIRPLANE_MODE_SETTINGS)), glyph = Glyph.Airplane),
            SystemPage("Mobiler Hotspot", "Internet teilen", teal, listOf(action("android.settings.TETHER_SETTINGS"), action(Settings.ACTION_WIRELESS_SETTINGS)), glyph = Glyph.Hotspot),
            SystemPage("NFC & Bezahlen", "Kontaktlos", gray, listOf(action(Settings.Panel.ACTION_NFC), action(Settings.ACTION_NFC_SETTINGS)), glyph = Glyph.Nfc),
            SystemPage("VPN", "Private Netze", gray, listOf(action(Settings.ACTION_VPN_SETTINGS)), glyph = Glyph.Lock),
            SystemPage("Weitere Verbindungen", "Drucken, Private DNS, Ethernet", gray, listOf(action(Settings.ACTION_WIRELESS_SETTINGS)), icon = Icons.Rounded.Share),
        ),
        "Geräte & Medien" to listOf(
            SystemPage("Bildschirm übertragen", "Smart View, Chromecast", violet, listOf(action(Settings.ACTION_CAST_SETTINGS)), glyph = Glyph.Cast),
            SystemPage("Drucken", "Drucker und Dienste", gray, listOf(action(Settings.ACTION_PRINT_SETTINGS)), icon = Icons.Rounded.Menu),
        ),
        "Töne & Benachrichtigungen" to listOf(
            SystemPage("Töne und Vibration", "Klingeltöne, Vibrationsmuster", violet, listOf(action(Settings.ACTION_SOUND_SETTINGS)), glyph = Glyph.Speaker),
            SystemPage("Lautstärke-Panel", "Alle Lautstärken", violet, listOf(action(Settings.Panel.ACTION_VOLUME), action(Settings.ACTION_SOUND_SETTINGS)), glyph = Glyph.Headphones),
            SystemPage("Benachrichtigungen", "App-Benachrichtigungen, Verlauf", red, listOf(action("android.settings.ALL_APPS_NOTIFICATION_SETTINGS"), action("android.settings.NOTIFICATION_SETTINGS"), action(Settings.ACTION_SETTINGS)), glyph = Glyph.Bell),
            SystemPage("Nicht stören", "Zeitpläne, Ausnahmen", violet, listOf(action("android.settings.ZEN_MODE_SETTINGS"), action(Settings.ACTION_SOUND_SETTINGS)), glyph = Glyph.Moon),
            SystemPage("Benachrichtigungszugriff", "Welche Apps mitlesen dürfen", gray, listOf(action(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)), glyph = Glyph.Bell),
        ),
        "Anzeige & Stil" to listOf(
            SystemPage("Anzeige", "Hell/Dunkel, Bildwiederholrate, Auflösung", orange, listOf(action(Settings.ACTION_DISPLAY_SETTINGS)), glyph = Glyph.Sun),
            SystemPage("Augenkomfort / Nachtlicht", "Wärmere Farben am Abend", orange, listOf(action("android.settings.NIGHT_DISPLAY_SETTINGS"), action(Settings.ACTION_DISPLAY_SETTINGS)), glyph = Glyph.Moon),
            SystemPage("Schriftgröße und -stil", "Text und Anzeigegröße", orange, listOf(action("android.settings.TEXT_READING_SETTINGS"), action(Settings.ACTION_DISPLAY_SETTINGS)), icon = Icons.Rounded.Edit),
            SystemPage("Bildschirmschoner", "Beim Laden und im Dock", orange, listOf(action(Settings.ACTION_DREAM_SETTINGS)), glyph = Glyph.Contrast),
            SystemPage("Hintergrund", "Startbildschirm und Sperrbildschirm", pink, listOf(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrund wählen")), icon = Icons.Rounded.Favorite),
            SystemPage("Standard-Startbildschirm", "Hearth als Launcher", blue, listOf(action(Settings.ACTION_HOME_SETTINGS), action(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)), icon = Icons.Rounded.Home),
        ),
        "Sicherheit & Datenschutz" to listOf(
            SystemPage("Sperrbildschirm & Sicherheit", "PIN, Muster, Fingerabdruck, Gesicht", blue, listOf(action(Settings.ACTION_SECURITY_SETTINGS)), glyph = Glyph.Lock),
            SystemPage("Biometrie", "Fingerabdruck, Gesichtserkennung", blue, listOf(action(if (android.os.Build.VERSION.SDK_INT >= 30) Settings.ACTION_BIOMETRIC_ENROLL else Settings.ACTION_SECURITY_SETTINGS), action(Settings.ACTION_SECURITY_SETTINGS)), icon = Icons.Rounded.Face),
            SystemPage("Datenschutz", "Berechtigungen, Dashboard", green, listOf(action(Settings.ACTION_PRIVACY_SETTINGS)), glyph = Glyph.Lock),
            SystemPage("Standort", "Ortung, App-Zugriff", green, listOf(action(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), glyph = Glyph.Location),
        ),
        "Akku & Gerätewartung" to listOf(
            SystemPage("Akku", "Verbrauch, Laden, Schutz", green, listOf(action(Intent.ACTION_POWER_USAGE_SUMMARY), action(Settings.ACTION_BATTERY_SAVER_SETTINGS)), glyph = Glyph.Battery),
            SystemPage("Energiesparmodus", "Länger durchhalten", green, listOf(action(Settings.ACTION_BATTERY_SAVER_SETTINGS)), glyph = Glyph.Battery),
            SystemPage("Akku-Optimierung", "Apps im Hintergrund", gray, listOf(action(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)), glyph = Glyph.Battery),
            SystemPage("Speicher", "Belegung, aufräumen", teal, listOf(action(android.os.storage.StorageManager.ACTION_MANAGE_STORAGE), action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)), icon = Icons.Rounded.Menu),
            SystemPage("Digitales Wohlbefinden", "Bildschirmzeit, Schlafenszeit", teal, appLaunch(context, "com.samsung.android.forest", "com.google.android.apps.wellbeing") + action(Settings.ACTION_SETTINGS), icon = Icons.Rounded.ThumbUp),
        ),
        "Apps & Konten" to listOf(
            SystemPage("Apps", "Alle Apps, Berechtigungen", blue, listOf(action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), action(Settings.ACTION_APPLICATION_SETTINGS)), icon = Icons.Rounded.Star),
            SystemPage("Standard-Apps", "Browser, Telefon, SMS", blue, listOf(action(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)), icon = Icons.Rounded.Star),
            SystemPage("Hearth (App-Info)", "Berechtigungen, Speicher von Hearth", gray, listOf(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, me)), icon = Icons.Rounded.Info),
            SystemPage("Konten und Sicherung", "Konten, Synchronisieren", orange, listOf(action(Settings.ACTION_SYNC_SETTINGS)), icon = Icons.Rounded.AccountCircle),
            SystemPage("Konto hinzufügen", "Google, Samsung, E-Mail …", orange, listOf(action(Settings.ACTION_ADD_ACCOUNT)), icon = Icons.Rounded.Person),
        ),
        "Allgemeine Verwaltung" to listOf(
            SystemPage("Sprache", "Systemsprache, Regionen", gray, listOf(action(Settings.ACTION_LOCALE_SETTINGS)), icon = Icons.Rounded.Settings),
            SystemPage("Tastatur", "Tastaturen und Eingabe", gray, listOf(action(Settings.ACTION_INPUT_METHOD_SETTINGS)), icon = Icons.Rounded.Edit),
            SystemPage("Datum und Uhrzeit", "Zeitzone, 24-Stunden", gray, listOf(action(Settings.ACTION_DATE_SETTINGS)), icon = Icons.Rounded.DateRange),
            SystemPage("Bedienungshilfen", "Sehen, Hören, Glimmer-Dienst", green, listOf(action(Settings.ACTION_ACCESSIBILITY_SETTINGS)), icon = Icons.Rounded.Person),
            SystemPage("System-Softwareupdate", "Android und One UI", blue, listOf(action("android.settings.SYSTEM_UPDATE_SETTINGS"), action(Settings.ACTION_DEVICE_INFO_SETTINGS)), icon = Icons.Rounded.Refresh),
            SystemPage("Telefoninfo (System)", "Modell, Android-Version, Status", gray, listOf(action(Settings.ACTION_DEVICE_INFO_SETTINGS)), icon = Icons.Rounded.Info),
            SystemPage("Entwickleroptionen", "Nur wenn eingeschaltet", red, listOf(action(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), action(Settings.ACTION_DEVICE_INFO_SETTINGS)), icon = Icons.Rounded.Build),
            SystemPage("Alle Systemeinstellungen", "Die Einstellungen-App", gray, listOf(action(Settings.ACTION_SETTINGS)), icon = Icons.Rounded.Settings),
        ),
    )
}

private fun open(context: Context, page: SystemPage) {
    val opened = page.intents.any { intent ->
        runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Screen timeouts as One UI offers them. */
private val Timeouts = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

private fun timeoutLabel(ms: Int): String = if (ms < 60_000) "${ms / 1000} Sek." else "${ms / 60_000} Min."

private val RingerModes = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)

private fun ringerLabel(mode: Int): String = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE -> "Vibration"
    AudioManager.RINGER_MODE_SILENT -> "Lautlos"
    else -> "Ton"
}

/**
 * The "System" page of Hearth's settings: quick controls at the top, then every part of the
 * phone's settings, one tap away.
 */
@Composable
internal fun SystemSettingsContent(controls: SystemControls) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(controls.state()) }
    var brightness by remember { mutableFloatStateOf(controls.brightness()) }
    var timeout by remember { mutableIntStateOf(controls.screenTimeout()) }
    var ringer by remember { mutableIntStateOf(controls.ringerMode()) }
    var levels by remember { mutableStateOf(controls.streams.associate { it.stream to controls.streamLevel(it.stream) }) }
    val torch by controls.torchOn.collectAsState()
    val groups = remember { systemGroups(context) }

    // Coming back from a system page (a permission given, something switched): read it all again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = controls.state()
                brightness = controls.brightness()
                timeout = controls.screenTimeout()
                ringer = controls.ringerMode()
                levels = controls.streams.associate { it.stream to controls.streamLevel(it.stream) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column {
        ShizukuSection(state) { state = controls.state() }
        Section("Schnell einstellen") {
            if (!state.canWriteSettings) {
                Note("Für Helligkeit, automatisches Drehen und den Bildschirm-Timeout braucht Hearth einmal die Erlaubnis „Systemeinstellungen ändern“.")
                SystemPageRow(
                    SystemPage("Erlaubnis geben", "Systemeinstellungen ändern", Color(0xFF3E91FF), emptyList(), icon = Icons.Rounded.Settings),
                ) { controls.requestWriteSettings() }
            } else {
                SliderRow(
                    label = "Helligkeit",
                    value = brightness,
                    range = 0f..1f,
                    valueText = "${(brightness * 100).roundToInt()} %",
                ) { v ->
                    brightness = v
                    if (controls.setBrightness(v)) state = state.copy(autoBrightness = false)
                }
                SwitchRow("Automatische Helligkeit", state.autoBrightness, "Passt sich dem Umgebungslicht an") { v ->
                    if (controls.setAutoBrightness(v)) state = state.copy(autoBrightness = v)
                }
                SwitchRow("Automatisch drehen", state.autoRotate) { v ->
                    if (controls.setAutoRotate(v)) state = state.copy(autoRotate = v)
                }
                ChoiceRow(
                    label = "Bildschirm-Timeout",
                    options = Timeouts,
                    selected = Timeouts.minByOrNull { abs(it - timeout) },
                    optionLabel = ::timeoutLabel,
                ) { t -> if (controls.setScreenTimeout(t)) timeout = t }
            }
            if (controls.hasTorch) {
                SwitchRow("Taschenlampe", torch) { v -> controls.setTorch(v) }
            }
            SwitchRow(
                "Nicht stören",
                state.doNotDisturb,
                if (controls.hasDoNotDisturbAccess()) "Nur wichtige Unterbrechungen" else "Braucht einmal Zugriff – antippen öffnet die Freigabe",
            ) { v -> if (controls.setDoNotDisturb(v)) state = state.copy(doNotDisturb = v) }
            ChoiceRow(
                label = "Klingelmodus",
                options = RingerModes,
                selected = ringer,
                optionLabel = ::ringerLabel,
            ) { m ->
                controls.setRingerMode(m)
                ringer = controls.ringerMode()
            }
        }
        Section("Lautstärke") {
            controls.streams.forEach { stream ->
                val level = levels[stream.stream] ?: 0f
                SliderRow(
                    label = stream.label,
                    value = level,
                    range = 0f..1f,
                    valueText = "${(level * 100).roundToInt()} %",
                ) { v ->
                    levels = levels + (stream.stream to v)
                    controls.setStreamLevel(stream.stream, v)
                }
            }
            Spacer(Modifier.size(8.dp))
        }
        groups.forEach { (title, pages) ->
            Section(title) {
                pages.forEachIndexed { index, page ->
                    if (index > 0) RowDivider()
                    SystemPageRow(page) { open(context, page) }
                }
            }
        }
        Note("Android lässt Apps WLAN, Bluetooth und mobile Daten nicht selbst umschalten – diese Zeilen öffnen die Schalter des Systems direkt.")
    }
}

/** A row like One UI's: a colored round icon, the name, what's inside, and an arrow. */
@Composable
internal fun SystemPageRow(page: SystemPage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .fluidTouch(page.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(page.color),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = page.glyph
            val icon = page.icon
            when {
                glyph != null -> GlyphIcon(glyph, Color.White, Modifier.size(20.dp))
                icon != null -> Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(page.subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
    }
}

/**
 * Shizuku: switches Android keeps from normal apps (Wi-Fi, Bluetooth, mobile data …), switched
 * by Hearth with ADB rights. Until Shizuku is ready, the steps to get there.
 */
@Composable
private fun ShizukuSection(state: ControlState, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ShizukuBridge.status(context)) }
    // What was just switched shows at once; the real state is read again a moment later.
    var pending by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var mobileData by remember { mutableStateOf(readMobileData(context)) }
    val hasNfc = remember { runCatching { android.nfc.NfcAdapter.getDefaultAdapter(context) != null }.getOrDefault(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val refresh = {
            status = ShizukuBridge.status(context)
            mobileData = readMobileData(context)
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        val result = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        lifecycle.addObserver(observer)
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(result)
        }
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching {
                Shizuku.removeBinderReceivedListener(received)
                Shizuku.removeBinderDeadListener(dead)
                Shizuku.removeRequestPermissionResultListener(result)
            }
        }
    }

    fun switch(key: String, on: Boolean, action: suspend (Boolean) -> Boolean) {
        pending = pending + (key to on)
        scope.launch {
            action(on)
            delay(900)
            mobileData = readMobileData(context)
            onChanged()
            pending = pending - key
        }
    }

    Section("Shizuku · Erweiterte Schalter") {
        when (status) {
            ShizukuBridge.Status.NotInstalled -> {
                Note("Mit der kostenlosen App Shizuku kann Hearth WLAN, Bluetooth, mobile Daten, Flugmodus, Standort, NFC, Dunkelmodus und Energiesparen selbst schalten – ohne Root und ohne neue ROM.")
                SystemPageRow(SystemPage("Shizuku installieren", "Kostenlos, aus dem Play Store", Color(0xFF3E91FF), emptyList(), icon = Icons.Rounded.Refresh)) {
                    ShizukuBridge.openApp(context)
                }
            }
            ShizukuBridge.Status.NotRunning -> {
                Note("Shizuku ist installiert, läuft aber noch nicht. Öffne Shizuku, schalte unter Entwickleroptionen „Kabelloses Debugging“ ein und tippe in Shizuku auf „Starten“. Nach jedem Neustart einmal.")
                SystemPageRow(SystemPage("Shizuku öffnen", "Und dort starten", Color(0xFF3E91FF), emptyList(), icon = Icons.Rounded.Refresh)) {
                    ShizukuBridge.openApp(context)
                }
                SystemPageRow(SystemPage("Entwickleroptionen", "Kabelloses Debugging", Color(0xFFFF453A), emptyList(), icon = Icons.Rounded.Build)) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            ShizukuBridge.Status.NoPermission -> {
                Note("Shizuku läuft. Erlaube Hearth einmal, es zu benutzen.")
                SystemPageRow(SystemPage("Hearth erlauben", "Zugriff auf Shizuku", Color(0xFF34C759), emptyList(), icon = Icons.Rounded.Settings)) {
                    ShizukuBridge.requestPermission()
                }
            }
            ShizukuBridge.Status.Ready -> {
                SwitchRow("WLAN", pending["wifi"] ?: state.wifi) { v -> switch("wifi", v, ShizukuBridge::setWifi) }
                SwitchRow("Bluetooth", pending["bt"] ?: state.bluetooth) { v -> switch("bt", v, ShizukuBridge::setBluetooth) }
                SwitchRow("Mobile Daten", pending["data"] ?: mobileData) { v -> switch("data", v, ShizukuBridge::setMobileData) }
                SwitchRow("Flugmodus", pending["air"] ?: state.airplane) { v -> switch("air", v, ShizukuBridge::setAirplane) }
                SwitchRow("Standort", pending["loc"] ?: state.location) { v -> switch("loc", v, ShizukuBridge::setLocation) }
                if (hasNfc) SwitchRow("NFC", pending["nfc"] ?: state.nfc) { v -> switch("nfc", v, ShizukuBridge::setNfc) }
                SwitchRow("Dunkelmodus", pending["dark"] ?: state.darkMode) { v -> switch("dark", v, ShizukuBridge::setDarkMode) }
                SwitchRow("Energiesparmodus", pending["saver"] ?: state.batterySaver) { v -> switch("saver", v, ShizukuBridge::setBatterySaver) }
                Note("Über Shizuku verbunden – Hearth schaltet mit ADB-Rechten.")
            }
        }
    }
    if (status == ShizukuBridge.Status.Ready) {
        ShizukuTools()
        ShizukuPowerTools()
    }
    PrivacyCheck(status == ShizukuBridge.Status.Ready)
}

private fun toast(context: Context, text: String) {
    runCatching { android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show() }
}

/** System animation speeds, as the developer options offer them. */
private val AnimationScales = listOf(0f, 0.5f, 1f, 1.5f)

private fun scaleLabel(scale: Float): String = when (scale) {
    0f -> "Aus"
    0.5f -> "Schnell (0,5×)"
    1f -> "Normal"
    else -> "Langsam (1,5×)"
}

/** What else Shizuku makes possible: tempo, ad blocking, memory, frozen apps, power. */
@Composable
private fun ShizukuTools() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(ShizukuBridge.animationScale(context)) }
    var dns by remember { mutableStateOf(ShizukuBridge.dns(context)) }
    var frozen by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { frozen = ShizukuBridge.frozenApps() }

    fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
    }.getOrDefault(pkg)

    Section("Shizuku · Tempo & Werbeblocker") {
        ChoiceRow(
            label = "System-Animationen",
            options = AnimationScales,
            selected = AnimationScales.minByOrNull { abs(it - scale) },
            optionLabel = ::scaleLabel,
        ) { v ->
            scope.launch {
                if (ShizukuBridge.setAnimationScale(v)) scale = v else toast(context, "Ging nicht")
            }
        }
        Note("„Schnell“ lässt das ganze Handy flotter wirken – Apps öffnen und schließen in halber Zeit.")
        ChoiceRow(
            label = "Werbeblocker fürs ganze Handy (Privates DNS)",
            options = ShizukuBridge.Dns.entries,
            selected = dns,
            optionLabel = { it.label },
        ) { d ->
            scope.launch {
                if (ShizukuBridge.setDns(d)) dns = d else toast(context, "Ging nicht")
            }
        }
        Note("AdGuard blockt Werbung und Tracker in allen Apps und im Browser, ohne zusätzliche App.")
    }

    Section("Shizuku · Speicher & Apps") {
        SystemPageRow(SystemPage("Hintergrund-Apps beenden", "Gibt Arbeitsspeicher frei", Color(0xFF34C759), emptyList(), icon = Icons.Rounded.Refresh)) {
            scope.launch {
                toast(context, if (ShizukuBridge.killBackgroundApps()) "Hintergrund-Apps beendet" else "Ging nicht")
            }
        }
        Note("Lange auf eine App drücken: „App stoppen“ und „Einfrieren“ (die App bleibt installiert, startet aber nicht mehr, bis du sie hier auftaust).")
        if (frozen.isNotEmpty()) {
            frozen.forEach { pkg ->
                RowDivider()
                SystemPageRow(SystemPage(label(pkg), "Eingefroren · tippen zum Auftauen", Color(0xFF30B0C7), emptyList(), icon = Icons.Rounded.Star)) {
                    scope.launch {
                        if (ShizukuBridge.thaw(pkg)) {
                            toast(context, "${label(pkg)} aufgetaut")
                            frozen = ShizukuBridge.frozenApps()
                        }
                    }
                }
            }
        }
    }

    Section("Shizuku · Ein/Aus") {
        SystemPageRow(SystemPage("Bildschirm aus", "Sofort sperren", Color(0xFF8E8E93), emptyList(), glyph = Glyph.Lock)) {
            scope.launch { ShizukuBridge.screenOff() }
        }
        RowDivider()
        // Restarting and switching off ask twice, so a slip of the finger doesn't do it.
        SystemPageRow(
            SystemPage(
                if (confirm == "reboot") "Nochmal tippen zum Neustarten" else "Neu starten",
                "Startet das Handy neu",
                Color(0xFFFF9F0A),
                emptyList(),
                glyph = Glyph.Power,
            ),
        ) {
            if (confirm == "reboot") scope.launch { ShizukuBridge.reboot() } else confirm = "reboot"
        }
        RowDivider()
        SystemPageRow(
            SystemPage(
                if (confirm == "off") "Nochmal tippen zum Ausschalten" else "Ausschalten",
                "Schaltet das Handy aus",
                Color(0xFFFF453A),
                emptyList(),
                glyph = Glyph.Power,
            ),
        ) {
            if (confirm == "off") scope.launch { ShizukuBridge.shutdown() } else confirm = "off"
        }
    }
}

/** Whether mobile data is switched on (not whether it's in use right now). */
private fun readMobileData(context: Context): Boolean =
    runCatching { Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1 }.getOrDefault(true)
