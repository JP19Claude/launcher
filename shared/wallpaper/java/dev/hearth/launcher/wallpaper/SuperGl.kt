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
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The graphics chip's way of drawing a Super-Wallpaper (OpenGL ES 3): the Moon and the planets are real spheres, a
 * shader turns the map of the body round them pixel by pixel – so the Moon can come close enough for single craters, with
 * its relief lit by the sun – the sky and the stars are drawn by a shader as well. If anything of this fails the caller
 * falls back to SuperRenderer (the pictures drawn by the processor). All calls on one thread, the one that created it.
 */
internal class SuperGl(private val assets: AssetManager, private val photoFile: File?) {

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
    private var heightTex = 0
    private var spriteTex = 0
    private var heightTexel = floatArrayOf(1f / 1440f, 1f / 720f)
    private var photoW = 1
    private var photoH = 1
    private var layer: Bitmap? = null
    private var layerRenderer: SuperRenderer? = null

    private val pose = SuperPose()
    private val rot = FloatArray(9)
    private val readBack: ByteBuffer = ByteBuffer.allocateDirect(8)
    private var verifyNext = false
    private var bodyShown = true
    private var lastCenterX = 0
    private var lastCenterY = 0

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
        val ids = intArrayOf(albedoTex, heightTex, spriteTex)
        if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
            for (id in ids) if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }
        albedoTex = 0
        heightTex = 0
        spriteTex = 0
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
                    val map = decodeAsset("planets/moon_map.jpg") ?: return false
                    albedoTex = upload(map, true, true)
                    map.recycle()
                    val height = decodeAsset("planets/moon_height.jpg") ?: return false
                    heightTexel = floatArrayOf(1f / height.width, 1f / height.height)
                    heightTex = upload(height, true, true)
                    height.recycle()
                    true
                }
                SuperScene.Earth -> {
                    kind = Kind.DISC
                    val disc = decodeAsset("planets/earth.webp") ?: return false
                    spriteTex = upload(disc, true, false)
                    disc.recycle()
                    true
                }
                SuperScene.Photo -> {
                    kind = Kind.PHOTO
                    val photo = decodePhoto()
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
                    val map = decodeAsset("planets/$name.jpg") ?: return false
                    albedoTex = upload(map, true, true)
                    map.recycle()
                    true
                }
            }
        } catch (t: Throwable) {
            false
        }
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
        GLES20.glDisable(GLES20.GL_BLEND)
        drawSky(w, h, timeMs, p, m)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        when (kind) {
            Kind.SPHERE -> drawSphere(w, h, m)
            Kind.DISC -> drawDisc(w, h, m)
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

    private fun drawSphere(w: Int, h: Int, m: SuperMotion) {
        val prog = sphere ?: return
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, d, pose)
        val radius = w * pose.r * m.zoom
        val cx = w * pose.cx - m.panX * w * lerp(0.03f, 0.08f, d) + m.tiltX * w * lerp(0.015f, 0.035f, d)
        val cyTop = h * pose.cy + m.tiltY * h * 0.015f
        val cy = h - cyTop
        val moon = scene == SuperScene.Moon
        val rings = scene == SuperScene.Saturn
        val reach = radius * (if (rings) 2.45f else 1.32f)
        val x0 = maxOf(0f, cx - reach)
        val x1 = minOf(w.toFloat(), cx + reach)
        val y0 = maxOf(0f, cy - reach)
        val y1 = minOf(h.toFloat(), cy + reach)
        if (x1 <= x0 || y1 <= y0) return

        rotation(m.spin, if (moon) -0.117f else -0.2f)
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
            GLES20.glUniform1f(prog.u("uBump"), 0.09f)
            GLES20.glUniform1f(prog.u("uAmbient"), 0.035f)
            GLES20.glUniform1f(prog.u("uGain"), 1.25f)
            GLES20.glUniform1f(prog.u("uLimb"), 0f)
            GLES20.glUniform1f(prog.u("uDetail"), 0.20f * ((d - 0.4f) / 0.4f).coerceIn(0f, 1f))
        } else {
            val sx = -0.52f
            val sy = 0.40f
            val sz = 0.75f
            val len = sqrt(sx * sx + sy * sy + sz * sz)
            GLES20.glUniform3f(prog.u("uSun"), sx / len, sy / len, sz / len)
            GLES20.glUniform1f(prog.u("uBump"), 0f)
            GLES20.glUniform1f(prog.u("uAmbient"), 0.10f)
            GLES20.glUniform1f(prog.u("uGain"), 1.05f)
            GLES20.glUniform1f(prog.u("uLimb"), 1f)
            GLES20.glUniform1f(prog.u("uDetail"), 0f)
        }
        GLES20.glUniform2f(prog.u("uHeightTexel"), heightTexel[0], heightTexel[1])
        // The haze round some planets.
        when (scene) {
            SuperScene.Mars -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.565f, 0.353f, 0.30f)
            SuperScene.Venus -> GLES20.glUniform4f(prog.u("uGlow"), 0.961f, 0.867f, 0.627f, 0.30f)
            SuperScene.Saturn -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.824f, 0.627f, 0.20f)
            SuperScene.Jupiter -> GLES20.glUniform4f(prog.u("uGlow"), 0.910f, 0.784f, 0.627f, 0.20f)
            SuperScene.Neptune -> GLES20.glUniform4f(prog.u("uGlow"), 0.353f, 0.549f, 1.000f, 0.30f)
            else -> GLES20.glUniform4f(prog.u("uGlow"), 0f, 0f, 0f, 0f)
        }
        // Saturn's rings: lying at 17° to the picture's horizontal, seen from a little above.
        GLES20.glUniform4f(prog.u("uRing"), if (rings) 1f else 0f, 0.297f, 0.30f, 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, albedoTex)
        GLES20.glUniform1i(prog.u("uAlbedo"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (heightTex != 0) heightTex else albedoTex)
        GLES20.glUniform1i(prog.u("uHeight"), 1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        lastCenterX = cx.toInt().coerceIn(0, w - 1)
        lastCenterY = cy.toInt().coerceIn(0, h - 1)
    }

    /** The Earth, a photograph of NASA cut to its disc, with the blue of its atmosphere at the rim. */
    private fun drawDisc(w: Int, h: Int, m: SuperMotion) {
        val prog = sprite ?: return
        val d = m.depth.coerceIn(0f, 1f)
        SuperCamera.pose(scene, d, pose)
        val radius = w * pose.r * m.zoom
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
        val cover = maxOf(w / photoW.toFloat(), h / photoH.toFloat())
        val scale = cover * lerp(1.04f, 1.18f, m.depth.coerceIn(0f, 1f)) * m.zoom
        val dw = photoW * scale
        val dh = photoH * scale
        val slackX = dw - w
        val slackY = dh - h
        val shiftX = (m.tiltX * 0.45f + m.panX * 0.5f).coerceIn(-1f, 1f)
        val shiftY = (m.tiltY * 0.45f).coerceIn(-1f, 1f)
        val left = -slackX / 2f - shiftX * slackX / 2f
        val top = -slackY / 2f - shiftY * slackY / 2f
        GLES20.glUseProgram(prog.id)
        GLES20.glUniform2f(prog.u("uRes"), w.toFloat(), h.toFloat())
        GLES20.glUniform4f(prog.u("uBox"), left, h - (top + dh), left + dw, h - top)
        GLES20.glUniform1i(prog.u("uMode"), 1)
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
        GLES20.glUniform1i(prog.u("uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** After the first picture of a scene: was the body drawn (the caller falls back to the processor's pictures if not)? */
    fun looksRight(): Boolean = bodyShown

    private fun readBodyShown(): Boolean {
        if (kind == Kind.PHOTO || kind == Kind.GEOMETRY) return true
        return try {
            val body = pixelSum(lastCenterX, lastCenterY)
            val sky = pixelSum(1, 1)
            // The body at its centre is lit and clearly brighter than the dark sky at the corner.
            body - sky > 25 || body > 120
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
out vec4 fragColor;

void main() {
  if (uMode == 1) {
    fragColor = texture(uTex, vUv);
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
uniform sampler2D uHeight;
uniform vec2 uHeightTexel;
uniform float uBump;
uniform float uAmbient;
uniform float uGain;
uniform float uLimb;
uniform float uDetail;
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
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(h3(i + vec3(0.0, 0.0, 0.0)), h3(i + vec3(1.0, 0.0, 0.0)), f.x),
                 mix(h3(i + vec3(0.0, 1.0, 0.0)), h3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
             mix(mix(h3(i + vec3(0.0, 0.0, 1.0)), h3(i + vec3(1.0, 0.0, 1.0)), f.x),
                 mix(h3(i + vec3(0.0, 1.0, 1.0)), h3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float hr(float x) {
  return fract(sin(x * 12.9898) * 43758.5453);
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
  vec3 albedo = textureGrad(uAlbedo, uv, gx, gy).rgb;
  if (uDetail > 0.0) {
    vec3 q = b * 90.0;
    float nz = vn(q) * 0.5 + vn(q * 2.3) * 0.3 + vn(q * 5.1) * 0.2;
    albedo *= 1.0 + uDetail * (nz - 0.5);
  }
  vec3 nv = n;
  if (uBump > 0.0) {
    vec2 tx = uHeightTexel;
    float hE = textureGrad(uHeight, uv + vec2(tx.x, 0.0), gx, gy).r;
    float hW = textureGrad(uHeight, uv - vec2(tx.x, 0.0), gx, gy).r;
    float hS = textureGrad(uHeight, uv + vec2(0.0, tx.y), gx, gy).r;
    float hN = textureGrad(uHeight, uv - vec2(0.0, tx.y), gx, gy).r;
    float cl = max(cos(lat), 0.2);
    float gxh = (hE - hW) / (2.0 * tx.x * 2.0 * PI * cl);
    float gyh = (hN - hS) / (2.0 * tx.y * PI);
    vec3 east = normalize(vec3(b.z, 0.0, -b.x) + vec3(0.0001, 0.0, 0.0));
    vec3 north = cross(b, east);
    vec3 bn = normalize(b - uBump * (gxh * east + gyh * north));
    nv = bn * uRot;
  }
  float lit = clamp(dot(nv, uSun), 0.0, 1.0);
  float shade = uAmbient + uGain * pow(lit, 0.85);
  shade *= mix(1.0, 0.55 + 0.45 * n.z, uLimb);
  vec3 col = clamp(albedo * shade, 0.0, 1.0);
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
