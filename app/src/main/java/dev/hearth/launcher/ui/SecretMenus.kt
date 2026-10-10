package dev.hearth.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeAssistant
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.ShizukuBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * Hearth UI 15: menus everywhere – and hidden ones. Like the service codes of a Galaxy, the
 * finder knows secret codes (*#0*#, *#1234#, *#0228# …) that open menus nobody sees otherwise,
 * OMEGA Labs unlocks with seven taps on the build number, and the Hearth menu and the power
 * menu sit one gesture away.
 */

/** OMEGA Labs: hidden until unlocked (seven taps on the build number, or its code). */
object HearthLabs {
    private const val PREFS = "hearth_labs"
    private var loaded = false

    var unlocked by mutableStateOf(false)
        private set

    /** A small frame counter on the home screen. */
    var fps by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        unlocked = p.getBoolean("unlocked", false)
        fps = p.getBoolean("fps", false)
    }

    fun unlock(context: Context) {
        init(context)
        if (!unlocked) {
            unlocked = true
            prefs(context).edit().putBoolean("unlocked", true).apply()
        }
        EasterEggs.find(context, "labs")
    }

    fun hide(context: Context) {
        unlocked = false
        fps = false
        prefs(context).edit().putBoolean("unlocked", false).putBoolean("fps", false).apply()
    }

    fun setFps(context: Context, on: Boolean) {
        fps = on
        prefs(context).edit().putBoolean("fps", on).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The hidden menus and the codes that open them (typed into the finder). */
enum class SecretMenu(
    val code: String,
    val title: String,
    val what: String,
    /** Words that open it from the finder (no spaces, case doesn't matter). */
    val words: List<String> = emptyList(),
    /** False: not in the list of codes – it has to be found. */
    val listed: Boolean = true,
) {
    Codes("*#0000#", "Geheimcodes", "Alle versteckten Menüs auf einen Blick"),
    Test("*#0*#", "Hardware-Test", "Display-Farben, Touch, Vibration und Sensoren"),
    Info("*#1234#", "Versionen", "ZENITH, ClaudeOS, Android und Build"),
    Battery("*#0228#", "Akku-Status", "Ladestand, Strom, Temperatur und Zustand – live"),
    Diagnose("*#9900#", "Diagnose", "Arbeitsspeicher, Speicher, Laufzeit und Shizuku"),
    Labs("*#4327#", "ZENITH Labs", "Experimente für Neugierige (H-E-A-R)"),
    Clawd("*#2529#", "Clawd-Studio", "Clawd in jeder Stimmung (C-L-A-W)"),
    Power("*#7697#", "Ein/Aus-Menü", "Bildschirm aus, Neustart, Ausschalten (P-O-W-R)"),

    /** ZENITH 19.6: more codes, T-E-R-M, N-E-O-N, S-N-A-K, D-R-A-W, O-R-A-K, Z-A-P-P, C-R-E-D. */
    Console("*#8376#", "Z-Konsole", "Ein Terminal mit Befehlen – tippe help"),
    Neon("*#6366#", "Neon", "Der Regen aus Zeichen – wach auf"),
    Snake("*#7625#", "Funken-Schlange", "Wische, friss Funken, werde lang"),
    Draw("*#3729#", "Lichtmalerei", "Mit dem Finger Licht in die Luft malen"),
    Oracle("*#6725#", "Ω-Orakel", "Eine Frage, ein Tipp, eine Antwort"),
    Zapp("*#9277#", "Blitz-Test", "Wie schnell sind deine Finger?"),
    Credits("*#2733#", "Abspann", "Alle, die mitgebaut haben"),

    /** Not a code: looking for "Omega" (or Ω) in the finder. Not in the code list. */
    Omega("Ω", "Ω", "Von Alpha bis Omega", listOf("omega", "Ω", "ω"), listed = false),

    /** ZENITH 19.6: words that open hidden screens from the finder. */
    Hearth("hearth", "Hearth-Kamin", "Das Feuer, mit dem alles anfing", listOf("hearth", "kamin"), listed = false),
    Konami("konami", "Der alte Code", "Hoch, hoch, runter, runter …", listOf("konami"), listed = false),
    Fluid("fluid", "OMEGA Fluid", "Das Licht, das dem Finger folgt", listOf("fluid"), listed = false),
    Zenith("zenith", "Zenith", "Der höchste Punkt", listOf("zenith"), listed = false),
    Sudo("sudo", "Sudo", "Nette Idee", listOf("sudosu", "sudomakemeasandwich"), listed = false),
    Hello("hello", "Hallo, Welt", "Das erste Programm von allen", listOf("helloworld", "hallowelt"), listed = false),
    Pi("pi", "π", "3,14159 …", listOf("π", "3,14", "3.14"), listed = false),
    ;

    companion object {
        fun forCode(text: String): SecretMenu? {
            val typed = text.replace(" ", "")
            return entries.firstOrNull { menu ->
                (menu.listed && menu.code == typed) || menu.words.any { it.equals(typed, ignoreCase = true) }
            }
        }
    }
}

/** A hidden menu, full screen. From the code list a menu opens on top; back returns to it. */
@Composable
fun SecretMenuScreen(menu: SecretMenu, onClose: () -> Unit) {
    var current by remember(menu) { mutableStateOf(menu) }
    val back: () -> Unit = { if (current != menu) current = menu else onClose() }
    Box(Modifier.fillMaxSize()) {
        when (current) {
            SecretMenu.Codes -> CodesMenu(onOpen = { current = it }, onClose = back)
            SecretMenu.Test -> TestMenu(back)
            SecretMenu.Info -> InfoMenu(back)
            SecretMenu.Battery -> BatteryMenu(back)
            SecretMenu.Diagnose -> DiagnoseMenu(back)
            SecretMenu.Labs -> LabsMenu(back)
            SecretMenu.Clawd -> ClawdStudio(back)
            SecretMenu.Power -> PowerMenu(back)
            SecretMenu.Omega -> OmegaEgg(back)
            SecretMenu.Console -> ZConsole(back)
            SecretMenu.Neon -> NeonMenu(back)
            SecretMenu.Snake -> SnakeMenu(back)
            SecretMenu.Draw -> LightPaintMenu(back)
            SecretMenu.Oracle -> OracleMenu(back)
            SecretMenu.Zapp -> ZappMenu(back)
            SecretMenu.Credits -> CreditsMenu(back)
            SecretMenu.Hearth -> HearthKaminEgg(back)
            SecretMenu.Konami -> KonamiEgg(back)
            SecretMenu.Fluid -> OmegaFluidEgg(back)
            SecretMenu.Zenith -> ZenithWordEgg(back)
            SecretMenu.Sudo -> SudoEgg(back)
            SecretMenu.Hello -> HelloWorldEgg(back)
            SecretMenu.Pi -> PiEgg(back)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SecretPage(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    accent: List<Color> = AiFluidColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0B1C), Color(0xFF050508))))
            // Takes every touch: nothing below reacts while a menu is open.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        FluidBackdrop(accent.take(3), strength = 0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(64.dp))
            Text(title, style = TextStyle(brush = Brush.linearGradient(accent), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(30.dp))
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

@Composable
internal fun SecretCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            title,
            color = Color(0xFF7FB2FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF16161A).copy(alpha = 0.82f))
            .glassSheen(24.dp)
            .aiFluidEdge(24.dp, strength = 0.4f, width = 1.dp)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
internal fun SecretValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)) {
        Text(label, color = Color.White, fontSize = 15.sp)
        Text(value, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
    }
}

@Composable
internal fun SecretAction(label: String, detail: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .fluidTouch()
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (destructive) Color(0xFFFF6B60) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (detail != null) Text(detail, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun SecretSwitch(label: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 15.sp)
            Text(detail, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        GlassSwitch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            onColor = Color(0xFF3E91FF),
        )
    }
}

private fun gb(bytes: Long): String = String.format(Locale.GERMAN, "%.1f GB", bytes / 1_073_741_824.0)

private fun mb(bytes: Long): String = String.format(Locale.GERMAN, "%.0f MB", bytes / 1_048_576.0)

private fun since(millis: Long): String {
    val minutes = millis / 60_000
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    return (if (days > 0) "$days T " else "") + "$hours Std ${minutes % 60} Min"
}

// ---------------------------------------------------------------------------------------------
// *#0000# – the list of all codes
// ---------------------------------------------------------------------------------------------

@Composable
private fun CodesMenu(onOpen: (SecretMenu) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "codes") }
    SecretPage("Geheimcodes", "*#0000# · psst – im Finder eintippen", onClose) {
        SecretCard("Codes") {
            SecretMenu.entries.filter { it.listed && it != SecretMenu.Codes }.forEach { menu ->
                SecretAction("${menu.code}  ·  ${menu.title}", menu.what) { onOpen(menu) }
            }
        }
        SecretCard("Weitere Verstecke") {
            SecretValue("Wörter im Finder", "Manche Verstecke öffnen sich nicht mit Zahlen, sondern mit dem richtigen Wort. Die Easter-Egg-Karte verrät sie.")
            SecretValue("ZENITH Labs", "Über das Telefon → siebenmal auf die Build-Nummer tippen")
            SecretValue("Easter-Egg-Karte", "Über das Telefon → lange auf „made by …“ drücken")
            SecretValue("Diese Liste", "Im ZENITH-Menü lange auf den Titel drücken")
            SecretValue("ZENITH-Menü", "Einstellungen → Startbildschirm → Doppeltippen, oder lange auf den Startbildschirm drücken")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#0*# – hardware test
// ---------------------------------------------------------------------------------------------

private val TestColors = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black, Color(0xFF808080))

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

private fun vibrate(context: Context, effect: VibrationEffect) {
    runCatching { vibrator(context)?.vibrate(effect) }
}

@Composable
private fun TestMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    var fill by remember { mutableStateOf<Int?>(null) }
    var touch by remember { mutableStateOf(false) }
    val sensors = remember {
        runCatching { context.getSystemService(SensorManager::class.java)?.getSensorList(Sensor.TYPE_ALL).orEmpty() }.getOrDefault(emptyList())
    }
    SecretPage("Hardware-Test", "*#0*# · wie im Servicemenü", onClose, listOf(Color(0xFFFF453A), Color(0xFF34C759), Color(0xFF3E91FF))) {
        SecretCard("Display") {
            SecretAction("Farbtest", "Rot, Grün, Blau, Weiß, Schwarz, Grau – tippen für die nächste Farbe") { fill = 0 }
            SecretAction("Touch-Test", "Mit dem Finger über den ganzen Bildschirm malen") { touch = true }
        }
        SecretCard("Vibration") {
            SecretAction("Kurz") { vibrate(context, VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }
            SecretAction("Lang") { vibrate(context, VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE)) }
            SecretAction("Herzschlag") { vibrate(context, VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60, 500, 60, 120, 60), -1)) }
        }
        SecretCard("Sensoren (${sensors.size})") {
            sensors.take(60).forEach { SecretValue(it.name, it.vendor) }
        }
    }
    fill?.let { i ->
        BackHandler { fill = null }
        Box(
            Modifier
                .fillMaxSize()
                .background(TestColors[i])
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    fill = if (i + 1 < TestColors.size) i + 1 else null
                },
        )
    }
    if (touch) TouchTest { touch = false }
}

@Composable
private fun TouchTest(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDragEnd = {
                            strokes.add(current)
                            current = emptyList()
                        },
                        onDragCancel = {
                            strokes.add(current)
                            current = emptyList()
                        },
                        onDrag = { change, _ -> current = current + change.position },
                    )
                },
        ) {
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, y), Offset(size.width, y))
                y += step
            }
            (strokes + listOf(current)).forEach { points ->
                for (i in 1 until points.size) {
                    drawLine(Color(0xFF34C759), points[i - 1], points[i], strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text(
            "Malen zum Testen · zurück zum Beenden",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp),
        )
        GlassCircle(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            size = 44.dp,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#1234# – versions
// ---------------------------------------------------------------------------------------------

@Composable
private fun InfoMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val code = remember(info) { info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } }
    SecretPage("Versionen", "*#1234#", onClose) {
        SecretCard("${dev.hearth.launcher.data.Brand.name}") {
            SecretValue(HearthUi.NAME, "${info?.versionName.orEmpty()} (Build $code)")
            SecretValue("ClaudeOS", "${ClaudeOs.VERSION} „${ClaudeOs.CODENAME}“")
            SecretValue("Paket", context.packageName)
            SecretValue("Ausgabe", "${dev.hearth.launcher.BuildConfig.FLAVOR} · ${dev.hearth.launcher.BuildConfig.BUILD_TYPE}")
        }
        SecretCard("Android") {
            SecretValue("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            SecretValue("Sicherheitspatch", Build.VERSION.SECURITY_PATCH)
            SecretValue("Build", Build.DISPLAY)
            SecretValue("Fingerabdruck", Build.FINGERPRINT)
            SecretValue("Bootloader", Build.BOOTLOADER)
            SecretValue("Basisband", Build.getRadioVersion() ?: "–")
            SecretValue("Kernel", System.getProperty("os.version") ?: "–")
        }
        SecretCard("Gerät") {
            SecretValue("Modell", "${Build.MANUFACTURER} ${Build.MODEL}")
            SecretValue("Gerätecode", "${Build.DEVICE} · ${Build.PRODUCT}")
            SecretValue("Plattform", Build.HARDWARE)
            SecretValue("Prozessor-Architektur", Build.SUPPORTED_ABIS.joinToString())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#0228# – battery, live
// ---------------------------------------------------------------------------------------------

private class BatteryReadout(val lines: List<Pair<String, String>>, val level: Float?)

private fun readBattery(context: Context): BatteryReadout {
    val b = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val manager = context.getSystemService(BatteryManager::class.java)
    val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val s = (b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
    val level = if (l >= 0) l * 100 / s else null
    val status = when (b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Lädt"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Entlädt"
        BatteryManager.BATTERY_STATUS_FULL -> "Voll"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Lädt nicht"
        else -> "Unbekannt"
    }
    val plug = when (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Netzteil"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kabellos"
        0 -> "Nicht angeschlossen"
        else -> "Angeschlossen"
    }
    val health = when (b?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Verbraucht"
        BatteryManager.BATTERY_HEALTH_COLD -> "Zu kalt"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
        else -> "Unbekannt"
    }
    // Some phones report µA, some mA.
    val current = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        ?.let { if (kotlin.math.abs(it) > 20_000) it / 1000 else it }
    val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }
    val temperature = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
    val voltage = b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
    val cycles = if (Build.VERSION.SDK_INT >= 34) b?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null
    val lines = buildList {
        add("Ladestand" to (level?.let { "$it %" } ?: "–"))
        add("Status" to status)
        add("Stromquelle" to plug)
        current?.let { add("Strom" to "$it mA" + if (it > 0) " (rein)" else " (raus)") }
        counter?.let { add("Ladung" to "${it / 1000} mAh") }
        temperature?.let { add("Temperatur" to String.format(Locale.GERMAN, "%.1f °C", it / 10f)) }
        voltage?.let { add("Spannung" to String.format(Locale.GERMAN, "%.3f V", it / 1000f)) }
        add("Zustand" to health)
        cycles?.let { add("Ladezyklen" to "$it") }
        b?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }?.let { add("Technologie" to it) }
    }
    return BatteryReadout(lines, level?.div(100f))
}

@Composable
private fun BatteryMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            state = readBattery(context)
        }
    }
    val green = listOf(Color(0xFF34C759), Color(0xFF30D5C8), Color(0xFF3E91FF))
    SecretPage("Akku-Status", "*#0228# · aktualisiert sich laufend", onClose, green) {
        state.level?.let { level ->
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(level.coerceIn(0.02f, 1f))
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(green)),
                )
                Text(
                    "${(level * 100).toInt()} %",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        SecretCard("Messwerte") {
            state.lines.forEach { (label, value) -> SecretValue(label, value) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#9900# – diagnosis
// ---------------------------------------------------------------------------------------------

private fun readDiagnose(context: Context): List<Pair<String, String>> {
    val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
    val runtime = Runtime.getRuntime()
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val shizuku = when (ShizukuBridge.status(context)) {
        ShizukuBridge.Status.Ready -> "Bereit"
        ShizukuBridge.Status.NoPermission -> "Läuft, ${dev.hearth.launcher.data.Brand.name} hat noch keine Erlaubnis"
        ShizukuBridge.Status.NotRunning -> "Installiert, läuft nicht"
        ShizukuBridge.Status.NotInstalled -> "Nicht installiert"
    }
    return buildList {
        add("Arbeitsspeicher" to "${gb(memory.availMem)} von ${gb(memory.totalMem)} frei" + if (memory.lowMemory) " · knapp!" else "")
        add("${dev.hearth.launcher.data.Brand.name} selbst" to "${mb(runtime.totalMemory() - runtime.freeMemory())} von ${mb(runtime.maxMemory())}")
        stat?.let { add("Speicher" to "${gb(it.availableBytes)} von ${gb(it.totalBytes)} frei") }
        add("Prozessorkerne" to "${runtime.availableProcessors()}")
        add("Seit dem Start" to since(SystemClock.elapsedRealtime()))
        add("Davon wach" to since(SystemClock.uptimeMillis()))
        add("Shizuku" to shizuku)
        add("Animationen" to String.format(Locale.GERMAN, "%.1f×", ShizukuBridge.animationScale(context)))
        add("${dev.hearth.launcher.data.Brand.name}-Prozess" to "PID ${android.os.Process.myPid()}")
    }
}

@Composable
private fun DiagnoseMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(readDiagnose(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            lines = readDiagnose(context)
        }
    }
    SecretPage("Diagnose", "*#9900# · aktualisiert sich laufend", onClose, listOf(Color(0xFF8E6BFF), Color(0xFF3E91FF), Color(0xFF30D5C8))) {
        SecretCard("System") {
            lines.forEach { (label, value) -> SecretValue(label, value) }
        }
        SecretCard("Aufräumen") {
            SecretAction("${dev.hearth.launcher.data.Brand.name}-Speicher freigeben", "Gibt nicht mehr gebrauchten Speicher von ${dev.hearth.launcher.data.Brand.name} zurück") {
                System.gc()
                lines = readDiagnose(context)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// *#4327# – OMEGA Labs
// ---------------------------------------------------------------------------------------------

private fun systemSetting(context: Context, key: String): Boolean =
    runCatching { Settings.System.getInt(context.contentResolver, key, 0) == 1 }.getOrDefault(false)

/** Starts Hearth fresh: the home screen opens again and the old process ends. */
private fun restartHearth(context: Context) {
    val home = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    runCatching { context.startActivity(home) }
    android.os.Process.killProcess(android.os.Process.myPid())
}

@Composable
private fun LabsMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { HearthLabs.unlock(context) }
    val shizuku = remember { ShizukuBridge.isReady(context) }
    var touches by remember { mutableStateOf(systemSetting(context, "show_touches")) }
    var pointer by remember { mutableStateOf(systemSetting(context, "pointer_location")) }
    var bounds by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(ShizukuBridge.animationScale(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var eggMap by remember { mutableStateOf(false) }
    val needs = "Braucht Shizuku (Einstellungen → System)"
    SecretPage(
        "ZENITH Labs",
        "Experimente · nicht alles hier ist fertig",
        onClose,
        listOf(Color(0xFF34C759), Color(0xFF3E91FF), Color(0xFF8E6BFF)),
    ) {
        message?.let {
            Text(it, color = Color(0xFF34C759), fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
        }
        SecretCard("Anzeige") {
            SecretSwitch("Bildrate anzeigen", "Ein kleiner FPS-Zähler oben links auf dem Startbildschirm", HearthLabs.fps) {
                HearthLabs.setFps(context, it)
            }
            SecretSwitch("Fingertipps zeigen", if (shizuku) "Jede Berührung erscheint als Punkt – auch in anderen Apps" else needs, touches, shizuku) { on ->
                touches = on
                scope.launch { ShizukuBridge.run("settings put system show_touches ${if (on) 1 else 0}") }
            }
            SecretSwitch("Zeigerposition", if (shizuku) "Koordinaten und Spur jeder Berührung" else needs, pointer, shizuku) { on ->
                pointer = on
                scope.launch { ShizukuBridge.run("settings put system pointer_location ${if (on) 1 else 0}") }
            }
            SecretSwitch("Layout-Grenzen", if (shizuku) "Ränder aller Elemente, wie in den Entwickleroptionen" else needs, bounds, shizuku) { on ->
                bounds = on
                scope.launch { ShizukuBridge.run("setprop debug.layout $on; service call activity 1599295570") }
            }
        }
        SecretCard("Animationen") {
            Text(
                if (shizuku) "Für das ganze Handy – jetzt ${String.format(Locale.GERMAN, "%.1f×", scale)}" else needs,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0f to "Aus", 0.5f to "0,5×", 1f to "1×", 2f to "2×", 5f to "Zeitlupe").forEach { (value, label) ->
                    GlassCapsule(
                        onClick = {
                            if (shizuku) {
                                scope.launch {
                                    ShizukuBridge.setAnimationScale(value)
                                    scale = value
                                }
                            }
                        },
                        tint = if (scale == value) Color(0xFF3E91FF).copy(alpha = 0.5f) else null,
                        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        SecretCard("Werkzeuge") {
            SecretAction("Willkommens-Tour wieder zeigen", "Beim nächsten Öffnen des Startbildschirms") {
                HearthUi.resetIntro(context)
                message = "Die Tour kommt beim nächsten Start."
            }
            SecretAction("Easter-Egg-Karte", "Wo alle Eggs versteckt sind") { eggMap = true }
            SecretAction("${dev.hearth.launcher.data.Brand.name} neu starten", "Startet den Launcher frisch (Glimmer kurz mit)") { restartHearth(context) }
            SecretAction("Labs wieder verstecken", "Bis zu den nächsten sieben Tipps", destructive = true) {
                HearthLabs.hide(context)
                onClose()
            }
        }
    }
    if (eggMap) EggMap { eggMap = false }
}

/** OMEGA Labs' frame counter: how many frames the home screen draws each second. */
@Composable
fun FpsMeter(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var start = 0L
        while (true) {
            withFrameNanos { t ->
                if (start == 0L) start = t
                frames++
                if (t - start >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    start = t
                }
            }
        }
    }
    Text(
        "$fps fps",
        color = when {
            fps >= 55 -> Color(0xFF34C759)
            fps >= 30 -> Color(0xFFFFCC00)
            else -> Color(0xFFFF453A)
        },
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// *#2529# – Clawd studio
// ---------------------------------------------------------------------------------------------

private fun moodName(mood: ClawdMood): String = when (mood) {
    ClawdMood.Idle -> "Ruhig"
    ClawdMood.Thinking -> "Denkt"
    ClawdMood.Dance -> "Tanzt"
    ClawdMood.Flip -> "Salto"
    ClawdMood.Love -> "Verliebt"
    ClawdMood.Wave -> "Winkt"
    ClawdMood.Sleep -> "Schläft"
    ClawdMood.Jump -> "Springt"
    ClawdMood.Blush -> "Rot"
    ClawdMood.Dizzy -> "Schwindelig"
    ClawdMood.Surprised -> "Staunt"
}

@Composable
private fun ClawdStudio(onClose: () -> Unit) {
    var mood by remember { mutableStateOf(ClawdMood.Wave) }
    SecretPage(
        "Clawd-Studio",
        "*#2529# · C-L-A-W auf der Telefontastatur",
        onClose,
        listOf(Color(0xFFD97757), Color(0xFFFF6FB5), Color(0xFFFFB347)),
    ) {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            Clawd(Modifier.size(width = 180.dp, height = 154.dp), mood = mood)
        }
        Text(
            moodName(mood),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretCard("Stimmungen") {
            ClawdMood.entries.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { m ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (m == mood) Color(0xFFD97757).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f))
                                .clickable { mood = m }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(moodName(m), color = Color.White, fontSize = 13.sp)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        SecretCard {
            SecretAction("Auf den Startbildschirm schicken", "Clawd zeigt diese Stimmung gleich auch dort") {
                ClaudeAssistant.react(mood, 6000)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The power menu (*#7697#, the Hearth menu, a double tap)
// ---------------------------------------------------------------------------------------------

@Composable
fun PowerMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = remember { ShizukuBridge.isReady(context) }
    // Restart and power off ask for a second tap.
    var armed by remember { mutableStateOf<String?>(null) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f)) }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(22.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    scaleX = 0.85f + 0.15f * p
                    scaleY = 0.85f + 0.15f * p
                    alpha = p.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(34.dp))
                .background(Color(0xFF16161A).copy(alpha = 0.92f))
                .glassSheen(34.dp)
                .aiFluidEdge(34.dp, strength = 0.8f, width = 1.5.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ein/Aus", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PowerButton(Icons.Rounded.Lock, "Bildschirm\naus", Color(0xFF3E91FF), ready) {
                    scope.launch {
                        ShizukuBridge.screenOff()
                        onClose()
                    }
                }
                PowerButton(Icons.Rounded.Refresh, if (armed == "reboot") "Nochmal\ntippen" else "Neu\nstarten", Color(0xFF34C759), ready) {
                    if (armed == "reboot") scope.launch { ShizukuBridge.reboot() } else armed = "reboot"
                }
                PowerButton(Icons.Rounded.Warning, if (armed == "off") "Nochmal\ntippen" else "Aus-\nschalten", Color(0xFFFF453A), ready) {
                    if (armed == "off") scope.launch { ShizukuBridge.shutdown() } else armed = "off"
                }
            }
            if (!ready) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Braucht Shizuku – einrichten unter Einstellungen → System → Shizuku.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PowerButton(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(70.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// The Hearth menu: everything one tap away
// ---------------------------------------------------------------------------------------------

class HubTile(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

/**
 * The Hearth menu: a glass sheet from below with everything Hearth can do at hand. Holding
 * its title opens the list of secret codes.
 */
@Composable
fun HearthMenu(tiles: List<HubTile>, onSecret: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 300f)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        LiquidGlass(
            cornerRadius = 34.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    translationY = (1f - p) * 160.dp.toPx()
                    alpha = p.coerceIn(0f, 1f)
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101014).copy(alpha = 0.55f))
                    .padding(vertical = 18.dp, horizontal = 10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ZENITH 19: the Z heads its menu – held until charged, the secret codes; tapped
                    // as often as ZENITH's number, the Clawd Illuminati.
                    val menuContext = androidx.compose.ui.platform.LocalContext.current
                    val version = remember {
                        runCatching { menuContext.packageManager.getPackageInfo(menuContext.packageName, 0).versionName }.getOrNull().orEmpty()
                    }
                    ZenithLogo(size = 64.dp, onTap = rememberCrystalTaps(version), onCharged = onSecret)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onSecret() }) }) {
                        Text(
                            "ZENITH-Menü",
                            style = TextStyle(brush = Brush.linearGradient(fluidPalette()), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        )
                        Text("Z halten: geheime Codes", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    // Claude Mythos: the Clawds have taken over this menu too.
                    MythosRow(30.dp)
                    Clawd(Modifier.size(width = 40.dp, height = 34.dp), mood = ClawdMood.Wave)
                }
                Spacer(Modifier.height(12.dp))
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { tile ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        onClose()
                                        tile.onClick()
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(tile.color, tile.color.copy(alpha = 0.55f)))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(tile.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    tile.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
