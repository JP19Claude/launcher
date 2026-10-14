package dev.hearth.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs

/** An installed icon pack, as shown in the settings. */
@Immutable
class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * Finds and loads icon packs in the common ADW / Nova format
 * (an `appfilter.xml` that maps app components to drawables).
 */
class IconPackRepository(private val context: Context) {

    private val pm = context.packageManager

    @Suppress("DEPRECATION")
    fun installedPacks(): List<IconPackInfo> {
        val iconPx = (context.resources.displayMetrics.density * 40).toInt()
        return PACK_ACTIONS
            .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    IconPackInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching {
                            pm.getApplicationIcon(appInfo).toBitmap(iconPx, iconPx).asImageBitmap()
                        }.getOrNull(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Parses the pack's appfilter. Returns null if the pack is missing or unreadable. */
    fun load(packageName: String): IconPack? = runCatching {
        val res = pm.getResourcesForApplication(packageName)
        val parser = openAppFilter(res, packageName) ?: return null
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        val name = component?.let(::normalizeComponent)
                        if (name != null && !drawable.isNullOrBlank()) {
                            byComponent.putIfAbsent(name, drawable)
                            byPackage.putIfAbsent(name.substringBefore('/'), drawable)
                        }
                    }
                    "iconback" -> for (i in 0 until parser.attributeCount) {
                        parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }?.let(backs::add)
                    }
                    "iconmask" -> mask = parser.getAttributeValue(null, "img1") ?: mask
                    "iconupon" -> upon = parser.getAttributeValue(null, "img1") ?: upon
                    "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: scale
                }
            }
            event = parser.next()
        }
        IconPack(packageName, res, byComponent, byPackage, backs, mask, upon, scale.coerceIn(0.2f, 1.2f))
    }.getOrNull()

    @SuppressLint("DiscouragedApi")
    private fun openAppFilter(res: Resources, packageName: String): XmlPullParser? {
        val xmlId = res.getIdentifier("appfilter", "xml", packageName)
        if (xmlId != 0) return res.getXml(xmlId)
        return runCatching {
            val stream = res.assets.open("appfilter.xml")
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
        }.getOrNull()
    }

    /** "ComponentInfo{com.app/com.app.Main}" -> "com.app/com.app.Main" */
    private fun normalizeComponent(raw: String): String? {
        val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
        return ComponentName.unflattenFromString(inner)?.flattenToString()
    }

    companion object {
        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "ginlemon.smartlauncher.THEMES",
        )
    }
}

class IconPack(
    val packageName: String,
    private val res: Resources,
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float,
) {
    /** The pack's own icon for this app, if it has one. */
    fun iconFor(component: ComponentName, densityDpi: Int): Drawable? {
        val name = byComponent[component.flattenToString()] ?: byPackage[component.packageName] ?: return null
        return drawable(name, densityDpi)
    }

    /**
     * For apps the pack doesn't cover: puts the original icon on the pack's
     * back plate, cut by its mask and covered by its overlay, so it still fits in.
     */
    fun adapt(original: Drawable, component: ComponentName, sizePx: Int, densityDpi: Int): Bitmap? {
        if (backs.isEmpty()) return null
        val back = drawable(backs[abs(component.hashCode()) % backs.size], densityDpi) ?: return null

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        back.bounds = Rect(0, 0, sizePx, sizePx)
        back.draw(canvas)

        // Original icon on its own layer, so the mask only cuts the icon.
        val layer = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        val inner = (sizePx * scale).toInt()
        val offset = (sizePx - inner) / 2
        original.bounds = Rect(offset, offset, offset + inner, offset + inner)
        original.draw(layerCanvas)
        mask?.let { drawable(it, densityDpi) }?.let { maskDrawable ->
            val maskBitmap = maskDrawable.toBitmap(sizePx, sizePx)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            layerCanvas.drawBitmap(maskBitmap, 0f, 0f, paint)
        }
        canvas.drawBitmap(layer, 0f, 0f, null)

        upon?.let { drawable(it, densityDpi) }?.let { overlay ->
            overlay.bounds = Rect(0, 0, sizePx, sizePx)
            overlay.draw(canvas)
        }
        return out
    }

    @SuppressLint("DiscouragedApi")
    private fun drawable(name: String, densityDpi: Int): Drawable? = runCatching {
        val id = res.getIdentifier(name, "drawable", packageName)
            .takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) null else res.getDrawableForDensity(id, densityDpi, null)
    }.getOrNull()
}
