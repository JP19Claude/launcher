package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.AppUsage
import dev.hearth.launcher.data.DrawerSort
import dev.hearth.launcher.data.LauncherSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Galaxy × Claude: One UI's app drawer in place of the App Library. Swipe up on the home
 * screen, and all apps rise in, row after row, in pages of a grid over the blurred wallpaper;
 * the search bar on top asks Claude too. Pull it down (or back) and it lets go.
 *
 * Everything that moves here is drawn on layers (no recomposition while it moves), so the
 * drawer stays smooth even with many apps.
 */
@Composable
fun OneUIDrawer(
    apps: List<AppInfo>,
    actions: AppActions,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
    /** The drawer's ⋮ menu: the launcher's settings, as on One UI. */
    onOpenSettings: () -> Unit = {},
    /** How often each app was opened, for "Meistgenutzt". */
    usage: Map<String, AppUsage> = emptyMap(),
    /** The apps you'll likely open next, shown on top. */
    suggestions: List<AppInfo> = emptyList(),
    /** Changes the drawer's own settings (order, suggestions) from its ⋮ menu. */
    onSettingsChange: ((LauncherSettings) -> LauncherSettings) -> Unit = {},
) {
    val settings = LocalSettings.current
    val animate = settings.animations
    val sorted = remember(apps, settings.drawerSort, usage) {
        when (settings.drawerSort) {
            DrawerSort.Alphabet -> apps.sortedBy { it.label.lowercase() }
            DrawerSort.Newest -> apps.sortedWith(compareByDescending<AppInfo> { it.installTime }.thenBy { it.label.lowercase() })
            DrawerSort.MostUsed -> apps.sortedWith(
                compareByDescending<AppInfo> { usage[it.key]?.count ?: 0 }.thenBy { it.label.lowercase() },
            )
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val shownSuggestions = if (settings.drawerSuggestions) suggestions.take(settings.columns.coerceIn(4, 5)) else emptyList()
    // One UI's drawer: 5 across (4 with big icons), 6 rows a page.
    val columns = settings.columns.coerceIn(4, 5)
    val rows = 6
    val pages = remember(sorted, columns) { sorted.chunked(columns * rows).ifEmpty { listOf(emptyList()) } }
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Opening: the rows rise one after another, like liquid filling the screen.
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 240f)) }

    // A new order: back to the first page, and the grid settles in again.
    val resort = remember { Animatable(1f) }
    var lastSort by remember { mutableStateOf(settings.drawerSort) }
    LaunchedEffect(settings.drawerSort) {
        if (settings.drawerSort != lastSort) {
            lastSort = settings.drawerSort
            pagerState.scrollToPage(0)
            if (animate) {
                resort.snapTo(0f)
                resort.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 340f))
            }
        }
    }

    // Pulling down: the drawer follows the finger (harder the farther), shrinks a little and
    // lets go past a point or on a quick flick; otherwise it springs back.
    val pull = remember { Animatable(0f) }
    val dismissPx = with(density) { 110.dp.toPx() }

    // The ⋮ menu grows out of its button and folds back into it.
    val menu = remember { Animatable(0f) }
    LaunchedEffect(menuOpen) {
        val target = if (menuOpen) 1f else 0f
        when {
            !animate -> menu.snapTo(target)
            menuOpen -> menu.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 460f))
            else -> menu.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1100f))
        }
    }
    val menuShown by remember { derivedStateOf { menuOpen || menu.value > 0.01f } }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragged = pull.value
                        scope.launch { pull.stop() }
                    },
                    onDragEnd = {
                        val speed = tracker.calculateVelocity().y
                        if (dragged > dismissPx || (speed > 1800f && dragged > 16.dp.toPx())) {
                            onDismiss()
                        } else {
                            scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) }
                        }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) } },
                ) { change, dragAmount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Rubber band: the farther it's pulled, the harder it pulls back.
                    val give = (1f - dragged / (dismissPx * 5f)).coerceIn(0.35f, 1f)
                    dragged = (dragged + dragAmount * give).coerceAtLeast(0f)
                    scope.launch { pull.snapTo(dragged) }
                    change.consume()
                }
            },
    ) {
        // The frosted wallpaper; it clears as the drawer is pulled away.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - (pull.value / (dismissPx * 3f)).coerceIn(0f, 0.6f) },
        ) {
            GlassBackdropFill(blur = 36.dp, modifier = Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = pull.value
                    translationY = p * 0.55f
                    val shrink = 1f - (p / (dismissPx * 6f)).coerceIn(0f, 0.08f)
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - (p / (dismissPx * 3f)).coerceIn(0f, 0.45f)
                    // While the menu is open the drawer steps back a touch.
                    val back = 1f - 0.03f * menu.value.coerceIn(0f, 1f)
                    scaleX *= back
                    scaleY *= back
                }
                .systemBarsPadding(),
        ) {
            // One UI's search bar; with Claude in the system it asks Claude as well. ⋮ next to it.
            Row(
                Modifier
                    .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)
                    .rise({ appear.value }, 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The search bar as a liquid glass capsule.
                LiquidGlass(
                    cornerRadius = 24.dp,
                    refraction = 14.dp,
                    interactive = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSearch),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (settings.galaxyClaude) {
                            // Clawd, where Galaxy AI would be.
                            ClawdInBar(24.dp, onTap = onOpenSearch)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (settings.galaxyClaude) "Frag Clawd oder suche" else "Suchen",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                MoreButton(progress = { menu.value }, onClick = { menuOpen = !menuOpen })
            }
            // How many apps, and the order – a tap on it takes the next order.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, bottom = 6.dp)
                    .rise({ appear.value }, 1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${apps.size} Apps",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                GlassCapsule(
                    onClick = {
                        onSettingsChange {
                            it.copy(drawerSort = DrawerSort.entries[(it.drawerSort.ordinal + 1) % DrawerSort.entries.size])
                        }
                    },
                    padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(sortIcon(settings.drawerSort), contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(settings.drawerSort.label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
            // One UI: the apps you'll likely want next, above all the others.
            if (shownSuggestions.isNotEmpty()) {
                Column(Modifier.rise({ appear.value }, 2)) {
                    Text(
                        "Vorgeschlagene Apps",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 2.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        for (c in 0 until columns) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                shownSuggestions.getOrNull(c)?.let { app ->
                                    AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(Color.White.copy(alpha = 0.18f)),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val pageApps = pages[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        // Depth while swiping: the page leaving shrinks and dims a little.
                        .graphicsLayer {
                            val off = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - 0.4f * off
                            val s = 1f - 0.06f * off
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    for (r in 0 until rows) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .rise({ appear.value * resort.value }, r + 3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (c in 0 until columns) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    pageApps.getOrNull(r * columns + c)?.let { app ->
                                        AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                DrawerDots(
                    state = pagerState,
                    count = pages.size,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, bottom = 18.dp),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
        if (menuShown) {
            DrawerMenu(
                progress = { menu.value },
                open = menuOpen,
                sort = settings.drawerSort,
                suggestions = settings.drawerSuggestions,
                onSort = { sort -> onSettingsChange { it.copy(drawerSort = sort) } },
                onSuggestions = { v -> onSettingsChange { it.copy(drawerSuggestions = v) } },
                onSettings = {
                    menuOpen = false
                    onOpenSettings()
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/**
 * Rises into place as [progress] goes from 0 to 1, [step] a little after the one before:
 * all on the layer, so nothing recomposes while it moves.
 */
private fun Modifier.rise(progress: () -> Float, step: Int): Modifier = graphicsLayer {
    val delay = (step * 0.06f).coerceAtMost(0.54f)
    val p = ((progress() - delay) / (1f - delay)).coerceIn(0f, 1.08f)
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * (28.dp.toPx() + step * 7.dp.toPx())
    val s = 0.94f + 0.06f * p.coerceAtMost(1f)
    scaleX = s
    scaleY = s
}

private fun sortIcon(sort: DrawerSort): ImageVector = when (sort) {
    DrawerSort.Alphabet -> Icons.Rounded.Menu
    DrawerSort.Newest -> Icons.Rounded.DateRange
    DrawerSort.MostUsed -> Icons.Rounded.Star
}

/** The ⋮ button: a liquid glass circle that turns while the menu is open. */
@Composable
private fun MoreButton(progress: () -> Float, onClick: () -> Unit) {
    // Liquid glass, giving under the finger; brighter while its menu is open.
    GlassCircle(onClick = onClick) {
        Box(
            Modifier
                .size(46.dp)
                .drawBehind { drawCircle(Color.White.copy(alpha = 0.14f * progress().coerceIn(0f, 1f))) },
        )
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = "Mehr",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.graphicsLayer { rotationZ = 90f * progress() },
        )
    }
}

/** One UI's page dots: the current one stretches to a pill and flows along with the swipe. */
@Composable
private fun DrawerDots(state: PagerState, count: Int, modifier: Modifier = Modifier) {
    val dot = 6.dp
    val wide = 18.dp
    val gap = 7.dp
    Canvas(modifier.size(width = wide + (dot + gap) * (count - 1), height = dot)) {
        val at = state.currentPage + state.currentPageOffsetFraction
        val h = size.height
        var x = 0f
        for (i in 0 until count) {
            val near = (1f - abs(at - i)).coerceIn(0f, 1f)
            val w = dot.toPx() + (wide - dot).toPx() * near
            drawRoundRect(
                Color.White.copy(alpha = 0.38f + 0.57f * near),
                topLeft = Offset(x, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
            x += w + gap.toPx()
        }
    }
}

/**
 * The drawer's ⋮ menu, as on One UI: sort the apps, suggested apps, settings – on glass. It
 * grows out of the ⋮ button with a spring, its rows following one after another, and folds
 * back into it on closing.
 */
@Composable
private fun DrawerMenu(
    progress: () -> Float,
    open: Boolean,
    sort: DrawerSort,
    suggestions: Boolean,
    onSort: (DrawerSort) -> Unit,
    onSuggestions: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // A soft shade over the drawer; tapping it closes the menu.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.28f * progress().coerceIn(0f, 1f))) }
                .then(
                    if (open) {
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    } else {
                        Modifier
                    },
                ),
        )
        LiquidGlass(
            cornerRadius = 26.dp,
            refraction = 16.dp,
            tint = Color.Black.copy(alpha = 0.34f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 62.dp, end = 14.dp)
                .width(262.dp)
                .graphicsLayer {
                    val p = progress()
                    // Out of the ⋮ button in the top right corner.
                    transformOrigin = TransformOrigin(0.93f, 0f)
                    alpha = (p * 1.5f).coerceIn(0f, 1f)
                    scaleX = 0.55f + 0.45f * p
                    scaleY = 0.3f + 0.7f * p
                    translationY = (1f - p) * -14.dp.toPx()
                }
                .clip(RoundedCornerShape(26.dp))
                // Taps on the menu stay on the menu.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    "Apps sortieren",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .menuRow(progress, 0),
                )
                DrawerSort.entries.forEachIndexed { i, option ->
                    MenuOption(
                        icon = sortIcon(option),
                        label = option.label,
                        checked = option == sort,
                        modifier = Modifier.menuRow(progress, i + 1),
                    ) { onSort(option) }
                }
                Box(
                    Modifier
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                MenuSwitch(
                    icon = Icons.Rounded.ThumbUp,
                    label = "Vorgeschlagene Apps",
                    on = suggestions,
                    modifier = Modifier.menuRow(progress, 5),
                ) { onSuggestions(!suggestions) }
                MenuOption(
                    icon = Icons.Rounded.Settings,
                    label = "Einstellungen",
                    checked = false,
                    modifier = Modifier.menuRow(progress, 6),
                    onClick = onSettings,
                )
            }
        }
    }
}

/** A menu row following the menu open, [step] after the one before (and leaving first on closing). */
private fun Modifier.menuRow(progress: () -> Float, step: Int): Modifier = graphicsLayer {
    val delay = step * 0.06f
    val q = ((progress() - delay) / (1f - delay)).coerceIn(0f, 1f)
    alpha = q
    translationY = (1f - q) * -10.dp.toPx()
}

@Composable
private fun MenuOption(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalSettings.current.accent.color
    val mark by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 620f),
        label = "menuCheck",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(lerp(Color.White.copy(alpha = 0.10f), accent.copy(alpha = 0.55f), mark.coerceIn(0f, 1f))) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = mark
                    scaleY = mark
                    alpha = mark.coerceIn(0f, 1f)
                },
        )
    }
}

/** A menu row with a little switch that slides over with a spring. */
@Composable
private fun MenuSwitch(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalSettings.current.accent.color
    val knob by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "menuSwitch",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(Color.White.copy(alpha = 0.22f), accent, knob.coerceIn(0f, 1f))) },
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .graphicsLayer { translationX = knob * 16.dp.toPx() }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * One UI's edit mode: a long press on the home screen zooms the pages out and shows the bar at
 * the bottom – wallpaper & style, widgets, settings, choosing apps. Tap anywhere else to leave.
 */
@Composable
fun OneUIEditBar(
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    onSelectApps: () -> Unit,
    onDismiss: () -> Unit,
    onMenu: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 28.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditAction(Icons.Rounded.Edit, "Hintergrund\nund Stil", onWallpaper)
            EditAction(Icons.Rounded.Add, "Widgets", onWidgets)
            EditAction(Icons.Rounded.CheckCircle, "Apps\nauswählen", onSelectApps)
            EditAction(Icons.Rounded.Menu, "Hearth-\nMenü", onMenu)
            EditAction(Icons.Rounded.Settings, "Einstellungen", onSettings)
        }
    }
}

@Composable
private fun EditAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Round liquid glass buttons, as everywhere in Hearth.
        LiquidGlass(
            cornerRadius = 27.dp,
            refraction = 12.dp,
            interactive = true,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).align(Alignment.Center))
        }
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}
