package dev.hearth.launcher.wallpaper

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.io.File
import kotlin.random.Random

/*
 * ZENITH's Super-Wallpaper: wallpapers that move, in the manner of the "super wallpapers" some phones have. The Moon and
 * the Earth are real photographs of NASA, the other planets and moons are turned from real maps of NASA (they ship inside
 * the app, see app/src/one/assets/planets/CREDITS.txt) laid round a sphere pixel by pixel. They turn or hang in the dark,
 * drift with the phone's tilt and with the home page. On the lock screen the whole body hangs calm and small; when the phone
 * is unlocked the camera dives in towards the surface, and out again when it locks (the "depth", see [SuperMotion] and
 * SuperDirector). "Foto" shows any photo of the gallery in the same way. One renderer for the live wallpaper, the
 * settings' preview and the glass.
 */

/** The scenes ("mods") one can choose. */
enum class SuperScene(val label: String, val note: String) {
    Moon("Mond", "Der echte Mond: eine Aufnahme der NASA (Apollo), klein und ruhig auf dem Sperrbildschirm, beim Entsperren fliegt die Kamera heran; er wandert mit der Neigung und den Seiten."),
    Earth("Erde", "Die Erde aus dem All, eine Aufnahme der NASA – Wolken, Ozeane, Kontinente, mit dem Glanz der Atmosphäre am Rand."),
    Mars("Mars", "Der rote Planet, gedreht aus der echten Karte der NASA: dunkle Ebenen, Schluchten, die Polkappe."),
    Venus("Venus", "Die Venus, wie das Radar der Raumsonde Magellan sie sah (NASA): Hochländer, Lavaebenen, Krater unter den Wolken."),
    Jupiter("Jupiter", "Jupiter mit seinen Wolkenbändern, Wirbeln und dem großen roten Fleck – gedreht aus einer Karte der NASA."),
    Saturn("Saturn", "Der Ringplanet, gedreht aus einer Karte der NASA; die Ringe liegen davor."),
    Neptune("Neptun", "Neptun in tiefem Blau mit seinen Wolkenbändern – gedreht aus einer Karte der NASA."),
    Pluto("Pluto", "Pluto, wie die Sonde New Horizons ihn sah: helle Ebenen, dunkle Gebiete, rötliche Hänge (NASA)."),
    Io("Io", "Der vulkanische Jupitermond Io, gedreht aus einer Karte der NASA."),
    Europa("Europa", "Der Jupitermond Europa mit seinen Eisrissen – gedreht aus einer Karte der NASA."),
    Ganymede("Ganymed", "Ganymed, der größte Mond im Sonnensystem, gedreht aus einer Karte der NASA."),
    Flag(
        "Iwo Jima",
        "Das Foto vom Iwo-Jima-Denkmal: der Vollmond hinter den Marines, die US-Flagge im Wind. Auf dem Sperrbildschirm fliegt die Kamera von der Flagge oben links über das ganze Bild, nach dem Entsperren fährt sie auf Flagge und Mond zu; ein sanftes Licht liegt auf der Flagge.",
    ),
    Geometry("Geometrie", "Ein Ikosaeder aus Licht mit einem zweiten Körper darin, die beide umeinander drehen."),
    Photo(
        "Eigenes Foto",
        "Ein Foto der NASA aus der Galerie als Hintergrund – was du wählst. Es folgt der Neigung des Telefons, rückt mit den Seiten und kommt beim Entsperren näher. Das Foto suchst du in der Wallpaper-Galerie aus („Als Live-Wallpaper“).",
    ),
    Mountains(
        "Berge",
        "Gebirge in fünf Schichten mit Dunst, Wolken und Schnee – von ZENITH selbst gezeichnet, im Stil der Landschafts-Hintergründe von Telefonen. Der Himmel folgt der Uhrzeit: Morgenrot, Mittag, Abendrot, Mond und Sterne mit einer Sternschnuppe. Beim Entsperren fliegt die Kamera ins Tal.",
    ),
    Silk(
        "Smaragd",
        "Fließende smaragdgrüne Seide mit glänzenden Falten – von ZENITH selbst gezeichnet, im Stil der Wellen-Hintergründe von Telefonen. Sie bewegt sich ruhig von allein, folgt der Neigung und kommt beim Entsperren näher.",
    ),
    Spheres(
        "Sphären",
        "Große blaue Kugeln, die sich überlappen und langsam umeinander ziehen – mit leuchtendem Rand auf der Lichtseite und Schatten aufeinander, vor einem Verlauf von Indigo zu Lavendel. Von ZENITH selbst gezeichnet, im Stil moderner Handy-Hintergründe; sie folgen der Neigung und kommen beim Entsperren näher.",
    ),
    Spray(
        "Gischt",
        "Ein Schwarm feiner Teilchen, der als breite Welle durchs Bild zieht – Türkis und Weiß, unten ein korallroter Schwall – vor Schwarz. Von ZENITH selbst gezeichnet, im Stil moderner Handy-Hintergründe; die Teilchen strömen von allein, die Welle wogt langsam, und beim Entsperren kommt die Kamera näher.",
    );

    /** A landscape ZENITH draws itself (no body in the dark): it has no size to set and no sphere, and shows the whole picture on the always-on screen. */
    val isScape: Boolean get() = this == Mountains || this == Silk || this == Spheres || this == Spray
}

/** What the landscapes need besides their pictures: the hour of the day (the sky of "Berge" follows it). */
internal object SuperScape {
    /** The local time as hours 0 … 24 (7.5 = half past seven), read at most once a second. */
    fun hourNow(): Float {
        val now = System.currentTimeMillis()
        if (now - stamp > 1000L) {
            stamp = now
            val c = java.util.Calendar.getInstance()
            hour = c.get(java.util.Calendar.HOUR_OF_DAY) + c.get(java.util.Calendar.MINUTE) / 60f + c.get(java.util.Calendar.SECOND) / 3600f
        }
        return hour
    }

    /** The hour the previews show: the golden hour, when the mountains look their best. */
    const val SHOWN_HOUR = 18.3f

    private var stamp = 0L
    private var hour = 12f
}

/** What the wallpaper is set to (read from ZENITH's settings). */
data class SuperParams(
    val scene: SuperScene = SuperScene.Moon,
    /** The scene shifts with the phone's tilt. */
    val tilt: Boolean = true,
    /** The flight from the lock screen to the home screen (and back), and moving with the home page. */
    val zoomOnWake: Boolean = true,
    /** How fast everything turns (0.2 … 3). */
    val speed: Float = 1f,
    val stars: Boolean = true,
    /** How big the planet or the Moon is, as a factor of its normal size (0.5 … 1.6; the photos ignore it). */
    val size: Float = 1f,
    /** Where the body lands when it has turned: a choice of [SuperPlaces] (its count = a different place every time). */
    val place: Int = 0,
    /** Which photo the "Foto" scene shows: a stamp that changes whenever another one is chosen (the file itself is [SuperPhoto]). */
    val photo: Long = 0L,
    /** The always-on screen (the nightstand "AOD" and the screen saver) shows the body, dimmed on black; off, they show only the clock. */
    val aod: Boolean = false,
    /** How bright the always-on screen is (0.1 … 0.8 of the normal picture). */
    val aodDim: Float = 0.45f,
    /** The time under the body on the always-on screen. */
    val aodClock: Boolean = true,
    /** Where the body hangs on the lock screen, moved from its usual place: fractions of the screen's width (right is positive) and height (down is positive), −0.45 … 0.45. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/** The photo of the "Foto" scene: one file in the app's own storage, made from a gallery photo at the screen's proportions. */
object SuperPhoto {
    fun file(context: Context): File = File(context.filesDir, "super_photo.jpg")

    fun save(context: Context, bitmap: Bitmap): Boolean = try {
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it) }
    } catch (e: Exception) {
        false
    }
}

/**
 * What moves the scene right now: the home page (-1 … 1), the tilt (-1 … 1), the zoom the launcher asks for (1 = rest), the
 * lock flight (the body flying in on the lock screen) and the depth – how near the camera is: 0 while the lock screen is
 * showing, 1 on the home screen (close to the surface). The unlocking is the depth gliding from 0 to 1.
 */
class SuperMotion(
    var panX: Float = 0f,
    var tiltX: Float = 0f,
    var tiltY: Float = 0f,
    var zoom: Float = 1f,
    var depth: Float = 1f,
    /** How far the body has turned about its axis (radians): the longitude in the middle of the picture is the minus of it. */
    var spin: Float = 0f,
    /** The latitude in the middle of the picture (radians): the body's axis leans towards or away from us by this much. */
    var lat: Float = -0.2f,
    /** The flight in from the upper left on the lock screen: 0 = just begun, 1 = in the middle of the screen. */
    var lockFly: Float = 1f,
    /**
     * The scene's own clock in seconds: it runs at the speed that was asked for (the slider), faster for a moment after something
     * happened (the "Schwung" of an unlocking, a tap, a swipe), and never jumps – everything that moves on its own is drawn from it.
     */
    var clock: Double = 0.0,
    /** A flash of light on the body and in the sky (0 … 1), which fades by itself. */
    var flash: Float = 0f,
    /** How fast the camera is flying in or out (0 … 1): the stars stretch to streaks. */
    var warp: Float = 0f,
    /** How far the sun has swept (radians about the vertical axis): the light comes round while the body lands. */
    var sunSweep: Float = 0f,
    /** The brightness of the whole picture (1 = as it is; the always-on screen dims it). */
    var dim: Float = 1f,
    /** The always-on screen: black sky, no stars. */
    var ambient: Boolean = false,
)

private const val TW = 1024
private const val TH = 512
private const val SPHERE = 560
private const val STAR_COUNT = 160

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Turns [scene] into pictures: [prepare] once (it takes a moment – call it off the main thread), then [draw] every frame. */
class SuperRenderer(val scene: SuperScene, private val photoFile: File? = null, private val assets: AssetManager? = null) {

    @Volatile
    private var ready = scene == SuperScene.Geometry || scene.isScape

    private var surface = IntArray(0)
    private var clouds = ByteArray(0)
    private var lonTex = IntArray(0)
    private var rowTex = IntArray(0)
    private var shade = IntArray(0)
    private var edge = IntArray(0)
    private var pixels = IntArray(0)
    private var planet: Bitmap? = null
    private var photo: Bitmap? = null
    private var disc: Bitmap? = null

    private val stars = FloatArray(STAR_COUNT * 4)
    private val ringNoise = FloatArray(96)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skyPaint = Paint()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val pose = SuperPose()
    private val box = FloatArray(4)
    private val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private var skyShader: Shader? = null
    private var skyW = 0
    private var skyH = 0

    init {
        val r = Random(7)
        for (i in 0 until STAR_COUNT) {
            stars[i * 4] = r.nextFloat()
            stars[i * 4 + 1] = r.nextFloat()
            stars[i * 4 + 2] = 0.5f + r.nextFloat() * 1.5f
            stars[i * 4 + 3] = r.nextFloat() * 6.28f
        }
        val q = Random(4)
        for (i in ringNoise.indices) ringNoise[i] = q.nextFloat()
    }

    // -----------------------------------------------------------------------------------------
    // Making the planets
    // -----------------------------------------------------------------------------------------

    /** Makes the texture and the sphere's tables (about a second); safe to call again. */
    fun prepare() {
        if (ready) return
        when (scene) {
            SuperScene.Geometry, SuperScene.Mountains, SuperScene.Silk -> Unit
            SuperScene.Photo, SuperScene.Flag -> {
                loadPhoto()
                ready = true
                return
            }
            SuperScene.Moon -> {
                loadDisc("moon")
                ready = true
                return
            }
            SuperScene.Earth -> {
                loadDisc("earth")
                ready = true
                return
            }
            else -> {
                val name = textureName()
                if (name == null || !loadSphereTexture(name)) {
                    when (scene) {
                        SuperScene.Saturn -> makeSaturn()
                        SuperScene.Jupiter -> makeJupiter()
                        SuperScene.Mars -> makeMars()
                        else -> surface = IntArray(TW * TH) { 0xFF808080.toInt() }
                    }
                }
            }
        }
        buildSphere()
        ready = true
    }

    /** The name of the map (in the app's assets) a planet is turned from. */
    private fun textureName(): String? = when (scene) {
        SuperScene.Mars -> "mars"
        SuperScene.Venus -> "venus"
        SuperScene.Jupiter -> "jupiter"
        SuperScene.Saturn -> "saturn"
        SuperScene.Neptune -> "neptune"
        SuperScene.Pluto -> "pluto"
        SuperScene.Io -> "io"
        SuperScene.Europa -> "europa"
        SuperScene.Ganymede -> "ganymede"
        else -> null
    }

    /** A real map of NASA, 1024 × 512 (longitude × latitude), becomes the surface. False if it can't be read. */
    private fun loadSphereTexture(name: String): Boolean {
        val manager = assets ?: return false
        return try {
            manager.open("planets/$name.jpg").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return false
                val fitted = if (bitmap.width == TW && bitmap.height == TH) bitmap else Bitmap.createScaledBitmap(bitmap, TW, TH, true)
                val px = IntArray(TW * TH)
                fitted.getPixels(px, 0, TW, 0, 0, TW, TH)
                surface = px
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** A real photograph of a round body on a clear ground (the Moon, the Earth), cut to its disc. */
    private fun loadDisc(name: String) {
        val manager = assets ?: return
        disc = runCatching { manager.open("planets/$name.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    /** Smooth value noise that repeats every [period] cells sideways (so the texture closes round the planet). */
    private fun noise(x: Float, y: Float, period: Int, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val xa = Math.floorMod(x0, period)
        val xb = Math.floorMod(x0 + 1, period)
        val a = hash(xa, y0, seed)
        val b = hash(xb, y0, seed)
        val c = hash(xa, y0 + 1, seed)
        val d = hash(xb, y0 + 1, seed)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }

    /** Layers of noise, [cellsX] × [cellsY] cells at first, twice as fine with every octave: 0 … 1. */
    private fun fbm(u: Float, v: Float, cellsX: Int, cellsY: Int, octaves: Int, seed: Int): Float {
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        for (o in 0 until octaves) {
            val cx = cellsX shl o
            val cy = cellsY shl o
            sum += amp * noise(u * cx, v * cy, cx, seed + o * 17)
            norm += amp
            amp *= 0.5f
        }
        return sum / norm
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = ((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * k).toInt()
        val g = ((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * k).toInt()
        val bl = (a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * k).toInt()
        return rgb(r, g, bl)
    }

    private inline fun paintTexture(block: (u: Float, v: Float) -> Int) {
        val out = IntArray(TW * TH)
        for (y in 0 until TH) {
            val v = (y + 0.5f) / TH
            for (x in 0 until TW) out[y * TW + x] = block((x + 0.5f) / TW, v)
        }
        surface = out
    }

    private fun makeMars() {
        paintTexture { u, v ->
            val lat = abs(v - 0.5f) * 2f
            val n = fbm(u, v, 6, 3, 5, 5)
            var c = mix(rgb(150, 70, 36), rgb(226, 144, 88), n)
            val m = fbm(u, v, 3, 2, 3, 9)
            if (m > 0.56f) c = mix(c, rgb(92, 52, 36), ((m - 0.56f) * 3.2f).coerceIn(0f, 0.85f))
            val streak = fbm(u, v, 14, 4, 2, 31)
            c = mix(c, rgb(255, 200, 160), (streak - 0.6f).coerceIn(0f, 0.4f) * 0.8f)
            if (lat > 0.9f) c = mix(c, rgb(244, 236, 230), ((lat - 0.9f) * 10f).coerceIn(0f, 1f))
            c
        }
    }

    private fun makeSaturn() {
        paintTexture { u, v ->
            val lat = (v - 0.5f) * PI.toFloat()
            val turb = fbm(u, v, 12, 3, 4, 41)
            val band = 0.5f + 0.5f * sin(lat * 24f + turb * 3.2f)
            val fine = 0.5f + 0.5f * sin(lat * 85f + turb * 6f)
            var c = mix(rgb(176, 138, 82), rgb(236, 214, 166), band)
            c = mix(c, rgb(200, 170, 112), fine * 0.35f)
            val polar = abs(lat) / (PI.toFloat() / 2f)
            mix(c, rgb(120, 126, 134), polar * polar * polar * 0.55f)
        }
    }

    private fun makeJupiter() {
        paintTexture { u, v ->
            val lat = (v - 0.5f) * PI.toFloat()
            val turb = fbm(u, v, 14, 4, 5, 51)
            val s = sin(lat * 15f + turb * 3.6f)
            val s2 = sin(lat * 38f + turb * 5f)
            var c = if (s > 0.2f) {
                mix(rgb(238, 224, 196), rgb(250, 242, 224), s - 0.2f)
            } else {
                mix(rgb(176, 112, 66), rgb(132, 82, 52), (0.2f - s).coerceIn(0f, 1f))
            }
            c = mix(c, rgb(214, 170, 126), (0.5f + 0.5f * s2) * 0.25f)
            // The great red spot.
            val gx = (u * TW - 0.33f * TW) / 62f
            val gy = (v * TH - 0.62f * TH) / 34f
            val g = gx * gx + gy * gy
            if (g < 1f) c = mix(c, rgb(186, 78, 52), ((1f - g) * 2.2f).coerceIn(0f, 0.95f))
            c
        }
    }

    /** For every pixel of the disc: which texel's column and row it shows, how lit it is, how much of it is inside the rim. */
    private fun buildSphere() {
        val s = SPHERE
        lonTex = IntArray(s * s)
        rowTex = IntArray(s * s)
        shade = IntArray(s * s)
        edge = IntArray(s * s)
        pixels = IntArray(s * s)
        lastTurn = Int.MIN_VALUE
        planet = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        // The light comes from the upper left, a little from the front.
        var lx = -0.52f
        var ly = 0.40f
        var lz = 0.75f
        val len = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= len
        ly /= len
        lz /= len
        val twoPi = (2.0 * PI).toFloat()
        for (py in 0 until s) {
            for (px in 0 until s) {
                val i = py * s + px
                val nx = (px + 0.5f) / s * 2f - 1f
                val ny = 1f - (py + 0.5f) / s * 2f
                val r2 = nx * nx + ny * ny
                if (r2 >= 1f) continue
                val nz = sqrt(1f - r2)
                val dist = sqrt(r2)
                edge[i] = (((1f - dist) * s * 0.5f).coerceIn(0f, 1f) * 255f).toInt()
                lonTex[i] = (atan2(nx, nz) / twoPi * TW).roundToInt()
                rowTex[i] = (((0.5f - asin(ny) / PI.toFloat()) * TH).toInt()).coerceIn(0, TH - 1) * TW
                val dot = nx * lx + ny * ly + nz * lz
                val lit = smooth(-0.15f, 0.55f, dot)
                shade[i] = (256f * (0.10f + 0.90f * lit) * (0.62f + 0.38f * nz)).toInt().coerceIn(0, 256)
            }
        }
    }

    private var lastTurn = Int.MIN_VALUE

    /**
     * The disc at [seconds]: the texture turned by [spin] (smoothly, between two texels; spin 0 shows the map's middle, the
     * same longitude as on the graphics chip – this path leans the axis never, so only the longitude of a place is kept),
     * the clouds (if any) turning faster, the light on it. The picture is only made again when it would look different (a
     * quarter of a texel), so a zoom on the lock screen costs nothing.
     */
    private fun fillSphere(seconds: Float, speed: Float, spin: Float) {
        val s = SPHERE
        val bitmap = planet ?: return
        val turn = spin * (TW / (2f * PI.toFloat())) + TW / 2f
        val key = (turn * 4f).toInt()
        if (key == lastTurn) return
        lastTurn = key
        val whole = floor(turn).toInt()
        val frac = ((turn - whole) * 256f).toInt().coerceIn(0, 255)
        val cloudRot = (seconds * 8.7f * speed).toInt() + 211
        val mask = TW - 1
        val hasClouds = clouds.isNotEmpty()
        val surf = surface
        val cl = clouds
        val keep = 256 - frac
        for (i in 0 until s * s) {
            val a = edge[i]
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val row = rowTex[i]
            val lon = lonTex[i]
            val c0 = surf[row + ((lon - whole) and mask)]
            val c1 = surf[row + ((lon - whole - 1) and mask)]
            var r = ((((c0 shr 16) and 0xFF) * keep) + (((c1 shr 16) and 0xFF) * frac)) shr 8
            var g = ((((c0 shr 8) and 0xFF) * keep) + (((c1 shr 8) and 0xFF) * frac)) shr 8
            var b = (((c0 and 0xFF) * keep) + ((c1 and 0xFF) * frac)) shr 8
            if (hasClouds) {
                val ca = cl[row + ((lon - cloudRot) and mask)].toInt() and 0xFF
                if (ca > 0) {
                    r += (255 - r) * ca / 255
                    g += (255 - g) * ca / 255
                    b += (255 - b) * ca / 255
                }
            }
            val sh = shade[i]
            pixels[i] = (a shl 24) or (((r * sh) shr 8) shl 16) or (((g * sh) shr 8) shl 8) or ((b * sh) shr 8)
        }
        bitmap.setPixels(pixels, 0, s, 0, 0, s, s)
    }

    // -----------------------------------------------------------------------------------------
    // Drawing a frame
    // -----------------------------------------------------------------------------------------

    /** One frame at [timeMs] onto [canvas] ([w] × [h] pixels); dimmed when the always-on screen asks for it ([SuperMotion.dim]). */
    fun draw(canvas: Canvas, w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        drawScene(canvas, w, h, timeMs, p, m)
        if (m.dim < 0.999f) canvas.drawColor(android.graphics.Color.argb(((1f - m.dim).coerceIn(0f, 1f) * 255f).toInt(), 0, 0, 0))
    }

    private fun drawScene(canvas: Canvas, w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        val seconds = timeMs / 1000f
        if (scene == SuperScene.Photo || scene == SuperScene.Flag) {
            drawPhoto(canvas, w, h, seconds, p, m)
            return
        }
        if (scene == SuperScene.Moon || scene == SuperScene.Earth) {
            drawDisc(canvas, w, h, seconds, p, m)
            return
        }
        if (scene.isScape) {
            drawScape(canvas, w, h, seconds, m)
            return
        }
        drawSky(canvas, w, h)
        if (p.stars) drawStars(canvas, w, h, seconds, m)
        if (scene == SuperScene.Geometry) {
            drawGeometry(canvas, w, h, seconds, p, m)
            return
        }
        if (!ready) return
        fillSphere(seconds, p.speed, m.spin)
        val bitmap = planet ?: return
        val d = m.depth.coerceIn(0f, 1f)
        // Lock screen: small in the upper left; unlocked: it flies to the bottom centre, then comes closer (SuperCamera).
        SuperCamera.pose(scene, m.lockFly, d, pose, p.offsetX, p.offsetY)
        val radius = w * pose.r * m.zoom * p.size
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.08f, d) + m.tiltX * w * lerp(0.015f, 0.035f, d)
        val cy = h * pose.cy + m.tiltY * h * 0.015f
        // The haze round the planet.
        val glow = when (scene) {
            SuperScene.Mars -> 0xFFE8905A.toInt() to 0.30f
            SuperScene.Venus -> 0xFFF5DDA0.toInt() to 0.30f
            SuperScene.Saturn -> 0xFFE8D2A0.toInt() to 0.20f
            SuperScene.Jupiter -> 0xFFE8C8A0.toInt() to 0.20f
            SuperScene.Neptune -> 0xFF5A8CFF.toInt() to 0.30f
            else -> 0 to 0f
        }
        if (glow.second > 0f) {
            val reach = radius * 1.28f
            val strong = (glow.second * 255f).toInt() shl 24 or (glow.first and 0xFFFFFF)
            glowPaint.shader = RadialGradient(cx, cy, reach, intArrayOf(strong, strong, glow.first and 0xFFFFFF), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, reach, glowPaint)
        }
        if (scene == SuperScene.Saturn) drawRings(canvas, cx, cy, radius, front = false)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawBitmap(bitmap, null, rect, paint)
        if (scene == SuperScene.Saturn) drawRings(canvas, cx, cy, radius, front = true)
    }

    /**
     * The Moon or the Earth as NASA photographed it, in the dark. On the lock screen the whole disc hangs low and calm; on
     * the home screen the camera has come close – the body fills the screen and its edge curves across it.
     */
    private fun drawDisc(canvas: Canvas, w: Int, h: Int, seconds: Float, p: SuperParams, m: SuperMotion) {
        drawSky(canvas, w, h)
        if (p.stars) drawStars(canvas, w, h, seconds, m)
        val bmp = disc ?: return
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, m.lockFly, d, pose, p.offsetX, p.offsetY)
        val diameter = w * pose.r * 2f * m.zoom * p.size
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.10f, d) + m.tiltX * w * lerp(0.02f, 0.05f, d)
        val cy = h * pose.cy + m.tiltY * h * 0.02f
        if (scene == SuperScene.Earth) {
            // The thin blue of the atmosphere at the rim – the Moon has none.
            val reach = diameter * 0.5f * 1.22f
            val strong = (0.34f * 255f).toInt() shl 24 or 0x4AA3FF
            glowPaint.shader = RadialGradient(cx, cy, reach, intArrayOf(strong, strong, 0x4AA3FF), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, reach, glowPaint)
        }
        rect.set(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f)
        canvas.drawBitmap(bmp, null, rect, paint)
    }

    private fun loadPhoto() {
        if (scene == SuperScene.Flag) {
            // The photo of this scene is part of the app.
            val manager = assets ?: return
            photo = runCatching { manager.open("planets/iwojima.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            return
        }
        val file = photoFile ?: return
        if (!file.exists()) return
        photo = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** The photo a little bigger than the screen, so there is room to move: with the tilt and the home page; it comes closer when unlocked. */
    private fun drawPhoto(canvas: Canvas, w: Int, h: Int, seconds: Float, p: SuperParams, m: SuperMotion) {
        val bmp = photo
        if (bmp == null) {
            // Nothing chosen yet: a calm dark sky.
            drawSky(canvas, w, h)
            if (p.stars) drawStars(canvas, w, h, seconds, m)
            return
        }
        SuperCamera.pose(scene, m.lockFly, m.depth.coerceIn(0f, 1f), pose)
        SuperCamera.photoBox(w, h, bmp.width, bmp.height, pose, m.zoom, -(m.tiltX * 0.03f + m.panX * 0.05f) * w, m.tiltY * 0.02f * h, box)
        rect.set(box[0], box[1], box[0] + box[2], box[1] + box[3])
        canvas.drawBitmap(bmp, null, rect, paint)
        if (scene == SuperScene.Flag) {
            // A soft light on the flag: an ellipse of white, added to the picture.
            val cx = box[0] + SuperCamera.FLAG_U * box[2]
            val cy = box[1] + SuperCamera.FLAG_V * box[3]
            val rx = SuperCamera.FLAG_RU * box[2]
            val ry = SuperCamera.FLAG_RV * box[3]
            spotPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, spotPaint)
            canvas.restore()
        }
    }

    // -----------------------------------------------------------------------------------------
    // The landscapes (the processor's simple version of what the graphics chip's shader draws)
    // -----------------------------------------------------------------------------------------

    private val scapePath = android.graphics.Path()
    private val scapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun mixColor(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = ((((a shr shift) and 255) * (1f - k) + ((b shr shift) and 255) * k)).toInt() and 255
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun ridgeAt(x: Float, seed: Float): Float {
        var sum = 0f
        var amp = 0.55f
        var freq = 1f
        for (i in 0 until 4) {
            sum += amp * (1f - abs(sin(x * freq * 2.3f + seed + i * 1.7f)))
            freq *= 2.1f
            amp *= 0.48f
        }
        return sum
    }

    private fun drawScape(canvas: Canvas, w: Int, h: Int, seconds: Float, m: SuperMotion) {
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, m.lockFly, d, pose)
        val mag = maxOf(1f, pose.r * m.zoom)
        val wf = w.toFloat()
        val hf = h.toFloat()
        canvas.save()
        canvas.translate(wf / 2f - m.panX * wf * 0.04f, hf / 2f)
        canvas.scale(mag, mag)
        canvas.translate(-wf * pose.cx, -hf * pose.cy)
        if (scene == SuperScene.Silk) {
            // Bands of silk sliding over each other.
            scapePaint.shader = LinearGradient(0f, 0f, wf, hf, intArrayOf(0xFF012A1E.toInt(), 0xFF05905A.toInt(), 0xFF0A3F2E.toInt(), 0xFF1BD68A.toInt(), 0xFF023B28.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(-wf, -hf, 2f * wf, 2f * hf, scapePaint)
            scapePaint.shader = null
            for (i in 0 until 5) {
                val y = hf * (0.1f + 0.2f * i) + sin(seconds * 0.35f + i) * hf * 0.04f
                scapePaint.shader = LinearGradient(0f, y - hf * 0.12f, 0f, y + hf * 0.12f, intArrayOf(0x0027FFB0, 0x6627FFB0, 0x0027FFB0), null, Shader.TileMode.CLAMP)
                canvas.drawRect(-wf, y - hf * 0.12f, 2f * wf, y + hf * 0.12f, scapePaint)
            }
            scapePaint.shader = null
            canvas.restore()
            return
        }
        if (scene == SuperScene.Spheres) {
            scapePaint.shader = LinearGradient(0f, 0f, 0f, hf, 0xFF130F44.toInt(), 0xFF7573DB.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(-wf, -hf, 2f * wf, 2f * hf, scapePaint)
            val sway = sin(seconds * 0.3f) * wf * 0.02f
            val balls = floatArrayOf(0.30f, 0.20f, 0.10f, 0.39f, 0.30f, 0.30f, 0.58f, 0.80f, 0.40f)
            for (i in 0 until 3) {
                val bx = wf * balls[i * 3] + sway * (i + 1)
                val by = hf * balls[i * 3 + 1]
                val br = wf * balls[i * 3 + 2] * 1.4f
                scapePaint.shader = RadialGradient(bx + br * 0.35f, by + br * 0.35f, br * 1.1f, intArrayOf(0xFF7FA6FF.toInt(), 0xFF1B4DEB.toInt(), 0xFF05094A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(bx, by, br, scapePaint)
            }
            scapePaint.shader = null
            canvas.restore()
            return
        }
        if (scene == SuperScene.Spray) {
            canvas.drawColor(0xFF010305.toInt())
            starPaint.style = Paint.Style.FILL
            for (i in 0 until STAR_COUNT) {
                val x = stars[i * 4] * wf
                val y = stars[i * 4 + 1] * hf
                val distance = abs(hypot(x - wf * 0.7f, y - hf * 0.3f) - wf * 0.6f) / (wf * 0.25f)
                if (distance > 1.6f) continue
                val weight = kotlin.math.exp(-distance * distance)
                starPaint.color = if (y > hf * 0.8f && x > wf * 0.5f) 0xFFDB3350.toInt() else if (weight > 0.5f) 0xFF2A9EA8.toInt() else 0xFFDDF5F6.toInt()
                starPaint.alpha = (80 + 175 * weight).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, stars[i * 4 + 2] * 1.1f, starPaint)
            }
            canvas.restore()
            return
        }
        val hour = SuperScape.hourNow()
        val day = (((hour - 5.5f) / 1.5f).coerceIn(0f, 1f) * (1f - ((hour - 19f) / 1.5f).coerceIn(0f, 1f)))
        val warm = maxOf((1f - abs(hour - 6.5f) / 1.6f).coerceIn(0f, 1f), (1f - abs(hour - 18.6f) / 1.8f).coerceIn(0f, 1f))
        val top = mixColor(0xFF050A22.toInt(), 0xFF2D6FD8.toInt(), day)
        val low = mixColor(mixColor(0xFF0B1636.toInt(), 0xFFA9D2F6.toInt(), day), 0xFFFF8248.toInt(), warm)
        val horizonY = hf * 0.62f
        scapePaint.shader = LinearGradient(0f, -hf * 0.5f, 0f, horizonY, top, low, Shader.TileMode.CLAMP)
        canvas.drawRect(-wf, -hf, 2f * wf, hf * 2f, scapePaint)
        scapePaint.shader = null
        // The sun by day, the moon by night.
        val up = if (day > 0.5f) ((hour - 6f) / 13f).coerceIn(0f, 1f) else (((hour - 19.6f + 24f) % 24f) / 10.4f).coerceIn(0f, 1f)
        val bx = wf * (0.5f - cos(up * PI.toFloat()) * 0.46f)
        val by = horizonY - sin(up * PI.toFloat()) * hf * 0.42f
        scapePaint.color = if (day > 0.5f) 0xFFFFF4D0.toInt() else 0xFFDDE6FF.toInt()
        canvas.drawCircle(bx, by, wf * 0.045f, scapePaint)
        // Five ranges, the far ones pale and lost in haze.
        val seeds = floatArrayOf(3.1f, 9.4f, 14.8f, 21.5f, 33.2f)
        val bases = floatArrayOf(0.60f, 0.67f, 0.75f, 0.84f, 0.93f)
        val amps = floatArrayOf(0.20f, 0.17f, 0.15f, 0.13f, 0.10f)
        val near = mixColor(0xFF04070A.toInt(), 0xFF2A3A3C.toInt(), day)
        val far = mixColor(0xFF1B2440.toInt(), 0xFFA9B6D2.toInt(), day)
        for (i in 0 until 5) {
            val shift = m.panX * wf * 0.05f * (0.4f + 0.3f * i)
            scapePath.reset()
            scapePath.moveTo(-wf, hf * 2f)
            var x = -wf
            while (x <= 2f * wf) {
                scapePath.lineTo(x, hf * (bases[i] - amps[i] * ridgeAt((x + shift) / wf * (1.2f + 0.8f * i), seeds[i])))
                x += wf / 36f
            }
            scapePath.lineTo(2f * wf, hf * 2f)
            scapePath.close()
            val tint = mixColor(far, near, i / 4f)
            scapePaint.color = mixColor(mixColor(tint, low, 0.30f * (1f - i / 4f)), 0xFFFF9A6A.toInt(), warm * 0.25f * (1f - i / 5f))
            canvas.drawPath(scapePath, scapePaint)
        }
        canvas.restore()
    }

    private fun drawSky(canvas: Canvas, w: Int, h: Int) {
        if (skyShader == null || skyW != w || skyH != h) {
            skyShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0xFF01020A.toInt(), 0xFF040818.toInt(), 0xFF0A1128.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
            skyW = w
            skyH = h
            skyPaint.shader = skyShader
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)
    }

    /** The stars: they twinkle, shift a little with tilt and page, and stream outward as the camera comes close (unlocking). */
    private fun drawStars(canvas: Canvas, w: Int, h: Int, seconds: Float, m: SuperMotion) {
        val unit = w / 1080f
        val d = m.depth.coerceIn(0f, 1f)
        val midX = w * 0.5f
        val midY = h * 0.5f
        for (i in 0 until STAR_COUNT) {
            val near = 0.3f + 0.7f * (((i * 37) % 100) / 100f)
            // Near stars fly out further than the far ones.
            val spread = 1f + 0.5f * d * near
            val x = midX + (stars[i * 4] * w - midX + (m.panX * -0.03f * near + m.tiltX * 0.02f * near) * w) * spread
            val y = midY + (stars[i * 4 + 1] * h - midY + m.tiltY * 0.012f * near * h) * spread
            if (x < -8f || x > w + 8f || y < -8f || y > h + 8f) continue
            val twinkle = 0.55f + 0.45f * sin(seconds * 0.9f + stars[i * 4 + 3])
            starPaint.color = ((twinkle * 0.9f * 255f).toInt() shl 24) or 0xFFFFFF
            canvas.drawCircle(x, y, stars[i * 4 + 2] * unit * 1.6f, starPaint)
        }
    }

    /** Saturn's rings: [front] is the half in front of the planet (drawn after it), else the half behind (drawn before). */
    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, radius: Float, front: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-17f)
        canvas.scale(1f, 0.30f)
        val big = radius * 4f
        if (front) canvas.clipRect(-big, 0f, big, big) else canvas.clipRect(-big, -big, big, 0f)
        ringPaint.strokeWidth = radius * 0.026f
        var r = radius * 1.27f
        var i = 0
        while (r < radius * 2.25f) {
            val gap = r > radius * 1.76f && r < radius * 1.83f
            var a = if (gap) 0.06f else 0.30f + 0.5f * ringNoise[i % ringNoise.size]
            if (r < radius * 1.5f) a *= 0.6f
            if (r > radius * 1.83f) a *= 0.85f
            val color = if (i % 2 == 0) 0xE2CEA0 else 0xCDB88C
            ringPaint.color = ((a * 255f).toInt() shl 24) or color
            canvas.drawCircle(0f, 0f, r, ringPaint)
            r += radius * 0.022f
            i++
        }
        canvas.restore()
    }

    // -----------------------------------------------------------------------------------------
    // Geometry
    // -----------------------------------------------------------------------------------------

    private val icoVerts: Array<FloatArray> by lazy {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val norm = sqrt(1f + t * t)
        val raw = listOf(
            floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
            floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
            floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f),
        )
        Array(raw.size) { i -> floatArrayOf(raw[i][0] / norm, raw[i][1] / norm, raw[i][2] / norm) }
    }
    private val icoEdges: List<IntArray> by lazy { edgesOf(icoVerts) }
    private val octVerts: Array<FloatArray> = arrayOf(
        floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
        floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
    )
    private val octEdges: List<IntArray> by lazy { edgesOf(octVerts) }

    /** The edges of a regular solid: every pair of vertices as near as the nearest pair. */
    private fun edgesOf(v: Array<FloatArray>): List<IntArray> {
        var best = Float.MAX_VALUE
        for (i in v.indices) for (j in i + 1 until v.size) best = minOf(best, dist2(v[i], v[j]))
        val edges = ArrayList<IntArray>()
        for (i in v.indices) for (j in i + 1 until v.size) if (dist2(v[i], v[j]) < best * 1.05f) edges.add(intArrayOf(i, j))
        return edges
    }

    private fun dist2(a: FloatArray, b: FloatArray): Float {
        val x = a[0] - b[0]
        val y = a[1] - b[1]
        val z = a[2] - b[2]
        return x * x + y * y + z * z
    }

    /** Only the lines and points of the geometry, without sky or stars (the graphics chip lays them over its own sky). */
    fun drawGeometryLayer(canvas: Canvas, w: Int, h: Int, seconds: Float, p: SuperParams, m: SuperMotion) = drawGeometry(canvas, w, h, seconds, p, m)

    private fun drawGeometry(canvas: Canvas, w: Int, h: Int, seconds: Float, p: SuperParams, m: SuperMotion) {
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(SuperScene.Geometry, m.lockFly, d, pose)
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.06f, d) + m.tiltX * w * lerp(0.015f, 0.03f, d)
        val cy = h * pose.cy + m.tiltY * h * 0.02f
        val r = w * pose.r * m.zoom * p.size
        val a = seconds * 0.35f * p.speed
        val b = seconds * 0.23f * p.speed
        drawShape(canvas, w, icoVerts, icoEdges, cx, cy, r, a, b)
        drawShape(canvas, w, octVerts, octEdges, cx, cy, r * 0.55f, -a * 1.6f, b * 1.3f)
    }

    private fun drawShape(canvas: Canvas, w: Int, verts: Array<FloatArray>, edges: List<IntArray>, cx: Float, cy: Float, r: Float, ax: Float, ay: Float) {
        val n = verts.size
        val px = FloatArray(n)
        val py = FloatArray(n)
        val pz = FloatArray(n)
        val ca = cos(ax)
        val sa = sin(ax)
        val cb = cos(ay)
        val sb = sin(ay)
        for (i in 0 until n) {
            val x = verts[i][0]
            val y = verts[i][1]
            val z = verts[i][2]
            val x1 = x * ca + z * sa
            val z1 = -x * sa + z * ca
            val y2 = y * cb - z1 * sb
            val z2 = y * sb + z1 * cb
            val s = 3.2f / (3.2f + z2)
            px[i] = cx + x1 * r * s
            py[i] = cy + y2 * r * s
            pz[i] = z2
        }
        val unit = w / 1080f
        for (e in edges) {
            val depth = ((pz[e[0]] + pz[e[1]]) * 0.5f + 1f) * 0.5f
            val near = 1f - depth
            val color = mix(0x5AA9FF, 0xB07CFF, depth) and 0xFFFFFF
            val alpha = 0.25f + 0.75f * near
            linePaint.strokeWidth = 7f * unit
            linePaint.color = ((alpha * 0.18f * 255f).toInt() shl 24) or color
            canvas.drawLine(px[e[0]], py[e[0]], px[e[1]], py[e[1]], linePaint)
            linePaint.strokeWidth = 2.2f * unit
            linePaint.color = ((alpha * 0.9f * 255f).toInt() shl 24) or color
            canvas.drawLine(px[e[0]], py[e[0]], px[e[1]], py[e[1]], linePaint)
        }
        for (i in 0 until n) {
            val near = 1f - (pz[i] + 1f) * 0.5f
            starPaint.color = ((0.4f + 0.6f * near) * 255f).toInt() shl 24 or 0xFFFFFF
            canvas.drawCircle(px[i], py[i], (2.4f + 2f * near) * unit, starPaint)
        }
    }
}
