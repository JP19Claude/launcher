package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.DockStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Dock(
    apps: List<AppInfo>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    draggingKey: String? = null,
    /** The macOS dock's last tile ("all apps"): what it opens; null for no tile. */
    onApps: (() -> Unit)? = null,
) {
    if (apps.isEmpty()) return
    val style = LocalSettings.current.dockStyle
    // Very big icons (up to 96 dp) on the home screen; the dock keeps them at a size that fits.
    val dockLook = LocalSettings.current.let { it.copy(iconSize = it.iconSize.coerceAtMost(68)) }
    val iconsSized: @Composable (Int) -> Unit = { iconSize ->
        androidx.compose.runtime.CompositionLocalProvider(LocalSettings provides dockLook.copy(iconSize = iconSize)) {
        apps.forEach { app ->
            key(app.key) {
                AppIcon(
                    app = app,
                    actions = actions,
                    showLabel = false,
                    draggable = true,
                    modifier = Modifier.graphicsLayer { alpha = if (app.key == draggingKey) 0f else 1f },
                )
            }
        }
        }
    }
    when (style) {
        // ZENITH 19: a slanted plate of the Z's dark metal, its ends cut like the Z's bars, green
        // light burning along its foot and pooling underneath.
        DockStyle.Zenith -> {
            val glassHud = LocalGlassStyle.current.hudGlass
            val hud = LocalGlassStyle.current.hud
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            // Projekt Zenith Glas: the dock is a slab of green liquid glass (below).
                            glassHud -> Modifier
                            // Projekt Zenith: the dock as a hologram.
                            hud -> Modifier.drawBehind { drawHudPlate(slant = size.height * 0.36f) }
                            else -> Modifier.drawBehind { drawZenithPlate(slant = size.height * 0.36f, unit = 190.dp.toPx()) }
                        },
                    ),
            ) {
                // Big icons, every one the same size, as big as fits (up to 78 dp): each takes its
                // icon plus 18 dp of cell.
                val inner = maxWidth - 28.dp
                val fit = ((inner / apps.size.coerceAtLeast(1)).value - 20f).toInt()
                val iconSize = fit.coerceIn(36, 78)
                val row: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { iconsSized(iconSize) }
                }
                if (glassHud) {
                    LiquidGlass(cornerRadius = 30.dp, refraction = 22.dp, modifier = Modifier.fillMaxWidth(), fluidEdge = false) { row() }
                } else {
                    row()
                }
            }
        }
        // The other docks: their icons are made to fit – each takes its icon plus 18 dp of cell, and
        // four big ones in a bar this wide would squeeze the last one small.
        else -> androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
            val room = maxWidth
            when (style) {
                // The full-width glass bar.
                DockStyle.Glass -> LiquidGlass(
                    cornerRadius = 34.dp,
                    refraction = 22.dp,
                    interactive = true,
                    // One UI 10 Fluid: Claude's colors round the dock (standing still: it's always on screen).
                    modifier = Modifier.fillMaxWidth().aiFluidEdge(34.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign, flowing = false),
                    fluidEdge = false,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                }
                // A glass capsule just around the icons, floating in the middle.
                DockStyle.Floating -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LiquidGlass(
                        cornerRadius = 30.dp,
                        refraction = 20.dp,
                        interactive = true,
                        modifier = Modifier.aiFluidEdge(30.dp, strength = 0.6f, enabled = LocalSettings.current.fluidDesign, flowing = false),
                        fluidEdge = false,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { iconsSized(fittingIconSize(room - 24.dp, apps.size, 10.dp, dockLook.iconSize)) }
                    }
                }
                // OMEGA UI 17: a dark glass island just around the icons, a ruby glow along its rim.
                DockStyle.Island -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val accent = LocalSettings.current.accent.color
                    LiquidGlass(
                        cornerRadius = 32.dp,
                        refraction = 20.dp,
                        interactive = true,
                        tint = Color(0xFF120A12).copy(alpha = 0.55f),
                        modifier = Modifier
                            .drawBehind {
                                // The glow underneath, as if the island floated on red light.
                                drawRoundRect(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                                        center = Offset(size.width / 2f, size.height),
                                        radius = size.width * 0.6f,
                                    ),
                                    topLeft = Offset(-12.dp.toPx(), 0f),
                                    size = Size(size.width + 24.dp.toPx(), size.height + 18.dp.toPx()),
                                    cornerRadius = CornerRadius(40.dp.toPx()),
                                )
                            }
                            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0.8f), accent.copy(alpha = 0.2f))), RoundedCornerShape(32.dp)),
                        fluidEdge = false,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { iconsSized(fittingIconSize(room - 28.dp, apps.size, 12.dp, dockLook.iconSize)) }
                    }
                }
                // OMEGA UI 17: the full bar, tinted ruby.
                DockStyle.Ruby -> LiquidGlass(
                    cornerRadius = 36.dp,
                    refraction = 22.dp,
                    interactive = true,
                    tint = Color(0xFFB0102C).copy(alpha = 0.28f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFF8A98).copy(alpha = 0.7f), Color(0xFFB0102C).copy(alpha = 0.2f))), RoundedCornerShape(36.dp)),
                    fluidEdge = false,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                }
                // Just the icons, as One UI does it.
                DockStyle.Clear -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { iconsSized(fittingIconSize(room - 12.dp, apps.size, 0.dp, dockLook.iconSize)) }
                DockStyle.Mac -> MacDock(room, apps, actions, dockLook, draggingKey, onApps)
                DockStyle.Zenith -> Unit
            }
        }
    }
}

/**
 * ZENITH 22: the dock of macOS. A shelf of liquid glass with a bright rim; the icons stand on it and swell as the pointer (a mouse, a
 * finger drawn along it) passes over them – the one under it the most, the next ones a little less, the others making room –; a thin
 * divider, then a tile for all apps. The magnifying is done in the icons' layers, so nothing is laid out again while the pointer moves.
 */
@Composable
private fun MacDock(
    room: Dp,
    apps: List<AppInfo>,
    actions: AppActions,
    look: dev.hearth.launcher.data.LauncherSettings,
    draggingKey: String?,
    onApps: (() -> Unit)?,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val n = apps.size
    val tile = onApps != null
    val gap = 4.dp
    val iconSize = fittingIconSize(room - 44.dp, n + if (tile) 1 else 0, gap, look.iconSize.coerceIn(56, 72))
    val cellPx = with(density) { (iconSize.dp + 18.dp).toPx() }
    val gapPx = with(density) { gap.toPx() }
    val dividerPx = with(density) { 16.dp.toPx() }
    val padPx = with(density) { 14.dp.toPx() }
    val items = n + if (tile) 1 else 0
    // Where the pointer is (along the shelf, from the first icon's left edge) and whether it is over the dock at all.
    var over by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var pointerX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val pull by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (over) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "macDock",
    )

    fun centerOf(k: Int): Float =
        if (k < n) k * (cellPx + gapPx) + cellPx / 2f else n * (cellPx + gapPx) + dividerPx + gapPx + cellPx / 2f

    fun scaleAt(k: Int): Float {
        val d = (pointerX - centerOf(k)) / (cellPx * 1.15f)
        return 1f + 0.7f * pull * kotlin.math.exp(-(d * d))
    }

    /** How far item [k] moves sideways so that the swollen ones push their neighbours aside (the whole row stays centered). */
    fun shiftOf(k: Int): Float {
        var before = 0f
        var total = 0f
        for (j in 0 until items) {
            val e = (scaleAt(j) - 1f) * cellPx
            total += e
            if (j < k) before += e
        }
        return before + (scaleAt(k) - 1f) * cellPx / 2f - total / 2f
    }

    /** The divider sits between the last icon and the tile: it moves with everything before it. */
    fun shiftOfDivider(): Float {
        var before = 0f
        var total = 0f
        for (j in 0 until items) {
            val e = (scaleAt(j) - 1f) * cellPx
            total += e
            if (j < n) before += e
        }
        return before - total / 2f
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Exit, PointerEventType.Release -> over = false
                            else -> {
                                over = true
                                pointerX = change.position.x - padPx
                            }
                        }
                    }
                }
            },
        ) {
            // The shelf.
            LiquidGlass(
                cornerRadius = 26.dp,
                refraction = 16.dp,
                interactive = false,
                tint = Color.White.copy(alpha = 0.10f),
                fluidEdge = false,
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.14f))),
                        RoundedCornerShape(26.dp),
                    ),
            ) {}
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalSettings provides look.copy(iconSize = iconSize)) {
                    apps.forEachIndexed { i, app ->
                        key(app.key) {
                            Box(
                                Modifier.graphicsLayer {
                                    val s = scaleAt(i)
                                    scaleX = s
                                    scaleY = s
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                    translationX = shiftOf(i)
                                },
                            ) {
                                AppIcon(
                                    app = app,
                                    actions = actions,
                                    showLabel = false,
                                    draggable = true,
                                    modifier = Modifier.graphicsLayer { alpha = if (app.key == draggingKey) 0f else 1f },
                                )
                            }
                        }
                    }
                    if (onApps != null) {
                        Box(
                            Modifier
                                .width(16.dp)
                                .height(iconSize.dp)
                                .padding(vertical = 2.dp)
                                .graphicsLayer { translationX = shiftOfDivider() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.35f)),
                            )
                        }
                        Box(
                            Modifier
                                .width(iconSize.dp + 18.dp)
                                .padding(vertical = 6.dp)
                                .graphicsLayer {
                                    val s = scaleAt(n)
                                    scaleX = s
                                    scaleY = s
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                    translationX = shiftOf(n)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            LaunchpadTile(iconSize, onApps)
                        }
                    }
                }
            }
        }
    }
}

/** The macOS dock's "all apps" tile: a light square with a grid of colored dots. */
@Composable
private fun LaunchpadTile(size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.24f).dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF6F8FC), Color(0xFFB4BED2))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val dots = listOf(
            Color(0xFFFF5F57), Color(0xFFFFBD2E), Color(0xFF28C840),
            Color(0xFF3E91FF), Color(0xFF8E6BFF), Color(0xFFFF6FB5),
            Color(0xFF30C2C2), Color(0xFFFF9F0A), Color(0xFF5E6C84),
        )
        androidx.compose.foundation.Canvas(Modifier.size((size * 0.58f).dp)) {
            val step = this.size.width / 3f
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    drawCircle(dots[row * 3 + col], radius = step * 0.3f, center = Offset(step * (col + 0.5f), step * (row + 0.5f)))
                }
            }
        }
    }
}

/**
 * The icon size at which [count] icons – each with its 18 dp of cell – fit in [room], [gap] apart;
 * never more than [wanted], and not below 36 dp.
 */
private fun fittingIconSize(room: Dp, count: Int, gap: Dp, wanted: Int): Int {
    val n = count.coerceAtLeast(1)
    val cell = (room - gap * (n - 1)) / n
    val fit = (cell.value - 18f).toInt()
    return minOf(wanted, maxOf(fit, 36))
}
