package dev.hearth.launcher.ui

import android.content.Intent
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppUpdater
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.EasterEggs
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/** Where an update stands. */
private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    class Newer(val release: AppUpdater.Release) : UpdateState
    class Downloading(val release: AppUpdater.Release, val progress: Float) : UpdateState
    class Ready(val release: AppUpdater.Release, val apk: File) : UpdateState
    class Failed(val reason: String) : UpdateState
}

private val UpdateAccent = Color(0xFFD97757)
private val UpdateCard = Color(0xFF1C1C1E)

/** The last answer from GitHub, so the settings row doesn't ask again every time it's shown. */
private var lastResult: Pair<Long, AppUpdater.Check>? = null

private fun AppUpdater.Check.asState(): UpdateState = when (this) {
    is AppUpdater.Check.Newer -> UpdateState.Newer(release)
    AppUpdater.Check.UpToDate -> UpdateState.UpToDate
    is AppUpdater.Check.Failed -> UpdateState.Failed(reason)
}

/**
 * The "Software-Update" row in each app's settings: what it found (looked up when shown, at
 * most every few minutes), and a tap opens the software update screen.
 */
@Composable
fun AppUpdateContent() {
    val context = LocalContext.current
    val name = remember { AppUpdater.appName(context) }
    val current = remember { AppUpdater.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(Unit) {
        val cached = lastResult?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000L }?.second
        val result = cached ?: AppUpdater.check(context).also { lastResult = System.currentTimeMillis() to it }
        state = result.asState()
    }
    val newer = state as? UpdateState.Newer
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent().setClassName(context.packageName, "dev.hearth.launcher.UpdateActivity")) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Software-Update", color = Color.White, fontSize = 15.sp)
            Text(
                when (val s = state) {
                    UpdateState.Checking -> "$name $current · suche …"
                    UpdateState.UpToDate -> "$name $current · neueste Version"
                    is UpdateState.Newer -> "$name ${s.release.version} ist verfügbar"
                    is UpdateState.Failed -> "$name $current · ${s.reason}"
                    else -> "$name $current"
                },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
        }
        // Like One UI: a little "N" badge when there's an update.
        if (newer != null) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFF453A)),
                contentAlignment = Alignment.Center,
            ) {
                Text("N", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.65f))
    }
}

/** What the update screen's top shows: the version big, Glimmer's island, or Clawd himself. */
private enum class UpdateHero { Number, Island, Clawd }

/** Each app's own software update screen: its colors, its hero, its tips. */
private class UpdateLook(
    val background: List<Color>,
    val fluid: List<Color>,
    val number: List<Color>,
    val accent: Color,
    val card: Color,
    val hero: UpdateHero,
    val tagline: String,
    val planet: List<Color>,
    val tips: List<String>,
)

private fun lookFor(packageName: String): UpdateLook = when (packageName) {
    "dev.hearth.glimmer" -> UpdateLook(
        background = listOf(Color(0xFF000000), Color(0xFF0B0910)),
        fluid = listOf(Color(0xFFFF9F0A), Color(0xFF8E6BFF), Color(0xFF30D158)),
        number = listOf(Color.White, Color(0xFFFFD27A), Color(0xFFFF9F0A)),
        accent = Color(0xFFFF9F0A),
        card = Color(0xFF141416),
        hero = UpdateHero.Island,
        tagline = "Die Insel um deine Kamera",
        planet = listOf(Color(0xFF2A2A30), Color(0xFF000000), Color(0xFF000000)),
        tips = listOf(
            "Doppeltippe auf die Insel: Clawd-Orakel, Münze oder Würfel – einstellbar unter „Gestaltung“.",
            "Im Querformat steht Glimmer senkrecht um die Kamera, wie im Hochformat.",
            "Wisch über Musik in der Insel zum nächsten oder vorigen Titel.",
            "Mit „Launcher-Animation verdecken“ fliegen Apps auch bei One UI Home in die Insel.",
            "Clawd sagt dir in der Insel Hallo, wenn du dein Handy entsperrst.",
        ),
    )
    "dev.hearth.clawd" -> UpdateLook(
        background = listOf(Color(0xFF2A1F1A), Color(0xFF141216), Color(0xFF0E0D10)),
        fluid = listOf(Color(0xFFD97757), Color(0xFFFFB494), Color(0xFFE5845F)),
        number = listOf(Color(0xFFFFE3D3), Color(0xFFFFB494), Color(0xFFD97757)),
        accent = Color(0xFFD97757),
        card = Color(0xFF231A17),
        hero = UpdateHero.Clawd,
        tagline = "Dein kleiner Pixel-Freund",
        planet = listOf(Color(0xFFFFB494), Color(0xFFD97757), Color(0xFF7A3B26)),
        tips = listOf(
            "Neu: Countdown, Rätsel und Schnick-Schnack-Schnuck als Widgets.",
            "Sag im Chat „Rätsel“ und danach „Lösung“.",
            "Sieben Tage am Stück im Clawd-Tagebuch bringen ein Abzeichen.",
            "Fünfmal in Folge gegen Clawd gewinnen ist ein Easter Egg.",
            "Clawd-Atmen: eine ruhige Minute, er atmet mit dir.",
        ),
    )
    else -> UpdateLook(
        background = listOf(Color(0xFF050506), Color(0xFF08090F)),
        fluid = listOf(Color(0xFF3E91FF), Color(0xFF8E6BFF), Color(0xFFD97757)),
        number = listOf(Color(0xFFBFDDFF), Color(0xFF3E91FF), Color(0xFF8E6BFF)),
        accent = Color(0xFF3E91FF),
        card = Color(0xFF1C1C1E),
        hero = UpdateHero.Number,
        tagline = "Galaxy × Claude",
        planet = listOf(Color(0xFFBFDDFF), Color(0xFF3E91FF), Color(0xFF5B3BC8)),
        tips = listOf(
            "OMEGA UI 16: Halte zwei Handys mit Glimmer oben aneinander – Glimmer Drop geht auf.",
            "Dunkles Glas für Dock, Widgets und Menüs: Design → Dunkelmodus.",
            "Das OMEGA-Menü: lange auf den Startbildschirm drücken → „OMEGA-Menü“.",
            "Wisch auf dem Startbildschirm nach unten für den Finder, nach oben für den App-Drawer.",
            "Fluid-Ränder „Dezent“ oder „Lebendig“: unter Design → Animationen.",
            "Frag Clawd in der Suche: „Wie lange noch bis Weihnachten?“",
            "Tipp Clawd auf dem Startbildschirm sechsmal schnell an …",
            "Hearth, Glimmer und Clawd aktualisieren sich jetzt selbst.",
        ),
    )
}

/**
 * The software update screen, as ColorOS has it: the version written big with a glow behind
 * it (Glimmer: its living island, Clawd: himself), the status in a pill, and underneath all
 * that changed – this version's changelog in full, the versions before it as a history, the
 * details (where "Version" hides the easter eggs) and a tip from Clawd. One button at the
 * bottom looks, downloads and installs, while Clawd runs along the download.
 */
@Composable
fun SoftwareUpdateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = remember { lookFor(context.packageName) }
    val name = remember { AppUpdater.appName(context) }
    val current = remember { AppUpdater.currentVersion(context) }
    val app = remember { HearthChangelog.appOf(context.packageName) }
    val notes = remember(current) { HearthChangelog.forVersion(current) }
    val history = remember(current) { HearthChangelog.before(current) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var lastCheck by remember { mutableLongStateOf(AppUpdater.lastChecked(context)) }
    var egg by remember { mutableStateOf(false) }
    // ClaudeOS has its own easter egg, like Android's under One UI.
    var osEgg by remember { mutableStateOf(false) }
    var osTaps by remember { mutableIntStateOf(0) }
    var osLastTap by remember { mutableLongStateOf(0L) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var tip by remember { mutableIntStateOf((0 until look.tips.size).random()) }
    val buildNumber = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.lastUpdateTime))
            "${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)} · installiert am $date"
        }.getOrDefault("–")
    }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            val result = AppUpdater.check(context)
            lastResult = System.currentTimeMillis() to result
            lastCheck = AppUpdater.lastChecked(context)
            state = result.asState()
        }
    }

    fun install(release: AppUpdater.Release, apk: File) {
        // Android lets an app install updates only once it's allowed to (asked once).
        if (!AppUpdater.canInstall(context)) {
            AppUpdater.askInstallPermission(context)
            state = UpdateState.Ready(release, apk)
            return
        }
        state = UpdateState.Ready(release, apk)
        if (!AppUpdater.install(context, apk)) state = UpdateState.Failed("Die Installation ließ sich nicht starten")
    }

    fun download(release: AppUpdater.Release) {
        state = UpdateState.Downloading(release, 0f)
        scope.launch {
            val apk = AppUpdater.download(context, release) { p ->
                scope.launch { if (state is UpdateState.Downloading) state = UpdateState.Downloading(release, p) }
            }
            if (apk == null) {
                state = UpdateState.Failed("Download fehlgeschlagen – bist du online?")
                return@launch
            }
            install(release, apk)
        }
    }

    LaunchedEffect(Unit) { check() }
    BackHandler(enabled = !egg && !osEgg, onBack = onClose)

    val s = state
    val release = when (s) {
        is UpdateState.Newer -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        else -> null
    }
    val animate = LocalSettings.current.animations
    // The page settles in from below, as ColorOS's does.
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f)) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(look.background))) {
        FluidBackdrop(look.fluid, strength = 0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ColorOS: a slim bar – back, and the title next to it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircle(onClick = onClose, size = 42.dp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text("Software-Update", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = appear.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * 40.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val shown = release?.version ?: current
                when (look.hero) {
                    // OMEGA UI 17: the ruby with the Ω, the system version under it.
                    UpdateHero.Number -> if (dev.hearth.launcher.BuildConfig.ALL_IN_ONE) {
                        OmegaRuby(Modifier.padding(top = 20.dp, bottom = 6.dp), size = 170.dp, version = "OMEGA UI ${HearthUi.major(shown)}")
                    } else {
                        VersionArtwork(HearthUi.major(shown), look.number, height = 220.dp)
                    }
                    UpdateHero.Island -> Box(Modifier.padding(top = 36.dp, bottom = 10.dp)) {
                        IslandHero(shown, look.accent, newer = release != null)
                    }
                    UpdateHero.Clawd -> Clawd(
                        Modifier
                            .padding(top = 24.dp, bottom = 8.dp)
                            .size(width = 150.dp, height = 128.dp),
                        mood = when (s) {
                            is UpdateState.Newer, is UpdateState.Ready -> ClawdMood.Jump
                            is UpdateState.Downloading -> ClawdMood.Dance
                            is UpdateState.Failed -> ClawdMood.Dizzy
                            UpdateState.Checking -> ClawdMood.Thinking
                            UpdateState.UpToDate -> ClawdMood.Love
                        },
                    )
                }
                Text("$name $shown", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                // Like "One UI 8 · Android 16": the app on its ground.
                Text(
                    "${dev.hearth.launcher.data.ClaudeOs.full} „${dev.hearth.launcher.data.ClaudeOs.CODENAME}“ · ${look.tagline}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                StatusPill(s, look.accent)
                val detail = when (s) {
                    is UpdateState.Ready -> if (AppUpdater.canInstall(context)) {
                        "Bereit zur Installation. Android fragt gleich, ob du aktualisieren willst."
                    } else {
                        "Erlaube $name einmal „Unbekannte Apps installieren“ und tippe dann auf „Installieren“."
                    }
                    is UpdateState.Failed -> s.reason
                    else -> null
                }
                if (detail != null) {
                    Text(
                        detail,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            // A newer version: what it brings, first.
            if (release != null) {
                NewVersionCard(name, release, look)
                Spacer(Modifier.height(14.dp))
            }

            // This version's changelog, all of it.
            notes?.let { log ->
                ChangelogCard(
                    release = log,
                    app = app,
                    title = "Was ist neu in $name ${log.version}",
                    accent = look.accent,
                    card = look.card,
                )
            }

            UpdateSectionTitle("Versionsdetails")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(look.card)
                    .glassSheen(26.dp)
                    .padding(vertical = 6.dp),
            ) {
                // Tapping the version quickly, like Android's in Samsung's settings, opens the egg.
                val tapVersion = {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < 1500) taps + 1 else 1
                    lastTap = now
                    if (taps >= 5) {
                        taps = 0
                        egg = true
                    }
                }
                InfoRow("$name-Version", current, Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = tapVersion))
                InfoRow(
                    "${dev.hearth.launcher.data.ClaudeOs.NAME}-Version",
                    "${dev.hearth.launcher.data.ClaudeOs.VERSION} („${dev.hearth.launcher.data.ClaudeOs.CODENAME}“)",
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                        val now = System.currentTimeMillis()
                        osTaps = if (now - osLastTap < 1500) osTaps + 1 else 1
                        osLastTap = now
                        if (osTaps >= 5) {
                            osTaps = 0
                            osEgg = true
                        }
                    },
                )
                InfoRow("Build-Nummer", buildNumber)
                if (release != null) InfoRow("Neue Version", "${release.version} · ${megabytes(release.size)}")
                InfoRow(
                    "Letzte Prüfung",
                    if (lastCheck > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastCheck)) else "Noch nie",
                )
                InfoRow("Quelle", "GitHub · JP19Claude/launcher")
            }

            if (history.isNotEmpty()) {
                UpdateSectionTitle("Update-Verlauf")
                UpdateHistoryCard(history, app, look.accent, look.card)
            }

            // A tip from Clawd; a tap shows the next one.
            UpdateSectionTitle("Tipp von Clawd")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(look.card)
                    .glassSheen(26.dp)
                    .fluidTouch(look.accent)
                    .clickable { tip++ }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Clawd(Modifier.size(width = 52.dp, height = 44.dp), mood = ClawdMood.Wave)
                Spacer(Modifier.width(12.dp))
                Text(
                    look.tips[Math.floorMod(tip, look.tips.size)],
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            // Hearth (on its own) can move to Hearth UI: everything in one app.
            if (!dev.hearth.launcher.BuildConfig.ALL_IN_ONE && context.packageName == "dev.hearth.launcher") {
                Spacer(Modifier.height(14.dp))
                HearthOneCard(look.accent)
            }
            // At the very bottom: going back to any earlier version.
            UpdateSectionTitle("Downgrade")
            DowngradeCard(name, current, look)
            Spacer(Modifier.height(160.dp))
        }

        // The one button at the bottom, a wide pill like ColorOS's.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, look.background.last(), look.background.last())))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s is UpdateState.Downloading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(s.progress * 100).toInt()} %", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${megabytes((s.release.size * s.progress).toLong())} / ${megabytes(s.release.size)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Clawd runs along the download.
                BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
                    val run = (maxWidth - 40.dp) * s.progress.coerceIn(0f, 1f)
                    Clawd(Modifier.offset(x = run).size(width = 40.dp, height = 34.dp).align(Alignment.BottomStart), mood = ClawdMood.Dance)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                    Box(
                        Modifier
                            .fillMaxWidth(s.progress.coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(look.number.drop(1))),
                    )
                }
            } else {
                val (label, action) = when (s) {
                    UpdateState.Checking -> "Suche …" to {}
                    UpdateState.UpToDate, is UpdateState.Failed -> "Nach Updates suchen" to { check() }
                    is UpdateState.Newer -> "Herunterladen und installieren" to { download(s.release) }
                    is UpdateState.Ready -> "Installieren" to { install(s.release, s.apk) }
                    else -> "" to {}
                }
                val strong = s is UpdateState.Newer || s is UpdateState.Ready
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(
                            if (strong) Brush.horizontalGradient(look.number.drop(1)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.10f))),
                        )
                        .glassSheen(27.dp)
                        .aiFluidEdge(27.dp, strength = if (strong) 0.9f else 0.35f, width = 1.5.dp, enabled = LocalSettings.current.fluidDesign, flowing = strong)
                        .fluidTouch()
                        .clickable(enabled = s !is UpdateState.Checking, onClick = action),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (strong && look.hero == UpdateHero.Island) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(egg, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            HearthVersionEgg(current, name) { egg = false }
        }
        AnimatedVisibility(osEgg, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
            ClaudeOsVersionEgg { osEgg = false }
        }
    }
}

/**
 * The app's own easter egg for [version] – a new one with every big version: Hearth UI 14
 * has the prism, 13.5 the drops, 13 silk, 12.5 the fluid ocean, before that the planet.
 */
@Composable
internal fun HearthVersionEgg(version: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val look = remember { lookFor(context.packageName) }
    val number = versionNumber(version)
    when {
        number >= 17f -> RubyEgg(version, name, onClose)
        number >= 16f -> FireflyEgg(version, name, onClose)
        number >= 15.5f -> SparkEgg(version, name, onClose)
        number >= 15f -> VaultEgg(version, name, onClose)
        number >= 14.5f -> CometEgg(version, name, onClose)
        number >= 14f -> PrismEgg(version, name, onClose)
        number >= 13.5f -> DropsEgg(version, name, onClose)
        number >= 13f -> SilkEgg(version, name, onClose)
        number >= 12.5f -> FluidOceanEgg(version, name, onClose)
        else -> VersionEgg(version, name, look, onClose)
    }
}

/** Where the check stands, in a pill under the version (a spinner while it looks). */
@Composable
private fun StatusPill(state: UpdateState, accent: Color) {
    val (text, ok) = when (state) {
        UpdateState.Checking -> "Suche nach Updates …" to false
        UpdateState.UpToDate -> "Dein System ist auf dem neuesten Stand" to true
        is UpdateState.Newer -> "Neue Version ${state.release.version} verfügbar" to false
        is UpdateState.Downloading -> "Update wird heruntergeladen …" to false
        is UpdateState.Ready -> "Bereit zur Installation" to true
        is UpdateState.Failed -> "Suche fehlgeschlagen" to false
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            state is UpdateState.Checking || state is UpdateState.Downloading -> CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = accent,
                strokeWidth = 2.dp,
            )
            ok -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF30C26B), modifier = Modifier.size(18.dp))
            state is UpdateState.Failed -> Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(18.dp))
            else -> Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A newer version found: its name, size and what it brings, as ColorOS lists "Update-Details". */
@Composable
private fun NewVersionCard(name: String, release: AppUpdater.Release, look: UpdateLook) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(look.card)
            .glassSheen(26.dp)
            .aiFluidEdge(26.dp, strength = 0.8f, width = 1.5.dp, enabled = LocalSettings.current.fluidDesign)
            .padding(20.dp),
    ) {
        Text("$name ${release.version}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text("Größe: ${megabytes(release.size)}", color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(release.notes, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** Where going back to an older version stands. */
private sealed interface Downgrade {
    data object Idle : Downgrade
    data object Loading : Downgrade
    class Pick(val releases: List<AppUpdater.Release>) : Downgrade
    class Failed(val reason: String) : Downgrade
    class Downloading(val release: AppUpdater.Release, val progress: Float) : Downgrade
    class Installing(val release: AppUpdater.Release) : Downgrade
    /** Android's installer was asked; if it says no, the other ways are a tap away. */
    class Asked(val release: AppUpdater.Release) : Downgrade
    /** Android wouldn't install the older version over the newer one: Shizuku, or by hand. */
    class NeedsShizuku(val release: AppUpdater.Release) : Downgrade
}

/**
 * Downgrade: every published version of the app, to go back to any of them. With Shizuku the
 * older version installs right over the newer one and everything stays; without it Android
 * refuses, so the card says how to do it by hand.
 */
@Composable
private fun DowngradeCard(name: String, current: String, look: UpdateLook) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Downgrade>(Downgrade.Idle) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun goBack(release: AppUpdater.Release) {
        val shizuku = dev.hearth.launcher.data.ShizukuBridge.isReady(context)
        // Without Shizuku the app installs it itself (asking Android to allow the older
        // version); that needs "install unknown apps" once.
        if (!shizuku && !AppUpdater.canInstall(context)) {
            AppUpdater.askInstallPermission(context)
            return
        }
        state = Downgrade.Downloading(release, 0f)
        scope.launch {
            val apk = AppUpdater.download(context, release) { p ->
                scope.launch { if (state is Downgrade.Downloading) state = Downgrade.Downloading(release, p) }
            }
            if (apk == null) {
                state = Downgrade.Failed("Download fehlgeschlagen – bist du online?")
                return@launch
            }
            state = Downgrade.Installing(release)
            if (shizuku) {
                // Hearth closes while its older self is installed; it comes back on its own.
                if (!dev.hearth.launcher.data.ShizukuBridge.installApk(apk, allowDowngrade = true)) {
                    state = Downgrade.NeedsShizuku(release)
                }
            } else if (!AppUpdater.install(context, apk, downgrade = true)) {
                state = Downgrade.NeedsShizuku(release)
            } else {
                state = Downgrade.Asked(release)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(look.card)
            .glassSheen(26.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(
            "Zurück zu einer früheren Version von $name – deine Einstellungen bleiben. Ältere Versionen können Funktionen nicht haben, die du jetzt nutzt.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when (val s = state) {
            Downgrade.Idle -> DowngradeRow("Frühere Versionen anzeigen", look.accent) {
                state = Downgrade.Loading
                scope.launch {
                    state = AppUpdater.allVersions(context).fold(
                        onSuccess = { list -> Downgrade.Pick(list.filter { AppUpdater.isNewer(current, it.version) }) },
                        onFailure = { Downgrade.Failed(it.message ?: "Keine Verbindung – bist du online?") },
                    )
                }
            }
            Downgrade.Loading -> DowngradeRow("Lade die Versionen …", look.accent) {}
            is Downgrade.Pick -> {
                if (s.releases.isEmpty()) {
                    DowngradeRow("Keine älteren Versionen gefunden", look.accent) {}
                }
                s.releases.forEach { release ->
                    val asked = confirm == release.version
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (asked) {
                                    confirm = null
                                    goBack(release)
                                } else {
                                    confirm = release.version
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$name ${release.version}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (asked) "Nochmal tippen, um auf ${release.version} zu wechseln" else megabytes(release.size),
                                color = if (asked) look.accent else Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            is Downgrade.Failed -> {
                Text(s.reason, color = Color(0xFFFF9F0A), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                DowngradeRow("Nochmal versuchen", look.accent) { state = Downgrade.Idle }
            }
            is Downgrade.Downloading -> DowngradeRow("Lade ${s.release.version} … ${(s.progress * 100).toInt()} %", look.accent) {}
            is Downgrade.Installing -> DowngradeRow("Installiere ${s.release.version} – $name startet gleich neu …", look.accent) {}
            is Downgrade.Asked -> {
                Text(
                    "Android fragt jetzt, ob du $name ${s.release.version} installieren willst. Kommt „App nicht installiert“, lässt dein Handy den Downgrade " +
                        "ohne Shizuku nicht zu – dann hilft einer der Wege unten.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                DowngradeRow("Andere Wege anzeigen", look.accent) { state = Downgrade.NeedsShizuku(s.release) }
            }
            is Downgrade.NeedsShizuku -> {
                Text(
                    "Android installiert eine ältere Version nicht über eine neuere. Zwei Wege:\n" +
                        "1. Mit Shizuku (Einstellungen → System): dann klappt es hier mit einem Tipp, und alles bleibt.\n" +
                        "2. Von Hand: Hearth sichern (Einstellungen → Allgemein → Sichern), die Datei herunterladen, $name deinstallieren, " +
                        "die Datei aus „Downloads“ installieren und die Sicherung wieder einspielen.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                DowngradeRow("${s.release.version} herunterladen", look.accent) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                DowngradeRow("$name deinstallieren", look.accent) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                DowngradeRow("Zurück zur Liste", look.accent) { state = Downgrade.Idle }
            }
        }
    }
}

@Composable
private fun DowngradeRow(text: String, accent: Color, onClick: () -> Unit) {
    Text(
        text,
        color = accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun UpdateSectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Glimmer's hero: a living island (it breathes and glows), Clawd riding in it with the version. */
@Composable
private fun IslandHero(version: String, accent: Color, newer: Boolean) {
    val pulse by rememberInfiniteTransition(label = "island").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "breathe",
    )
    val width = 210.dp + 40.dp * pulse
    Box(Modifier.padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
        // The glow around it, like Glimmer's when something new comes.
        Canvas(Modifier.size(width = width + 40.dp, height = 96.dp)) {
            for (i in 1..4) {
                val spread = i * 5.dp.toPx() * (0.6f + 0.4f * pulse)
                drawRoundRect(
                    accent.copy(alpha = (if (newer) 0.22f else 0.12f) / i),
                    topLeft = Offset(20.dp.toPx() - spread, 16.dp.toPx() - spread),
                    size = androidx.compose.ui.geometry.Size(size.width - 40.dp.toPx() + spread * 2, 64.dp.toPx() + spread * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx() + spread),
                )
            }
        }
        Row(
            Modifier
                .size(width = width, height = 64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clawd(Modifier.size(width = 42.dp, height = 36.dp), mood = if (newer) ClawdMood.Jump else ClawdMood.Wave)
            Spacer(Modifier.weight(1f))
            Text("Glimmer $version", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            // The camera, as the island has it.
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF1C1C22)))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "0 MB" else String.format(java.util.Locale.GERMAN, "%.1f MB", bytes / 1_048_576.0)

/** One emoji flying out of the version badge. */
private class Spark(val emoji: String, val angle: Float, val speed: Float, val born: Long)

private val SparkEmojis = listOf("🧡", "✨", "🐞", "🎉", "⭐", "🦀", "💫", "🪐", "🍕", "🎈")

/**
 * The easter egg behind "Version", like Android's own in Samsung's settings: the version as a
 * glowing planet (Glimmer: as a big island) with Clawd circling it under twinkling stars. A
 * tap bounces it and makes him flip; holding sends a spiral of emojis flying out.
 */
@Composable
private fun VersionEgg(version: String, name: String, look: UpdateLook, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EasterEggs.find(context, "version") }
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val orbit by rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "angle",
    )
    val bounce = remember { Animatable(1f) }
    var mood by remember { mutableStateOf(ClawdMood.Wave) }
    val moods = remember { listOf(ClawdMood.Flip, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Jump, ClawdMood.Surprised, ClawdMood.Wave) }
    var moodIndex by remember { mutableIntStateOf(0) }
    val sparks = remember { mutableStateListOf<Spark>() }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                now = t
                sparks.removeAll { t - it.born > 4200 }
            }
        }
    }
    val stars = remember { List(70) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat() * 6f) } }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val island = look.hero == UpdateHero.Island

    fun burst(count: Int) {
        val t = now
        repeat(count) { i ->
            sparks.add(
                Spark(
                    SparkEmojis.random(),
                    (i.toFloat() / count) * (2 * Math.PI).toFloat() + (Math.random().toFloat() * 0.3f),
                    180f + Math.random().toFloat() * 260f,
                    t,
                ),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07061A), Color(0xFF120C2E), Color(0xFF050506)))),
        contentAlignment = Alignment.Center,
    ) {
        // Twinkling pixel stars and the flying emojis.
        Canvas(Modifier.fillMaxSize()) {
            val px = 3.dp.toPx()
            stars.forEach { (x, y, phase) ->
                val a = 0.25f + 0.75f * ((sin(now / 600f + phase) + 1f) / 2f)
                drawRect(Color.White.copy(alpha = a), Offset(x * size.width, y * size.height), androidx.compose.ui.geometry.Size(px, px))
            }
            paint.textSize = 30.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawIntoCanvas { canvas ->
                sparks.forEach { s ->
                    val age = (now - s.born) / 1000f
                    val r = s.speed * age
                    // Spiralling out like Android's emoji swirl.
                    val a = s.angle + age * 1.4f
                    paint.alpha = (255 * (1f - ((age - 2.6f) / 1.6f).coerceIn(0f, 1f))).toInt()
                    canvas.nativeCanvas.drawText(s.emoji, c.x + cos(a) * r, c.y + sin(a) * r + paint.textSize / 3f, paint)
                }
            }
        }

        // The version as a glowing planet – or, for Glimmer, as a big island.
        Box(
            Modifier
                .size(width = if (island) 300.dp else 230.dp, height = if (island) 110.dp else 230.dp)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .clip(if (island) RoundedCornerShape(55.dp) else CircleShape)
                .background(Brush.radialGradient(look.planet))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            moodIndex++
                            mood = moods[moodIndex % moods.size]
                            burst(8)
                            scope.launch {
                                bounce.snapTo(0.9f)
                                bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 300f))
                            }
                        },
                        onLongPress = { burst(48) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(version, color = Color.White, fontSize = if (island) 52.sp else 72.sp, fontWeight = FontWeight.Bold)
        }

        // Clawd circling it.
        Clawd(
            Modifier
                .size(width = 70.dp, height = 60.dp)
                .graphicsLayer {
                    val r = 165.dp.toPx()
                    translationX = cos(orbit) * r
                    translationY = sin(orbit) * r * (if (island) 0.6f else 0.85f)
                },
            mood = mood,
        )

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$name $version", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Antippen · gedrückt halten", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
    }
}

/** A little spark flung off the spinning Claude star. */
private class Ember(val angle: Float, val speed: Float, val born: Long, val size: Float)

/**
 * ClaudeOS's own easter egg, like Android's under One UI: the Claude star, big and glowing,
 * with its version and codename. Drag it round and it spins; spinning fast it throws off
 * embers, and after ten whole turns Clawds tumble out and dance round it.
 */
@Composable
internal fun ClaudeOsEgg(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    var rotation by remember { mutableStateOf(0f) }
    var velocity by remember { mutableStateOf(0f) }
    var turned by remember { mutableStateOf(0f) }
    var now by remember { mutableLongStateOf(0L) }
    var clawds by remember { mutableStateOf(false) }
    val embers = remember { mutableStateListOf<Ember>() }
    val center = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last == 0L) 0f else (t - last) / 1000f
                last = t
                now = t
                // It keeps spinning after you let go, slowing down little by little.
                rotation += velocity * dt
                turned += kotlin.math.abs(velocity * dt)
                velocity *= 0.985f
                if (kotlin.math.abs(velocity) > 540f && (0..2).random() == 0) {
                    embers.add(Ember((Math.random() * 2 * Math.PI).toFloat(), 240f + Math.random().toFloat() * 320f, t, 4f + Math.random().toFloat() * 8f))
                }
                embers.removeAll { t - it.born > 1800 }
                if (!clawds && turned > 3600f) {
                    clawds = true
                    EasterEggs.find(context, "claudeos")
                }
            }
        }
    }
    val dance by rememberInfiniteTransition(label = "osDance").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "dance",
    )
    val glow = (kotlin.math.abs(velocity) / 900f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0F0B), Color(0xFF0B0707), Color(0xFF000000))))
            .pointerInput(Unit) {
                // Dragging round the middle spins the star; the speed stays when you let go.
                detectDragGestures(
                    onDrag = { change, drag ->
                        val c = center.value
                        val p = change.position
                        val before = p - drag
                        val a0 = kotlin.math.atan2(before.y - c.y, before.x - c.x)
                        val a1 = kotlin.math.atan2(p.y - c.y, p.x - c.x)
                        var d = Math.toDegrees((a1 - a0).toDouble()).toFloat()
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        rotation += d
                        val dtMs = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                        velocity = (velocity * 0.5f + d / dtMs * 1000f * 0.5f).coerceIn(-2400f, 2400f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            center.value = Offset(size.width / 2f, size.height / 2f)
            val c = center.value
            // The glow grows with the speed.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFD97757).copy(alpha = 0.25f + 0.45f * glow), Color(0xFF8E6BFF).copy(alpha = 0.12f * glow), Color.Transparent),
                    center = c,
                    radius = size.minDimension * (0.45f + 0.2f * glow),
                ),
                radius = size.minDimension * (0.45f + 0.2f * glow),
                center = c,
            )
            embers.forEach { e ->
                val age = (now - e.born) / 1000f
                val r = 110.dp.toPx() + e.speed * age
                val alpha = (1f - age / 1.8f).coerceIn(0f, 1f)
                drawCircle(
                    AiFluidColors[(e.size.toInt()) % AiFluidColors.size].copy(alpha = alpha),
                    radius = e.size * (1f - age / 2f).coerceAtLeast(0.2f),
                    center = Offset(c.x + kotlin.math.cos(e.angle) * r, c.y + kotlin.math.sin(e.angle) * r),
                )
            }
        }
        ClaudeSpark(
            Color(0xFFD97757),
            Modifier.size(200.dp).graphicsLayer {
                rotationZ = rotation
                val s = 1f + 0.08f * glow
                scaleX = s
                scaleY = s
            },
        )
        // Ten whole turns: Clawds tumble out and dance round the star.
        if (clawds) {
            listOf(ClawdMood.Dance, ClawdMood.Flip, ClawdMood.Love, ClawdMood.Jump, ClawdMood.Wave).forEachIndexed { i, mood ->
                Clawd(
                    Modifier
                        .size(width = 56.dp, height = 48.dp)
                        .graphicsLayer {
                            val a = dance + i * (2 * Math.PI / 5).toFloat()
                            val r = 150.dp.toPx()
                            translationX = kotlin.math.cos(a) * r
                            translationY = kotlin.math.sin(a) * r
                        },
                    mood = mood,
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                dev.hearth.launcher.data.ClaudeOs.full,
                style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Text("Codename „${dev.hearth.launcher.data.ClaudeOs.CODENAME}“", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (clawds) "Die Clawds sind los! 🎉" else "Dreh den Stern im Kreis",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
        }
        // At the end: ClaudeOS 1's own Claude mark.
        ClaudeOsFinale(clawds, ClaudeMarkStyle.Ember)
    }
}
