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
        tagline = "One UI 10 Fluid · Galaxy × Claude",
        planet = listOf(Color(0xFFBFDDFF), Color(0xFF3E91FF), Color(0xFF5B3BC8)),
        tips = listOf(
            "One UI 10 Fluid: Berührungen breiten sich wie Licht aus – unter Design → Animationen.",
            "Wisch auf dem Startbildschirm nach unten für das Kontrollzentrum.",
            "Frag Clawd in der Suche: „Wie lange noch bis Weihnachten?“",
            "Tipp Clawd auf dem Startbildschirm sechsmal schnell an …",
            "Hearth, Glimmer und Clawd aktualisieren sich jetzt selbst.",
        ),
    )
}

/**
 * The software update screen, each app with its own (One UI 10 Fluid): Hearth's version big
 * in blue and violet, Glimmer's living island, Clawd himself. What's new, a tip from Clawd,
 * and one button at the bottom that looks, downloads and installs – while Clawd runs along
 * the download. Tapping "Version" often enough opens an easter egg, like Android's in
 * Samsung's settings.
 */
@Composable
fun SoftwareUpdateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = remember { lookFor(context.packageName) }
    val name = remember { AppUpdater.appName(context) }
    val current = remember { AppUpdater.currentVersion(context) }
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
    val glow by rememberInfiniteTransition(label = "updateGlow").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glow",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(look.background))) {
        // One UI 10 Fluid: the app's colors drifting, and a glow behind the hero.
        FluidBackdrop(look.fluid, strength = 0.7f)
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(look.accent.copy(alpha = 0.30f * glow), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.28f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width / 2f, size.height * 0.28f),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Color.White)
            }
            // One UI's big title.
            Text(
                "Software-Update",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 22.dp),
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val shown = release?.version ?: current
                when (look.hero) {
                    UpdateHero.Number -> Unit
                    UpdateHero.Island -> IslandHero(shown, look.accent, newer = release != null)
                    UpdateHero.Clawd -> Clawd(
                        Modifier.size(width = 150.dp, height = 128.dp),
                        mood = when (s) {
                            is UpdateState.Newer, is UpdateState.Ready -> ClawdMood.Jump
                            is UpdateState.Downloading -> ClawdMood.Dance
                            is UpdateState.Failed -> ClawdMood.Dizzy
                            UpdateState.Checking -> ClawdMood.Thinking
                            UpdateState.UpToDate -> ClawdMood.Love
                        },
                    )
                }
                Text(
                    shown,
                    style = TextStyle(
                        brush = Brush.linearGradient(look.number),
                        fontSize = if (look.hero == UpdateHero.Number) 96.sp else 64.sp,
                        fontWeight = if (look.hero == UpdateHero.Clawd) FontWeight.Bold else FontWeight.Light,
                    ),
                )
                Text(name, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                // Like "One UI 8 · Android 16": the app on its ground.
                Text("auf ${dev.hearth.launcher.data.ClaudeOs.full}", color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(look.tagline, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    when (s) {
                        UpdateState.Checking -> "Nach Updates suchen …"
                        UpdateState.UpToDate -> "Deine Software ist auf dem neuesten Stand."
                        is UpdateState.Newer -> "Ein Software-Update ist verfügbar."
                        is UpdateState.Downloading -> "Update wird heruntergeladen …"
                        is UpdateState.Ready -> if (AppUpdater.canInstall(context)) {
                            "Bereit zur Installation. Android fragt gleich, ob du aktualisieren willst."
                        } else {
                            "Erlaube $name einmal „Unbekannte Apps installieren“ und tippe dann auf „Installieren“."
                        }
                        is UpdateState.Failed -> s.reason
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(24.dp))

            // About this version; "Version" hides the easter egg.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(look.card)
                    .glassSheen(26.dp)
                    .aiFluidEdge(26.dp, strength = 0.35f, enabled = LocalSettings.current.fluidDesign)
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

            if (release != null && release.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(look.card).glassSheen(26.dp).padding(20.dp)) {
                    Text("Was ist neu", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            // A tip from Clawd; a tap shows the next one.
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(look.card)
                    .glassSheen(26.dp)
                    .aiFluidEdge(26.dp, strength = 0.5f, enabled = LocalSettings.current.fluidDesign)
                    .fluidTouch(look.accent)
                    .clickable { tip++ }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Clawd(Modifier.size(width = 52.dp, height = 44.dp), mood = ClawdMood.Wave)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tipp von Clawd", color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(look.tips[Math.floorMod(tip, look.tips.size)], color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(160.dp))
        }

        // The one button at the bottom, like One UI's "Herunterladen und installieren".
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
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (strong) look.accent else Color.White.copy(alpha = 0.14f))
                        .glassSheen(26.dp)
                        .aiFluidEdge(26.dp, strength = if (strong) 1f else 0.45f, width = 2.dp, enabled = LocalSettings.current.fluidDesign)
                        .fluidTouch()
                        .clickable(enabled = s !is UpdateState.Checking, onClick = action),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (strong && look.hero == UpdateHero.Island) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(egg, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            VersionEgg(current, name, look) { egg = false }
        }
        AnimatedVisibility(osEgg, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
            ClaudeOsEgg { osEgg = false }
        }
    }
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
private fun ClaudeOsEgg(onClose: () -> Unit) {
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
    }
}
