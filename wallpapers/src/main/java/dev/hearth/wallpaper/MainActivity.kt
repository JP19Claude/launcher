package dev.hearth.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * This app: the scene big, what it is, a few options and the button that makes it the phone's wallpaper. Plain views, so
 * the app stays small.
 */
class MainActivity : Activity() {

    private lateinit var preview: ScenePreview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = SceneConfig.scene
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val prefs = getSharedPreferences(SceneConfig.PREFS, Context.MODE_PRIVATE)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        preview = ScenePreview(this)
        column.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.5f).toInt()),
        )
        column.addView(
            TextView(this).apply {
                text = "ZENITH ${scene.label}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            },
        )
        column.addView(
            TextView(this).apply {
                text = scene.note
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, dp(12))
            },
        )

        fun option(label: String, key: String) {
            column.addView(
                Switch(this).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    isChecked = prefs.getBoolean(key, true)
                    setPadding(0, dp(8), 0, dp(8))
                    setOnCheckedChangeListener { _, on ->
                        prefs.edit().putBoolean(key, on).apply()
                        preview.reload()
                    }
                },
            )
        }
        option("Neigung folgen", "tilt")
        option("Zoom beim Einschalten und Seitenwechsel", "zoom")
        option("Sterne", "stars")

        column.addView(
            TextView(this).apply {
                text = "Geschwindigkeit"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(10), 0, 0)
            },
        )
        column.addView(
            SeekBar(this).apply {
                max = 28
                progress = ((prefs.getFloat("speed", 1f) - 0.2f) / 0.1f).toInt().coerceIn(0, 28)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                            if (!fromUser) return
                            prefs.edit().putFloat("speed", 0.2f + value * 0.1f).apply()
                            preview.reload()
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                    },
                )
            },
        )
        column.addView(
            Button(this).apply {
                text = "Als Hintergrund festlegen"
                isAllCaps = false
                setOnClickListener { openWallpaper() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) },
        )
        column.addView(
            TextView(this).apply {
                text = "Die Bilder sind von der NASA (frei, ohne Copyright), aus „NASA 3D Resources“ – ein Hintergrund aus ZENITH, als eigene App."
                setTextColor(0xFF7F8896.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(14), 0, 0)
            },
        )
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(0xFF0B0F1A.toInt())
                isFillViewport = true
                fitsSystemWindows = true
                addView(column)
            },
        )
    }

    /** Android's preview of the wallpaper (it asks "Hintergrund festlegen" there); the chooser if the phone has no direct way. */
    private fun openWallpaper() {
        val component = ComponentName(this, SceneWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        if (runCatching { startActivity(direct) }.isFailure) {
            runCatching { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
        }
    }
}
