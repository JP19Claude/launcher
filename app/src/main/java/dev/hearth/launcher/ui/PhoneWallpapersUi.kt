package dev.hearth.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.wallpaper.PhoneWallpaper
import dev.hearth.launcher.wallpaper.PhoneWallpapers
import dev.hearth.launcher.wallpaper.SuperScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * ZENITH 21.5 (215): a compartment of its own for the live wallpapers of the phone – OnePlus' own first, then everybody else's. They are the
 * ones that are installed on this phone: ZENITH shows them with the picture they have in Android's picker and hands the one you tap to
 * Android's preview, which sets it. Nothing of them is copied into ZENITH or passed on.
 */

/** The settings page "OnePlus Live-Wallpaper". */
@Composable
internal fun PhoneWallpapersSection(update: ((LauncherSettings) -> LauncherSettings) -> Unit, onSuper: () -> Unit) {
    val context = LocalContext.current
    // Back from Android's preview (or from installing a wallpaper app): look again.
    var round by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { round++ }
    val loaded by produceState<List<PhoneWallpaper>?>(null, round) {
        value = withContext(Dispatchers.IO) { PhoneWallpapers.load(context) }
    }
    val all = loaded
    val onePlus = all?.filter { it.onePlus }.orEmpty()
    val others = all?.filter { !it.onePlus }.orEmpty()
    Section("OnePlus Live-Wallpaper") {
        when {
            all == null -> Note("Ich suche die Live-Wallpaper auf diesem Telefon …")
            onePlus.isEmpty() -> Note(
                "Auf diesem Telefon sind keine Live-Wallpaper von OnePlus installiert. Sie gehören OnePlus – ZENITH kann sie nicht einbauen oder weitergeben –, " +
                    "erscheinen aber hier, sobald sie auf dem Telefon sind (auf einem OnePlus-Telefon bringt sie das System selbst mit).",
            )
            else -> onePlus.forEachIndexed { index, wallpaper ->
                if (index > 0) RowDivider()
                PhoneWallpaperRow(wallpaper) { PhoneWallpapers.openPreview(context, wallpaper) }
            }
        }
    }
    if (others.isNotEmpty()) {
        Section("Weitere Live-Wallpaper auf diesem Telefon") {
            others.forEachIndexed { index, wallpaper ->
                if (index > 0) RowDivider()
                PhoneWallpaperRow(wallpaper) { PhoneWallpapers.openPreview(context, wallpaper) }
            }
        }
    }
    // What cannot be shipped (the wallpapers are OnePlus') ZENITH draws itself, in the same spirit: the mountains and the flowing silk.
    Section("Im OnePlus-Stil – von ZENITH selbst gemacht") {
        SuperScene.entries.filter { it.isScape }.forEachIndexed { index, scene ->
            if (index > 0) RowDivider()
            ActionRow(
                label = scene.label,
                description = scene.note,
                onClick = {
                    update { it.copy(superParams = it.superParams.copy(scene = scene)) }
                    onSuper()
                },
            )
        }
        Note("Das sind eigene Szenen von ZENITH, nicht die von OnePlus – gezeichnet von der Grafik des Telefons, ohne ein einziges Bild von OnePlus. Tippen wählt die Szene und öffnet das Super-Wallpaper, dort „Als Live-Wallpaper festlegen“.")
    }
    Section("Auswahl des Telefons") {
        ActionRow(
            label = "Alle Live-Wallpaper in Androids Auswahl",
            description = "Die Liste des Telefons selbst – dort auch die, die sich hier nicht zeigen",
            onClick = { PhoneWallpapers.openPicker(context) },
        )
        Note("Tippen auf ein Live-Wallpaper öffnet Androids Vorschau; dort „Hintergrund festlegen“ wählen – für den Startbildschirm und, wo Android es zulässt, den Sperrbildschirm. Das Super-Wallpaper von ZENITH bleibt unter „Super-Wallpaper“.")
    }
}

/** One live wallpaper: its picture, its name and who made it. */
@Composable
private fun PhoneWallpaperRow(wallpaper: PhoneWallpaper, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .fluidTouch(LocalSettings.current.accent.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            val picture = wallpaper.thumbnail
            if (picture != null) {
                Image(picture.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(wallpaper.label, color = Color.White, fontSize = 15.sp)
            val detail = listOf(wallpaper.author, wallpaper.summary).filter { it.isNotBlank() }.joinToString(" – ")
            if (detail.isNotBlank()) {
                Text(detail, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 2)
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
        )
    }
}
