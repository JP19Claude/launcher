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
import dev.hearth.launcher.data.ClawdOutfit
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.ClawdOracle
import dev.hearth.launcher.data.ClawdFocus
import dev.hearth.launcher.data.ClawdGameScore
import dev.hearth.launcher.ui.drawClawdAt
import dev.hearth.launcher.ui.drawClawdWorldScenery
import dev.hearth.launcher.data.ClawdDice
import dev.hearth.launcher.data.ClawdMotivation
import dev.hearth.launcher.data.ClawdWater
import dev.hearth.launcher.data.clawdWeekend
import dev.hearth.launcher.ui.drawDie
import androidx.compose.ui.graphics.asAndroidBitmap
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
private class Look(val color: Color, val hat: ClawdHat, val outfit: ClawdOutfit, val accent: Color) {
    companion object {
        fun of(context: Context): Look {
            val s = SettingsRepository(context).settings.value
            val color = if (s.clawdSkin == ClawdSkin.Rainbow) {
                rainbow((System.currentTimeMillis() / 60_000 % 360) / 360f)
            } else {
                s.clawdSkin.color
            }
            return Look(color, s.clawdHat, s.clawdOutfit, s.accent.color)
        }
    }
}

private fun widgetPrefs(context: Context) = context.getSharedPreferences("clawd_widgets", Context.MODE_PRIVATE)

/** The poses a tap goes through. */
private val Poses = listOf(ClawdMood.Idle, ClawdMood.Wave, ClawdMood.Dance, ClawdMood.Love, ClawdMood.Flip, ClawdMood.Sleep)

/** Clawd alone on a transparent picture. */
private fun clawdPicture(context: Context, mood: ClawdMood, width: Int = 280, height: Int = 230): Bitmap {
    val look = Look.of(context)
    return renderClawd(width, height, mood, look.color, look.hat, look.outfit)
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
            ClawdFashionWidget::class.java,
            ClawdWaterWidget::class.java,
            ClawdDiceWidget::class.java,
            ClawdMotivationWidget::class.java,
            ClawdWeekendWidget::class.java,
            ClawdWorldWidget::class.java,
            ClawdFocusWidget::class.java,
            ClawdGameWidget::class.java,
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
        val picture = renderClawd(440, 440, mood, body, look.hat, look.outfit, clawdScale = 0.66f) {
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
            setTextViewText(R.id.status, "Lv. ${state.level} · ${state.status}")
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

/** Any picture, drawn with Compose's drawing commands (for the die and such). */
private fun renderPicture(width: Int, height: Int, block: DrawScope.() -> Unit): Bitmap {
    val image = androidx.compose.ui.graphics.ImageBitmap(width, height)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image),
        Size(width.toFloat(), height.toFloat()),
    ) { block() }
    return image.asAndroidBitmap()
}

// Clawd-Modenschau

class ClawdFashionWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val look = widgetPrefs(context).getInt("fashion_$id", 1)
        val outfit = ClawdOutfit.entries[Math.floorMod(look, ClawdOutfit.entries.size)]
        val hat = ClawdHat.entries[Math.floorMod(look * 3, ClawdHat.entries.size)]
        val color = Look.of(context).color
        val picture = renderClawd(440, 440, ClawdMood.Wave, color, hat, outfit, clawdScale = 0.62f) {
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF3A2B4F), Color(0xFF1E1A2B))),
                cornerRadius = CornerRadius(size.minDimension * 0.11f),
            )
            drawOval(Color.White.copy(alpha = 0.10f), Offset(size.width * 0.18f, size.height * 0.7f), Size(size.width * 0.64f, size.height * 0.12f))
        }
        val name = listOfNotNull(
            outfit.takeIf { it != ClawdOutfit.None }?.label,
            hat.takeIf { it != ClawdHat.None }?.label,
        ).joinToString(" + ").ifEmpty { "Ganz natürlich" }
        return RemoteViews(context.packageName, R.layout.clawd_widget_fashion).apply {
            setImageViewBitmap(R.id.image, picture)
            setTextViewText(R.id.label, name)
            setOnClickPendingIntent(R.id.root, tap(context, id, "next"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        prefs.edit().putInt("fashion_$id", prefs.getInt("fashion_$id", 1) + 1).apply()
    }
}

// Clawd-Wasser

class ClawdWaterWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val count = ClawdWater.glasses(context)
        val goal = ClawdWater.GOAL
        return RemoteViews(context.packageName, R.layout.clawd_widget_water).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (count >= goal) ClawdMood.Dance else ClawdMood.Wave))
            setTextViewText(R.id.count, "$count / $goal 💧")
            setProgressBar(R.id.bar, goal, count.coerceAtMost(goal), false)
            setTextViewText(R.id.line, ClawdWater.line(count))
            setOnClickPendingIntent(R.id.root, tap(context, id, "drink"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        ClawdWater.add(context)
        // Every glass counts for all water widgets.
        ClawdWidgetProvider.refreshAll(context)
    }
}

// Clawd-Würfel

class ClawdDiceWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val value = widgetPrefs(context).getInt("dice_$id", 6)
        return RemoteViews(context.packageName, R.layout.clawd_widget_dice).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (value == 6) ClawdMood.Dance else ClawdMood.Wave))
            setImageViewBitmap(R.id.die, renderPicture(240, 240) { drawDie(value) })
            setOnClickPendingIntent(R.id.root, tap(context, id, "roll"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        widgetPrefs(context).edit().putInt("dice_$id", ClawdDice.roll()).apply()
    }
}

// Clawd-Motivation

class ClawdMotivationWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val extra = widgetPrefs(context).getInt("motivation_$id", 0)
        return RemoteViews(context.packageName, R.layout.clawd_widget_motivation).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (extra % 2 == 0) ClawdMood.Wave else ClawdMood.Love))
            setTextViewText(R.id.text, ClawdMotivation.of(LocalDateTime.now().dayOfYear, extra))
            setOnClickPendingIntent(R.id.root, tap(context, id, "next"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        prefs.edit().putInt("motivation_$id", prefs.getInt("motivation_$id", 0) + 1).apply()
    }
}

// Clawd-Wochenende

class ClawdWeekendWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val (big, line) = clawdWeekend()
        val look = Look.of(context)
        return RemoteViews(context.packageName, R.layout.clawd_widget_weekend).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, if (big == "🎉") ClawdMood.Dance else ClawdMood.Thinking))
            setTextViewText(R.id.big, big)
            setTextViewText(R.id.line, line)
            setTextColor(R.id.line, (if (look.accent == Color.White) Color(0xFFE8A07F) else look.accent).toArgb())
            setOnClickPendingIntent(R.id.root, tap(context, id, "refresh"))
        }
    }
}

// Clawd-Welt

class ClawdWorldWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val prefs = widgetPrefs(context)
        val now = LocalDateTime.now()
        val night = now.hour >= 21 || now.hour < 6
        val evening = now.hour in 17..20
        val pos = prefs.getFloat("world_$id", 0.5f)
        val left = prefs.getBoolean("worldLeft_$id", false)
        val pose = prefs.getInt("worldPose_$id", 0)
        val mood = if (night) ClawdMood.Sleep else listOf(ClawdMood.Wave, ClawdMood.Thinking, ClawdMood.Dance, ClawdMood.Love)[pose % 4]
        val look = Look.of(context)
        val picture = renderPicture(720, 360) {
            val sky = when {
                night -> listOf(Color(0xFF0B1028), Color(0xFF1E2550))
                evening -> listOf(Color(0xFF4E3D7A), Color(0xFFE07F6F), Color(0xFFF5BE7A))
                else -> listOf(Color(0xFF5CA9F0), Color(0xFFA9D8FA))
            }
            drawRoundRect(Brush.verticalGradient(sky), cornerRadius = CornerRadius(size.height * 0.12f))
            if (night) {
                val s = size.height / 60f
                listOf(0.1f to 0.15f, 0.3f to 0.32f, 0.45f to 0.1f, 0.62f to 0.28f, 0.9f to 0.4f, 0.2f to 0.5f)
                    .forEach { (x, y) -> drawRect(Color.White.copy(alpha = 0.8f), Offset(size.width * x, size.height * y), Size(s * 1.6f, s * 1.6f)) }
            }
            drawClawdWorldScenery(night, evening, drift = (now.hour * 60 + now.minute) / 1440f)
            val ch = size.height * 0.5f
            val cw = ch * 14f / 11f
            val ground = size.height * 0.8f
            drawClawdAt(
                Offset((size.width - cw) * pos, ground - ch * 0.955f),
                cw,
                ch,
                mood,
                look.color,
                look.hat,
                look.outfit,
                mirrored = left,
            )
        }
        return RemoteViews(context.packageName, R.layout.clawd_widget_world).apply {
            setImageViewBitmap(R.id.image, picture)
            setOnClickPendingIntent(R.id.root, tap(context, id, "walk"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        val prefs = widgetPrefs(context)
        val from = prefs.getFloat("world_$id", 0.5f)
        val to = kotlin.random.Random.nextFloat()
        prefs.edit()
            .putFloat("world_$id", to)
            .putBoolean("worldLeft_$id", to < from)
            .putInt("worldPose_$id", prefs.getInt("worldPose_$id", 0) + 1)
            .apply()
    }
}

// Clawd-Fokus

class ClawdFocusWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val prefs = widgetPrefs(context)
        val now = System.currentTimeMillis()
        var end = ClawdFocus.endsAt(context)
        // Time's up (the alarm, or the next update): counted, and Clawd celebrates a while.
        if (end in 1..now) {
            ClawdFocus.finished(context)
            prefs.edit().putLong("focusDone", now).apply()
            end = 0L
        }
        val running = end > 0L
        val celebrating = !running && now - prefs.getLong("focusDone", 0L) < 10 * 60_000L
        val left = if (running) end - now else ClawdFocus.MINUTES * 60_000L
        val done = ClawdFocus.done(context)
        return RemoteViews(context.packageName, R.layout.clawd_widget_focus).apply {
            setImageViewBitmap(
                R.id.image,
                clawdPicture(context, if (running) ClawdMood.Thinking else if (celebrating) ClawdMood.Dance else ClawdMood.Idle),
            )
            setChronometer(R.id.timer, android.os.SystemClock.elapsedRealtime() + left, null, running)
            setChronometerCountDown(R.id.timer, true)
            setTextViewText(
                R.id.status,
                when {
                    running -> "Wir arbeiten · tippen stoppt"
                    celebrating -> "Geschafft! 🎉 Pause verdient"
                    done > 0 -> "Fokus · tippen startet · $done ✓"
                    else -> "Fokus · tippen startet"
                },
            )
            setOnClickPendingIntent(R.id.root, tap(context, id, "toggle"))
        }
    }

    override fun act(context: Context, id: Int, what: String) {
        if (what != "toggle") return
        val alarms = context.getSystemService(android.app.AlarmManager::class.java)
        val finish = tap(context, id, "finish")
        if (ClawdFocus.endsAt(context) > 0L) {
            ClawdFocus.stop(context)
            alarms?.cancel(finish)
        } else {
            ClawdFocus.start(context)
            widgetPrefs(context).edit().remove("focusDone").apply()
            // Wakes the widget when the time is up, so Clawd can celebrate.
            runCatching { alarms?.set(android.app.AlarmManager.RTC_WAKEUP, ClawdFocus.endsAt(context), finish) }
        }
        // One session for all focus widgets.
        refreshAll(context)
    }
}

// Clawd-Spiel

class ClawdGameWidget : ClawdWidgetProvider() {
    override fun views(context: Context, id: Int): RemoteViews {
        val best = ClawdGameScore.best(context)
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent().setClassName(context.packageName, "dev.hearth.launcher.ClawdGameActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteViews(context.packageName, R.layout.clawd_widget_game).apply {
            setImageViewBitmap(R.id.image, clawdPicture(context, ClawdMood.Dance))
            setTextViewText(R.id.best, if (best > 0) "Rekord $best · tippen zum Spielen" else "Tippen zum Spielen")
            setOnClickPendingIntent(R.id.root, open)
        }
    }
}
