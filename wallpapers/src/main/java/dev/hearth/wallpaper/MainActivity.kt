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
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.hearth.launcher.wallpaper.PlanetPictures
import dev.hearth.launcher.wallpaper.SuperDiag
import dev.hearth.launcher.wallpaper.SuperPlaces
import dev.hearth.launcher.wallpaper.SuperScene
import dev.hearth.launcher.wallpaper.SuperVideo
import dev.hearth.launcher.wallpaper.WallpaperUpdater
import kotlin.math.roundToInt

/**
 * This app: the scene big, what it is, a few options and the button that makes it the phone's wallpaper. Plain views, so
 * the app stays small.
 */
/** The request code of the video chooser. */
private const val PICK_VIDEO = 7301

class MainActivity : Activity() {

    private lateinit var preview: ScenePreview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = SceneConfig.scene(this)
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
                text = if (SceneConfig.all) "ZENITH Hintergründe" else "ZENITH ${scene.label}"
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

        // The app with every scene: the scene is chosen here (the screen is built anew for it).
        if (SceneConfig.all) {
            val names = Array(SceneConfig.choices.size) { SceneConfig.choices[it].label }
            column.addView(
                Button(this).apply {
                    isAllCaps = false
                    text = "Szene: ${scene.label}"
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Szene")
                            .setSingleChoiceItems(names, SceneConfig.choices.indexOf(scene).coerceAtLeast(0)) { dialog, which ->
                                dialog.dismiss()
                                if (SceneConfig.choices[which] != scene) {
                                    val chosen = SceneConfig.choices[which]
                                    SceneConfig.choose(this@MainActivity, chosen)
                                    // A video scene with no video yet: the chooser opens by itself on the new page.
                                    prefs.edit().putBoolean("askVideo", chosen.isVideo && !SuperVideo.exists(this@MainActivity, chosen)).apply()
                                    recreate()
                                }
                            }
                            .show()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) },
            )
        }

        // A video of your own: a file of the gallery, copied into the app and played muted in a loop. The first thing on the page, as nothing runs without it.
        if (scene.isVideo) {
            val have = SuperVideo.exists(this, scene)
            column.addView(
                Button(this).apply {
                    isAllCaps = false
                    text = if (have) "${scene.label}: anderes Video wählen" else "${scene.label}: Video aus der Galerie wählen"
                    setOnClickListener { pickVideo() }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) },
            )
            column.addView(
                TextView(this).apply {
                    text = if (have) {
                        "Das Video läuft stumm in einer Endlosschleife, bildfüllend. Die App bringt kein Video mit – das hier ist deine Datei."
                    } else {
                        "Noch kein Video gewählt – ohne Video bleibt der Hintergrund ein leerer Sternenhimmel. Tippe auf die Taste oben und nimm die Datei aus deiner Galerie oder dem Download-Ordner."
                    }
                    setTextColor(if (have) 0xFFB9C1CC.toInt() else 0xFFE0A458.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, 0, 0, dp(10))
                },
            )
        }

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
        // (a video has no tilt, flight, stars or speed)
        if (!scene.isVideo) {
            option("Neigung folgen", "tilt")
            option("Entsperr-Flug und Seitenwechsel", "zoom")
            option("Sterne", "stars")
        }

        if (!scene.isVideo) column.addView(
            TextView(this).apply {
                text = "Geschwindigkeit"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(10), 0, 0)
            },
        )
        if (!scene.isVideo) column.addView(
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
        // The always-on screen: Android keeps the phone's own always-on display for the maker's apps, so this is a screen of its own –
        // the body dimmed on black with the time – and a screen saver.
        val aodBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.getBoolean("aod", false)) View.VISIBLE else View.GONE
        }
        column.addView(
            Switch(this).apply {
                text = "Always-on-Bildschirm (AOD)"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                isChecked = prefs.getBoolean("aod", false)
                setPadding(0, dp(14), 0, dp(4))
                setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean("aod", on).apply()
                    aodBox.visibility = if (on) View.VISIBLE else View.GONE
                }
            },
        )
        column.addView(
            TextView(this).apply {
                text = "Der Körper gedimmt auf Schwarz, mit der Uhrzeit; er wandert ab und zu ein paar Pixel (gegen Einbrennen). Auf dem echten AOD des Telefons kann ein Live-Wallpaper nicht zeichnen (das darf Android nur dem Hersteller überlassen; ZENITH schafft es über seine Bedienungshilfe Glimmer) – darum läuft das hier als eigener Bildschirm und als Bildschirmschoner."
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
        )
        aodBox.addView(
            Button(this).apply {
                isAllCaps = false
                text = "AOD-Bildschirm jetzt starten"
                setOnClickListener { startActivity(Intent(this@MainActivity, SceneAodActivity::class.java)) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        aodBox.addView(
            TextView(this).apply {
                text = "Tippen beendet ihn; nach sechs Stunden endet er von selbst."
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(2), 0, 0)
            },
        )
        aodBox.addView(
            Button(this).apply {
                isAllCaps = false
                text = "Als Bildschirmschoner einrichten"
                setOnClickListener {
                    val opened = listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS).any { action ->
                        runCatching { startActivity(Intent(action)) }.isSuccess
                    }
                    if (!opened) Toast.makeText(this@MainActivity, "Dieses Telefon bietet keine Bildschirmschoner-Einstellung.", Toast.LENGTH_LONG).show()
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) },
        )
        aodBox.addView(
            TextView(this).apply {
                text = "Dort „${scene.label}“ wählen (Beim Laden): dann startet der AOD-Bildschirm am Ladegerät von selbst."
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(2), 0, 0)
            },
        )
        val dimLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(10), 0, 0)
        }
        val startDim = prefs.getFloat("aodDim", 0.45f).coerceIn(0.1f, 0.8f)
        dimLabel.text = "AOD-Helligkeit  ${(startDim * 100f).roundToInt()} %"
        aodBox.addView(dimLabel)
        aodBox.addView(
            SeekBar(this).apply {
                max = 14
                progress = ((startDim - 0.1f) / 0.05f).roundToInt().coerceIn(0, 14)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                            val dim = 0.1f + value * 0.05f
                            dimLabel.text = "AOD-Helligkeit  ${(dim * 100f).roundToInt()} %"
                            if (fromUser) prefs.edit().putFloat("aodDim", dim).apply()
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                    },
                )
            },
        )
        aodBox.addView(
            Switch(this).apply {
                text = "Uhrzeit im AOD"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                isChecked = prefs.getBoolean("aodClock", true)
                setPadding(0, dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("aodClock", on).apply() }
            },
        )
        column.addView(aodBox)
        // Where the body lands when it has finished turning (the Earth is a photograph and "Iwo Jima" a photo: no places).
        val placeCount = SuperPlaces.count(scene)
        if (placeCount > 0) {
            val names = Array(placeCount + 1) { SuperPlaces.label(scene, it) }
            val placeKey = SceneConfig.placeKey(scene)
            var chosen = prefs.getInt(placeKey, 0).coerceIn(0, placeCount)
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
                                prefs.edit().putInt(placeKey, which).apply()
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
        // The size of the planet or the Moon (the photo of "Iwo Jima" and the landscapes have none to resize).
        if (scene != SuperScene.Flag && scene != SuperScene.Photo && !scene.isScape && !scene.isVideo) {
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
        // Where the body hangs on the lock screen: moved right/left and down/up, e.g. up behind the clock.
        if (scene != SuperScene.Flag && scene != SuperScene.Photo && scene != SuperScene.Geometry && !scene.isScape && !scene.isVideo) {
            fun positionSlider(title: String, key: String) {
                val label = TextView(this).apply {
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setPadding(0, dp(10), 0, 0)
                }
                fun show(percent: Int) {
                    label.text = "$title  ${if (percent > 0) "+" else ""}$percent %"
                }
                val start = prefs.getFloat(key, 0f).coerceIn(-0.45f, 0.45f)
                show((start * 100f).roundToInt())
                column.addView(label)
                column.addView(
                    SeekBar(this).apply {
                        max = 90
                        progress = ((start + 0.45f) / 0.01f).roundToInt().coerceIn(0, 90)
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                    val offset = -0.45f + value * 0.01f
                                    show((offset * 100f).roundToInt())
                                    if (!fromUser) return
                                    prefs.edit().putFloat(key, offset).apply()
                                    preview.reload()
                                }

                                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                            },
                        )
                    },
                )
            }
            positionSlider("Position waagerecht (← links, rechts →)", "offsetX")
            positionSlider("Position senkrecht (↑ oben, unten ↓)", "offsetY")
        }
        column.addView(
            Button(this).apply {
                text = "Als Hintergrund festlegen"
                isAllCaps = false
                setOnClickListener { openWallpaper() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) },
        )
        // Ten more pictures of this planet, to keep (from the NASA library), in several sizes.
        column.addView(
            Button(this).apply {
                isAllCaps = false
                text = "${PlanetPictures.COUNT} Bilder zu ${scene.label} herunterladen"
                setOnClickListener { startActivity(Intent(this@MainActivity, PicturesActivity::class.java)) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) },
        )
        // The live wallpapers of the phone (OnePlus' own first), in a compartment of their own.
        column.addView(
            Button(this).apply {
                isAllCaps = false
                text = "OnePlus Live-Wallpaper des Telefons"
                setOnClickListener { startActivity(Intent(this@MainActivity, PhoneWallpapersActivity::class.java)) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) },
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
        // Just chosen: a video scene with no video asks for one right away.
        if (scene.isVideo && prefs.getBoolean("askVideo", false)) {
            prefs.edit().putBoolean("askVideo", false).apply()
            if (!SuperVideo.exists(this, scene)) column.post { pickVideo() }
        }
        // What went wrong the last time with the graphics or a video (a wallpaper that stays black says nothing else).
        SuperDiag.last(this)?.let { text ->
            column.addView(
                TextView(this).apply {
                    this.text = "Letzte Meldung der Grafik oder des Videos (steht der Hintergrund still und schwarz, hilft sie bei der Suche):\n$text"
                    setTextColor(0xFFE0A458.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextIsSelectable(true)
                    setPadding(0, dp(14), 0, 0)
                },
            )
            column.addView(
                Button(this).apply {
                    isAllCaps = false
                    this.text = "Meldung löschen"
                    setOnClickListener {
                        SuperDiag.clear(this@MainActivity)
                        recreate()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) },
            )
        }
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

    /** Opens the gallery's chooser for the video of the scene. */
    private fun pickVideo() {
        val pick = Intent(Intent.ACTION_GET_CONTENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE)
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(pick, "Video wählen"), PICK_VIDEO)
    }

    /** The video the person picked: copied into the app (off the main thread), then the page starts over with it. */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val chosen = SceneConfig.scene(this)
        if (!chosen.isVideo) return
        Toast.makeText(this, "Das Video wird übernommen …", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = SuperVideo.save(this, chosen, uri)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (ok) {
                    // (a new stamp: the wallpaper and the preview load the new video)
                    getSharedPreferences(SceneConfig.PREFS, Context.MODE_PRIVATE).edit().putLong("photo", System.currentTimeMillis()).apply()
                    recreate()
                } else {
                    Toast.makeText(this, "Dieses Video ließ sich nicht übernehmen (kein Video, zu groß oder nicht lesbar).", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** Android's preview of the wallpaper (it asks "Hintergrund festlegen" there); the chooser if the phone has no direct way. */
    private fun openWallpaper() {
        val chosen = SceneConfig.scene(this)
        if (chosen.isVideo && !SuperVideo.exists(this, chosen)) {
            Toast.makeText(this, "Wähle zuerst ein Video – ohne Video bleibt der Hintergrund schwarz.", Toast.LENGTH_LONG).show()
            return
        }
        val component = ComponentName(this, SceneWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        if (runCatching { startActivity(direct) }.isFailure) {
            runCatching { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
        }
    }
}
