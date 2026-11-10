package dev.hearth.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.hearth.launcher.data.ShizukuBridge
import dev.hearth.launcher.system.GlimmerDeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ZENITH 22.1, "Tiefer im System" in Glimmer's settings: what Glimmer can do with Shizuku (the rights of ADB, no root) – set itself up
 * in one tap, stay switched on, and take over from the system's own banners. See [GlimmerDeep].
 */
@Composable
internal fun GlimmerDeepSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ShizukuBridge.status(context)) }
    var canWrite by remember { mutableStateOf(GlimmerDeep.canWriteSecure(context)) }
    var keep by remember { mutableStateOf(GlimmerDeep.keepAlive(context)) }
    var quiet by remember { mutableStateOf(GlimmerDeep.bannersOff(context)) }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // (Shizuku is started and allowed outside this screen: looked at again every second while it is open.)
    LaunchedEffect(Unit) {
        while (true) {
            status = ShizukuBridge.status(context)
            canWrite = GlimmerDeep.canWriteSecure(context)
            quiet = GlimmerDeep.bannersOff(context)
            delay(1000)
        }
    }
    val ready = status == ShizukuBridge.Status.Ready

    Section("Tiefer im System (Shizuku)") {
        Note(
            "Mit Shizuku (den Rechten von ADB, ohne Root) richtet sich Glimmer selbst ein, bleibt eingeschaltet und übernimmt die Banner des Systems. " +
                "Shizuku brauchst du nur einmal zum Einrichten: Danach hat Glimmer das Recht selbst, auch nach einem Neustart.",
        )
        ActionRow(
            label = when (status) {
                ShizukuBridge.Status.NotInstalled -> "Shizuku installieren"
                ShizukuBridge.Status.NotRunning -> "Shizuku starten"
                ShizukuBridge.Status.NoPermission -> "Shizuku: Erlaubnis geben"
                ShizukuBridge.Status.Ready -> "Shizuku ist bereit"
            },
            description = when (status) {
                ShizukuBridge.Status.NotInstalled -> "Die App Shizuku fehlt – öffnet ihre Seite"
                ShizukuBridge.Status.NotRunning -> "Shizuku ist installiert, läuft aber nicht (nach jedem Neustart einmal über „Kabelloses Debugging“ starten)"
                ShizukuBridge.Status.NoPermission -> "Shizuku läuft; Glimmer darf es noch nicht benutzen"
                ShizukuBridge.Status.Ready -> if (canWrite) "Glimmer hat auch das Recht, sich selbst einzuschalten" else "Tippe unten auf „Alles einrichten“"
            },
        ) {
            when (status) {
                ShizukuBridge.Status.NoPermission -> ShizukuBridge.requestPermission()
                ShizukuBridge.Status.Ready -> Unit
                else -> ShizukuBridge.openApp(context)
            }
        }
        if (ready) {
            ActionRow(
                label = if (busy) "Richte ein …" else "Alles einrichten",
                description = report
                    ?: "Schaltet Glimmers Bedienungshilfe und den Zugriff auf Benachrichtigungen ein, nimmt Glimmer vom Akkusparen aus und gibt ihm seine Berechtigungen – ohne den Weg durch die Einstellungen",
            ) {
                if (!busy) {
                    busy = true
                    scope.launch {
                        val steps = GlimmerDeep.setUp(context)
                        val failed = steps.filterNot { it.ok }
                        report = if (failed.isEmpty()) {
                            "Fertig: alles ist eingeschaltet"
                        } else {
                            "Nicht gelungen: " + failed.joinToString(", ") { it.name } + ". Der Rest ist eingeschaltet"
                        }
                        canWrite = GlimmerDeep.canWriteSecure(context)
                        busy = false
                    }
                }
            }
        }
        if (canWrite) {
            SwitchRow(
                label = "Glimmer eingeschaltet halten",
                description = "Schaltet Android Glimmers Bedienungshilfe ab (nach einem Absturz, einem Update oder vom Akkusparen), schaltet Glimmer sie beim nächsten Start von selbst wieder ein. Aus: du schaltest sie wie bisher von Hand ein",
                checked = keep,
            ) { v ->
                GlimmerDeep.setKeepAlive(context, v)
                keep = v
            }
        }
        if (canWrite || ready) {
            SwitchRow(
                label = "Banner des Systems aus",
                description = "Neue Nachrichten schieben sich nicht mehr von oben ins Bild – nur die Insel meldet sie, wie beim iPhone. Gilt für das ganze Handy; ausgeschaltet ist alles wie vorher. Nicht jedes Handy hält sich daran",
                checked = quiet,
            ) { v ->
                scope.launch {
                    if (GlimmerDeep.setBannersOff(context, v)) quiet = GlimmerDeep.bannersOff(context)
                }
            }
        }
    }
}
