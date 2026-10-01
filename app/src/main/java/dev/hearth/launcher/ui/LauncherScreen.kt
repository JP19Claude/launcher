package dev.hearth.launcher.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.SwipeDownAction
import dev.hearth.launcher.system.ControlCenterService
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    val needsWallpaperAccess by vm.needsWallpaperAccess.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var openFolder by remember { mutableStateOf<LibraryFolderContent?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var controlOpen by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<GlassMenuRequest?>(null) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val endSelection = {
        selecting = false
        selected = emptySet()
    }

    val context = LocalContext.current
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshWallpaper() }
    val requestWallpaperAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: "All files access" is the only way a launcher may read the wallpaper.
            val appSpecific = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(appSpecific) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val columns = settings.columns.coerceIn(3, 6)
    val rows = settings.rows.coerceIn(3, 9)
    val clockRows = if (settings.clockStyle == ClockStyle.Hidden) 0 else 2
    val claudeRows = if (settings.showClaudeCard) 1 else 0
    val firstPageRows = (rows - clockRows - claudeRows).coerceAtLeast(1)
    val homeApps = remember(apps, dock) {
        val dockKeys = dock.map { it.key }.toSet()
        apps.filterNot { it.key in dockKeys }
    }
    val pages = remember(homeApps, columns, rows, firstPageRows) {
        paginate(homeApps, columns, rows, firstPageRows)
    }
    val libraryPages = if (settings.showAppLibrary) 1 else 0
    val widgetPages = if (settings.showWidgetPage) 1 else 0
    // Page order: [widgets] home pages… [App Library]; the launcher opens on the first home page.
    val pagerState = rememberPagerState(initialPage = widgetPages) { widgetPages + pages.size + libraryPages }
    val onLibraryPage = libraryPages > 0 && pagerState.currentPage >= widgetPages + pages.size

    // Adding a widget: permission dialog (once per app) and the widget's own setup screen.
    val activity = context as? Activity
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity?.let { vm.widgets.onBindResult(result.resultCode == Activity.RESULT_OK, it) }
    }
    val addWidget: (AppWidgetProviderInfo) -> Unit = { info ->
        widgetPickerOpen = false
        val permission = vm.widgets.begin(info)
        if (permission == null) {
            activity?.let { vm.widgets.finishBinding(it) }
        } else {
            runCatching { bindWidget.launch(permission) }
        }
    }

    // Apps open with a zoom out of their icon, which the system can start right away.
    val view = LocalView.current
    val launchApp: (AppInfo, Rect?) -> Unit = remember(vm, view) {
        { app, bounds ->
            val area = bounds?.takeIf { it.width > 0f && it.height > 0f }
            val source = area?.let {
                android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
            }
            val options = source?.let {
                runCatching {
                    ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                }.getOrNull()
            }
            vm.launch(app, source, options)
        }
    }
    val actions = remember(vm, launchApp) {
        AppActions(
            launch = launchApp,
            menu = { app, bounds ->
                val items = appMenuItems(vm, app) {
                    selecting = true
                    selected = setOf(app.key)
                }
                menu = GlassMenuRequest(bounds, items, app)
            },
        )
    }
    // Inside an opened App Library folder: launching closes the folder.
    val folderActions = remember(actions) {
        AppActions(
            launch = { app, bounds ->
                openFolder = null
                actions.launch(app, bounds)
            },
            menu = actions.menu,
        )
    }
    val homeMenu: (Offset) -> Unit = { position ->
        menu = GlassMenuRequest(
            anchor = Rect(position, Size(1f, 1f)),
            title = "Hearth",
            items = listOf(
                GlassMenuItem("Launcher-Einstellungen", Icons.Rounded.Settings) { settingsOpen = true },
                GlassMenuItem("Kontrollzentrum", Icons.Rounded.Home) { controlOpen = true },
                GlassMenuItem("Widget hinzufügen", Icons.Rounded.Add) { widgetPickerOpen = true },
                GlassMenuItem("Apps auswählen", Icons.Rounded.CheckCircle) {
                    selecting = true
                    selected = emptySet()
                },
                GlassMenuItem("Hintergrundbild ändern", Icons.Rounded.Edit) { vm.openWallpaperPicker() },
                GlassMenuItem("Suche öffnen", Icons.Rounded.Search) { searchOpen = true },
            ),
        )
    }

    // Home button: close everything. Only a press on the home screen itself jumps back
    // to the first page; coming back from an app keeps the page, so no scroll animation
    // swallows the next tap on an icon.
    LaunchedEffect(Unit) {
        vm.homeEvents.collect { alreadyInFront ->
            menu = null
            openFolder = null
            searchOpen = false
            settingsOpen = false
            controlOpen = false
            widgetPickerOpen = false
            endSelection()
            if (alreadyInFront && pagerState.currentPage != widgetPages) pagerState.animateScrollToPage(widgetPages)
        }
    }

    LaunchedEffect(Unit) {
        vm.settingsRequests.collect {
            vm.consumeSettingsRequest()
            menu = null
            searchOpen = false
            controlOpen = false
            settingsOpen = true
        }
    }

    // Always enabled: on the home screen, back does nothing (like every launcher).
    BackHandler {
        when {
            menu != null -> menu = null
            widgetPickerOpen -> widgetPickerOpen = false
            selecting -> endSelection()
            openFolder != null -> openFolder = null
            controlOpen -> controlOpen = false
            settingsOpen -> settingsOpen = false
            else -> searchOpen = false
        }
    }

    val light = rememberGlassLight(settings.glassMotion)
    val glassStyle = remember(settings) { GlassStyle.from(settings) }
    val menuBlur by animateDpAsState(
        targetValue = when {
            controlOpen -> 24.dp
            menu != null || openFolder != null -> 16.dp
            else -> 0.dp
        },
        animationSpec = tween(260),
        label = "menuBlur",
    )
    val onSwipeDown: (Boolean) -> Unit = { leftHalf ->
        when (settings.swipeDownAction) {
            SwipeDownAction.ControlCenter -> controlOpen = true
            SwipeDownAction.Search -> searchOpen = true
            SwipeDownAction.Notifications -> if (!vm.controls.expandNotifications()) controlOpen = true
            SwipeDownAction.Split ->
                if (!(leftHalf && vm.controls.expandNotifications())) controlOpen = true
        }
        Unit
    }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalSettings provides settings,
        LocalGlassStyle provides glassStyle,
        LocalGlassLight provides light,
        LocalSelection provides SelectionState(selecting, selected) { app ->
            selected = if (app.key in selected) selected - app.key else selected + app.key
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The home screen blurs away behind an open menu (Android 12+).
                        val radius = menuBlur.toPx()
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) {
                            BlurEffect(radius, radius, TileMode.Decal)
                        } else {
                            null
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = settings.dimWallpaper.coerceIn(0f, 0.9f)))
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.22f),
                                0.35f to Color.Transparent,
                                0.8f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.18f),
                            ),
                        )
                        .homeSwipes(
                            onSwipeUp = { if (settings.swipeOpensSearch) searchOpen = true },
                            onSwipeDown = onSwipeDown,
                        ),
                ) {
                    Column(Modifier.fillMaxSize().systemBarsPadding()) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { pagerPage ->
                            if (pagerPage < widgetPages) {
                                WidgetPage(repo = vm.widgets, onAddWidget = { widgetPickerOpen = true })
                                return@HorizontalPager
                            }
                            val page = pagerPage - widgetPages
                            if (page >= pages.size) {
                                AppLibraryPage(
                                    library = library,
                                    actions = actions,
                                    onOpenSearch = { searchOpen = true },
                                    onOpenFolder = { openFolder = it },
                                    modifier = Modifier.fadingEdges(),
                                )
                                return@HorizontalPager
                            }
                            Box(Modifier.fillMaxSize()) {
                                LongPressArea(onLongPress = homeMenu, modifier = Modifier.matchParentSize())
                                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                    if (page == 0) {
                                        HomeHeader(settings, Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 12.dp))
                                        if (settings.showClaudeCard) {
                                            ClaudeCard(
                                                onClick = { vm.askClaude() },
                                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                                            )
                                        }
                                        if (needsWallpaperAccess) {
                                            WallpaperAccessHint(
                                                onClick = requestWallpaperAccess,
                                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.padding(top = 12.dp))
                                    }
                                    AppGrid(
                                        apps = pages[page],
                                        columns = columns,
                                        rows = if (page == 0) firstPageRows else rows,
                                        actions = actions,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (widgetPages + pages.size + libraryPages > 1) {
                                PageDots(
                                    count = widgetPages + pages.size + libraryPages,
                                    current = pagerState.currentPage,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                            // The App Library has its own search bar at the top.
                            if (settings.showSearchPill && !onLibraryPage) {
                                SearchPill(onClick = { searchOpen = true })
                            }
                        }

                        Dock(
                            apps = dock,
                            actions = actions,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = searchOpen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it / 10 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 10 },
                ) {
                    SearchOverlay(
                        apps = apps,
                        actions = actions,
                        onLaunch = { app, bounds ->
                            launchApp(app, bounds)
                            searchOpen = false
                        },
                        onWebSearch = {
                            vm.webSearch(it)
                            searchOpen = false
                        },
                        onOpenSettings = {
                            searchOpen = false
                            settingsOpen = true
                        },
                        onAskClaude = {
                            vm.askClaude(it)
                            searchOpen = false
                        },
                        onDismiss = { searchOpen = false },
                    )
                }

                AnimatedVisibility(
                    visible = settingsOpen,
                    enter = fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 0.94f),
                    exit = fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.94f),
                ) {
                    SettingsScreen(
                        vm = vm,
                        needsWallpaperAccess = needsWallpaperAccess,
                        onRequestWallpaperAccess = requestWallpaperAccess,
                        onClose = { settingsOpen = false },
                    )
                }
            }

            AnimatedVisibility(
                visible = controlOpen,
                enter = fadeIn(tween(200)) + slideInVertically(tween(320)) { -it / 6 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 6 },
            ) {
                ControlCenter(
                    controls = vm.controls,
                    onClose = { controlOpen = false },
                    onOpenLauncherSettings = {
                        controlOpen = false
                        settingsOpen = true
                    },
                    onShowNotifications = {
                        controlOpen = false
                        vm.controls.expandNotifications()
                        Unit
                    },
                    onShowSystemQuickSettings = ControlCenterService.instance?.let { service ->
                        val open: () -> Unit = {
                            controlOpen = false
                            service.showSystemQuickSettings()
                        }
                        open
                    },
                )
            }

            AnimatedVisibility(
                visible = widgetPickerOpen,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 8 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 8 },
            ) {
                WidgetPicker(repo = vm.widgets, onPick = addWidget, onDismiss = { widgetPickerOpen = false })
            }

            LibraryFolderOverlay(folder = openFolder, actions = folderActions, onDismiss = { openFolder = null })

            SelectionBar(
                visible = selecting,
                count = selected.size,
                onHide = {
                    vm.hideAll(selected)
                    endSelection()
                },
                onDone = endSelection,
            )

            GlassMenuOverlay(request = menu, onDismiss = { menu = null })
        }
    }
}

private fun appMenuItems(vm: LauncherViewModel, app: AppInfo, onSelect: () -> Unit): List<GlassMenuItem> = buildList {
    add(GlassMenuItem("App-Info", Icons.Rounded.Info) { vm.openAppInfo(app) })
    if (vm.isInDock(app)) {
        val dockKeys = vm.dock.value.map { it.key }
        val index = dockKeys.indexOf(app.key)
        if (index > 0) {
            add(GlassMenuItem("Im Dock nach links", Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { vm.moveInDock(app, -1) })
        }
        if (index in 0 until dockKeys.lastIndex) {
            add(GlassMenuItem("Im Dock nach rechts", Icons.AutoMirrored.Rounded.KeyboardArrowRight) { vm.moveInDock(app, 1) })
        }
        add(GlassMenuItem("Aus dem Dock entfernen", Icons.Rounded.Close) { vm.removeFromDock(app) })
    } else if (vm.canAddToDock()) {
        add(GlassMenuItem("Zum Dock hinzufügen", Icons.Rounded.Add) { vm.addToDock(app) })
    }
    add(GlassMenuItem("Auswählen", Icons.Rounded.CheckCircle, onClick = onSelect))
    add(GlassMenuItem("Ausblenden", Icons.Rounded.Clear) { vm.hide(app) })
    add(GlassMenuItem("Deinstallieren", Icons.Rounded.Delete, destructive = true) { vm.uninstall(app) })
}

/** First page holds the clock, so it has fewer rows of apps. */
private fun paginate(apps: List<AppInfo>, columns: Int, rows: Int, firstPageRows: Int): List<List<AppInfo>> {
    val firstPageSize = columns * firstPageRows
    val rest = apps.drop(firstPageSize).chunked(columns * rows)
    return listOf(apps.take(firstPageSize)) + rest
}

/**
 * Vertical swipes on the home screen: up opens search, down opens the control center
 * or notifications (ColorOS-style split: left half notifications, right half controls).
 */
@Composable
private fun Modifier.homeSwipes(onSwipeUp: () -> Unit, onSwipeDown: (leftHalf: Boolean) -> Unit): Modifier {
    val up by rememberUpdatedState(onSwipeUp)
    val down by rememberUpdatedState(onSwipeDown)
    return pointerInput(Unit) {
        val threshold = 64.dp.toPx()
        var total = 0f
        var startX = 0f
        detectVerticalDragGestures(
            onDragStart = { start ->
                total = 0f
                startX = start.x
            },
            onDragEnd = {
                when {
                    total < -threshold -> up()
                    total > threshold -> down(startX < size.width / 2f)
                }
            },
            onVerticalDrag = { _, dragAmount -> total += dragAmount },
        )
    }
}

/** Empty space behind the icons: a long press opens the home screen menu. */
@Composable
private fun LongPressArea(onLongPress: (Offset) -> Unit, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress(origin + it) })
            },
    )
}

@Composable
private fun rememberNow(): State<LocalDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                val msToNextMinute = 60_000L - (now.second * 1_000L + now.nano / 1_000_000L)
                delay(msToNextMinute.coerceAtLeast(500L))
            }
        }
    }
}

private class BatteryState(val percent: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): State<BatteryState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            state.value = BatteryState(
                percent = level * 100 / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent)
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        read(sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun greetingFor(hour: Int) = when (hour) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Guten Tag"
    in 18..22 -> "Guten Abend"
    else -> "Gute Nacht"
}

@Composable
private fun HomeHeader(settings: LauncherSettings, modifier: Modifier = Modifier) {
    when (settings.clockStyle) {
        ClockStyle.Hidden -> Spacer(modifier)
        ClockStyle.ColorOS -> ColorOSClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Large -> LargeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Glass -> GlassClockCard(settings, modifier)
    }
}

@Composable
private fun LargeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val locale = Locale.getDefault()
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm", locale)) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    Column(modifier) {
        if (settings.showGreeting) {
            Text(
                text = greetingFor(now.hour),
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                style = OnWallpaperText,
            )
        }
        Text(
            text = time,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 76.sp,
            lineHeight = 80.sp,
            style = OnWallpaperText,
        )
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            style = OnWallpaperText,
        )
    }
}

/** ColorOS-style home clock: big light digits, date with battery, and a Claude-flavored greeting. */
@Composable
private fun ColorOSClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val battery by rememberBattery()
    val locale = Locale.getDefault()
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm", locale)) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val accent = settings.accent.color

    Column(modifier) {
        Text(
            text = time,
            color = Color.White,
            fontWeight = FontWeight.Light,
            fontSize = 84.sp,
            lineHeight = 86.sp,
            letterSpacing = (-2).sp,
            style = OnWallpaperText,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = date, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, style = OnWallpaperText)
            val level = battery
            if (settings.showBattery && level != null) {
                Text(
                    text = "  ·  " + (if (level.charging) "⚡" else "") + "${level.percent} %",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    style = OnWallpaperText,
                )
            }
        }
        if (settings.showGreeting) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                ClaudeSpark(accent, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = greetingFor(now.hour),
                    color = accent,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    style = OnWallpaperText,
                )
            }
        }
    }
}

/** Whimsical "thinking" words, in the spirit of Claude Code's spinner. */
private val ClaudeMoods = listOf(
    "Grübelt …", "Sinniert …", "Tüftelt …", "Clauding …", "Brütet Ideen aus …",
    "Philosophiert …", "Kombiniert …", "Denkt mit …", "Schmiedet Pläne …", "Ist neugierig …",
)

/** Glass card in Claude's terracotta: tap to open Claude. */
@Composable
private fun ClaudeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalSettings.current.accent.color
    var mood by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            mood = (mood + 1) % ClaudeMoods.size
        }
    }
    LiquidGlass(
        cornerRadius = 26.dp,
        refraction = 20.dp,
        interactive = true,
        tint = Color(0xFFD97757).copy(alpha = 0.20f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeSpark(accent, Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Frag Claude",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    style = OnWallpaperText,
                )
                Crossfade(targetState = ClaudeMoods[mood], animationSpec = tween(500), label = "claudeMood") { text ->
                    Text(
                        text = text,
                        color = Color.White.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        style = OnWallpaperText,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Clock, date and battery on a big liquid glass card. Tap opens the alarms. */
@Composable
private fun GlassClockCard(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val battery by rememberBattery()
    val locale = Locale.getDefault()
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm", locale)) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    LiquidGlass(
        cornerRadius = 32.dp,
        refraction = 26.dp,
        blur = 18.dp,
        interactive = true,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .clickable {
                val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(alarms) }
            },
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (settings.showGreeting) {
                    Text(
                        text = greetingFor(now.hour),
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 17.sp,
                        style = OnWallpaperText,
                    )
                }
                Text(
                    text = time,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light,
                    fontSize = 60.sp,
                    lineHeight = 64.sp,
                    style = OnWallpaperText,
                )
                Text(
                    text = date,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    style = OnWallpaperText,
                )
            }
            val level = battery
            if (settings.showBattery && level != null) {
                Spacer(Modifier.width(12.dp))
                BatteryRing(level)
            }
        }
    }
}

@Composable
private fun BatteryRing(battery: BatteryState) {
    val accent = LocalSettings.current.accent.color
    val color = when {
        battery.charging -> Color(0xFF34C759)
        battery.percent <= 15 -> Color(0xFFFF6B5E)
        else -> accent
    }
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * battery.percent / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = if (battery.charging) "⚡${battery.percent}" else "${battery.percent}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            style = OnWallpaperText,
        )
    }
}

/** Always [rows] rows high, so icons sit in the same places on every page. */
@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    columns: Int,
    rows: Int,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) { r ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(columns) { c ->
                    val app = apps.getOrNull(r * columns + c)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (app != null) {
                            key(app.key) {
                                AppIcon(app, actions, fillCell = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Page indicator on a small glass capsule. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    LiquidGlass(cornerRadius = 12.dp, refraction = 6.dp, blur = 10.dp, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            repeat(count) { index ->
                Box(
                    Modifier
                        .size(if (index == current) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)),
                )
            }
        }
    }
}

/** Glass capsule; swells and glows under the finger, like liquid glass. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    LiquidGlass(
        cornerRadius = 20.dp,
        refraction = 10.dp,
        interactive = true,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Suchen", color = Color.White, fontSize = 14.sp, style = OnWallpaperText)
        }
    }
}

/** Shown until the launcher may read the wallpaper; without it, glass can only be frosted. */
@Composable
private fun WallpaperAccessHint(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlass(
        cornerRadius = 20.dp,
        interactive = true,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Echtes Glas aktivieren",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    style = OnWallpaperText,
                )
                Text(
                    text = "Tippen und Zugriff auf alle Dateien erlauben, damit das Glas dein Hintergrundbild zeigen kann.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    style = OnWallpaperText,
                )
            }
        }
    }
}

/** Glass bar while selecting several apps: count, hide them, or finish. */
@Composable
private fun SelectionBar(visible: Boolean, count: Int, onHide: () -> Unit, onDone: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            LiquidGlass(
                cornerRadius = 26.dp,
                refraction = 18.dp,
                tint = Color.Black.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (count == 0) "Apps antippen zum Auswählen" else "$count ausgewählt",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        GlassChip("Ausblenden", onClick = onHide)
                        Spacer(Modifier.width(8.dp))
                    }
                    GlassChip("Fertig", onClick = onDone)
                }
            }
        }
    }
}
