package dev.hearth.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.AppUsage
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LibraryCategory
import dev.hearth.launcher.data.SmartGlass
import dev.hearth.launcher.data.withSmartGlass
import dev.hearth.launcher.data.withSmartGlimmer
import dev.hearth.launcher.system.GlimmerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * ZENITH 20.5, "Be Smarter": the launcher dressed after the Smart Launcher. The look itself is a
 * layer of settings laid over what is chosen while drawing (see withSmartLook); what lives here is
 * what the other looks have no counterpart for – the drawer with its category bar – and the
 * Illuminati's controls for the mode.
 */

/** One tab of the Smart drawer: a category (or all apps, or the favorites) with its apps. */
private class SmartTab(val label: String, val title: String, val icon: ImageVector, val apps: List<AppInfo>)

private fun categoryIcon(category: LibraryCategory): ImageVector = when (category) {
    LibraryCategory.Social -> Icons.Rounded.Face
    LibraryCategory.Productivity -> Icons.Rounded.DateRange
    LibraryCategory.Entertainment -> Icons.Rounded.PlayArrow
    LibraryCategory.Creativity -> Icons.Rounded.Create
    LibraryCategory.Information -> Icons.Rounded.Info
    LibraryCategory.Shopping -> Icons.Rounded.ShoppingCart
    LibraryCategory.Travel -> Icons.Rounded.Place
    LibraryCategory.Health -> Icons.Rounded.Favorite
    LibraryCategory.Games -> Icons.Rounded.ThumbUp
    LibraryCategory.Utilities -> Icons.Rounded.Build
    LibraryCategory.Other -> Icons.Rounded.MoreVert
}

private fun categoryLabel(category: LibraryCategory): String = when (category) {
    LibraryCategory.Social -> "Social"
    LibraryCategory.Productivity -> "Arbeit"
    LibraryCategory.Entertainment -> "Medien"
    LibraryCategory.Creativity -> "Kreativ"
    LibraryCategory.Information -> "News"
    LibraryCategory.Shopping -> "Shopping"
    LibraryCategory.Travel -> "Reisen"
    LibraryCategory.Health -> "Fitness"
    LibraryCategory.Games -> "Spiele"
    LibraryCategory.Utilities -> "Tools"
    LibraryCategory.Other -> "Andere"
}

/**
 * Rises into place as [progress] goes from 0 to 1, [row] a little after the one before: all on
 * the layer, so nothing recomposes while it moves.
 */
private fun Modifier.smartRise(progress: () -> Float, row: Int): Modifier = graphicsLayer {
    val delay = (row * 0.06f).coerceAtMost(0.5f)
    val p = ((progress() - delay) / (1f - delay)).coerceIn(0f, 1f)
    alpha = p
    translationY = (1f - p) * 30.dp.toPx()
}

/**
 * The Smart Launcher's app drawer: all apps over the frosted (or liquid) wallpaper, one category
 * at a time – swipe sideways or tap the bar at the foot to change it – with the search on top.
 * Pull it down (or go back) and it lets go.
 */
@Composable
fun SmartDrawer(
    apps: List<AppInfo>,
    actions: AppActions,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    /** How often each app was opened, for the favorites. */
    usage: Map<String, AppUsage> = emptyMap(),
    /** The apps you'll likely open next, first among the favorites. */
    suggestions: List<AppInfo> = emptyList(),
) {
    val settings = LocalSettings.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val columns = settings.columns.coerceIn(4, 5)

    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val favorites = remember(sorted, usage, suggestions) {
        val mostUsed = sorted
            .filter { (usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { usage[it.key]?.count ?: 0 }
        (suggestions + mostUsed).distinctBy { it.key }.take(20)
    }
    val tabs = remember(sorted, favorites) {
        buildList {
            add(SmartTab("Alle", "Alle Apps", Icons.Rounded.Menu, sorted))
            if (favorites.isNotEmpty()) add(SmartTab("Favoriten", "Favoriten", Icons.Rounded.Star, favorites))
            LibraryCategory.entries.forEach { category ->
                val inside = sorted.filter { it.category == category }
                if (inside.isNotEmpty()) add(SmartTab(categoryLabel(category), category.title, categoryIcon(category), inside))
            }
        }
    }
    val pagerState = rememberPagerState { tabs.size }

    // Opening: the rows rise in one after another.
    val appear = remember { Animatable(if (settings.animations) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    // Pulled down from the first row: the drawer follows the finger (harder the farther) and
    // lets go past a point or on a quick flick; otherwise it springs back.
    val pull = remember { Animatable(0f) }
    val dismissPx = with(density) { 120.dp.toPx() }
    val flickMinPx = with(density) { 16.dp.toPx() }
    val nested = remember {
        object : NestedScrollConnection {
            var dragged = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up gives the pull back before the list moves.
                if (available.y < 0f && dragged > 0f) {
                    val take = maxOf(available.y, -dragged)
                    dragged += take
                    scope.launch { pull.snapTo(dragged) }
                    return Offset(0f, take)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    val give = (1f - dragged / (dismissPx * 4f)).coerceIn(0.3f, 1f)
                    dragged += available.y * give
                    scope.launch { pull.snapTo(dragged) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragged > 0f) {
                    val release = dragged > dismissPx || (available.y > 1800f && dragged > flickMinPx)
                    dragged = 0f
                    if (release) {
                        onDismiss()
                    } else {
                        pull.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f))
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(nested),
    ) {
        // The wallpaper, frosted by the glass chosen for the mode; it clears as the drawer is pulled away.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - (pull.value / (dismissPx * 3f)).coerceIn(0f, 0.6f) },
        ) {
            OmegaScreenBackdrop(
                modifier = Modifier.matchParentSize(),
                blur = (14f + 22f * settings.glassBlur).coerceIn(10f, 60f).dp,
                dim = 0.34f,
                omegaDim = 0.28f,
                noGlassDim = 0.5f,
                fluid = 0f,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = pull.value
                    translationY = p * 0.55f
                    alpha = 1f - (p / (dismissPx * 3f)).coerceIn(0f, 0.45f)
                }
                .systemBarsPadding(),
        ) {
            // The search on top, the launcher's settings beside it.
            Row(
                Modifier
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
                    .smartRise({ appear.value }, 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlass(
                    cornerRadius = 24.dp,
                    refraction = 14.dp,
                    interactive = true,
                    fluidEdge = false,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSearch),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Apps und Web durchsuchen", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassCircle(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen", tint = Color.White.copy(alpha = 0.9f))
                }
            }

            // Which tab this is, and how many apps are in it.
            val current = tabs.getOrNull(pagerState.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 2.dp)
                    .smartRise({ appear.value }, 1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current?.title.orEmpty(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("${current?.apps?.size ?: 0} Apps", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val tab = tabs[page]
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        // The tab leaving shrinks and dims a little.
                        .graphicsLayer {
                            val off = kotlin.math.abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - 0.5f * off
                            val s = 1f - 0.05f * off
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    itemsIndexed(tab.apps, key = { _, app -> app.key }) { index, app ->
                        Box(
                            Modifier.smartRise({ appear.value }, 2 + index / columns),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                        }
                    }
                }
            }

            // The category bar at the foot, on glass; the chosen tab lit.
            SmartTabBar(
                tabs = tabs,
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
                    .smartRise({ appear.value }, 3),
            )
        }
    }
}

/** The category bar: one glass capsule with a tab for each category, scrolling to follow the chosen one. */
@Composable
private fun SmartTabBar(tabs: List<SmartTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val itemWidth = 64.dp
    LaunchedEffect(selected) {
        val at = with(density) { itemWidth.toPx() } * (selected - 2)
        scroll.animateScrollTo(at.coerceAtLeast(0f).roundToInt())
    }
    LiquidGlass(
        cornerRadius = 30.dp,
        refraction = 14.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                SmartTabItem(tab, index == selected, itemWidth) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun SmartTabItem(tab: SmartTab, on: Boolean, width: Dp, onClick: () -> Unit) {
    val lit by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "smartTab")
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.2f * lit))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.title,
            tint = Color.White.copy(alpha = 0.62f + 0.38f * lit),
            modifier = Modifier.size(22.dp),
        )
        Text(
            tab.label,
            color = Color.White.copy(alpha = 0.55f + 0.45f * lit),
            fontSize = 10.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val SmartBlue = Color(0xFF4DA3FF)

/**
 * The Clawd Illuminati's "Be Smarter": the card that switches the mode, and under it the glass
 * (Frosted or Liquid, with its sliders), the speed of opening apps and Glimmer bound into the system.
 */
@Composable
internal fun SmartSection(settings: LauncherSettings, onChange: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    // Whether Glimmer's service runs (it is switched on by hand in the accessibility settings).
    var glimmerRunning by remember { mutableStateOf(GlimmerService.isEnabled) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            glimmerRunning = GlimmerService.isEnabled
        }
    }

    SmartCard(settings.beSmarter) { v -> onChange { it.copy(beSmarter = v) } }
    Spacer(Modifier.height(18.dp))

    IlluminationSection("Be Smarter · Glas") {
        IlluminatiChoice("◇", "Glas", SmartGlass.entries, settings.smartGlass, { it.label }) { glass -> onChange { it.withSmartGlass(glass) } }
        PercentSlider("Unschärfe", settings.smartBlur, 0f..2.5f) { v -> onChange { it.copy(smartBlur = v) } }
        PercentSlider("Lichtbrechung", settings.smartRefraction, 0f..2.5f) { v -> onChange { it.copy(smartRefraction = v) } }
        PercentSlider("Glanz", settings.smartSpecular, 0f..2f) { v -> onChange { it.copy(smartSpecular = v) } }
        PercentSlider("Tönung", settings.smartTint, 0f..2.5f) { v -> onChange { it.copy(smartTint = v) } }
        SmartNote(
            "Frosted ist milchig und weich: Es verwischt, was dahinter liegt, und biegt es kaum. Liquid ist klar mit einer Linse: " +
                "Es bricht und spiegelt es und fängt das Licht an seinem Rand. Mit der Wahl springen die Regler auf die Werte des Glases – danach sind sie frei.",
        )
    }

    IlluminationSection("Be Smarter · Tempo") {
        IlluminationSwitch(
            "⚡", "Ohne Verzögerung",
            "Apps öffnen sich im Moment des Tippens: kein Fluid-Spritzer, keine Feder am Icon, die Öffnen-Animation nur ein kurzes Einblenden. " +
                "Aus: Die App klappt wie beim Smart Launcher aus ihrem Icon auf. (Die Dauer richtet sich nach dem Fensteranimationsmaßstab der Entwickleroptionen.)",
            settings.smartInstant,
        ) { v -> onChange { it.copy(smartInstant = v) } }
    }

    IlluminationSection("Be Smarter · Glimmer") {
        IlluminationSwitch(
            "◉", "Glimmer ins System binden",
            "Glimmer, sein Schließ-Fly-in und das Verdecken der Launcher-Animation gehen für alles an: Beim Schließen schrumpft jede App – auch mit One UI, " +
                "ColorOS oder dem Pixel-Launcher als Startbildschirm – zurück in ihr Icon, im Stil des Smart Launchers. Die Öffnen-Animation anderer Launcher " +
                "kann Glimmer nicht ersetzen; die kommt vom Launcher selbst.",
            settings.smartGlimmer,
        ) { v -> onChange { it.withSmartGlimmer(v) } }
        if (settings.smartGlimmer) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !glimmerRunning) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (glimmerRunning) "Glimmer-Dienst: läuft" else "Glimmer-Dienst: aus – hier tippen und in den Bedienungshilfen einschalten",
                    color = if (glimmerRunning) Color(0xFF8BE0A4) else Color(0xFFFFC27A),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun SmartNote(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The big card of the mode: tapped, it switches Be Smarter. */
@Composable
private fun SmartCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B1F36), Color(0xFF163A63), Color(0xFF0B1F36))))
            .border(if (on) 2.dp else 1.dp, SmartBlue.copy(alpha = if (on) 1f else 0.55f), RoundedCornerShape(26.dp))
            .clickable { onToggle(!on) }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BE SMARTER",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFDCEEFF), SmartBlue)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    if (on) "Der Launcher ist jetzt smart." else "Mach aus dem Launcher einen Smart Launcher.",
                    color = Color(0xFFBBD9FF).copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }
            GlassSwitch(
                checked = on,
                onCheckedChange = onToggle,
                onColor = Color(0xFF2F7BE0),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Nach dem Vorbild des Smart Launchers: runde Icons, seine Uhr mit dem Wetter, ein Dock aus Glas und ein App-Drawer mit Kategorie-Leiste, " +
                "den du nach oben wischst – alles in Frosted oder Liquid Glass. Apps öffnen sich ohne Verzögerung. Die eigenen Looks und Effekte von " +
                "ZENITH treten solange zurück. Aus: alles wie vorher.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
