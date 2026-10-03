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

/** A widget on a home screen page, covering [spanX] × [spanY] grid cells from ([col], [row]). */
@Immutable
data class HomeWidget(
    val id: Int,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
) {
    fun covers(c: Int, r: Int): Boolean = c in col until col + spanX && r in row until row + spanY

    fun overlaps(other: HomeWidget): Boolean =
        page == other.page &&
            col < other.col + other.spanX && other.col < col + spanX &&
            row < other.row + other.spanY && other.row < row + spanY
}

/** Hearth's own widgets, all on liquid glass; their size on the home screen and the widget page. */
enum class HearthWidget(val label: String, val description: String, val spanX: Int, val spanY: Int, val pageHeight: Int) {
    Photos("Fotos", "Deine Bilder als Diashow auf Glas", 2, 2, 220),
    Clock("Glas-Uhr", "Analoge Uhr mit Sekundenzeiger, wie aus Glas", 2, 2, 200),
    Battery("Akku", "Akkustand als Ring, grün beim Laden", 2, 2, 170),
    Date("Datum", "Wochentag, Tag und nächster Wecker, groß wie bei One UI", 2, 2, 170),
    Toggles("Schnellschalter", "Taschenlampe, Nicht stören, Vibration und Drehen als Glasknöpfe", 4, 1, 110),
    Note("Notiz", "Ein Zettel aus Glas: antippen und schreiben, wird von selbst gespeichert", 2, 2, 180),
    ClawdPicture("Clawd-Bild", "Clawd als Bild: hell, Terrakotta, Nacht, Abend, Akzent oder Glas – antippen und er bewegt sich", 2, 2, 210),
    ClawdWorld("Clawd-Welt", "Clawd spaziert durch eine Pixel-Landschaft, die mit der Tageszeit wechselt", 4, 2, 200),
    ClawdClock("Clawd-Uhr", "Die Uhrzeit mit Clawd und einem Gruß – nachts schläft er", 4, 2, 170),
    ClawdAsk("Frag Clawd", "Ein Tipp und du sprichst mit Clawd, mit seiner letzten Antwort", 4, 1, 110),
    ClawdBattery("Clawd-Akku", "Clawd tanzt beim Laden und wird müde, wenn der Akku leer wird", 2, 2, 190),
    ClawdJoke("Clawd-Witz", "Jeden Tag ein Witz von Clawd, antippen für den nächsten", 4, 2, 160),
    ClawdSticker("Clawd-Sticker", "Nur Clawd, klein und lebendig – passt in jede Lücke", 1, 1, 120),
    ClawdPet("Clawd-Tamagotchi", "Füttern, spielen, streicheln: Clawd wird hungrig und will beschäftigt werden", 4, 2, 170),
    ClawdOracle("Clawd-Orakel", "Denk an eine Ja/Nein-Frage, tipp ihn an – Clawd weiß die Antwort", 2, 2, 190),
    ClawdFocus("Clawd-Fokus", "25 Minuten Fokus-Timer: Clawd arbeitet mit und tanzt am Ende", 2, 2, 190),
    ClawdFashion("Clawd-Modenschau", "Clawd probiert seine Outfits und Hüte an – antippen für den nächsten Look", 2, 2, 210),
    ClawdWater("Clawd-Wasser", "Gläser Wasser heute: antippen für eins mehr, Clawd feuert dich an", 2, 2, 190),
    ClawdDice("Clawd-Würfel", "Antippen und Clawd würfelt für dich", 2, 2, 160),
    ClawdMotivation("Clawd-Motivation", "Jeden Tag ein liebes Wort von Clawd", 4, 2, 160),
    ClawdWeekend("Clawd-Wochenende", "Wie lange noch bis zum Wochenende – am Wochenende tanzt er", 2, 2, 180),
    ClawdBadges("Clawd-Abzeichen", "Wie viele Abzeichen du mit Clawd gesammelt hast und welches als Nächstes kommt", 2, 2, 190),
    ClawdGame("Clawd-Spiel", "Clawd Jump: antippen und losspielen – spring über Bugs, sammle Herzen", 2, 2, 180),
    ClawdDiary("Clawd-Tagebuch", "Wie geht's dir heute? Ein Gesicht pro Tag, die ganze Woche im Blick – Clawd fühlt mit", 4, 2, 170),
    ClawdCoin("Clawd-Münze", "Kopf oder Zahl: antippen und Clawd wirft – ganz selten landet sie auf der Kante", 2, 2, 170),
    ClawdBreath("Clawd-Atmen", "Eine ruhige Minute: Clawd atmet mit dir ein und aus", 2, 2, 190),
    ClawdCountdown("Clawd-Countdown", "Wie lange noch bis Weihnachten, Silvester, Ostern … – antippen für den nächsten großen Tag", 2, 2, 180),
    ClawdRiddle("Clawd-Rätsel", "Clawd stellt dir Rätsel: antippen für die Lösung, nochmal für das nächste", 4, 2, 170),
    ClawdRps("Clawd: Schnick-Schnack-Schnuck", "Schere, Stein, Papier gegen Clawd – mit Punktestand", 4, 2, 180),
    ;

    /** One of Clawd's widgets (shown with him in the picker). */
    val clawd: Boolean get() = name.startsWith("Clawd")
}

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

    /** Pictures of the photo widgets. */
    val photos = PhotoStore(context)
    private val manager = AppWidgetManager.getInstance(context)
    private val prefs = context.getSharedPreferences("hearth_widgets", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density

    private val _widgets = MutableStateFlow(read())
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    private val _homeWidgets = MutableStateFlow(readHome())
    val homeWidgets: StateFlow<List<HomeWidget>> = _homeWidgets.asStateFlow()

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    /** Where the widget being added goes on the home screen; null = widget page. */
    private var pendingHomeSlot: HomeWidget? = null

    /** The widget's preferred size in dp, to work out how many grid cells it needs. */
    fun minSizeDp(info: AppWidgetProviderInfo): Pair<Int, Int> =
        (info.minWidth / density).roundToInt() to (info.minHeight / density).roundToInt()

    /** Previews can be huge; keep them at most 480 px wide for the list. */
    private fun toImage(drawable: Drawable): ImageBitmap {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 240
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 240
        val scale = minOf(1f, 480f / w)
        return drawable.toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            .asImageBitmap()
    }

    /** Hearth's own widgets (photos) have negative ids; Android widget ids are positive. */
    fun isInternal(id: Int) = id < 0

    /**
     * Adds a photo widget, on the home screen at [homeSlot] or on the widget page.
     * Returns its id; the caller then lets the user pick the photos.
     */
    fun addPhotoWidget(homeSlot: HomeWidget?): Int = addHearthWidget(HearthWidget.Photos, homeSlot)

    /** Adds one of Hearth's own widgets (clock, battery, date, photos); returns its id. */
    fun addHearthWidget(kind: HearthWidget, homeSlot: HomeWidget?): Int {
        val id = prefs.getInt(KEY_NEXT_INTERNAL, -1)
        prefs.edit().putInt(KEY_NEXT_INTERNAL, id - 1).putString("kind_$id", kind.name).apply()
        if (homeSlot != null) {
            updateHome(_homeWidgets.value + homeSlot.copy(id = id))
        } else {
            update(_widgets.value + PlacedWidget(id, kind.pageHeight))
        }
        return id
    }

    /** Which of Hearth's widgets an id is (older ones are all photo widgets). */
    fun kindOf(id: Int): HearthWidget =
        prefs.getString("kind_$id", null)?.let { n -> HearthWidget.entries.firstOrNull { it.name == n } } ?: HearthWidget.Photos

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
    fun begin(info: AppWidgetProviderInfo, homeSlot: HomeWidget? = null): Intent? {
        cancelPending()
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        pendingHomeSlot = homeSlot
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
        val slot = pendingHomeSlot
        pendingId = -1
        pendingInfo = null
        pendingHomeSlot = null
        if (slot != null) {
            updateHome(_homeWidgets.value + slot.copy(id = id))
            return
        }
        val minHeightDp = (info.minHeight / density).roundToInt()
        val height = minHeightDp.coerceIn(MIN_HEIGHT, 320).coerceAtLeast(110)
        update(_widgets.value + PlacedWidget(id, height))
    }

    private fun cancelPending() {
        if (pendingId >= 0) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = -1
        pendingInfo = null
        pendingHomeSlot = null
    }

    /** Replaces a home widget's position or size (the caller checks that it fits). */
    fun moveHome(widget: HomeWidget) {
        updateHome(_homeWidgets.value.map { if (it.id == widget.id) widget else it })
    }

    fun removeHome(id: Int) {
        if (isInternal(id)) photos.delete(id) else runCatching { host.deleteAppWidgetId(id) }
        updateHome(_homeWidgets.value.filterNot { it.id == id })
    }

    private fun updateHome(list: List<HomeWidget>) {
        _homeWidgets.value = list
        prefs.edit()
            .putString(KEY_HOME, list.joinToString(";") { "${it.id},${it.page},${it.col},${it.row},${it.spanX},${it.spanY}" })
            .apply()
    }

    private fun readHome(): List<HomeWidget> = prefs.getString(KEY_HOME, null)
        ?.split(';')
        ?.mapNotNull { entry ->
            val n = entry.split(',').mapNotNull { it.toIntOrNull() }
            if (n.size != 6) null else HomeWidget(n[0], n[1], n[2], n[3], n[4].coerceAtLeast(1), n[5].coerceAtLeast(1))
        }
        ?: emptyList()

    fun remove(id: Int) {
        if (isInternal(id)) photos.delete(id) else runCatching { host.deleteAppWidgetId(id) }
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

    private var listening = false

    /** Only once: coming back from an app doesn't make every widget redraw at once. */
    fun startListening() {
        if (listening) return
        listening = runCatching { host.startListening() }.isSuccess
    }

    fun stopListening() {
        if (!listening) return
        listening = false
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
        private const val KEY_HOME = "homeWidgets"
        private const val KEY_NEXT_INTERNAL = "nextInternalId"
        const val MIN_HEIGHT = 80
        const val MAX_HEIGHT = 520
    }
}
