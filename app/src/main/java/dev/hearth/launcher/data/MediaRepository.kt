package dev.hearth.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import dev.hearth.launcher.system.MediaListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** What is playing right now, for the media card. */
@Immutable
data class NowPlaying(
    val title: String,
    val artist: String,
    val appLabel: String,
    val art: ImageBitmap?,
    /** Average color of the cover, to tint the glass. */
    val artColor: Color?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** When [positionMs] was measured (elapsedRealtime), to move the progress bar along. */
    val positionUpdated: Long,
    val speed: Float,
    /** Two or three vivid colors from the cover (for Glimmer's Hyper Island look). */
    val artPalette: List<Color> = emptyList(),
) {
    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing) return positionMs
        val moved = ((now - positionUpdated) * speed).toLong()
        return (positionMs + moved).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }
}

/**
 * Follows the active media sessions (Spotify, YouTube Music, podcasts, …).
 * Needs notification access; without it the card asks for it.
 */
class MediaRepository(private val context: Context) {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private var users = 0

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun requestAccess() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!started) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Call while a media card is visible; calls are counted, so several cards can share this. */
    fun start() {
        users++
        if (users == 1) {
            runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, listener, handler) }
        }
        refreshSessions()
    }

    fun stop() {
        users = max(0, users - 1)
        if (users > 0) return
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun refreshSessions() {
        if (!hasAccess()) {
            _nowPlaying.value = null
            return
        }
        val list = runCatching { sessions?.getActiveSessions(listener) }.getOrNull().orEmpty()
        pick(list)
    }

    /** The session that is playing, or else the most recent one. */
    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private var lastArtSource: Bitmap? = null
    private var lastArt: Pair<ImageBitmap, Color>? = null
    private var lastPalette: List<Color> = emptyList()

    private fun publish() {
        val c = controller
        val metadata = c?.metadata
        val state = c?.playbackState
        if (c == null || metadata == null) {
            _nowPlaying.value = null
            return
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val source = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val sameArt = source === lastArtSource
        val art = if (sameArt) lastArt else source?.let(::prepareArt)
        val palette = if (sameArt) lastPalette else source?.let(::vividColors).orEmpty()
        lastArtSource = source
        lastArt = art
        lastPalette = palette

        val appLabel = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)

        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            appLabel = appLabel,
            art = art?.first,
            artColor = art?.second,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0L,
            positionUpdated = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            artPalette = palette,
        )
    }

    /**
     * The cover's most vivid colors, a few different hues, brightened so they glow on black.
     * Grey covers give soft white and their average tone.
     */
    private fun vividColors(bitmap: Bitmap): List<Color> = runCatching {
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val tiny = Bitmap.createScaledBitmap(soft, 12, 12, true)
        val hsv = FloatArray(3)
        val candidates = buildList {
            for (y in 0 until tiny.height) for (x in 0 until tiny.width) {
                android.graphics.Color.colorToHSV(tiny.getPixel(x, y), hsv)
                add(Triple(hsv[0], hsv[1], hsv[2]))
            }
        }.sortedByDescending { (_, sat, value) -> sat * (0.35f + value) }
        val picked = mutableListOf<Triple<Float, Float, Float>>()
        for (c in candidates) {
            if (c.second < 0.18f) break
            val far = picked.all { p ->
                val d = kotlin.math.abs(p.first - c.first)
                minOf(d, 360f - d) > 28f
            }
            if (far) picked += c
            if (picked.size == 3) break
        }
        if (picked.isEmpty()) return@runCatching listOf(Color(0xFFEDEDED), Color(0xFF9A9AA5))
        picked.map { (hue, sat, value) ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0.45f, 0.9f), value.coerceIn(0.8f, 1f))))
        }.let { if (it.size == 1) it + it.first().copy(alpha = 1f) else it }
    }.getOrDefault(emptyList())

    /** Small copy of the cover plus its average color. */
    private fun prepareArt(bitmap: Bitmap): Pair<ImageBitmap, Color>? = runCatching {
        val size = 256
        val scaled = if (bitmap.width > size || bitmap.height > size) {
            val ratio = size.toFloat() / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val pixel = Bitmap.createScaledBitmap(scaled, 1, 1, true).getPixel(0, 0)
        scaled.asImageBitmap() to Color(pixel)
    }.getOrNull()

    /**
     * Pauses or resumes. Some players only take the media buttons (no "play" action), or drop
     * the session's controls while paused; then the play/pause key is sent to the session.
     */
    fun playPause() {
        val c = controller ?: run {
            refreshSessions()
            controller
        } ?: return
        val state = c.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING
        val actions = state?.actions ?: 0L
        val canPlay = actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L
        val canPause = actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L
        when {
            playing && canPause -> c.transportControls.pause()
            !playing && canPlay -> c.transportControls.play()
            else -> mediaButton(c, if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun mediaButton(c: MediaController, keyCode: Int) {
        runCatching {
            c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    /** Opens the app that is playing. */
    fun openPlayer() {
        val c = controller ?: return
        val intent = c.sessionActivity
        if (intent != null) {
            // Android 14+: the sender has to vouch for starting the player's screen.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            runCatching { intent.send(context, 0, null, null, null, null, options) }
                .onFailure { runCatching { intent.send() } }
        } else {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let { launch ->
                runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    /** True while a session exists we can control directly (otherwise media keys are used). */
    val hasSession: Boolean get() = controller != null
}
