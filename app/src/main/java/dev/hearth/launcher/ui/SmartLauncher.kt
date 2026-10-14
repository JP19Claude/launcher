package dev.hearth.launcher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hearth.launcher.data.AppInfo
import dev.hearth.launcher.data.ClockFont
import dev.hearth.launcher.data.DefaultSmartTabs
import dev.hearth.launcher.data.IconShape
import dev.hearth.launcher.data.LabelSize
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LibraryCategory
import dev.hearth.launcher.data.SmartClockLayout
import dev.hearth.launcher.data.SmartColor
import dev.hearth.launcher.data.SmartGlass
import dev.hearth.launcher.data.SmartHours
import dev.hearth.launcher.data.SmartPageEffect
import dev.hearth.launcher.data.SmartTab
import dev.hearth.launcher.data.WallpaperBackdrop
import dev.hearth.launcher.data.showSlogan
import dev.hearth.launcher.data.withSmartGlass
import dev.hearth.launcher.data.withSmarter
import dev.hearth.launcher.data.withSmartGlimmer
import dev.hearth.launcher.system.GlimmerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * ZENITH 20.5, "Be Smarter": the launcher dressed after Smart Launcher 6. The look itself is a layer
 * of settings laid over what is chosen while drawing (see withSmartLook); what lives here is what the
 * other looks have no counterpart for – the drawer with its category bar, the colors of its themes,
 * the search pill, the sheet of the home screen – and the Illuminati's controls for the mode.
 */

// ---------------------------------------------------------------------------------------------
// The colors of the themes
// ---------------------------------------------------------------------------------------------

/**
 * The colors Smart Launcher's surfaces take: [surface] for flat glass (the dock, widgets), [highlight]
 * for what stands out a little (the search pill, the category bar), [accent] for what is chosen.
 * Null colors leave the glass to its own look (Frosted, Opaque, Liquid).
 */
@Immutable
internal class SmartPalette(val surface: Color?, val highlight: Color?, val accent: Color)

internal val LocalSmartPalette = compositionLocalOf<SmartPalette?> { null }

private val SmartAccent = Color(0xFF5CCBFF)
private val SmartFallback = Color(0xFF2B2F6B)

/** The wallpaper's color as a deep, rich shade – what Ambient Design tints its surfaces with. */
internal fun ambientColor(backdrop: WallpaperBackdrop?): Color {
    val source = backdrop?.image?.asAndroidBitmap() ?: return SmartFallback
    return runCatching {
        val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val tiny = Bitmap.createScaledBitmap(soft, 12, 12, true)
        var red = 0L
        var green = 0L
        var blue = 0L
        for (y in 0 until tiny.height) {
            for (x in 0 until tiny.width) {
                val pixel = tiny.getPixel(x, y)
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
            }
        }
        val count = tiny.width * tiny.height
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((red / count).toInt(), (green / count).toInt(), (blue / count).toInt(), hsv)
        hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.26f, 0.5f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }.getOrDefault(SmartFallback)
}

private fun ambientPalette(ambient: Color) = SmartPalette(
    surface = ambient.copy(alpha = 0.55f),
    highlight = lerp(ambient, Color.White, 0.2f).copy(alpha = 0.85f),
    accent = SmartAccent,
)

/** Material You: the colors the phone itself uses (Android 12 and newer); before that, the wallpaper's. */
private fun materialPalette(context: Context, ambient: Color): SmartPalette {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ambientPalette(ambient)
    return runCatching {
        SmartPalette(
            surface = Color(context.getColor(android.R.color.system_accent2_800)).copy(alpha = 0.7f),
            highlight = Color(context.getColor(android.R.color.system_accent1_700)).copy(alpha = 0.85f),
            accent = Color(context.getColor(android.R.color.system_accent1_200)),
        )
    }.getOrElse { ambientPalette(ambient) }
}

/** The palette of the chosen theme, or null while "Be Smarter" is off. */
@Composable
internal fun rememberSmartPalette(settings: LauncherSettings, backdrop: WallpaperBackdrop?): SmartPalette? {
    val context = LocalContext.current
    val ambient = remember(backdrop) { ambientColor(backdrop) }
    val on = settings.beSmarter
    val theme = settings.smartGlass
    // "Thema bearbeiten → Farbe": one of the chosen colors instead of the wallpaper's.
    val base = if (settings.smartColor == SmartColor.Custom) Color(settings.smartCustomColor) else settings.smartColor.color ?: ambient
    return remember(on, theme, base) {
        if (!on) {
            null
        } else {
            when (theme) {
                SmartGlass.Ambient -> ambientPalette(base)
                SmartGlass.MaterialYou -> materialPalette(context, base)
                SmartGlass.Frosted, SmartGlass.Opaque, SmartGlass.Liquid -> SmartPalette(null, null, SmartAccent)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The categories and their line icons
// ---------------------------------------------------------------------------------------------

internal enum class SmartGlyph { Chat, Social, Games, Media, Tools, Gear, Bag, Camera, Music, Star, Heart, Cart, Book, Pin, Cloud, Folder }

/** The sign named in a tab ([dev.hearth.launcher.data.SmartTab.glyph]); the tools' one if the name is unknown. */
internal fun smartGlyphOf(name: String): SmartGlyph = SmartGlyph.entries.firstOrNull { it.name == name } ?: SmartGlyph.Tools

/** A line icon of Smart Launcher's kind, drawn in a 24 × 24 box. */
@Composable
internal fun SmartGlyphIcon(glyph: SmartGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val line = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        when (glyph) {
            SmartGlyph.Chat -> {
                drawRoundRect(color, topLeft = at(3f, 3.5f), size = Size(18f * u, 13f * u), cornerRadius = CornerRadius(6f * u), style = line)
                val tail = Path().apply {
                    moveTo(7.5f * u, 16.5f * u)
                    lineTo(6.5f * u, 20.5f * u)
                    lineTo(11.5f * u, 16.5f * u)
                }
                drawPath(tail, color, style = line)
                drawCircle(color, 1.1f * u, at(8f, 10f))
                drawCircle(color, 1.1f * u, at(12f, 10f))
                drawCircle(color, 1.1f * u, at(16f, 10f))
            }
            SmartGlyph.Social -> {
                drawCircle(color, 2.6f * u, at(12f, 6f), style = line)
                drawCircle(color, 2.2f * u, at(6f, 17.5f), style = line)
                drawCircle(color, 2.2f * u, at(18f, 17.5f), style = line)
                drawLine(color, at(11f, 8.4f), at(7f, 15.5f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(13f, 8.4f), at(17f, 15.5f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(8.4f, 17.5f), at(15.6f, 17.5f), 1.8f * u, StrokeCap.Round)
            }
            SmartGlyph.Games -> {
                drawRoundRect(color, topLeft = at(2.5f, 6.5f), size = Size(19f * u, 11.5f * u), cornerRadius = CornerRadius(5.75f * u), style = line)
                drawLine(color, at(6f, 12.25f), at(10f, 12.25f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(8f, 10.25f), at(8f, 14.25f), 1.8f * u, StrokeCap.Round)
                drawCircle(color, 1.1f * u, at(15.5f, 11.2f))
                drawCircle(color, 1.1f * u, at(18f, 13.6f))
            }
            SmartGlyph.Media -> {
                drawRoundRect(color, topLeft = at(3f, 6f), size = Size(15f * u, 15f * u), cornerRadius = CornerRadius(3f * u), style = line)
                val hills = Path().apply {
                    moveTo(4.5f * u, 18.5f * u)
                    lineTo(9f * u, 13.5f * u)
                    lineTo(12f * u, 16.5f * u)
                    lineTo(13.5f * u, 15f * u)
                    lineTo(16.5f * u, 18.5f * u)
                }
                drawPath(hills, color, style = line)
                drawLine(color, at(19.5f, 2f), at(19.5f, 7f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(17f, 4.5f), at(22f, 4.5f), 1.8f * u, StrokeCap.Round)
            }
            SmartGlyph.Tools -> {
                val triangle = Path().apply {
                    moveTo(12f * u, 3f * u)
                    lineTo(16.8f * u, 11f * u)
                    lineTo(7.2f * u, 11f * u)
                    close()
                }
                drawPath(triangle, color, style = line)
                drawRoundRect(color, topLeft = at(3.5f, 14f), size = Size(7f * u, 7f * u), cornerRadius = CornerRadius(1.4f * u), style = line)
                drawCircle(color, 3.5f * u, at(17.5f, 17.5f), style = line)
            }
            SmartGlyph.Gear -> {
                val gear = Path()
                val cx = 12f * u
                val cy = 12f * u
                for (i in 0 until 8) {
                    val base = i * 45f
                    val corners = listOf(base - 13f to 7.2f, base - 7f to 9.6f, base + 7f to 9.6f, base + 13f to 7.2f)
                    corners.forEachIndexed { k, corner ->
                        val angle = Math.toRadians(corner.first.toDouble())
                        val x = cx + (corner.second * u * kotlin.math.cos(angle)).toFloat()
                        val y = cy + (corner.second * u * kotlin.math.sin(angle)).toFloat()
                        if (i == 0 && k == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                    }
                }
                gear.close()
                drawPath(gear, color, style = line)
                drawCircle(color, 3f * u, Offset(cx, cy), style = line)
            }
            SmartGlyph.Bag -> {
                drawRoundRect(color, topLeft = at(5f, 8f), size = Size(14f * u, 13f * u), cornerRadius = CornerRadius(2.5f * u), style = line)
                val handle = Path().apply {
                    moveTo(9f * u, 11f * u)
                    lineTo(9f * u, 7f * u)
                    cubicTo(9f * u, 3f * u, 15f * u, 3f * u, 15f * u, 7f * u)
                    lineTo(15f * u, 11f * u)
                }
                drawPath(handle, color, style = line)
            }
            SmartGlyph.Camera -> {
                drawRoundRect(color, topLeft = at(3f, 7f), size = Size(18f * u, 12.5f * u), cornerRadius = CornerRadius(3f * u), style = line)
                val bump = Path().apply {
                    moveTo(8.5f * u, 7f * u)
                    lineTo(10f * u, 4.5f * u)
                    lineTo(14f * u, 4.5f * u)
                    lineTo(15.5f * u, 7f * u)
                }
                drawPath(bump, color, style = line)
                drawCircle(color, 3.6f * u, at(12f, 13.2f), style = line)
            }
            SmartGlyph.Music -> {
                drawCircle(color, 2.6f * u, at(7.5f, 18f), style = line)
                drawCircle(color, 2.6f * u, at(17f, 16f), style = line)
                drawLine(color, at(10.1f, 18f), at(10.1f, 6f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(19.6f, 16f), at(19.6f, 4f), 1.8f * u, StrokeCap.Round)
                drawLine(color, at(10.1f, 6f), at(19.6f, 4f), 1.8f * u, StrokeCap.Round)
            }
            SmartGlyph.Star -> {
                val star = Path()
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) 9.5f else 4.2f
                    val angle = Math.toRadians(-90.0 + 36.0 * i)
                    val x = (12f + radius * kotlin.math.cos(angle).toFloat()) * u
                    val y = (12.5f + radius * kotlin.math.sin(angle).toFloat()) * u
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, color, style = line)
            }
            SmartGlyph.Heart -> {
                val heart = Path().apply {
                    moveTo(12f * u, 20f * u)
                    cubicTo(5f * u, 15f * u, 3f * u, 10f * u, 6f * u, 7f * u)
                    cubicTo(8.5f * u, 4.5f * u, 11f * u, 6f * u, 12f * u, 8f * u)
                    cubicTo(13f * u, 6f * u, 15.5f * u, 4.5f * u, 18f * u, 7f * u)
                    cubicTo(21f * u, 10f * u, 19f * u, 15f * u, 12f * u, 20f * u)
                    close()
                }
                drawPath(heart, color, style = line)
            }
            SmartGlyph.Cart -> {
                val cart = Path().apply {
                    moveTo(3f * u, 4f * u)
                    lineTo(5.5f * u, 4f * u)
                    lineTo(8f * u, 15f * u)
                    lineTo(18f * u, 15f * u)
                    lineTo(20f * u, 7f * u)
                    lineTo(6.5f * u, 7f * u)
                }
                drawPath(cart, color, style = line)
                drawCircle(color, 1.4f * u, at(9.5f, 19f), style = line)
                drawCircle(color, 1.4f * u, at(16.5f, 19f), style = line)
            }
            SmartGlyph.Book -> {
                val book = Path().apply {
                    moveTo(12f * u, 7f * u)
                    lineTo(5f * u, 5.5f * u)
                    lineTo(5f * u, 18f * u)
                    lineTo(12f * u, 19.5f * u)
                    lineTo(19f * u, 18f * u)
                    lineTo(19f * u, 5.5f * u)
                    close()
                    moveTo(12f * u, 7f * u)
                    lineTo(12f * u, 19.5f * u)
                }
                drawPath(book, color, style = line)
            }
            SmartGlyph.Pin -> {
                val pin = Path().apply {
                    moveTo(12f * u, 21f * u)
                    cubicTo(6f * u, 14f * u, 5f * u, 11f * u, 5f * u, 9.5f * u)
                    cubicTo(5f * u, 6f * u, 8f * u, 3f * u, 12f * u, 3f * u)
                    cubicTo(16f * u, 3f * u, 19f * u, 6f * u, 19f * u, 9.5f * u)
                    cubicTo(19f * u, 11f * u, 18f * u, 14f * u, 12f * u, 21f * u)
                    close()
                }
                drawPath(pin, color, style = line)
                drawCircle(color, 2.8f * u, at(12f, 9.5f), style = line)
            }
            SmartGlyph.Cloud -> {
                val cloud = Path().apply {
                    moveTo(7f * u, 18.5f * u)
                    cubicTo(2.5f * u, 18.5f * u, 2.5f * u, 11f * u, 8f * u, 11f * u)
                    cubicTo(8.5f * u, 5f * u, 17f * u, 5f * u, 17.5f * u, 11.5f * u)
                    cubicTo(22f * u, 11.5f * u, 22f * u, 18.5f * u, 17.5f * u, 18.5f * u)
                    close()
                }
                drawPath(cloud, color, style = line)
            }
            SmartGlyph.Folder -> {
                drawRoundRect(color, topLeft = at(3f, 8f), size = Size(18f * u, 11f * u), cornerRadius = CornerRadius(2.5f * u), style = line)
                val tab = Path().apply {
                    moveTo(3f * u, 8f * u)
                    lineTo(3f * u, 5.5f * u)
                    lineTo(9f * u, 5.5f * u)
                    lineTo(11f * u, 8f * u)
                }
                drawPath(tab, color, style = line)
            }
        }
    }
}

/** The kinds apps are sorted into by themselves – each the id of one of the six tabs the drawer starts with. */
private enum class SmartCategory(val id: String) {
    Communication("comm"),
    Social("social"),
    Games("games"),
    Media("media"),
    Utilities("tools"),
    Preferences("prefs"),
}

/** Packages (parts of their names) of the apps that talk to people: phone, messages, mail, messengers. */
private val CommunicationHints = listOf(
    "dialer", "contacts", "messag", "mms", "sms", "whatsapp", "telegram", "thoughtcrime", "threema",
    "android.gm", "outlook", "skype", "zoom", "teams", "viber", "discord", "android.email", "protonmail",
    "k9mail", "com.android.phone", "telecom",
)

/** Which kind an app is, by what it declares and what it is called. */
private fun smartAutoCategory(app: AppInfo): SmartCategory {
    val pkg = app.packageName.lowercase()
    if (pkg == "com.android.settings" || pkg.endsWith(".settings") || pkg.contains(".settings.") || app.component.className.endsWith(".SettingsShortcutActivity")) {
        return SmartCategory.Preferences
    }
    if (CommunicationHints.any { pkg.contains(it) }) return SmartCategory.Communication
    return when (app.category) {
        LibraryCategory.Social -> SmartCategory.Social
        LibraryCategory.Games -> SmartCategory.Games
        LibraryCategory.Entertainment, LibraryCategory.Creativity -> SmartCategory.Media
        else -> SmartCategory.Utilities
    }
}

/**
 * Which tab an app is in: the one it was moved to; else the one of its kind – or, if that tab was removed, the tools'
 * (or the first).
 */
internal fun smartCategoryOf(app: AppInfo, moved: Map<String, String>, tabs: List<SmartTab>): String {
    moved[app.packageName]?.let { id -> if (tabs.any { it.id == id }) return id }
    val auto = smartAutoCategory(app).id
    if (tabs.any { it.id == auto }) return auto
    return (tabs.firstOrNull { it.id == "tools" } ?: tabs.first()).id
}

/**
 * ZENITH 20.5: the app menu's way to move an app to another tab of the drawer (Smart Launcher lets you
 * sort them by hand): the app goes to the next tab on each press, and "Kategorie zurücksetzen" brings
 * it back to where it belongs by itself. [save] gets the new list of moved apps.
 */
internal fun smartCategoryMenu(app: AppInfo, moved: Map<String, String>, tabs: List<SmartTab>, save: (Map<String, String>) -> Unit): List<GlassMenuItem> {
    if (tabs.size < 2) return emptyList()
    val here = smartCategoryOf(app, moved, tabs)
    val at = tabs.indexOfFirst { it.id == here }.coerceAtLeast(0)
    val next = tabs[(at + 1) % tabs.size]
    return buildList {
        add(GlassMenuItem("In „${next.title}“ verschieben", Icons.Rounded.Edit) { save(moved + (app.packageName to next.id)) })
        if (app.packageName in moved) {
            add(GlassMenuItem("Kategorie zurücksetzen", Icons.Rounded.Home) { save(moved - app.packageName) })
        }
    }
}

/** The tab the drawer was left on, so it opens there again. */
private var rememberedTab = 0

// ---------------------------------------------------------------------------------------------
// The drawer
// ---------------------------------------------------------------------------------------------

/**
 * "Übergangseffekt der Kategorien": how a page of the drawer moves while it comes in or leaves.
 * [position] is the page's place in page widths from the one in the middle (0 = in the middle, -1 = a page to the left).
 */
private fun Modifier.smartPageEffect(effect: SmartPageEffect, position: () -> Float): Modifier = graphicsLayer {
    val at = position()
    val far = kotlin.math.abs(at).coerceIn(0f, 1f)
    when (effect) {
        SmartPageEffect.Slide -> alpha = 1f - 0.5f * far
        SmartPageEffect.Fade -> {
            // Held in place, the pages melt into one another.
            translationX = -at * size.width
            alpha = 1f - far
        }
        SmartPageEffect.Zoom -> {
            val k = 1f - 0.3f * far
            scaleX = k
            scaleY = k
            alpha = 1f - 0.6f * far
        }
        SmartPageEffect.Rotate -> {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = at * 14f
            alpha = 1f - 0.5f * far
        }
        SmartPageEffect.Flip -> {
            cameraDistance = 12f * density
            rotationY = at * -70f
            alpha = 1f - 0.7f * far
        }
        SmartPageEffect.Stretch -> {
            transformOrigin = TransformOrigin(if (at > 0f) 0f else 1f, 0.5f)
            scaleX = 1f - 0.5f * far
            alpha = 1f - 0.5f * far
        }
    }
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
 * Smart Launcher 6's app drawer: the apps of one category at a time, set low on the screen where the
 * thumb reaches – the category's name big on top with search, the store and a menu beside it, the
 * apps in four columns with their names, and at the foot the bar of the six categories (the chosen
 * one underlined). Swipe sideways or tap the bar to change category; pull down (or go back) to let go.
 */
@Composable
fun SmartDrawer(
    apps: List<AppInfo>,
    actions: AppActions,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings = LocalSettings.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val columns = settings.smartDrawerColumns.coerceIn(3, 6)
    // The names stand under the icons here (if wanted), though the home screen goes without them.
    val shown = remember(settings) { settings.copy(showLabels = settings.smartDrawerNames) }

    val moved = settings.smartCategories
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val tabList = settings.smartTabs.ifEmpty { DefaultSmartTabs }
    val byCategory = remember(sorted, moved, tabList) { sorted.groupBy { smartCategoryOf(it, moved, tabList) } }
    // "Kategorieleiste": off, the drawer is one page with all the apps. "Letzte Kategorie geöffnet": it opens where it was left.
    val bar = settings.smartCategoryBar
    val tabs = if (bar) tabList.size else 1
    val pagerState = rememberPagerState(initialPage = (if (settings.smartLastCategory) rememberedTab else 0).coerceIn(0, tabs - 1)) { tabs }
    LaunchedEffect(pagerState.currentPage) { if (bar) rememberedTab = pagerState.currentPage }

    // The search of the drawer itself: the title turns into a field, the apps of all categories are filtered.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    val matches = remember(sorted, query) {
        val text = query.trim()
        if (text.isEmpty()) sorted else sorted.filter { it.label.contains(text, ignoreCase = true) }
    }

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

    CompositionLocalProvider(LocalSettings provides shown) {
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nested),
        ) {
            // The wallpaper, frosted by the theme's blur; it clears as the drawer is pulled away.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - (pull.value / (dismissPx * 3f)).coerceIn(0f, 0.6f) },
            ) {
                OmegaScreenBackdrop(
                    modifier = Modifier.matchParentSize(),
                    blur = (14f + 22f * settings.glassBlur).coerceIn(10f, 60f).dp,
                    dim = 0.38f,
                    omegaDim = 0.3f,
                    noGlassDim = 0.55f,
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
                    .systemBarsPadding()
                    .imePadding(),
            ) {
                if (searching) {
                    SmartPage(
                        title = "",
                        apps = matches,
                        columns = columns,
                        actions = actions,
                        onLaunch = onLaunch,
                        search = SmartSearchState(
                            active = true,
                            query = query,
                            onQuery = { query = it },
                            onGo = {
                                // Enter: the one app that is left starts; nothing found goes on to the big search.
                                val first = matches.firstOrNull()
                                if (first != null && matches.size == 1) onLaunch(first, null) else if (matches.isEmpty()) onOpenSearch()
                            },
                            onOpen = {},
                            onClose = {
                                searching = false
                                query = ""
                            },
                        ),
                        onMore = onOpenSettings,
                        appear = { 1f },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { page ->
                        val category = if (bar) tabList.getOrNull(page) else null
                        SmartPage(
                            title = category?.title ?: "Alle Apps",
                            apps = if (category != null) byCategory[category.id].orEmpty() else sorted,
                            columns = columns,
                            actions = actions,
                            onLaunch = onLaunch,
                            search = SmartSearchState(
                                active = false,
                                query = "",
                                onQuery = {},
                                onGo = {},
                                onOpen = { searching = true },
                                onClose = {},
                            ),
                            onMore = onOpenSettings,
                            appear = { appear.value },
                            modifier = Modifier.smartPageEffect(settings.smartPageEffect) {
                                page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            },
                        )
                    }
                }
                if (bar && !searching) {
                    SmartTabBar(
                        tabs = tabList,
                        selected = pagerState.currentPage,
                        titles = settings.smartCategoryTitles,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                            .smartRise({ appear.value }, 3),
                    )
                }
            }
        }
    }
}

/** The drawer's own search: closed (the magnifier opens it) or open with what has been typed. */
private class SmartSearchState(
    val active: Boolean,
    val query: String,
    val onQuery: (String) -> Unit,
    val onGo: () -> Unit,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * One category: its name with the buttons beside it (searching, the store, the menu), then its apps in rows – all
 * set low on the page. The action bar is 64 dp high as Smart Launcher 6's is, the buttons 48 dp; while searching the
 * title is a 48 dp field with a 28 dp round outline, and a cross takes the search away again.
 */
@Composable
private fun SmartPage(
    title: String,
    apps: List<AppInfo>,
    columns: Int,
    actions: AppActions,
    onLaunch: (AppInfo, Rect?) -> Unit,
    search: SmartSearchState,
    onMore: () -> Unit,
    appear: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rows = remember(apps, columns) { apps.chunked(columns) }
    // "Abstand zwischen den Icons": extra room between the rows.
    val gap = LocalSettings.current.smartItemSpacing.coerceIn(0, 32).dp
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        item(key = "title") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 12.dp, end = 4.dp)
                    .smartRise(appear, 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (search.active) {
                    SmartSearchField(search.query, search.onQuery, search.onGo, Modifier.weight(1f))
                    SmartTopButton(onClick = search.onClose) {
                        // The cross.
                        Canvas(Modifier.size(20.dp)) {
                            val w = 2.dp.toPx()
                            drawLine(Color.White, Offset(size.width * 0.1f, size.height * 0.1f), Offset(size.width * 0.9f, size.height * 0.9f), w, StrokeCap.Round)
                            drawLine(Color.White, Offset(size.width * 0.9f, size.height * 0.1f), Offset(size.width * 0.1f, size.height * 0.9f), w, StrokeCap.Round)
                        }
                    }
                } else {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SmartTopButton(onClick = search.onOpen) {
                        Icon(Icons.Rounded.Search, contentDescription = "Suchen", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    SmartTopButton(
                        onClick = {
                            runCatching {
                                context.packageManager.getLaunchIntentForPackage("com.android.vending")
                                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        },
                    ) {
                        SmartGlyphIcon(SmartGlyph.Bag, Color.White)
                    }
                    SmartTopButton(onClick = onMore) {
                        // The three dots of the menu.
                        Canvas(Modifier.size(24.dp)) {
                            val r = 2.1.dp.toPx()
                            drawCircle(Color.White, r, Offset(size.width / 2f, size.height * 0.2f))
                            drawCircle(Color.White, r, Offset(size.width / 2f, size.height * 0.5f))
                            drawCircle(Color.White, r, Offset(size.width / 2f, size.height * 0.8f))
                        }
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (search.active) "Nichts gefunden." else "Hier liegt noch nichts.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(start = 12.dp, top = 8.dp, bottom = 24.dp)
                        .smartRise(appear, 1),
                )
            }
        }
        itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = gap)
                    .smartRise(appear, 1 + index),
                verticalAlignment = Alignment.Top,
            ) {
                for (column in 0 until columns) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        row.getOrNull(column)?.let { app ->
                            AppIcon(app, actions, onWallpaper = true, fillCell = true, onLaunch = onLaunch)
                        }
                    }
                }
            }
        }
    }
}

/** The field in the title's place: 48 dp high, a 28 dp round outline of white at 12 %, 20 dp in from the sides. */
@Composable
private fun SmartSearchField(query: String, onQuery: (String) -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focus.requestFocus() }
    }
    Box(
        modifier
            .height(48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text("Suchen", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp, maxLines = 1)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
}

@Composable
private fun SmartTopButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The bar of the six categories, a rounded pill of the theme's color; the chosen tab has a short line under its icon. */
@Composable
private fun SmartTabBar(tabs: List<SmartTab>, selected: Int, titles: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalSmartPalette.current
    // More than seven tabs (the ones made) no longer fit side by side: the bar scrolls.
    val many = tabs.size > 7
    LiquidGlass(
        cornerRadius = 30.dp,
        refraction = 12.dp,
        tint = palette?.highlight,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (many) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val cell = if (many) Modifier.width(66.dp) else Modifier.weight(1f)
                SmartTabCell(tab, index == selected, titles, palette?.accent ?: SmartAccent, cell) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun SmartTabCell(tab: SmartTab, on: Boolean, titles: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val line by animateDpAsState(if (on) 24.dp else 0.dp, tween(200), label = "smartTabLine")
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmartGlyphIcon(smartGlyphOf(tab.glyph), Color.White)
        if (titles) {
            // "Kategorientitel": the name under the sign.
            Text(
                tab.title,
                color = Color.White.copy(alpha = if (on) 0.95f else 0.6f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(line)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The search pill and the sheet of the home screen
// ---------------------------------------------------------------------------------------------

/** "Smart-Suche": the full-width pill at the foot of the home screen, under the dock. */
@Composable
internal fun SmartSearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalSmartPalette.current
    LiquidGlass(
        cornerRadius = 26.dp,
        refraction = 12.dp,
        tint = palette?.highlight,
        interactive = true,
        fluidEdge = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Smart-Suche", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private val SheetColor = Color(0xFF22252B)
private val SheetBlue = Color(0xFF2F8CFF)

/**
 * The sheet that rises from the foot when the home screen is held, as in Smart Launcher: its
 * actions in a row, then the places of the settings, and a chip above it for all of them.
 */
@Composable
internal fun SmartHomeSheet(
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onSelectApps: () -> Unit,
    onSearch: () -> Unit,
    onWallpaper: () -> Unit,
    onAppearance: () -> Unit,
    onHomeSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // "Alle Einstellungen anzeigen" – a dark chip on the seam.
            Row(
                Modifier
                    .padding(bottom = 14.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(onClick = onHomeSettings)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val w = 1.8.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width * 0.1f, size.height * 0.7f)
                        lineTo(size.width * 0.5f, size.height * 0.3f)
                        lineTo(size.width * 0.9f, size.height * 0.7f)
                    }
                    drawPath(path, Color.White, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Spacer(Modifier.width(12.dp))
                Text("Alle Einstellungen anzeigen", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SheetColor)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
            ) {
                Text("Startbildschirm", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    SheetAction(Icons.Rounded.Add, "Widget hinzufügen", Modifier.weight(1f), onAddWidget)
                    SheetAction(Icons.Rounded.CheckCircle, "Apps auswählen", Modifier.weight(1f), onSelectApps)
                    SheetAction(Icons.Rounded.Search, "Suche öffnen", Modifier.weight(1f), onSearch)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                SheetRow(Icons.Rounded.Edit, Color(0xFF63D68A), "Hintergrundbild", onWallpaper)
                SheetRow(Icons.Rounded.Star, Color(0xFFFFA45C), "Allgemeine Darstellung", onAppearance)
                SheetRow(Icons.Rounded.Home, SheetBlue, "Startbildschirm", onHomeSettings)
                if (LocalSettings.current.showSlogan) {
                    SloganLine(Modifier.padding(top = 4.dp, bottom = 6.dp), alpha = 0.35f)
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .border(2.dp, SheetBlue, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SheetBlue, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .border(2.dp, tint, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(22.dp))
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------------------------
// The Illuminati's controls
// ---------------------------------------------------------------------------------------------

private val SmartBlue = Color(0xFF4DA3FF)

/**
 * The Clawd Illuminati's "Be Smarter": the card that switches the mode, and under it the theme
 * (the five of Smart Launcher 6) with the sliders of its glass, the speed of opening apps and Glimmer
 * bound into the system.
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

    SmartCard(settings.beSmarter) { v -> onChange { it.withSmarter(v) } }
    Spacer(Modifier.height(18.dp))

    IlluminationSection("Be Smarter · Launcher-Thema") {
        SmartThemeList(settings.smartGlass) { theme -> onChange { it.withSmartGlass(theme) } }
        SmartNote("Wie in „Allgemeine Darstellung“ des Smart Launchers 6. Mit der Wahl springen die Regler und die Form der Icons auf die Werte des Themes – danach sind sie frei.")
    }

    IlluminationSection("Be Smarter · Oberfläche") {
        IlluminatiChoice("◐", "Farbe der Flächen (Ambient Design)", SmartColor.entries, settings.smartColor, { it.label }) { color -> onChange { it.copy(smartColor = color) } }
        SliderRow("Unschärferadius", settings.smartBlur, 0f..2.5f, "${"%.1f".format(settings.smartBlur)} %") { v -> onChange { it.copy(smartBlur = v) } }
        SliderRow("Lebendigkeit verwischen", settings.smartVibrancy, 0f..1f, "+${(settings.smartVibrancy * 100).roundToInt()} %") { v -> onChange { it.copy(smartVibrancy = v) } }
        SliderRow("Brechung", settings.smartRefraction, 0f..2.5f, "${(settings.smartRefraction * 100).roundToInt()} %") { v -> onChange { it.copy(smartRefraction = v) } }
        SliderRow("Tiefe", settings.smartDepth, 0.5f..3f, "${(settings.smartDepth * 100).roundToInt()} %") { v -> onChange { it.copy(smartDepth = v) } }
        SliderRow("Streuung", settings.smartDispersion, 0f..1.5f, "${(settings.smartDispersion * 100).roundToInt()} %") { v -> onChange { it.copy(smartDispersion = v) } }
        SliderRow("Glanz", settings.smartSpecular, 0f..2f, "${(settings.smartSpecular * 100).roundToInt()} %") { v -> onChange { it.copy(smartSpecular = v) } }
        SliderRow("Tönung", settings.smartTint, 0f..2.5f, "${(settings.smartTint * 100).roundToInt()} %") { v -> onChange { it.copy(smartTint = v) } }
        SmartNote("Brechung, Tiefe, Streuung und Lebendigkeit zeigen sich vor allem im Liquid Glass. Ambient Design und Material You färben die Flächen selbst; die Tönung gilt dort nicht.")
    }

    IlluminationSection("Be Smarter · Startbildschirm") {
        IlluminatiChoice("▢", "Form der Icons", IconShape.entries, settings.smartShape, { it.label }) { shape -> onChange { it.copy(smartShape = shape) } }
        IntSlider("Größe der Icons", settings.iconSize, 40..96, unit = " dp") { v -> onChange { it.copy(iconSize = v) } }
        IntSlider("Spalten", settings.columns, 3..6) { v -> onChange { it.copy(columns = v) } }
        IntSlider("Reihen", settings.rows, 4..8) { v -> onChange { it.copy(rows = v) } }
        IlluminationSwitch("⌕", "Smart-Suche", "Die Pille „Smart-Suche“ ganz unten, unter dem Dock.", settings.showSearchPill) { v -> onChange { it.copy(showSearchPill = v) } }
        IlluminationSwitch("◐", "Lesbarkeit verbessern", "Ein dunklerer Schleier über dem Hintergrundbild – für helle Bilder.", settings.smartReadable) { v -> onChange { it.copy(smartReadable = v) } }
        IlluminationSwitch("•", "Zeige Indikator", "Die Punkte, die zeigen, auf welcher Seite du bist.", settings.smartIndicator) { v -> onChange { it.copy(smartIndicator = v) } }
        SliderRow("Eckenradius der Widgets", settings.smartWidgetRadius.toFloat(), 0f..44f, "${settings.smartWidgetRadius} dp") { v -> onChange { it.copy(smartWidgetRadius = v.roundToInt()) } }
    }

    IlluminationSection("Be Smarter · Uhr") {
        IlluminatiChoice("◷", "Anordnung", SmartClockLayout.entries, settings.smartClockLayout, { it.label }) { layout -> onChange { it.copy(smartClockLayout = layout) } }
        SliderRow("Größe der Uhr", settings.smartClockSize, 48f..100f, "${settings.smartClockSize.roundToInt()} sp") { v -> onChange { it.copy(smartClockSize = v) } }
        IlluminatiChoice("Aa", "Schrift der Uhr", ClockFont.entries, settings.clockFont, { it.label }) { font -> onChange { it.copy(clockFont = font) } }
        IlluminationSwitch("☁", "Wetter an der Uhr", "Bild und Temperatur hinter dem Datum (der Ort steht in der Zentrale).", settings.smartClockWeather) { v -> onChange { it.copy(smartClockWeather = v) } }
        IlluminationSwitch("⏰", "Wecker an der Uhr", "Die Zeile „Wecker · 07:00“ unter dem Datum.", settings.smartClockAlarm) { v -> onChange { it.copy(smartClockAlarm = v) } }
        IlluminatiChoice("◷", "Stundenformat", SmartHours.entries, settings.smartHours, { it.label }) { hours -> onChange { it.copy(smartHours = hours) } }
        IlluminationSwitch("°", "Temperatur in Fahrenheit", "Das Wetter an der Uhr in °F statt in °C.", settings.smartFahrenheit) { v -> onChange { it.copy(smartFahrenheit = v) } }
        // The calendar's permission is asked once, here, by the system's own dialog – nothing is granted behind your back.
        val context = LocalContext.current
        val calendarAllowed = {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val askCalendar = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { ok -> onChange { it.copy(smartClockEvent = ok) } }
        IlluminationSwitch(
            "📅", "Nächster Termin an der Uhr",
            "Die Zeile „Termin · 11:00 Zahnarzt“ aus deinem Kalender. Android fragt dich einmal nach dem Zugriff auf den Kalender.",
            settings.smartClockEvent && calendarAllowed(),
        ) { v ->
            if (v && !calendarAllowed()) askCalendar.launch(android.Manifest.permission.READ_CALENDAR) else onChange { it.copy(smartClockEvent = v) }
        }
        IlluminationSwitch("☀", "Ganztägige Ereignisse ausblenden", "Geburtstage und andere Ereignisse ohne Uhrzeit stehen nicht in der Zeile.", settings.smartHideAllDay) { v -> onChange { it.copy(smartHideAllDay = v) } }
    }

    IlluminationSection("Be Smarter · Drawer") {
        IlluminationSwitch("▤", "Kategorieleiste", "Die Leiste mit den sechs Kategorien unten. Aus: eine einzige Liste mit allen Apps.", settings.smartCategoryBar) { v -> onChange { it.copy(smartCategoryBar = v) } }
        IlluminationSwitch("Ab", "Kategorientitel", "Der Name der Kategorie steht in der Leiste unter ihrem Zeichen.", settings.smartCategoryTitles) { v -> onChange { it.copy(smartCategoryTitles = v) } }
        IlluminationSwitch("↺", "Letzte Kategorie geöffnet", "Der Drawer öffnet sich auf der Kategorie, auf der du ihn verlassen hast.", settings.smartLastCategory) { v -> onChange { it.copy(smartLastCategory = v) } }
        IlluminatiChoice("⇄", "Übergangseffekt der Kategorien", SmartPageEffect.entries, settings.smartPageEffect, { it.label }) { effect -> onChange { it.copy(smartPageEffect = effect) } }
        IntSlider("Spalten im Drawer", settings.smartDrawerColumns, 3..6) { v -> onChange { it.copy(smartDrawerColumns = v) } }
        IntSlider("Abstand zwischen den Icons", settings.smartItemSpacing, 0..32, unit = " dp") { v -> onChange { it.copy(smartItemSpacing = v) } }
        IlluminationSwitch("Ab", "Namen unter den Icons", "Im Drawer stehen die Namen der Apps unter den Icons.", settings.smartDrawerNames) { v -> onChange { it.copy(smartDrawerNames = v) } }
        IlluminatiChoice("Aa", "Größe der Namen", LabelSize.entries, settings.labelSize, { it.label }) { size -> onChange { it.copy(labelSize = size) } }
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

/** The five themes in a list, each with its radio mark – the way Smart Launcher shows them. */
@Composable
private fun SmartThemeList(selected: SmartGlass, onPick: (SmartGlass) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        SmartGlass.entries.forEach { theme ->
            val on = theme == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(theme) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                ) {
                    Text(theme.label, color = Color.White, fontSize = 17.sp)
                    if (theme.note.isNotEmpty()) {
                        Text(theme.note, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                Box(
                    Modifier
                        .size(24.dp)
                        .border(2.dp, if (on) SmartBlue else Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(SmartBlue, CircleShape),
                        )
                    }
                }
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
            "Nach dem Vorbild von Smart Launcher 6: seine Uhr mit Datum, Wetter, Wecker und Termin, Icons ohne Namen, die Smart-Suche unten, ein App-Drawer " +
                "mit sechs Kategorien, eigener Suche und Übergangseffekten und seine fünf Themes bis zum Liquid Glass. Apps öffnen sich ohne Verzögerung. Die eigenen Looks und Effekte von " +
                "ZENITH treten solange zurück. Aus: alles wie vorher.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
