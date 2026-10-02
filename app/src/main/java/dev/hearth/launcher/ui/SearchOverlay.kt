package dev.hearth.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo

/** Full-screen search, opened by swiping or tapping the search pill. */
@Composable
fun SearchOverlay(
    apps: List<AppInfo>,
    actions: AppActions,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onWebSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAskClaude: (String) -> Unit,
    onDismiss: () -> Unit,
    suggestions: List<AppInfo> = emptyList(),
    /** Galaxy × Claude: Claude comes first, and "Go" without a matching app asks Claude. */
    claudeFirst: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = query.trim()
    val results = remember(trimmed, apps) {
        if (trimmed.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.contains(trimmed, ignoreCase = true) }
                .sortedByDescending { it.label.startsWith(trimmed, ignoreCase = true) }
        }
    }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val hasGlass = LocalBackdrop.current != null

    Box(Modifier.fillMaxSize()) {
    // Frosted glass sheet over the wallpaper; nearly opaque if the wallpaper can't be read.
    GlassBackdropFill(blur = 40.dp, modifier = Modifier.matchParentSize())
    Box(
        Modifier
            .matchParentSize()
            .background(colors.background.copy(alpha = if (hasGlass) 0.55f else 0.94f)),
    )
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = if (claudeFirst) "Frag Claude oder suche Apps" else "Apps und Web durchsuchen",
                onSubmit = {
                    val first = results.firstOrNull()
                    when {
                        first != null -> onLaunch(first, null)
                        trimmed.isEmpty() -> Unit
                        claudeFirst -> onAskClaude(trimmed)
                        else -> onWebSearch(trimmed)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.width(8.dp))
            LiquidGlass(
                cornerRadius = 23.dp,
                refraction = 12.dp,
                interactive = true,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Einstellungen",
                    tint = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = colors.primary, fontSize = 16.sp)
            }
        }

        Text(
            text = when {
                trimmed.isEmpty() -> "Alle Apps"
                calculate(trimmed) != null -> "Rechner"
                results.isEmpty() -> "Keine App gefunden"
                else -> "Apps"
            },
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            color = colors.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // All results sit on one big sheet of liquid glass.
        LiquidGlass(
            cornerRadius = 30.dp,
            refraction = 22.dp,
            blur = 20.dp,
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp)),
        ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(LocalSettings.current.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 24.dp),
        ) {
            // Galaxy × Claude: things to ask Claude, before anything is typed.
            if (trimmed.isEmpty() && claudeFirst) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "claude-prompts") {
                    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Text(
                            "Claude",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ClaudePrompts.forEach { (label, prompt) ->
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFD97757).copy(alpha = 0.18f))
                                        .clickable { onAskClaude(prompt) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ClaudeSpark(Color(0xFFD97757), Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, color = colors.onSurface, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (trimmed.isEmpty() && suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "suggestions") {
                    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Text(
                            "Vorschläge",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            suggestions.take(LocalSettings.current.columns).forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AppIcon(app, actions, onWallpaper = false, fillCell = true, onLaunch = onLaunch)
                                }
                            }
                        }
                    }
                }
            }
            if (trimmed.isNotEmpty()) {
                calculate(trimmed)?.let { result ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "calculator") {
                        CalculatorRow(trimmed, result, onDone = onDismiss)
                    }
                }
                // Galaxy × Claude: asking Claude comes first.
                if (claudeFirst) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "claude-first") {
                        ClaudeRow(query = trimmed, onClick = { onAskClaude(trimmed) })
                    }
                }
                matchingSettings(trimmed).forEach { setting ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "setting-${setting.label}") {
                        SettingRow(setting, onDone = onDismiss)
                    }
                }
            }
            items(results, key = { it.key }) { app ->
                // Results glide into place while typing.
                Box(Modifier.animateItem(), contentAlignment = Alignment.Center) {
                    AppIcon(app, actions, onWallpaper = false, fillCell = true, onLaunch = onLaunch)
                }
            }
            if (trimmed.isNotEmpty()) {
                if (!claudeFirst) item(span = { GridItemSpan(maxLineSpan) }) {
                    ClaudeRow(query = trimmed, onClick = { onAskClaude(trimmed) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WebSearchRow(query = trimmed, onClick = { onWebSearch(trimmed) })
                }
            }
        }
        }
    }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Apps und Web durchsuchen",
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 17.sp),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier,
        decorationBox = { innerTextField ->
            LiquidGlass(
                cornerRadius = 23.dp,
                refraction = 14.dp,
                tint = colors.surface.copy(alpha = 0.25f),
            ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.onSurfaceVariant, fontSize = 17.sp)
                    }
                    innerTextField()
                }
            }
            }
        },
    )
}

@Composable
private fun WebSearchRow(query: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    LiquidGlass(
        cornerRadius = 20.dp,
        interactive = true,
        tint = colors.surface.copy(alpha = 0.25f),
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
    Row(
        Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Im Web nach „$query“ suchen",
            color = colors.onSurface,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    }
}

/** Hand the question over to Claude (app, or claude.ai if the app isn't installed). */
@Composable
private fun ClaudeRow(query: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    LiquidGlass(
        cornerRadius = 20.dp,
        interactive = true,
        tint = Color(0xFFD97757).copy(alpha = 0.22f),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeSpark(Color(0xFFD97757), Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Claude fragen: „$query“",
                color = colors.onSurface,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
