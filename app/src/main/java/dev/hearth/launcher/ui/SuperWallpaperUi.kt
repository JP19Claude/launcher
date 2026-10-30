package dev.hearth.launcher.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.system.GlimmerService
import dev.hearth.launcher.wallpaper.SuperLiveView
import dev.hearth.launcher.wallpaper.SuperMotion
import dev.hearth.launcher.wallpaper.SuperParams
import dev.hearth.launcher.wallpaper.SuperPhoto
import dev.hearth.launcher.wallpaper.SuperPlaces
import dev.hearth.launcher.wallpaper.SuperRenderer
import dev.hearth.launcher.wallpaper.SuperScene
import dev.hearth.launcher.wallpaper.SuperWallpaperService
import dev.hearth.launcher.wallpaper.WallpaperUpdater
import dev.hearth.launcher.wallpaper.ZenithAodActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * ZENITH 21.5, the Super-Wallpaper's page in the settings: the scene shown live (the very renderer the wallpaper
 * runs), the scenes to choose from, how it moves, and the button that makes it the phone's wallpaper.
 */

/** The scene as it runs, in a box: the very thing the live wallpaper runs, with the flight from the lock screen to the home screen playing in a loop. */
@Composable
internal fun SuperScenePreview(params: SuperParams, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c -> SuperLiveView(c, SuperPhoto.file(c)).also { it.setParams(params) } },
        modifier = modifier,
        update = { it.setParams(params) },
    )
}

/** The settings page "Super-Wallpaper". */
@Composable
internal fun SuperWallpaperSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onGallery: () -> Unit = {}) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(SuperWallpaperService.isActiveNow(context)) }
    // Back from Android's preview: is it the wallpaper now?
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { active = SuperWallpaperService.isActiveNow(context) }
    val params = s.superParams
    // Glimmer (the accessibility service) draws the AOD: is it on?
    var glimmerOn by remember { mutableStateOf(GlimmerService.isEnabled) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { glimmerOn = GlimmerService.isEnabled }
    Section("Super-Wallpaper") {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            SuperScenePreview(params, Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(24.dp)))
        }
        ChoiceRow(
            label = "Szene",
            options = SuperScene.entries,
            selected = s.superScene,
            optionLabel = { it.label },
            onSelect = { scene -> update { it.copy(superParams = it.superParams.copy(scene = scene)) } },
        )
        Note(s.superScene.note)
        // Where the body lands when it has finished turning: the place in the middle of the zoom.
        val placeCount = SuperPlaces.count(s.superScene)
        if (placeCount > 0) {
            ChoiceRow(
                label = "Landeplatz",
                options = (0..placeCount).toList(),
                selected = params.place.coerceAtMost(placeCount),
                optionLabel = { SuperPlaces.label(s.superScene, it) },
                onSelect = { index -> update { it.copy(superParams = it.superParams.copy(place = index)) } },
            )
            Note("Beim Entsperren dreht sich ${if (s.superScene == SuperScene.Moon) "der Mond" else "der Körper"} und bleibt auf diesem Ort stehen – dort zoomt die Kamera heran. „Überraschung“ nimmt jedes Mal einen anderen.")
        }
        if (s.superScene == SuperScene.Photo) {
            ActionRow(
                label = if (SuperPhoto.file(context).exists()) "Anderes Foto wählen" else "Foto wählen",
                description = if (SuperPhoto.file(context).exists()) {
                    "In der Wallpaper-Galerie ein NASA-Foto öffnen und „Als Live-Wallpaper“ tippen."
                } else {
                    "Noch kein Foto: In der Wallpaper-Galerie ein NASA-Foto (Mond, Planeten, Erde …) öffnen und „Als Live-Wallpaper“ tippen."
                },
                onClick = onGallery,
            )
        }
        ActionRow(
            label = if (active) "Läuft als Hintergrund" else "Als Hintergrund setzen",
            description = if (active) {
                "Das Super-Wallpaper ist dein Hintergrund. Die Szene wechselt hier sofort; Tippen öffnet Androids Vorschau noch einmal."
            } else {
                "Öffnet Androids Vorschau: dort „Hintergrund festlegen“ wählen – für den Startbildschirm und, wo Android es zulässt, den Sperrbildschirm."
            },
            onClick = { openSuperWallpaper(context) },
        )
        if (s.zenithWallpaper) {
            ActionRow(
                label = "ZENITH-Hintergrund ausschalten",
                description = "Der gezeichnete Hintergrund von ZENITH liegt sonst über dem Super-Wallpaper.",
                onClick = { update { it.copy(zenithWallpaper = false) } },
            )
        }
        SwitchRow(
            label = "Neigung",
            description = "Die Szene wandert mit, wenn du das Telefon kippst",
            checked = s.superTilt,
        ) { v -> update { it.copy(superParams = it.superParams.copy(tilt = v)) } }
        SwitchRow(
            label = "Entsperr-Flug und Seitenwechsel",
            description = "Auf dem Sperrbildschirm hängt der Körper klein und ruhig; beim Entsperren fliegt die Kamera schnell heran, beim Sperren wieder zurück – wie beim Super-Wallpaper von Xiaomi HyperOS. Mit den Seiten des Startbildschirms dreht oder rückt er weiter",
            checked = s.superZoom,
        ) { v -> update { it.copy(superParams = it.superParams.copy(zoomOnWake = v)) } }
        SwitchRow(
            label = "Sterne",
            description = "Der Sternenhimmel hinter der Szene",
            checked = s.superStars,
        ) { v -> update { it.copy(superParams = it.superParams.copy(stars = v)) } }
        SwitchRow(
            label = "Planet auf dem AOD",
            description = "Der Körper gedimmt auf dem Always-on-Display des Telefons – als Standbild auf Schwarz, mit Uhr, Datum und Akku (Glimmers „Voller AOD“). Das geht nur über Glimmer: Die Bedienungshilfe „Glimmer“ muss an sein, sonst bleibt der AOD, wie ihn dein Telefon zeigt",
            checked = params.aod,
        ) { v -> update { it.copy(superParams = it.superParams.copy(aod = v)) } }
        if (params.aod) {
            if (!glimmerOn) {
                ActionRow(
                    label = "Glimmer einschalten",
                    description = "Die Bedienungshilfe „Glimmer“ ist aus – ohne sie bleibt der AOD ohne Planet. Öffnet Androids Bedienungshilfen: dort „Glimmer“ wählen und einschalten",
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
            SliderRow(
                label = "AOD-Helligkeit",
                value = params.aodDim,
                range = 0.1f..0.8f,
                valueText = "${(params.aodDim * 100f).roundToInt()} %",
            ) { v -> update { it.copy(superParams = it.superParams.copy(aodDim = v)) } }
            ActionRow(
                label = "AOD-Bildschirm jetzt starten",
                description = "Zusätzlich ein Dauerbildschirm: das Display bleibt an, sehr dunkel, mit dem sich drehenden Körper und der Uhrzeit – Tippen beendet ihn, nach sechs Stunden endet er von selbst",
                onClick = { openAodScreen(context) },
            )
            ActionRow(
                label = "Als Bildschirmschoner einrichten",
                description = "Öffnet Androids Einstellung: dort „ZENITH Planet“ wählen, Start „Beim Laden“ – dann geht der Dauerbildschirm am Ladegerät von selbst an",
                onClick = { openDreamSettings(context) },
            )
            SwitchRow(
                label = "Uhrzeit im Dauerbildschirm",
                description = "Die Uhrzeit unter dem Körper im Dauerbildschirm und im Bildschirmschoner, dünn und gedimmt",
                checked = params.aodClock,
            ) { v -> update { it.copy(superParams = it.superParams.copy(aodClock = v)) } }
        }
        SliderRow(
            label = "Geschwindigkeit",
            value = s.superSpeed,
            range = 0.2f..3f,
            valueText = "${"%.1f".format(s.superSpeed)}×",
        ) { v -> update { it.copy(superParams = it.superParams.copy(speed = v)) } }
        // The photos (Iwo Jima, your own) and the landscapes have no body to resize.
        if (s.superScene != SuperScene.Photo && s.superScene != SuperScene.Flag && !s.superScene.isScape) {
            SliderRow(
                label = "Größe",
                value = params.size,
                range = 0.5f..1.6f,
                valueText = "${(params.size * 100f).roundToInt()} %",
            ) { v -> update { it.copy(superParams = it.superParams.copy(size = v)) } }
        }
        ActionRow(
            label = "Als einzelne App herunterladen",
            description = "Jede Szene gibt es auch als eigene kleine App (ein Live-Wallpaper) – einzeln zu installieren und weiterzugeben. Öffnet die Download-Seite.",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JP19Claude/launcher/releases/tag/hintergruende")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
        WallpaperAppsUpdate()
        Note("Mond und Erde sind Aufnahmen der NASA, Mars, Venus, Jupiter, Saturn, Neptun, Pluto und die Monde drehen sich auf echten Karten der NASA (gemeinfrei, im App-Paket enthalten, keine Verbindung nötig). Das Glas in ZENITH bricht ein Standbild der Szene; es bewegt sich nur der Hintergrund selbst. „Iwo Jima“ ist ein Foto im Paket (aus der NASA-Bildbibliothek), „Eigenes Foto“ zeigt ein Foto aus der Wallpaper-Galerie, „Geometrie“ ist in ZENITH selbst gezeichnet.")
    }
}

/**
 * The wallpaper apps (ZENITH Hintergründe) that are installed next to ZENITH, and which of them has a newer build: ZENITH
 * asks GitHub once (a plain file), and a tap downloads that app's file and hands it to Android's installer, which asks again.
 * Nothing shows if no wallpaper app is installed.
 */
@Composable
private fun WallpaperAppsUpdate() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf<String?>(null) }
    // Back from Android's installer (or its permission page): look at what is installed now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        round++
        working = null
    }
    val apps = remember(round) {
        WallpaperUpdater.NAMES.mapNotNull { (id, name) ->
            WallpaperUpdater.installed(context, WallpaperUpdater.packageOf(id))?.let { Triple(id, name, it) }
        }
    }
    if (apps.isEmpty()) return
    var latest by remember { mutableStateOf<WallpaperUpdater.Latest?>(null) }
    var looked by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        latest = withContext(Dispatchers.IO) { WallpaperUpdater.latest() }
        looked = true
    }
    val newest = latest
    val outdated = if (newest == null) emptyList() else apps.filter { (_, _, app) -> newest.build > app.build }
    outdated.forEach { (id, name, app) ->
        ActionRow(
            label = "Hintergrund-App „$name“ aktualisieren",
            description = "Version ${app.version} → ${newest?.version.orEmpty()}" + (if (working == id) " – $progress" else ""),
            onClick = {
                val update = newest?.updateFor(id)
                if (working != null || update == null) return@ActionRow
                if (!WallpaperUpdater.canInstall(context)) {
                    WallpaperUpdater.askInstallPermission(context)
                    return@ActionRow
                }
                working = id
                progress = "Lade … 0 %"
                scope.launch {
                    val pkg = WallpaperUpdater.packageOf(id)
                    val file = withContext(Dispatchers.IO) {
                        WallpaperUpdater.download(context, pkg, update) { p -> progress = "Lade … ${(p * 100).roundToInt()} %" }
                    }
                    progress = when {
                        file == null -> "Der Download hat nicht geklappt"
                        WallpaperUpdater.install(context, file) -> "Android fragt gleich nach"
                        else -> "Die Installation ließ sich nicht starten"
                    }
                    if (file == null) working = null
                }
            },
        )
    }
    Note(
        when {
            !looked -> "Hintergrund-Apps: ${apps.size} installiert – ich suche nach Updates …"
            newest == null -> "Hintergrund-Apps: ${apps.size} installiert. Ob es Updates gibt, ließ sich nicht prüfen (keine Verbindung?)."
            outdated.isEmpty() -> "Alle ${apps.size} installierten Hintergrund-Apps sind auf dem neuesten Stand (Version ${newest.version})."
            else -> "${outdated.size} von ${apps.size} installierten Hintergrund-Apps haben ein Update (Version ${newest.version}). Jede fragt Android noch einmal, bevor sie sich ändert."
        },
    )
}

/** The always-on screen (the body dimmed on black with the time) – a tap leaves it. */
internal fun openAodScreen(context: Context) {
    runCatching { context.startActivity(Intent(context, ZenithAodActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Android's screen-saver settings (where "ZENITH Planet" is chosen); the display settings if the phone has none. */
internal fun openDreamSettings(context: Context) {
    val opened = listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS).any { action ->
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    if (!opened) Toast.makeText(context, "Dieses Telefon bietet keine Bildschirmschoner-Einstellung.", Toast.LENGTH_LONG).show()
}

/** Android's preview of the wallpaper (it asks "Hintergrund festlegen" there); the chooser if the phone has no direct way. */
internal fun openSuperWallpaper(context: Context) {
    val component = ComponentName(context, SuperWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
