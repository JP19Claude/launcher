package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.runtime.mutableStateOf

/**
 * Galaxy × Claude: One UI's app drawer in place of the App Library. Swipe up on the home
 * screen, and all apps sit A–Z in pages of a grid with dots underneath, over the blurred
 * wallpaper; the search bar on top asks Claude too. Swipe down (or back) closes it.
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
    // Pulling down: the drawer follows the finger and lets go past a point.
    var pull by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = pull * 0.6f
                alpha = 1f - (pull / 900f).coerceIn(0f, 0.5f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (pull > 90.dp.toPx()) onDismiss()
                        pull = 0f
                    },
                    onDragCancel = { pull = 0f },
                ) { change, dragAmount ->
                    pull = (pull + dragAmount).coerceAtLeast(0f)
                    change.consume()
                }
            },
    ) {
        GlassBackdropFill(blur = 36.dp, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.38f)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            // One UI's search bar; with Claude in the system it asks Claude as well. ⋮ next to it.
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
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
                    ClaudeSpark(Color(0xFFD97757), Modifier.size(18.dp))
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (settings.galaxyClaude) "Frag Claude oder suche" else "Suchen",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                )
            }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr", tint = Color.White.copy(alpha = 0.85f))
            }
            }
            // One UI: the apps you'll likely want next, above all the others.
            if (shownSuggestions.isNotEmpty()) {
                Text(
                    "Vorgeschlagene Apps",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 22.dp, bottom = 2.dp),
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val pageApps = pages[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                ) {
                    for (r in 0 until rows) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    repeat(pages.size) { i ->
                        val current = i == pagerState.currentPage
                        Box(
                            Modifier
                                .size(if (current) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (current) 0.95f else 0.4f)),
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
        if (menuOpen) {
            DrawerMenu(
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

/** The drawer's ⋮ menu, as on One UI: sort the apps, suggested apps, settings – on glass. */
@Composable
private fun DrawerMenu(
    sort: DrawerSort,
    suggestions: Boolean,
    onSort: (DrawerSort) -> Unit,
    onSuggestions: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        LiquidGlass(
            cornerRadius = 24.dp,
            refraction = 16.dp,
            tint = Color.Black.copy(alpha = 0.32f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 64.dp, end = 12.dp)
                .width(240.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                // Taps on the menu stay on the menu.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Sortieren",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
                DrawerSort.entries.forEach { option ->
                    MenuOption(option.label, checked = option == sort) { onSort(option) }
                }
                Box(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                MenuOption("Vorgeschlagene Apps", checked = suggestions) { onSuggestions(!suggestions) }
                MenuOption("Einstellungen", checked = false, onClick = onSettings)
            }
        }
    }
}

@Composable
private fun MenuOption(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = LocalSettings.current.accent.color, modifier = Modifier.size(18.dp))
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
