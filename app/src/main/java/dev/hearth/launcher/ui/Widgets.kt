package dev.hearth.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.PlacedWidget
import dev.hearth.launcher.data.WidgetChoice
import dev.hearth.launcher.data.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Widget page left of the home screen, like the Today view on iOS:
 * regular Android widgets, each on a liquid glass card.
 */
@Composable
fun WidgetPage(repo: WidgetRepository, onAddWidget: () -> Unit, modifier: Modifier = Modifier) {
    // modifier carries the page transition; the scrolling column sits inside it.
    val widgets by repo.widgets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    if (widgets.isEmpty() && editing) editing = false

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = "Widgets",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontSize = 32.sp,
                style = OnWallpaperText,
                modifier = Modifier.weight(1f),
            )
            if (widgets.isNotEmpty()) {
                GlassChip(if (editing) "Fertig" else "Bearbeiten") { editing = !editing }
            }
        }

        if (widgets.isEmpty()) {
            LiquidGlass(cornerRadius = 28.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Noch keine Widgets", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Füge Uhr, Wetter, Kalender, Musik und mehr hinzu. Jedes Widget sitzt auf einer Glas-Karte.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        widgets.forEachIndexed { index, widget ->
            key(widget.id) {
                Box(Modifier.staggeredEntrance(index + 1)) {
                WidgetCard(
                    repo = repo,
                    widget = widget,
                    editing = editing,
                    canMoveUp = index > 0,
                    canMoveDown = index < widgets.lastIndex,
                )
                }
            }
        }

        LiquidGlass(
            cornerRadius = 24.dp,
            interactive = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onAddWidget),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Widget hinzufügen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WidgetCard(
    repo: WidgetRepository,
    widget: PlacedWidget,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val info = remember(widget.id) { repo.info(widget.id) }
    Column {
        LiquidGlass(
            cornerRadius = 28.dp,
            refraction = 18.dp,
            blur = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(widget.heightDp.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            if (info == null) {
                Text(
                    "Widget nicht mehr verfügbar",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                HostedWidget(repo, widget.id, info, Modifier.fillMaxSize().padding(6.dp))
            }
            if (editing) {
                // Blocks the widget's own buttons while arranging.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
        if (editing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                EditButton(Icons.Rounded.KeyboardArrowUp, "Nach oben", enabled = canMoveUp) { repo.move(widget.id, -1) }
                EditButton(Icons.Rounded.KeyboardArrowDown, "Nach unten", enabled = canMoveDown) { repo.move(widget.id, 1) }
                EditButton(null, "Kleiner", label = "−") { repo.resize(widget.id, -40) }
                EditButton(Icons.Rounded.Add, "Größer") { repo.resize(widget.id, 40) }
                EditButton(Icons.Rounded.Delete, "Entfernen", tint = Color(0xFFFF8A80)) { repo.remove(widget.id) }
            }
        }
    }
}

/** The real Android widget view, told how much room it has. */
@Composable
internal fun HostedWidget(repo: WidgetRepository, id: Int, info: AppWidgetProviderInfo, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
        AndroidView(
            factory = { context ->
                repo.host.createView(context, id, info).also { hostView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
        LaunchedEffect(hostView, widthDp, heightDp) {
            @Suppress("DEPRECATION")
            hostView?.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
    }
}

@Composable
private fun EditButton(
    icon: ImageVector?,
    description: String,
    enabled: Boolean = true,
    label: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.35f)
    LiquidGlass(
        cornerRadius = 22.dp,
        refraction = 10.dp,
        interactive = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = description, tint = color)
            } else {
                Text(label ?: "", color = color, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun GlassChip(text: String, onClick: () -> Unit) {
    LiquidGlass(
        cornerRadius = 18.dp,
        refraction = 10.dp,
        interactive = true,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** List of every installable widget, with previews, on frosted glass. */
@Composable
fun WidgetPicker(
    repo: WidgetRepository,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
    LaunchedEffect(Unit) {
        choices = withContext(Dispatchers.Default) { repo.choices() }
    }

    Box(Modifier.fillMaxSize()) {
        GlassBackdropFill(blur = 36.dp, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (LocalBackdrop.current != null) 0.35f else 0.88f)),
        )
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Widget hinzufügen",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    style = OnWallpaperText,
                    modifier = Modifier.weight(1f),
                )
                LiquidGlass(
                    cornerRadius = 22.dp,
                    refraction = 12.dp,
                    interactive = true,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Schließen",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            val list = choices
            if (list == null) {
                Text(
                    "Widgets werden geladen …",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(22.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { choice ->
                        WidgetChoiceRow(choice) { onPick(choice.info) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetChoiceRow(choice: WidgetChoice, onClick: () -> Unit) {
    LiquidGlass(
        cornerRadius = 24.dp,
        refraction = 16.dp,
        interactive = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            choice.preview?.let { preview ->
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                choice.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                choice.appLabel,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
