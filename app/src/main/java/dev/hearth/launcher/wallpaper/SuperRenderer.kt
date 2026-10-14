package dev.hearth.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import dev.hearth.launcher.data.SettingsRepository
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
import kotlin.random.Random

/*
 * ZENITH's Super-Wallpaper: wallpapers that move – a planet turning in the dark, a wireframe that spins –
 * in the manner of the "super wallpapers" some phones have (Saturn, Earth, Mars …). All of it is drawn here,
 * from noise and a few gradients, no picture files: the planets are textures made at the first start, laid
 * round a sphere pixel by pixel. They turn slowly, drift with the phone's tilt and with the home page, and
 * zoom in when the screen wakes. One renderer for the live wallpaper, the settings' preview and the glass.
 */

/** The scenes ("mods") one can choose. */
enum class SuperScene(val label: String, val note: String) {
    Saturn("Saturn", "Der Ringplanet, groß im Bild – die Ringe fangen das Licht, die Bänder ziehen langsam vorbei."),
    Jupiter("Jupiter", "Streifen, Wirbel und der große rote Fleck, der Riese dreht sich in sechs Minuten."),
    Earth("Erde", "Blauer Planet, Kontinente und Wolken, die schneller ziehen, als er sich dreht – mit Dunst am Rand."),
    Mars("Mars", "Rostroter Planet mit dunklen Meeren und weißen Polkappen."),
    Moon("Mond", "Grauer Mond voller Krater, ruhig und nah."),
    Geometry("Geometrie", "Ein Ikosaeder aus Licht mit einem zweiten Körper darin, die beide umeinander drehen."),
}

/** What the wallpaper is set to (read from ZENITH's settings). */
class SuperParams(
    val scene: SuperScene,
    /** The scene shifts with the phone's tilt. */
    val tilt: Boolean,
    /** It zooms in when the screen wakes, and moves with the home page. */
    val zoomOnWake: Boolean,
    /** How fast everything turns (0.2 … 3). */
    val speed: Float,
    val stars: Boolean,
)

/** What moves the scene right now: the home page (-1 … 1), the tilt (-1 … 1) and the zoom (1 = rest). */
class SuperMotion(var panX: Float = 0f, var tiltX: Float = 0f, var tiltY: Float = 0f, var zoom: Float = 1f)

/** The settings of the Super-Wallpaper, as ZENITH's settings keep them. */
object SuperPrefs {
    fun read(context: Context): SuperParams {
        val p = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val scene = runCatching { SuperScene.valueOf(p.getString("superScene", "").orEmpty()) }.getOrDefault(SuperScene.Saturn)
        return SuperParams(
            scene = scene,
            tilt = p.getBoolean("superTilt", true),
            zoomOnWake = p.getBoolean("superZoom", true),
            speed = p.getFloat("superSpeed", 1f).coerceIn(0.2f, 3f),
            stars = p.getBoolean("superStars", true),
        )
    }

    /** One still frame of the wallpaper as set, [w] × [h] – the glass of ZENITH bends and blurs this. Null if it couldn't be drawn. */
    fun snapshot(context: Context, w: Int, h: Int): Bitmap? = runCatching {
        val params = read(context)
        val renderer = SuperRenderer(params.scene)
        renderer.prepare()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bitmap), w, h, 5000L, params, SuperMotion())
        bitmap
    }.getOrNull()
}

private const val TW = 1024
private const val TH = 512
private const val SPHERE = 380
private const val STAR_COUNT = 160

/** Turns [scene] into pictures: [prepare] once (it takes a moment – call it off the main thread), then [draw] every frame. */
class SuperRenderer(val scene: SuperScene) {

    @Volatile
    private var ready = scene == SuperScene.Geometry

    private var surface = IntArray(0)
    private var clouds = ByteArray(0)
    private var lonTex = IntArray(0)
    private var rowTex = IntArray(0)
    private var shade = IntArray(0)
    private var edge = IntArray(0)
    private var pixels = IntArray(0)
    private var planet: Bitmap? = null

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
            SuperScene.Saturn -> makeSaturn()
            SuperScene.Jupiter -> makeJupiter()
            SuperScene.Earth -> makeEarth()
            SuperScene.Mars -> makeMars()
            SuperScene.Moon -> makeMoon()
            SuperScene.Geometry -> Unit
        }
        buildSphere()
        ready = true
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

    private fun makeEarth() {
        paintTexture { u, v ->
            val lat = abs(v - 0.5f) * 2f
            val e = fbm(u, v, 4, 2, 5, 11)
            val sea = 0.50f
            var c: Int
            if (e < sea) {
                val d = e / sea
                c = mix(rgb(8, 30, 84), rgb(30, 110, 190), d * d)
                if (d > 0.93f) c = mix(c, rgb(70, 170, 200), (d - 0.93f) / 0.07f)
            } else {
                val h = (e - sea) / (1f - sea)
                val dry = fbm(u, v, 6, 3, 3, 29)
                c = mix(rgb(54, 120, 52), rgb(126, 104, 62), (h * 1.6f).coerceIn(0f, 1f))
                if (dry > 0.56f && lat < 0.62f) c = mix(c, rgb(205, 170, 100), ((dry - 0.56f) * 4f).coerceIn(0f, 0.9f))
                if (h > 0.62f) c = mix(c, rgb(240, 244, 248), ((h - 0.62f) * 4f).coerceIn(0f, 1f))
            }
            if (lat > 0.84f) c = mix(c, rgb(246, 250, 255), ((lat - 0.84f) * 8f).coerceIn(0f, 1f))
            c
        }
        val cl = ByteArray(TW * TH)
        for (y in 0 until TH) {
            val v = (y + 0.5f) / TH
            for (x in 0 until TW) {
                val c = fbm((x + 0.5f) / TW, v, 5, 3, 5, 77)
                cl[y * TW + x] = (smooth(0.50f, 0.74f, c) * 235f).toInt().toByte()
            }
        }
        clouds = cl
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

    private fun makeMoon() {
        paintTexture { u, v ->
            val n = fbm(u, v, 8, 4, 5, 3)
            var gray = 118f + n * 74f
            val maria = fbm(u, v, 3, 2, 3, 8)
            if (maria > 0.55f) gray *= 1f - ((maria - 0.55f) * 2.2f).coerceIn(0f, 0.38f)
            rgb(gray.toInt(), gray.toInt(), (gray * 1.02f).toInt())
        }
        // Craters, stamped: a dark floor, a bright rim, a little halo.
        val rnd = Random(33)
        repeat(240) {
            val cx = rnd.nextInt(TW)
            val cy = (TH * (0.12f + 0.76f * rnd.nextFloat())).toInt()
            val rad = 3 + (rnd.nextFloat().pow(2.2f) * 34f).toInt()
            for (dy in -rad..rad) {
                for (dx in -rad..rad) {
                    val d = hypot(dx.toFloat(), dy.toFloat()) / rad
                    if (d > 1.2f) continue
                    val delta = when {
                        d < 0.82f -> -20f * (1f - d * 0.6f)
                        d < 1.0f -> 36f * sin((d - 0.82f) / 0.18f * PI.toFloat())
                        else -> 9f * (1.2f - d) / 0.2f
                    }
                    val x = Math.floorMod(cx + dx, TW)
                    val y = (cy + dy).coerceIn(0, TH - 1)
                    val i = y * TW + x
                    val c = surface[i]
                    val g = (((c shr 16) and 0xFF) + delta.toInt()).coerceIn(0, 255)
                    surface[i] = rgb(g, g, (g * 1.02f).toInt())
                }
            }
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

    /** The disc at [seconds]: the texture turned, the clouds (if any) turning faster, the light on it. */
    private fun fillSphere(seconds: Float, speed: Float) {
        val s = SPHERE
        val bitmap = planet ?: return
        val rot = (seconds * 6f * speed).toInt()
        val cloudRot = (seconds * 8.7f * speed).toInt() + 211
        val mask = TW - 1
        val hasClouds = clouds.isNotEmpty()
        val surf = surface
        val cl = clouds
        for (i in 0 until s * s) {
            val a = edge[i]
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val row = rowTex[i]
            val lon = lonTex[i]
            var col = surf[row + ((lon - rot) and mask)]
            if (hasClouds) {
                val ca = cl[row + ((lon - cloudRot) and mask)].toInt() and 0xFF
                if (ca > 0) {
                    val r = ((col shr 16) and 0xFF)
                    val g = ((col shr 8) and 0xFF)
                    val b = (col and 0xFF)
                    col = ((r + (255 - r) * ca / 255) shl 16) or ((g + (255 - g) * ca / 255) shl 8) or (b + (255 - b) * ca / 255)
                }
            }
            val sh = shade[i]
            val r = (((col shr 16) and 0xFF) * sh) shr 8
            val g = (((col shr 8) and 0xFF) * sh) shr 8
            val b = ((col and 0xFF) * sh) shr 8
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, s, 0, 0, s, s)
    }

    // -----------------------------------------------------------------------------------------
    // Drawing a frame
    // -----------------------------------------------------------------------------------------

    /** One frame at [timeMs] onto [canvas] ([w] × [h] pixels). */
    fun draw(canvas: Canvas, w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        val seconds = timeMs / 1000f
        drawSky(canvas, w, h)
        if (p.stars) drawStars(canvas, w, h, seconds, m)
        if (scene == SuperScene.Geometry) {
            drawGeometry(canvas, w, h, seconds, p, m)
            return
        }
        if (!ready) return
        fillSphere(seconds, p.speed)
        val bitmap = planet ?: return
        val base = when (scene) {
            SuperScene.Saturn -> 0.30f
            SuperScene.Jupiter -> 0.46f
            SuperScene.Earth -> 0.44f
            SuperScene.Mars -> 0.37f
            else -> 0.40f
        }
        val radius = w * base * m.zoom
        val cx = w * 0.5f - m.panX * w * 0.10f + m.tiltX * w * 0.03f
        val cy = h * (if (scene == SuperScene.Saturn) 0.55f else 0.60f) + m.tiltY * h * 0.02f
        // The haze round the planet.
        val glow = when (scene) {
            SuperScene.Earth -> 0xFF4AA3FF.toInt() to 0.55f
            SuperScene.Mars -> 0xFFE8905A.toInt() to 0.30f
            SuperScene.Saturn -> 0xFFE8D2A0.toInt() to 0.20f
            SuperScene.Jupiter -> 0xFFE8C8A0.toInt() to 0.20f
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

    private fun drawStars(canvas: Canvas, w: Int, h: Int, seconds: Float, m: SuperMotion) {
        val unit = w / 1080f
        for (i in 0 until STAR_COUNT) {
            val depth = 0.3f + 0.7f * (((i * 37) % 100) / 100f)
            val x = stars[i * 4] * w + (m.panX * -0.03f * depth + m.tiltX * 0.02f * depth) * w
            val y = stars[i * 4 + 1] * h + m.tiltY * 0.012f * depth * h
            val twinkle = 0.55f + 0.45f * sin(seconds * 0.9f + stars[i * 4 + 3])
            starPaint.color = ((twinkle * 0.9f * 255f).toInt() shl 24) or 0xFFFFFF
            canvas.drawCircle(((x % w) + w) % w, y, stars[i * 4 + 2] * unit * 1.6f, starPaint)
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

    private fun drawGeometry(canvas: Canvas, w: Int, h: Int, seconds: Float, p: SuperParams, m: SuperMotion) {
        val cx = w * 0.5f - m.panX * w * 0.06f + m.tiltX * w * 0.03f
        val cy = h * 0.52f + m.tiltY * h * 0.02f
        val r = w * 0.36f * m.zoom
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
