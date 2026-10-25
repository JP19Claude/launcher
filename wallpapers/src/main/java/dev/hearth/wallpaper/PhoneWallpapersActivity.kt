package dev.hearth.wallpaper

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.hearth.launcher.wallpaper.PhoneWallpaper
import dev.hearth.launcher.wallpaper.PhoneWallpapers

/**
 * A compartment of its own for the live wallpapers of the phone – OnePlus' own first, then everybody else's. They are the ones that are
 * installed on this phone: the app shows them with the picture they have in Android's picker and hands the one you tap to Android's
 * preview, which sets it. Nothing of them is copied into this app or passed on. Plain views, so the app stays small.
 */
class PhoneWallpapersActivity : Activity() {

    private lateinit var column: LinearLayout
    private var density = 1f

    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(0xFF0B0F1A.toInt())
                isFillViewport = true
                fitsSystemWindows = true
                addView(column)
            },
        )
        show(null)
    }

    /** Back from Android's preview (or from installing a wallpaper app): look again. */
    override fun onResume() {
        super.onResume()
        Thread {
            val list = PhoneWallpapers.load(this)
            runOnUiThread { if (!isFinishing) show(list) }
        }.start()
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, top, 0, 0)
    }

    private fun show(list: List<PhoneWallpaper>?) {
        column.removeAllViews()
        column.addView(text("OnePlus Live-Wallpaper", 26f, Color.WHITE, bold = true))
        column.addView(
            text(
                "Die Live-Wallpaper, die auf diesem Telefon installiert sind – die von OnePlus zuerst. Ein Tippen öffnet Androids Vorschau, dort „Hintergrund festlegen“. " +
                    "Die Wallpaper gehören OnePlus und ihren Machern: Diese App zeigt sie nur, sie baut sie nicht ein und gibt sie nicht weiter.",
                14f, 0xFFB9C1CC.toInt(), top = dp(6),
            ),
        )
        when {
            list == null -> column.addView(text("Ich suche die Live-Wallpaper auf diesem Telefon …", 14f, 0xFFB9C1CC.toInt(), top = dp(18)))
            else -> {
                val onePlus = list.filter { it.onePlus }
                val others = list.filter { !it.onePlus }
                column.addView(text("OnePlus", 13f, 0xFF7F8896.toInt(), bold = true, top = dp(22)))
                if (onePlus.isEmpty()) {
                    column.addView(
                        text(
                            "Auf diesem Telefon sind keine Live-Wallpaper von OnePlus installiert. Auf einem OnePlus-Telefon bringt das System sie selbst mit; sie erscheinen hier, sobald sie auf dem Telefon sind.",
                            14f, 0xFFB9C1CC.toInt(), top = dp(6),
                        ),
                    )
                } else {
                    onePlus.forEach { column.addView(row(it)) }
                }
                if (others.isNotEmpty()) {
                    column.addView(text("Weitere Live-Wallpaper auf diesem Telefon", 13f, 0xFF7F8896.toInt(), bold = true, top = dp(22)))
                    others.forEach { column.addView(row(it)) }
                }
            }
        }
        column.addView(
            Button(this).apply {
                isAllCaps = false
                text = "Alle Live-Wallpaper in Androids Auswahl"
                setOnClickListener { PhoneWallpapers.openPicker(this@PhoneWallpapersActivity) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) },
        )
    }

    /** One live wallpaper: its picture, its name and who made it. */
    private fun row(wallpaper: PhoneWallpaper): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        setOnClickListener { PhoneWallpapers.openPreview(this@PhoneWallpapersActivity, wallpaper) }
        addView(
            ImageView(this@PhoneWallpapersActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x14FFFFFF)
                wallpaper.thumbnail?.let { setImageBitmap(it) }
            },
            LinearLayout.LayoutParams(dp(54), dp(80)),
        )
        val detail = listOf(wallpaper.author, wallpaper.summary).filter { it.isNotBlank() }.joinToString(" – ")
        addView(
            LinearLayout(this@PhoneWallpapersActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(text(wallpaper.label, 15f, Color.WHITE))
                if (detail.isNotBlank()) addView(text(detail, 13f, 0xFFB9C1CC.toInt()).apply { maxLines = 2 })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }
}
