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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
) {
    val settings = LocalSettings.current
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
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
            // One UI's search bar; with Claude in the system it asks Claude as well.
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
    }
}
