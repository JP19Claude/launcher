package dev.hearth.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.hearth.launcher.wallpaper.PictureSize
import dev.hearth.launcher.wallpaper.PlanetPicture
import dev.hearth.launcher.wallpaper.PlanetPictures
import dev.hearth.launcher.wallpaper.SuperScene
import kotlin.math.roundToInt

/**
 * Ten more pictures of this app's planet (or moon), from the NASA Image and Video Library: the small ones are shown here, a tap
 * on one asks which size to keep – small, medium, large or the original – and saves it in the gallery (Pictures/ZENITH). Plain
 * views, so the app stays small.
 */
class PicturesActivity : Activity() {

    private lateinit var scene: SuperScene
    private lateinit var status: TextView
    private lateinit var grid: LinearLayout
    private var busy = false
    private var shown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scene = SceneConfig.scene(this)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }
        column.addView(
            TextView(this).apply {
                text = "Bilder: ${scene.label}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        column.addView(
            TextView(this).apply {
                text = "Zehn Aufnahmen aus der NASA-Bildbibliothek. Tipp auf ein Bild, dann wählst du die Größe – es landet in deiner Galerie (Bilder/ZENITH)."
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, dp(10))
            },
        )
        status = TextView(this).apply {
            setTextColor(0xFFB9C1CC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, dp(8))
        }
        column.addView(status)
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(grid)
        column.addView(
            TextView(this).apply {
                text = "Bilder: NASA Image and Video Library (images.nasa.gov). NASA-Bilder sind in der Regel gemeinfrei; Titel und Nummer jedes Bildes stehen beim Herunterladen. Es wird nur die Suche nach dem Planeten gesendet."
                setTextColor(0xFF7F8896.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(18), 0, 0)
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
        load()
    }

    /** Asks the library for the ten pictures, shows them, then fetches their small pictures one after the other. */
    private fun load() {
        if (busy) return
        busy = true
        grid.removeAllViews()
        status.text = "Suche Bilder von ${scene.label} …"
        Thread {
            val result = try {
                PlanetPictures.search(scene)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                busy = false
                if (result == null || result.isEmpty()) {
                    status.text = if (result == null) "Die NASA-Bibliothek ist gerade nicht erreichbar – bist du online?" else "Die Bibliothek hat zu ${scene.label} nichts gefunden."
                    grid.addView(
                        Button(this).apply {
                            isAllCaps = false
                            text = "Noch einmal versuchen"
                            setOnClickListener { load() }
                        },
                    )
                } else {
                    shown = result.size
                    status.text = "$shown Bilder – tippen zum Herunterladen."
                    show(result)
                }
            }
        }.start()
    }

    private fun show(pictures: List<PlanetPicture>) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val cell = (resources.displayMetrics.widthPixels - dp(16 * 2 + 10)) / 2
        val views = ArrayList<ImageView>()
        var row: LinearLayout? = null
        for ((index, picture) in pictures.withIndex()) {
            if (index % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF161C2B.toInt())
            }
            views.add(image)
            val holder = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { choose(picture) }
                addView(image, LinearLayout.LayoutParams(cell, cell))
                addView(
                    TextView(this@PicturesActivity).apply {
                        text = picture.title
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.START
                        setPadding(0, dp(4), 0, 0)
                    },
                    LinearLayout.LayoutParams(cell, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }
            row!!.addView(holder, LinearLayout.LayoutParams(cell, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (index % 2 == 0) rightMargin = dp(10) })
        }
        Thread {
            for ((index, picture) in pictures.withIndex()) {
                val bitmap: Bitmap? = PlanetPictures.thumbnail(picture.thumb, 520)
                runOnUiThread { if (bitmap != null) views[index].setImageBitmap(bitmap) }
            }
        }.start()
    }

    /** The sizes of [picture] are asked; then a list to choose from. */
    private fun choose(picture: PlanetPicture) {
        if (busy) return
        busy = true
        status.text = "Prüfe die Größen …"
        Thread {
            val sizes = try {
                PlanetPictures.sizes(picture)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                busy = false
                if (sizes.isEmpty()) {
                    status.text = "Zu diesem Bild gibt es gerade keine Datei."
                    return@runOnUiThread
                }
                status.text = "$shown Bilder – tippen zum Herunterladen."
                val labels = Array(sizes.size) { i ->
                    val mb = PlanetPictures.megabytes(sizes[i].size)
                    sizes[i].label + if (mb.isNotEmpty()) " – $mb" else ""
                }
                AlertDialog.Builder(this)
                    .setTitle(picture.title)
                    .setItems(labels) { _, which -> download(picture, sizes[which]) }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }.start()
    }

    private fun download(picture: PlanetPicture, size: PictureSize) {
        if (busy) return
        busy = true
        status.text = "Lade … 0 %"
        Thread {
            val saved = PlanetPictures.download(this, scene, picture, size) { p ->
                runOnUiThread { status.text = "Lade … ${(p * 100).roundToInt()} %" }
            }
            runOnUiThread {
                busy = false
                if (saved == null) {
                    status.text = "Das Herunterladen hat nicht geklappt – versuch es noch einmal."
                } else {
                    val mb = PlanetPictures.megabytes(saved.bytes)
                    status.text = "Gespeichert: ${saved.width} × ${saved.height} Pixel, $mb – in ${saved.where}.\nNASA-Nummer: ${picture.id}"
                    Toast.makeText(this, "In der Galerie: ${saved.where}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
