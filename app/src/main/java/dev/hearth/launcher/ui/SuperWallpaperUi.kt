package dev.hearth.launcher.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.wallpaper.SuperDirector
import dev.hearth.launcher.wallpaper.SuperMotion
import dev.hearth.launcher.wallpaper.SuperParams
import dev.hearth.launcher.wallpaper.SuperPhoto
import dev.hearth.launcher.wallpaper.SuperRenderer
import dev.hearth.launcher.wallpaper.SuperScene
import dev.hearth.launcher.wallpaper.SuperWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * ZENITH 21.5, the Super-Wallpaper's page in the settings: the scene shown live (the very renderer the wallpaper
 * runs), the scenes to choose from, how it moves, and the button that makes it the phone's wallpaper.
 */

/** The scene as it runs, in a box: with a slow sway as if the phone were tilted, and the flight from the lock screen to the home screen and back, round and round. */
@Composable
internal fun SuperScenePreview(params: SuperParams, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(params.scene, params.photo) { SuperRenderer(params.scene, SuperPhoto.file(context), context.assets) }
    LaunchedEffect(params.scene, params.photo) { withContext(Dispatchers.Default) { renderer.prepare() } }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (t - last >= 16L) {
                    last = t
                    now = t
                }
            }
        }
    }
    Canvas(modifier) {
        val t = now
        val depth = if (params.zoomOnWake) SuperDirector.demoDepth(t) else 1f
        val motion = SuperMotion(panX = 0f, tiltX = sin(t / 2600f) * 0.4f, tiltY = 0f, zoom = 1f, depth = depth)
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, size.width.roundToInt(), size.height.roundToInt(), t, params, motion) }
    }
}

/** The settings page "Super-Wallpaper". */
@Composable
internal fun SuperWallpaperSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onGallery: () -> Unit = {}) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(SuperWallpaperService.isActiveNow(context)) }
    // Back from Android's preview: is it the wallpaper now?
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { active = SuperWallpaperService.isActiveNow(context) }
    val params = s.superParams
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
        SliderRow(
            label = "Geschwindigkeit",
            value = s.superSpeed,
            range = 0.2f..3f,
            valueText = "${"%.1f".format(s.superSpeed)}×",
        ) { v -> update { it.copy(superParams = it.superParams.copy(speed = v)) } }
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
        Note("Mond und Erde sind Aufnahmen der NASA, Mars, Venus, Jupiter, Saturn, Neptun, Pluto und die Monde drehen sich auf echten Karten der NASA (gemeinfrei, im App-Paket enthalten, keine Verbindung nötig). Das Glas in ZENITH bricht ein Standbild der Szene; es bewegt sich nur der Hintergrund selbst. „Eigenes Foto“ zeigt ein Foto aus der Wallpaper-Galerie, „Geometrie“ ist in ZENITH selbst gezeichnet.")
    }
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
