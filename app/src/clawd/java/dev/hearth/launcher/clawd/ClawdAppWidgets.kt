package dev.hearth.launcher.clawd

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import dev.hearth.launcher.R
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdOracle
import dev.hearth.launcher.data.ClawdPet
import dev.hearth.launcher.data.ClawdSkin
import dev.hearth.launcher.data.ClawdTalk
import dev.hearth.launcher.data.SettingsRepository
import dev.hearth.launcher.data.clawdAsleep
import dev.hearth.launcher.data.clawdGreeting
import dev.hearth.launcher.system.ClawdLink
import dev.hearth.launcher.ui.rainbow
import dev.hearth.launcher.ui.renderClawd
import java.time.LocalDateTime

/*
 * Clawd's widgets for any launcher (Samsung's One UI Home too). Another launcher can't run
 * Hearth's Compose widgets, so Clawd is drawn into a picture with the same pixels, and taps
 * come back here as broadcasts: next pose, next joke, feed him …
 */

/** Clawd's look as set in the app (or handed over by Hearth). */
private class Look(val color: Color, val hat: ClawdHat, val accent: Color) {
    companion object {
        fun of(context: Context): Look {
            val s = SettingsRepository(context).settings.value
            val color = if (s.clawdSkin == ClawdSkin.Rainbow) {
                rainbow((System.currentTimeMillis() / 60_000 % 360) / 360f)
            } else {
                s.clawdSkin.color
            }
            return Look(color, s.clawdHat, s.accent.color)
        }
    }
}

private fun widgetPrefs(context: Context) = context.getSharedPreferences("clawd_widgets", Context.MODE_PRIVATE)

/** The poses a tap goes through. */
private val Poses = listOf(ClawdMood.Idle, ClawdMood.Wave, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Flip, ClawdMood.Sleep)

/** Clawd alone on a transparent picture. */
private fun clawdPicture(context: Context, mood: ClawdMood, width: Int = 280, height: Int = 230): Bitmap {
    val look = Look.of(context)
    return renderClawd(width, height, mood, look.color, look.hat)
}

/** What all of Clawd's widgets share: updating, taps, refreshing when his look changes. */
abstract class ClawdWidgetProvider : AppWidgetProvider() {

    /** The widget [id] as it should look now. */
    abstract fun views(context: Context, id: Int): RemoteViews

    /** A tap on part [what] of widget [id]. */
    open fun act(context: Context, id: Int, what: String) {}

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> runCatching { manager.updateAppWidget(id, views(context, id)) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        runCatching { manager.updateAppWidget(id, views(context, id)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TAP -> {
                val id = intent.getIntExtra(EXTRA_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val what = intent.getStringExtra(EXTRA_WHAT) ?: return
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                runCatching { act(context, id, what) }
                update(context, id)
            }
            ClawdLink.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
            }
        }
    }

    protected fun update(context: Context, id: Int) {
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, id)) }
    }

    /** A tap on [what] of widget [id], back to this provider. */
    protected fun tap(context: Context, id: Int, what: String): PendingIntent {
        val intent = Intent(context, javaClass)
            .setAction(ACTION_TAP)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_WHAT, what)
        return PendingIntent.getBroadcast(
            context,
            id * 31 + what.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Opens the Clawd app; [ask] puts the cursor straight into the chat. */
    protected fun openApp(context: Context, ask: Boolean = false): PendingIntent {
        val intent = Intent(context, ClawdAppActivity::class.java)
            .putExtra(ClawdAppActivity.EXTRA_ASK, ask)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            if (ask) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_TAP = "dev.hearth.clawd.TAP"
        const val EXTRA_ID = "id"
        const val EXTRA_WHAT = "what"

        /** All of Clawd's widget kinds, for the app's gallery and for refreshing. */
        val All: List<Class<out ClawdWidgetProvider>> = listOf(
            ClawdPictureWidget::class.java,
            ClawdClockWidget::class.java,
            ClawdAskWidget::class.java,
            ClawdPetWidget::class.java,
            ClawdJokeWidget::class.java,
            ClawdBatteryWidget::class.java,
            ClawdOracleWidget::class.java,
            ClawdStickerWidget::class.java,
        )

        /** Draws every placed widget again (his look changed, the pet was fed in the app …). */
        fun refreshAll(context: Context) {
            context.sendBroadcast(Intent(ClawdLink.ACTION_REFRESH).setPackage(context.packageName))
        }
    }
}

// Clawd-Bild

/** The backgrounds of Clawd's picture. */
private enum class Scene { Light, Terracotta, Night, Sunset, Accent, Glass }

class ClawdPictureWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val prefs = widgetPrefs(context)
        val scene = Scene.entries.getOrElse(prefs.getInt("scene_$id", 0)) { Scene.Light }
        val mood = Poses.getOrElse(prefs.getInt("pose_$id", 1)) { ClawdMood.Wave }
        val look = Look.of(context)
        val ivory = Color(0xFFFAF9F5)
        val body = if (scene == Scene.Terracotta || scene == Scene.Accent) ivory else look.color
        val picture = renderClawd(440, 440, mood, body, look.hat, clawdScale = 0.66f) {
            drawScene(scene, look.accent)
        }
        return RemoteViews(context.packageName, R.layout.clawd_widget_picture).apply {
            setImageViewBitmap(R.id.image, picture)
            setTextColor(R.id.scene, if (scene == Scene.Light) 0xFF6B6A66.toInt() else 0xFFFFFFFF.toInt())
            setOnClickPendingIntent(R.id.image, tap(context, id, "pose"))
            setOnClickPendingIntent(R.id.scene, tap(context, id, "scene"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        when (what) {
            "pose" -> prefs.edit().putInt("pose_$id", (prefs.getInt("pose_$id", 1) + 1) % Poses.size).apply()
            "scene" -> prefs.edit().putInt("scene_$id", (prefs.getInt("scene_$id", 0) + 1) % Scene.entries.size).apply()
        }
    }
}

/** The picture's background, with round corners like a widget. */
private fun DrawScope.drawScene(scene: Scene, accent: Color) {
    val corner = CornerRadius(size.minDimension * 0.11f)
    val brush = when (scene) {
        Scene.Light -> Brush.verticalGradient(listOf(Color(0xFFFAF9F5), Color(0xFFF0EEE6)))
        Scene.Terracotta -> Brush.verticalGradient(listOf(Color(0xFFE08A6B), Color(0xFFD97757)))
        Scene.Night -> Brush.verticalGradient(listOf(Color(0xFF0F1630), Color(0xFF242B4D)))
        Scene.Sunset -> Brush.verticalGradient(listOf(Color(0xFF5B4B8A), Color(0xFFE0837A), Color(0xFFF6C27A)))
        Scene.Accent -> Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.6f)))
        Scene.Glass -> Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
    }
    drawRoundRect(brush, cornerRadius = corner)
    if (scene == Scene.Night) {
        val px = size.minDimension / 60f
        listOf(0.12f to 0.14f, 0.3f to 0.3f, 0.52f to 0.1f, 0.74f to 0.24f, 0.88f to 0.12f, 0.2f to 0.5f, 0.84f to 0.46f)
            .forEachIndexed { i, (x, y) ->
                val s = px * if (i % 3 == 0) 2f else 1.4f
                drawRect(Color.White.copy(alpha = 0.5f + 0.1f * i), Offset(size.width * x, size.height * y), Size(s, s))
            }
    }
    // A soft shadow under his feet.
    val w = size.minDimension * 0.42f
    drawOval(Color.Black.copy(alpha = if (scene == Scene.Light) 0.08f else 0.18f), Offset((size.width - w) / 2f, size.height * 0.79f), Size(w, w * 0.12f))
}

// Clawd-Uhr

class ClawdClockWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val hour = LocalDateTime.now().hour
        val look = Look.of(context)
        return RemoteViews(context.packageName, R.layout.clawd_widget_clock).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (clawdAsleep(hour)) ClawdMood.Sleep else ClawdMood.Wave))
            setTextViewText(R.id.greeting, clawdGreeting(hour))
            setTextColor(R.id.greeting, (if (look.accent == Color.White) Color(0xFFE8A07F) else look.accent).toArgb())
            setOnClickPendingIntent(R.id.root, openApp(context))
        }
    }
}

// Frag Clawd

class ClawdAskWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val hour = LocalDateTime.now().hour
        return RemoteViews(context.packageName, R.layout.clawd_widget_ask).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (clawdAsleep(hour)) ClawdMood.Sleep else ClawdMood.Idle, 200, 170))
            setTextViewText(R.id.sub, "${clawdGreeting(hour)} Tippen und losreden")
            setOnClickPendingIntent(R.id.root, openApp(context, ask = true))
        }
    }
}

// Clawd-Witz

class ClawdJokeWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val extra = widgetPrefs(context).getInt("joke_$id", 0)
        val day = LocalDateTime.now().dayOfYear
        return RemoteViews(context.packageName, R.layout.clawd_widget_joke).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (extra % 2 == 0) ClawdMood.Wave else ClawdMood.Dance))
            setTextViewText(R.id.text, ClawdTalk.joke(day + extra))
            setOnClickPendingIntent(R.id.root, tap(context, id, "next"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        prefs.edit().putInt("joke_$id", prefs.getInt("joke_$id", 0) + 1).apply()
    }
}

// Clawd-Akku

class ClawdBatteryWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val battery = runCatching { context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val raw = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = if (raw < 0) -1 else raw * 100 / scale
        val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val low = level in 0..15
        val mood = when {
            charging -> ClawdMood.Dance
            low -> ClawdMood.Sleep
            else -> ClawdMood.Idle
        }
        return RemoteViews(context.packageName, R.layout.clawd_widget_battery).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, mood))
            setProgressBar(R.id.bar, 100, level.coerceAtLeast(0), false)
            setTextViewText(R.id.percent, if (level >= 0) "$level %" else "–")
            setTextViewText(
                R.id.status,
                when {
                    charging -> "Ich tanke auf! ⚡"
                    low -> "Bin müde … Ladekabel?"
                    else -> "Voll fit · tippen zum Auffrischen"
                },
            )
            setOnClickPendingIntent(R.id.root, tap(context, id, "refresh"))
        }
    }
}

// Clawd-Sticker

class ClawdStickerWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val mood = Poses.getOrElse(widgetPrefs(context).getInt("pose_$id", 1)) { ClawdMood.Wave }
        return RemoteViews(context.packageName, R.layout.clawd_widget_sticker).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, mood, 220, 200))
            setOnClickPendingIntent(R.id.image, tap(context, id, "pose"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        prefs.edit().putInt("pose_$id", (prefs.getInt("pose_$id", 1) + 1) % Poses.size).apply()
    }
}

// Clawd-Tamagotchi

class ClawdPetWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val state = ClawdPet.state(context)
        val prefs = widgetPrefs(context)
        // Right after feeding or playing he shows it for a moment (until the next update).
        val shown = prefs.getString("petMood_$id", null)?.let { n -> ClawdMood.entries.firstOrNull { it.name == n } }
        return RemoteViews(context.packageName, R.layout.clawd_widget_pet).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, shown ?: state.mood))
            setTextViewText(R.id.status, state.status)
            setProgressBar(R.id.food, 100, state.food, false)
            setProgressBar(R.id.joy, 100, state.joy, false)
            setOnClickPendingIntent(R.id.feed, tap(context, id, "feed"))
            setOnClickPendingIntent(R.id.play, tap(context, id, "play"))
            setOnClickPendingIntent(R.id.image, tap(context, id, "pet"))
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // The regular update shows how he feels again.
        val edit = widgetPrefs(context).edit()
        ids.forEach { edit.remove("petMood_$it") }
        edit.apply()
        super.onUpdate(context, manager, ids)
    }

    override fun act(context: Context, id: Int, what: String) {
        val mood = when (what) {
            "feed" -> {
                ClawdPet.feed(context)
                ClawdMood.Dance
            }
            "play" -> {
                ClawdPet.play(context)
                ClawdMood.Flip
            }
            else -> {
                ClawdPet.pet(context)
                ClawdMood.Love
            }
        }
        widgetPrefs(context).edit().putString("petMood_$id", mood.name).apply()
    }
}

// Clawd-Orakel

class ClawdOracleWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val answer = widgetPrefs(context).getString("oracle_$id", null)
        return RemoteViews(context.packageName, R.layout.clawd_widget_oracle).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (answer == null) ClawdMood.Idle else ClawdMood.Wave))
            setTextViewText(R.id.text, answer ?: "Denk an eine Ja/Nein-Frage und tipp mich an")
            setOnClickPendingIntent(R.id.root, tap(context, id, "ask"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        widgetPrefs(context).edit().putString("oracle_$id", ClawdOracle.ask()).apply()
    }
}
