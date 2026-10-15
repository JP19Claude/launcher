package dev.hearth.launcher.ui

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.hearth.launcher.wallpaper.ArtCatalog
import dev.hearth.launcher.wallpaper.ArtExport
import dev.hearth.launcher.wallpaper.ArtGroup
import dev.hearth.launcher.wallpaper.ArtPiece
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/*
 * ZENITH 21.5, the wallpaper gallery's page in the settings: many pictures to choose from, drawn by ZENITH itself and
 * made in the size of this phone's screen – and very many of the Moon. Tapping one shows it large; from there it
 * becomes the wallpaper (home screen, lock screen or both), is saved in Pictures/ZENITH (the download) or is shared.
 */

/** The small pictures of the gallery, drawn once and kept (a few at a time, so the page doesn't stall). */
private object ArtThumbs {
    private val cache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(2)

    fun peek(id: String, w: Int): Bitmap? = cache.get("$id@$w")

    suspend fun load(piece: ArtPiece, w: Int, h: Int): Bitmap? {
        peek(piece.id, w)?.let { return it }
        return withContext(Dispatchers.Default) {
            gate.withPermit {
                val bitmap = runCatching { ArtCatalog.render(piece, w, h) }.getOrNull()
                if (bitmap != null) cache.put("${piece.id}@$w", bitmap)
                bitmap
            }
        }
    }
}

/** The settings page "Wallpaper-Galerie". */
@Composable
internal fun WallpaperGallerySection() {
    val context = LocalContext.current
    var groupName by rememberSaveable { mutableStateOf(ArtGroup.Moon.name) }
    var opened by remember { mutableStateOf<ArtPiece?>(null) }
    val group = ArtGroup.entries.firstOrNull { it.name == groupName } ?: ArtGroup.Moon
    val (screenW, screenH) = remember { ArtExport.screenSize(context) }
    val thumbW = 180
    val thumbH = (thumbW * screenH.toFloat() / screenW).toInt()

    Section("Wallpaper-Galerie") {
        Note("${ArtCatalog.all.size} Hintergründe, alle von ZENITH selbst gezeichnet – in der Größe deines Bildschirms, keine Bilddateien von irgendwoher. Tippen zeigt das Bild groß; von dort wird es dein Hintergrund, landet als Bild in „Bilder/ZENITH“ (zum Herunterladen) oder wird geteilt.")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (g in ArtGroup.entries) {
                val selected = g == group
                Text(
                    text = "${g.label} · ${ArtCatalog.of(g).size}",
                    color = if (selected) Color(0xFF0B1020) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f))
                        .clickable { groupName = g.name }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in ArtCatalog.of(group).chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (piece in row) {
                        ArtTile(piece, thumbW, thumbH, Modifier.weight(1f)) { opened = piece }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    opened?.let { piece -> ArtPreview(piece, screenW, screenH) { opened = null } }
}

@Composable
private fun ArtTile(piece: ArtPiece, thumbW: Int, thumbH: Int, modifier: Modifier, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(initialValue = ArtThumbs.peek(piece.id, thumbW)?.asImageBitmap(), piece.id, thumbW) {
        if (value == null) value = ArtThumbs.load(piece, thumbW, thumbH)?.asImageBitmap()
    }
    Box(
        modifier
            .aspectRatio(thumbW.toFloat() / thumbH)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1220))
            .clickable(onClick = onClick),
    ) {
        image?.let {
            Image(bitmap = it, contentDescription = piece.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(
                piece.title,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One wallpaper, large, with what can be done with it. */
@Composable
private fun ArtPreview(piece: ArtPiece, screenW: Int, screenH: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewW = (screenW / 2).coerceAtLeast(360)
    val previewH = (previewW * screenH.toFloat() / screenW).toInt()
    val preview by produceState<ImageBitmap?>(initialValue = null, piece.id) {
        value = withContext(Dispatchers.Default) {
            runCatching { ArtCatalog.render(piece, previewW, previewH).asImageBitmap() }.getOrNull()
        }
    }
    var busy by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    fun say(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Draws the piece in the screen's size, then does [work] with it (off the main thread); [work] says what to tell. */
    fun full(work: (Bitmap) -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val text = withContext(Dispatchers.Default) {
                val bitmap = runCatching { ArtCatalog.render(piece, screenW, screenH) }.getOrNull()
                if (bitmap == null) {
                    "Das Bild konnte nicht gezeichnet werden."
                } else {
                    val result = work(bitmap)
                    bitmap.recycle()
                    result
                }
            }
            busy = false
            say(text)
        }
    }

    fun setWallpaper(home: Boolean, lock: Boolean) {
        choosing = false
        full { bitmap ->
            if (ArtExport.setAsWallpaper(context, bitmap, home, lock)) "„${piece.title}“ ist jetzt dein Hintergrund." else "Android hat den Hintergrund nicht angenommen."
        }
    }

    fun download() {
        full { bitmap ->
            val uri = ArtExport.saveToPictures(context, bitmap, "ZENITH-${piece.id}")
            if (uri != null) "Gespeichert unter Bilder/ZENITH." else "Speichern ging nicht – Android hat den Zugriff auf Bilder verweigert."
        }
    }

    fun share() {
        if (busy) return
        busy = true
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val bitmap = runCatching { ArtCatalog.render(piece, screenW, screenH) }.getOrNull()
                val saved = if (bitmap != null) ArtExport.saveToPictures(context, bitmap, "ZENITH-${piece.id}") else null
                bitmap?.recycle()
                saved
            }
            busy = false
            if (uri != null) ArtExport.share(context, uri) else say("Teilen ging nicht – das Bild konnte nicht gespeichert werden.")
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val shown = preview
            if (shown != null) {
                Image(bitmap = shown, contentDescription = piece.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 40.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(start = 18.dp, end = 18.dp, top = 54.dp, bottom = 30.dp),
            ) {
                Text(piece.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(piece.note, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("  Wird in der Größe deines Bildschirms gezeichnet …", color = Color.White, fontSize = 14.sp)
                    }
                } else if (choosing) {
                    Text("Als Hintergrund für …", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryButton("Startbildschirm", Modifier.weight(1f), true) { setWallpaper(home = true, lock = false) }
                        GalleryButton("Sperrbildschirm", Modifier.weight(1f), true) { setWallpaper(home = false, lock = true) }
                        GalleryButton("Beide", Modifier.weight(1f), true) { setWallpaper(home = true, lock = true) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryButton("Als Hintergrund", Modifier.weight(1.3f), true) { choosing = true }
                        GalleryButton("Herunterladen", Modifier.weight(1.2f), false) { download() }
                        GalleryButton("Teilen", Modifier.weight(0.8f), false) { share() }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryButton(label: String, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color(0xFF0B1020) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
