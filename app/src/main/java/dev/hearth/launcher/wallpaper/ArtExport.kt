package dev.hearth.launcher.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display

/*
 * ZENITH 21.5 – what the wallpaper gallery does with a picture: make it the phone's wallpaper (home screen, lock screen
 * or both), save it as an image in Pictures/ZENITH (the "download"), or share it.
 */
internal object ArtExport {

    /** The screen's real size, standing upright (width × height). */
    @Suppress("DEPRECATION")
    fun screenSize(context: Context): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        display.getRealMetrics(metrics)
        return minOf(metrics.widthPixels, metrics.heightPixels) to maxOf(metrics.widthPixels, metrics.heightPixels)
    }

    /** Saves [bitmap] as Pictures/ZENITH/[name].png; the address of the new image, or null if Android wouldn't. */
    fun saveToPictures(context: Context, bitmap: Bitmap, name: String): Uri? = try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZENITH")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            null
        } else {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        }
    } catch (e: Exception) {
        null
    }

    /** Makes [bitmap] the phone's wallpaper: the home screen, the lock screen or both. True if it worked. */
    @SuppressLint("MissingPermission")
    fun setAsWallpaper(context: Context, bitmap: Bitmap, home: Boolean, lock: Boolean): Boolean = try {
        val flags = (if (home) WallpaperManager.FLAG_SYSTEM else 0) or (if (lock) WallpaperManager.FLAG_LOCK else 0)
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags) > 0
    } catch (e: Exception) {
        false
    }

    /** Opens Android's share sheet for an image saved earlier. */
    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(send, "Hintergrund teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
