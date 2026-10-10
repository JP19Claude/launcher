package dev.hearth.launcher.duft

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.hearth.launcher.PerfumeActivity
import dev.hearth.launcher.R
import dev.hearth.launcher.data.ClawdHat
import dev.hearth.launcher.data.ClawdMood
import dev.hearth.launcher.data.Perfume
import dev.hearth.launcher.data.PerfumeLook
import dev.hearth.launcher.data.PerfumeShelf
import dev.hearth.launcher.ui.ZenithZAspect
import dev.hearth.launcher.ui.drawZenithZ
import dev.hearth.launcher.ui.perfumeTone
import dev.hearth.launcher.ui.renderClawd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Duftschrank's widget for any launcher: today's perfume with its picture, its whole name and
 * why – Clawd and/or the ZENITH Z beside it, as chosen in the app. Tapped, the shelf opens. The
 * app tells it to draw itself again whenever the shelf or today's choice changes.
 */
class PerfumeAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, choose = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PerfumeShelf.REFRESH_ACTION -> refresh(context, choose = false)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> refresh(context, choose = true)
        }
    }

    /** Draws the widget; with [choose], today's choice is worked out first (for a few seconds at most). */
    private fun refresh(context: Context, choose: Boolean) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PerfumeShelf.init(app)
                if (choose) withTimeoutOrNull(9_000) { PerfumeShelf.refreshToday() }
                render(app)
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PerfumeAppWidget::class.java))
        if (ids.isEmpty()) return
        val shelf = PerfumeShelf.shelf.value
        val pick = PerfumeShelf.pick.value
        val perfume = pick?.let { p -> shelf.firstOrNull { it.id == p.perfumeId } }
        val look = PerfumeShelf.look.value
        val views = RemoteViews(context.packageName, R.layout.duft_widget)
        views.setOnClickPendingIntent(
            R.id.duft_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, PerfumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        when {
            shelf.isEmpty() -> {
                views.setTextViewText(R.id.duft_caption, "DUFTSCHRANK")
                views.setTextViewText(R.id.duft_name, "Zeig mir deine Parfüms")
                views.setTextViewText(R.id.duft_reason, "Tippe hier: schreib sie auf oder schick ein Foto.")
                views.setImageViewBitmap(R.id.duft_picture, bottle(Perfume(id = "", name = ""), 260))
            }
            perfume == null -> {
                views.setTextViewText(R.id.duft_caption, "DUFTSCHRANK")
                views.setTextViewText(R.id.duft_name, "Noch kein Vorschlag")
                views.setTextViewText(R.id.duft_reason, "Tippe hier, dann sucht dir der Duftschrank eins aus.")
                views.setImageViewBitmap(R.id.duft_picture, bottle(Perfume(id = "", name = ""), 260))
            }
            else -> {
                views.setTextViewText(R.id.duft_caption, "HEUTE")
                views.setTextViewText(R.id.duft_name, perfume.title)
                views.setTextViewText(R.id.duft_reason, pick?.reason.orEmpty())
                views.setImageViewBitmap(R.id.duft_picture, picture(perfume))
            }
        }
        val figure = figures(look, perfume != null)
        if (figure == null) {
            views.setViewVisibility(R.id.duft_figure, View.GONE)
        } else {
            views.setViewVisibility(R.id.duft_figure, View.VISIBLE)
            views.setImageViewBitmap(R.id.duft_figure, figure)
        }
        manager.updateAppWidget(ids, views)
    }

    /** The perfume's picture, small enough for a widget – or a drawn bottle if it has none. */
    private fun picture(perfume: Perfume): Bitmap {
        if (perfume.photo) {
            PerfumeShelf.loadPhoto(perfume.id)?.let { photo ->
                val scale = 260f / maxOf(photo.width, photo.height)
                return if (scale >= 1f) photo else Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt().coerceAtLeast(1), (photo.height * scale).toInt().coerceAtLeast(1), true)
            }
        }
        return bottle(perfume, 260)
    }

    /** A flacon, drawn the way the app draws it. */
    private fun bottle(perfume: Perfume, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = size.toFloat()
        val tone = perfumeTone(perfume).toArgb()
        paint.color = 0xFF0B0F12.toInt()
        canvas.drawRect(0f, 0f, w, w, paint)
        val bodyW = w * 0.5f
        val bodyH = w * 0.46f
        val left = (w - bodyW) / 2f
        val top = w * 0.4f
        paint.color = 0xFFD8DDE0.toInt()
        canvas.drawRoundRect(RectF(w * 0.37f, top - w * 0.2f, w * 0.63f, top - w * 0.06f), w * 0.03f, w * 0.03f, paint)
        paint.color = tone
        canvas.drawRect(w * 0.43f, top - w * 0.07f, w * 0.57f, top + w * 0.01f, paint)
        canvas.drawRoundRect(RectF(left, top, left + bodyW, top + bodyH), w * 0.08f, w * 0.08f, paint)
        paint.color = 0x4DFFFFFF
        canvas.drawRoundRect(RectF(left + bodyW * 0.1f, top + bodyH * 0.07f, left + bodyW * 0.2f, top + bodyH * 0.85f), w * 0.03f, w * 0.03f, paint)
        paint.color = 0xE0FFFFFF.toInt()
        canvas.drawRoundRect(RectF(left + bodyW * 0.22f, top + bodyH * 0.36f, left + bodyW * 0.82f, top + bodyH * 0.66f), w * 0.02f, w * 0.02f, paint)
        return bitmap
    }

    /** Clawd and/or the ZENITH Z, side by side in one picture (null when both are switched off). */
    private fun figures(look: PerfumeLook, happy: Boolean): Bitmap? {
        if (!look.clawd && !look.zenith) return null
        val one = 160
        val count = (if (look.clawd) 1 else 0) + (if (look.zenith) 1 else 0)
        val result = Bitmap.createBitmap(one * count, one, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var x = 0f
        if (look.clawd) {
            val clawd = renderClawd(one, one, if (happy) ClawdMood.Love else ClawdMood.Idle, Color(0xFFD97757), ClawdHat.None)
            canvas.drawBitmap(clawd, x, 0f, null)
            x += one
        }
        if (look.zenith) canvas.drawBitmap(zenithZ(one), x, 0f, null)
        return result
    }

    /** The ZENITH Z, drawn with the same code as in the launcher, into a picture. */
    private fun zenithZ(size: Int): Bitmap {
        val image = ImageBitmap(size, size)
        val full = Size(size.toFloat(), size.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), full) {
            val w = full.width * 0.9f
            drawZenithZ((full.width - w) / 2f, (full.height - w * ZenithZAspect) / 2f, w)
        }
        return image.asAndroidBitmap()
    }
}
