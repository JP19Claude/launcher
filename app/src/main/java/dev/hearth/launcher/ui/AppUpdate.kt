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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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

/**
 * The software update screen, like One UI 8.5's: the version big and glowing, what's new,
 * and one button at the bottom that looks, downloads and installs. Tapping "Version" often
 * enough opens an easter egg, like Android's own in Samsung's settings.
 */
@Composable
fun SoftwareUpdateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = remember { AppUpdater.appName(context) }
    val current = remember { AppUpdater.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var lastCheck by remember { mutableLongStateOf(AppUpdater.lastChecked(context)) }
    var egg by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }

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
    BackHandler(enabled = !egg, onBack = onClose)

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

    Box(Modifier.fillMaxSize().background(Color(0xFF050506))) {
        // A soft glow in Claude's color behind the version, breathing slowly.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(UpdateAccent.copy(alpha = 0.34f * glow), Color(0xFF6A4BFF).copy(alpha = 0.12f * glow), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width / 2f, size.height * 0.3f),
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
                modifier = Modifier.padding(start = 6.dp, top = 28.dp, bottom = 26.dp),
            )
            // The version, big, the way One UI 8.5 shows its own.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    release?.version ?: current,
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFFFD2BF), UpdateAccent, Color(0xFF9C7BFF))),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Light,
                    ),
                )
                Text(name, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(18.dp))
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
            Spacer(Modifier.height(26.dp))

            // About this version; "Version" hides the easter egg.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(UpdateCard).padding(vertical = 6.dp)) {
                InfoRow("Version", "$name $current", Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < 1500) taps + 1 else 1
                    lastTap = now
                    if (taps >= 5) {
                        taps = 0
                        egg = true
                    }
                })
                if (release != null) InfoRow("Neue Version", "${release.version} · ${megabytes(release.size)}")
                InfoRow(
                    "Letzte Prüfung",
                    if (lastCheck > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastCheck)) else "Noch nie",
                )
                InfoRow("Quelle", "GitHub · Vinted7777/launcher")
            }

            if (release != null && release.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(UpdateCard).padding(20.dp)) {
                    Text("Was ist neu", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(150.dp))
        }

        // The one button at the bottom, like One UI's "Herunterladen und installieren".
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050506), Color(0xFF050506))))
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
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                    Box(
                        Modifier
                            .fillMaxWidth(s.progress.coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(UpdateAccent, Color(0xFF9C7BFF)))),
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
                        .background(if (strong) UpdateAccent else Color.White.copy(alpha = 0.14f))
                        .clickable(enabled = s !is UpdateState.Checking, onClick = action),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(egg, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            VersionEgg(current, name) { egg = false }
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
 * glowing planet with Clawd circling it under twinkling pixel stars. A tap makes him flip and
 * the planet bounce; holding sends a spiral of emojis flying out.
 */
@Composable
private fun VersionEgg(version: String, name: String, onClose: () -> Unit) {
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

        // The version as a glowing planet.
        Box(
            Modifier
                .size(230.dp)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFB494), UpdateAccent, Color(0xFF6A3BC8))))
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
            Text(version, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
        }

        // Clawd circling it.
        Clawd(
            Modifier
                .size(width = 70.dp, height = 60.dp)
                .graphicsLayer {
                    val r = 165.dp.toPx()
                    translationX = cos(orbit) * r
                    translationY = sin(orbit) * r * 0.85f
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
