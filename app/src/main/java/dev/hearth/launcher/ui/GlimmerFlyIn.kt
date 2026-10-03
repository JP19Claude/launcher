package dev.hearth.launcher.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.FlyInStyle
import dev.hearth.launcher.system.GlimmerLink
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.core.CubicBezierEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import android.content.Context
import android.util.TypedValue
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The icon's average color, for a card that looks like the app's splash screen. */
private fun iconColor(app: AppInfo): Color = runCatching {
    val bitmap = app.icon.asAndroidBitmap()
    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    Color(Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)).copy(alpha = 1f)
}.getOrDefault(Color(0xFF2A2A2E))

/**
 * The color the app's own window has (its splash or window background), so the card looks
 * like the app that just closed, not just its icon. Null if the app doesn't say.
 */
private fun appWindowColor(context: Context, app: AppInfo): Color? = runCatching {
    val pm = context.packageManager
    val activity = pm.getActivityInfo(app.component, 0)
    val themeRes = activity.themeResource.takeIf { it != 0 } ?: activity.applicationInfo.theme
    if (themeRes == 0) return@runCatching null
    val appContext = context.createPackageContext(app.packageName, 0)
    val theme = appContext.resources.newTheme().apply { applyStyle(themeRes, true) }
    val attrs = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(android.R.attr.windowSplashScreenBackground)
        add(android.R.attr.windowBackground)
        add(android.R.attr.colorBackground)
    }
    val value = TypedValue()
    attrs.firstNotNullOfOrNull { attr ->
        if (theme.resolveAttribute(attr, value, true) &&
            value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT &&
            (value.data ushr 24) > 0x80
        ) {
            Color(value.data)
        } else {
            null
        }
    }
}.getOrNull()

/**
 * Closing an app, the way HarmonyOS does it: the app shrinks to a card in its own colors,
 * rises towards the camera while it flattens into a capsule, turns dark, and flows into
 * Glimmer through a liquid neck (Android 12+); the island takes it in with a springy squash.
 * Draws only, never takes touches.
 */
@Composable
fun GlimmerFlyIn(
    app: AppInfo,
    island: DpSize?,
    onPulse: () -> Unit,
    onDone: () -> Unit,
    /** The last picture of the app (Android 11+); with it, the app itself flies, 1:1 like HarmonyOS. */
    snapshot: ImageBitmap? = null,
    style: FlyInStyle = FlyInStyle.HarmonyOS,
    /** HyperOS: where in Glimmer the app lands. */
    landing: GlimmerLink.Landing = GlimmerLink.Landing(),
    /** HyperOS: Glimmer takes the app's icon (small picture, color) as it lands. */
    onArrive: (Bitmap?, Int) -> Unit = { _, _ -> },
) {
    if (style == FlyInStyle.HyperOS) {
        HyperFlyIn(app, landing, onArrive, onDone, snapshot)
        return
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val travel = remember(app) { Animatable(0f) }
    val absorb = remember(app) { Animatable(0f) }
    val pulse by rememberUpdatedState(onPulse)
    val done by rememberUpdatedState(onDone)
    val context = LocalContext.current
    // The app's own background, softly mixed with its icon color so it never looks flat.
    val color = remember(app) {
        val icon = iconColor(app)
        appWindowColor(context, app)?.let { lerpColor(it, icon, 0.25f) } ?: icon
    }
    val icon = app.icon

    // Where Glimmer sits: centered on the camera cutout at the top, or the top middle.
    val camera = remember(view) {
        val rects: List<Rect> = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        rects.minByOrNull { it.top }?.takeIf { it.top < view.height / 4 }
    }
    // Aim at the island as it is now; without one, into the camera itself.
    val islandShown = island != null && island.width.value > 0f
    val targetW = with(density) {
        if (islandShown) island!!.width.toPx() else ISLAND_HEIGHT_DP.dp.toPx()
    }
    val targetH = with(density) { ISLAND_HEIGHT_DP.dp.toPx() }

    // The liquid neck: blur the dark shapes, then keep only what's dense enough.
    val goo = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = with(density) { 9.dp.toPx() }
                val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
                val threshold = ColorMatrixColorFilter(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 22f, -9f * 255f,
                    ),
                )
                RenderEffect.createColorFilterEffect(threshold, blur).asComposeRenderEffect()
            }.getOrNull()
        } else {
            null
        }
    }

    LaunchedEffect(app) {
        launch {
            var pulsed = false
            snapshotFlow { travel.value }.collect { t ->
                if (!pulsed && t > 0.84f) {
                    pulsed = true
                    // The island swallows it: squashed wide, then springs back.
                    pulse()
                    launch {
                        absorb.animateTo(1f, tween(90))
                        absorb.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 420f))
                    }
                }
            }
        }
        travel.animateTo(1f, spring(dampingRatio = 1f, stiffness = 150f, visibilityThreshold = 0.002f))
        delay(220)
        done()
    }

    Box(Modifier.fillMaxSize()) {
        val w = view.width.toFloat().coerceAtLeast(1f)
        val h = view.height.toFloat().coerceAtLeast(1f)
        val camX = camera?.exactCenterX() ?: (w / 2f)
        val camY = camera?.exactCenterY() ?: with(density) { 15.dp.toPx() }

        // One place for the card's shape, read while drawing (no recomposition per frame).
        fun card(t: Float): Pair<Offset, Size> {
            // With the app's picture it starts as the whole screen (the app itself, under the
            // system's closing animation); otherwise as a card the size of the closing window.
            val startW = if (snapshot != null) w else w * 0.84f
            val startH = if (snapshot != null) h else h * 0.74f
            // Height collapses faster than width: the card flattens into a capsule on the way.
            val fw = 1f - (1f - t).pow(1.9f)
            val fh = 1f - (1f - t).pow(2.9f)
            val cw = lerp(startW, targetW, fw)
            val ch = lerp(startH, targetH, fh)
            val cx = lerp(w / 2f, camX, t)
            // Rises a little ahead of its shrinking: a soft arc up into the island.
            val cy = lerp(if (snapshot != null) h * 0.5f else h * 0.52f, camY, 1f - (1f - t).pow(1.5f))
            return Offset(cx - cw / 2f, cy - ch / 2f) to Size(cw, ch)
        }

        // The dark body and the island, melting into each other.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = goo
                    alpha = if (islandShown) 1f else 1f - smoothstep(0.88f, 1f, travel.value)
                },
        ) {
            val t = travel.value.coerceIn(0f, 1f)
            if (t < 0.32f) return@Canvas
            val (pos, size) = card(t)
            val radius = min(size.width, size.height) / 2f
            drawRoundRect(Color.Black, pos, size, CornerRadius(min(lerp(36.dp.toPx(), radius, t), radius)))
            // The island reaches down for the card as it comes (like a drop pulled towards
            // another), then swells as it takes it in.
            val a = absorb.value
            val reach = smoothstep(0.5f, 0.82f, t) * (1f - smoothstep(0.84f, 0.95f, t))
            val pw = targetW * (1f + 0.10f * reach + 0.18f * a)
            val ph = targetH * (1f + 0.45f * reach - 0.10f * a)
            drawRoundRect(
                Color.Black,
                Offset(camX - pw / 2f, camY - targetH / 2f),
                Size(pw, ph),
                CornerRadius(min(ph, pw) / 2f),
                alpha = smoothstep(0.32f, 0.5f, t),
            )
        }

        // The app itself: its colors and icon, fading into the dark body as it nears the top.
        Canvas(Modifier.fillMaxSize()) {
            val t = travel.value.coerceIn(0f, 1f)
            val appear = smoothstep(0f, 0.06f, t)
            val body = (1f - smoothstep(0.42f, 0.76f, t)) * appear
            if (body <= 0.001f) return@Canvas
            val (pos, size) = card(t)
            val corner = CornerRadius(min(lerp(36.dp.toPx(), min(size.width, size.height) / 2f, t), min(size.width, size.height) / 2f))
            if (snapshot == null) drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to lerpColor(color, Color.White, 0.18f),
                    1f to lerpColor(color, Color.Black, 0.35f),
                    startY = pos.y,
                    endY = pos.y + size.height,
                ),
                topLeft = pos,
                size = size,
                cornerRadius = corner,
                alpha = body,
            )
            // Light along the top, like glass over the app's window.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f * body),
                    0.25f to Color.Transparent,
                    startY = pos.y,
                    endY = pos.y + size.height,
                ),
                topLeft = pos,
                size = size,
                cornerRadius = corner,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.22f * body),
                pos,
                size,
                corner,
                style = Stroke(1.dp.toPx()),
            )
        }

        // The icon blurs as it shrinks (Android 12+), the app's content dissolving into color.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = travel.value.coerceIn(0f, 1f)
                    val r = if (snapshot != null) 16.dp.toPx() * smoothstep(0.25f, 0.65f, t) else 22.dp.toPx() * smoothstep(0.08f, 0.5f, t)
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) {
                        BlurEffect(r, r, TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            val t = travel.value.coerceIn(0f, 1f)
            val appear = smoothstep(0f, 0.06f, t)
            val (pos, size) = card(t)
            if (snapshot != null) {
                // The app's own screen, shrinking with the card: cropped, never squeezed, as the
                // card flattens into the capsule; it melts into the dark body underneath.
                val body = 1f - smoothstep(0.42f, 0.76f, t)
                if (body > 0.001f) {
                    val r = min(lerp(36.dp.toPx(), min(size.width, size.height) / 2f, t), min(size.width, size.height) / 2f)
                    val scale = max(size.width / snapshot.width, size.height / snapshot.height)
                    val dw = snapshot.width * scale
                    val dh = snapshot.height * scale
                    val clip = Path().apply {
                        addRoundRect(RoundRect(pos.x, pos.y, pos.x + size.width, pos.y + size.height, CornerRadius(r)))
                    }
                    clipPath(clip) {
                        drawImage(
                            image = snapshot,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(snapshot.width, snapshot.height),
                            dstOffset = IntOffset((pos.x + (size.width - dw) / 2f).roundToInt(), (pos.y + (size.height - dh) / 2f).roundToInt()),
                            dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                            alpha = body,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
                return@Canvas
            }
            val iconAlpha = (1f - smoothstep(0.3f, 0.58f, t)) * appear
            if (iconAlpha > 0.001f) {
                val iconSize = max(lerp(84.dp.toPx(), 18.dp.toPx(), t), 1f)
                val c = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                drawImage(
                    image = icon,
                    dstOffset = IntOffset((c.x - iconSize / 2f).roundToInt(), (c.y - iconSize / 2f).roundToInt()),
                    dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
                    alpha = iconAlpha,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

/** HyperOS's quick, decisive curve: fast out of the gate, landing softly. */
private val HyperEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Closing an app, the way HyperOS does it: the app stays whole and keeps its shape, lifts off
 * with a soft shadow, shrinks in one quick arc towards the camera and turns into its icon on
 * the way. Just before it gets there Glimmer itself takes over: the island opens for the app,
 * its icon sits in it for a moment (like Xiaomi's Hyper Island) and it closes again. So the
 * landing happens in the real island, in its own style and place, not in a copy drawn here.
 */
@Composable
private fun HyperFlyIn(
    app: AppInfo,
    landing: GlimmerLink.Landing,
    onArrive: (Bitmap?, Int) -> Unit,
    onDone: () -> Unit,
    snapshot: ImageBitmap?,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val travel = remember(app) { Animatable(0f) }
    val arrive by rememberUpdatedState(onArrive)
    val done by rememberUpdatedState(onDone)
    val color = remember(app) {
        val icon = iconColor(app)
        appWindowColor(context, app)?.let { lerpColor(it, icon, 0.25f) } ?: icon
    }
    val icon = app.icon
    // A small copy of the icon for Glimmer (it can't look up other apps' icons itself).
    val smallIcon = remember(app) {
        runCatching {
            val bitmap = icon.asAndroidBitmap()
            val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            Bitmap.createScaledBitmap(soft, 96, 96, true)
        }.getOrNull()
    }
    val camera = remember(view) {
        val rects: List<Rect> = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        rects.minByOrNull { it.top }?.takeIf { it.top < view.height / 4 }
    }
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    LaunchedEffect(app) {
        launch {
            var sent = false
            snapshotFlow { travel.value }.collect { t ->
                // Late enough that the app is small, early enough that the island has opened
                // by the time it gets there.
                if (!sent && t > 0.9f) {
                    sent = true
                    arrive(smallIcon, color.toArgb())
                }
            }
        }
        travel.animateTo(1f, tween(620, easing = HyperEase))
        delay(140)
        done()
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val camX = (camera?.exactCenterX() ?: (w / 2f)) + landing.dx.dp.toPx()
        val camY = (camera?.exactCenterY() ?: 15.dp.toPx()) + landing.dy.dp.toPx()
        // The icon's place in the small island: its left end (as Glimmer draws it).
        val landW = if (landing.width > 0f) landing.width.dp.toPx() else 170.dp.toPx()
        val endX = camX - landW / 2f + 16.dp.toPx()
        val endY = camY
        val iconEnd = 20.dp.toPx()

        val k = travel.value.coerceIn(0f, 1f)
        // Keeps its proportions while shrinking, only near the end it rounds into a square.
        val square = smoothstep(0.45f, 0.85f, k)
        val cw = lerp(w, iconEnd, k)
        val ch = lerp(h * (cw / w), cw, square)
        // A quick arc: up faster than across, like a flick towards the top.
        val cx = lerp(w / 2f, endX, 1f - (1f - k).pow(1.3f))
        val cy = lerp(h / 2f, endY, 1f - (1f - k).pow(2.2f))
        val pos = Offset(cx - cw / 2f, cy - ch / 2f)
        val r = lerp(36.dp.toPx(), min(cw, ch) * 0.3f, square).coerceAtMost(min(cw, ch) / 2f)
        // The real island covers it in the end; what's left here fades as Glimmer takes over.
        val fadeAll = 1f - smoothstep(0.93f, 1f, k)
        if (fadeAll <= 0.001f) return@Canvas

        // A soft shadow under the lifted app (only while the app itself still covers it).
        val shadow = (1f - smoothstep(0.15f, 0.4f, k)) * 0.45f
        if (shadow > 0.01f) {
            drawIntoCanvas { canvas ->
                shadowPaint.color = color.toArgb()
                shadowPaint.setShadowLayer(
                    lerp(28.dp.toPx(), 8.dp.toPx(), k),
                    0f,
                    lerp(12.dp.toPx(), 2.dp.toPx(), k),
                    Color.Black.copy(alpha = shadow).toArgb(),
                )
                canvas.nativeCanvas.drawRoundRect(pos.x, pos.y, pos.x + cw, pos.y + ch, r, r, shadowPaint)
            }
        }

        val clip = Path().apply { addRoundRect(RoundRect(pos.x, pos.y, pos.x + cw, pos.y + ch, CornerRadius(r))) }
        // The app: its own picture (or its colors) at first, its icon at the end.
        val content = 1f - smoothstep(0.4f, 0.72f, k)
        clipPath(clip) {
            if (content > 0.001f) {
                if (snapshot != null) {
                    val scale = max(cw / snapshot.width, ch / snapshot.height)
                    val dw = snapshot.width * scale
                    val dh = snapshot.height * scale
                    drawImage(
                        image = snapshot,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(snapshot.width, snapshot.height),
                        dstOffset = IntOffset((pos.x + (cw - dw) / 2f).roundToInt(), (pos.y + (ch - dh) / 2f).roundToInt()),
                        dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                        alpha = content * fadeAll,
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    drawRect(
                        Brush.verticalGradient(
                            0f to lerpColor(color, Color.White, 0.18f),
                            1f to lerpColor(color, Color.Black, 0.3f),
                            startY = pos.y,
                            endY = pos.y + ch,
                        ),
                        topLeft = pos,
                        size = Size(cw, ch),
                        alpha = content * fadeAll,
                    )
                    // Its icon in the middle, like the app's splash.
                    val big = min(cw, ch) * 0.32f
                    if (big > 1f) drawImage(
                        image = icon,
                        dstOffset = IntOffset((pos.x + cw / 2f - big / 2f).roundToInt(), (pos.y + ch / 2f - big / 2f).roundToInt()),
                        dstSize = IntSize(big.roundToInt(), big.roundToInt()),
                        alpha = content * fadeAll,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
            // Turning into its icon.
            val asIcon = smoothstep(0.38f, 0.7f, k) * fadeAll
            if (asIcon > 0.001f) {
                val side = min(cw, ch)
                drawImage(
                    image = icon,
                    dstOffset = IntOffset((pos.x + (cw - side) / 2f).roundToInt(), (pos.y + (ch - side) / 2f).roundToInt()),
                    dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)),
                    alpha = asIcon,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        // A thin bright rim while it's big, like glass catching the light.
        val rim = (1f - smoothstep(0.3f, 0.6f, k)) * 0.25f * fadeAll
        if (rim > 0.01f) drawRoundRect(Color.White.copy(alpha = rim), pos, Size(cw, ch), CornerRadius(r), style = Stroke(1.dp.toPx()))
    }
}

/**
 * Whether a captured picture shows anything: apps that protect their screen (banking,
 * passwords) come out pure black, and then the colored card flies instead.
 */
fun snapshotLooksReal(bitmap: Bitmap): Boolean = runCatching {
    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val tiny = Bitmap.createScaledBitmap(soft, 12, 24, true)
    var lit = 0
    for (y in 0 until tiny.height) for (x in 0 until tiny.width) {
        val p = tiny.getPixel(x, y)
        val v = ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
        if (v > 30) lit++
    }
    lit > 6
}.getOrDefault(false)

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = lerp(a.red, b.red, t),
    green = lerp(a.green, b.green, t),
    blue = lerp(a.blue, b.blue, t),
    alpha = 1f,
)

/**
 * The fly-in with another launcher (One UI, ColorOS, Pixel …): that launcher has just shrunk
 * the app into its icon, so the icon lifts off right there ([from], on screen) and flies in an
 * arc into Glimmer ([to], the island's middle on screen) – landing in the island (HyperOS) or
 * melting into it at the camera (HarmonyOS). Without its icon on the screen, it rises from
 * the middle.
 */
@Composable
fun IconFlyIn(
    app: AppInfo,
    from: android.graphics.RectF?,
    to: Offset,
    islandWidth: Float,
    style: FlyInStyle,
    onPulse: () -> Unit,
    onArrive: (Bitmap?, Int) -> Unit,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    val travel = remember(app) { Animatable(0f) }
    val pulse by rememberUpdatedState(onPulse)
    val arrive by rememberUpdatedState(onArrive)
    val done by rememberUpdatedState(onDone)
    val color = remember(app) { iconColor(app) }
    val smallIcon = remember(app) {
        runCatching {
            val bitmap = app.icon.asAndroidBitmap()
            val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            Bitmap.createScaledBitmap(soft, 96, 96, true)
        }.getOrNull()
    }
    LaunchedEffect(app) {
        launch {
            var sent = false
            snapshotFlow { travel.value }.collect { t ->
                if (!sent && t > 0.86f) {
                    sent = true
                    if (style == FlyInStyle.HyperOS) arrive(smallIcon, color.toArgb())
                }
            }
        }
        travel.animateTo(1f, tween(640, easing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)))
        if (style != FlyInStyle.HyperOS) pulse()
        delay(120)
        done()
    }
    val icon = app.icon
    Canvas(Modifier.fillMaxSize()) {
        // Screen positions into this window's (it covers the screen, but be exact).
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val ox = loc[0].toFloat()
        val oy = loc[1].toFloat()
        val startSide = from?.let { min(it.width(), it.height()) } ?: 56.dp.toPx()
        val sx = (from?.centerX() ?: (size.width / 2f + ox)) - ox
        val sy = (from?.centerY() ?: (size.height * 0.55f + oy)) - oy
        val hyper = style == FlyInStyle.HyperOS
        // HyperOS: the icon's place at the island's left end; HarmonyOS: right into the camera.
        val ex = (if (hyper) to.x - islandWidth / 2f + 16.dp.toPx() else to.x) - ox
        val ey = to.y - oy
        val endSide = if (hyper) 20.dp.toPx() else 12.dp.toPx()

        val k = travel.value.coerceIn(0f, 1f)
        // A little lift-off first (it grows a touch), then it shrinks on the way up.
        val lift = 1f + 0.12f * smoothstep(0f, 0.12f, k) * (1f - smoothstep(0.12f, 0.35f, k))
        val side = lerp(startSide, endSide, smoothstep(0.08f, 1f, k)) * lift
        // Up faster than across: a quick flick towards the top.
        val cx = lerp(sx, ex, 1f - (1f - k).pow(1.3f))
        val cy = lerp(sy, ey, 1f - (1f - k).pow(2.4f))
        val fade = 1f - smoothstep(0.9f, 1f, k)
        if (fade <= 0.001f) return@Canvas
        val pos = Offset(cx - side / 2f, cy - side / 2f)
        val r = side * 0.3f
        // A soft shadow while it's lifted off.
        val shadow = (1f - smoothstep(0.2f, 0.55f, k)) * 0.35f
        if (shadow > 0.01f) {
            drawRoundRect(
                Color.Black.copy(alpha = shadow * fade),
                Offset(pos.x, pos.y + side * 0.08f),
                Size(side, side),
                CornerRadius(r),
            )
        }
        val clip = Path().apply { addRoundRect(RoundRect(pos.x, pos.y, pos.x + side, pos.y + side, CornerRadius(r))) }
        clipPath(clip) {
            drawImage(
                image = icon,
                dstOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)),
                alpha = fade,
                filterQuality = FilterQuality.Medium,
            )
            // HarmonyOS: it darkens into the island as it reaches the camera.
            if (!hyper) {
                val dark = smoothstep(0.6f, 0.95f, k) * fade
                if (dark > 0.01f) drawRect(Color.Black.copy(alpha = dark), pos, Size(side, side))
            }
        }
    }
}
