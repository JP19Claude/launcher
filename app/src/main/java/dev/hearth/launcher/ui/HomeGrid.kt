package dev.hearth.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.CellPos
import dev.hearth.launcher.data.HomeWidget
import dev.hearth.launcher.data.WidgetRepository
import kotlin.math.ceil
import kotlin.math.roundToInt

/** An app at a grid cell. */
@Immutable
data class HomeCell(val app: AppInfo, val col: Int, val row: Int)

/** One home screen page: widgets at fixed cells, apps flowing around them. */
@Immutable
data class HomePage(val rows: Int, val apps: List<HomeCell>, val widgets: List<HomeWidget>)

/** Keeps a widget inside the page, e.g. after the grid got smaller in the settings. */
private fun HomeWidget.fitInto(columns: Int, rows: Int): HomeWidget {
    val sx = spanX.coerceIn(1, columns)
    val sy = spanY.coerceIn(1, rows)
    return copy(spanX = sx, spanY = sy, col = col.coerceIn(0, columns - sx), row = row.coerceIn(0, rows - sy))
}

/**
 * Lays out the home screen. Widgets keep their cells, apps the user placed keep theirs,
 * and all other apps fill the free cells row by row, page after page.
 * The first page has fewer rows (clock on top).
 */
fun layoutHome(
    apps: List<AppInfo>,
    widgets: List<HomeWidget>,
    pinned: Map<String, CellPos>,
    columns: Int,
    rows: Int,
    firstPageRows: Int,
): List<HomePage> {
    val taken = HashMap<Int, Array<BooleanArray>>()
    fun grid(page: Int) = taken.getOrPut(page) { Array(pageRows(page, rows, firstPageRows)) { BooleanArray(columns) } }

    // 1. Widgets
    val placedWidgets = HashMap<Int, MutableList<HomeWidget>>()
    widgets.filter { it.page in 0 until MAX_PAGES }.forEach { raw ->
        val w = raw.fitInto(columns, pageRows(raw.page, rows, firstPageRows))
        val onPage = placedWidgets.getOrPut(w.page) { ArrayList() }
        if (onPage.none { it.overlaps(w) }) {
            onPage += w
            val g = grid(w.page)
            for (r in w.row until w.row + w.spanY) for (c in w.col until w.col + w.spanX) g[r][c] = true
        }
    }

    // 2. Apps with a place of their own
    val cells = HashMap<Int, MutableList<HomeCell>>()
    val flowing = ArrayList<AppInfo>()
    apps.forEach { app ->
        val pos = pinned[app.key]
        val ok = pos != null && pos.page in 0 until MAX_PAGES && pos.col in 0 until columns &&
            pos.row in 0 until pageRows(pos.page, rows, firstPageRows) && !grid(pos.page)[pos.row][pos.col]
        if (ok && pos != null) {
            grid(pos.page)[pos.row][pos.col] = true
            cells.getOrPut(pos.page) { ArrayList() } += HomeCell(app, pos.col, pos.row)
        } else {
            flowing += app
        }
    }

    // 3. Everything else fills the gaps
    var page = 0
    var next = 0
    while (next < flowing.size && page < MAX_PAGES) {
        val g = grid(page)
        for (r in g.indices) for (c in 0 until columns) {
            if (!g[r][c] && next < flowing.size) {
                g[r][c] = true
                cells.getOrPut(page) { ArrayList() } += HomeCell(flowing[next++], c, r)
            }
        }
        page++
    }

    val lastPage = maxOf(0, taken.keys.maxOrNull() ?: 0, cells.keys.maxOrNull() ?: 0)
    // Pages that ended up empty in the middle are kept, so pinned apps don't jump pages.
    return (0..lastPage).map { p ->
        HomePage(
            rows = pageRows(p, rows, firstPageRows),
            apps = cells[p].orEmpty(),
            widgets = placedWidgets[p].orEmpty(),
        )
    }
}

private const val MAX_PAGES = 40

fun pageRows(page: Int, rows: Int, firstPageRows: Int) = if (page == 0) firstPageRows else rows

/** True if [candidate] lies inside its page and doesn't overlap another widget. */
fun canPlace(candidate: HomeWidget, widgets: List<HomeWidget>, columns: Int, rows: Int, firstPageRows: Int): Boolean {
    if (candidate.page < 0) return false
    val pr = pageRows(candidate.page, rows, firstPageRows)
    if (candidate.col < 0 || candidate.row < 0) return false
    if (candidate.col + candidate.spanX > columns || candidate.row + candidate.spanY > pr) return false
    return widgets.none { it.id != candidate.id && it.overlaps(candidate) }
}

/** First free spot for a widget of this size, starting at [startPage]; a new page if needed. */
fun findFreeSlot(
    widgets: List<HomeWidget>,
    pageCount: Int,
    startPage: Int,
    spanX: Int,
    spanY: Int,
    columns: Int,
    rows: Int,
    firstPageRows: Int,
): HomeWidget {
    for (page in startPage.coerceAtLeast(0)..pageCount) {
        val pr = pageRows(page, rows, firstPageRows)
        val sx = spanX.coerceIn(1, columns)
        val sy = spanY.coerceIn(1, pr)
        for (r in 0..(pr - sy)) for (c in 0..(columns - sx)) {
            val candidate = HomeWidget(-1, page, c, r, sx, sy)
            if (canPlace(candidate, widgets, columns, rows, firstPageRows)) return candidate
        }
    }
    return HomeWidget(-1, pageCount.coerceAtLeast(1), 0, 0, spanX.coerceIn(1, columns), spanY.coerceIn(1, rows))
}

/** How many cells a widget wants, from its minimum size. */
fun spanFor(sizeDp: Int, cellDp: Float, max: Int): Int =
    ceil((sizeDp + 8f) / cellDp.coerceAtLeast(1f)).toInt().coerceIn(1, max)

/**
 * Long press, then optionally drag. Watches the touch before the content does, so it also
 * works on top of real Android widgets: once the long press fires, the widget's own touch
 * is cancelled. A long press without moving reports `moved = false` (show the menu).
 */
fun Modifier.longPressDrag(
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (moved: Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var early = false
            while (!early) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                early = change == null || !change.pressed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (endedEarly != null) return@awaitEachGesture

        onLongPress()
        var moved = false
        var total = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - change.previousPosition
            change.consume()
            if (!change.pressed) break
            total += delta
            if (!moved && total.getDistance() > viewConfiguration.touchSlop) moved = true
            if (moved) onDrag(delta)
        }
        onEnd(moved)
    }
}

/**
 * A home page drawn on its grid. Icons glide to their new cell when the layout changes
 * (a widget added, an app hidden), widgets sit on glass and can be dragged to other cells.
 */
@Composable
fun HomePageGrid(
    page: HomePage,
    pageIndex: Int,
    columns: Int,
    actions: AppActions,
    widgets: WidgetRepository,
    onWidgetMenu: (HomeWidget, Rect) -> Unit,
    onWidgetDrop: (HomeWidget, Int, Int) -> Unit,
    onGeometry: (Int, Rect) -> Unit,
    draggingKey: String?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { onGeometry(pageIndex, it.boundsInRoot()) },
    ) {
        val cellW = maxWidth / columns
        val cellH = maxHeight / page.rows
        page.widgets.forEach { widget ->
            key("widget-${widget.id}") {
                HomeWidgetView(widget, cellW, cellH, widgets, onWidgetMenu, onWidgetDrop)
            }
        }
        page.apps.forEach { cell ->
            key(cell.app.key) {
                val spec = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                val x by animateDpAsState(cellW * cell.col, spec, label = "iconX")
                val y by animateDpAsState(cellH * cell.row, spec, label = "iconY")
                val beingDragged = cell.app.key == draggingKey
                Box(
                    Modifier
                        .offset(x, y)
                        .size(cellW, cellH)
                        // The dragged icon follows the finger in an overlay; its cell stays empty.
                        .graphicsLayer { alpha = if (beingDragged) 0f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(cell.app, actions, fillCell = true, draggable = true)
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetView(
    widget: HomeWidget,
    cellW: Dp,
    cellH: Dp,
    repo: WidgetRepository,
    onMenu: (HomeWidget, Rect) -> Unit,
    onDrop: (HomeWidget, Int, Int) -> Unit,
) {
    val info = remember(widget.id) { repo.info(widget.id) }
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalSettings.current.haptics
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val current by rememberUpdatedState(widget)
    val cellWpx = with(density) { cellW.toPx() }
    val cellHpx = with(density) { cellH.toPx() }

    val spec = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val x by animateDpAsState(cellW * widget.col, spec, label = "widgetX")
    val y by animateDpAsState(cellH * widget.row, spec, label = "widgetY")
    val w by animateDpAsState(cellW * widget.spanX, spec, label = "widgetW")
    val h by animateDpAsState(cellH * widget.spanY, spec, label = "widgetH")
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "widgetLift",
    )

    Box(
        Modifier
            .offset(x, y)
            .size(w, h)
            .zIndex(if (dragging) 2f else 1f)
            .padding(5.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .longPressDrag(
                onLongPress = {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragging = true
                },
                onDrag = { drag += it },
                onEnd = { moved ->
                    if (moved) {
                        onDrop(current, (drag.x / cellWpx).roundToInt(), (drag.y / cellHpx).roundToInt())
                    } else {
                        onMenu(current, bounds)
                    }
                    drag = Offset.Zero
                    dragging = false
                },
            )
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                scaleX = lift
                scaleY = lift
            },
    ) {
        LiquidGlass(
            cornerRadius = 24.dp,
            refraction = 16.dp,
            blur = 18.dp,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp)),
        ) {
            if (repo.isInternal(widget.id)) {
                PhotoWidget(repo, widget.id, Modifier.fillMaxSize().padding(4.dp))
            } else if (info == null) {
                Text(
                    "Widget nicht verfügbar",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                HostedWidget(repo, widget.id, info, Modifier.fillMaxSize().padding(4.dp))
            }
        }
    }
}
