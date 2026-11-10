package dev.hearth.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.hearth.launcher.data.Brand
import dev.hearth.launcher.data.ShizukuBridge
import dev.hearth.launcher.system.ZenithDeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * ZENITH 22.1, Shizuku deeper in the normal system (Einstellungen → System, once Shizuku is ready): all the launcher's permissions in
 * one tap, ZENITH as the phone's home screen without the trip through Android's settings, Shizuku starting itself after a restart,
 * and the size of everything on the screen. See [ZenithDeep] and [ShizukuBridge].
 */

/** "Rechte & Einrichten": what the launcher may do by itself, and the one tap that gives it all. */
@Composable
internal fun ShizukuRightsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rights by remember { mutableStateOf(ZenithDeep.rights(context)) }
    var home by remember { mutableStateOf(ZenithDeep.isHome(context)) }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var startReport by remember { mutableStateOf<String?>(null) }
    // (Rights are given on Android's own pages too, so the list is looked at again every second while this page is open.)
    LaunchedEffect(Unit) {
        while (true) {
            rights = ZenithDeep.rights(context)
            home = ZenithDeep.isHome(context)
            delay(1000)
        }
    }
    val open = rights.filterNot { it.granted }

    Section("Shizuku · Rechte & Einrichten") {
        Note(
            "${Brand.name} braucht ein paar Rechte, die Android sonst nur einzeln auf eigenen Seiten vergibt. " +
                "Mit Shizuku gibst du sie auf einen Tipp – danach laufen auch die Schalter in diesem Bereich ohne Shizuku weiter.",
        )
        ActionRow(
            label = when {
                busy -> "Gebe die Rechte …"
                open.isEmpty() -> "Alle Rechte sind erlaubt"
                else -> "Alle Rechte erlauben"
            },
            description = report ?: if (open.isEmpty()) {
                "${rights.size} von ${rights.size} erlaubt – tippe, um alles noch einmal zu prüfen"
            } else {
                "${rights.size - open.size} von ${rights.size} erlaubt – offen: " + open.joinToString(", ") { it.label }
            },
        ) {
            if (!busy) {
                busy = true
                scope.launch {
                    val missing = ZenithDeep.grantAll(context)
                    rights = ZenithDeep.rights(context)
                    report = if (missing.isEmpty()) {
                        "Fertig: alle Rechte sind erlaubt"
                    } else {
                        "Android hält noch zurück: " + missing.joinToString(", ") + ". Der Rest ist erlaubt"
                    }
                    busy = false
                }
            }
        }
        if (!home) {
            ActionRow(
                label = "${Brand.name} zum Startbildschirm machen",
                description = "Setzt den Launcher als Standard, ohne den Weg über Einstellungen → Apps → Standard-Apps",
            ) {
                scope.launch {
                    ShizukuBridge.makeHome(context.packageName)
                    delay(500)
                    home = ZenithDeep.isHome(context)
                    if (!home) report = "Das Setzen als Startbildschirm hat Android nicht angenommen – wähle ihn in den Standard-Apps"
                }
            }
        }
        ActionRow(
            label = "Shizuku nach dem Neustart selbst starten lassen",
            description = startReport
                ?: "Gibt Shizuku das Recht, sich nach einem Neustart über das kabellose Debugging wieder zu starten (nötig: neuere Shizuku-Version und WLAN)",
        ) {
            scope.launch {
                startReport = if (ShizukuBridge.letShizukuStartItself()) {
                    "Shizuku darf sich jetzt selbst starten – in Shizuku dazu „Beim Start von Android starten“ einschalten"
                } else {
                    "Das hat nicht geklappt – Shizuku läuft zu alt oder verweigert es"
                }
            }
        }
    }
}

/** How big everything on the screen is (dpi), in steps of the display's own. */
private val DensitySteps = listOf(0.85f, 0.92f, 1f, 1.1f, 1.2f)

private fun densityLabel(factor: Float): String = when (factor) {
    1f -> "Normal"
    else -> (if (factor < 1f) "Kleiner" else "Größer") + " (${(factor * 100).roundToInt()} %)"
}

/** "Anzeige": the size of everything on the screen, which Android keeps in its developer tools. */
@Composable
internal fun ShizukuDisplaySection() {
    val scope = rememberCoroutineScope()
    // (display's own dpi, the one in use now); null until it has been read, or if it can't be.
    var density by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { density = ShizukuBridge.density() }
    val shown = density ?: return

    Section("Shizuku · Anzeige") {
        val (own, now) = shown
        val ratio = now.toFloat() / own
        ChoiceRow(
            label = "Größe von allem auf dem Bildschirm",
            options = DensitySteps,
            selected = DensitySteps.minByOrNull { abs(it - ratio) },
            optionLabel = ::densityLabel,
        ) { factor ->
            scope.launch {
                val dpi = if (factor == 1f) null else (own * factor).roundToInt()
                failed = !ShizukuBridge.setDensity(dpi)
                delay(400)
                density = ShizukuBridge.density() ?: shown
            }
        }
        Note(
            if (failed) {
                "Das hat nicht geklappt."
            } else {
                "Wie „Anzeigegröße“ in den Entwickleroptionen, nur genauer: Alles wird kleiner (mehr passt auf den Bildschirm) oder größer. " +
                    "Der Bildschirm baut sich dabei kurz neu auf. Zurück geht es mit „Normal“."
            },
        )
    }
}
