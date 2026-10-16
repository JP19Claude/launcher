package dev.hearth.launcher.wallpaper

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The graphics chip's way of drawing a Super-Wallpaper (OpenGL ES 3): the Moon and the planets are real spheres, a
 * shader turns the map of the body round them pixel by pixel – so the Moon can come close enough for single craters, with
 * its relief lit by the sun – the sky and the stars are drawn by a shader as well. If anything of this fails the caller
 * falls back to SuperRenderer (the pictures drawn by the processor). All calls on one thread, the one that created it.
 */
internal class SuperGl(
    private val assets: AssetManager,
    private val photoFile: File?,
    /** The Moon's finest pictures (about 270 MB on the graphics chip): only for phones with the memory for it. */
    private val fullDetail: Boolean = true,
) {

    private enum class Kind { SPHERE, DISC, PHOTO, GEOMETRY }

    private class Prog(val id: Int) {
        private val cache = HashMap<String, Int>()
        fun u(name: String): Int = cache.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }
    }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var sky: Prog? = null
    private var sphere: Prog? = null
    private var sprite: Prog? = null

    private var kind = Kind.SPHERE
    private var scene = SuperScene.Moon
    private var albedoTex = 0
    private var normalTex = 0
    private var extraTex = 0
    private var spriteTex = 0
    /** What the albedo map is: 0 a colour map, 1 the Moon's brightness, 2 a brightness map with a colour map beside it, 3 the Earth. */
    private var albedoKind = 0
    private var look = Look(0, 0f, 0f)
    private var photoW = 1
    private var photoH = 1
    private var layer: Bitmap? = null
    private var layerRenderer: SuperRenderer? = null

    private val pose = SuperPose()
    private val photoBox = FloatArray(4)
    private val rot = FloatArray(9)
    private val readBack: ByteBuffer = ByteBuffer.allocateDirect(8)
    private var verifyNext = false
    private var bodyShown = true
    private var lastCenterX = 0
    private var lastCenterY = 0
    private var lastHeight = 1

    // ---------------------------------------------------------------------------------------------
    // The graphics context
    // ---------------------------------------------------------------------------------------------

    /** Sets up EGL and the shaders on [surface]. False if this phone's graphics driver won't do it. */
    fun start(surface: Surface): Boolean {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return fail()
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return fail()
            val wanted = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 0,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, wanted, 0, configs, 0, 1, count, 0) || count[0] <= 0) return fail()
            val config = configs[0] ?: return fail()
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            if (context == EGL14.EGL_NO_CONTEXT) return fail()
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return fail()
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) return fail()
            sky = link(SKY_VERTEX, SKY_FRAGMENT)
            sphere = link(BOX_VERTEX, SPHERE_FRAGMENT)
            sprite = link(BOX_VERTEX, SPRITE_FRAGMENT)
            return true
        } catch (t: Throwable) {
            stop()
            return false
        }
    }

    private fun fail(): Boolean {
        stop()
        return false
    }

    /** Lets go of everything (also the window: afterwards the surface may be used by a canvas again). */
    fun stop() {
        try {
            freeTextures()
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        } catch (t: Throwable) {
            // nothing more to do
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        sky = null
        sphere = null
        sprite = null
    }

    // ---------------------------------------------------------------------------------------------
    // Shaders and textures
    // ---------------------------------------------------------------------------------------------

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("shader: $log")
        }
        return shader
    }

    private fun link(vertex: String, fragment: String): Prog {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("program: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return Prog(program)
    }

    private fun upload(bitmap: Bitmap, mips: Boolean, repeat: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (mips) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        if (mips) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return ids[0]
    }

    private fun freeTextures() {
        val ids = intArrayOf(albedoTex, normalTex, extraTex, spriteTex)
        if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
            for (id in ids) if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }
        albedoTex = 0
        normalTex = 0
        extraTex = 0
        spriteTex = 0
        albedoKind = 0
        layer = null
        layerRenderer = null
    }

    private fun decodeAsset(path: String): Bitmap? =
        runCatching { assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun decodePhoto(): Bitmap? {
        val file = photoFile ?: return null
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** The textures of [forScene]. False if its picture can't be read (the caller then draws without the graphics chip). */
    fun load(forScene: SuperScene): Boolean {
        return try {
            freeTextures()
            scene = forScene
            verifyNext = true
            bodyShown = true
            when (forScene) {
                SuperScene.Moon -> {
                    kind = Kind.SPHERE
                    albedoKind = 1
                    albedoTex = loadMoonAlbedo()
                    if (albedoTex == 0) return false
                    normalTex = loadMoonNormals("moon_normal")
                    normalTex != 0
                }
                SuperScene.Earth -> {
                    if (loadEarth()) {
                        kind = Kind.SPHERE
                        albedoKind = 3
                        look = lookOf(forScene)
                        true
                    } else {
                        // The maps of the sphere can't be read: the old photograph of the disc.
                        freeTextures()
                        kind = Kind.DISC
                        val disc = decodeAsset("planets/earth.webp") ?: return false
                        spriteTex = upload(disc, true, false)
                        disc.recycle()
                        true
                    }
                }
                SuperScene.Photo, SuperScene.Flag -> {
                    kind = Kind.PHOTO
                    val photo = if (forScene == SuperScene.Flag) decodeAsset("planets/iwojima.jpg") else decodePhoto()
                    if (photo != null) {
                        photoW = photo.width
                        photoH = photo.height
                        spriteTex = upload(photo, true, false)
                        photo.recycle()
                    } else {
                        spriteTex = 0
                    }
                    true
                }
                SuperScene.Geometry -> {
                    kind = Kind.GEOMETRY
                    layerRenderer = SuperRenderer(SuperScene.Geometry, null, assets)
                    true
                }
                else -> {
                    kind = Kind.SPHERE
                    val name = planetFile(forScene) ?: return false
                    look = lookOf(forScene)
                    if (hasAsset("planets/${name}_y_00_0.webp")) {
                        // A map of the brightness (16384 × 8192) with a small map of the colours beside it.
                        albedoTex = loadLuma("${name}_y", LUMA_W, LUMA_H, LUMA_ROWS)
                        if (albedoTex != 0) {
                            albedoKind = 2
                            extraTex = loadChroma(name)
                        }
                    }
                    if (albedoTex == 0) albedoTex = loadHdMap(name)
                    if (albedoTex == 0) {
                        // No big map (the gas planets) or no room for it: the 2048 × 1024 picture.
                        val map = decodeAsset("planets/$name.jpg") ?: return false
                        albedoTex = upload(map, true, true)
                        map.recycle()
                    }
                    // The terrain, where its heights are known.
                    normalTex = if (look.relief > 0f) loadNormalStrips(name) else 0
                    albedoTex != 0
                }
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun newTexture(mips: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (mips) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        return ids[0]
    }

    /** The size of a texture of [base] pixels width: [wanted] times halved, and more if the chip can't take that. */
    private fun textureStep(base: Int, wanted: Int): Int {
        val limit = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, limit, 0)
        var step = wanted
        while (base / step > limit[0] && step < 8) step *= 2
        return step
    }

    /**
     * The Moon's albedo, 16384 × 8192 pixels of one byte each (about 134 MB on the graphics chip, 45 pixels to a degree – about one to a
     * pixel of the screen when the camera is close): the LROC wide-angle mosaic (with the low sun of its pictures in it, from the
     * west), made tile by tile from its 64 × 2 pictures (8192 × 128 each), so that never more than a few megabytes are in memory at
     * once. Where the phone is short of memory, or the chip can't take such a texture, every 2 × 2 (or 4 × 4) pixels are
     * averaged: 8192 × 4096. 0 if a picture can't be read.
     */
    private fun loadMoonAlbedo(): Int = loadLuma("moon_albedo", MOON_W, MOON_H, MOON_ROWS)

    /**
     * A map of brightness of [width] × [height] pixels, one byte each, from [rows] rows of tiles named "<prefix>_<row>_<half>.webp"
     * (the halves of a row, as a picture can't be wider than 16383 pixels; maps of 8192 or less come whole). 0 if a tile can't be read.
     */
    private fun loadLuma(prefix: String, width: Int, height: Int, rows: Int): Int {
        var step = textureStep(width, if (fullDetail) 1 else 2)
        while (true) {
            val id = tryLoadLuma(prefix, width, height, rows, step)
            if (id != 0 || step >= 4) return id
            // The graphics memory is full: with less.
            step *= 2
        }
    }

    private fun tryLoadLuma(prefix: String, width: Int, height: Int, tileRows: Int, step: Int): Int {
        val halves = if (width > 8192) 2 else 1
        val w = width / step
        val h = height / step
        val id = newTexture(true)
        GLES20.glGetError()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R8, w, h, 0, GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, null)
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            return 0
        }
        var row = 0
        // One buffer for all the tiles (they are all the same size).
        var px = IntArray(0)
        var bytes: ByteBuffer? = null
        for (i in 0 until tileRows) {
            var column = 0
            var outH = 0
            for (half in 0 until halves) {
                val tile = decodeAsset("planets/%s_%02d_%d.webp".format(prefix, i, half)) ?: return fail(id)
                val sw = tile.width
                val sh = tile.height
                if (sw != width / halves) {
                    tile.recycle()
                    return fail(id)
                }
                if (px.size != sw * sh) px = IntArray(sw * sh)
                tile.getPixels(px, 0, sw, 0, 0, sw, sh)
                tile.recycle()
                val outW = sw / step
                outH = sh / step
                val out = bytes?.takeIf { it.capacity() == outW * outH } ?: ByteBuffer.allocateDirect(outW * outH).also { bytes = it }
                out.clear()
                val area = step * step
                for (y in 0 until outH) {
                    for (x in 0 until outW) {
                        var sum = 0
                        for (dy in 0 until step) {
                            val base = (y * step + dy) * sw + x * step
                            for (dx in 0 until step) sum += (px[base + dx] shr 16) and 0xFF
                        }
                        out.put((sum / area).toByte())
                    }
                }
                out.position(0)
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, column, row, outW, outH, GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, out)
                column += outW
            }
            row += outH
        }
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return id
    }

    private fun hasAsset(path: String): Boolean = runCatching { assets.open(path).close() }.isSuccess

    /**
     * The big map of a solid planet or moon: 8192 × 4096 pixels of three bytes (134 MB with its smaller sizes on the graphics chip),
     * from [HD_STRIPS] strips of 8192 × 256 – a strip at a time, so that no more than a few megabytes are in memory at once. Where
     * the phone is short of memory (or the chip can't take it) every 2 × 2 pixels are averaged: 4096 × 2048. 0 if there are no
     * strips (the gas planets) or one can't be read.
     */
    private fun loadHdMap(name: String): Int {
        if (!hasAsset("planets/${name}_hd_00.webp")) return 0
        var step = textureStep(HD_W, if (fullDetail) 1 else 2)
        while (true) {
            val id = tryLoadHdMap(name, step)
            if (id != 0 || step >= 4) return id
            // The graphics memory is full: with less.
            step *= 2
        }
    }

    private fun tryLoadHdMap(name: String, step: Int): Int {
        val w = HD_W / step
        val h = HD_H / step
        val id = newTexture(true)
        GLES20.glGetError()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGB8, w, h, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null)
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            return 0
        }
        var row = 0
        var px = IntArray(0)
        var out: ByteBuffer? = null
        var raw = ByteArray(0)
        for (i in 0 until HD_STRIPS) {
            val tile = decodeAsset("planets/%s_hd_%02d.webp".format(name, i)) ?: return fail(id)
            val sw = tile.width
            val sh = tile.height
            if (sw != HD_W || sh != HD_H / HD_STRIPS) {
                tile.recycle()
                return fail(id)
            }
            if (px.size != sw * sh) px = IntArray(sw * sh)
            tile.getPixels(px, 0, sw, 0, 0, sw, sh)
            tile.recycle()
            val outW = sw / step
            val outH = sh / step
            val size = outW * outH * 3
            if (raw.size != size) raw = ByteArray(size)
            if (step == 1) {
                var o = 0
                for (k in 0 until sw * sh) {
                    val c = px[k]
                    raw[o++] = (c shr 16).toByte()
                    raw[o++] = (c shr 8).toByte()
                    raw[o++] = c.toByte()
                }
            } else {
                val area = step * step
                var o = 0
                for (y in 0 until outH) {
                    for (x in 0 until outW) {
                        var r = 0
                        var g = 0
                        var b = 0
                        for (dy in 0 until step) {
                            val base = (y * step + dy) * sw + x * step
                            for (dx in 0 until step) {
                                val c = px[base + dx]
                                r += (c shr 16) and 0xFF
                                g += (c shr 8) and 0xFF
                                b += c and 0xFF
                            }
                        }
                        raw[o++] = (r / area).toByte()
                        raw[o++] = (g / area).toByte()
                        raw[o++] = (b / area).toByte()
                    }
                }
            }
            val buffer = out?.takeIf { it.capacity() == size } ?: ByteBuffer.allocateDirect(size).also { out = it }
            buffer.clear()
            buffer.put(raw)
            buffer.position(0)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, row, outW, outH, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buffer)
            row += outH
        }
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return id
    }

    /** Drops a texture that couldn't be made whole; 0. */
    private fun fail(id: Int): Int {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        return 0
    }

    /** The terrain (the Moon's from LOLA, the Earth's): the east and north parts of the surface normal, two bytes per pixel, from [prefix]_east and _north. 0 if it can't be read. */
    private fun loadMoonNormals(prefix: String): Int {
        val east = decodeAsset("planets/${prefix}_east.webp") ?: return 0
        val w = east.width
        val h = east.height
        val bytes = ByteBuffer.allocateDirect(w * h * 2)
        val chunk = IntArray(w * 128)
        var y = 0
        while (y < h) {
            val rows = minOf(128, h - y)
            east.getPixels(chunk, 0, w, 0, y, w, rows)
            for (i in 0 until w * rows) bytes.put((y * w + i) * 2, ((chunk[i] shr 16) and 0xFF).toByte())
            y += rows
        }
        east.recycle()
        val north = decodeAsset("planets/${prefix}_north.webp") ?: return 0
        if (north.width != w || north.height != h) return 0
        y = 0
        while (y < h) {
            val rows = minOf(128, h - y)
            north.getPixels(chunk, 0, w, 0, y, w, rows)
            for (i in 0 until w * rows) bytes.put((y * w + i) * 2 + 1, ((chunk[i] shr 16) and 0xFF).toByte())
            y += rows
        }
        north.recycle()
        bytes.position(0)
        val id = newTexture(true)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RG8, w, h, 0, GLES30.GL_RG, GLES20.GL_UNSIGNED_BYTE, bytes)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return id
    }

    /**
     * The terrain of a planet or moon from its heights (MOLA, Magellan, New Horizons): the east and north parts of the surface normal, in
     * [NORMAL_STRIPS] strips named "<name>_n_<strip>_e.webp" and "_n.webp" (one byte each, coded so that the deflection is ±(t · t) of
     * the byte's distance from the middle). Where the phone is short of memory every 2 × 2 pixels are averaged. 0 if there are none.
     */
    private fun loadNormalStrips(name: String): Int {
        if (!hasAsset("planets/${name}_n_00_e.webp")) return 0
        var step = if (fullDetail) 1 else 2
        while (true) {
            val id = tryLoadNormalStrips(name, step)
            if (id != 0 || step >= 4) return id
            step *= 2
        }
    }

    private fun tryLoadNormalStrips(name: String, step: Int): Int {
        val first = decodeAsset("planets/%s_n_00_e.webp".format(name)) ?: return 0
        val sw = first.width
        val sh = first.height
        first.recycle()
        val w = sw / step
        val stripH = sh / step
        val id = newTexture(true)
        GLES20.glGetError()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RG8, w, stripH * NORMAL_STRIPS, 0, GLES30.GL_RG, GLES20.GL_UNSIGNED_BYTE, null)
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            return 0
        }
        val buffer = ByteBuffer.allocateDirect(w * stripH * 2)
        val raw = ByteArray(w * stripH * 2)
        val pe = IntArray(sw * sh)
        val pn = IntArray(sw * sh)
        val area = step * step
        for (i in 0 until NORMAL_STRIPS) {
            val east = decodeAsset("planets/%s_n_%02d_e.webp".format(name, i)) ?: return fail(id)
            if (east.width != sw || east.height != sh) {
                east.recycle()
                return fail(id)
            }
            east.getPixels(pe, 0, sw, 0, 0, sw, sh)
            east.recycle()
            val north = decodeAsset("planets/%s_n_%02d_n.webp".format(name, i)) ?: return fail(id)
            if (north.width != sw || north.height != sh) {
                north.recycle()
                return fail(id)
            }
            north.getPixels(pn, 0, sw, 0, 0, sw, sh)
            north.recycle()
            var o = 0
            for (y in 0 until stripH) {
                for (x in 0 until w) {
                    var e = 0
                    var n = 0
                    for (dy in 0 until step) {
                        val base = (y * step + dy) * sw + x * step
                        for (dx in 0 until step) {
                            e += (pe[base + dx] shr 16) and 0xFF
                            n += (pn[base + dx] shr 16) and 0xFF
                        }
                    }
                    raw[o++] = (e / area).toByte()
                    raw[o++] = (n / area).toByte()
                }
            }
            buffer.clear()
            buffer.put(raw)
            buffer.position(0)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, i * stripH, w, stripH, GLES30.GL_RG, GLES20.GL_UNSIGNED_BYTE, buffer)
        }
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return id
    }

    /** The colours of a body drawn from a map of brightness (each place's colour divided by its brightness, halved); grey if there is none. */
    private fun loadChroma(name: String): Int {
        val map = decodeAsset("planets/${name}_chroma.webp")
        if (map != null) {
            val id = upload(map, true, true)
            map.recycle()
            return id
        }
        val id = newTexture(false)
        val grey = ByteBuffer.allocateDirect(3)
        grey.put(byteArrayOf(127, 127, 127))
        grey.position(0)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGB8, 1, 1, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, grey)
        return id
    }

    /**
     * The Earth as a sphere: the day (NASA's Blue Marble, 4096 × 2048), its terrain, and in a second map the lights of the cities (red), the
     * clouds (green) and the sea (blue). False if a map can't be read.
     */
    private fun loadEarth(): Boolean {
        val day = decodeAsset("planets/earth_day.webp") ?: return false
        albedoTex = upload(day, true, true)
        day.recycle()
        extraTex = loadEarthMasks()
        normalTex = loadMoonNormals("earth_normal")
        return albedoTex != 0 && extraTex != 0
    }

    private fun loadEarthMasks(): Int {
        var bytes: ByteBuffer? = null
        var w = 0
        var h = 0
        for ((k, part) in arrayOf("lights", "clouds", "water").withIndex()) {
            val map = decodeAsset("planets/earth_$part.webp") ?: return 0
            if (k == 0) {
                w = map.width
                h = map.height
                bytes = ByteBuffer.allocateDirect(w * h * 3)
            } else if (map.width != w || map.height != h) {
                map.recycle()
                return 0
            }
            val out = bytes ?: return 0
            val chunk = IntArray(w * 128)
            var y = 0
            while (y < h) {
                val rows = minOf(128, h - y)
                map.getPixels(chunk, 0, w, 0, y, w, rows)
                for (i in 0 until w * rows) out.put((y * w + i) * 3 + k, ((chunk[i] shr 16) and 0xFF).toByte())
                y += rows
            }
            map.recycle()
        }
        val out = bytes ?: return 0
        out.position(0)
        val id = newTexture(true)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGB8, w, h, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, out)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return id
    }

    /** How a body is drawn: what its maps are, how strongly its terrain shows, and how strongly relief is read from its brightness. */
    private class Look(val kind: Int, val relief: Float, val pseudo: Float)

    private fun lookOf(s: SuperScene): Look = when (s) {
        SuperScene.Mars -> Look(2, 3.5f, 0f)
        SuperScene.Pluto -> Look(2, 2.2f, 0f)
        SuperScene.Venus -> Look(0, 6.0f, 0f)
        SuperScene.Europa -> Look(2, 0f, 1.5f)
        SuperScene.Io -> Look(0, 0f, 1.2f)
        SuperScene.Ganymede -> Look(0, 0f, 1.4f)
        SuperScene.Earth -> Look(3, 6.0f, 0f)
        else -> Look(0, 0f, 0f)
    }

    /** How strongly the fine cloud structure is drawn on [s] when the camera is close (0 for the solid bodies). */
    private fun gasDetail(s: SuperScene): Float = when (s) {
        SuperScene.Jupiter -> 1.0f
        SuperScene.Saturn -> 0.6f
        SuperScene.Neptune -> 0.7f
        else -> 0f
    }

    private fun planetFile(s: SuperScene): String? = when (s) {
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

    // ---------------------------------------------------------------------------------------------
    // A frame
    // ---------------------------------------------------------------------------------------------

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** One picture at [timeMs] on a surface of [w] × [h]. */
    fun draw(w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        GLES20.glViewport(0, 0, w, h)
        lastHeight = h
        GLES20.glDisable(GLES20.GL_BLEND)
        drawSky(w, h, timeMs, p, m)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        when (kind) {
            Kind.SPHERE -> drawSphere(w, h, timeMs, p, m)
            Kind.DISC -> drawDisc(w, h, p, m)
            Kind.PHOTO -> drawPhoto(w, h, m)
            Kind.GEOMETRY -> drawGeometry(w, h, timeMs, p, m)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        if (verifyNext) {
            // Before the swap: after it the picture in the buffer is no longer ours to read.
            verifyNext = false
            bodyShown = readBodyShown()
        }
        if (!EGL14.eglSwapBuffers(display, eglSurface)) throw IllegalStateException("swap")
    }

    private fun drawSky(w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        val s = sky ?: return
        GLES20.glUseProgram(s.id)
        val d = m.depth.coerceIn(0f, 1f)
        GLES20.glUniform2f(s.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(s.u("uTime"), (timeMs % 600000L) / 1000f)
        GLES20.glUniform2f(s.u("uShift"), m.panX * -0.03f * w + m.tiltX * 0.02f * w, -(m.tiltY * 0.012f * h))
        GLES20.glUniform1f(s.u("uSpread"), 1f + 0.45f * d)
        GLES20.glUniform1f(s.u("uStars"), if (p.stars) 1f else 0f)
        GLES20.glUniform4f(s.u("uBox"), 0f, 0f, w.toFloat(), h.toFloat())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Fills [rot] with the view-to-body rotation for the body turned by [spin] about its tilted axis. */
    private fun rotation(spin: Float, tilt: Float) {
        val c = cos(spin)
        val s = sin(spin)
        val ct = cos(tilt)
        val st = sin(tilt)
        // Row by row: R = Rx(tilt) * Ry(spin); the shader multiplies by its transpose.
        rot[0] = c
        rot[1] = 0f
        rot[2] = s
        rot[3] = st * s
        rot[4] = ct
        rot[5] = -st * c
        rot[6] = -ct * s
        rot[7] = st
        rot[8] = ct * c
    }

    private fun drawSphere(w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        val prog = sphere ?: return
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, m.lockFly, d, pose)
        val radius = w * pose.r * m.zoom * p.size
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.08f, d) + m.tiltX * w * lerp(0.015f, 0.035f, d)
        val cyTop = h * pose.cy + m.tiltY * h * 0.015f
        val cy = h - cyTop
        val moon = scene == SuperScene.Moon
        val rings = scene == SuperScene.Saturn
        // How close the camera is (0 on the lock screen and at the home position, 1 at the closest zoom).
        val near = ((d - 0.4f) / 0.4f).coerceIn(0f, 1f)
        val reach = radius * (if (rings) 2.45f else 1.32f)
        val x0 = maxOf(0f, cx - reach)
        val x1 = minOf(w.toFloat(), cx + reach)
        val y0 = maxOf(0f, cy - reach)
        val y1 = minOf(h.toFloat(), cy + reach)
        if (x1 <= x0 || y1 <= y0) return

        rotation(m.spin, m.lat)
        GLES20.glUseProgram(prog.id)
        GLES20.glUniform2f(prog.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(prog.u("uBox"), x0, y0, x1, y1)
        GLES20.glUniform3f(prog.u("uPlanet"), cx, cy, radius)
        GLES20.glUniformMatrix3fv(prog.u("uRot"), 1, false, rot, 0)
        if (moon) {
            val sx = -0.60f
            val sy = 0.45f
            val sz = 0.66f
            val len = sqrt(sx * sx + sy * sy + sz * sz)
            GLES20.glUniform3f(prog.u("uSun"), sx / len, sy / len, sz / len)
            GLES20.glUniform1i(prog.u("uKind"), 1)
            // The terrain's shadows are felt more the closer the camera is; a little sharpening where the picture is magnified.
            GLES20.glUniform1f(prog.u("uRelief"), 1.3f + 0.4f * near)
            GLES20.glUniform1f(prog.u("uSharp"), 0.15f + 0.25f * near)
            GLES20.glUniform3f(prog.u("uTint"), 1.70f, 1.66f, 1.58f)
            GLES20.glUniform1f(prog.u("uAmbient"), 0.035f)
            GLES20.glUniform1f(prog.u("uGain"), 1.25f)
            GLES20.glUniform1f(prog.u("uLimb"), 0f)
            GLES20.glUniform1f(prog.u("uDetail"), 0.08f * near)
            GLES20.glUniform1f(prog.u("uGas"), 0f)
            GLES20.glUniform1f(prog.u("uPseudo"), 0f)
        } else {
            val terrain = normalTex != 0 && look.relief > 0f
            // The sun lies low, as on the Moon, where the terrain is to show.
            val low = terrain || look.pseudo > 0f
            val sx = if (low) -0.60f else -0.52f
            val sy = if (low) 0.45f else 0.40f
            val sz = if (low) 0.66f else 0.75f
            val len = sqrt(sx * sx + sy * sy + sz * sz)
            GLES20.glUniform3f(prog.u("uSun"), sx / len, sy / len, sz / len)
            GLES20.glUniform1i(prog.u("uKind"), albedoKind)
            // The terrain of the bodies whose heights are known; where they are not, relief is read from the picture's brightness.
            GLES20.glUniform1f(prog.u("uRelief"), if (terrain) look.relief * (0.8f + 0.5f * near) else 0f)
            GLES20.glUniform1f(prog.u("uPseudo"), look.pseudo * (0.6f + 0.6f * near))
            // A little sharpening where the picture is magnified; the gas planets get fine cloud structure on top (their
            // maps end where the spacecraft's pictures end).
            GLES20.glUniform1f(prog.u("uSharp"), 0.10f + 0.25f * near)
            GLES20.glUniform1f(prog.u("uGas"), gasDetail(scene) * near)
            GLES20.glUniform3f(prog.u("uTint"), 1f, 1f, 1f)
            val earth = albedoKind == 3
            GLES20.glUniform1f(prog.u("uAmbient"), if (earth) 0.045f else 0.10f)
            GLES20.glUniform1f(prog.u("uGain"), if (earth) 1.15f else 1.05f)
            GLES20.glUniform1f(prog.u("uLimb"), if (earth) 0.5f else 1f)
            GLES20.glUniform1f(prog.u("uDetail"), 0f)
            // The clouds of the Earth drift against the ground: once round in forty minutes.
            GLES20.glUniform1f(prog.u("uCloud"), (timeMs % 2_400_000L) / 2_400_000f)
        }
        // The haze round some planets.
        when (scene) {
            SuperScene.Mars -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.565f, 0.353f, 0.30f)
            SuperScene.Venus -> GLES20.glUniform4f(prog.u("uGlow"), 0.961f, 0.867f, 0.627f, 0.30f)
            SuperScene.Saturn -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.824f, 0.627f, 0.20f)
            SuperScene.Jupiter -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.784f, 0.627f, 0.20f)
            SuperScene.Neptune -> GLES20.glUniform4f(prog.u("uGlow"), 0.353f, 0.549f, 1.000f, 0.30f)
            SuperScene.Earth -> GLES20.glUniform4f(prog.u("uGlow"), 0.290f, 0.639f, 1.000f, 0.34f)
            else -> GLES20.glUniform4f(prog.u("uGlow"), 0f, 0f, 0f, 0f)
        }
        // Saturn's rings: lying at 17° to the picture's horizontal, seen from a little above – opening up the more the axis leans.
        val open = (0.30f + (abs(sin(m.lat)) - 0.199f) * 0.8f).coerceIn(0.12f, 0.6f)
        GLES20.glUniform4f(prog.u("uRing"), if (rings) 1f else 0f, 0.297f, open, 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, albedoTex)
        GLES20.glUniform1i(prog.u("uAlbedo"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (normalTex != 0) normalTex else albedoTex)
        GLES20.glUniform1i(prog.u("uNormal"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (extraTex != 0) extraTex else albedoTex)
        GLES20.glUniform1i(prog.u("uExtra"), 2)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        lastCenterX = cx.toInt().coerceIn(0, w - 1)
        lastCenterY = cy.toInt().coerceIn(0, h - 1)
    }

    /** The Earth, a photograph of NASA cut to its disc, with the blue of its atmosphere at the rim. */
    private fun drawDisc(w: Int, h: Int, p: SuperParams, m: SuperMotion) {
        val prog = sprite ?: return
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, m.lockFly, d, pose)
        val radius = w * pose.r * m.zoom * p.size
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.10f, d) + m.tiltX * w * lerp(0.02f, 0.05f, d)
        val cy = h - (h * pose.cy + m.tiltY * h * 0.02f)
        val reach = radius * 1.3f
        GLES20.glUseProgram(prog.id)
        GLES20.glUniform2f(prog.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(prog.u("uBox"), maxOf(0f, cx - reach), maxOf(0f, cy - reach), minOf(w.toFloat(), cx + reach), minOf(h.toFloat(), cy + reach))
        GLES20.glUniform1i(prog.u("uMode"), 0)
        GLES20.glUniform3f(prog.u("uPlanet"), cx, cy, radius)
        GLES20.glUniform4f(prog.u("uGlow"), 0.290f, 0.639f, 1.000f, 0.34f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, spriteTex)
        GLES20.glUniform1i(prog.u("uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        lastCenterX = cx.toInt().coerceIn(0, w - 1)
        lastCenterY = cy.toInt().coerceIn(0, h - 1)
    }

    /** A chosen photo, a little bigger than the screen so it has room to move with the tilt and the pages. */
    private fun drawPhoto(w: Int, h: Int, m: SuperMotion) {
        val prog = sprite ?: return
        if (spriteTex == 0) return
        SuperCamera.pose(scene, m.lockFly, m.depth.coerceIn(0f, 1f), pose)
        SuperCamera.photoBox(w, h, photoW, photoH, pose, m.zoom, -(m.tiltX * 0.03f + m.panX * 0.05f) * w, m.tiltY * 0.02f * h, photoBox)
        val left = photoBox[0]
        val top = photoBox[1]
        GLES20.glUseProgram(prog.id)
        GLES20.glUniform2f(prog.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(prog.u("uBox"), left, h - (top + photoBox[3]), left + photoBox[2], h - top)
        GLES20.glUniform1i(prog.u("uMode"), 1)
        // A soft light on the flag in the photo of the "Iwo Jima" scene.
        GLES20.glUniform4f(prog.u("uSpot"), SuperCamera.FLAG_U, SuperCamera.FLAG_V, SuperCamera.FLAG_RU, SuperCamera.FLAG_RV)
        GLES20.glUniform1f(prog.u("uSpotGain"), if (scene == SuperScene.Flag) 1.8f else 1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, spriteTex)
        GLES20.glUniform1i(prog.u("uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** The geometry: drawn by SuperRenderer into a picture of half the size, laid over the sky. */
    private fun drawGeometry(w: Int, h: Int, timeMs: Long, p: SuperParams, m: SuperMotion) {
        val prog = sprite ?: return
        val renderer = layerRenderer ?: return
        val lw = maxOf(2, w / 2)
        val lh = maxOf(2, h / 2)
        var bitmap = layer
        if (bitmap == null || bitmap.width != lw || bitmap.height != lh) {
            bitmap = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            layer = bitmap
            if (spriteTex != 0) GLES20.glDeleteTextures(1, intArrayOf(spriteTex), 0)
            spriteTex = upload(bitmap, false, false)
        }
        bitmap.eraseColor(0)
        renderer.drawGeometryLayer(Canvas(bitmap), lw, lh, timeMs / 1000f, p, m)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, spriteTex)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        GLES20.glUseProgram(prog.id)
        GLES20.glUniform2f(prog.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(prog.u("uBox"), 0f, 0f, w.toFloat(), h.toFloat())
        GLES20.glUniform1i(prog.u("uMode"), 1)
        GLES20.glUniform1f(prog.u("uSpotGain"), 1f)
        GLES20.glUniform1i(prog.u("uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** After the first picture of a scene: was the body drawn (the caller falls back to the processor's pictures if not)? */
    fun looksRight(): Boolean = bodyShown

    private fun readBodyShown(): Boolean {
        if (kind == Kind.PHOTO || kind == Kind.GEOMETRY) return true
        return try {
            val body = pixelSum(lastCenterX, lastCenterY)
            // What the sky alone would show there (the gradient of the sky shader, in the sum of red, green and blue).
            val t = lastCenterY / lastHeight.toFloat()
            val smooth = t * t * (3f - 2f * t)
            val sky = 67f + (13f - 67f) * smooth
            // Black, or just the sky where the body should be: it wasn't drawn.
            body >= 20 && kotlin.math.abs(body - sky) > 12f
        } catch (t: Throwable) {
            true
        }
    }

    private fun pixelSum(x: Int, y: Int): Int {
        readBack.clear()
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readBack)
        return (readBack.get(0).toInt() and 0xFF) + (readBack.get(1).toInt() and 0xFF) + (readBack.get(2).toInt() and 0xFF)
    }

    private companion object {
        /** The Moon's albedo: 16384 × 8192 pixels in 64 rows of two tiles (8192 × 128 each). */
        const val MOON_W = 16384
        const val MOON_H = 8192
        const val MOON_ROWS = 64

        /** The brightness maps of Mars, Pluto and Europa: 16384 × 8192 in 64 rows of tiles; the terrain in 16 strips. */
        const val LUMA_W = 16384
        const val LUMA_H = 8192
        const val LUMA_ROWS = 64
        const val NORMAL_STRIPS = 16

        /** The big maps of the solid planets: 8192 × 4096 in 16 strips. */
        const val HD_W = 8192
        const val HD_H = 4096
        const val HD_STRIPS = 16

        const val BOX_VERTEX = """#version 300 es
uniform vec2 uRes;
uniform vec4 uBox;
out vec2 vUv;
void main() {
  vec2 c = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
  vec2 px = mix(uBox.xy, uBox.zw, c);
  vUv = vec2(c.x, 1.0 - c.y);
  gl_Position = vec4(px / uRes * 2.0 - 1.0, 0.0, 1.0);
}
"""

        const val SKY_VERTEX = BOX_VERTEX

        const val SKY_FRAGMENT = """#version 300 es
precision highp float;
uniform vec2 uRes;
uniform float uTime;
uniform vec2 uShift;
uniform float uSpread;
uniform float uStars;
out vec4 fragColor;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

void main() {
  vec2 frag = gl_FragCoord.xy;
  float t = frag.y / uRes.y;
  vec3 c = mix(vec3(0.039, 0.067, 0.157), vec3(0.004, 0.008, 0.039), smoothstep(0.0, 1.0, t));
  if (uStars > 0.5) {
    float unit = uRes.x / 1080.0;
    vec2 mid = uRes * 0.5;
    for (int i = 0; i < 3; i++) {
      float fi = float(i);
      float cs = (120.0 - fi * 28.0) * unit;
      float depthF = 0.35 + 0.3 * fi;
      vec2 q = mid + (frag - mid) / (1.0 + (uSpread - 1.0) * depthF * 2.0) - uShift * depthF;
      vec2 id = floor(q / cs);
      vec2 f = fract(q / cs);
      float h = hash(id + fi * 31.7);
      if (h > 0.45) {
        vec2 pos = 0.2 + 0.6 * vec2(hash(id + 11.3), hash(id + 57.1));
        float d = length((f - pos) * cs);
        float rad = (0.8 + 1.5 * hash(id + 3.7)) * unit;
        float tw = 0.6 + 0.4 * sin(uTime * 0.9 + 6.2831 * hash(id + 5.5));
        float b = (1.0 - smoothstep(rad * 0.35, rad, d)) * tw * (0.55 + 0.45 * hash(id + 9.9));
        c += vec3(0.92, 0.94, 1.0) * b;
      }
    }
  }
  fragColor = vec4(c, 1.0);
}
"""

        const val SPRITE_FRAGMENT = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
uniform sampler2D uTex;
uniform int uMode;
uniform vec3 uPlanet;
uniform vec4 uGlow;
uniform vec4 uSpot;
uniform float uSpotGain;
out vec4 fragColor;

void main() {
  if (uMode == 1) {
    vec4 c = texture(uTex, vUv);
    // A soft light (uSpotGain > 1) on an ellipse of the picture.
    float k = 1.0 + (uSpotGain - 1.0) * (1.0 - smoothstep(0.0, 1.0, length((vUv - uSpot.xy) / uSpot.zw)));
    fragColor = vec4(c.rgb * k, c.a);
    return;
  }
  vec2 p = (gl_FragCoord.xy - uPlanet.xy) / uPlanet.z;
  float rr = length(p);
  vec2 uv = vec2(0.5 + p.x * 0.4724, 0.5 - p.y * 0.4724);
  vec4 c = texture(uTex, uv);
  if (rr > 1.06) c = vec4(0.0);
  float g = uGlow.a * pow(clamp(1.0 - (rr - 1.0) / 0.22, 0.0, 1.0), 2.0);
  vec4 glow = vec4(uGlow.rgb * g, g);
  fragColor = c + glow * (1.0 - c.a);
}
"""

        const val SPHERE_FRAGMENT = """#version 300 es
precision highp float;
precision highp sampler2D;
uniform vec3 uPlanet;
uniform mat3 uRot;
uniform vec3 uSun;
uniform sampler2D uAlbedo;
uniform sampler2D uNormal;
uniform sampler2D uExtra;
uniform float uPseudo;
uniform float uCloud;
uniform int uKind;
uniform float uRelief;
uniform float uSharp;
uniform vec3 uTint;
uniform float uAmbient;
uniform float uGain;
uniform float uLimb;
uniform float uDetail;
uniform float uGas;
uniform vec4 uGlow;
uniform vec4 uRing;
out vec4 fragColor;

const float PI = 3.14159265;

float h3(vec3 p) {
  p = fract(p * 0.3183099 + 0.1);
  p *= 17.0;
  return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vn(vec3 x) {
  vec3 i = floor(x);
  vec3 f = fract(x);
  f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
  return mix(mix(mix(h3(i + vec3(0.0, 0.0, 0.0)), h3(i + vec3(1.0, 0.0, 0.0)), f.x),
                 mix(h3(i + vec3(0.0, 1.0, 0.0)), h3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
             mix(mix(h3(i + vec3(0.0, 0.0, 1.0)), h3(i + vec3(1.0, 0.0, 1.0)), f.x),
                 mix(h3(i + vec3(0.0, 1.0, 1.0)), h3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float hr(float x) {
  return fract(sin(x * 12.9898) * 43758.5453);
}

// Fine cloud structure for the gas planets (the maps end where the spacecraft's pictures end): noise with its cells pressed
// together in the direction of the poles, so that everything is drawn out along the bands, as the winds draw it.
float fbm(vec3 q) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    s += a * vn(q);
    q = q * 2.07 + vec3(11.3, 7.1, 3.7);
    a *= 0.5;
  }
  return s;
}

// The brightness of the map at [p] (the colour maps' mean, the brightness maps' value).
float lumAt(vec2 p, vec2 gx, vec2 gy) {
  vec3 c = textureGrad(uAlbedo, p, gx, gy).rgb;
  return (uKind == 2) ? c.r : dot(c, vec3(0.299, 0.587, 0.114));
}

vec4 ringColor(float rho, float aa) {
  float edge = smoothstep(1.27, 1.27 + aa, rho) * (1.0 - smoothstep(2.25 - aa, 2.25, rho));
  if (edge <= 0.0) return vec4(0.0);
  float idx = floor((rho - 1.27) / 0.022);
  float a = 0.30 + 0.5 * hr(idx);
  if (rho > 1.76 && rho < 1.83) a = 0.06;
  if (rho < 1.5) a *= 0.6;
  if (rho > 1.83) a *= 0.85;
  vec3 col = (mod(idx, 2.0) < 1.0) ? vec3(0.886, 0.808, 0.627) : vec3(0.804, 0.722, 0.549);
  a = min(a * 1.25, 0.95) * edge;
  return vec4(col * a, a);
}

void main() {
  vec2 p = (gl_FragCoord.xy - uPlanet.xy) / uPlanet.z;
  float rr = length(p);
  float rc = min(rr, 0.9995);
  vec2 pc = (rr > 0.0001) ? p * (rc / rr) : p;
  vec3 n = vec3(pc, sqrt(max(1.0 - rc * rc, 0.0)));
  vec3 b = uRot * n;
  float lon = atan(b.x, b.z);
  float lat = asin(clamp(b.y, -1.0, 1.0));
  float u = lon * 0.15915494 + 0.5;
  float v = 0.5 - lat * 0.31830989;
  float uf = fract(u);
  float ug = fract(u + 0.5) - 0.5;
  vec2 dxA = dFdx(vec2(uf, v));
  vec2 dyA = dFdy(vec2(uf, v));
  vec2 dxB = dFdx(vec2(ug, v));
  vec2 dyB = dFdy(vec2(ug, v));
  bool useA = abs(dxA.x) + abs(dyA.x) <= abs(dxB.x) + abs(dyB.x);
  vec2 gx = useA ? dxA : dxB;
  vec2 gy = useA ? dyA : dyB;
  vec2 uv = vec2(uf, v);
  vec3 albedo;
  vec3 ex = vec3(0.0);
  float cl = 0.0;
  if (uKind == 1) {
    float a = textureGrad(uAlbedo, uv, gx, gy).r;
    float lo = textureGrad(uAlbedo, uv, gx * 3.0, gy * 3.0).r;
    a = clamp(a + uSharp * (a - lo), 0.0, 1.0);
    albedo = vec3(a) * uTint;
  } else if (uKind == 2) {
    // A map of the brightness (16384 × 8192) and a small map of the colours (the colour divided by the brightness, halved).
    float y = textureGrad(uAlbedo, uv, gx, gy).r;
    float lo = textureGrad(uAlbedo, uv, gx * 3.0, gy * 3.0).r;
    y = clamp(y + uSharp * (y - lo), 0.0, 1.0);
    albedo = clamp(textureGrad(uExtra, uv, gx, gy).rgb * (2.0 * y), 0.0, 1.0);
  } else if (uKind == 3) {
    // The Earth: the day, and in the second map the lights of the cities (red), the clouds (green; they drift) and the sea (blue).
    albedo = textureGrad(uAlbedo, uv, gx, gy).rgb;
    if (uSharp > 0.0) {
      vec3 lo = textureGrad(uAlbedo, uv, gx * 3.0, gy * 3.0).rgb;
      albedo = clamp(albedo + uSharp * (albedo - lo), 0.0, 1.0);
    }
    ex = textureGrad(uExtra, uv, gx, gy).rgb;
    cl = textureGrad(uExtra, vec2(uf + uCloud, v), gx, gy).g;
  } else {
    vec2 uvl = uv;
    vec3 qa = vec3(b.x, b.y * 5.0, b.z);
    if (uGas > 0.0) {
      // The belts' edges meander and curl: the map is read a little to the side, by a noise that is drawn out along the bands.
      float wu = fbm(qa * 40.0 + 17.0) - 0.47;
      float wv = fbm(qa * 40.0) - 0.47;
      float bu = fbm(qa * 9.0 + 5.0) - 0.47;
      float bv = fbm(qa * 9.0 + 29.0) - 0.47;
      uvl += uGas * vec2(wu * 0.0012 + bu * 0.0050, wv * 0.0016 + bv * 0.0056);
    }
    albedo = textureGrad(uAlbedo, uvl, gx, gy).rgb;
    if (uSharp > 0.0) {
      vec3 lo = textureGrad(uAlbedo, uvl, gx * 3.0, gy * 3.0).rgb;
      albedo = clamp(albedo + uSharp * (albedo - lo), 0.0, 1.0);
    }
    if (uGas > 0.0) {
      // Thin streaks of cloud along the winds.
      float st = fbm(vec3(b.x, b.y * 5.0, b.z) * 110.0);
      float fi = fbm(vec3(b.x, b.y * 3.0, b.z) * 340.0);
      albedo *= 1.0 + uGas * (0.13 * (st - 0.47) + 0.06 * (fi - 0.47));
    }
  }
  if (uDetail > 0.0) {
    vec3 q = b * 90.0;
    float nz = vn(q) * 0.5 + vn(q * 2.3) * 0.3 + vn(q * 5.1) * 0.2;
    albedo *= 1.0 + uDetail * (nz - 0.5);
  }
  vec3 nv = n;
  vec2 ne = vec2(0.0);
  if (uRelief > 0.0) {
    vec2 t = textureGrad(uNormal, uv, gx, gy).rg * 2.0 - 1.0;
    ne = sign(t) * t * t * uRelief;
  }
  if (uPseudo > 0.0) {
    // Where no heights are known the brightness of the picture is read as height (the darker, the deeper).
    vec2 st = vec2(max(abs(gx.x), abs(gy.x)), max(abs(gx.y), abs(gy.y))) * 1.4;
    float du = lumAt(uv + vec2(st.x, 0.0), gx, gy) - lumAt(uv - vec2(st.x, 0.0), gx, gy);
    float dv = lumAt(uv + vec2(0.0, st.y), gx, gy) - lumAt(uv - vec2(0.0, st.y), gx, gy);
    ne += clamp(-vec2(du, -dv) * uPseudo, -0.5, 0.5);
  }
  if (uRelief > 0.0 || uPseudo > 0.0) {
    float up = sqrt(max(1.0 - dot(ne, ne), 0.05));
    vec3 east = normalize(vec3(b.z, 0.0, -b.x) + vec3(0.0001, 0.0, 0.0));
    vec3 north = cross(b, east);
    vec3 bn = normalize(ne.x * east + ne.y * north + up * b);
    nv = bn * uRot;
  }
  float lit = clamp(dot(nv, uSun), 0.0, 1.0);
  float shade = uAmbient + uGain * pow(lit, 0.85);
  shade *= mix(1.0, 0.55 + 0.45 * n.z, uLimb);
  vec3 col = clamp(albedo * shade, 0.0, 1.0);
  if (uKind == 3) {
    float day = clamp(dot(n, uSun) * 3.0 + 0.4, 0.0, 1.0);
    // The glint of the sun on the sea, the clouds (lit by the sun, not by the terrain), the lights of the cities on the night side and
    // the blue of the air at the rim.
    vec3 hv = normalize(uSun + vec3(0.0, 0.0, 1.0));
    float glint = pow(max(dot(nv, hv), 0.0), 60.0) * ex.b * (1.0 - cl) * day;
    col += vec3(1.0, 0.95, 0.85) * glint * 0.6;
    float cshade = clamp(uAmbient + 1.1 * pow(clamp(dot(n, uSun), 0.0, 1.0), 0.8), 0.0, 1.0);
    col = mix(col, vec3(cshade), cl * 0.92);
    col += vec3(1.0, 0.76, 0.42) * (ex.r * 1.4) * (1.0 - day) * (1.0 - cl * 0.8);
    float rim = pow(1.0 - n.z, 2.6);
    col += vec3(0.30, 0.58, 1.0) * rim * (0.12 + 0.88 * day) * 0.9;
    col = clamp(col, 0.0, 1.0);
  }
  float edge = clamp((1.0 - rr) * uPlanet.z, 0.0, 1.0);
  vec4 res = vec4(col * edge, edge);
  float g = uGlow.a * pow(clamp(1.0 - (rr - 1.0) / 0.28, 0.0, 1.0), 2.0);
  res = res + vec4(uGlow.rgb * g, g) * (1.0 - res.a);
  if (uRing.x > 0.5) {
    float cs = cos(uRing.y);
    float sn = sin(uRing.y);
    float nzr = uRing.z;
    float nyr = sqrt(1.0 - nzr * nzr);
    vec3 rn = vec3(-nyr * sn, nyr * cs, nzr);
    float zr = -(rn.x * p.x + rn.y * p.y) / rn.z;
    float rho = length(vec3(p, zr));
    vec4 ring = ringColor(rho, 2.0 / uPlanet.z);
    float zs = (rr < 1.0) ? sqrt(1.0 - rr * rr) : -10.0;
    if (zr > zs) {
      res = ring + res * (1.0 - ring.a);
    } else {
      res = res + ring * (1.0 - res.a);
    }
  }
  fragColor = res;
}
"""
    }
}
