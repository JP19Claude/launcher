package dev.hearth.launcher.ui

import android.media.AudioManager
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

private val OnGlass = Color.White
private val OnGlassDim = Color.White.copy(alpha = 0.7f)

/**
 * The launcher's own control center, ColorOS-style, made of liquid glass:
 * connectivity, media, quick toggles, and big vertical sliders for brightness and volume.
 */
@Composable
fun ControlCenter(
    controls: SystemControls,
    media: MediaRepository,
    onClose: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onShowNotifications: (() -> Unit)? = null,
    onShowSystemQuickSettings: (() -> Unit)? = null,
    scrim: Color = Color.Black.copy(alpha = 0.28f),
) {
    val accent = LocalSettings.current.accent.color
    var state by remember { mutableStateOf(controls.state()) }
    val torch by controls.torchOn.collectAsStateWithLifecycle()
    var brightness by remember { mutableFloatStateOf(controls.brightness()) }
    var volume by remember { mutableFloatStateOf(controls.volume()) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var askedForPermission by remember { mutableStateOf(false) }

    // Keep toggles and sliders in sync with changes made elsewhere (hardware keys, system panels).
    // Reading the system state takes several system calls; done off the main thread,
    // so the control center doesn't stutter once a second.
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

    // Scrolls when it doesn't fit; pulling up past the end closes it.
    val density = LocalDensity.current
    val closeAfter = with(density) { 110.dp.toPx() }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .nestedScroll(pullToClose),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: time and date on the left, launcher settings on the right.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.staggeredEntrance(0).padding(horizontal = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                        color = OnGlass,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        style = OnWallpaperText,
                    )
                    Text(
                        text = now.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale)),
                        color = OnGlassDim,
                        fontSize = 15.sp,
                        style = OnWallpaperText,
                    )
                }
                GlassCircleButton(size = 44.dp, onClick = onOpenLauncherSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Launcher-Einstellungen", tint = OnGlass)
                }
            }

            if (!state.canWriteSettings) {
                GlassCard(
                    modifier = Modifier
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

            // Connectivity + media
            Row(Modifier.staggeredEntrance(1).fillMaxWidth().height(164.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ControlToggle(Glyph.Wifi, "WLAN", state.wifi, accent, onClick = controls::openWifi)
                            ControlToggle(Glyph.Cellular, "Mobil", state.mobileData, accent, onClick = controls::openInternet)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ControlToggle(Glyph.Bluetooth, "Bluetooth", state.bluetooth, accent, onClick = controls::openBluetooth)
                            ControlToggle(Glyph.Airplane, "Flugmodus", state.airplane, accent, onClick = controls::openAirplane)
                        }
                    }
                }
                MediaCard(media, controls, accent, Modifier.weight(1f).fillMaxHeight())
            }

            // Toggles + vertical sliders
            Row(Modifier.staggeredEntrance(2).fillMaxWidth().height(212.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ControlToggle(
                                Glyph.Torch, "Lampe", torch, accent,
                                enabled = controls.hasTorch,
                                onClick = { controls.setTorch(!torch) },
                            )
                            ControlToggle(Glyph.Moon, "Nicht stören", state.doNotDisturb, accent, onClick = {
                                controls.toggleDoNotDisturb()
                                refresh()
                            })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ControlToggle(Glyph.Vibrate, "Vibration", state.vibrate, accent, onClick = {
                                controls.toggleVibrate()
                                refresh()
                            }, onLongClick = { controls.openSound() })
                            ControlToggle(Glyph.Rotate, "Drehen", state.autoRotate, accent, onClick = {
                                controls.setAutoRotate(!state.autoRotate)
                                refresh()
                            })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ControlToggle(Glyph.AutoSun, "Auto-Hell", state.autoBrightness, accent, onClick = {
                                controls.setAutoBrightness(!state.autoBrightness)
                                refresh()
                            }, onLongClick = { controls.openDisplay() })
                            ControlToggle(Glyph.Location, "Standort", state.location, accent, onClick = controls::openLocation)
                        }
                    }
                }
                GlassVerticalSlider(
                    value = brightness,
                    glyph = Glyph.Sun,
                    modifier = Modifier.width(78.dp).fillMaxHeight(),
                    onLongPress = { controls.openDisplay() },
                    onValueChange = { v ->
                        lastTouch = System.currentTimeMillis()
                        brightness = v
                        if (controls.setBrightness(v)) {
                            state = state.copy(autoBrightness = false)
                        } else if (!state.canWriteSettings && !askedForPermission) {
                            askedForPermission = true
                            controls.requestWriteSettings()
                        }
                    },
                )
                GlassVerticalSlider(
                    value = volume,
                    glyph = Glyph.Speaker,
                    modifier = Modifier.width(78.dp).fillMaxHeight(),
                    // Hold for all volumes, the output device and the earbuds.
                    onLongPress = { audioOpen = true },
                    onValueChange = { v ->
                        lastTouch = System.currentTimeMillis()
                        volume = v
                        controls.setVolume(v)
                    },
                )
            }

            // More switches, like One UI's quick panel
            GlassCard(Modifier.staggeredEntrance(3).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ControlToggle(Glyph.Screenshot, "Screenshot", false, accent, onClick = {
                            onClose()
                            controls.takeScreenshot()
                        })
                        ControlToggle(Glyph.Lock, "Sperren", false, accent, onClick = {
                            onClose()
                            controls.lockScreen()
                        })
                        ControlToggle(Glyph.Power, "Ein/Aus", false, accent, onClick = {
                            onClose()
                            controls.powerMenu()
                        })
                        ControlToggle(Glyph.Battery, "Energie sparen", state.batterySaver, accent, onClick = controls::openBatterySaver)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ControlToggle(Glyph.Hotspot, "Hotspot", false, accent, onClick = controls::openHotspot)
                        ControlToggle(Glyph.Nfc, "NFC", state.nfc, accent, onClick = controls::openNfc)
                        ControlToggle(Glyph.Cast, "Smart View", false, accent, onClick = controls::openCast)
                        ControlToggle(
                            Glyph.Contrast, "Dunkel", state.darkMode, accent,
                            onClick = controls::openDisplay,
                        )
                    }
                }
            }

            // Shortcuts
            GlassCard(Modifier.staggeredEntrance(4).fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Shortcut(Glyph.Camera, "Kamera") { controls.openCamera(); onClose() }
                    Shortcut(Glyph.Alarm, "Wecker") { controls.openAlarms(); onClose() }
                    Shortcut(Glyph.Calculator, "Rechner") { controls.openCalculator(); onClose() }
                    Shortcut(Glyph.Spark, "Claude", tint = accent) { controls.openClaude(); onClose() }
                }
            }

            // Way out to the system's own panels (notifications, and anything only the system can switch).
            if (onShowNotifications != null || onShowSystemQuickSettings != null) {
                Row(Modifier.staggeredEntrance(4).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onShowNotifications != null) {
                        GlassPill(Glyph.Bell, "Mitteilungen", Modifier.weight(1f), onShowNotifications)
                    }
                    if (onShowSystemQuickSettings != null) {
                        GlassPill(Glyph.Tiles, "System-Schalter", Modifier.weight(1f), onShowSystemQuickSettings)
                    }
                }
            }

            // A small Claude-flavored footer.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClaudeSpark(accent, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Nach oben wischen zum Schließen",
                    color = OnGlassDim,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    style = OnWallpaperText,
                )
            }
        }

        AnimatedVisibility(
            visible = audioOpen,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.72f), initialScale = 0.9f),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.95f),
        ) {
            AudioSheet(controls, onClose = { audioOpen = false })
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
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
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

/** Round glass toggle; filled with the accent color while on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlToggle(
    glyph: Glyph,
    label: String,
    active: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        LiquidGlass(
            cornerRadius = 26.dp,
            refraction = 14.dp,
            blur = 10.dp,
            interactive = true,
            tint = if (active) accent.copy(alpha = 0.88f) else null,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        ) {
            GlyphIcon(
                glyph = glyph,
                tint = if (enabled) OnGlass else OnGlassDim,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = OnGlass,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = OnWallpaperText,
        )
    }
}

@Composable
private fun Shortcut(glyph: Glyph, label: String, tint: Color = OnGlass, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircleButton(size = 54.dp, onClick = onClick) {
            GlyphIcon(glyph, tint, Modifier.size(26.dp))
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
                        .then(if (hasAccess) Modifier else Modifier.clickable { media.requestAccess() }),
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
                        .clickable { media.openPlayer() },
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
 * Tall glass slider like on ColorOS and iOS: drag anywhere on it, it fills from the bottom.
 */
@Composable
fun GlassVerticalSlider(
    value: Float,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit,
) {
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val longPress by rememberUpdatedState(onLongPress)
    val level = value.coerceIn(0f, 1f)
    LiquidGlass(
        cornerRadius = 28.dp,
        refraction = 22.dp,
        blur = 22.dp,
        interactive = true,
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, amount ->
                    change.consume()
                    onChange((current - amount / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { longPress?.invoke() },
                    onTap = { offset -> onChange((1f - offset.y / size.height).coerceIn(0f, 1f)) },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(level)
                .background(Color.White.copy(alpha = 0.9f)),
        )
        GlyphIcon(
            glyph = glyph,
            tint = if (level > 0.14f) Color(0xFF3D3929) else OnGlass,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(26.dp),
        )
        Text(
            text = "${(level * 100).toInt()}",
            color = if (level > 0.9f) Color(0xFF3D3929) else OnGlass,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        )
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
 * The wallpaper isn't behind it here, so the window blurs the app below instead (Android 12+).
 */
@Composable
fun OverlayControlCenter(
    settings: LauncherSettings,
    controls: SystemControls,
    media: MediaRepository,
    onClose: () -> Unit,
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
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    HearthTheme(dark = true) {
        CompositionLocalProvider(
            LocalSettings provides settings,
            LocalGlassStyle provides GlassStyle.from(settings),
            LocalGlassLight provides light,
            LocalBackdrop provides null,
        ) {
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(tween(180)) + slideInVertically(tween(300)) { -it / 6 },
            ) {
                ControlCenter(
                    controls = controls,
                    media = media,
                    onClose = onClose,
                    onOpenLauncherSettings = onOpenLauncherSettings,
                    onShowNotifications = onShowNotifications,
                    onShowSystemQuickSettings = onShowSystemQuickSettings,
                    scrim = Color.Black.copy(alpha = if (blurBehind) 0.35f else 0.72f),
                )
            }
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
