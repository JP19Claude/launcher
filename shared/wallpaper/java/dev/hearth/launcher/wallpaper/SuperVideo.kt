package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import java.io.File

/*
 * The video scenes ("Video 1", "Video 2"): a video of the user's own as a live wallpaper. It is chosen on the phone (a file of the gallery),
 * copied into the app's own storage and played on the wallpaper's surface – muted, in an endless loop, filling the screen. No video comes
 * with the app: what one puts there is one's own. A picture of its first moments is kept beside it (for the previews, the always-on screen
 * and the moment before the video runs).
 */
object SuperVideo {
    /** The biggest video taken (it lives in the app's storage twice over while it is copied). */
    private const val MAX_BYTES = 400L * 1024L * 1024L

    fun file(context: Context, scene: SuperScene): File = File(context.filesDir, "super_video_${scene.name.lowercase()}.mp4")

    /** The picture of the video's first moments. */
    fun frame(context: Context, scene: SuperScene): File = File(context.filesDir, "super_video_${scene.name.lowercase()}_frame.jpg")

    fun exists(context: Context, scene: SuperScene): Boolean = scene.isVideo && file(context, scene).let { it.exists() && it.length() > 0L }

    /** Copies the video behind [uri] into the app's storage for [scene]; false if it isn't a video the phone can show (or is too big). */
    fun save(context: Context, scene: SuperScene, uri: Uri): Boolean {
        if (!scene.isVideo) return false
        val target = file(context, scene)
        val temp = File(target.path + ".tmp")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { source ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IllegalStateException("video too big")
                        out.write(buffer, 0, n)
                    }
                }
            }
            val picture = firstPicture(temp) ?: throw IllegalStateException("not a video")
            frame(context, scene).outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            picture.recycle()
            target.delete()
            temp.renameTo(target)
        } catch (t: Throwable) {
            temp.delete()
            false
        }
    }

    private fun firstPicture(video: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            retriever.getFrameAtTime(400_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: retriever.getFrameAtTime(0L)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** Plays a video on a surface: muted, in a loop, filling the surface (the picture is cropped at its sides, never stretched). All calls on the thread that made it. */
internal class SuperVideoPlayer(private val file: File, private val onProblem: (Throwable) -> Unit) {
    private var player: MediaPlayer? = null
    private var prepared = false
    private var playing = false

    fun start(surface: Surface) {
        release()
        val p = MediaPlayer()
        player = p
        try {
            p.setDataSource(file.path)
            p.setSurface(surface)
            p.setVolume(0f, 0f)
            p.isLooping = true
            runCatching { p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) }
            p.setOnPreparedListener { ready ->
                prepared = true
                runCatching { ready.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) }
                if (playing) runCatching { ready.start() }
            }
            p.setOnErrorListener { _, what, extra ->
                onProblem(IllegalStateException("video: $what/$extra"))
                true
            }
            p.prepareAsync()
        } catch (t: Throwable) {
            release()
            onProblem(t)
        }
    }

    /** Runs the video (the wallpaper is seen) or holds it (it isn't). */
    fun play(on: Boolean) {
        playing = on
        val p = player ?: return
        if (!prepared) return
        runCatching {
            if (on) {
                if (!p.isPlaying) p.start()
            } else if (p.isPlaying) {
                p.pause()
            }
        }
    }

    fun release() {
        val p = player ?: return
        player = null
        prepared = false
        runCatching { p.setOnPreparedListener(null) }
        runCatching { p.setOnErrorListener(null) }
        runCatching { p.reset() }
        runCatching { p.release() }
    }
}
