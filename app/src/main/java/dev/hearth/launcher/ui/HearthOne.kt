package dev.hearth.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppUpdater
import dev.hearth.launcher.data.ClaudeOs
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.HearthOneUpgrade
import dev.hearth.launcher.system.GlimmerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where moving to Hearth One stands. */
private sealed interface OneStep {
    data object Ready : OneStep
    data object TakingOver : OneStep
    class OldTooOld(val apps: List<AppUpdater.Kind>) : OneStep
    class Updating(val name: String, val progress: Float) : OneStep
    class Downloading(val progress: Float) : OneStep
    data object Installing : OneStep
    class Failed(val reason: String) : OneStep
}

/**
 * In Hearth's software update: the way to Hearth One, Hearth with Glimmer and Clawd built in.
 * One tap takes Glimmer's and Clawd's data over (bringing them up to date first if they're too
 * old to hand it over), then installs Hearth One over Hearth – keeping everything.
 */
@Composable
fun HearthOneCard(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<OneStep>(OneStep.Ready) }

    fun upgrade() {
        scope.launch {
            step = OneStep.TakingOver
            val tooOld = HearthOneUpgrade.takeOver(context)
            if (tooOld.isNotEmpty()) {
                step = OneStep.OldTooOld(tooOld)
                return@launch
            }
            when (val found = AppUpdater.check(context, AppUpdater.HearthOne, than = "0")) {
                is AppUpdater.Check.Newer -> {
                    step = OneStep.Downloading(0f)
                    val apk = AppUpdater.download(context, found.release) { p ->
                        scope.launch { if (step is OneStep.Downloading) step = OneStep.Downloading(p) }
                    }
                    if (apk == null) {
                        step = OneStep.Failed("Download fehlgeschlagen – bist du online?")
                        return@launch
                    }
                    if (!AppUpdater.canInstall(context)) {
                        AppUpdater.askInstallPermission(context)
                        step = OneStep.Failed("Erlaube Hearth einmal „Unbekannte Apps installieren“ und tippe dann nochmal auf „Upgraden“.")
                        return@launch
                    }
                    step = OneStep.Installing
                    if (!AppUpdater.install(context, apk)) step = OneStep.Failed("Die Installation ließ sich nicht starten")
                }
                is AppUpdater.Check.Failed -> step = OneStep.Failed(found.reason)
                AppUpdater.Check.UpToDate -> step = OneStep.Failed("Hearth One ist noch nicht veröffentlicht")
            }
        }
    }

    /** An old Glimmer or Clawd can't hand its data over yet: it's brought up to date first. */
    fun updateOld(kind: AppUpdater.Kind) {
        scope.launch {
            val installed = AppUpdater.installedVersion(context, kind.packageName) ?: "0"
            val found = AppUpdater.check(context, kind, than = installed)
            if (found !is AppUpdater.Check.Newer) {
                step = OneStep.Failed("Keine neuere ${kind.name}-Version gefunden")
                return@launch
            }
            step = OneStep.Updating(kind.name, 0f)
            val apk = AppUpdater.download(context, found.release) { p ->
                scope.launch { if (step is OneStep.Updating) step = OneStep.Updating(kind.name, p) }
            }
            if (apk == null || !AppUpdater.canInstall(context)) {
                if (!AppUpdater.canInstall(context)) AppUpdater.askInstallPermission(context)
                step = OneStep.Failed("${kind.name} ließ sich nicht aktualisieren – versuch es nochmal")
                return@launch
            }
            AppUpdater.install(context, apk, kind.packageName)
            // Once it's updated, the next tap takes its data over.
            step = OneStep.Ready
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1A3A), Color(0xFF1A1420))))
            .glassSheen(26.dp)
            .aiFluidEdge(26.dp, strength = 0.9f, width = 2.dp)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Clawd(Modifier.size(width = 52.dp, height = 44.dp), mood = ClawdMood.Jump)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Hearth One",
                    style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                Text("Alles in einer App · ${ClaudeOs.full}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Hearth mit Glimmer und Clawd eingebaut: eine App statt drei. Deine Einstellungen, Clawds Tamagotchi, " +
                "Rekorde, Abzeichen und Easter Eggs kommen mit. Danach kannst du die einzelnen Apps Glimmer und Clawd entfernen.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        val s = step
        Text(
            when (s) {
                OneStep.Ready -> "Großes Update · jederzeit möglich"
                OneStep.TakingOver -> "Übernehme Daten von Glimmer und Clawd …"
                is OneStep.OldTooOld -> "${s.apps.joinToString(" und ") { it.name }} ist zu alt, um die Daten zu übergeben – erst aktualisieren, dann nochmal „Upgraden“."
                is OneStep.Updating -> "Aktualisiere ${s.name} … ${(s.progress * 100).toInt()} %"
                is OneStep.Downloading -> "Lade Hearth One … ${(s.progress * 100).toInt()} %"
                OneStep.Installing -> "Android fragt gleich, ob du Hearth aktualisieren willst."
                is OneStep.Failed -> s.reason
            },
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        val busy = s is OneStep.TakingOver || s is OneStep.Downloading || s is OneStep.Updating
        val (label, action) = when (s) {
            is OneStep.OldTooOld -> "${s.apps.first().name} aktualisieren" to { updateOld(s.apps.first()) }
            else -> "Auf Hearth One upgraden" to { upgrade() }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFD97757), Color(0xFF8E6BFF))))
                .fluidTouch()
                .clickable(enabled = !busy, onClick = action),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (busy) "Einen Moment …" else label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The first start of Hearth One: what came over, the old Glimmer and Clawd apps to remove
 * (Android asks for each), and Glimmer's island to switch on again (now as part of Hearth).
 */
@Composable
fun HearthOneWelcome(onDone: () -> Unit) {
    val context = LocalContext.current
    var oldApps by remember { mutableStateOf(HearthOneUpgrade.oldAppsInstalled(context)) }
    var glimmerOn by remember { mutableStateOf(GlimmerService.isEnabled) }
    var taken by remember { mutableStateOf(HearthOneUpgrade.takenOver(context)) }
    // Takes over again (whatever changed since the upgrade), then keeps an eye on the steps.
    LaunchedEffect(Unit) {
        if (HearthOneUpgrade.takeOver(context).isEmpty()) taken = true
        while (true) {
            delay(1200)
            oldApps = HearthOneUpgrade.oldAppsInstalled(context)
            glimmerOn = GlimmerService.isEnabled
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14101C), Color(0xFF050506))))) {
        FluidBackdrop(AiFluidColors.take(3), strength = 0.8f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Clawd(Modifier.size(width = 130.dp, height = 112.dp), mood = ClawdMood.Dance)
            Text(
                "Willkommen bei Hearth One",
                style = TextStyle(brush = Brush.linearGradient(AiFluidColors), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                "Hearth, Glimmer und Clawd sind jetzt eine App · ${ClaudeOs.full}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            WelcomeStep(
                number = 1,
                title = "Fortschritt übernommen",
                text = if (taken) {
                    "Einstellungen, Tamagotchi, Rekorde, Abzeichen und Easter Eggs sind in Hearth."
                } else {
                    "Ein Teil konnte nicht übernommen werden (zu alte Glimmer- oder Clawd-App). Hearth hat, was es hatte."
                },
                done = taken,
            )
            WelcomeStep(
                number = 2,
                title = "Alte Apps entfernen",
                text = if (oldApps.isEmpty()) {
                    "Erledigt – Glimmer und Clawd gibt es nur noch in Hearth."
                } else {
                    "Glimmer und Clawd stecken jetzt in Hearth. Die einzelnen Apps brauchst du nicht mehr."
                },
                done = oldApps.isEmpty(),
            ) {
                oldApps.forEach { pkg ->
                    WelcomeButton("${HearthOneUpgrade.label(pkg)} entfernen") { HearthOneUpgrade.uninstall(context, pkg) }
                }
            }
            WelcomeStep(
                number = 3,
                title = "Glimmer einschalten",
                text = if (glimmerOn) {
                    "Die Insel läuft – jetzt als Teil von Hearth."
                } else {
                    "Android verlangt das einmal neu: unter Bedienungshilfen bei „Hearth“ den Dienst „Glimmer“ einschalten."
                },
                done = glimmerOn,
            ) {
                if (!glimmerOn) WelcomeButton("Bedienungshilfen öffnen") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFD97757), Color(0xFF8E6BFF))))
                    .fluidTouch()
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (oldApps.isEmpty() && glimmerOn) "Los geht's" else "Später", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WelcomeStep(number: Int, title: String, text: String, done: Boolean, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .glassSheen(24.dp)
            .aiFluidEdge(24.dp, strength = if (done) 0.3f else 0.8f, width = 1.dp)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (done) Color(0xFF34C759) else Color(0xFFD97757)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else "$number", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 19.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { extra() }
    }
}

@Composable
private fun WelcomeButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .fluidTouch()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
