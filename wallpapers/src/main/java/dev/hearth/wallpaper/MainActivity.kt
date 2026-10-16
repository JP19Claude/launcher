package dev.hearth.wallpaper

import android.app.Activity
import android.app.AlertDialog
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
import dev.hearth.launcher.wallpaper.SuperPlaces
import dev.hearth.launcher.wallpaper.SuperScene
import dev.hearth.launcher.wallpaper.WallpaperUpdater
import kotlin.math.roundToInt

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
        option("Entsperr-Flug und Seitenwechsel", "zoom")
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
        // Where the body lands when it has finished turning (the Earth is a photograph and "Iwo Jima" a photo: no places).
        val placeCount = SuperPlaces.count(scene)
        if (placeCount > 0) {
            val names = Array(placeCount + 1) { SuperPlaces.label(scene, it) }
            var chosen = prefs.getInt("place", 0).coerceIn(0, placeCount)
            column.addView(
                TextView(this).apply {
                    text = "Beim Entsperren dreht sich der Körper und bleibt auf diesem Ort stehen – dort zoomt die Kamera heran."
                    setTextColor(0xFFB9C1CC.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(14), 0, 0)
                },
            )
            column.addView(
                Button(this).apply {
                    isAllCaps = false
                    text = "Landeplatz: ${names[chosen]}"
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Landeplatz")
                            .setSingleChoiceItems(names, chosen) { dialog, which ->
                                chosen = which
                                prefs.edit().putInt("place", which).apply()
                                text = "Landeplatz: ${names[which]}"
                                preview.reload()
                                dialog.dismiss()
                            }
                            .show()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        // The size of the planet or the Moon (the photo of "Iwo Jima" has none to resize).
        if (scene != SuperScene.Flag && scene != SuperScene.Photo) {
            val sizeLabel = TextView(this).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(10), 0, 0)
            }
            fun showSize(percent: Int) {
                sizeLabel.text = "Größe  $percent %"
            }
            val startSize = prefs.getFloat("size", 1f).coerceIn(0.5f, 1.6f)
            showSize((startSize * 100f).roundToInt())
            column.addView(sizeLabel)
            column.addView(
                SeekBar(this).apply {
                    max = 22
                    progress = ((startSize - 0.5f) / 0.05f).roundToInt().coerceIn(0, 22)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                val size = 0.5f + value * 0.05f
                                showSize((size * 100f).roundToInt())
                                if (!fromUser) return
                                prefs.edit().putFloat("size", size).apply()
                                preview.reload()
                            }

                            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                        },
                    )
                },
            )
        }
        column.addView(
            Button(this).apply {
                text = "Als Hintergrund festlegen"
                isAllCaps = false
                setOnClickListener { openWallpaper() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) },
        )
        addUpdate(column) { dp(it) }
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

    /**
     * The update of this app, from the app itself: it looks (when the app is opened and on a tap) for a newer build on GitHub,
     * downloads it and hands it to Android's installer, which asks once more. The only thing this app ever sends is that request.
     */
    private fun addUpdate(column: LinearLayout, dp: (Int) -> Int) {
        val installed = WallpaperUpdater.installed(this, packageName)
        val build = installed?.build ?: 0L
        val status = TextView(this).apply {
            setTextColor(0xFFB9C1CC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(6))
        }
        val button = Button(this).apply {
            isAllCaps = false
            text = "Nach Update suchen"
        }
        column.addView(
            TextView(this).apply {
                text = "Version ${installed?.version.orEmpty()} (Build $build)"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(22), 0, 0)
            },
        )
        column.addView(status)
        column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        var found: WallpaperUpdater.Update? = null
        var busy = false

        fun showResult(result: WallpaperUpdater.Check) {
            busy = false
            button.isEnabled = true
            when (result) {
                is WallpaperUpdater.Check.Newer -> {
                    found = result.update
                    val mb = result.update.size / 1_048_576.0
                    status.text = "Neu: Version ${result.update.version} (Build ${result.update.build}), ${"%.1f".format(mb)} MB.\n${result.update.notes.lines().firstOrNull { it.isNotBlank() }.orEmpty()}"
                    button.text = "Jetzt aktualisieren"
                }
                WallpaperUpdater.Check.UpToDate -> {
                    found = null
                    status.text = "Das ist die neueste Version."
                    button.text = "Nach Update suchen"
                }
                is WallpaperUpdater.Check.Failed -> {
                    found = null
                    status.text = result.reason
                    button.text = "Nach Update suchen"
                }
            }
        }

        fun look() {
            if (busy) return
            busy = true
            button.isEnabled = false
            status.text = "Suche nach Updates …"
            Thread {
                val result = WallpaperUpdater.check(packageName, build)
                runOnUiThread { showResult(result) }
            }.start()
        }

        fun install(update: WallpaperUpdater.Update) {
            if (busy) return
            if (!WallpaperUpdater.canInstall(this)) {
                status.text = "Erlaube dieser App erst „Unbekannte Apps installieren“ – die Einstellung öffnet sich. Danach noch einmal tippen."
                WallpaperUpdater.askInstallPermission(this)
                return
            }
            busy = true
            button.isEnabled = false
            status.text = "Lade … 0 %"
            Thread {
                val file = WallpaperUpdater.download(this, packageName, update) { p ->
                    runOnUiThread { status.text = "Lade … ${(p * 100).roundToInt()} %" }
                }
                runOnUiThread {
                    busy = false
                    button.isEnabled = true
                    status.text = when {
                        file == null -> "Der Download hat nicht geklappt – tipp noch einmal."
                        WallpaperUpdater.install(this, file) -> "Android fragt gleich, ob du installieren willst."
                        else -> "Die Installation ließ sich nicht starten."
                    }
                }
            }.start()
        }

        button.setOnClickListener {
            val update = found
            if (update == null) look() else install(update)
        }
        look()
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
