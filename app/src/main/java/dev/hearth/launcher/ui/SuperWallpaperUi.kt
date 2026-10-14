package dev.hearth.launcher.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import dev.hearth.launcher.wallpaper.SuperMotion
import dev.hearth.launcher.wallpaper.SuperParams
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

/** The scene as it runs, in a box: with a slow sway as if the phone were tilted. */
@Composable
internal fun SuperScenePreview(params: SuperParams, modifier: Modifier = Modifier) {
    val renderer = remember(params.scene) { SuperRenderer(params.scene) }
    LaunchedEffect(params.scene) { withContext(Dispatchers.Default) { renderer.prepare() } }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (t - last >= 33L) {
                    last = t
                    now = t
                }
            }
        }
    }
    Canvas(modifier) {
        val t = now
        val motion = SuperMotion(panX = 0f, tiltX = sin(t / 2600f) * 0.4f, tiltY = 0f, zoom = 1f)
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, size.width.roundToInt(), size.height.roundToInt(), t, params, motion) }
    }
}

/** The settings page "Super-Wallpaper". */
@Composable
internal fun SuperWallpaperSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(SuperWallpaperService.isActiveNow(context)) }
    // Back from Android's preview: is it the wallpaper now?
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { active = SuperWallpaperService.isActiveNow(context) }
    val params = remember(s.superScene, s.superTilt, s.superZoom, s.superSpeed, s.superStars) {
        SuperParams(s.superScene, s.superTilt, s.superZoom, s.superSpeed, s.superStars)
    }
    Section("Super-Wallpaper") {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            SuperScenePreview(params, Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(24.dp)))
        }
        ChoiceRow(
            label = "Szene",
            options = SuperScene.entries,
            selected = s.superScene,
            optionLabel = { it.label },
            onSelect = { scene -> update { it.copy(superScene = scene) } },
        )
        Note(s.superScene.note)
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
        ) { v -> update { it.copy(superTilt = v) } }
        SwitchRow(
            label = "Zoom beim Aufwachen und Seitenwechsel",
            description = "Beim Einschalten kommt die Szene von nahem heran; mit den Seiten des Startbildschirms rückt sie zur Seite",
            checked = s.superZoom,
        ) { v -> update { it.copy(superZoom = v) } }
        SwitchRow(
            label = "Sterne",
            description = "Der Sternenhimmel hinter der Szene",
            checked = s.superStars,
        ) { v -> update { it.copy(superStars = v) } }
        SliderRow(
            label = "Geschwindigkeit",
            value = s.superSpeed,
            range = 0.2f..3f,
            valueText = "${"%.1f".format(s.superSpeed)}×",
        ) { v -> update { it.copy(superSpeed = v) } }
        Note("Alles ist in ZENITH selbst gezeichnet – Saturn, Erde, Mars und die anderen entstehen beim ersten Start aus Rauschen und Farbverläufen, es sind keine Bilddateien und nicht die Super-Wallpaper eines Telefonherstellers. Das Glas in ZENITH bricht ein Standbild der Szene; es bewegt sich nur der Hintergrund selbst.")
    }
}

/** Android's preview of the wallpaper (it asks "Hintergrund festlegen" there); the chooser if the phone has no direct way. */
private fun openSuperWallpaper(context: Context) {
    val component = ComponentName(context, SuperWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
