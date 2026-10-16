package dev.hearth.launcher.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.wallpaper.ArtCatalog
import dev.hearth.launcher.wallpaper.ArtExport
import dev.hearth.launcher.wallpaper.ArtGroup
import dev.hearth.launcher.wallpaper.ArtPiece
import dev.hearth.launcher.wallpaper.NasaGroup
import dev.hearth.launcher.wallpaper.NasaImage
import dev.hearth.launcher.wallpaper.NasaLibrary
import dev.hearth.launcher.wallpaper.NasaTopic
import dev.hearth.launcher.wallpaper.SuperPhoto
import dev.hearth.launcher.wallpaper.SuperScene
import dev.hearth.launcher.wallpaper.SuperWallpaperService
import dev.hearth.launcher.wallpaper.WallpaperCut
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/*
 * ZENITH 21.5, the wallpaper gallery's page in the settings. Above all the real photos of NASA (the Moon in all its
 * phases, Apollo, craters, Earth, the planets, Hubble and Webb – public domain, fetched from NASA's image library when
 * wanted), and next to them the pictures ZENITH draws itself. Tapping one shows it large (drag to move the section
 * that fills the screen); from there it becomes the wallpaper (home screen, lock screen or both), is saved in
 * Pictures/ZENITH (the download) or is shared.
 */

/** The small pictures of the drawn pieces, drawn once and kept (a few at a time, so the page doesn't stall). */
private object ArtThumbs {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
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

/** The small pictures of NASA's photos, fetched once and kept. */
private object NasaThumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(4)

    /** The grid's tiles are a little narrower than high (width ÷ height). */
    const val TILE_ASPECT = 0.8f

    fun peek(image: NasaImage): Bitmap? = cache.get(image.id)

    suspend fun load(image: NasaImage): Bitmap? {
        peek(image)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                val small = image.thumb.replace("~thumb.jpg", "~small.jpg")
                val bitmap = NasaLibrary.fetchThumb(small, 480, TILE_ASPECT) ?: NasaLibrary.fetchThumb(image.thumb, 480, TILE_ASPECT)
                if (bitmap != null) cache.put(image.id, bitmap)
                bitmap
            }
        }
    }
}

/** The pictures found for one topic, page by page. */
private class NasaFeed(val topic: NasaTopic) {
    val items = mutableStateListOf<NasaImage>()
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var more by mutableStateOf(true)
    private var page = 0

    suspend fun loadMore() {
        if (loading || !more) return
        loading = true
        failed = false
        val result = withContext(Dispatchers.IO) { runCatching { NasaLibrary.search(topic, page) }.getOrNull() }
        if (result == null) {
            failed = true
        } else {
            val known = items.map { it.id }.toHashSet()
            items.addAll(result.images.filter { it.id !in known })
            page++
            more = result.hasMore && result.images.isNotEmpty()
        }
        loading = false
    }
}

private class GalleryTab(val id: String, val label: String)

private val GalleryTabs = listOf(
    GalleryTab("moon", "Mond"),
    GalleryTab("earth", "Erde"),
    GalleryTab("planets", "Planeten"),
    GalleryTab("space", "Weltraum"),
    GalleryTab("drawn", "Gezeichnet"),
)

@Composable
private fun GalleryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color(0xFF0B1020) else Color.White,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** The settings page "Wallpaper-Galerie". */
@Composable
internal fun WallpaperGallerySection(update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tabId by rememberSaveable { mutableStateOf("moon") }
    var topicId by rememberSaveable { mutableStateOf("") }
    var drawnName by rememberSaveable { mutableStateOf(ArtGroup.Space.name) }
    var openedArt by remember { mutableStateOf<ArtPiece?>(null) }
    var openedNasa by remember { mutableStateOf<NasaImage?>(null) }
    val feeds = remember { HashMap<String, NasaFeed>() }
    val (screenW, screenH) = remember { ArtExport.screenSize(context) }
    val thumbW = 180
    val thumbH = (thumbW * screenH.toFloat() / screenW).toInt()
    val nasaThumbW = 180
    val nasaThumbH = (nasaThumbW / NasaThumbs.TILE_ASPECT).toInt()
    val tab = GalleryTabs.firstOrNull { it.id == tabId } ?: GalleryTabs[0]

    Section("Wallpaper-Galerie") {
        Note("Echte Fotos der NASA – sehr viele vom Mond, dazu Erde, Planeten und Weltraum (gemeinfrei, sie werden erst geladen, wenn du sie ansiehst) – und ${ArtCatalog.all.size} Bilder, die ZENITH selbst zeichnet. Antippen zeigt das Bild groß: mit dem Finger verschieben, mit zwei Fingern zoomen oder „Ganzes Foto“ wählen. Von dort wird es dein Hintergrund oder dein Live-Wallpaper, landet als Bild in „Bilder/ZENITH“ oder wird geteilt.")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (t in GalleryTabs) GalleryChip(t.label, t.id == tab.id) { tabId = t.id }
        }

        if (tab.id == "drawn") {
            val group = ArtGroup.entries.firstOrNull { it.name == drawnName } ?: ArtGroup.Space
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (g in ArtGroup.entries) GalleryChip("${g.label} · ${ArtCatalog.of(g).size}", g == group) { drawnName = g.name }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (row in ArtCatalog.of(group).chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (piece in row) ArtTile(piece, thumbW, thumbH, Modifier.weight(1f)) { openedArt = piece }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            val group = NasaGroup.entries.first { it.label == tab.label }
            val topics = NasaLibrary.of(group)
            val topic = topics.firstOrNull { it.id == topicId } ?: topics.first()
            val feed = feeds.getOrPut(topic.id) { NasaFeed(topic) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (t in topics) GalleryChip(t.label, t.id == topic.id) { topicId = t.id }
            }
            LaunchedEffect(topic.id) {
                if (feed.items.isEmpty()) {
                    feed.loadMore()
                    feed.loadMore()
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (row in feed.items.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (image in row) NasaTile(image, nasaThumbW, nasaThumbH, Modifier.weight(1f)) { openedNasa = image }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (feed.loading) {
                Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
                }
            } else if (feed.failed) {
                Text(
                    "Keine Verbindung zur NASA-Bibliothek. Tippen, um es noch einmal zu versuchen.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { feed.loadMore() } }
                        .padding(14.dp),
                )
            } else if (feed.more && feed.items.isNotEmpty()) {
                Text(
                    "Mehr zeigen",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { scope.launch { feed.loadMore() } }
                        .padding(vertical = 12.dp),
                )
            } else if (feed.items.isEmpty()) {
                Note("Dazu hat die NASA-Bibliothek nichts gefunden.")
            }
            Note("Bilder: NASA Image and Video Library (images.nasa.gov). NASA-Bilder sind in der Regel gemeinfrei; Titel und Nummer jedes Bildes stehen in der Großansicht.")
        }
    }
    openedArt?.let { piece -> ArtPreview(piece, screenW, screenH) { openedArt = null } }
    openedNasa?.let { image -> NasaPreview(image, screenW, screenH, update) { openedNasa = null } }
}

@Composable
private fun TileFrame(modifier: Modifier, thumbW: Int, thumbH: Int, title: String, image: ImageBitmap?, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(thumbW.toFloat() / thumbH)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1220))
            .clickable(onClick = onClick),
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.66f))))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(
                title,
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

@Composable
private fun ArtTile(piece: ArtPiece, thumbW: Int, thumbH: Int, modifier: Modifier, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(initialValue = ArtThumbs.peek(piece.id, thumbW)?.asImageBitmap(), piece.id, thumbW) {
        if (value == null) value = ArtThumbs.load(piece, thumbW, thumbH)?.asImageBitmap()
    }
    TileFrame(modifier, thumbW, thumbH, piece.title, image, onClick)
}

@Composable
private fun NasaTile(item: NasaImage, thumbW: Int, thumbH: Int, modifier: Modifier, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(initialValue = NasaThumbs.peek(item)?.asImageBitmap(), item.id) {
        if (value == null) value = NasaThumbs.load(item)?.asImageBitmap()
    }
    TileFrame(modifier, thumbW, thumbH, item.title, image, onClick)
}

/**
 * The large view round a wallpaper: the picture ([content]), a close button and, below, its name, what it is ([extra] may add
 * controls) and the things to do with it. [onWallpaper] gets "home" and "lock"; [onLive] (if there is one) makes it the
 * moving Super-Wallpaper. [busy] (if not null) is shown instead of the buttons.
 */
@Composable
private fun WallpaperViewer(
    title: String,
    note: String,
    busy: String?,
    onClose: () -> Unit,
    onWallpaper: (Boolean, Boolean) -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onLive: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            content()
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(start = 18.dp, end = 18.dp, top = 54.dp, bottom = 26.dp),
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(note, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                if (busy != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("  $busy", color = Color.White, fontSize = 14.sp)
                    }
                } else if (choosing) {
                    Text("Als Hintergrund für …", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryButton("Startbildschirm", Modifier.weight(1f), true) { choosing = false; onWallpaper(true, false) }
                        GalleryButton("Sperrbildschirm", Modifier.weight(1f), true) { choosing = false; onWallpaper(false, true) }
                        GalleryButton("Beide", Modifier.weight(1f), true) { choosing = false; onWallpaper(true, true) }
                    }
                } else {
                    extra?.invoke()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryButton("Als Hintergrund", Modifier.weight(1f), true) { choosing = true }
                        if (onLive != null) GalleryButton("Als Live-Wallpaper", Modifier.weight(1f), true) { onLive() }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryButton("Herunterladen", Modifier.weight(1f), false) { onDownload() }
                        GalleryButton("Teilen", Modifier.weight(1f), false) { onShare() }
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

/** What is done with a finished wallpaper picture (off the main thread); the text says how it went. */
private typealias WallpaperWork = (Bitmap) -> String

/** One drawn wallpaper, large. */
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
    var busy by remember { mutableStateOf<String?>(null) }

    fun say(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    fun full(work: WallpaperWork) {
        if (busy != null) return
        busy = "Wird in der Größe deines Bildschirms gezeichnet …"
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
            busy = null
            say(text)
        }
    }

    WallpaperViewer(
        title = piece.title,
        note = piece.note,
        busy = busy,
        onClose = onClose,
        onWallpaper = { home, lock ->
            full { bitmap ->
                if (ArtExport.setAsWallpaper(context, bitmap, home, lock)) "„${piece.title}“ ist jetzt dein Hintergrund." else "Android hat den Hintergrund nicht angenommen."
            }
        },
        onDownload = {
            full { bitmap ->
                if (ArtExport.saveToPictures(context, bitmap, "ZENITH-${piece.id}") != null) "Gespeichert unter Bilder/ZENITH." else "Speichern ging nicht – Android hat den Zugriff auf Bilder verweigert."
            }
        },
        onShare = {
            if (busy == null) {
                busy = "Wird gezeichnet …"
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        val bitmap = runCatching { ArtCatalog.render(piece, screenW, screenH) }.getOrNull()
                        val saved = if (bitmap != null) ArtExport.saveToPictures(context, bitmap, "ZENITH-${piece.id}") else null
                        bitmap?.recycle()
                        saved
                    }
                    busy = null
                    if (uri != null) ArtExport.share(context, uri) else say("Teilen ging nicht – das Bild konnte nicht gespeichert werden.")
                }
            }
        },
    ) {
        val shown = preview
        if (shown != null) {
            Image(bitmap = shown, contentDescription = piece.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

/** What a background job found out: the words to tell, and whether it worked. */
private class WorkResult(val text: String, val ok: Boolean)

/** The tiny, blurred copy of a photo that serves as the ground when the whole photo is shown. */
private fun groundOf(photo: Bitmap, aspect: Float): ImageBitmap? = try {
    val c = NasaLibrary.cover(photo.width, photo.height, aspect, 1f, 0.5f, 0.5f)
    val piece = Bitmap.createBitmap(photo, c.x, c.y, c.w, c.h)
    Bitmap.createScaledBitmap(piece, 36, max(1, (36 / aspect).toInt()), true).asImageBitmap()
} catch (e: Throwable) {
    null
}

/**
 * One of NASA's photos, large – exactly as it will become the wallpaper: drag to move it, two fingers to zoom, or show the
 * whole photo on a blurred ground. What is seen here is what is made.
 */
@Composable
private fun NasaPreview(image: NasaImage, screenW: Int, screenH: Int, update: ((LauncherSettings) -> LauncherSettings) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aspect = screenW.toFloat() / screenH
    var busy by remember { mutableStateOf<String?>(null) }
    var fit by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0.5f) }
    var panY by remember { mutableFloatStateOf(0.5f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val thumb = remember(image.id) { NasaThumbs.peek(image)?.asImageBitmap() }
    // The big picture for looking at: the "large" file, or – if there is none – a smaller one.
    val photo by produceState<Bitmap?>(initialValue = null, image.id) {
        value = withContext(Dispatchers.IO) {
            val assets = NasaLibrary.assets(image)
            val file = listOfNotNull(assets.large, assets.medium, assets.small)
                .firstNotNullOfOrNull { NasaLibrary.fetchFile(context, it, 30L * 1024 * 1024) }
            if (file == null) null else NasaLibrary.decodeSampled(file, 2000)
        }
    }
    val photoImage = remember(photo) { photo?.asImageBitmap() }
    val ground = remember(photo) { photo?.let { groundOf(it, aspect) } }

    fun say(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Fetches the photo in full size (the original, if it isn't gigantic), cuts it as chosen, [work]s with it, then [after] on success. */
    fun act(factor: Float, work: (Bitmap) -> WorkResult, after: () -> Unit = {}) {
        if (busy != null) return
        busy = "Das Foto wird geladen …"
        val cut = WallpaperCut(fit, zoom, panX, panY)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val assets = NasaLibrary.assets(image)
                val file = listOfNotNull(assets.orig, assets.large, assets.medium, assets.small)
                    .firstNotNullOfOrNull { NasaLibrary.fetchFile(context, it, 60L * 1024 * 1024) }
                if (file == null) {
                    WorkResult("Das Foto konnte nicht geladen werden – ist das Telefon online?", false)
                } else {
                    val bitmap = NasaLibrary.compose(file, (screenW * factor).roundToInt(), (screenH * factor).roundToInt(), cut)
                    if (bitmap == null) {
                        WorkResult("Das Foto konnte nicht zugeschnitten werden.", false)
                    } else {
                        val done = work(bitmap)
                        bitmap.recycle()
                        done
                    }
                }
            }
            busy = null
            if (result.text.isNotEmpty()) say(result.text)
            if (result.ok) after()
        }
    }

    val fileName = "ZENITH-NASA-" + image.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val credit = "NASA · gemeinfrei · ${image.id}" + if (image.description.isNotBlank()) "\n${image.description}" else ""

    WallpaperViewer(
        title = image.title,
        note = credit,
        busy = busy,
        onClose = onClose,
        onWallpaper = { home, lock ->
            act(1f, { bitmap ->
                if (ArtExport.setAsWallpaper(context, bitmap, home, lock)) {
                    WorkResult("Das Foto ist jetzt dein Hintergrund.", true)
                } else {
                    WorkResult("Android hat den Hintergrund nicht angenommen.", false)
                }
            })
        },
        onDownload = {
            act(1f, { bitmap ->
                if (ArtExport.saveToPictures(context, bitmap, fileName) != null) {
                    WorkResult("Gespeichert unter Bilder/ZENITH.", true)
                } else {
                    WorkResult("Speichern ging nicht – Android hat den Zugriff auf Bilder verweigert.", false)
                }
            })
        },
        onShare = {
            var saved: Uri? = null
            act(1f, { bitmap ->
                saved = ArtExport.saveToPictures(context, bitmap, fileName)
                if (saved != null) WorkResult("", true) else WorkResult("Teilen ging nicht – das Foto konnte nicht gespeichert werden.", false)
            }, after = { saved?.let { ArtExport.share(context, it) } })
        },
        onLive = {
            // A little larger than the screen, so the Super-Wallpaper has room to move.
            act(1.15f, { bitmap ->
                if (SuperPhoto.save(context, bitmap)) WorkResult("", true) else WorkResult("Das Foto konnte nicht gespeichert werden.", false)
            }, after = {
                update { it.copy(superParams = it.superParams.copy(scene = SuperScene.Photo, photo = System.currentTimeMillis())) }
                if (SuperWallpaperService.isActiveNow(context)) {
                    say("Das Foto ist jetzt dein Super-Wallpaper.")
                } else {
                    say("Das Foto ist als Super-Wallpaper gespeichert – in Androids Vorschau noch „Hintergrund festlegen“ wählen.")
                    openSuperWallpaper(context)
                }
            })
        },
        extra = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GalleryChip("Füllen", !fit) { fit = false }
                GalleryChip("Ganzes Foto", fit) { fit = true }
                Text(
                    if (fit) "Das ganze Foto, mit unscharfem Rand" else "Ziehen verschiebt, zwei Finger zoomen",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .aspectRatio(aspect)
                .onSizeChanged { box = it }
                .pointerInput(photo, fit) {
                    if (!fit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val img = photo
                            if (img != null && box.width > 0 && box.height > 0) {
                                val current = NasaLibrary.cover(img.width, img.height, aspect, zoom, panX, panY)
                                val base = NasaLibrary.cover(img.width, img.height, aspect, 1f, 0.5f, 0.5f)
                                val newZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                                val newW = base.w / newZoom
                                val newH = base.h / newZoom
                                val cx = current.x + current.w / 2f - pan.x * current.w / box.width
                                val cy = current.y + current.h / 2f - pan.y * current.h / box.height
                                val maxX = img.width - newW
                                val maxY = img.height - newH
                                val nx = (cx - newW / 2f).coerceIn(0f, max(maxX, 0f))
                                val ny = (cy - newH / 2f).coerceIn(0f, max(maxY, 0f))
                                panX = if (maxX > 0.5f) nx / maxX else 0.5f
                                panY = if (maxY > 0.5f) ny / maxY else 0.5f
                                zoom = newZoom
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val img = photoImage ?: thumb
                if (img != null) {
                    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    if (!fit) {
                        val c = NasaLibrary.cover(img.width, img.height, aspect, zoom, panX, panY)
                        drawImage(
                            img,
                            srcOffset = IntOffset(c.x, c.y),
                            srcSize = IntSize(c.w, c.h),
                            dstSize = dst,
                            filterQuality = FilterQuality.Medium,
                        )
                    } else {
                        val g = ground
                        if (g != null) {
                            drawImage(g, dstSize = dst, filterQuality = FilterQuality.Low)
                            drawRect(Color.Black.copy(alpha = 0.37f))
                        }
                        val scale = min(dst.width / img.width.toFloat(), dst.height / img.height.toFloat())
                        val dw = (img.width * scale).roundToInt()
                        val dh = (img.height * scale).roundToInt()
                        drawImage(
                            img,
                            dstOffset = IntOffset((dst.width - dw) / 2, (dst.height - dh) / 2),
                            dstSize = IntSize(dw, dh),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            }
            if (photo == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}
