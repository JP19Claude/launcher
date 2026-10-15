package dev.hearth.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * ZENITH 21.5 – the brush of the wallpaper gallery: a picture of [w] × [h] pixels and the things one paints on it – a
 * sky, stars, glows, the Moon and the planets, mountains, a sea with the Moon's light on it, pines, a city, clouds,
 * aurora, nebulae, a galaxy … Positions are in pixels (use [fw] and [fh]); sizes that should look the same on every
 * screen are multiplied with [u] (1 at a width of 1080 pixels). Colours are 0xRRGGBB.
 */
internal class ArtCanvas(val w: Int, val h: Int, val seed: Int = 1) {

    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    val fw = w.toFloat()
    val fh = h.toFloat()
    val u = w / 1080f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private fun argb(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (rgb and 0xFFFFFF)

    private fun opaque(rgb: Int): Int = (0xFF shl 24) or (rgb and 0xFFFFFF)

    // -----------------------------------------------------------------------------------------------------------------
    // The ground of every picture
    // -----------------------------------------------------------------------------------------------------------------

    /** The sky: colours from the top (0) to the bottom (1). */
    fun sky(vararg stops: Pair<Float, Int>) {
        val pos = FloatArray(stops.size) { stops[it].first }
        val col = IntArray(stops.size) { opaque(stops[it].second) }
        paint.shader = LinearGradient(0f, 0f, 0f, fh, col, pos, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, fw, fh, paint)
        paint.shader = null
    }

    /** A soft light: [alpha] in the middle, fading to nothing at [r] ([power] says how fast; [sx] stretches it sideways). */
    fun glow(cx: Float, cy: Float, r: Float, color: Int, alpha: Float, power: Float = 2f, sx: Float = 1f) {
        val steps = 8
        val cols = IntArray(steps + 1)
        val pos = FloatArray(steps + 1)
        for (i in 0..steps) {
            val d = i / steps.toFloat()
            pos[i] = d
            cols[i] = argb(color, alpha * (1f - d).pow(power))
        }
        paint.shader = RadialGradient(cx, cy, r, cols, pos, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(sx, 1f, cx, cy)
        canvas.drawCircle(cx, cy, r, paint)
        canvas.restore()
        paint.shader = null
    }

    /** Stars, scattered between [y0] and [y1] (fractions of the height); a few are bigger and glow. */
    fun stars(n: Int, y0: Float = 0f, y1: Float = 1f, bright: Float = 1f, size: Float = 1f, tint: Int = 0xFFFFFF, seedOffset: Int = 99) {
        val r = Random(seed + seedOffset)
        for (i in 0 until n) {
            val x = r.nextFloat() * fw
            val y = (y0 + (y1 - y0) * r.nextFloat()) * fh
            val m = r.nextFloat().pow(3)
            val rad = (0.7f + 2.2f * m) * u * size * 1.1f
            val al = (0.25f + 0.75f * m) * bright
            paint.color = argb(tint, al)
            canvas.drawCircle(x, y, rad, paint)
            if (m > 0.55f) {
                paint.color = argb(tint, al * 0.18f)
                canvas.drawCircle(x, y, rad * 3f, paint)
            }
        }
    }

    /** A band of haze or colour across the picture: [a0] strong at [y0] to [a1] at [y1]. */
    fun fogBand(y0: Float, y1: Float, color: Int, a0: Float, a1: Float) {
        paint.shader = LinearGradient(0f, y0, 0f, y1, argb(color, a0), argb(color, a1), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, min(y0, y1), fw, max(y0, y1), paint)
        paint.shader = null
    }

    /** Fills the picture's bottom from [y] on with a colour fading to another. */
    fun ground(y: Float, top: Int, bottom: Int) {
        paint.shader = LinearGradient(0f, y, 0f, fh, opaque(top), opaque(bottom), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, y, fw, fh, paint)
        paint.shader = null
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Land
    // -----------------------------------------------------------------------------------------------------------------

    /** A mountain range: ridged noise; [base] is where its middle stands, [amp] its height, [freq] how many peaks. */
    fun peaks(base: Float, amp: Float, freq: Float, noiseSeed: Int, top: Int, bottom: Int, to: Float, off: Float = 0f) {
        path.reset()
        val step = max(3f, fw / 180f)
        var x = 0f
        var minY = fh
        path.moveTo(0f, fh)
        while (x <= fw + step) {
            val t = x / fw * freq + off
            val rid = 1f - abs(2f * artFbm1(t, 4, noiseSeed) - 1f)
            val soft = artFbm1(t * 0.5f, 3, noiseSeed + 20)
            val y = base - amp * (0.65f * rid.pow(1.6f) + 0.35f * soft)
            if (y < minY) minY = y
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(fw + step, fh)
        path.close()
        paint.shader = LinearGradient(0f, minY, 0f, to, opaque(top), opaque(bottom), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    /** Rolling dunes or hills: two slow waves; [light] (if not 0) shines along the crest. */
    fun hills(base: Float, amp: Float, freq: Float, phase: Float, top: Int, bottom: Int, light: Int = 0, lightAlpha: Float = 0.5f) {
        path.reset()
        val step = max(3f, fw / 200f)
        var x = 0f
        var minY = fh
        val ys = ArrayList<Float>()
        path.moveTo(0f, fh)
        while (x <= fw + step) {
            val t = x / fw
            val y = base - amp * sin(t * freq + phase) - amp * 0.5f * sin(t * freq * 2.7f + phase * 1.7f)
            if (y < minY) minY = y
            path.lineTo(x, y)
            ys.add(y)
            x += step
        }
        path.lineTo(fw + step, fh)
        path.close()
        paint.shader = LinearGradient(0f, minY, 0f, fh, opaque(top), opaque(bottom), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        if (light != 0) {
            strokePaint.strokeWidth = max(1f, 2.2f * u)
            strokePaint.color = argb(light, lightAlpha)
            path.reset()
            var px = 0f
            for (k in ys.indices) {
                if (k == 0) path.moveTo(px, ys[k]) else path.lineTo(px, ys[k])
                px += step
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    /** One row of pine trees along [baseY]; [hMin]/[hMax] are tree heights as fractions of the picture's height. */
    fun pines(color: Int, baseY: Float, hMin: Float, hMax: Float, count: Int, seedOffset: Int = 8) {
        val r = Random(seed + seedOffset)
        paint.color = opaque(color)
        for (i in 0 until count) {
            val x = (i + r.nextFloat() * 0.8f) / count * (fw * 1.1f) - fw * 0.05f
            val th = fh * (hMin + (hMax - hMin) * r.nextFloat())
            val tw = th * (0.28f + 0.1f * r.nextFloat())
            val tiers = 5
            path.reset()
            path.moveTo(x, baseY - th)
            for (t in 0 until tiers) {
                val f = (t + 1) / tiers.toFloat()
                path.lineTo(x + tw * f * 0.6f * (if (t % 2 == 0) 1f else 0.78f), baseY - th + th * f * 0.96f)
            }
            path.lineTo(x + tw * 0.05f, baseY)
            path.lineTo(x - tw * 0.05f, baseY)
            for (t in tiers - 1 downTo 0) {
                val f = (t + 1) / tiers.toFloat()
                path.lineTo(x - tw * f * 0.6f * (if (t % 2 == 0) 1f else 0.78f), baseY - th + th * f * 0.96f)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /** A row of buildings along [baseY] with lit windows ([lit] = the share of them that shine). */
    fun city(color: Int, baseY: Float, hMin: Float, hMax: Float, buildingWidth: Float, lit: Float, windowColor: Int = 0xFFD88A, seedOffset: Int = 2) {
        val r = Random(seed + seedOffset)
        var x = -10f * u
        while (x < fw) {
            val bw = buildingWidth * (0.7f + 0.9f * r.nextFloat())
            val bh = fh * (hMin + (hMax - hMin) * r.nextFloat().pow(1.3f))
            paint.color = opaque(color)
            canvas.drawRect(x, baseY - bh, x + bw, baseY + 5f * u, paint)
            if (lit > 0f) {
                paint.color = argb(windowColor, 0.9f)
                var wx = x + 12f * u
                while (wx + 9f * u < x + bw - 9f * u) {
                    var wy = baseY - bh + 18f * u
                    while (wy < baseY - 18f * u) {
                        if (r.nextFloat() < lit) canvas.drawRect(wx, wy, wx + 6f * u, wy + 9f * u, paint)
                        wy += 24f * u
                    }
                    wx += 18f * u
                }
            }
            x += bw + r.nextFloat() * 9f * u
        }
    }

    /** A bare tree, branch by branch. */
    fun bareTree(x: Float, baseY: Float, length: Float, color: Int, depth: Int = 9, spread: Float = 0.5f, seedOffset: Int = 5) {
        val r = Random(seed + seedOffset)
        strokePaint.color = opaque(color)
        fun branch(x0: Float, y0: Float, len: Float, ang: Float, d: Int) {
            if (d == 0 || len < 2f) return
            val x1 = x0 + sin(ang) * len
            val y1 = y0 - cos(ang) * len
            strokePaint.strokeWidth = max(1f, len * 0.11f)
            canvas.drawLine(x0, y0, x1, y1, strokePaint)
            val n = if (d > 3 && r.nextFloat() < 0.35f) 3 else 2
            for (k in 0 until n) {
                val da = (k - (n - 1) / 2f) * spread + (r.nextFloat() - 0.5f) * 0.35f
                branch(x1, y1, len * (0.68f + 0.1f * r.nextFloat()), ang + da, d - 1)
            }
        }
        branch(x, baseY, length, 0f, depth)
    }

    /** Grey airless ground with craters, for the Moon's surface; the horizon runs at [horizonY]. */
    fun lunarGround(horizonY: Float, craters: Int = 60, light: Int = 0x9A9AA2, dark: Int = 0x1E1E24) {
        path.reset()
        val step = max(3f, fw / 160f)
        var x = 0f
        var minY = fh
        path.moveTo(0f, fh)
        while (x <= fw + step) {
            val y = horizonY - fh * 0.05f * artFbm1(x / fw * 3f, 4, 3)
            if (y < minY) minY = y
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(fw + step, fh)
        path.close()
        paint.shader = LinearGradient(0f, minY, 0f, fh, opaque(light), opaque(dark), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        val r = Random(seed + 6)
        strokePaint.strokeCap = Paint.Cap.BUTT
        for (i in 0 until craters) {
            val t = r.nextFloat().pow(1.7f)
            val cy = horizonY + (fh - horizonY) * (0.05f + 0.95f * t)
            val cx = r.nextFloat() * fw
            val rw = fw * (0.02f + 0.2f * t * r.nextFloat())
            val rh = rw * (0.18f + 0.5f * t)
            // The floor: dark, with a lit rim on the far side.
            rect.set(cx - rw, cy - rh, cx + rw, cy + rh)
            paint.color = argb(0x16161C, 0.5f)
            canvas.drawOval(rect, paint)
            strokePaint.strokeWidth = max(1f, rw * 0.10f)
            strokePaint.color = argb(0xC8C8D0, 0.32f)
            canvas.drawArc(rect, 180f, 180f, false, strokePaint)
        }
        strokePaint.strokeCap = Paint.Cap.ROUND
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Water
    // -----------------------------------------------------------------------------------------------------------------

    /** A sea from [horizonY] down; the Moon at [moonX] with the radius [moonR] lays a path of light on it ([strength] 0 = none). */
    fun sea(horizonY: Float, top: Int, bottom: Int, moonX: Float, moonR: Float, light: Int = 0xE8F0FF, strength: Float = 0.8f) {
        ground(horizonY, top, bottom)
        if (strength <= 0f) return
        val rowStep = max(2f, 5f * u)
        var y = horizonY + 1f
        while (y < fh) {
            val t = (y - horizonY) / (fh - horizonY)
            val hw = moonR * (0.35f + 0.9f * t)
            val py = y / (3f * u)
            val wob = (artNoise1(py * 0.09f, 4) - 0.5f) * moonR * 0.8f * t
            val amp = (0.25f + 0.75f * artNoise1(py * 0.21f, 8)) * (1f - 0.65f * t) * strength
            val c = moonX + wob
            val x0 = c - 2f * hw
            val x1 = c + 2f * hw
            val cols = intArrayOf(
                argb(light, 0f), argb(light, 0.37f * amp), argb(light, 0.78f * amp), argb(light, amp),
                argb(light, 0.78f * amp), argb(light, 0.37f * amp), argb(light, 0f),
            )
            val pos = floatArrayOf(0f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1f)
            paint.shader = LinearGradient(x0, 0f, x1, 0f, cols, pos, Shader.TileMode.CLAMP)
            canvas.drawRect(x0, y, x1, y + rowStep, paint)
            y += rowStep
        }
        paint.shader = null
    }

    /** A still lake from [horizonY] down: it mirrors what is above, darkened to [tint], with a few ripples. */
    fun lake(horizonY: Float, tint: Int, tintAlpha: Float, ripple: Int = 0xFFFFFF) {
        val hy = horizonY.toInt().coerceIn(1, h - 1)
        ground(horizonY, tint, tint)
        val copy = Bitmap.createBitmap(bitmap, 0, 0, w, hy)
        canvas.save()
        canvas.clipRect(0f, horizonY, fw, fh)
        canvas.scale(1f, -1f, 0f, horizonY)
        bitmapPaint.alpha = 190
        canvas.drawBitmap(copy, 0f, 0f, bitmapPaint)
        bitmapPaint.alpha = 255
        canvas.restore()
        copy.recycle()
        fogBand(horizonY, fh, tint, tintAlpha * 0.5f, tintAlpha)
        val r = Random(seed + 12)
        for (i in 0 until 70) {
            val t = r.nextFloat().pow(0.8f)
            val y = horizonY + (fh - horizonY) * t
            val len = fw * (0.04f + 0.18f * r.nextFloat()) * (0.4f + t)
            val x = r.nextFloat() * fw
            strokePaint.strokeWidth = max(1f, 1.6f * u * (0.5f + t))
            strokePaint.color = argb(ripple, 0.05f + 0.10f * r.nextFloat())
            canvas.drawLine(x, y, x + len, y, strokePaint)
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Sky
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Clouds, soft blobs between [yTop] and [yBottom]; near the point ([litX], [litY]) they take the colour [lit]
     * (the Moon's light), far from it [dark].
     */
    fun clouds(
        count: Int,
        yTop: Float,
        yBottom: Float,
        dark: Int,
        lit: Int,
        litX: Float,
        litY: Float,
        litReach: Float,
        alphaMin: Float = 0.30f,
        alphaVar: Float = 0.35f,
        widthMin: Float = 0.14f,
        widthVar: Float = 0.28f,
        seedOffset: Int = 3,
    ) {
        val r = Random(seed + seedOffset)
        for (i in 0 until count) {
            val x = r.nextFloat() * fw * 1.2f - fw * 0.1f
            val y = yTop + (yBottom - yTop) * r.nextFloat()
            val rw = fw * (widthMin + widthVar * r.nextFloat())
            val rh = rw * (0.12f + 0.12f * r.nextFloat())
            val a = alphaMin + alphaVar * r.nextFloat()
            val near = exp(-hypot(x - litX, y - litY) / litReach)
            val col = artMix(dark, lit, near)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(rw, rh)
            paint.shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(argb(col, a), argb(col, a * 0.44f), argb(col, a * 0.11f), argb(col, 0f)),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
        paint.shader = null
    }

    /** Curtains of northern lights: each layer is a colour and a strength; the top edge runs about [topFrac] of the way down. */
    fun aurora(topFrac: Float, layers: List<Pair<Int, Float>>, noiseSeed: Int = 7) {
        val step = max(1.5f, 2f * u)
        val pos = floatArrayOf(0f, 0.206f, 0.353f, 0.5f, 0.647f, 0.794f, 0.941f, 1f)
        val prof = floatArrayOf(0f, 0.15f, 0.40f, 0.74f, 1f, 0.45f, 0.04f, 0f)
        for (li in layers.indices) {
            val color = layers[li].first
            val amp = layers[li].second
            var x = 0f
            while (x < fw) {
                val tx = x / fw
                val c = fh * (topFrac + 0.12f * artFbm1(tx * 2.2f, 3, noiseSeed)) + fh * 0.10f * li
                val wdt = fh * (0.10f + 0.05f * artFbm1(tx * 3f + li, 3, noiseSeed + 12))
                val ray = 0.55f + 0.45f * artFbm1(tx * 60f + li * 9f, 2, noiseSeed + 26)
                val y0 = c - 2.2f * wdt
                val y1 = c + 1.2f * wdt
                val cols = IntArray(prof.size) { argb(color, prof[it] * ray * amp) }
                paint.shader = LinearGradient(0f, y0, 0f, y1, cols, pos, Shader.TileMode.CLAMP)
                canvas.drawRect(x, y0, x + step + 1f, y1, paint)
                x += step
            }
        }
        paint.shader = null
    }

    /** A cloud of gas and dust in two colours, made from noise at a lower resolution; [cover] 0.4 (much) … 0.65 (little). */
    fun nebula(c1: Int, c2: Int, noiseSeed: Int, cover: Float) {
        val nw = max(24, w / 4)
        val nh = max(24, h / 4)
        val px = IntArray(nw * nh)
        for (y in 0 until nh) {
            for (x in 0 until nw) {
                val fx = x.toFloat() / nw * 3.2f
                val fy = y.toFloat() / nh * 5.2f
                val wx = artFbm3(fx + 7f, fy, 1.3f, 3, noiseSeed) * 2.2f
                val wy = artFbm3(fx, fy + 9f, 4.1f, 3, noiseSeed + 4) * 2.2f
                val v = artFbm3(fx + wx, fy + wy, 2.2f, 5, noiseSeed + 9)
                val inten = artSmooth(cover - 0.12f, cover + 0.30f, v)
                val k = artFbm3(fx * 0.8f + wy, fy * 0.8f, 6.1f, 2, noiseSeed + 2)
                val dust = artSmooth(0.58f, 0.8f, artFbm3(fx * 1.5f + 3f, fy * 1.5f + wx, 8.8f, 3, noiseSeed + 13))
                val al = (inten * 0.95f * (1f - 0.7f * dust)).coerceIn(0f, 1f)
                px[y * nw + x] = ((al * 255f).toInt() shl 24) or (artMix(c1, c2, k) and 0xFFFFFF)
            }
        }
        val layer = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        layer.setPixels(px, 0, nw, 0, 0, nw, nh)
        rect.set(0f, 0f, fw, fh)
        canvas.drawBitmap(layer, null, rect, bitmapPaint)
        layer.recycle()
    }

    /** A spiral galaxy seen from the side: [squash] 1 = face on, 0.3 = steep. */
    fun galaxy(cx: Float, cy: Float, r: Float, tilt: Float, squash: Float, inner: Int, outer: Int, arms: Int = 2) {
        glow(cx, cy, r * 1.15f, outer, 0.22f, 2f, 1f)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        canvas.scale(1f, squash)
        paint.shader = RadialGradient(
            0f, 0f, r * 0.5f,
            intArrayOf(argb(inner, 0.9f), argb(inner, 0.45f), argb(outer, 0f)),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, 0f, r * 0.5f, paint)
        paint.shader = null
        val rnd = Random(seed + 31)
        for (i in 0 until 7000) {
            val arm = rnd.nextInt(arms)
            val t = rnd.nextFloat().pow(0.75f)
            val spread = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * 0.55f * (1f - 0.45f * t)
            val ang = t * 5.4f + arm * (2f * PI.toFloat() / arms) + spread
            val rad = t * r
            val sx = cos(ang) * rad + (rnd.nextFloat() - 0.5f) * r * 0.03f
            val sy = sin(ang) * rad + (rnd.nextFloat() - 0.5f) * r * 0.03f
            paint.color = argb(artMix(inner, outer, t), (0.20f + 0.55f * rnd.nextFloat()) * (1f - 0.5f * t))
            canvas.drawCircle(sx, sy, (0.5f + 1.5f * rnd.nextFloat()) * u * 1.3f, paint)
        }
        canvas.restore()
    }

    /** The Milky Way: a broad band of faint light, dust lanes and a crowd of stars, from ([x0],[y0]) to ([x1],[y1]). */
    fun milkyWay(x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
        val r = Random(seed + 41)
        val colors = intArrayOf(0x8FA8FF, 0xFFC8E0, 0xFFE3A8, 0x9CC8FF)
        for (i in 0 until 46) {
            val t = -0.15f + 1.3f * i / 45f
            val x = x0 + (x1 - x0) * t + (r.nextFloat() - 0.5f) * width * 0.35f
            val y = y0 + (y1 - y0) * t + (r.nextFloat() - 0.5f) * width * 0.35f
            glow(x, y, width * (0.55f + 0.5f * r.nextFloat()), colors[i % colors.size], 0.07f + 0.07f * r.nextFloat(), 1.5f)
        }
        for (i in 0 until 22) {
            val t = r.nextFloat()
            val x = x0 + (x1 - x0) * t + (r.nextFloat() - 0.5f) * width * 0.6f
            val y = y0 + (y1 - y0) * t + (r.nextFloat() - 0.5f) * width * 0.6f
            glow(x, y, width * (0.18f + 0.22f * r.nextFloat()), 0x02030A, 0.30f, 1.4f)
        }
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy)
        val nx = -dy / len
        val ny = dx / len
        for (i in 0 until 2600) {
            val t = r.nextFloat() * 1.3f - 0.15f
            val off = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) * width * 0.55f
            val x = x0 + dx * t + nx * off
            val y = y0 + dy * t + ny * off
            paint.color = argb(0xFFFFFF, 0.15f + 0.6f * r.nextFloat().pow(2))
            canvas.drawCircle(x, y, (0.5f + 1.2f * r.nextFloat()) * u, paint)
        }
    }

    /** A black hole: the hole, the bright disc round it and the ring of light bent over its top. */
    fun blackHole(cx: Float, cy: Float, rs: Float) {
        glow(cx, cy, rs * 5.5f, 0xFF9A40, 0.35f, 2f)
        strokePaint.strokeCap = Paint.Cap.BUTT
        for (pass in 0..1) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(-14f)
            canvas.scale(1f, 0.26f)
            if (pass == 1) canvas.clipRect(-rs * 6f, 0f, rs * 6f, rs * 6f)
            var rr = rs * 1.45f
            while (rr < rs * 4.4f) {
                val t = (rr - rs * 1.45f) / (rs * 2.95f)
                val col = artMix(artMix(0xFFF2D0, 0xFFA040, t * 1.6f), 0xC83A10, (t - 0.45f) * 1.8f)
                val a = (1f - t).pow(0.8f)
                strokePaint.strokeWidth = rs * 0.075f
                rect.set(-rr, -rr, rr, rr)
                strokePaint.color = argb(col, 0.95f * a)
                canvas.drawArc(rect, 90f, 180f, false, strokePaint)
                strokePaint.color = argb(col, 0.45f * a)
                canvas.drawArc(rect, 270f, 180f, false, strokePaint)
                rr += rs * 0.05f
            }
            canvas.restore()
            if (pass == 0) {
                paint.color = 0xFF000000.toInt()
                canvas.drawCircle(cx, cy, rs, paint)
                for (k in 0..5) {
                    strokePaint.strokeWidth = rs * (0.05f + 0.045f * k)
                    strokePaint.color = argb(0xFFD79A, 0.55f / (1f + k * 0.9f))
                    canvas.drawCircle(cx, cy, rs * 1.12f, strokePaint)
                }
            }
        }
        strokePaint.strokeCap = Paint.Cap.ROUND
    }

    /** A comet: a bright head at ([hx],[hy]) and two tails flowing away from it, to ([tx],[ty]). */
    fun comet(hx: Float, hy: Float, tx: Float, ty: Float, headR: Float) {
        val dx = tx - hx
        val dy = ty - hy
        val len = hypot(dx, dy)
        val nx = -dy / len
        val ny = dx / len
        // The gas tail: straight, blue, thin.
        path.reset()
        path.moveTo(hx + nx * headR * 0.5f, hy + ny * headR * 0.5f)
        path.lineTo(hx + dx + nx * headR * 3.2f, hy + dy + ny * headR * 3.2f)
        path.lineTo(hx + dx - nx * headR * 3.2f, hy + dy - ny * headR * 3.2f)
        path.lineTo(hx - nx * headR * 0.5f, hy - ny * headR * 0.5f)
        path.close()
        paint.shader = LinearGradient(hx, hy, tx, ty, argb(0x9CD0FF, 0.75f), argb(0x9CD0FF, 0f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        // The dust tail: broader, curved, warm.
        path.reset()
        path.moveTo(hx + nx * headR * 0.8f, hy + ny * headR * 0.8f)
        path.quadTo(hx + dx * 0.55f + nx * len * 0.16f, hy + dy * 0.55f + ny * len * 0.16f, hx + dx * 0.9f + nx * len * 0.38f, hy + dy * 0.9f + ny * len * 0.38f)
        path.lineTo(hx + dx * 0.75f + nx * len * 0.5f, hy + dy * 0.75f + ny * len * 0.5f)
        path.quadTo(hx + dx * 0.35f + nx * len * 0.1f, hy + dy * 0.35f + ny * len * 0.1f, hx - nx * headR * 0.2f, hy - ny * headR * 0.2f)
        path.close()
        paint.shader = LinearGradient(hx, hy, tx, ty, argb(0xFFE2B0, 0.55f), argb(0xFFE2B0, 0f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        glow(hx, hy, headR * 7f, 0xBFE0FF, 0.55f, 2f)
        paint.color = argb(0xFFFFFF, 0.98f)
        canvas.drawCircle(hx, hy, headR, paint)
    }

    /** The Sun over the horizon: a disc with a corona. */
    fun sun(cx: Float, cy: Float, r: Float, color: Int, glowColor: Int, glowAlpha: Float = 0.6f) {
        glow(cx, cy, r * 6f, glowColor, glowAlpha, 1.8f)
        glow(cx, cy, r * 2.4f, color, 0.55f, 1.4f)
        paint.shader = RadialGradient(cx, cy, r, intArrayOf(opaque(0xFFFFFF), opaque(color)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The Moon and the planets
    // -----------------------------------------------------------------------------------------------------------------

    /** The Moon at ([cx],[cy]) with the radius [r]; [halo] adds the ring of light that ice crystals make round it. */
    fun moon(
        cx: Float,
        cy: Float,
        r: Float,
        look: MoonLook = MoonLook(),
        glowColor: Int = 0xBFD0FF,
        glowAlpha: Float = 0.5f,
        glowReach: Float = 3.2f,
        halo: Boolean = false,
    ) {
        val lit = (1f + cos(look.phase * PI.toFloat() / 180f)) / 2f
        if (glowAlpha > 0f) {
            glow(cx, cy, r * glowReach, glowColor, glowAlpha * (0.35f + 0.65f * lit), 2.2f)
            glow(cx, cy, r * 1.5f, glowColor, glowAlpha * 0.55f * (0.3f + 0.7f * lit), 1.6f)
        }
        if (halo) {
            val reach = 3.1f
            val cols = intArrayOf(
                argb(0xDCE6FF, 0f), argb(0xDCE6FF, 0f), argb(0xDCE6FF, 0.10f), argb(0xDCE6FF, 0.22f),
                argb(0xDCE6FF, 0.10f), argb(0xDCE6FF, 0.04f), argb(0xDCE6FF, 0f),
            )
            val pos = floatArrayOf(0f, 2.0f / reach, 2.22f / reach, 2.35f / reach, 2.5f / reach, 2.8f / reach, 1f)
            paint.shader = RadialGradient(cx, cy, r * reach, cols, pos, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * reach, paint)
            paint.shader = null
        }
        val d = (r * 2f).toInt().coerceAtLeast(8)
        val shrink = ceil(d / 900f).toInt().coerceAtLeast(1)
        val bmp = moonDisc((d / shrink).coerceAtLeast(8), look)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        bmp.recycle()
    }

    /** A picture of the Moon's ground, close up, over the whole picture (made at a lower resolution, then enlarged). */
    fun moonSurface(look: MoonLook) {
        val shrink = ceil(max(w, h) / 800f).toInt().coerceAtLeast(1)
        val side = max(8, max(w, h) / shrink)
        val bmp = moonDisc(side, look)
        val half = side * shrink / 2f
        rect.set(fw / 2f - half, fh / 2f - half, fw / 2f + half, fh / 2f + half)
        canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        bmp.recycle()
    }

    /** A Moon drawn flat, as a symbol: a disc of [color]; [carve] (the sky's colour) bites a crescent out of it. */
    fun flatMoon(cx: Float, cy: Float, r: Float, color: Int, carve: Int = 0, shift: Float = 0.45f) {
        glow(cx, cy, r * 2.6f, color, 0.22f, 2f)
        paint.color = opaque(color)
        canvas.drawCircle(cx, cy, r, paint)
        if (carve != 0) {
            canvas.save()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.clipPath(path)
            paint.color = opaque(carve)
            canvas.drawCircle(cx + r * shift * 1.4f, cy - r * shift * 0.3f, r * 0.92f, paint)
            canvas.restore()
        }
    }

    /** Rays of light falling from ([cx],[cy]) down through the trees. */
    fun lightShafts(cx: Float, cy: Float, count: Int, length: Float, color: Int, alpha: Float, seedOffset: Int = 17) {
        val r = Random(seed + seedOffset)
        for (i in 0 until count) {
            val ang = (-0.55f + 1.1f * r.nextFloat()) // radians from straight down
            val spread = 0.03f + 0.05f * r.nextFloat()
            val x1 = cx + sin(ang - spread) * length
            val y1 = cy + cos(ang - spread) * length
            val x2 = cx + sin(ang + spread) * length
            val y2 = cy + cos(ang + spread) * length
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(x1, y1)
            path.lineTo(x2, y2)
            path.close()
            paint.shader = LinearGradient(cx, cy, cx + sin(ang) * length, cy + cos(ang) * length, argb(color, alpha * (0.5f + 0.5f * r.nextFloat())), argb(color, 0f), Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        paint.shader = null
    }

    /** A planet; Saturn gets its rings. The light comes from the side [phase] says. */
    fun planet(kind: Planet, cx: Float, cy: Float, r: Float, phase: Float = 35f, tilt: Float = 15f, glowColor: Int = 0, glowAlpha: Float = 0f) {
        if (glowAlpha > 0f) glow(cx, cy, r * 1.5f, glowColor, glowAlpha, 2f)
        if (kind == Planet.Saturn) rings(cx, cy, r, front = false)
        val d = (r * 2f).toInt().coerceAtLeast(8)
        val shrink = ceil(d / 900f).toInt().coerceAtLeast(1)
        val bmp = planetDisc(kind, (d / shrink).coerceAtLeast(8), phase, tilt)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        bmp.recycle()
        if (kind == Planet.Saturn) rings(cx, cy, r, front = true)
    }

    /** Saturn's rings: [front] is the half in front of the planet (drawn after it), else the half behind (drawn before). */
    private fun rings(cx: Float, cy: Float, radius: Float, front: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-17f)
        canvas.scale(1f, 0.30f)
        val big = radius * 4f
        if (front) canvas.clipRect(-big, 0f, big, big) else canvas.clipRect(-big, -big, big, 0f)
        strokePaint.strokeCap = Paint.Cap.BUTT
        strokePaint.strokeWidth = radius * 0.026f
        val rnd = Random(2)
        var rr = radius * 1.27f
        var i = 0
        while (rr < radius * 2.25f) {
            val gap = rr > radius * 1.76f && rr < radius * 1.83f
            var a = if (gap) 0.06f else 0.30f + 0.5f * rnd.nextFloat()
            if (rr < radius * 1.5f) a *= 0.6f
            if (rr > radius * 1.83f) a *= 0.85f
            strokePaint.color = argb(if (i % 2 == 0) 0xE2CEA0 else 0xCDB88C, a)
            canvas.drawCircle(0f, 0f, rr, strokePaint)
            rr += radius * 0.022f
            i++
        }
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.restore()
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Abstract
    // -----------------------------------------------------------------------------------------------------------------

    /** Layers of waves, from [baseY] down, each a colour of [colors] at [alpha]. */
    fun waves(count: Int, baseY: Float, spacing: Float, amp: Float, colors: IntArray, alpha: Float, phase0: Float = 0f) {
        val step = max(3f, fw / 160f)
        for (i in 0 until count) {
            val y0 = baseY + spacing * i
            val f1 = 2.4f + 0.7f * i
            val f2 = 5.6f + 1.1f * i
            val p1 = phase0 + i * 1.3f
            val p2 = phase0 * 1.7f + i * 2.1f
            path.reset()
            path.moveTo(0f, fh)
            var x = 0f
            while (x <= fw + step) {
                val t = x / fw
                path.lineTo(x, y0 - amp * sin(t * f1 * 2f + p1) - amp * 0.45f * sin(t * f2 * 2f + p2))
                x += step
            }
            path.lineTo(fw + step, fh)
            path.close()
            val c = colors[i % colors.size]
            paint.shader = LinearGradient(0f, y0 - amp * 1.5f, 0f, y0 + spacing * 3f, argb(c, alpha), argb(c, alpha * 0.25f), Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        paint.shader = null
    }

    /** Concentric rings round ([cx],[cy]). */
    fun ringsArt(cx: Float, cy: Float, count: Int, first: Float, spacing: Float, color: Int, alpha: Float) {
        for (i in 0 until count) {
            val r = first + spacing * i
            val a = alpha * (1f - i / count.toFloat()).pow(0.7f)
            strokePaint.strokeWidth = max(1f, 2.5f * u * (1f + 0.35f * (i % 3)))
            strokePaint.color = argb(color, a * 0.25f)
            canvas.drawCircle(cx, cy, r, strokePaint)
            strokePaint.strokeWidth = max(1f, 1.2f * u)
            strokePaint.color = argb(color, a)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }
    }

    /** Facets: a grid of triangles with shifted corners, colours running from [c1] to [c2] with a little shimmer. */
    fun crystal(c1: Int, c2: Int, cols: Int, rows: Int, seedOffset: Int = 4) {
        val r = Random(seed + seedOffset)
        val px = Array(rows + 1) { FloatArray(cols + 1) }
        val py = Array(rows + 1) { FloatArray(cols + 1) }
        for (j in 0..rows) {
            for (i in 0..cols) {
                val jx = if (i == 0 || i == cols) 0f else (r.nextFloat() - 0.5f) * 0.8f
                val jy = if (j == 0 || j == rows) 0f else (r.nextFloat() - 0.5f) * 0.8f
                px[j][i] = (i + jx) / cols * fw
                py[j][i] = (j + jy) / rows * fh
            }
        }
        paint.isAntiAlias = false
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = 1f
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                for (k in 0..1) {
                    path.reset()
                    if (k == 0) {
                        path.moveTo(px[j][i], py[j][i])
                        path.lineTo(px[j][i + 1], py[j][i + 1])
                        path.lineTo(px[j + 1][i], py[j + 1][i])
                    } else {
                        path.moveTo(px[j][i + 1], py[j][i + 1])
                        path.lineTo(px[j + 1][i + 1], py[j + 1][i + 1])
                        path.lineTo(px[j + 1][i], py[j + 1][i])
                    }
                    path.close()
                    val cxm = (px[j][i] + px[j + 1][i + 1]) / 2f
                    val cym = (py[j][i] + py[j + 1][i + 1]) / 2f
                    val t = (cxm / fw * 0.35f + cym / fh * 0.65f).coerceIn(0f, 1f)
                    val shade = 0.78f + 0.40f * r.nextFloat()
                    val c = artMix(c1, c2, t)
                    paint.color = opaque(
                        artRgb(((c shr 16) and 255) / 255f * shade, ((c shr 8) and 255) / 255f * shade, (c and 255) / 255f * shade),
                    )
                    canvas.drawPath(path, paint)
                }
            }
        }
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
    }

    /** Flowing lines: [count] curves, colours running from [c1] to [c2]. */
    fun flow(count: Int, baseY: Float, spread: Float, amp: Float, c1: Int, c2: Int, alpha: Float) {
        val step = max(4f, fw / 120f)
        strokePaint.strokeWidth = max(1f, 1.6f * u)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            path.reset()
            var x = 0f
            val y0 = baseY + (t - 0.5f) * spread
            var first = true
            while (x <= fw + step) {
                val q = x / fw
                val y = y0 + amp * sin(q * 5.2f + t * 3.4f) + amp * 0.6f * sin(q * 9.7f - t * 5f) * (0.4f + t)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            strokePaint.color = argb(artMix(c1, c2, t), alpha * (0.35f + 0.65f * sin(t * PI.toFloat())))
            canvas.drawPath(path, strokePaint)
        }
    }

    /** Soft blobs of colour: (x, y, radius) as fractions of the width/height/width. */
    fun orbs(items: List<Orb>) {
        for (o in items) glow(o.x * fw, o.y * fh, o.r * fw, o.color, o.alpha, 1.25f)
    }

    /** Bubbles of clear glass, clustered in the lower part and rising. */
    fun bubbles(count: Int, minR: Float, maxR: Float, seedOffset: Int = 9) {
        val r = Random(seed + seedOffset)
        for (i in 0 until count) {
            val rad = fw * (minR + (maxR - minR) * r.nextFloat().pow(2.2f))
            val x = r.nextFloat() * fw
            val y = fh * (0.05f + 0.95f * r.nextFloat().pow(0.8f))
            paint.shader = RadialGradient(
                x, y, rad,
                intArrayOf(argb(0xFFFFFF, 0.02f), argb(0xBFE4FF, 0.06f), argb(0xFFFFFF, 0.34f)),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, rad, paint)
            paint.shader = null
            strokePaint.strokeWidth = max(1f, 1.4f * u)
            strokePaint.color = argb(0xFFFFFF, 0.5f)
            canvas.drawCircle(x, y, rad, strokePaint)
            // The gleam.
            paint.color = argb(0xFFFFFF, 0.55f)
            rect.set(x - rad * 0.55f, y - rad * 0.62f, x - rad * 0.12f, y - rad * 0.30f)
            canvas.save()
            canvas.rotate(-35f, x - rad * 0.33f, y - rad * 0.46f)
            canvas.drawOval(rect, paint)
            canvas.restore()
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ZENITH
    // -----------------------------------------------------------------------------------------------------------------

    /** The Z of ZENITH, big, in metal with a green light along its edge. */
    fun zMark(cx: Float, cy: Float, size: Float, glowColor: Int = 0x39E88A) {
        val s = size / 2f
        path.reset()
        path.moveTo(cx - 0.8f * s, cy - 1.0f * s)
        path.lineTo(cx + 0.8f * s, cy - 1.0f * s)
        path.lineTo(cx + 0.8f * s, cy - 0.66f * s)
        path.lineTo(cx - 0.22f * s, cy + 0.66f * s)
        path.lineTo(cx + 0.8f * s, cy + 0.66f * s)
        path.lineTo(cx + 0.8f * s, cy + 1.0f * s)
        path.lineTo(cx - 0.8f * s, cy + 1.0f * s)
        path.lineTo(cx - 0.8f * s, cy + 0.66f * s)
        path.lineTo(cx + 0.22f * s, cy - 0.66f * s)
        path.lineTo(cx - 0.8f * s, cy - 0.66f * s)
        path.close()
        glow(cx, cy, size * 1.1f, glowColor, 0.30f, 2f)
        strokePaint.strokeJoin = Paint.Join.ROUND
        for (k in 6 downTo 1) {
            strokePaint.strokeWidth = size * 0.012f * k * k * 0.5f + 2f * u
            strokePaint.color = argb(glowColor, 0.05f + 0.02f * (6 - k))
            canvas.drawPath(path, strokePaint)
        }
        paint.shader = LinearGradient(
            cx - s, cy - s, cx + s, cy + s,
            intArrayOf(opaque(0xE8ECF0), opaque(0x8A9098), opaque(0x3A3F46), opaque(0x9BA2AA)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        strokePaint.strokeWidth = max(1.5f, 3f * u)
        strokePaint.color = argb(glowColor, 0.95f)
        canvas.drawPath(path, strokePaint)
    }

    /** Some words, centred at ([cx],[y]). */
    fun text(value: String, cx: Float, y: Float, size: Float, color: Int, alpha: Float = 0.9f, spacing: Float = 0.08f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        p.letterSpacing = spacing
        p.color = argb(color, alpha)
        canvas.drawText(value, cx, y, p)
    }

    /** Darkens the edges a little, so the middle stands out. */
    fun vignette(strength: Float = 0.45f) {
        paint.shader = RadialGradient(
            fw / 2f, fh / 2f, hypot(fw, fh) * 0.62f,
            intArrayOf(argb(0x000000, 0f), argb(0x000000, 0f), argb(0x000000, strength)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, fw, fh, paint)
        paint.shader = null
    }

    /** A rotating ring of light, as the SweepGradient makes it (for the accretion of a disc, a halo) – centred, [r] wide. */
    fun sweepRing(cx: Float, cy: Float, r: Float, width: Float, c1: Int, c2: Int, alpha: Float) {
        strokePaint.shader = SweepGradient(cx, cy, intArrayOf(argb(c1, alpha), argb(c2, alpha), argb(c1, alpha * 0.2f), argb(c1, alpha)), null)
        strokePaint.strokeWidth = width
        canvas.drawCircle(cx, cy, r, strokePaint)
        strokePaint.shader = null
    }
}

/** One soft blob of colour for [ArtCanvas.orbs]: its middle ([x],[y]) and radius [r] as fractions of the width/height/width. */
internal class Orb(val x: Float, val y: Float, val r: Float, val color: Int, val alpha: Float)
