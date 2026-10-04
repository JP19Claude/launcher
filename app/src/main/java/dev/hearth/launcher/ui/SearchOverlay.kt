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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import dev.hearth.launcher.ui.theme.HearthTheme

/**
 * The finder: swipe down on the home screen (or tap the search pill). Frosted wallpaper with
 * Claude's colors drifting through it, a liquid glass search bar whose rim flows while you
 * type, and everything found on glass – Clawd's prompts, suggestions, apps, settings, the web.
 */
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
    /** Hearth UI 15: a secret code (*#0*#, *#1234# …) opens its hidden menu. */
    onSecret: (SecretMenu) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = query.trim()
    // Easter egg: a rainbow, when you look for one.
    val eggContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(trimmed) {
        if (trimmed.equals("regenbogen", ignoreCase = true) || trimmed.equals("rainbow", ignoreCase = true)) {
            dev.hearth.launcher.data.EasterEggs.find(eggContext, "rainbow")
        }
        SecretMenu.forCode(trimmed)?.let { onSecret(it) }
        // Looking for Clawd himself.
        if (trimmed.equals("clawd", ignoreCase = true)) {
            dev.hearth.launcher.data.ClaudeAssistant.react(dev.hearth.launcher.data.ClawdMood.Dance)
            dev.hearth.launcher.data.EasterEggs.find(eggContext, "walk")
        }
    }
    val results = remember(trimmed, apps) {
        if (trimmed.isEmpty()) {
            apps.sortedBy { it.label.lowercase() }
        } else {
            apps.filter { it.label.contains(trimmed, ignoreCase = true) }
                .sortedByDescending { it.label.startsWith(trimmed, ignoreCase = true) }
        }
    }
    val columns = LocalSettings.current.columns.coerceIn(4, 5)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val hasGlass = LocalBackdrop.current != null
    // Comes down from the top like a sheet of liquid, and the results follow a moment later.
    val animate = LocalSettings.current.animations
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }

    // Always dark glass, whatever the system theme: light text on the frosted wallpaper.
    HearthTheme(dark = true) {
    Box(Modifier.fillMaxSize()) {
        GlassBackdropFill(blur = 40.dp, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xFF06060C).copy(alpha = if (hasGlass) 0.42f else 0.94f)),
        )
        // One UI 10 Fluid: Claude's colors drift slowly behind everything (and hold still
        // while the results scroll).
        FluidBackdrop(AiFluidColors.take(3), modifier = Modifier.matchParentSize(), strength = 0.5f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
                    .graphicsLayer {
                        val p = appear.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * -24.dp.toPx()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (claudeFirst) "Frag Clawd oder suche" else "Apps und Web durchsuchen",
                    trailing = if (claudeFirst) {
                        { ClawdInBar(26.dp) { onAskClaude(trimmed) } }
                    } else {
                        null
                    },
                    onSubmit = {
                        val first = results.firstOrNull()
                        when {
                            trimmed.isEmpty() -> Unit
                            first != null -> onLaunch(first, null)
                            claudeFirst -> onAskClaude(trimmed)
                            else -> onWebSearch(trimmed)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                GlassCircle(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                GlassCircle(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Schließen", tint = Color.White)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 10.dp, bottom = 36.dp)
                    .graphicsLayer {
                        val p = appear.value
                        alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        translationY = (1f - p) * -40.dp.toPx()
                    },
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 28.dp),
            ) {
                // Galaxy × Claude: things to ask Claude, before anything is typed.
                if (trimmed.isEmpty() && claudeFirst) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "claude-prompts") {
                        Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                            SectionLabel("Clawd")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ClaudePrompts.forEach { (label, prompt) ->
                                    GlassCapsule(onClick = { onAskClaude(prompt) }, tint = Color(0xFFD97757).copy(alpha = 0.22f)) {
                                        Clawd(Modifier.size(16.dp), animate = false)
                                        Spacer(Modifier.width(7.dp))
                                        Text(label, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                if (trimmed.isEmpty() && suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "suggestions") {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            SectionLabel("Vorschläge")
                            // The apps you'll likely want, on their own piece of glass.
                            LiquidGlass(
                                cornerRadius = 26.dp,
                                refraction = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp)),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    for (c in 0 until columns) {
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            suggestions.getOrNull(c)?.let { app ->
                                                AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                                            }
                                        }
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
                if (results.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "apps-label") {
                        SectionLabel(if (trimmed.isEmpty()) "Alle Apps" else "Apps", Modifier.padding(top = 6.dp))
                    }
                }
                items(results, key = { it.key }) { app ->
                    // Results glide into place while typing.
                    Box(Modifier.animateItem(), contentAlignment = Alignment.Center) {
                        AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                    }
                }
                // Nothing found: Clawd scratches his head and offers to help.
                if (trimmed.isNotEmpty() && results.isEmpty() && calculate(trimmed) == null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "clawd-empty") {
                        LiquidGlass(
                            cornerRadius = 22.dp,
                            interactive = true,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onAskClaude(trimmed) },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Clawd(Modifier.size(width = 56.dp, height = 46.dp), mood = dev.hearth.launcher.data.ClawdMood.Thinking)
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text("Hier ist keine App mit dem Namen.", color = Color.White, fontSize = 15.sp)
                                    Text("Tipp mich an, dann kümmere ich mich drum.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                if (trimmed.isNotEmpty()) {
                    if (!claudeFirst) item(span = { GridItemSpan(maxLineSpan) }, key = "claude-last") {
                        ClaudeRow(query = trimmed, onClick = { onAskClaude(trimmed) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "web") {
                        WebSearchRow(query = trimmed, onClick = { onWebSearch(trimmed) })
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Apps und Web durchsuchen",
    /** Clawd at the bar's end (Galaxy × Claude), where Galaxy AI would be. */
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = SolidColor(Color(0xFFFFB494)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            // A liquid glass capsule; while you type, Claude's colors flow round its rim.
            LiquidGlass(
                cornerRadius = 26.dp,
                refraction = 14.dp,
                tint = Color.White.copy(alpha = 0.06f),
                fluidEdge = false,
                modifier = Modifier.aiFluidEdge(
                    26.dp,
                    strength = if (focused) 0.75f else 0.35f,
                    enabled = LocalSettings.current.fluidDesign,
                    flowing = focused,
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
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
        tint = Color.White.copy(alpha = 0.06f),
        modifier = Modifier
            .padding(top = 12.dp)
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
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clawd(Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Clawd fragen: „$query“",
                color = colors.onSurface,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
