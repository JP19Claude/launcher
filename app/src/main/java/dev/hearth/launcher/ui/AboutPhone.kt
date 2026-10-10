package dev.hearth.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One line of the phone's info: what it is and its value; [share] 0..1 draws a bar under it. */
private class Spec(
    val label: String,
    val value: String,
    val share: Float? = null,
    val claudeOs: Boolean = false,
    /** Hearth UI's own version: five taps open its easter egg. */
    val hearthEgg: Boolean = false,
    /** The build number: seven taps unlock OMEGA Labs, like Android's developer options. */
    val labs: Boolean = false,
)

private class SpecGroup(val title: String, val specs: List<Spec>)

private fun gb(bytes: Long): String = String.format(Locale.GERMAN, "%.1f GB", bytes / 1_073_741_824.0)

/** A system property (One UI's version, the marketing name …), or null where Android won't tell. */
private fun systemProperty(name: String): String? = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as? String
}.getOrNull()?.takeIf { it.isNotBlank() }

/** The phone's name as the user knows it (Samsung: "Galaxy S24"), else maker and model. */
private fun phoneName(context: Context): String =
    runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: systemProperty("ro.product.marketname")
        ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

/** One UI's version, from Samsung's build property (70000 → "7.0"); null on other phones. */
private fun oneUiVersion(): String? = systemProperty("ro.build.version.oneui")?.toIntOrNull()?.let { v ->
    val major = v / 10000
    val minor = (v % 10000) / 100
    "$major.$minor"
}

/** The fastest a core can run, read from the kernel (GHz). */
private fun maxCpuGhz(): String? = runCatching {
    (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
        File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
    }.maxOrNull()?.let { khz -> String.format(Locale.GERMAN, "%.2f GHz", khz / 1_000_000.0) }
}.getOrNull()

private fun uptime(): String {
    val minutes = SystemClock.elapsedRealtime() / 60_000
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return buildString {
        if (days > 0) append("$days T ")
        append("$hours Std $mins Min")
    }
}

private fun gatherSpecs(context: Context): List<SpecGroup> {
    val pm = context.packageManager
    val info = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
    val hearth = info?.versionName.orEmpty()
    val edition = if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) "ZENITH (Launcher, Glimmer und Clawd in einer App)" else "Hearth"
    val build = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }?.toString().orEmpty()

    val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val storageTotal = stat?.totalBytes ?: 0L
    val storageFree = stat?.availableBytes ?: 0L

    val wm = context.getSystemService(WindowManager::class.java)
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { context.display }.getOrNull() else null
    val maxHz = display?.supportedModes?.maxOfOrNull { it.refreshRate }
    val nowHz = display?.refreshRate

    val battery = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    val level = battery?.let { b ->
        val l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (l >= 0) l * 100 / s else null
    }
    val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Verbraucht"
        BatteryManager.BATTERY_HEALTH_COLD -> "Zu kalt"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
        else -> "Unbekannt"
    }
    val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
    val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
    val technology = battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
    val cycles = if (Build.VERSION.SDK_INT >= 34) battery?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null

    val chip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
    } else {
        ""
    }.ifBlank { Build.HARDWARE }

    return listOf(
        SpecGroup(
            if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) "ZENITH & ClaudeOS" else "Hearth & ClaudeOS",
            listOf(
                Spec(if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) "ZENITH-Version" else "Hearth-Version", hearth, hearthEgg = true),
                Spec("ClaudeOS-Version", "${ClaudeOs.VERSION} („${ClaudeOs.CODENAME}“)", claudeOs = true),
                Spec("Ausgabe", edition),
                Spec("Build-Nummer", build, labs = true),
                Spec("Design", "ZENITH 19 · Sonne · Liquid Glass · Zenit & Nadir"),
            ),
        ),
        SpecGroup(
            "Gerät",
            listOf(
                Spec("Name", phoneName(context)),
                Spec("Modell", Build.MODEL),
                Spec("Hersteller", Build.MANUFACTURER.replaceFirstChar { it.uppercase() }),
                Spec("Gerätecode", Build.DEVICE),
            ),
        ),
        SpecGroup(
            "Software",
            listOfNotNull(
                Spec("Android-Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
                oneUiVersion()?.let { Spec("One UI-Version", it) },
                Spec("Sicherheitspatch", Build.VERSION.SECURITY_PATCH),
                Spec("Build", Build.DISPLAY),
                System.getProperty("os.version")?.let { Spec("Kernel", it) },
            ),
        ),
        SpecGroup(
            "Leistung",
            listOfNotNull(
                Spec("Chip", chip),
                Spec("Kerne", "${Runtime.getRuntime().availableProcessors()}"),
                maxCpuGhz()?.let { Spec("Höchster Takt", it) },
                Spec(
                    "Arbeitsspeicher",
                    "${gb(memory.totalMem)} · ${gb(memory.availMem)} frei",
                    share = if (memory.totalMem > 0) 1f - memory.availMem.toFloat() / memory.totalMem else null,
                ),
                Spec(
                    "Speicher",
                    "${gb(storageTotal)} · ${gb(storageFree)} frei",
                    share = if (storageTotal > 0) 1f - storageFree.toFloat() / storageTotal else null,
                ),
            ),
        ),
        SpecGroup(
            "Display",
            listOfNotNull(
                Spec("Auflösung", "$w × $h"),
                Spec("Pixeldichte", "${context.resources.displayMetrics.densityDpi} dpi"),
                maxHz?.let { Spec("Bildrate", "bis ${it.toInt()} Hz" + (nowHz?.let { n -> " · jetzt ${n.toInt()} Hz" } ?: "")) },
            ),
        ),
        SpecGroup(
            "Akku",
            listOfNotNull(
                level?.let { Spec("Ladestand", "$it %" + if (plugged) " · lädt" else "", share = it / 100f) },
                Spec("Zustand", health),
                cycles?.let { Spec("Ladezyklen", "$it") },
                temperature?.let { Spec("Temperatur", String.format(Locale.GERMAN, "%.1f °C", it / 10f)) },
                voltage?.let { Spec("Spannung", String.format(Locale.GERMAN, "%.2f V", it / 1000f)) },
                technology?.takeIf { it.isNotBlank() }?.let { Spec("Technologie", it) },
            ),
        ),
        SpecGroup(
            "Laufzeit",
            listOf(
                Spec("Seit dem Start", uptime()),
                Spec("Stand", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())),
            ),
        ),
    )
}

/**
 * Like Samsung's "Telefoninfo": the phone big at the top (drawn, with Glimmer's island and
 * Clawd on its screen), its name, Hearth and ClaudeOS – then everything about it on glass
 * cards with One UI 10 Fluid. Tapping the ClaudeOS version five times opens its easter egg.
 */
@Composable
fun AboutPhoneScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf(gatherSpecs(context)) }
    var osEgg by remember { mutableStateOf(false) }
    // ZENITH 19, the ClaudeOS museum: held, the ClaudeOS version shows every ClaudeOS and its egg.
    var osMuseum by remember { mutableStateOf(false) }
    var osOld by remember { mutableStateOf<String?>(null) }
    // ZENITH 19, the Ruhmeshalle: held, the system's version shows every big version and its egg.
    var hall by remember { mutableStateOf(false) }
    var hallEgg by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    // Hearth UI's own easter egg, five taps on its version – next to ClaudeOS's.
    var hearthEgg by remember { mutableStateOf(false) }
    // Hidden: holding the "made by" line opens the map of all easter eggs.
    var eggMap by remember { mutableStateOf(false) }
    var hearthTaps by remember { mutableIntStateOf(0) }
    // Hidden: seven taps on the build number unlock OMEGA Labs.
    LaunchedEffect(Unit) { HearthLabs.init(context) }
    var labsOpen by remember { mutableStateOf(false) }
    var labsTaps by remember { mutableIntStateOf(0) }
    var labsHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(labsHint) {
        if (labsHint != null) {
            delay(1800)
            labsHint = null
        }
    }
    var hearthLastTap by remember { mutableLongStateOf(0L) }
    val appName = remember { dev.hearth.launcher.data.AppUpdater.appName(context) }
    // Battery, memory and uptime change: read again now and then.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            groups = gatherSpecs(context)
        }
    }
    BackHandler(enabled = !osEgg && !hearthEgg && !eggMap && !labsOpen && !osMuseum && osOld == null && !hall && hallEgg == null, onBack = onClose)
    val name = remember { phoneName(context) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070912), Color(0xFF050506))))) {
        FluidBackdrop(listOf(Color(0xFF3E91FF), Color(0xFF8E6BFF), Color(0xFFD97757)), strength = 0.9f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Box(
                Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // ZENITH 19: the ZENITH mark instead of the drawn phone – hold it like ColorOS's logo;
                // tapped as often as ZENITH's number, it opens the Clawd Illuminati.
                if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) {
                    val shownVersion = groups.first().specs.first().value
                    val crystal = rememberCrystalTaps(shownVersion)
                    // ZENITH 19.5: a tap on the card makes it grow, as tall as OxygenOS's; a tap on
                    // the card beside the Z makes it small again (the Z itself only ever opens it, so
                    // the quick taps for the Illuminati don't make it flicker).
                    var big by remember { mutableStateOf(false) }
                    val aspect by animateFloatAsState(
                        if (big) 0.62f else 0.8f,
                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                        label = "heroAspect",
                    )
                    // ZENITH 19: as ColorOS 17 shows its version – the Z big, the number huge.
                    ZenithVersionHero(
                        shownVersion,
                        Modifier
                            .padding(top = 10.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { big = !big },
                        onTap = {
                            crystal()
                            big = true
                        },
                        aspect = aspect,
                    )
                } else {
                    PhoneHero()
                }
                Spacer(Modifier.height(14.dp))
                Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(
                    "$appName ${groups.first().specs.first().value} · ${ClaudeOs.full} „${ClaudeOs.CODENAME}“",
                    style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "made by Julius Pranner und Claude Code",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { eggMap = true })
                    },
                )
                Spacer(Modifier.height(18.dp))
            }
            groups.forEach { group ->
                Text(
                    group.title,
                    color = Color(0xFF7FB2FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF16161A).copy(alpha = 0.82f))
                        .glassSheen(26.dp)
                        .aiFluidEdge(26.dp, strength = 0.45f, width = 1.dp)
                        .padding(vertical = 6.dp),
                ) {
                    group.specs.forEach { spec ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        spec.claudeOs -> Modifier.tapOrHold(
                                            remember { MutableInteractionSource() },
                                            onTap = {
                                                val now = System.currentTimeMillis()
                                                taps = if (now - lastTap < 1500) taps + 1 else 1
                                                lastTap = now
                                                if (taps >= 5) {
                                                    taps = 0
                                                    osEgg = true
                                                }
                                            },
                                            onHold = { osMuseum = true },
                                        )
                                        spec.hearthEgg -> Modifier.tapOrHold(
                                            remember { MutableInteractionSource() },
                                            onTap = {
                                                val now = System.currentTimeMillis()
                                                hearthTaps = if (now - hearthLastTap < 1500) hearthTaps + 1 else 1
                                                hearthLastTap = now
                                                if (hearthTaps >= 5) {
                                                    hearthTaps = 0
                                                    hearthEgg = true
                                                }
                                            },
                                            onHold = { hall = true },
                                        )
                                        spec.labs -> Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                                            if (HearthLabs.unlocked) {
                                                labsHint = "ZENITH Labs ist schon freigeschaltet"
                                                return@clickable
                                            }
                                            labsTaps++
                                            val left = 7 - labsTaps
                                            when {
                                                left <= 0 -> {
                                                    labsTaps = 0
                                                    HearthLabs.unlock(context)
                                                    labsHint = "ZENITH Labs freigeschaltet! 🧪"
                                                    labsOpen = true
                                                }
                                                left <= 4 -> labsHint = if (left == 1) "Noch 1 Tipp bis ZENITH Labs" else "Noch $left Tipps bis ZENITH Labs"
                                                else -> Unit
                                            }
                                        }
                                        else -> Modifier
                                    },
                                )
                                .fluidTouch()
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                        ) {
                            Text(spec.label, color = Color.White, fontSize = 16.sp)
                            Text(spec.value, color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp)
                            spec.share?.let { share ->
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(share.coerceIn(0.02f, 1f))
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Brush.horizontalGradient(AiFluidColors.take(3))),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (HearthLabs.unlocked) {
                Text(
                    "Versteckt",
                    color = Color(0xFF7FB2FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF16161A).copy(alpha = 0.82f))
                        .glassSheen(26.dp)
                        .aiFluidEdge(26.dp, strength = 0.45f, width = 1.dp)
                        .clickable { labsOpen = true }
                        .fluidTouch()
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                ) {
                    Text("ZENITH Labs 🧪", color = Color.White, fontSize = 16.sp)
                    Text("Experimente, Bildrate, Fingertipps, Layout-Grenzen …", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
        labsHint?.let { hint ->
            Text(
                hint,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 30.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A30).copy(alpha = 0.95f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(labsOpen, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            SecretMenuScreen(SecretMenu.Labs) { labsOpen = false }
        }
        AnimatedVisibility(osEgg, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
            ClaudeOsVersionEgg { osEgg = false }
        }
        if (hall) SystemHall(onPick = { hallEgg = it }, onClaudeOs = { osMuseum = true }, onClose = { hall = false })
        if (osMuseum) ClaudeOsMuseum(onPick = { osOld = it }, onClose = { osMuseum = false })
        osOld?.let { old -> ClaudeOsVersionEgg(old) { osOld = null } }
        hallEgg?.let { old -> HearthVersionEgg(old, systemEra(old).system) { hallEgg = null } }
        AnimatedVisibility(eggMap, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
            EggMap { eggMap = false }
        }
        AnimatedVisibility(hearthEgg, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            HearthVersionEgg(groups.first().specs.first().value, appName) { hearthEgg = false }
        }
    }
}

/** The phone, drawn: glass body with an AI Fluid rim, Glimmer's island at the top, Clawd on screen. */
@Composable
private fun PhoneHero() {
    val flow by rememberInfiniteTransition(label = "phone").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "screen",
    )
    Box(Modifier.size(width = 150.dp, height = 300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(30.dp.toPx())
            // The screen: Claude's colors drifting, like a fluid wallpaper.
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color(0xFF1B2450), Color(0xFF5B3BC8), Color(0xFFD97757)),
                    start = Offset(0f, size.height * flow),
                    end = Offset(size.width, size.height * (1f - flow)),
                ),
                cornerRadius = r,
            )
            // Glass light along the top.
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.4f),
                cornerRadius = r,
            )
            // The frame.
            drawRoundRect(Color(0xFF2A2A30), cornerRadius = r, style = Stroke(5.dp.toPx()))
            // Glimmer's island around the camera.
            val iw = size.width * 0.42f
            val ih = 18.dp.toPx()
            drawRoundRect(
                Color.Black,
                topLeft = Offset((size.width - iw) / 2f, 14.dp.toPx()),
                size = Size(iw, ih),
                cornerRadius = CornerRadius(ih / 2f),
            )
            drawCircle(Color(0xFF1C1C22), radius = 5.dp.toPx(), center = Offset(size.width / 2f + iw * 0.3f, 14.dp.toPx() + ih / 2f))
        }
        Box(Modifier.fillMaxSize().aiFluidEdge(30.dp, strength = 1f, width = 2.dp))
        Clawd(Modifier.size(width = 84.dp, height = 72.dp), mood = ClawdMood.Wave)
    }
}
