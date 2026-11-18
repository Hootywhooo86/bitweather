package com.hootywhooo86.bitweather.pixel

import kotlin.math.floor
import kotlin.math.sin

/** A small ARGB pixel canvas plus the pixel-art helpers (ramps, dithering, selective outlines) the art is built from. */
class Raster(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    operator fun get(x: Int, y: Int) = if (inside(x, y)) px[y * w + x] else 0
    operator fun set(x: Int, y: Int, c: Int) { if (inside(x, y)) px[y * w + x] = c }

    /**
     * Fill a shape (mask) with a per-pixel colour, then outline it. [outline] gets the neighbouring fill colour,
     * the side the shape is on (0 below, 1 left, 2 above, 3 right) and the pixel position; null leaves the pixel as is.
     */
    fun paint(mask: BooleanArray, fill: (Int, Int) -> Int, outline: ((fill: Int, side: Int, x: Int, y: Int) -> Int?)? = null) {
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) px[y * w + x] = fill(x, y)
        if (outline == null) return
        val edge = ArrayList<IntArray>()
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x]) continue
            // the neighbouring fill pixel decides the outline shade (selective outlining)
            var n = -1; var dir = 0
            if (x > 0 && mask[y * w + x - 1]) { n = (y * w + x - 1); dir = 1 }
            else if (x < w - 1 && mask[y * w + x + 1]) { n = (y * w + x + 1); dir = 3 }
            else if (y > 0 && mask[(y - 1) * w + x]) { n = ((y - 1) * w + x); dir = 2 }
            else if (y < h - 1 && mask[(y + 1) * w + x]) { n = ((y + 1) * w + x); dir = 0 }
            if (n >= 0) edge += intArrayOf(x, y, px[n], dir)
        }
        for (e in edge) outline(e[2], e[3], e[0], e[1])?.let { px[e[1] * w + e[0]] = it }
    }

    fun mask(test: (Int, Int) -> Boolean) = BooleanArray(w * h) { test(it % w, it / w) }
}

object Px {
    fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun hex(h: Long) = h.toInt()
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF

    fun mix(a: Int, b: Int, t: Float) = argb(
        (r(a) + (r(b) - r(a)) * t).toInt(), (g(a) + (g(b) - g(a)) * t).toInt(), (b(a) + (b(b) - b(a)) * t).toInt(),
    )

    /** Darker, cooler (hue-shifted toward blue/purple) version of a colour — used for shadows and outlines. */
    fun shadow(c: Int, k: Float = 0.55f) = argb((r(c) * k).toInt(), (g(c) * (k - 0.03f)).toInt(), (b(c) * (k + 0.12f) + 22).toInt())

    /** Night tint for landscape colours. */
    fun night(c: Int) = argb((r(c) * 0.32f).toInt() + 6, (g(c) * 0.36f).toInt() + 8, (b(c) * 0.55f).toInt() + 26)

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Ordered-dither threshold in 0..1 for a pixel (4x4 Bayer matrix). */
    fun bayer(x: Int, y: Int) = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    /**
     * Pick from a colour ramp by t in 0..1. Between tones, ordered dithering is used only in a band of
     * width [dither] (0 = hard cel-shaded bands, 1 = dithered all the way), as pixel artists do.
     */
    fun ramp(ramp: IntArray, t: Float, x: Int, y: Int, dither: Float = 0.4f): Int {
        val f = (t.coerceIn(0f, 0.9999f)) * (ramp.size - 1)
        val i = floor(f).toInt(); val frac = f - i
        if (i >= ramp.size - 1) return ramp.last()
        val sharp = if (dither <= 0f) (if (frac >= 0.5f) 1f else 0f) else ((frac - 0.5f) / dither + 0.5f).coerceIn(0f, 1f)
        return if (sharp > bayer(x, y)) ramp[i + 1] else ramp[i]
    }

    /** Hard-banded ramp (cel shading, no dither). */
    fun band(ramp: IntArray, t: Float) = ramp[(t.coerceIn(0f, 0.9999f) * ramp.size).toInt()]

    fun rnd(i: Int, salt: Int): Float {
        val v = sin(i * 12.9898 + salt * 78.233) * 43758.5453
        return (v - floor(v)).toFloat()
    }

    fun hexes(vararg h: Long) = IntArray(h.size) { h[it].toInt() }
}
