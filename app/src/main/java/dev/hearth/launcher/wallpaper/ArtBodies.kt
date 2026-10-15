package dev.hearth.launcher.wallpaper

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * ZENITH 21.5 – the heavenly bodies of the wallpaper gallery: the Moon in every phase (craters with relief, seas, rays,
 * earthshine, an eclipse) and the planets, each a sphere lit from one side, made pixel by pixel from noise.
 */

/** How one Moon looks. [phase] 0 = full, 90 = a quarter, 180 = new; negative: waning (lit on the left). */
internal class MoonLook(
    val phase: Float = 0f,
    val tilt: Float = 0f,
    /** The colour of its light (0xRRGGBB). */
    val tint: Int = 0xFFFCF5,
    /** How much the dark side shows in the Earth's light (0 … 0.3). */
    val earthshine: Float = 0f,
    /** Another number: another Moon (other craters, other seas). */
    val seed: Int = 3,
    /** 0 = none, 1 = total: the Earth's shadow, red. */
    val eclipse: Float = 0f,
    val eclipseX: Float = 1f,
    val eclipseY: Float = 0f,
    val relief: Float = 1f,
    /** Closer in: 1 = the whole disc, 0.3 = a small piece of its middle (then [offsetX]/[offsetY] say which one). */
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** How many craters, and how big (a close-up wants more and larger ones). */
    val craters: Int = 360,
    val craterSize: Float = 1f,
)

private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).toInt()

/** The Moon as a [size] × [size] picture with a clear background. */
internal fun moonDisc(size: Int, look: MoonLook): Bitmap {
    val s = size
    val n = s * s
    val height = FloatArray(n)
    val count = look.craters
    val zoom = look.zoom
    val ox = look.offsetX
    val oy = look.offsetY
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val vz = FloatArray(count)
    val vr = FloatArray(count)
    val rnd = Random(look.seed * 7919 + 13)
    for (i in 0 until count) {
        val z = rnd.nextFloat() * 2f - 1f
        val phi = rnd.nextFloat() * 6.2831855f
        val ring = sqrt(max(1f - z * z, 0f))
        vx[i] = ring * cos(phi)
        vy[i] = ring * sin(phi)
        vz[i] = z
        vr[i] = (0.007f + 0.15f * rnd.nextFloat().pow(4.4f)) * look.craterSize
    }

    // The craters' heights: a bowl, a raised rim, a little ejecta round it.
    for (i in 0 until count) {
        if (vz[i] < -0.3f) continue
        val r = vr[i]
        val ext = min(1.9f * r / max(vz[i], 0.2f), 2f)
        val x0 = max(0, (((vx[i] - ext - ox) / zoom + 1f) / 2f * s).toInt())
        val x1 = min(s - 1, (((vx[i] + ext - ox) / zoom + 1f) / 2f * s).toInt() + 1)
        val y0 = max(0, ((1f - (vy[i] + ext - oy) / zoom) / 2f * s).toInt())
        val y1 = min(s - 1, ((1f - (vy[i] - ext - oy) / zoom) / 2f * s).toInt() + 1)
        for (y in y0..y1) {
            val ny = (1f - (y + 0.5f) / s * 2f) * zoom + oy
            for (x in x0..x1) {
                val nx = ((x + 0.5f) / s * 2f - 1f) * zoom + ox
                val r2 = nx * nx + ny * ny
                if (r2 >= 1f) continue
                val nz = sqrt(1f - r2)
                val dot = nx * vx[i] + ny * vy[i] + nz * vz[i]
                val t = sqrt(max(2f - 2f * dot, 0f)) / r
                if (t >= 1.9f) continue
                val q = t / 0.82f
                val bowl = if (t < 0.82f) -(1f - q * q) else 0f
                val dd = (t - 0.95f) / 0.16f
                val rim = 0.20f * exp(-dd * dd)
                val ejecta = if (t >= 1.1f) {
                    val e = 1f - (t - 1.1f) / 0.8f
                    0.05f * e * e
                } else {
                    0f
                }
                height[y * s + x] += 0.30f * r * (bowl + rim + ejecta)
            }
        }
    }

    // The bright rays of the few youngest big craters.
    val rays = FloatArray(n)
    val order = (0 until count).sortedByDescending { vr[it] }
    val rayRnd = Random(look.seed + 5)
    var done = 0
    for (i in order) {
        if (vz[i] < 0.1f) continue
        val r = max(vr[i], 0.03f) * 0.6f
        val len = sqrt(vx[i] * vx[i] + vy[i] * vy[i]) + 1e-6f
        val exx = vy[i] / len
        val exy = -vx[i] / len
        val eyx = vz[i] * vx[i] / len
        val eyy = vz[i] * vy[i] / len
        val eyz = -(vx[i] * vx[i] + vy[i] * vy[i]) / len
        val ph = rayRnd.nextFloat() * 6.28f
        val ext = min(8f * r / max(vz[i], 0.2f), 2f)
        val x0 = max(0, (((vx[i] - ext - ox) / zoom + 1f) / 2f * s).toInt())
        val x1 = min(s - 1, (((vx[i] + ext - ox) / zoom + 1f) / 2f * s).toInt() + 1)
        val y0 = max(0, ((1f - (vy[i] + ext - oy) / zoom) / 2f * s).toInt())
        val y1 = min(s - 1, ((1f - (vy[i] - ext - oy) / zoom) / 2f * s).toInt() + 1)
        for (y in y0..y1) {
            val ny = (1f - (y + 0.5f) / s * 2f) * zoom + oy
            for (x in x0..x1) {
                val nx = ((x + 0.5f) / s * 2f - 1f) * zoom + ox
                val r2 = nx * nx + ny * ny
                if (r2 >= 1f) continue
                val nz = sqrt(1f - r2)
                val dot = nx * vx[i] + ny * vy[i] + nz * vz[i]
                val t = sqrt(max(2f - 2f * dot, 0f)) / r
                if (t > 8f) continue
                val tx = nx - dot * vx[i]
                val ty = ny - dot * vy[i]
                val tz = nz - dot * vz[i]
                val ang = atan2(tx * eyx + ty * eyy + tz * eyz, tx * exx + ty * exy)
                val ray = (0.5f + 0.5f * cos(ang * 9f + ph + 3f * cos(ang * 4f))).pow(3)
                val fall = if (t > 1f) (1f - (t - 1f) / 7f).coerceIn(0f, 1f).pow(1.8f) else 0f
                rays[y * s + x] += 0.055f * ray * fall + 0.05f * (1f - t).coerceIn(0f, 1f)
            }
        }
        done++
        if (done >= 4) break
    }

    val out = IntArray(n)
    val a = look.phase * PI.toFloat() / 180f
    val tl = look.tilt * PI.toFloat() / 180f
    val lxv = sin(a)
    val lx = lxv * cos(tl)
    val ly = lxv * sin(tl)
    val lz = cos(a)
    val step = 2f * zoom / s
    val tr = ((look.tint shr 16) and 255) / 255f
    val tg = ((look.tint shr 8) and 255) / 255f
    val tb = (look.tint and 255) / 255f
    val seed = look.seed
    val seedF = seed * 3.1f
    val k = 0.9f * look.relief
    for (y in 0 until s) {
        val ny = (1f - (y + 0.5f) / s * 2f) * zoom + oy
        for (x in 0 until s) {
            val i = y * s + x
            val nx = ((x + 0.5f) / s * 2f - 1f) * zoom + ox
            val r2 = nx * nx + ny * ny
            if (r2 >= 1f) continue
            val nz = sqrt(1f - r2)
            val alpha = ((1f - sqrt(r2)) * s * 0.5f / zoom).coerceIn(0f, 1f)

            // The ground's brightness: highlands, dark seas, fine grain.
            val fx = nx + 5.1f + seedF
            val fy = ny + 2.3f
            val fz = nz + 7.7f
            val base = artFbm3(fx * 2.3f, fy * 2.3f, fz * 2.3f, 4, seed)
            val maria = artFbm3(fx * 1.2f, fy * 1.2f, fz * 1.2f, 3, seed + 5)
            val seas = artSmooth(0.52f, 0.62f, maria)
            val grain = artFbm3(fx * 34f, fy * 34f, fz * 34f, 2, seed + 9)
            var alb = 0.80f + 0.26f * (base - 0.5f) + 0.10f * (grain - 0.5f)
            alb *= 1f - 0.40f * seas
            alb += rays[i] + (height[i] / 0.02f).coerceIn(-1f, 1f) * 0.045f

            // The relief tilts the surface a little; the light falls on that.
            val gx = if (x in 1 until s - 1) (height[i + 1] - height[i - 1]) / (2f * step) else 0f
            val gy = if (y in 1 until s - 1) -(height[i + s] - height[i - s]) / (2f * step) else 0f
            var px = nx - k * gx * nz
            var py = ny - k * gy * nz
            var pz = nz
            val ln = sqrt(px * px + py * py + pz * pz) + 1e-6f
            px /= ln
            py /= ln
            pz /= ln
            val dnl = px * lx + py * ly + pz * lz
            val dnlGeo = nx * lx + ny * ly + nz * lz
            val ls = if (dnl > 0f) dnl / (dnl + max(nz, 0.06f)) * 2f else 0f
            val term = artSmooth(0f, 0.14f, dnlGeo)
            val bright = min(ls * term, 1.3f)
            val lum = alb * (bright + look.earthshine * (1f - term) * (0.75f + 0.25f * nz))
            var r = lum * tr
            var g = lum * tg
            var b = lum * tb
            if (look.earthshine > 0f) {
                val sh = (1f - term) * look.earthshine
                b += alb * sh * 0.25f
                g += alb * sh * 0.06f
            }
            if (look.eclipse > 0f) {
                val off = (1f - look.eclipse) * 2.6f
                val ddx = nx - look.eclipseX * off
                val ddy = ny - look.eclipseY * off
                val d = sqrt(ddx * ddx + ddy * ddy)
                val umbra = artSmooth(2.02f, 1.45f, d)
                var lum2 = alb * (0.20f + 0.34f * (1f - artSmooth(0f, 1.9f, d)))
                lum2 *= 0.85f + 0.3f * ls.coerceIn(0f, 1f)
                r = r * (1f - umbra) + lum2 * 1.6f * umbra
                g = g * (1f - umbra) + lum2 * 0.52f * umbra
                b = b * (1f - umbra) + lum2 * 0.26f * umbra
            }
            out[i] = (to8(alpha) shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
        }
    }
    val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(out, 0, s, 0, 0, s, s)
    return bitmap
}

// ---------------------------------------------------------------------------------------------------------------------
// The planets
// ---------------------------------------------------------------------------------------------------------------------

internal enum class Planet { Jupiter, Saturn, Mars, Venus, Neptune, Earth }

/** A sphere lit from one side, [size] × [size]; [colorAt] says the colour (0xRRGGBB) of the surface at a point of the unit sphere. */
private inline fun sphereDisc(
    size: Int,
    phase: Float,
    tilt: Float,
    ambient: Float,
    rimK: Float,
    colorAt: (Float, Float, Float) -> Int,
): Bitmap {
    val s = size
    val out = IntArray(s * s)
    val a = phase * PI.toFloat() / 180f
    val t = tilt * PI.toFloat() / 180f
    val lx = sin(a) * cos(t)
    val ly = sin(a) * sin(t)
    val lz = cos(a)
    for (y in 0 until s) {
        val ny = 1f - (y + 0.5f) / s * 2f
        for (x in 0 until s) {
            val nx = (x + 0.5f) / s * 2f - 1f
            val r2 = nx * nx + ny * ny
            if (r2 >= 1f) continue
            val nz = sqrt(1f - r2)
            val alpha = ((1f - sqrt(r2)) * s * 0.5f).coerceIn(0f, 1f)
            val c = colorAt(nx, ny, nz)
            var r = ((c shr 16) and 255) / 255f
            var g = ((c shr 8) and 255) / 255f
            var b = (c and 255) / 255f
            val dnl = nx * lx + ny * ly + nz * lz
            val lit = artSmooth(-0.08f, 0.40f, dnl) * (0.35f + 0.65f * sqrt(nz))
            val k = ambient + (1f - ambient) * lit
            r *= k
            g *= k
            b *= k
            if (rimK > 0f) {
                val q = (1f - nz) * (1f - nz) * (1f - nz) * artSmooth(-0.3f, 0.4f, dnl) * rimK
                r += 0.30f * q
                g += 0.50f * q
                b += 1.00f * q
            }
            out[y * s + x] = (to8(alpha) shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
        }
    }
    val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(out, 0, s, 0, 0, s, s)
    return bitmap
}

private fun jupiterColor(nx: Float, ny: Float, nz: Float): Int {
    val turb = artFbm3(nx * 3f + 1f, ny * 6f + 2f, nz * 3f + 3f, 5, 51)
    val s = sin(ny * 13f + turb * 3.6f)
    val s2 = sin(ny * 34f + turb * 5f)
    var c = if (s > 0.2f) artMix(0xEDE0C4, 0xFAF2E0, s - 0.2f) else artMix(0xB07042, 0x855233, 0.2f - s)
    c = artMix(c, 0xD6AB7D, 0.25f * (0.5f + 0.5f * s2))
    val gx = (nx + 0.25f) / 0.34f
    val gy = (ny + 0.32f) / 0.17f
    val g = gx * gx + gy * gy
    if (g < 1f) c = artMix(c, 0xBA4F33, ((1f - g) * 2.2f).coerceIn(0f, 0.95f))
    return c
}

private fun saturnColor(nx: Float, ny: Float, nz: Float): Int {
    val turb = artFbm3(nx * 3f + 4f, ny * 4f + 1f, nz * 3f + 9f, 4, 41)
    val band = 0.5f + 0.5f * sin(ny * 20f + turb * 3.2f)
    val fine = 0.5f + 0.5f * sin(ny * 70f + turb * 6f)
    var c = artMix(0xB08A52, 0xEDD6A6, band)
    c = artMix(c, 0xC7AB70, fine * 0.35f)
    val pol = abs(ny) * abs(ny) * abs(ny)
    return artMix(c, 0x787D87, pol * 0.5f)
}

private fun marsColor(nx: Float, ny: Float, nz: Float): Int {
    val n = artFbm3(nx * 3f + 2f, ny * 3f + 7f, nz * 3f + 1f, 5, 5)
    var c = artMix(0x964524, 0xE38F59, n)
    val m = artFbm3(nx * 1.5f + 8f, ny * 1.5f, nz * 1.5f, 3, 9)
    c = artMix(c, 0x5C3324, ((m - 0.54f) * 3.2f).coerceIn(0f, 0.8f))
    return artMix(c, 0xF2EBE6, artSmooth(0.88f, 0.95f, abs(ny)))
}

private fun venusColor(nx: Float, ny: Float, nz: Float): Int {
    val w = artFbm3(nx * 2f + 3f, ny * 3f + 1f, nz * 2f + 5f, 4, 71)
    val sw = artFbm3(nx * 2f + w * 1.6f, ny * 5f + w * 2.2f, nz * 2f + 9f, 5, 73)
    return artMix(0xCCA666, 0xF7E6B8, sw * 1.4f - 0.2f)
}

private fun neptuneColor(nx: Float, ny: Float, nz: Float): Int {
    val t = artFbm3(nx * 2f + 1f, ny * 6f, nz * 2f + 3f, 4, 81)
    val b = 0.5f + 0.5f * sin(ny * 9f + t * 2.4f)
    var c = artMix(0x1A389E, 0x4780EB, b * 0.8f + t * 0.3f)
    val gx = (nx + 0.2f) / 0.28f
    val gy = (ny - 0.18f) / 0.14f
    val g = gx * gx + gy * gy
    c = artMix(c, 0x0A1A57, ((1f - g) * 1.6f).coerceIn(0f, 0.7f))
    return c
}

private fun earthColor(nx: Float, ny: Float, nz: Float): Int {
    val fx = nx + 3.1f
    val fy = ny + 1.7f
    val fz = nz + 5.3f
    val e = artFbm3(fx * 1.6f, fy * 1.6f, fz * 1.6f, 5, 4)
    val land = artSmooth(0.52f, 0.56f, e)
    val sea = artRgb(0.03f + 0.08f * (1f - e), 0.13f + 0.20f * (1f - e), 0.38f + 0.28f * (1f - e))
    val h = ((e - 0.54f) / 0.3f).coerceIn(0f, 1f)
    val dry = artFbm3(fx * 3.1f, fy * 3.1f, fz * 3.1f, 3, 7)
    val green = artRgb(0.17f + 0.2f * h, 0.34f + 0.08f * h, 0.14f + 0.06f * h)
    val ground = artMix(green, 0x9E8557, artSmooth(0.52f, 0.64f, dry))
    var c = artMix(sea, ground, land)
    c = artMix(c, 0xF0F5FF, artSmooth(0.84f, 0.93f, abs(ny)))
    val clouds = artSmooth(0.52f, 0.72f, artFbm3(fx * 2.6f + 9f, fy * 3.4f, fz * 2.6f, 5, 11))
    return artMix(c, 0xFFFFFF, clouds * 0.8f)
}

/** A planet as a [size] × [size] picture with a clear background; the light comes from the side [phase] says. */
internal fun planetDisc(kind: Planet, size: Int, phase: Float = 35f, tilt: Float = 15f): Bitmap = when (kind) {
    Planet.Jupiter -> sphereDisc(size, phase, tilt, 0.06f, 0f) { x, y, z -> jupiterColor(x, y, z) }
    Planet.Saturn -> sphereDisc(size, phase, tilt, 0.06f, 0f) { x, y, z -> saturnColor(x, y, z) }
    Planet.Mars -> sphereDisc(size, phase, tilt, 0.06f, 0f) { x, y, z -> marsColor(x, y, z) }
    Planet.Venus -> sphereDisc(size, phase, tilt, 0.06f, 0f) { x, y, z -> venusColor(x, y, z) }
    Planet.Neptune -> sphereDisc(size, phase, tilt, 0.06f, 0.5f) { x, y, z -> neptuneColor(x, y, z) }
    Planet.Earth -> sphereDisc(size, phase, tilt, 0.05f, 0.8f) { x, y, z -> earthColor(x, y, z) }
}
