package dev.hearth.launcher.wallpaper

import kotlin.math.floor

/*
 * ZENITH 21.5 – the wallpaper gallery's numbers: noise in one and three dimensions (the same hash everywhere, so a
 * piece looks the same on every phone), smooth steps and colours. Everything in the gallery is drawn from these,
 * no picture files.
 */

internal fun artHash3(ix: Int, iy: Int, iz: Int, seed: Int): Float {
    var h = ix * 374761393 + iy * 668265263 + iz * 1274126177 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

/** Smooth value noise, 0 … 1. */
internal fun artNoise3(x: Float, y: Float, z: Float, seed: Int): Float {
    val xf = floor(x)
    val yf = floor(y)
    val zf = floor(z)
    val fx = x - xf
    val fy = y - yf
    val fz = z - zf
    val sx = fx * fx * (3f - 2f * fx)
    val sy = fy * fy * (3f - 2f * fy)
    val sz = fz * fz * (3f - 2f * fz)
    val x0 = xf.toInt()
    val y0 = yf.toInt()
    val z0 = zf.toInt()
    val c000 = artHash3(x0, y0, z0, seed)
    val c100 = artHash3(x0 + 1, y0, z0, seed)
    val c010 = artHash3(x0, y0 + 1, z0, seed)
    val c110 = artHash3(x0 + 1, y0 + 1, z0, seed)
    val c001 = artHash3(x0, y0, z0 + 1, seed)
    val c101 = artHash3(x0 + 1, y0, z0 + 1, seed)
    val c011 = artHash3(x0, y0 + 1, z0 + 1, seed)
    val c111 = artHash3(x0 + 1, y0 + 1, z0 + 1, seed)
    val a = c000 + (c100 - c000) * sx
    val b = c010 + (c110 - c010) * sx
    val c = c001 + (c101 - c001) * sx
    val d = c011 + (c111 - c011) * sx
    val e = a + (b - a) * sy
    val f = c + (d - c) * sy
    return e + (f - e) * sz
}

/** Layers of noise, each twice as fine and half as strong: 0 … 1. */
internal fun artFbm3(x: Float, y: Float, z: Float, octaves: Int, seed: Int): Float {
    var sum = 0f
    var amp = 0.5f
    var norm = 0f
    var f = 1f
    for (o in 0 until octaves) {
        sum += amp * artNoise3(x * f, y * f, z * f, seed + o * 17)
        norm += amp
        amp *= 0.5f
        f *= 2f
    }
    return sum / norm
}

internal fun artFbm1(x: Float, octaves: Int, seed: Int): Float = artFbm3(x, 0f, 0f, octaves, seed)

internal fun artNoise1(x: Float, seed: Int): Float = artNoise3(x, 0f, 0f, seed)

/** 0 below [a], 1 above [b], smooth between (a may be above b: then it runs the other way). */
internal fun artSmooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A colour (0xRRGGBB) from 0 … 1 parts. */
internal fun artRgb(r: Float, g: Float, b: Float): Int =
    ((r.coerceIn(0f, 1f) * 255f).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255f).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255f).toInt()

/** Between two colours (0xRRGGBB). */
internal fun artMix(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val r = ar + (((b shr 16) and 0xFF) - ar) * k
    val g = ag + (((b shr 8) and 0xFF) - ag) * k
    val bl = ab + ((b and 0xFF) - ab) * k
    return (r.toInt() shl 16) or (g.toInt() shl 8) or bl.toInt()
}
