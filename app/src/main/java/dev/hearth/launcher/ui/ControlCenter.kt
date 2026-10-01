package dev.hearth.launcher.ui

import android.view.KeyEvent
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.ControlState
import dev.hearth.launcher.data.SystemControls
import kotlinx.coroutines.delay
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
    onClose: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
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
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            state = controls.state()
            now = LocalDateTime.now()
            if (System.currentTimeMillis() - lastTouch > 1500) {
                brightness = controls.brightness()
                volume = controls.volume()
            }
        }
    }
    val refresh = { state = controls.state() }
    val locale = Locale.getDefault()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -60.dp.toPx()) onClose() },
                    onVerticalDrag = { _, amount -> total += amount },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: time and date on the left, launcher settings on the right.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
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
            Row(Modifier.fillMaxWidth().height(164.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                MediaCard(controls, accent, Modifier.weight(1f).fillMaxHeight())
            }

            // Toggles + vertical sliders
            Row(Modifier.fillMaxWidth().height(212.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    onValueChange = { v ->
                        lastTouch = System.currentTimeMillis()
                        volume = v
                        controls.setVolume(v)
                    },
                )
            }

            // Shortcuts
            GlassCard(Modifier.fillMaxWidth()) {
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

@Composable
private fun MediaCard(controls: SystemControls, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudeSpark(accent, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Medien", color = OnGlass, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                "Steuert die zuletzt aktive Wiedergabe",
                color = OnGlassDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MediaButton(Glyph.Previous) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                MediaButton(Glyph.Play, big = true) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                MediaButton(Glyph.Next) { controls.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
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
    onValueChange: (Float) -> Unit,
) {
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
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
                detectTapGestures { offset -> onChange((1f - offset.y / size.height).coerceIn(0f, 1f)) }
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
