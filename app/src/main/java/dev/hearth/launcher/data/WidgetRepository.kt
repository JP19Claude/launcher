package dev.hearth.launcher.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** A widget on the widget page, with the height the user gave it. */
@Immutable
data class PlacedWidget(val id: Int, val heightDp: Int)

/** An installable widget, for the picker. */
@Immutable
class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val preview: ImageBitmap?,
)

/**
 * Hosts regular Android widgets (clock, weather, calendar, music, …) for the widget page.
 *
 * Adding one takes up to three steps: allocate an id, get the user's permission to bind
 * it (once per app, shown by the system), and run the widget's own setup screen if it has one.
 */
class WidgetRepository(private val context: Context) {

    val host = AppWidgetHost(context, HOST_ID)
    private val manager = AppWidgetManager.getInstance(context)
    private val prefs = context.getSharedPreferences("hearth_widgets", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density

    private val _widgets = MutableStateFlow(read())
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    /** Previews can be huge; keep them at most 480 px wide for the list. */
    private fun toImage(drawable: Drawable): ImageBitmap {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 240
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 240
        val scale = minOf(1f, 480f / w)
        return drawable.toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            .asImageBitmap()
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun choices(): List<WidgetChoice> {
        val pm = context.packageManager
        return runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                WidgetChoice(
                    info = info,
                    label = runCatching { info.loadLabel(pm) }.getOrNull() ?: appLabel,
                    appLabel = appLabel,
                    preview = runCatching {
                        (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.let(::toImage)
                    }.getOrNull(),
                )
            }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    /**
     * Starts adding a widget. Returns an intent the caller must launch to ask the user for
     * permission, or null if the widget could be bound right away (then call [finishBinding]).
     */
    fun begin(info: AppWidgetProviderInfo): Intent? {
        cancelPending()
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }
            .getOrDefault(false)
        if (bound) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
    }

    /** After binding: run the widget's setup screen if it has one, else place it. */
    fun finishBinding(activity: Activity) {
        val id = pendingId
        val info = pendingInfo ?: info(id)
        if (id < 0 || info == null) return
        if (info.configure != null) {
            val started = runCatching {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
            }.isSuccess
            if (started) return
        }
        place(id, info)
    }

    /** Result of the permission dialog. */
    fun onBindResult(granted: Boolean, activity: Activity) {
        if (granted) finishBinding(activity) else cancelPending()
    }

    /** Result of the widget's own setup screen. */
    fun onConfigureResult(ok: Boolean) {
        val id = pendingId
        val info = pendingInfo ?: info(id)
        if (ok && id >= 0 && info != null) place(id, info) else cancelPending()
    }

    private fun place(id: Int, info: AppWidgetProviderInfo) {
        pendingId = -1
        pendingInfo = null
        val minHeightDp = (info.minHeight / density).roundToInt()
        val height = minHeightDp.coerceIn(MIN_HEIGHT, 320).coerceAtLeast(110)
        update(_widgets.value + PlacedWidget(id, height))
    }

    private fun cancelPending() {
        if (pendingId >= 0) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = -1
        pendingInfo = null
    }

    fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        update(_widgets.value.filterNot { it.id == id })
    }

    fun resize(id: Int, deltaDp: Int) {
        update(_widgets.value.map {
            if (it.id == id) it.copy(heightDp = (it.heightDp + deltaDp).coerceIn(MIN_HEIGHT, MAX_HEIGHT)) else it
        })
    }

    fun move(id: Int, direction: Int) {
        val list = _widgets.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + direction
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        update(list)
    }

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    private fun update(list: List<PlacedWidget>) {
        _widgets.value = list
        prefs.edit().putString(KEY, list.joinToString(";") { "${it.id},${it.heightDp}" }).apply()
    }

    private fun read(): List<PlacedWidget> = prefs.getString(KEY, null)
        ?.split(';')
        ?.mapNotNull { entry ->
            val parts = entry.split(',')
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val height = parts.getOrNull(1)?.toIntOrNull() ?: 160
            PlacedWidget(id, height)
        }
        ?: emptyList()

    companion object {
        const val HOST_ID = 0x4845
        const val REQUEST_CONFIGURE = 0x4846
        private const val KEY = "widgets"
        const val MIN_HEIGHT = 80
        const val MAX_HEIGHT = 520
    }
}
