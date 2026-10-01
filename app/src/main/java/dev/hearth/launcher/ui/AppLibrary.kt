package dev.hearth.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.AppLibraryData
import dev.hearth.launcher.data.AppInfo

/** A folder opened from the App Library: title and its apps. */
typealias LibraryFolderContent = Pair<String, List<AppInfo>>

/**
 * App Library like on iOS: a search bar on top, then glass folders sorted by category.
 * Each folder shows three big icons; the fourth corner holds small icons and opens the folder.
 */
@Composable
fun AppLibraryPage(
    library: AppLibraryData,
    actions: AppActions,
    onOpenSearch: () -> Unit,
    onOpenFolder: (LibraryFolderContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
            LibrarySearchBar(onClick = onOpenSearch)
        }
        if (library.suggestions.isNotEmpty()) {
            item(key = "suggestions") {
                Box(Modifier.staggeredEntrance(0)) {
                    LibraryFolder("Vorschläge", library.suggestions, actions, onOpenFolder)
                }
            }
        }
        if (library.recent.isNotEmpty()) {
            item(key = "recent") {
                Box(Modifier.staggeredEntrance(1)) {
                    LibraryFolder("Neu hinzugefügt", library.recent, actions, onOpenFolder)
                }
            }
        }
        itemsIndexed(library.folders, key = { _, folder -> folder.first.name }) { index, (category, apps) ->
            Box(Modifier.staggeredEntrance(index + 2)) {
                LibraryFolder(category.title, apps, actions, onOpenFolder)
            }
        }
    }
}

@Composable
private fun LibrarySearchBar(onClick: () -> Unit) {
    LiquidGlass(
        cornerRadius = 22.dp,
        refraction = 14.dp,
        interactive = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("App-Mediathek", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, style = OnWallpaperText)
        }
    }
}

@Composable
private fun LibraryFolder(
    title: String,
    apps: List<AppInfo>,
    actions: AppActions,
    onOpenFolder: (LibraryFolderContent) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LiquidGlass(
            cornerRadius = 28.dp,
            refraction = 22.dp,
            blur = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                val gap = 10.dp
                val cell = (maxWidth - gap) / 2
                val showMini = apps.size > 4
                val slot: @Composable (Int) -> Unit = { index ->
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (index == 3 && showMini) {
                            MiniGrid(apps.drop(3).take(4), cell) { onOpenFolder(title to apps) }
                        } else {
                            apps.getOrNull(index)?.let { app -> LibraryIcon(app, cell * 0.92f, actions) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        slot(0)
                        slot(1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        slot(2)
                        slot(3)
                    }
                }
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = OnWallpaperText,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryIcon(app: AppInfo, size: Dp, actions: AppActions) {
    val settings = LocalSettings.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "libraryIconPress",
    )
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val selection = LocalSelection.current
    val isSelected = selection.active && app.key in selection.selected
    Box(Modifier.wiggle(selection.active, app.key.hashCode())) {
        AppIconImage(
            app = app,
            size = size,
            modifier = Modifier
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (selection.active && !isSelected) 0.75f else 1f
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { if (selection.active) selection.toggle(app) else actions.launch(app, bounds) },
                    onLongClick = {
                        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selection.active) selection.toggle(app) else actions.menu(app, bounds)
                    },
                ),
        )
        if (selection.active) {
            SelectionBadge(isSelected, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Four small icons in one corner of a folder; tapping opens the whole folder. */
@Composable
private fun MiniGrid(apps: List<AppInfo>, size: Dp, onClick: () -> Unit) {
    val gap = 6.dp
    val mini = (size - gap) / 2
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 2) {
                        Box(Modifier.size(mini), contentAlignment = Alignment.Center) {
                            apps.getOrNull(row * 2 + col)?.let { AppIconImage(it, mini * 0.9f) }
                        }
                    }
                }
            }
        }
    }
}

/** An opened App Library folder: big glass panel with all its apps, over the blurred home screen. */
@Composable
fun LibraryFolderOverlay(folder: LibraryFolderContent?, actions: AppActions, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(folder) }
    if (folder != null) shown = folder

    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(260), initialScale = 0.85f),
        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.9f),
    ) {
        val (title, apps) = shown ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    style = OnWallpaperText,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                LiquidGlass(
                    cornerRadius = 34.dp,
                    refraction = 26.dp,
                    blur = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(10.dp),
                    ) {
                        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
                            Box(Modifier.staggeredEntrance(index / 4), contentAlignment = Alignment.Center) {
                                AppIcon(app, actions, fillCell = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
