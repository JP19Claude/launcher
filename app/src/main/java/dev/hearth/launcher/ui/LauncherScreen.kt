package dev.hearth.launcher.ui

import androidx.compose.ui.unit.DpSize
import dev.hearth.launcher.system.GlimmerLink
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.mutableIntStateOf
import android.Manifest
import android.accessibilityservice.AccessibilityService
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import dev.hearth.launcher.data.CellPos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import dev.hearth.launcher.data.NotificationHub
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.hearth.launcher.data.HomeWidget
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.AppInfo
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.hearth.launcher.data.EasterEggs
import dev.hearth.launcher.data.ClockFont
import dev.hearth.launcher.data.HomeGesture
import dev.hearth.launcher.data.NightMode
import androidx.compose.material.icons.rounded.Share
import dev.hearth.launcher.data.HearthWidget
import dev.hearth.launcher.data.ClockStyle
import dev.hearth.launcher.data.nightActive
import dev.hearth.launcher.data.forNight
import dev.hearth.launcher.data.mythic
import dev.hearth.launcher.data.Perk
import dev.hearth.launcher.data.has
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.SwipeDownAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val storedSettings by vm.settings.collectAsStateWithLifecycle()
    // Night mode: by night the whole of Hearth gets darker and calmer (Auto follows the clock).
    // (Derived, so the minute ticking by doesn't redraw the whole screen – only night changing.)
    val clock = rememberNow()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val night by remember(systemDark) { derivedStateOf { storedSettings.nightActive(clock.value.hour, systemDark) } }
    // OMEGA UI 18: Claude Mythos lays its takeover over everything chosen.
    val settings = remember(storedSettings, night) { (if (night) storedSettings.forNight() else storedSettings).mythic() }
    val library by vm.library.collectAsStateWithLifecycle()
    val assistantOpen by vm.assistantOpen.collectAsStateWithLifecycle()
    val appUsage by vm.appUsage.collectAsStateWithLifecycle()
    var openFolder by remember { mutableStateOf<LibraryFolderContent?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Galaxy × Claude: One UI's app drawer (swipe up) instead of the App Library page.
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // Galaxy × Claude: One UI's edit mode (long press on the home screen).
    var editMode by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var controlOpen by rememberSaveable { mutableStateOf(false) }
    var controlPage by remember { mutableIntStateOf(0) }
    // The app that was just closed, flying into Glimmer.
    var flyApp by remember { mutableStateOf<Triple<AppInfo, ImageBitmap?, DpSize?>?>(null) }
    var flyLook by remember { mutableStateOf<FlyInLook?>(null) }
    var flyLanding by remember { mutableStateOf(GlimmerLink.Landing()) }
    var menu by remember { mutableStateOf<GlassMenuRequest?>(null) }
    // Hearth UI 15: the Hearth menu, and the hidden menus behind secret codes.
    var hubOpen by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf<SecretMenu?>(null) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val endSelection = {
        selecting = false
        selected = emptySet()
    }

    val context = LocalContext.current
    remember { HearthLabs.init(context) }
    val scope = rememberCoroutineScope()
    // Hearth UI 14: the tour of what's new, once after the big update.
    var showIntro by remember { mutableStateOf(!HearthUi.introSeen(context)) }
    // ZENITH 19: once, "OMEGA UI is now ZENITH" – the sun rises; the tour follows.
    var showOmega by remember { mutableStateOf(dev.hearth.launcher.BuildConfig.ALL_IN_ONE && !HearthUi.omegaSeen(context)) }
    // Glimmer Drop: "devices nearby" asked once, so two phones held together just work.
    val dropPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        dev.hearth.launcher.data.GlimmerDrop.setReady(context, dev.hearth.launcher.data.GlimmerDrop.ready.value)
    }
    LaunchedEffect(showIntro, showOmega) {
        if (showIntro || showOmega || !dev.hearth.launcher.BuildConfig.ALL_IN_ONE) return@LaunchedEffect
        val drop = dev.hearth.launcher.data.GlimmerDrop
        drop.loadReady(context)
        if (drop.enabled.value && drop.ready.value && !drop.hasPermissions(context) && drop.askOnce(context)) {
            dropPermissions.launch(drop.neededPermissions())
        }
    }
    // Night mode reaches Android too (with Shizuku): dark mode and eye comfort follow it.
    // Only a change of night is passed on, so switching them by hand in between stays.
    LaunchedEffect(night, storedSettings.nightSystem) {
        if (!storedSettings.nightSystem || storedSettings.nightMode == NightMode.System) return@LaunchedEffect
        if (!dev.hearth.launcher.data.ShizukuBridge.isReady(context)) return@LaunchedEffect
        val prefs = context.getSharedPreferences("hearth_night", Context.MODE_PRIVATE)
        if (prefs.getBoolean("applied", false) == night) return@LaunchedEffect
        dev.hearth.launcher.data.ShizukuBridge.setDarkMode(night)
        prefs.edit().putBoolean("applied", night).apply()
    }
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
    // Galaxy × Claude's Now Brief is about two rows tall, the Claude card one.
    val claudeRows = when {
        settings.galaxyClaude -> 2
        settings.showClaudeCard -> 1
        else -> 0
    }
    val firstPageRows = (rows - clockRows - claudeRows).coerceAtLeast(1)
    val homeApps = remember(apps, dock, settings.removedFromHome) {
        val dockKeys = dock.map { it.key }.toSet()
        apps.filterNot { it.key in dockKeys || it.key in settings.removedFromHome }
    }
    val homeWidgets by vm.widgets.homeWidgets.collectAsStateWithLifecycle()
    val pinned by vm.homePositions.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val badges by NotificationHub.badges.collectAsStateWithLifecycle()
    // Apps in a folder show up as the folder; everything else as itself.
    val homeItems = remember(homeApps, folders) {
        val byKey = homeApps.associateBy { it.key }
        val folderItems = folders.mapNotNull { f ->
            val inside = f.apps.mapNotNull { byKey[it] }
            if (inside.isEmpty()) null else FolderItem(f.id, f.name, inside)
        }
        val foldered = folderItems.flatMap { f -> f.apps.map { it.key } }.toSet()
        homeApps.filterNot { it.key in foldered }.map { AppItem(it) } + folderItems
    }
    val laidOut = remember(homeItems, homeWidgets, pinned, columns, rows, firstPageRows) {
        layoutHome(homeItems, homeWidgets, pinned, columns, rows, firstPageRows)
    }
    var openHomeFolder by remember { mutableStateOf<FolderItem?>(null) }
    var renaming by remember { mutableStateOf<FolderItem?>(null) }
    // Dragging an app: where the finger is, and an extra empty page to drop onto.
    var drag by remember { mutableStateOf<AppDrag?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val gridGeometry = remember { mutableStateMapOf<Int, Rect>() }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var rootWidth by remember { mutableStateOf(0) }
    val pages = if (drag != null) laidOut + HomePage(rows, emptyList(), emptyList()) else laidOut
    var toast by remember { mutableStateOf<GlassToast?>(null) }
    // Size of the pager, to turn a widget's size in dp into grid cells.
    var pagerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val libraryPages = if (settings.showAppLibrary && !settings.galaxyClaude) 1 else 0
    val widgetPages = if (settings.showWidgetPage) 1 else 0
    // Page order: [widgets] home pages… [App Library]; the launcher opens on the first home page.
    val pagerState = rememberPagerState(initialPage = widgetPages) { widgetPages + pages.size + libraryPages }
    val onLibraryPage = libraryPages > 0 && pagerState.currentPage >= widgetPages + pages.size
    val editScale by animateFloatAsState(if (editMode) 0.84f else 1f, spring(dampingRatio = 0.85f, stiffness = 380f), label = "editScale")

    // Adding a widget: permission dialog (once per app) and the widget's own setup screen.
    val activity = context as? Activity
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity?.let { vm.widgets.onBindResult(result.resultCode == Activity.RESULT_OK, it) }
    }
    // null: widget page; otherwise the home page the new widget goes to.
    var widgetTarget by remember { mutableStateOf<Int?>(null) }

    // Photo widgets: the system photo picker, then the pictures are copied into Hearth.
    var photoWidgetId by remember { mutableStateOf<Int?>(null) }
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        val id = photoWidgetId ?: return@rememberLauncherForActivityResult
        photoWidgetId = null
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = vm.widgets.photos.import(id, uris)
                toast = GlassToast(if (count == 1) "1 Foto übernommen" else "$count Fotos übernommen")
            }
        }
    }
    val requestPhotos: (Int) -> Unit = { id ->
        photoWidgetId = id
        runCatching {
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    val addPhotoWidget: () -> Unit = {
        widgetPickerOpen = false
        val slot = widgetTarget?.let { targetPage ->
            findFreeSlot(homeWidgets, pages.size, targetPage, 2, 2, columns, rows, firstPageRows)
        }
        requestPhotos(vm.widgets.addPhotoWidget(slot))
    }
    val addHearthWidget: (HearthWidget) -> Unit = { kind ->
        widgetPickerOpen = false
        val slot = widgetTarget?.let { targetPage ->
            findFreeSlot(homeWidgets, pages.size, targetPage, kind.spanX, kind.spanY, columns, rows, firstPageRows)
        }
        vm.widgets.addHearthWidget(kind, slot)
        toast = GlassToast("${kind.label} hinzugefügt")
    }
    val addWidget: (AppWidgetProviderInfo) -> Unit = { info ->
        widgetPickerOpen = false
        val slot = widgetTarget?.let { targetPage ->
            val cellW = with(density) { pagerSize.width.toDp().value } / columns
            val cellH = with(density) { pagerSize.height.toDp().value } / rows
            val (minW, minH) = vm.widgets.minSizeDp(info)
            val wantX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
                info.targetCellWidth.coerceIn(1, columns)
            } else {
                spanFor(minW, cellW, columns)
            }
            val wantY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
                info.targetCellHeight.coerceIn(1, rows)
            } else {
                spanFor(minH, cellH, rows)
            }
            findFreeSlot(homeWidgets, pages.size, targetPage, wantX, wantY, columns, rows, firstPageRows)
        }
        val permission = vm.widgets.begin(info, slot)
        if (permission == null) {
            activity?.let { vm.widgets.finishBinding(it) }
        } else {
            runCatching { bindWidget.launch(permission) }
        }
    }

    // One UI 10 Fluid: waves of Claude's colors when an app opens, and coming home.
    val fluidWaves = rememberFluidWaves()
    val waveOwner = LocalLifecycleOwner.current
    DisposableEffect(waveOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) fluidWaves.bloom()
        }
        waveOwner.lifecycle.addObserver(observer)
        onDispose { waveOwner.lifecycle.removeObserver(observer) }
    }

    // Apps open with a zoom out of their icon, which the system can start right away.
    val view = LocalView.current
    val launchApp: (AppInfo, Rect?) -> Unit = remember(vm, view) {
        { app, bounds ->
            val area = bounds?.takeIf { it.width > 0f && it.height > 0f }
            area?.let { fluidWaves.splash(it.center) }
            val source = area?.let {
                android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
            }
            val options = source?.let {
                runCatching {
                    ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                }.getOrNull()
            }
            // An app still flying into Glimmer makes way at once for the next one.
            flyApp = null
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
            dragStart = { app, finger, iconBounds ->
                menu = null
                drag = AppDrag(AppItem(app), vm.isInDock(app), finger - iconBounds.topLeft, iconBounds.size)
                dragPos = finger
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
        if (settings.galaxyClaude) {
            editMode = true
        } else {
            menu = GlassMenuRequest(
            anchor = Rect(position, Size(1f, 1f)),
            title = "Hearth",
            items = listOf(
                GlassMenuItem("Launcher-Einstellungen", Icons.Rounded.Settings) { settingsOpen = true },
                GlassMenuItem("Schnelleinstellungen", Icons.Rounded.Home) {
                    vm.controls.expandQuickSettings()
                },
                GlassMenuItem("Widget hierher", Icons.Rounded.Add) {
                    widgetTarget = (pagerState.currentPage - widgetPages).coerceAtLeast(0)
                    widgetPickerOpen = true
                },
                GlassMenuItem("Apps auswählen", Icons.Rounded.CheckCircle) {
                    selecting = true
                    selected = emptySet()
                },
                GlassMenuItem("Hintergrundbild ändern", Icons.Rounded.Edit) { vm.openWallpaperPicker() },
                GlassMenuItem("Suche öffnen", Icons.Rounded.Search) { searchOpen = true },
                GlassMenuItem("OMEGA-Menü", Icons.Rounded.Star) { hubOpen = true },
                GlassMenuItem("Ein/Aus-Menü", Icons.Rounded.Lock) { secret = SecretMenu.Power },
            ),
        )
        }
    }

    // Home button: close everything. Only a press on the home screen itself jumps back
    // to the first page; coming back from an app keeps the page, so no scroll animation
    // swallows the next tap on an icon.
    LaunchedEffect(Unit) {
        vm.homeEvents.collect { alreadyInFront ->
            menu = null
            hubOpen = false
            secret = null
            openFolder = null
            openHomeFolder = null
            renaming = null
            drag = null
            searchOpen = false
            drawerOpen = false
            editMode = false
            vm.closeAssistant()
            settingsOpen = false
            controlOpen = false
            widgetPickerOpen = false
            endSelection()
            if (alreadyInFront && pagerState.currentPage != widgetPages) pagerState.animateScrollToPage(widgetPages)
        }
    }

    val currentSettings by rememberUpdatedState(settings)
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        vm.flyIns.collect { fly ->
            val s = currentSettings
            // Needs the Glimmer app running with its island on.
            if (s.glimmerFlyIn && s.animations && fly.ready) {
                // Apps that hide their screen (banking, passwords) give a black picture: then the
                // card in the app's colors flies instead.
                val shot = fly.snapshot?.takeIf { withContext(Dispatchers.Default) { snapshotLooksReal(it) } }
                // Its colors and small icon, worked out off the main thread (so taps aren't held up).
                flyLook = withContext(Dispatchers.Default) { runCatching { flyInLook(appContext, fly.app) }.getOrNull() }
                flyLanding = fly.landing
                flyApp = Triple(fly.app, shot?.asImageBitmap(), fly.island)
            }
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
            assistantOpen -> vm.closeAssistant()
            editMode -> editMode = false
            menu != null -> menu = null
            renaming != null -> renaming = null
            openHomeFolder != null -> openHomeFolder = null
            widgetPickerOpen -> widgetPickerOpen = false
            selecting -> endSelection()
            openFolder != null -> openFolder = null
            controlOpen -> controlOpen = false
            settingsOpen -> settingsOpen = false
            searchOpen -> searchOpen = false
            else -> drawerOpen = false
        }
    }

    val light = rememberGlassLight(settings.glassMotion)
    val glassStyle = remember(settings) { GlassStyle.from(settings) }
    val menuBlur by animateDpAsState(
        targetValue = when {
            controlOpen -> 24.dp
            menu != null || openFolder != null || openHomeFolder != null -> 16.dp
            else -> 0.dp
        },
        animationSpec = tween(260),
        label = "menuBlur",
    )
    // Swiping down opens the phone's own panels (there's no separate control center anymore).
    val openControlCenter: () -> Unit = {
        vm.controls.expandQuickSettings()
        Unit
    }
    val openNotifications: () -> Unit = {
        vm.controls.expandNotifications()
        Unit
    }
    val onSwipeDown: (Boolean) -> Unit = { leftHalf ->
        when (settings.swipeDownAction) {
            SwipeDownAction.ControlCenter -> openControlCenter()
            SwipeDownAction.Search -> searchOpen = true
            SwipeDownAction.Notifications -> openNotifications()
            SwipeDownAction.Split -> if (leftHalf) openNotifications() else openControlCenter()
        }
        Unit
    }

    // Where a dragged app would land: (home page, column, row), or null.
    fun dropCell(d: AppDrag, finger: Offset): Triple<Int, Int, Int>? {
        val point = finger - d.grabOffset + Offset(d.iconSize.width / 2f, d.iconSize.height / 2f)
        val homePage = pagerState.currentPage - widgetPages
        if (homePage !in pages.indices) return null
        val rect = gridGeometry[homePage] ?: return null
        if (!rect.contains(point)) return null
        val pageRows = pages[homePage].rows
        val col = ((point.x - rect.left) / (rect.width / columns)).toInt().coerceIn(0, columns - 1)
        val row = ((point.y - rect.top) / (rect.height / pageRows)).toInt().coerceIn(0, pageRows - 1)
        return Triple(homePage, col, row)
    }

    val finishDrag: () -> Unit = {
        val d = drag
        val finger = dragPos
        drag = null
        if (d != null) {
            val point = finger - d.grabOffset + Offset(d.iconSize.width / 2f, d.iconSize.height / 2f)
            val target = dropCell(d, finger)
            when {
                dockBounds.contains(point) -> when {
                    d.fromDock -> Unit
                    d.item !is AppItem -> toast = GlassToast("Ordner passen nicht ins Dock")
                    vm.canAddToDock() -> {
                        val app = (d.item as AppItem).app
                        vm.forgetHomePosition(app.key)
                        vm.addToDock(app)
                    }
                    else -> toast = GlassToast("Das Dock ist voll")
                }
                target != null -> {
                    val (page, col, row) = target
                    val draggedKey = d.item.key
                    val occupant = laidOut.getOrNull(page)?.apps
                        ?.firstOrNull { it.col == col && it.row == row && it.item.key != draggedKey }?.item
                    // From now on everything keeps its place.
                    val positions = HashMap<String, CellPos>()
                    laidOut.forEachIndexed { p, hp -> hp.apps.forEach { positions[it.item.key] = CellPos(p, it.col, it.row) } }
                    val newPos = CellPos(page, col, row)
                    val dragged = d.item
                    when {
                        pages[page].widgets.any { it.covers(col, row) } -> toast = GlassToast("Dort ist ein Widget")
                        // App on app: a new folder in that place.
                        dragged is AppItem && occupant is AppItem -> {
                            val folder = vm.createFolder(occupant.app, dragged.app)
                            positions.remove(occupant.key)
                            positions.remove(dragged.key)
                            positions[folder.key] = newPos
                            vm.setHomePositions(positions)
                            if (d.fromDock) vm.removeFromDock(dragged.app)
                            toast = GlassToast("Ordner „${folder.name}“ erstellt")
                        }
                        // App on folder: into the folder.
                        dragged is AppItem && occupant is FolderItem -> {
                            vm.addToFolder(occupant.id, dragged.app)
                            positions.remove(dragged.key)
                            vm.setHomePositions(positions)
                            if (d.fromDock) vm.removeFromDock(dragged.app)
                        }
                        // Free cell, or a folder dropped on something: move (and swap).
                        else -> {
                            val old = positions[draggedKey]
                            if (occupant != null) {
                                if (old != null) positions[occupant.key] = old else positions.remove(occupant.key)
                            }
                            positions[draggedKey] = newPos
                            vm.setHomePositions(positions)
                            if (d.fromDock && dragged is AppItem) vm.removeFromDock(dragged.app)
                        }
                    }
                }
            }
        }
        Unit
    }

    // The touch handler below lives as long as the screen: it must always drop with the home
    // screen as it is now (widgets moved or removed since), not as it was when it started.
    val latestFinishDrag by rememberUpdatedState(finishDrag)

    // Holding a dragged app at the screen edge turns the page.
    val edgeZone = with(density) { 28.dp.toPx() }
    val edge = when {
        drag == null || rootWidth == 0 -> 0
        dragPos.x < edgeZone -> -1
        dragPos.x > rootWidth - edgeZone -> 1
        else -> 0
    }
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(650)
            val target = pagerState.currentPage + edge
            if (target < widgetPages || target >= widgetPages + pages.size) break
            pagerState.animateScrollToPage(target)
        }
    }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalSettings provides settings,
        LocalGlassStyle provides glassStyle,
        LocalGlassLight provides light,
        LocalPhotoPicker provides requestPhotos,
        LocalBadges provides badges,
        // OMEGA UI 18: the color world is laid over the root below, with these settings.
        LocalColorWorldApplied provides true,
        // Remembered, so icons don't all recompose whenever the home screen does.
        LocalSelection provides remember(selecting, selected) {
            SelectionState(selecting, selected) { app ->
                selected = if (app.key in selected) selected - app.key else selected + app.key
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // OMEGA UI 18: Farbwelt, Noir, Sepia, Leuchtfarben, Augenschutz – now really on
                // the home screen (the theme above doesn't know these settings yet).
                .colorWorld()
                .onSizeChanged { rootWidth = it.width }
                // While an app is dragged, this follows the finger everywhere and keeps the touch
                // away from pages and icons below.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (drag == null) continue
                            val change = event.changes.firstOrNull() ?: continue
                            event.changes.forEach { it.consume() }
                            if (change.pressed) dragPos = change.position else latestFinishDrag()
                        }
                    }
                },
        ) {
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
                // OMEGA UI 18, Funkenspur: every touch on the home screen throws sparks.
                val sparkField = remember(settings.has(Perk.Sparks)) { if (settings.has(Perk.Sparks)) SparkField() else null }
                Box(
                    Modifier
                        .fillMaxSize()
                        .sparkSource(sparkField)
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
                            onSwipeUp = {
                                when {
                                    settings.galaxyClaude -> drawerOpen = true
                                    settings.swipeOpensSearch -> searchOpen = true
                                }
                            },
                            onSwipeDown = onSwipeDown,
                        ),
                ) {
                    // OMEGA UI 17.5: OMEGA light between the wallpaper and the apps and widgets.
                    OmegaHomeLight(Modifier.matchParentSize())
                    // OMEGA UI 18: the perks behind the apps and widgets (and Claude Mythos's Clawd).
                    PerkBackLayers(settings, Modifier.matchParentSize())
                    // Claude Mythos: giant Clawds behind everything.
                    MythosGiants()
                    // OMEGA UI 17.5, Clawd Illumination: Ω rising behind everything.
                    if (settings.omegaRain) OmegaRain(Modifier.matchParentSize())
                    // OMEGA UI 17.6, Clawd Illuminati: the eye watches from the top.
                    if (settings.allSeeingEye) {
                        AllSeeingEye(
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 2.dp)
                                .size(width = 46.dp, height = 40.dp),
                        )
                    }
                    Column(Modifier.fillMaxSize().parallax(settings.has(Perk.Parallax)).systemBarsPadding()) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onSizeChanged { pagerSize = it }
                                // One UI's edit mode: the pages step back a little.
                                .graphicsLayer {
                                    scaleX = editScale
                                    scaleY = editScale
                                },
                        ) { pagerPage ->
                            val pageMotion = Modifier.pageTransition(settings.pageTransition) {
                                (pagerState.currentPage - pagerPage) + pagerState.currentPageOffsetFraction
                            }
                            if (pagerPage < widgetPages) {
                                WidgetPage(
                                    repo = vm.widgets,
                                    onAddWidget = {
                                        widgetTarget = null
                                        widgetPickerOpen = true
                                    },
                                    modifier = pageMotion,
                                )
                                return@HorizontalPager
                            }
                            val page = pagerPage - widgetPages
                            if (page >= pages.size) {
                                AppLibraryPage(
                                    library = library,
                                    actions = actions,
                                    onOpenSearch = { searchOpen = true },
                                    onOpenFolder = { openFolder = it },
                                    modifier = pageMotion.fadingEdges(),
                                )
                                return@HorizontalPager
                            }
                            Box(Modifier.fillMaxSize().then(pageMotion)) {
                                LongPressArea(
                                    onLongPress = homeMenu,
                                    onDoubleTap = {
                                        when (settings.doubleTapAction) {
                                            HomeGesture.Off -> Unit
                                            HomeGesture.Lock -> {
                                                // Locking needs an accessibility service: Glimmer's.
                                                if (GlimmerLink.isInstalled(context)) {
                                                    vm.controls.lockScreen()
                                                } else {
                                                    toast = GlassToast("Zum Sperren per Doppeltippen: die App „Glimmer“ installieren und ihre Bedienungshilfe einschalten")
                                                }
                                            }
                                            HomeGesture.Claude -> vm.askClaude()
                                            HomeGesture.Search -> searchOpen = true
                                            HomeGesture.Drawer -> if (settings.galaxyClaude) drawerOpen = true else searchOpen = true
                                            HomeGesture.Notifications -> openNotifications()
                                            HomeGesture.Torch -> vm.controls.setTorch(!vm.controls.torchOn.value)
                                            HomeGesture.Menu -> hubOpen = true
                                            HomeGesture.Power -> secret = SecretMenu.Power
                                        }
                                        Unit
                                    },
                                    modifier = Modifier.matchParentSize(),
                                )
                                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                    if (page == 0) {
                                        HomeHeader(
                                            settings,
                                            Modifier
                                                .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 12.dp)
                                                .then(if (settings.has(Perk.GiantClock)) Modifier.grow(1.3f) else Modifier),
                                        )
                                        if (settings.galaxyClaude) {
                                            NowBriefCard(
                                                onAsk = { vm.askClaude(it) },
                                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                                            )
                                        } else if (settings.showClaudeCard) {
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
                                    HomePageGrid(
                                        page = pages[page],
                                        pageIndex = page,
                                        onGeometry = { index, rect -> gridGeometry[index] = rect },
                                        draggingKey = drag?.item?.key,
                                        onOpenFolder = { openHomeFolder = it },
                                        onFolderMenu = { folder, bounds ->
                                            menu = GlassMenuRequest(
                                                anchor = bounds,
                                                title = folder.name,
                                                items = listOf(
                                                    GlassMenuItem("Öffnen", Icons.Rounded.Search) { openHomeFolder = folder },
                                                    GlassMenuItem("Umbenennen", Icons.Rounded.Edit) { renaming = folder },
                                                    GlassMenuItem("Ordner auflösen", Icons.Rounded.Clear, destructive = true) {
                                                        vm.dissolveFolder(folder.id)
                                                    },
                                                ),
                                            )
                                        },
                                        onFolderDragStart = { folder, finger, bounds ->
                                            menu = null
                                            drag = AppDrag(folder, false, finger - bounds.topLeft, bounds.size)
                                            dragPos = finger
                                        },
                                        columns = columns,
                                        actions = actions,
                                        widgets = vm.widgets,
                                        onWidgetMenu = { widget, bounds ->
                                            menu = widgetMenu(
                                                widget = widget,
                                                bounds = bounds,
                                                pageCount = pages.size,
                                                tryPlace = { candidate ->
                                                    val ok = canPlace(candidate, homeWidgets, columns, rows, firstPageRows)
                                                    if (ok) vm.widgets.moveHome(candidate)
                                                    ok
                                                },
                                                findSlot = { targetPage ->
                                                    findFreeSlot(
                                                        homeWidgets.filterNot { it.id == widget.id }, pages.size,
                                                        targetPage, widget.spanX, widget.spanY, columns, rows, firstPageRows,
                                                    ).takeIf { it.page == targetPage }
                                                },
                                                onRemove = {
                                                    vm.widgets.removeHome(widget.id)
                                                    toast = GlassToast("Widget entfernt")
                                                },
                                                onPickPhotos = if (vm.widgets.isInternal(widget.id) && vm.widgets.kindOf(widget.id) == HearthWidget.Photos) {
                                                    { requestPhotos(widget.id) }
                                                } else {
                                                    null
                                                },
                                            )
                                        },
                                        onWidgetDrop = { widget, dCols, dRows ->
                                            val moved = widget.copy(col = widget.col + dCols, row = widget.row + dRows)
                                            if (canPlace(moved, homeWidgets, columns, rows, firstPageRows)) {
                                                vm.widgets.moveHome(moved)
                                            } else {
                                                toast = GlassToast("Dort ist kein Platz")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Clawd lives here, on the home pages: he walks, talks and plays.
                            if (settings.clawdCompanion && !onLibraryPage && pagerState.currentPage >= widgetPages) {
                                ClawdCompanion(onTalk = { vm.askClaude() })
                            }
                            if (widgetPages + pages.size + libraryPages > 1) {
                                PageDots(
                                    count = widgetPages + pages.size + libraryPages,
                                    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
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
                            draggingKey = drag?.item?.key,
                            modifier = Modifier
                                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
                                .onGloballyPositioned { dockBounds = it.boundsInRoot() },
                        )
                    }
                    // OMEGA UI 17.5, Clawd Illumination: now and then a parade of Clawds marches
                    // along the top of the dock.
                    // OMEGA UI 18.5: a strip of glass behind the status bar.
                    GlassStatusBar()
                    // OMEGA UI 18: the perks over the home screen – and Claude Mythos's banner.
                    PerkFrontLayers(settings, sparkField, if (dockBounds.height > 0f) dockBounds.top else 0f)
                    // Claude Mythos: Clawds peeking in from the edges and sitting on the dock.
                    MythosCrowd(if (dockBounds.height > 0f) dockBounds.top else 0f)
                    if (settings.clawdParade && dockBounds.height > 0f) {
                        ClawdParade(
                            Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .graphicsLayer { translationY = dockBounds.top - 34.dp.toPx() + 3.dp.toPx() },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = drawerOpen,
                    // The drawer lets its rows rise in itself; from outside just a short, soft lift.
                    enter = fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it / 14 },
                    exit = fadeOut(tween(170)) + slideOutVertically(tween(220)) { it / 9 },
                ) {
                    OneUIDrawer(
                        apps = apps,
                        actions = actions,
                        onLaunch = { app, bounds ->
                            launchApp(app, bounds)
                            drawerOpen = false
                        },
                        onOpenSearch = {
                            drawerOpen = false
                            searchOpen = true
                        },
                        onDismiss = { drawerOpen = false },
                        onOpenSettings = {
                            drawerOpen = false
                            settingsOpen = true
                        },
                        usage = appUsage,
                        suggestions = library.suggestions,
                        onSettingsChange = vm::updateSettings,
                    )
                }

                AnimatedVisibility(
                    visible = editMode,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(280)) { it / 8 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 8 },
                ) {
                    OneUIEditBar(
                        onWallpaper = {
                            editMode = false
                            vm.openWallpaperPicker()
                        },
                        onWidgets = {
                            editMode = false
                            widgetTarget = (pagerState.currentPage - widgetPages).coerceAtLeast(0)
                            widgetPickerOpen = true
                        },
                        onSettings = {
                            editMode = false
                            settingsOpen = true
                        },
                        onSelectApps = {
                            editMode = false
                            selecting = true
                            selected = emptySet()
                        },
                        onMenu = {
                            editMode = false
                            hubOpen = true
                        },
                        onDismiss = { editMode = false },
                    )
                }

                AnimatedVisibility(
                    visible = searchOpen,
                    // The finder comes down from the top, like a sheet of liquid.
                    enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.84f, stiffness = 380f)) { -it / 8 },
                    exit = fadeOut(tween(170)) + slideOutVertically(tween(220)) { -it / 8 },
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
                        claudeFirst = settings.galaxyClaude,
                        onSecret = {
                            searchOpen = false
                            secret = it
                        },
                        onDismiss = { searchOpen = false },
                        suggestions = library.suggestions,
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

            FluidWaveLayer(fluidWaves)

            flyApp?.let { (app, shot, island) ->
                GlimmerFlyIn(
                    app = app,
                    snapshot = shot,
                    island = island,
                    style = settings.glimmerFlyInStyle,
                    landing = flyLanding,
                    look = flyLook,
                    onArrive = vm::arriveGlimmer,
                    onPulse = vm::pulseGlimmer,
                    onDone = { flyApp = null },
                )
            }

            AnimatedVisibility(
                visible = widgetPickerOpen,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 8 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 8 },
            ) {
                WidgetPicker(
                    repo = vm.widgets,
                    onPick = addWidget,
                    onPickPhotos = addPhotoWidget,
                    onPickHearth = addHearthWidget,
                    onDismiss = { widgetPickerOpen = false },
                )
            }

            LibraryFolderOverlay(folder = openFolder, actions = folderActions, onDismiss = { openFolder = null })

            // A home screen folder opens like an App Library folder.
            val homeFolderContent = openHomeFolder?.let { f ->
                val current = folders.firstOrNull { it.id == f.id }
                val apps = current?.apps?.mapNotNull { key -> homeApps.firstOrNull { it.key == key } }.orEmpty()
                if (current == null || apps.isEmpty()) null else current.name to apps
            }
            LaunchedEffect(homeFolderContent == null) { if (homeFolderContent == null) openHomeFolder = null }
            LibraryFolderOverlay(
                folder = homeFolderContent,
                actions = remember(actions) {
                    AppActions(
                        launch = { app, bounds ->
                            openHomeFolder = null
                            actions.launch(app, bounds)
                        },
                        menu = actions.menu,
                    )
                },
                onDismiss = { openHomeFolder = null },
            )

            renaming?.let { folder ->
                RenameDialog(
                    initial = folder.name,
                    onConfirm = { name ->
                        vm.renameFolder(folder.id, name)
                        renaming = null
                    },
                    onDismiss = { renaming = null },
                )
            }

            SelectionBar(
                visible = selecting,
                count = selected.size,
                onHide = {
                    val keys = selected
                    vm.hideAll(keys)
                    toast = GlassToast("${keys.size} ausgeblendet", "Rückgängig") { vm.unhide(keys) }
                    endSelection()
                },
                onRemoveFromHome = {
                    val keys = selected
                    vm.removeFromHome(keys)
                    toast = GlassToast("${keys.size} vom Startbildschirm entfernt", "Rückgängig") { vm.addToHome(keys) }
                    endSelection()
                },
                onDone = endSelection,
            )

            GlassToastHost(toast = toast, onDismiss = { toast = null })

            GlassMenuOverlay(request = menu, onDismiss = { menu = null })

            // Claude, over everything: it can act on the phone (alarms, lights, apps, messages …).
            if (assistantOpen) {
                ClaudeAssistantSheet(
                    onDismiss = vm::closeAssistant,
                    onOpenSettings = {
                        searchOpen = false
                        drawerOpen = false
                        settingsOpen = true
                    },
                )
            }

            // Hearth UI 15: the Hearth menu – everything one tap away.
            if (hubOpen) {
                val torch by vm.controls.torchOn.collectAsStateWithLifecycle()
                val dropOn by dev.hearth.launcher.data.GlimmerDrop.enabled.collectAsStateWithLifecycle()
                HearthMenu(
                    tiles = listOfNotNull(
                        HubTile("Einstellungen", Icons.Rounded.Settings, Color(0xFF8E8E93)) { settingsOpen = true },
                        HubTile("Finder", Icons.Rounded.Search, Color(0xFF3E91FF)) { searchOpen = true },
                        HubTile("Alle Apps", Icons.Rounded.Home, Color(0xFF30B0C7)) {
                            if (settings.galaxyClaude) drawerOpen = true else searchOpen = true
                        },
                        HubTile("Clawd", Icons.Rounded.Face, Color(0xFFD97757)) { vm.askClaude() },
                        if (dropOn) {
                            HubTile("Glimmer\nDrop", Icons.Rounded.Share, Color(0xFF5E9BFF)) {
                                runCatching { context.startActivity(Intent(context, dev.hearth.launcher.DropActivity::class.java)) }
                            }
                        } else {
                            null
                        },
                        HubTile("Widgets", Icons.Rounded.Add, Color(0xFF34C759)) {
                            widgetTarget = (pagerState.currentPage - widgetPages).coerceAtLeast(0)
                            widgetPickerOpen = true
                        },
                        HubTile("Hintergrund", Icons.Rounded.Edit, Color(0xFFFF6FB5)) { vm.openWallpaperPicker() },
                        HubTile("Schnell-\neinstellungen", Icons.Rounded.CheckCircle, Color(0xFF5E5CE6)) {
                            vm.controls.expandQuickSettings()
                        },
                        HubTile("Mitteilungen", Icons.Rounded.Info, Color(0xFFFF9F0A)) { openNotifications() },
                        HubTile(if (torch) "Licht aus" else "Taschen-\nlampe", Icons.Rounded.Star, Color(0xFFFFCC00)) {
                            vm.controls.setTorch(!torch)
                        },
                        HubTile(if (night) "Dunkelmodus\naus" else "Dunkelmodus", Icons.Rounded.DateRange, Color(0xFF8E6BFF)) {
                            vm.updateSettings { it.copy(nightMode = if (night) NightMode.Off else NightMode.On) }
                        },
                        HubTile("Apps\nauswählen", Icons.Rounded.CheckCircle, Color(0xFF64D2FF)) {
                            selecting = true
                            selected = emptySet()
                        },
                        HubTile("Ein/Aus", Icons.Rounded.Lock, Color(0xFFFF453A)) { secret = SecretMenu.Power },
                    ),
                    onSecret = {
                        hubOpen = false
                        secret = SecretMenu.Codes
                    },
                    onClose = { hubOpen = false },
                )
            }

            // Hidden menus (secret codes in the finder, OMEGA Labs, the power menu).
            secret?.let { open -> SecretMenuScreen(open) { secret = null } }

            // OMEGA Labs: the frame counter.
            if (HearthLabs.fps) {
                Box(Modifier.fillMaxSize()) {
                    FpsMeter(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 4.dp))
                }
            }

            // Easter eggs: confetti and a glass message when one is found.
            CelebrationOverlay()

            if (showIntro && !showOmega) {
                HearthUiIntro { newLook ->
                    HearthUi.markIntroSeen(context)
                    // ZENITH 19's tour: the ZENITH design, if wanted.
                    if (newLook) vm.updateSettings { it.withPreset(dev.hearth.launcher.data.DesignPreset.GalaxyClaude) }
                    showIntro = false
                }
            }

            // ZENITH 19: the move from OMEGA UI – the Ω fades, the sun climbs to the zenith and
            // the ZENITH mark forms; the tour follows.
            if (showOmega) {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                ZenithUpgrade(version = HearthUi.major(version)) {
                    HearthUi.markOmegaSeen(context)
                    showOmega = false
                }
            }

            drag?.let { d ->
                DragOverlay(
                    drag = d,
                    finger = dragPos,
                    target = dropCell(d, dragPos)?.let { (page, col, row) ->
                        val rect = gridGeometry[page]
                        if (rect == null) {
                            null
                        } else {
                            val cellW = rect.width / columns
                            val cellH = rect.height / pages[page].rows
                            Rect(Offset(rect.left + col * cellW, rect.top + row * cellH), Size(cellW, cellH))
                        }
                    },
                    overDock = dockBounds.contains(
                        dragPos - d.grabOffset + Offset(d.iconSize.width / 2f, d.iconSize.height / 2f),
                    ),
                    dockBounds = dockBounds,
                )
            }
        }
    }
}

private fun appMenuItems(vm: LauncherViewModel, app: AppInfo, onSelect: () -> Unit): List<GlassMenuItem> = buildList {
    // The app's own shortcuts first, like on One UI and iOS.
    vm.shortcuts(app).forEach { shortcut ->
        add(GlassMenuItem(shortcut.label, Icons.Rounded.Star, image = shortcut.icon) { vm.startShortcut(shortcut) })
    }
    // Galaxy × Claude: Claude knows every app, right from its menu. Opens the Claude app itself
    // (or claude.ai), with the question already in it.
    if (vm.settings.value.galaxyClaude) {
        add(GlassMenuItem("Claude fragen", Icons.Rounded.Face) {
            vm.controls.openClaudeApp("Wie nutze ich die App „${app.label}“ am besten? Gib mir ein paar Tipps und versteckte Funktionen.")
        })
    }
    // App lock: locking is instant, unlocking asks for the PIN or biometrics first.
    add(GlassMenuItem(if (vm.isLocked(app)) "Entsperren" else "Sperren", Icons.Rounded.Lock) { vm.toggleLock(app) })
    add(GlassMenuItem("Teilen", Icons.Rounded.Share) { vm.shareApp(app) })
    add(GlassMenuItem("App-Info", Icons.Rounded.Info) { vm.openAppInfo(app) })
    // With Shizuku: stop an app at once, or freeze it (thawed again under Einstellungen → System).
    if (app.packageName != vm.getApplication<android.app.Application>().packageName &&
        dev.hearth.launcher.data.ShizukuBridge.isReady(vm.getApplication<android.app.Application>())
    ) {
        // OMEGA UI 17: the app as a floating window over everything.
        add(GlassMenuItem("Im Fenster öffnen", Icons.Rounded.Add) {
            vm.shizukuAction("${app.label} im Fenster") { dev.hearth.launcher.data.ShizukuBridge.openInWindow(app.component.flattenToShortString()) }
        })
        add(GlassMenuItem("App stoppen", Icons.Rounded.Close) {
            vm.shizukuAction("${app.label} gestoppt") { dev.hearth.launcher.data.ShizukuBridge.forceStop(app.packageName) }
        })
        add(GlassMenuItem("Einfrieren", Icons.Rounded.Lock) {
            vm.shizukuAction("${app.label} eingefroren") { dev.hearth.launcher.data.ShizukuBridge.freeze(app.packageName) }
        })
        val context = vm.getApplication<android.app.Application>()
        if (BackgroundLock.isLocked(context, app.packageName)) {
            add(GlassMenuItem("Im Hintergrund erlauben", Icons.Rounded.CheckCircle) {
                vm.shizukuAction("${app.label} darf wieder im Hintergrund laufen") { BackgroundLock.set(context, app.packageName, false) }
            })
        } else {
            add(GlassMenuItem("Im Hintergrund verbieten", Icons.Rounded.Clear) {
                vm.shizukuAction("${app.label} läuft nur noch, wenn sie offen ist") { BackgroundLock.set(context, app.packageName, true) }
            })
        }
    }
    add(GlassMenuItem("APK teilen", Icons.Rounded.Share) { vm.shareApk(app) })
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
    if (vm.isOnHome(app)) {
        if (!vm.isInDock(app)) {
            add(GlassMenuItem("Vom Startbildschirm entfernen", Icons.Rounded.Home) { vm.removeFromHome(listOf(app.key)) })
        }
    } else {
        add(GlassMenuItem("Zum Startbildschirm hinzufügen", Icons.Rounded.Home) { vm.addToHome(app.key) })
    }
    if (vm.folderOf(app.key) != null) {
        add(GlassMenuItem("Aus dem Ordner nehmen", Icons.Rounded.Close) { vm.removeFromFolder(app.key) })
    }
    add(GlassMenuItem("Auswählen", Icons.Rounded.CheckCircle, onClick = onSelect))
    add(GlassMenuItem("Ausblenden", Icons.Rounded.Clear) { vm.hide(app) })
    add(GlassMenuItem("Deinstallieren", Icons.Rounded.Delete, destructive = true) { vm.uninstall(app) })
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
private fun LongPressArea(onLongPress: (Offset) -> Unit, onDoubleTap: () -> Unit, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val longPress by rememberUpdatedState(onLongPress)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { longPress(origin + it) },
                    onDoubleTap = { doubleTap() },
                )
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
    // Easter egg: five quick taps on the clock.
    val context = LocalContext.current
    val taps = remember { longArrayOf(0L, 0L) }
    val tapped = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        val now = System.currentTimeMillis()
        taps[0] = if (now - taps[1] < 600) taps[0] + 1 else 1
        taps[1] = now
        if (taps[0] >= 5) {
            taps[0] = 0
            EasterEggs.find(context, "clock")
        }
    }
    HomeHeaderContent(settings, modifier.then(tapped))
}

@Composable
private fun HomeHeaderContent(settings: LauncherSettings, modifier: Modifier = Modifier) {
    when (settings.clockStyle) {
        ClockStyle.Hidden -> Spacer(modifier)
        ClockStyle.Stacked -> StackedClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.ColorOS -> ColorOSClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.OneUI -> OneUIClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Large -> LargeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Glass -> GlassClockCard(settings, modifier)
        ClockStyle.Zenith -> ZenithHomeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Omega -> OmegaHomeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Analog -> AnalogHomeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Words -> WordsHomeClock(settings, modifier.padding(start = 8.dp))
        ClockStyle.Split -> SplitHomeClock(settings, modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LargeClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val locale = Locale.getDefault()
    val time = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", locale)), settings.omegaZeros) }
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
        ClockDigits(
            settings,
            text = time,
            color = Color.White,
            fontFamily = clockFamily(settings, FontFamily.Serif),
            fontWeight = clockWeight(settings, FontWeight.Light),
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
    val time = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", locale)), settings.omegaZeros) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val accent = settings.accent.color

    Column(modifier) {
        ClockDigits(
            settings,
            text = time,
            color = Color.White,
            fontFamily = clockFamily(settings, null),
            fontWeight = clockWeight(settings, FontWeight.Light),
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

private val ClaudeTerracotta = Color(0xFFD97757)

/** One UI's home clock: big, bold digits set close, the date and battery underneath. */
@Composable
private fun OneUIClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val battery by rememberBattery()
    val locale = Locale.getDefault()
    val time = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", locale)), settings.omegaZeros) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    // OMEGA UI 17.4: glass digits, the date on glass chips – OMEGA UI 18.5: "Uhren aus Glas".
    val glass = settings.glassClocks
    val context = LocalContext.current
    Column(modifier) {
        if (glass) {
            GlassNumerals(
                text = time,
                fontFamily = clockFamily(settings, null),
                fontWeight = clockWeight(settings, FontWeight.Bold),
                fontSize = 82.sp,
                lineHeight = 84.sp,
                letterSpacing = (-3).sp,
            )
        } else {
            RollingText(
                text = time,
                color = Color.White,
                fontFamily = clockFamily(settings, null),
                fontWeight = clockWeight(settings, FontWeight.SemiBold),
                fontSize = 78.sp,
                lineHeight = 80.sp,
                letterSpacing = (-3).sp,
                style = OnWallpaperText,
            )
        }
        val level = battery
        if (glass) {
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCapsule(onClick = { openCalendar(context) }, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(text = date, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (settings.showBattery && level != null) {
                    GlassCapsule(onClick = { openBattery(context) }, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                            text = (if (level.charging) "⚡ " else "") + "${level.percent} %",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = date, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = OnWallpaperText)
                if (settings.showBattery && level != null) {
                    Text(
                        text = "   " + (if (level.charging) "⚡" else "") + "${level.percent} %",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 17.sp,
                        style = OnWallpaperText,
                    )
                }
            }
        }
        if (settings.showGreeting && !settings.galaxyClaude) {
            Text(
                text = greetingFor(now.hour),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
                style = OnWallpaperText,
            )
        }
    }
}

/** Opens the calendar app (the date chips). */
private fun openCalendar(context: Context) {
    runCatching {
        context.startActivity(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Opens the battery page (the battery chips). */
private fun openBattery(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens the alarms of the clock app (the alarm chip). */
private fun openAlarms(context: Context) {
    runCatching { context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Hearth UI 14's clock: hours over minutes, big and stacked, and under them the date and the
 * battery as little glass chips (a tap opens the calendar or the battery page).
 */
@Composable
private fun StackedClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val battery by rememberBattery()
    val locale = Locale.getDefault()
    val hours = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("HH", locale)), settings.omegaZeros) }
    val minutes = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("mm", locale)), settings.omegaZeros) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Column(modifier) {
        ClockDigits(
            settings,
            text = hours,
            color = Color.White,
            fontFamily = clockFamily(settings, null),
            fontWeight = clockWeight(settings, FontWeight.Bold),
            fontSize = 84.sp,
            lineHeight = 76.sp,
            letterSpacing = (-4).sp,
            style = OnWallpaperText,
        )
        ClockDigits(
            settings,
            text = minutes,
            color = Color.White.copy(alpha = 0.78f),
            fontFamily = clockFamily(settings, null),
            fontWeight = clockWeight(settings, FontWeight.Bold),
            fontSize = 84.sp,
            lineHeight = 76.sp,
            letterSpacing = (-4).sp,
            style = OnWallpaperText,
        )
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCapsule(onClick = { openCalendar(context) }, padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(date, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            val level = battery
            if (settings.showBattery && level != null) {
                GlassCapsule(onClick = { openBattery(context) }, padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(
                        (if (level.charging) "⚡ " else "") + "${level.percent} %",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (settings.showGreeting && !settings.galaxyClaude) {
            Text(
                text = greetingFor(now.hour),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
                style = OnWallpaperText,
            )
        }
    }
}

/** Things to start with Claude straight away, like Galaxy AI's suggestions. */
internal val ClaudePrompts = listOf(
    "Plane meinen Tag" to "Hilf mir, meinen Tag heute zu planen. Frag mich kurz, was ansteht.",
    "Schreib für mich" to "Hilf mir, eine Nachricht zu schreiben. Frag mich zuerst, an wen und worum es geht.",
    "Erklär mir was" to "Erklär mir in zwei Minuten etwas Überraschendes, das ich noch nicht weiß.",
    "Übersetzen" to "Ich brauche eine Übersetzung. Frag mich nach dem Text und der Sprache.",
    "Idee für heute" to "Gib mir eine kleine, konkrete Idee, was ich heute Schönes machen könnte.",
)

/** What Claude says in the Now Brief, by the time of day. */
private fun briefLine(hour: Int): String = when (hour) {
    in 5..10 -> "Wenn du willst, planen wir zusammen deinen Tag."
    in 11..13 -> "Mittagszeit. Brauchst du schnell Hilfe bei etwas?"
    in 14..17 -> "Schönen Nachmittag. Ich bin da, wenn du etwas fragen willst."
    in 18..22 -> "Soll ich dir helfen, den Tag abzuschließen?"
    else -> "Spät geworden. Noch kurz etwas fragen, bevor du schläfst?"
}

/**
 * Galaxy × Claude: One UI's Now Brief, written by Claude – a word for the time of day, the
 * next alarm and the battery at a glance, and things to ask Claude with one tap.
 */
@Composable
private fun NowBriefCard(onAsk: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val battery by rememberBattery()
    val locale = Locale.getDefault()
    val nextAlarm = remember(now.hour, now.minute / 5) {
        context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let { at ->
            java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        }
    }
    // What's worth a glance, each with what a tap on it opens.
    val facts: List<Pair<String, (Context) -> Unit>> = buildList {
        nextAlarm?.let { alarm ->
            val day = if (alarm.toLocalDate() == now.toLocalDate()) "" else if (alarm.toLocalDate() == now.toLocalDate().plusDays(1)) "morgen " else alarm.format(DateTimeFormatter.ofPattern("EEE ", locale))
            add(("⏰ Wecker $day" + alarm.format(DateTimeFormatter.ofPattern("HH:mm", locale))) to ::openAlarms)
        }
        battery?.let { add(((if (it.charging) "⚡ Lädt · " else "🔋 Akku ") + "${it.percent} %") to ::openBattery) }
        add(("📅 " + now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)).replaceFirstChar { it.titlecase(locale) }) to ::openCalendar)
    }
    // OMEGA UI 17.4: much more glass – OMEGA glass (an own tint would cover it), deeper lens,
    // light along the top, OMEGA Fluid drifting inside and lying along the rim.
    val omega = LocalSettings.current.omegaGlass
    val fluidOn = LocalSettings.current.fluidDesign
    LiquidGlass(
        cornerRadius = 30.dp,
        refraction = 26.dp,
        interactive = true,
        tint = if (omega) null else Color(0xFF7C8CFF).copy(alpha = 0.12f),
        fluidEdge = false,
        modifier = modifier
            .fillMaxWidth()
            .aiFluidEdge(30.dp, strength = if (omega) 0.95f else 0.45f, width = 1.5.dp, enabled = fluidOn, flowing = false)
            .clip(RoundedCornerShape(30.dp)),
    ) {
        if (fluidOn) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(30.dp))
                    .fluidGlow(strength = if (omega) 1f else 0.6f),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .glassSheen(30.dp),
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAsk(null) },
            ) {
                Clawd(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("NOW BRIEF", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, style = OnWallpaperText)
                Text("  ·  Claude", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, style = OnWallpaperText)
                Spacer(Modifier.weight(1f))
                if (omega) {
                    OmegaSign(16.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            }
            // Hearth UI 14: the greeting big, then what Claude has to say.
            Text(
                text = greetingFor(now.hour),
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
                style = OnWallpaperText,
            )
            Text(
                text = briefLine(now.hour),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp),
                style = OnWallpaperText,
            )
            // One row of glass chips: at a glance (the next alarm, the battery, today), then
            // things to ask Claude in his colors.
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                facts.forEach { (fact, open) ->
                    GlassCapsule(onClick = { open(context) }, tint = if (omega) null else Color.White.copy(alpha = 0.06f), padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                        Text(fact, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                ClaudePrompts.forEach { (label, prompt) ->
                    GlassCapsule(onClick = { onAsk(prompt) }, tint = Color(0xFFD97757).copy(alpha = 0.16f), padding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
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
            Clawd(Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Frag Clawd",
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
    val time = remember(now, settings.omegaZeros) { omegaDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", locale)), settings.omegaZeros) }
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
                RollingText(
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
    val sweep by animateFloatAsState(
        targetValue = battery.percent / 100f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "batterySweep",
    )
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
                sweepAngle = 360f * sweep,
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

/** Page indicator on a small glass capsule. */
@Composable
private fun PageDots(count: Int, position: () -> Float, modifier: Modifier = Modifier) {
    // Hearth UI 14: the current page's dot stretches to a pill and flows along with the swipe
    // (drawn from the pager's position, so swiping never recomposes it).
    val dot = 6.dp
    val wide = 18.dp
    val gap = 8.dp
    val omega = LocalSettings.current.omegaGlass
    LiquidGlass(cornerRadius = 12.dp, refraction = 6.dp, blur = 10.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        // OMEGA UI 17.5: the Ω leads the page dots.
        if (omega) OmegaSign(12.sp, Modifier.padding(start = 10.dp))
        Canvas(
            Modifier
                .padding(start = if (omega) 7.dp else 11.dp, end = 11.dp, top = 7.dp, bottom = 7.dp)
                .size(width = wide + (dot + gap) * (count - 1), height = dot),
        ) {
            val at = position()
            val h = size.height
            var x = 0f
            for (i in 0 until count) {
                val near = (1f - kotlin.math.abs(at - i)).coerceIn(0f, 1f)
                val w = dot.toPx() + (wide - dot).toPx() * near
                drawRoundRect(
                    Color.White.copy(alpha = 0.4f + 0.55f * near),
                    topLeft = Offset(x, 0f),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                )
                x += w + gap.toPx()
            }
        }
        }
    }
}

/** Glass capsule; swells and glows under the finger, like liquid glass. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    // Galaxy × Claude: the pill asks Claude first, like Galaxy AI's search bar.
    val galaxy = LocalSettings.current.galaxyClaude
    // OMEGA UI 17.3: OMEGA glass with OMEGA Fluid flowing round it and drifting inside.
    LiquidGlass(
        cornerRadius = 20.dp,
        refraction = 12.dp,
        interactive = true,
        fluidEdge = false,
        modifier = Modifier
            .searchFluidRim(20.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        SearchFluidFill(20.dp)
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (galaxy) {
                ClawdInBar(22.dp, onTap = onClick)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(if (galaxy) "Frag Clawd oder suche" else "Suchen", color = Color.White, fontSize = 14.sp, style = OnWallpaperText)
            // OMEGA UI 17.5: and the Ω at its end.
            if (LocalSettings.current.omegaGlass) {
                Spacer(Modifier.width(8.dp))
                OmegaSign(15.sp)
            }
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
private fun SelectionBar(
    visible: Boolean,
    count: Int,
    onHide: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onDone: () -> Unit,
) {
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
                        text = if (count == 0) "Apps antippen" else "$count ausgewählt",
                        maxLines = 1,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        GlassChip("Vom Home", onClick = onRemoveFromHome)
                        Spacer(Modifier.width(6.dp))
                        GlassChip("Ausblenden", onClick = onHide)
                        Spacer(Modifier.width(6.dp))
                    }
                    GlassChip("Fertig", onClick = onDone)
                }
            }
        }
    }
}

/** Long press on a home screen widget: resize it, send it to another page, or remove it. */
private fun widgetMenu(
    widget: HomeWidget,
    bounds: Rect,
    pageCount: Int,
    tryPlace: (HomeWidget) -> Boolean,
    findSlot: (Int) -> HomeWidget?,
    onRemove: () -> Unit,
    onPickPhotos: (() -> Unit)?,
): GlassMenuRequest {
    val items = buildList {
        if (onPickPhotos != null) add(GlassMenuItem("Fotos auswählen", Icons.Rounded.Edit, onClick = onPickPhotos))
        add(GlassMenuItem("Breiter", Icons.AutoMirrored.Rounded.KeyboardArrowRight) {
            tryPlace(widget.copy(spanX = widget.spanX + 1)) || tryPlace(widget.copy(col = widget.col - 1, spanX = widget.spanX + 1))
        })
        if (widget.spanX > 1) {
            add(GlassMenuItem("Schmaler", Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { tryPlace(widget.copy(spanX = widget.spanX - 1)) })
        }
        add(GlassMenuItem("Höher", Icons.Rounded.KeyboardArrowDown) {
            tryPlace(widget.copy(spanY = widget.spanY + 1)) || tryPlace(widget.copy(row = widget.row - 1, spanY = widget.spanY + 1))
        })
        if (widget.spanY > 1) {
            add(GlassMenuItem("Niedriger", Icons.Rounded.KeyboardArrowUp) { tryPlace(widget.copy(spanY = widget.spanY - 1)) })
        }
        if (widget.page > 0) {
            add(GlassMenuItem("Auf vorige Seite", Icons.AutoMirrored.Rounded.KeyboardArrowLeft) {
                findSlot(widget.page - 1)?.let { tryPlace(it.copy(id = widget.id)) }
            })
        }
        add(GlassMenuItem("Auf nächste Seite", Icons.AutoMirrored.Rounded.KeyboardArrowRight) {
            findSlot(widget.page + 1)?.let { tryPlace(it.copy(id = widget.id)) }
        })
        add(GlassMenuItem("Entfernen", Icons.Rounded.Delete, destructive = true, onClick = onRemove))
    }
    return GlassMenuRequest(
        anchor = bounds,
        items = items,
        title = "Widget (Seite ${widget.page + 1}/${pageCount.coerceAtLeast(widget.page + 1)}): halten und ziehen verschiebt",
    )
}

/** An app being dragged on the home screen. */
@Immutable
class AppDrag(val item: HomeItem, val fromDock: Boolean, val grabOffset: Offset, val iconSize: Size)

/** The dragged icon under the finger, and a glass cell where it will land. */
@Composable
private fun DragOverlay(drag: AppDrag, finger: Offset, target: Rect?, overDock: Boolean, dockBounds: Rect) {
    val density = LocalDensity.current
    val highlight = if (overDock) dockBounds else target
    if (highlight != null) {
        val inset = with(density) { 4.dp.toPx() }
        val glow by animateFloatAsState(1f, tween(200), label = "dropGlow")
        Box(
            Modifier
                .offset { IntOffset((highlight.left + inset).roundToInt(), (highlight.top + inset).roundToInt()) }
                .size(
                    with(density) { (highlight.width - 2 * inset).coerceAtLeast(0f).toDp() },
                    with(density) { (highlight.height - 2 * inset).coerceAtLeast(0f).toDp() },
                )
                .graphicsLayer { alpha = glow },
        ) {
            LiquidGlass(
                cornerRadius = 22.dp,
                refraction = 14.dp,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxSize(),
            ) { }
        }
    }
    val topLeft = finger - drag.grabOffset
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .graphicsLayer {
                scaleX = 1.15f
                scaleY = 1.15f
                alpha = 0.95f
            },
    ) {
        val size = with(density) { drag.iconSize.width.toDp() }
        when (val item = drag.item) {
            is AppItem -> AppIconImage(item.app, size)
            is FolderItem -> FolderTile(item, size)
        }
    }
}

/** Small glass dialog to rename a folder. */
@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlass(
            cornerRadius = 28.dp,
            refraction = 18.dp,
            tint = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Ordner umbenennen", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 22.sp)
                Spacer(Modifier.padding(top = 12.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.padding(top = 14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassChip("Abbrechen", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    GlassChip("Fertig") { onConfirm(text) }
                }
            }
        }
    }
}

/** The clock's typeface as chosen ("Wie der Look" keeps each clock's own). */
internal fun clockFamily(settings: LauncherSettings, lookDefault: FontFamily?): FontFamily? = when (settings.clockFont) {
    ClockFont.Default -> lookDefault
    ClockFont.Serif -> FontFamily.Serif
    ClockFont.Mono -> FontFamily.Monospace
    ClockFont.Sans, ClockFont.Thin, ClockFont.Bold -> FontFamily.SansSerif
}

internal fun clockWeight(settings: LauncherSettings, lookDefault: FontWeight): FontWeight = when (settings.clockFont) {
    ClockFont.Thin -> FontWeight.Thin
    ClockFont.Bold -> FontWeight.Bold
    ClockFont.Sans, ClockFont.Mono -> FontWeight.Normal
    else -> lookDefault
}


/**
 * OMEGA UI 18.5, "Uhren aus Glas": a clock's digits – of glass when that's on, else the plain
 * rolling ones, with the same look otherwise.
 */
@Composable
private fun ClockDigits(
    settings: LauncherSettings,
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
) {
    if (settings.glassClocks) {
        GlassNumerals(
            text = text,
            fontSize = fontSize,
            modifier = modifier,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        )
    } else {
        RollingText(
            text = text,
            color = color,
            fontSize = fontSize,
            modifier = modifier,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            style = style,
        )
    }
}
