package dev.hearth.launcher.ui

import android.media.AudioManager
import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import dev.hearth.launcher.data.ControlState
import kotlin.math.abs
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import dev.hearth.launcher.data.OutputKind
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.ContentScale
import dev.hearth.launcher.data.MediaRepository
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.SystemControls
import dev.hearth.launcher.ui.theme.HearthTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.luminance
import dev.hearth.launcher.data.CcColorMode
import dev.hearth.launcher.data.CcSliderStyle
import dev.hearth.launcher.data.CcToggleShape
import dev.hearth.launcher.data.NoticeAction
import dev.hearth.launcher.data.NotificationHub
import dev.hearth.launcher.data.ShadeNotice
import java.time.Instant
import java.time.ZoneId

private val OnGlass = Color.White
private val OnGlassDim = Color.White.copy(alpha = 0.7f)



/**
 * How far the overlay control center is pulled out, shared between the service (which sees
 * the finger on the status bar) and the panel. While [dragging], the panel sits exactly at
 * [progress]; on release it springs to [target] (1 = open, 0 = closed and gone).
 */
class PanelReveal {
    var progress by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var target by mutableFloatStateOf(1f)

    /** Speed of the finger on release, in panel heights per second. */
    var flingVelocity = 0f

    /** Page to open on: 0 = switches, 1 = notifications (pulled from the left side). */
    var startPage = 0

    fun close() {
        flingVelocity = 0f
        dragging = false
        target = 0f
    }
}

/**
 * Rows unfold from under the status bar one after another as the panel comes out,
 * like on ColorOS. Only reads [reveal] while drawing, so pulling never recomposes.
 */
private fun Modifier.unfold(index: Int, reveal: () -> Float): Modifier = graphicsLayer {
    val p = reveal()
    val rest = 1f - p.coerceAtMost(1f)
    translationY = -rest * (36.dp.toPx() + index * 26.dp.toPx()) + (p - 1f).coerceAtLeast(0f) * 60.dp.toPx()
    val s = 0.9f + 0.1f * p.coerceIn(0f, 1f)
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, 0f)
    alpha = ((p - index * 0.05f) / 0.6f).coerceIn(0f, 1f)
}

/** One switch in the control center, described once and placed by the chosen layout. */
private class ToggleSpec(
    val glyph: Glyph,
    val label: String,
    val active: Boolean,
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * The launcher's own control center, in the style of ColorOS 17, made of liquid glass:
 * big connectivity tiles, the media card, round toggles that glow while on, and sliders
 * for brightness and volume. Swipe right for the notifications. The look is set in the
 * settings ("Kontrollzentrum: Aussehen").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlCenter(
    controls: SystemControls,
    media: MediaRepository,
    onClose: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onShowNotifications: (() -> Unit)? = null,
    onShowSystemQuickSettings: (() -> Unit)? = null,
    scrim: Color = Color.Black.copy(alpha = 0.28f),
    /** Explicit room for status and navigation bar; overlay windows don't always get insets. */
    barPadding: PaddingValues? = null,
    /** How far the panel is out (0..1), read while drawing; 1 when it simply appears. */
    reveal: () -> Float = { 1f },
    /** 0 = switches, 1 = notifications. */
    initialPage: Int = 0,
) {
    val settings = LocalSettings.current
    val accent = settings.accent.color
    // Starts with the last known state (no toggles flickering on), refreshed off the main thread.
    var state by remember { mutableStateOf(controls.lastState ?: ControlState()) }
    LaunchedEffect(Unit) { state = withContext(Dispatchers.Default) { controls.state() } }

    // Whatever opens a system screen or app from here closes the control center,
    // otherwise it would stay on top of what just opened.
    val closeNow by rememberUpdatedState(onClose)
    DisposableEffect(controls) {
        val hook: () -> Unit = { closeNow() }
        controls.onOpenedScreen = hook
        onDispose { if (controls.onOpenedScreen === hook) controls.onOpenedScreen = null }
    }
    val torch by controls.torchOn.collectAsStateWithLifecycle()
    var brightness by remember { mutableFloatStateOf(controls.brightness()) }
    var volume by remember { mutableFloatStateOf(controls.volume()) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var askedForPermission by remember { mutableStateOf(false) }

    // Keep toggles and sliders in sync with changes made elsewhere (hardware keys, system panels).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val fresh = withContext(Dispatchers.Default) { controls.state() }
            state = fresh
            now = LocalDateTime.now()
            if (System.currentTimeMillis() - lastTouch > 1500) {
                val (b, v) = withContext(Dispatchers.Default) { controls.brightness() to controls.volume() }
                brightness = b
                volume = v
            }
        }
    }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { state = withContext(Dispatchers.Default) { controls.state() } } }
    val locale = Locale.getDefault()
    var audioOpen by remember { mutableStateOf(false) }

    val pageCount = if (settings.ccNotifications) 2 else 1
    // The notifications sit left of the switches: swipe right to see them.
    val notificationIndex = 0
    val controlsIndex = if (settings.ccNotifications) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = if (initialPage == 1 && settings.ccNotifications) notificationIndex else controlsIndex,
    ) { pageCount }
    val showNotificationPage: () -> Unit = { scope.launch { pagerState.animateScrollToPage(notificationIndex) } }

    // Scrolls when it doesn't fit; pulling up past the end closes it.
    val density = LocalDensity.current
    val closeAfter = with(density) { 90.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            var flinging = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!flinging && available.y < 0f) {
                    pulled -= available.y
                    if (pulled > closeAfter) {
                        pulled = 0f
                        close()
                    }
                } else if (available.y > 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                flinging = true
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                flinging = false
                return Velocity.Zero
            }
        }
    }

    // All switches, described once; the chosen look decides where they go.
    val wifi = ToggleSpec(Glyph.Wifi, "WLAN", state.wifi, onClick = controls::openWifi)
    val mobile = ToggleSpec(Glyph.Cellular, "Mobile Daten", state.mobileData, onClick = controls::openInternet)
    val quadA = listOf(
        ToggleSpec(Glyph.Bluetooth, "Bluetooth", state.bluetooth, onClick = controls::openBluetooth),
        ToggleSpec(Glyph.Airplane, "Flugmodus", state.airplane, onClick = controls::openAirplane),
        ToggleSpec(Glyph.Torch, "Taschenlampe", torch, enabled = controls.hasTorch, onClick = { controls.setTorch(!torch) }),
        ToggleSpec(Glyph.Location, "Standort", state.location, onClick = controls::openLocation),
    )
    val quadB = listOf(
        ToggleSpec(Glyph.Moon, "Nicht stören", state.doNotDisturb, onClick = {
            controls.toggleDoNotDisturb()
            refresh()
        }),
        ToggleSpec(Glyph.Vibrate, "Vibration", state.vibrate, onLongClick = { audioOpen = true }, onClick = {
            controls.toggleVibrate()
            refresh()
        }),
        ToggleSpec(Glyph.Rotate, "Drehen", state.autoRotate, onClick = {
            controls.setAutoRotate(!state.autoRotate)
            refresh()
        }),
        ToggleSpec(Glyph.AutoSun, "Auto-Hell", state.autoBrightness, onLongClick = { controls.openDisplay() }, onClick = {
            controls.setAutoBrightness(!state.autoBrightness)
            refresh()
        }),
    )
    val extras = buildList {
        if (!settings.ccBigTiles) {
            add(wifi)
            add(mobile)
        }
        add(ToggleSpec(Glyph.Battery, "Energie sparen", state.batterySaver, onClick = controls::openBatterySaver))
        add(ToggleSpec(Glyph.Hotspot, "Hotspot", false, onClick = controls::openHotspot))
        add(ToggleSpec(Glyph.Nfc, "NFC", state.nfc, onClick = controls::openNfc))
        add(ToggleSpec(Glyph.Contrast, "Dunkel", state.darkMode, onClick = controls::openDisplay))
        add(ToggleSpec(Glyph.Cast, "Smart View", false, onClick = controls::openCast))
        add(ToggleSpec(Glyph.Screenshot, "Screenshot", false, onClick = {
            onClose()
            controls.takeScreenshot()
        }))
        add(ToggleSpec(Glyph.Lock, "Sperren", false, onClick = {
            onClose()
            controls.lockScreen()
        }))
        add(ToggleSpec(Glyph.Power, "Ein/Aus", false, onClick = {
            onClose()
            controls.powerMenu()
        }))
    }

    val onBrightness: (Float) -> Unit = { v ->
        lastTouch = System.currentTimeMillis()
        brightness = v
        if (controls.setBrightness(v)) {
            if (state.autoBrightness) state = state.copy(autoBrightness = false)
        } else if (!state.canWriteSettings && !askedForPermission) {
            askedForPermission = true
            controls.requestWriteSettings()
        }
    }
    val onVolume: (Float) -> Unit = { v ->
        lastTouch = System.currentTimeMillis()
        volume = v
        controls.setVolume(v)
    }
    val tall = settings.ccSliders == CcSliderStyle.Tall
    val dim = 0.4f + 1.2f * settings.ccDim.coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = (scrim.alpha * dim).coerceIn(0f, 0.95f) * reveal().coerceIn(0f, 1f)
                drawRect(scrim.copy(alpha = alpha))
            }
            // Tap or swipe up on the empty area: close.
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        dragged += amount
                        if (dragged < -closeAfter / 2) {
                            change.consume()
                            dragged = 0f
                            onClose()
                        }
                    },
                )
            }
            .nestedScroll(pullToClose),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (settings.ccNotifications && page == notificationIndex) {
                NotificationPage(
                    barPadding = barPadding,
                    reveal = reveal,
                    onClose = onClose,
                    onShowSystemNotifications = onShowNotifications,
                    onRequestAccess = {
                        media.requestAccess()
                        onClose()
                    },
                )
                return@HorizontalPager
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(if (barPadding != null) Modifier.padding(barPadding) else Modifier.systemBarsPadding())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header: date over a big light clock, like ColorOS; settings on the right.
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.unfold(0, reveal).padding(horizontal = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        if (settings.ccShowClock) {
                            Text(
                                text = now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)),
                                color = OnGlassDim,
                                fontSize = 14.sp,
                                style = OnWallpaperText,
                            )
                            Text(
                                text = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                                color = OnGlass,
                                fontSize = 50.sp,
                                fontWeight = FontWeight.Light,
                                style = OnWallpaperText,
                            )
                        }
                    }
                    if (onShowSystemQuickSettings != null) {
                        GlassCircleButton(size = 40.dp, onClick = onShowSystemQuickSettings) {
                            GlyphIcon(Glyph.Tiles, OnGlass, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    GlassCircleButton(size = 40.dp, onClick = onOpenLauncherSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Launcher-Einstellungen", tint = OnGlass, modifier = Modifier.size(22.dp))
                    }
                }

                if (!state.canWriteSettings) {
                    GlassCard(
                        modifier = Modifier
                            .unfold(0, reveal)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { controls.requestWriteSettings() },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Sun, accent, Modifier.size(26.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Helligkeit freischalten", color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Tippen und „Systemeinstellungen ändern“ erlauben, damit Helligkeit und Drehung hier gehen.",
                                    color = OnGlassDim,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // Two big tiles: WLAN and mobile data.
                if (settings.ccBigTiles) {
                    Row(Modifier.unfold(1, reveal).fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigTile(
                            spec = wifi,
                            status = if (state.wifi) "An" else "Aus",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        BigTile(
                            spec = mobile,
                            status = if (state.airplane) "Flugmodus" else if (state.mobileData) "Verbunden" else "Aus",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // Media card next to four switches (or the switches alone).
                if (settings.ccShowMedia) {
                    Row(Modifier.unfold(2, reveal).fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaCard(media, controls, accent, onClose, Modifier.weight(1f).fillMaxHeight())
                        ToggleCard(quadA, columns = 2, modifier = Modifier.weight(1f).fillMaxHeight(), fill = true)
                    }
                } else {
                    ToggleCard(quadA, columns = 4, modifier = Modifier.unfold(2, reveal).fillMaxWidth())
                }

                // Four more switches and the sliders: tall beside them, or wide below.
                if (tall) {
                    Row(Modifier.unfold(3, reveal).fillMaxWidth().height(196.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleCard(quadB, columns = 2, modifier = Modifier.weight(1f).fillMaxHeight(), fill = true)
                        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassVerticalSlider(
                                value = brightness,
                                glyph = Glyph.Sun,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onLongPress = { controls.openDisplay() },
                                onValueChange = onBrightness,
                            )
                            GlassVerticalSlider(
                                value = volume,
                                glyph = Glyph.Speaker,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                // Hold for all volumes, the output device and the earbuds.
                                onLongPress = { audioOpen = true },
                                onValueChange = onVolume,
                            )
                        }
                    }
                } else {
                    ToggleCard(quadB, columns = 4, modifier = Modifier.unfold(3, reveal).fillMaxWidth())
                    Column(Modifier.unfold(3, reveal).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassVerticalSlider(
                            value = brightness,
                            glyph = Glyph.Sun,
                            vertical = false,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            onLongPress = { controls.openDisplay() },
                            onValueChange = onBrightness,
                        )
                        GlassVerticalSlider(
                            value = volume,
                            glyph = Glyph.Speaker,
                            vertical = false,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            onLongPress = { audioOpen = true },
                            onValueChange = onVolume,
                        )
                    }
                }

                // Everything else, four to a row, like the lower part of the ColorOS panel.
                ToggleCard(extras, columns = 4, modifier = Modifier.unfold(4, reveal).fillMaxWidth())

                if (settings.ccShowShortcuts) {
                    Row(
                        Modifier
                            .unfold(5, reveal)
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Shortcut(Glyph.Camera, "Kamera") { controls.openCamera(); onClose() }
                        Shortcut(Glyph.Alarm, "Wecker") { controls.openAlarms(); onClose() }
                        Shortcut(Glyph.Calculator, "Rechner") { controls.openCalculator(); onClose() }
                        Shortcut(Glyph.Spark, "Claude", tint = accent) { controls.openClaude(); onClose() }
                    }
                }

                // Notifications: Hearth's own page next door, or the system's shade.
                if (settings.ccNotifications) {
                    GlassPill(Glyph.Bell, "Mitteilungen", Modifier.unfold(6, reveal).fillMaxWidth(), showNotificationPage)
                } else if (onShowNotifications != null) {
                    GlassPill(Glyph.Bell, "Mitteilungen anzeigen", Modifier.unfold(6, reveal).fillMaxWidth(), onShowNotifications)
                }

                // A small Claude-flavored footer, and the handle to push it back up.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClaudeSpark(accent, Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (settings.ccNotifications) "Nach rechts: Mitteilungen · nach oben: schließen" else "Nach oben wischen zum Schließen",
                            color = OnGlassDim,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            style = OnWallpaperText,
                        )
                    }
                }
            }
        }

        // Which page: switches or notifications.
        if (pageCount > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (barPadding != null) {
                            Modifier.padding(bottom = barPadding.calculateBottomPadding())
                        } else {
                            Modifier.navigationBarsPadding()
                        },
                    )
                    .padding(bottom = 10.dp)
                    .graphicsLayer { alpha = reveal().coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        Modifier
                            .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 0.9f else 0.4f)),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = audioOpen,
            enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 500f), initialScale = 0.9f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.95f),
        ) {
            AudioSheet(controls, onClose = { audioOpen = false })
        }
    }
}

/** Hearth's own notification list, one swipe right from the switches. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationPage(
    barPadding: PaddingValues?,
    reveal: () -> Float,
    onClose: () -> Unit,
    onShowSystemNotifications: (() -> Unit)?,
    onRequestAccess: () -> Unit,
) {
    val context = LocalContext.current
    val connected by NotificationHub.connected.collectAsStateWithLifecycle()
    val notices by NotificationHub.all.collectAsStateWithLifecycle()
    val accent = LocalSettings.current.accent.color
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .then(if (barPadding != null) Modifier.padding(barPadding) else Modifier.systemBarsPadding())
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.unfold(0, reveal).padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !connected -> "Kein Zugriff"
                            notices.isEmpty() -> "Keine neuen"
                            notices.size == 1 -> "1 Mitteilung"
                            else -> "${notices.size} Mitteilungen"
                        },
                        color = OnGlassDim,
                        fontSize = 14.sp,
                        style = OnWallpaperText,
                    )
                    Text("Mitteilungen", color = OnGlass, fontSize = 34.sp, fontWeight = FontWeight.Light, style = OnWallpaperText)
                }
                if (notices.any { it.clearable }) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { NotificationHub.dismissAll() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Alle löschen", color = OnGlass, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (!connected) {
            item(key = "access") {
                GlassCard(
                    Modifier
                        .unfold(1, reveal)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onRequestAccess),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Bell, accent, Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Mitteilungen hier zeigen", color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Tippen und Hearth den „Benachrichtigungszugriff“ erlauben.",
                                color = OnGlassDim,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        } else if (notices.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .unfold(1, reveal)
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ClaudeSpark(accent, Modifier.size(30.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Alles erledigt",
                        color = OnGlass,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        style = OnWallpaperText,
                    )
                }
            }
        }

        items(notices, key = { it.key }) { notice ->
            NotificationCard(
                notice = notice,
                now = now,
                modifier = Modifier.animateItem().unfold(1, reveal),
                onOpen = { if (NotificationHub.open(context, notice)) onClose() },
                onAction = { action ->
                    NotificationHub.send(context, action.intent)
                    onClose()
                },
                onDismiss = { NotificationHub.dismiss(notice.key) },
            )
        }

        if (onShowSystemNotifications != null) {
            item(key = "system") {
                GlassPill(Glyph.Tiles, "System-Mitteilungen öffnen", Modifier.fillMaxWidth(), onShowSystemNotifications)
            }
        }
    }
}

private fun noticeTime(time: Long, now: Long): String {
    val diff = now - time
    val zoned = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    return when {
        diff < 60_000L -> "jetzt"
        diff < 3_600_000L -> "vor ${diff / 60_000L} Min."
        diff < 86_400_000L -> zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> zoned.format(DateTimeFormatter.ofPattern("d.M., HH:mm"))
    }
}

/**
 * One notification on glass: tap opens it, swipe right removes it (a swipe to the left
 * belongs to the page and goes back to the switches).
 */
@Composable
private fun NotificationCard(
    notice: ShadeNotice,
    now: Long,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onAction: (NoticeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var expanded by remember { mutableStateOf(false) }
    LiquidGlass(
        cornerRadius = 24.dp,
        refraction = 16.dp,
        blur = 18.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offset.value
                alpha = (1f - abs(offset.value) / (size.width.coerceAtLeast(1f) * 0.9f)).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(notice.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    var totalX = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - change.previousPosition.x
                        if (!dragging) {
                            totalX += dx
                            val dy = abs(change.position.y - down.position.y)
                            val slop = viewConfiguration.touchSlop
                            when {
                                dy > slop && dy > abs(totalX) -> break // scrolling the list
                                totalX > slop -> dragging = true
                                totalX < -slop -> break // back to the switches
                            }
                        }
                        if (dragging) {
                            change.consume()
                            val target = (offset.value + dx).coerceAtLeast(0f)
                            scope.launch { offset.snapTo(target) }
                        }
                    }
                    if (dragging) {
                        val width = size.width.toFloat()
                        scope.launch {
                            if (notice.clearable && offset.value > width * 0.33f) {
                                offset.animateTo(width * 1.2f, tween(180))
                                dismiss()
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                            }
                        }
                    }
                }
            }
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = notice.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    GlyphIcon(Glyph.Bell, OnGlass, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notice.appLabel,
                    color = OnGlassDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(noticeTime(notice.time, now), color = OnGlassDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            if (notice.title.isNotBlank()) {
                Text(
                    text = notice.title,
                    color = OnGlass,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notice.text.isNotBlank()) {
                Text(
                    text = notice.text,
                    color = OnGlass.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = if (expanded) 12 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = notice.text.length > 120) { expanded = !expanded },
                )
            }
            if (notice.actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    notice.actions.forEach { action ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { onAction(action) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(action.title, color = OnGlass, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Glass card that swallows taps, so tapping inside doesn't close the control center. */
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LiquidGlass(
        cornerRadius = 28.dp,
        refraction = 20.dp,
        blur = 22.dp,
        // The swallowing comes first (outermost): a click set on the card itself still wins,
        // only taps nothing inside wanted don't fall through and close everything.
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }.then(modifier),
    ) {
        content()
    }
}

@Composable
private fun GlassCircleButton(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    LiquidGlass(
        cornerRadius = size / 2,
        refraction = size * 0.25f,
        interactive = true,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** The soft colored glow around a switch that is on (ColorOS 17's lit contour). */
private fun Modifier.onGlow(active: Boolean, color: Color, cornerRadius: Dp? = null): Modifier =
    if (!active) {
        this
    } else {
        drawBehind {
            val spread = 7.dp.toPx()
            if (cornerRadius == null) {
                val radius = size.minDimension / 2f + spread
                drawCircle(
                    brush = Brush.radialGradient(
                        0.55f to color.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            } else {
                drawRoundRect(
                    color = color.copy(alpha = 0.28f),
                    topLeft = Offset(-spread / 2f, -spread / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width + spread, size.height + spread),
                    cornerRadius = CornerRadius(cornerRadius.toPx() + spread / 2f),
                )
            }
        }
    }

/** Glass card with switches in rows of [columns]; [fill] spreads the rows over its height. */
@Composable
private fun ToggleCard(specs: List<ToggleSpec>, columns: Int, modifier: Modifier = Modifier, fill: Boolean = false) {
    GlassCard(modifier) {
        Column(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(vertical = if (fill) 6.dp else 12.dp),
            verticalArrangement = if (fill) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp),
        ) {
            specs.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { ControlToggle(it) }
                    repeat(columns - row.size) { Spacer(Modifier.width(ToggleWidth)) }
                }
            }
        }
    }
}

private val ToggleWidth = 70.dp

/** ColorOS 16's multicolor switches: every switch has its own color while on. */
private fun multicolor(glyph: Glyph): Color? = when (glyph) {
    Glyph.Wifi -> Color(0xFF3B82F6)
    Glyph.Cellular -> Color(0xFF22C55E)
    Glyph.Bluetooth -> Color(0xFF2563EB)
    Glyph.Airplane -> Color(0xFFF59E0B)
    Glyph.Torch -> Color(0xFFFACC15)
    Glyph.Location -> Color(0xFF14B8A6)
    Glyph.Moon -> Color(0xFF8B5CF6)
    Glyph.Vibrate -> Color(0xFFEC4899)
    Glyph.Rotate -> Color(0xFF06B6D4)
    Glyph.AutoSun -> Color(0xFFF97316)
    Glyph.Battery -> Color(0xFF84CC16)
    Glyph.Hotspot -> Color(0xFF0EA5E9)
    Glyph.Nfc -> Color(0xFF6366F1)
    Glyph.Contrast -> Color(0xFF64748B)
    Glyph.Cast -> Color(0xFFA855F7)
    else -> null
}

/** The color a switch shows while on, as chosen under "Kontrollzentrum: Aussehen". */
private fun onColor(glyph: Glyph, accent: Color, mode: CcColorMode): Color = when (mode) {
    CcColorMode.Accent -> accent
    CcColorMode.White -> Color.White
    CcColorMode.Multicolor -> multicolor(glyph) ?: accent
}

/** Dark icons on light colors (white mode, yellow torch), white ones otherwise. */
private fun iconOn(color: Color): Color = if (color.luminance() > 0.6f) Color(0xFF1F1E1D) else OnGlass

/** Round glass switch; filled with its color and glowing while on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlToggle(spec: ToggleSpec) {
    val settings = LocalSettings.current
    val color = onColor(spec.glyph, settings.accent.color, settings.ccColors)
    val corner = if (settings.ccShape == CcToggleShape.Circle) 26.dp else 17.dp
    val shape = RoundedCornerShape(corner)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(ToggleWidth)) {
        LiquidGlass(
            cornerRadius = corner,
            refraction = 14.dp,
            blur = 10.dp,
            interactive = true,
            tint = if (spec.active) color.copy(alpha = 0.92f) else null,
            modifier = Modifier
                .onGlow(spec.active && settings.ccGlow, color, cornerRadius = if (settings.ccShape == CcToggleShape.Circle) null else corner)
                .size(52.dp)
                .clip(shape)
                .combinedClickable(enabled = spec.enabled, onClick = spec.onClick, onLongClick = spec.onLongClick),
        ) {
            GlyphIcon(
                glyph = spec.glyph,
                tint = when {
                    !spec.enabled -> OnGlassDim
                    spec.active -> iconOn(color)
                    else -> OnGlass
                },
                modifier = Modifier.align(Alignment.Center).size(24.dp),
            )
        }
        if (settings.ccLabels) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = spec.label,
                color = if (spec.active) OnGlass else OnGlassDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = OnWallpaperText,
            )
        }
    }
}

/** Wide ColorOS tile: icon in a circle, name and state; lit with its color while on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigTile(
    spec: ToggleSpec,
    status: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val color = onColor(spec.glyph, settings.accent.color, settings.ccColors)
    val active = spec.active
    LiquidGlass(
        cornerRadius = 26.dp,
        refraction = 18.dp,
        blur = 18.dp,
        interactive = true,
        tint = if (active) color.copy(alpha = 0.32f) else null,
        modifier = modifier
            .onGlow(active && settings.ccGlow, color, cornerRadius = 26.dp)
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (active && settings.ccGlow) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(color.copy(alpha = 0.9f), Color.White.copy(alpha = 0.25f), color.copy(alpha = 0.6f))),
                        shape = RoundedCornerShape(26.dp),
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = spec.onClick, onLongClick = spec.onLongClick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(if (settings.ccShape == CcToggleShape.Circle) CircleShape else RoundedCornerShape(14.dp))
                    .background(if (active) color else Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(spec.glyph, if (active) iconOn(color) else OnGlass, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.label, color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = OnGlassDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Shortcut(glyph: Glyph, label: String, tint: Color = OnGlass, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircleButton(size = 52.dp, onClick = onClick) {
            GlyphIcon(glyph, tint, Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = OnGlass, fontSize = 11.sp, style = OnWallpaperText)
    }
}

/** What is playing, with cover and progress; the glass takes on the cover's color. */
@Composable
private fun MediaCard(
    media: MediaRepository,
    controls: SystemControls,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(media) {
        media.start()
        onDispose { media.stop() }
    }
    val playing by media.nowPlaying.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(media.hasAccess()) }
    // Picks up the permission when coming back from the system settings.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            val access = withContext(Dispatchers.Default) { media.hasAccess() }
            if (access != hasAccess) {
                hasAccess = access
                media.refreshSessions()
            }
        }
    }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing?.playing) {
        while (playing?.playing == true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val item = playing

    LiquidGlass(
        cornerRadius = 28.dp,
        refraction = 20.dp,
        blur = 22.dp,
        tint = item?.artColor?.copy(alpha = 0.45f),
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            if (item == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClaudeSpark(accent, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Medien", color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = if (hasAccess) "Gerade läuft nichts" else "Tippen, um zu sehen, was gerade läuft",
                    color = OnGlassDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(
                            if (hasAccess) {
                                Modifier
                            } else {
                                Modifier.clickable {
                                    media.requestAccess()
                                    onClose()
                                }
                            },
                        ),
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MediaButton(Glyph.Previous) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                    MediaButton(Glyph.Play, big = true) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                    MediaButton(Glyph.Next) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            media.openPlayer()
                            onClose()
                        },
                ) {
                    val art = item.art
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Speaker, OnGlass, Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifBlank { item.appLabel },
                            color = OnGlass,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.artist.ifBlank { item.appLabel },
                            color = OnGlassDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (item.durationMs > 0) {
                    val fraction = (item.currentPosition(now).toFloat() / item.durationMs).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Color.White.copy(alpha = 0.92f)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MediaButton(Glyph.Previous) { media.previous() }
                    MediaButton(if (item.playing) Glyph.Pause else Glyph.Play, big = true) { media.playPause() }
                    MediaButton(Glyph.Next) { media.next() }
                }
            }
        }
    }
}

@Composable
private fun MediaButton(glyph: Glyph, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (big) 0.22f else 0f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, OnGlass, Modifier.size(if (big) 24.dp else 22.dp))
    }
}

/**
 * Glass slider like on ColorOS and iOS: drag anywhere on it, it fills from the bottom
 * (or, with [vertical] off, from the left like One UI).
 */
@Composable
fun GlassVerticalSlider(
    value: Float,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit,
) {
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val longPress by rememberUpdatedState(onLongPress)
    val level = value.coerceIn(0f, 1f)
    val corner = if (vertical) 28.dp else 22.dp
    LiquidGlass(
        cornerRadius = corner,
        refraction = 22.dp,
        blur = 22.dp,
        interactive = true,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            // One gesture decides: move = drag the level, lift = jump to the tapped spot,
            // hold still = long press. Separate detectors used to fight over the same touch.
            .pointerInput(vertical) {
                fun along(p: Offset) = if (vertical) p.y else p.x
                fun across(p: Offset) = if (vertical) p.x else p.y
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val extent = (if (vertical) size.height else size.width).toFloat().coerceAtLeast(1f)
                    fun levelAt(p: Offset) = (if (vertical) 1f - p.y / extent else p.x / extent).coerceIn(0f, 1f)
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 150) {
                        var result = 0 // 1 = tap, 2 = drag, 3 = moved the other way (not for us)
                        while (result == 0) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            result = when {
                                change == null -> 1
                                !change.pressed -> 1
                                abs(along(change.position) - along(down.position)) > viewConfiguration.touchSlop -> 2
                                abs(across(change.position) - across(down.position)) > viewConfiguration.touchSlop * 2 -> 3
                                else -> 0
                            }
                            // Keep the surrounding scroll view from taking over the slider's touch.
                            if (result == 0 || result == 2) change?.consume()
                        }
                        result
                    }
                    when (outcome) {
                        1 -> onChange(levelAt(down.position))
                        2 -> {
                            // Track the level here: several moves can arrive before the
                            // screen updates, and reading the shown value would lose them.
                            var level = current
                            var last = along(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = along(change.position) - last
                                last = along(change.position)
                                change.consume()
                                level = (if (vertical) level - delta / extent else level + delta / extent).coerceIn(0f, 1f)
                                onChange(level)
                            }
                        }
                        3 -> Unit
                        else -> {
                            longPress?.invoke()
                            // Swallow the rest of this touch.
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                }
            },
    ) {
        val dark = Color(0xFF3D3929)
        if (vertical) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(level)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
            GlyphIcon(
                glyph = glyph,
                tint = if (level > 0.14f) dark else OnGlass,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(26.dp),
            )
            Text(
                text = "${(level * 100).toInt()}",
                color = if (level > 0.9f) dark else OnGlass,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
            GlyphIcon(
                glyph = glyph,
                tint = if (level > 0.12f) dark else OnGlass,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(24.dp),
            )
            Text(
                text = "${(level * 100).toInt()} %",
                color = if (level > 0.88f) dark else OnGlass,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            )
        }
    }
}

@Composable
private fun GlassPill(glyph: Glyph, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LiquidGlass(
        cornerRadius = 24.dp,
        refraction = 14.dp,
        interactive = true,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(glyph, OnGlass, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = OnGlass, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}


/**
 * The control center as its own window over other apps (opened by [dev.hearth.launcher.system.ControlCenterService]).
 * It follows the finger while pulled from the status bar and springs open or back on release.
 * The wallpaper isn't behind it here, so the window blurs the app below instead (Android 12+).
 */
@Composable
fun OverlayControlCenter(
    settings: LauncherSettings,
    controls: SystemControls,
    media: MediaRepository,
    reveal: PanelReveal,
    onClosed: () -> Unit,
    onRevealChanged: (Float) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onShowNotifications: () -> Unit,
    onShowSystemQuickSettings: () -> Unit,
) {
    val context = LocalContext.current
    val blurBehind = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
    val light = rememberGlassLight(settings.glassMotion)
    val shown = remember { Animatable(if (reveal.dragging) reveal.progress else 0f) }
    val closed by rememberUpdatedState(onClosed)
    val revealChanged by rememberUpdatedState(onRevealChanged)
    LaunchedEffect(reveal) {
        snapshotFlow { reveal.dragging to reveal.target }.collectLatest { (dragging, target) ->
            if (dragging) {
                // Glued to the finger.
                snapshotFlow { reveal.progress }.collect { shown.snapTo(it) }
            } else {
                val spec = if (target > 0f) {
                    spring<Float>(dampingRatio = 0.8f, stiffness = 480f)
                } else {
                    spring<Float>(dampingRatio = 1f, stiffness = 900f)
                }
                shown.animateTo(target, spec, initialVelocity = reveal.flingVelocity)
                if (target <= 0f) closed()
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shown.value }.collect { revealChanged(it) }
    }
    // Room for status bar (with the camera) and navigation bar, measured directly.
    val density = LocalDensity.current
    val barPadding = remember(context, density) {
        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        fun bar(name: String): Int {
            val id = context.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) context.resources.getDimensionPixelSize(id) else 0
        }
        with(density) {
            PaddingValues(
                top = (bar("status_bar_height").toDp() + 6.dp).coerceAtLeast(30.dp),
                bottom = bar("navigation_bar_height").toDp() + 4.dp,
            )
        }
    }
    HearthTheme(dark = true) {
        CompositionLocalProvider(
            LocalSettings provides settings,
            LocalGlassStyle provides GlassStyle.from(settings),
            LocalGlassLight provides light,
            LocalBackdrop provides null,
        ) {
            ControlCenter(
                controls = controls,
                media = media,
                onClose = reveal::close,
                onOpenLauncherSettings = onOpenLauncherSettings,
                onShowNotifications = onShowNotifications,
                onShowSystemQuickSettings = onShowSystemQuickSettings,
                scrim = Color.Black.copy(alpha = if (blurBehind) 0.38f else 0.74f),
                barPadding = barPadding,
                reveal = { shown.value },
                initialPage = reveal.startPage,
            )
        }
    }
}

/**
 * Hold the volume slider: every volume, ring mode, where the sound goes,
 * and the way to the earbuds' noise cancelling.
 */
@Composable
private fun AudioSheet(controls: SystemControls, onClose: () -> Unit) {
    val accent = LocalSettings.current.accent.color
    var output by remember { mutableStateOf(controls.currentOutput()) }
    var ringer by remember { mutableIntStateOf(controls.ringerMode()) }
    val levels = remember { mutableStateMapOf<Int, Float>() }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val headphoneApp = remember(output) { controls.headphoneApp() }
    val spatial = remember(output) { controls.spatialAudio() }

    LaunchedEffect(Unit) {
        while (true) {
            val fresh = withContext(Dispatchers.Default) {
                Triple(controls.currentOutput(), controls.ringerMode(), controls.streams.associate { it.stream to controls.streamLevel(it.stream) })
            }
            output = fresh.first
            ringer = fresh.second
            if (System.currentTimeMillis() - lastTouch > 1500) levels.putAll(fresh.third)
            delay(1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlass(
            cornerRadius = 32.dp,
            refraction = 22.dp,
            tint = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Where the sound goes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            if (output.kind == OutputKind.Speaker) Glyph.Speaker else Glyph.Headphones,
                            Color.White,
                            Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(output.name, color = OnGlass, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(output.kind.label, color = OnGlassDim, fontSize = 13.sp)
                    }
                    GlassCircleButton(size = 40.dp, onClick = onClose) {
                        Text("✕", color = OnGlass, fontSize = 16.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SheetChip("Ausgabe wechseln", Modifier.weight(1f)) { controls.openMediaOutput() }
                    SheetChip("System-Lautstärke", Modifier.weight(1f)) { controls.openVolumePanel() }
                }

                // Noise cancelling lives in the earbuds' app; Android gives other apps no switch for it.
                if (output.kind != OutputKind.Speaker) {
                    SheetSection("Geräuschunterdrückung") {
                        Text(
                            if (headphoneApp != null) {
                                "Geräuschunterdrückung, Umgebungsklang und Equalizer schaltest du in ${headphoneApp.first}."
                            } else {
                                "Android lässt Apps die Geräuschunterdrückung nicht direkt schalten. Öffne die App deiner Kopfhörer oder die Bluetooth-Einstellungen."
                            },
                            color = OnGlassDim,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        SheetChip(
                            if (headphoneApp != null) "${headphoneApp.first} öffnen" else "Bluetooth-Einstellungen",
                            Modifier.fillMaxWidth(),
                            highlighted = true,
                        ) { controls.openHeadphoneApp() }
                    }
                }

                if (spatial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("3D-Audio", color = OnGlass, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        SheetChip(if (spatial) "An" else "Aus") { controls.openSound() }
                    }
                }

                // Ring mode
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        AudioManager.RINGER_MODE_NORMAL to "Ton",
                        AudioManager.RINGER_MODE_VIBRATE to "Vibration",
                        AudioManager.RINGER_MODE_SILENT to "Lautlos",
                    ).forEach { (mode, label) ->
                        SheetChip(label, Modifier.weight(1f), highlighted = ringer == mode) {
                            controls.setRingerMode(mode)
                            ringer = controls.ringerMode()
                        }
                    }
                }

                // Every volume
                controls.streams.forEach { stream ->
                    Column {
                        Row {
                            Text(stream.label, color = OnGlass, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${((levels[stream.stream] ?: 0f) * 100).toInt()} %", color = OnGlassDim, fontSize = 13.sp)
                        }
                        Slider(
                            value = levels[stream.stream] ?: controls.streamLevel(stream.stream),
                            onValueChange = { v ->
                                lastTouch = System.currentTimeMillis()
                                levels[stream.stream] = v
                                controls.setStreamLevel(stream.stream, v)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.9f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp),
    ) {
        Text(title, color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SheetChip(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    val accent = LocalSettings.current.accent.color
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
