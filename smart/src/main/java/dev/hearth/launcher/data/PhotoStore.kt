package dev.hearth.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Photos of the photo widgets. The chosen pictures are copied (scaled down) into the
 * launcher's own storage, so they keep working without any gallery permission.
 */
class PhotoStore(private val context: Context) {

    private val root = File(context.filesDir, "photo-widgets")

    private val _versions = MutableStateFlow<Map<Int, Long>>(emptyMap())

    /** Bumped per widget when its photos change, so the widget reloads. */
    val versions: StateFlow<Map<Int, Long>> = _versions.asStateFlow()

    private fun dir(widgetId: Int) = File(root, widgetId.toString())

    fun photos(widgetId: Int): List<File> =
        dir(widgetId).listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.name }.orEmpty()

    /** Replaces the widget's photos with these. Returns how many could be imported. */
    suspend fun import(widgetId: Int, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val target = dir(widgetId)
        target.deleteRecursively()
        target.mkdirs()
        var count = 0
        uris.forEachIndexed { index, uri ->
            val bitmap = runCatching { decode(uri) }.getOrNull() ?: return@forEachIndexed
            runCatching {
                File(target, "%03d.jpg".format(index)).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                count++
            }
            bitmap.recycle()
        }
        _versions.update { it + (widgetId to System.currentTimeMillis()) }
        count
    }

    fun delete(widgetId: Int) {
        dir(widgetId).deleteRecursively()
        _versions.update { it - widgetId }
    }

    /** Loads one photo at about the size it is shown. */
    suspend fun load(file: File, maxSide: Int = 1080): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }

    /** Decodes with the right rotation, scaled so the longer side is at most 1440 px. */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private companion object {
        const val MAX_SIDE = 1440
    }
}
