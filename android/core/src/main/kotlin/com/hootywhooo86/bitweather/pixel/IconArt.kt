package com.hootywhooo86.bitweather.pixel

import com.hootywhooo86.bitweather.pixel.Px.hexes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detailed 32x32 weather icons: shaded colour ramps with hue-shifted shadows, rim light, selective
 * outlines and a 4-frame animation loop. Returns ARGB pixels (0 = transparent).
 */
object IconArt {
    const val SIZE = 32
    const val FRAMES = 4

    private val SUN = hexes(0xFFFFF8D2, 0xFFFFE470, 0xFFFFC23A, 0xFFFF9A22)
    private val SUN_LINE = 0xFFB4501A.toInt()
    private val RAY = hexes(0xFFFFE36A, 0xFFFFB52E, 0xFFFF8A1E)
    private val MOON = hexes(0xFFFFFFFF, 0xFFEDE7FF, 0xFFCBC0F4, 0xFF9E92D8)
    private val MOON_LINE = 0xFF483D86.toInt()
    private val CLOUD = hexes(0xFFFFFFFF, 0xFFEEF1FA, 0xFFD3D9EC, 0xFFA9B2CE)
    private val CLOUD_DARK = hexes(0xFFC2C8DC, 0xFF9CA4BF, 0xFF7B84A4, 0xFF5D6588)
    private val DROP = hexes(0xFFDDF4FF, 0xFF84CCFF, 0xFF3A90E6)
    private val ICE = hexes(0xFFF2FFFF, 0xFFBDF2FF, 0xFF6CC8E8)
    private val FLAKE = hexes(0xFFFFFFFF, 0xFFE4F2FF, 0xFFA9CFF4)
    private val BOLT = hexes(0xFFFFFBC8, 0xFFFFE454, 0xFFFFAE12)
    private val BOLT_LINE = 0xFF6E3C00.toInt()
    private val FOG = hexes(0xFFE6E9F3, 0xFFC4C9DB, 0xFF989FB8)

    private val cache = HashMap<String, IntArray>()

    @Synchronized
    fun icon(kind: String, day: Boolean, frame: Int = 0): IntArray {
        val f = ((frame % FRAMES) + FRAMES) % FRAMES
        return cache.getOrPut("$kind$day$f") { draw(kind, day, f).px }
    }

    // ---------------------------------------------------------------- parts
    private fun disc(cx: Double, cy: Double, r: Double, x: Int, y: Int): Boolean {
        val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun sun(R: Raster, cx: Double, cy: Double, r: Double, f: Int) {
        // rays: long on the axes, short on the diagonals; they pulse every other frame
        val m = R.mask { x, y ->
            val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
            (0 until 8).any { k ->
                val a = k * PI / 4
                val ux = cos(a); val uy = sin(a)
                val along = dx * ux + dy * uy
                val perp = kotlin.math.abs(-dx * uy + dy * ux)
                val diag = k % 2 == 1
                val len = (if (diag) r * 0.42 else r * 0.62) + (if (f % 2 == 0) 1.2 else 0.0)
                val start = r + 1.8
                // tapered ray: wide at the base, narrow at the tip
                along in start..(start + len) && perp <= (if (diag) 1.0 else 1.5) * (1 - 0.55 * (along - start) / len)
            }
        }
        R.paint(m, { x, y ->
            val d = sqrt((x + 0.5 - cx).let { it * it } + (y + 0.5 - cy).let { it * it })
            Px.band(RAY, ((d - r - 1.6) / (r * 0.7)).toFloat())
        })
        val body = R.mask { x, y -> disc(cx, cy, r, x, y) }
        val lx = cx - r * 0.4; val ly = cy - r * 0.4
        R.paint(body, { x, y ->
            val d = sqrt((x + 0.5 - lx).let { it * it } + (y + 0.5 - ly).let { it * it }) / (r * 1.75)
            Px.ramp(SUN, d.toFloat(), x, y, 0.3f)
        }) { _, _, _, _ -> SUN_LINE }
        // specular glint
        R[(cx - r * 0.45).toInt(), (cy - r * 0.5).toInt()] = 0xFFFFFFFF.toInt()
    }

    private fun moon(R: Raster, cx: Double, cy: Double, r: Double) {
        val m = R.mask { x, y -> disc(cx, cy, r, x, y) && !disc(cx + r * 0.62, cy - r * 0.38, r * 0.86, x, y) }
        val lx = cx - r * 0.55; val ly = cy - r * 0.1
        R.paint(m, { x, y ->
            val d = sqrt((x + 0.5 - lx).let { it * it } + (y + 0.5 - ly).let { it * it }) / (r * 1.6)
            Px.ramp(MOON, d.toFloat(), x, y, 0.3f)
        }) { _, _, _, _ -> MOON_LINE }
        // craters
        for ((dx, dy) in listOf(-0.45 to 0.1, -0.2 to 0.55, -0.6 to -0.35)) {
            val x = (cx + dx * r).toInt(); val y = (cy + dy * r).toInt()
            if (m.getOrElse(y * R.w + x) { false }) { R[x, y] = MOON[2]; R[x + 1, y] = MOON[3] }
        }
    }

    private fun stars(R: Raster, f: Int) {
        val spots = listOf(listOf(26 to 5, 5 to 27), listOf(28 to 11, 4 to 6), listOf(26 to 5, 7 to 25), listOf(23 to 3, 28 to 15))[f]
        for ((x, y) in spots) {
            R[x, y] = 0xFFFFFFFF.toInt()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) if (R[x + dx, y + dy] == 0) R[x + dx, y + dy] = 0xFFB9C4FF.toInt()
        }
    }

    /** A puffy cloud built from overlapping circles; ox/oy/s place and scale it in the 32 grid. */
    private fun cloud(R: Raster, ox: Double, oy: Double, s: Double, dark: Boolean) {
        val circles = listOf(Triple(10.0, 19.5, 5.5), Triple(16.5, 14.5, 7.5), Triple(23.5, 18.5, 6.0), Triple(7.0, 22.0, 4.0), Triple(26.0, 22.0, 3.6))
        val bottom = oy + 25.5 * s
        val top = oy + 7.0 * s
        val m = R.mask { x, y ->
            y + 0.5 <= bottom && (circles.any { (cx, cy, r) -> disc(ox + cx * s, oy + cy * s, r * s, x, y) } ||
                (x + 0.5 >= ox + 7.0 * s && x + 0.5 <= ox + 27.0 * s && y + 0.5 >= oy + 19.0 * s))
        }
        val ramp = if (dark) CLOUD_DARK else CLOUD
        R.paint(m, { x, y ->
            val rim = y > 0 && !m[(y - 1) * R.w + x]
            if (rim) return@paint ramp[0]
            val t = ((y + 0.5 - top) / (bottom - top)).toFloat() + (x - (ox + 16 * s)).toFloat() / (60f * s.toFloat())
            Px.ramp(ramp, t * 0.95f + 0.05f, x, y, 0.25f)
        }) { c, side, _, _ -> if (side == 2) Px.shadow(c, 0.5f) else Px.shadow(c, if (dark) 0.5f else 0.62f) }
    }

    private fun drops(R: Raster, list: List<Pair<Int, Int>>, f: Int, len: Int, ramp: IntArray, top: Int = 23) {
        val span = R.h - top
        for ((x, y0) in list) {
            val y = top + ((y0 - top + f * 2) % span + span) % span
            for (k in 0 until len) R[x - (k + y) / 6, y + k] = ramp[minOf(k, ramp.size - 1)]
        }
    }

    private fun flakes(R: Raster, list: List<Pair<Int, Int>>, f: Int) {
        val span = R.h - 22
        list.forEachIndexed { i, (x0, y0) ->
            val y = 22 + ((y0 - 22 + f) % span + span) % span
            val x = x0 + if ((f + i) % 4 < 2) 0 else 1
            if (i % 2 == 0) {
                R[x, y] = FLAKE[0]
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) R[x + dx, y + dy] = FLAKE[1]
                R[x + 1, y + 2] = FLAKE[2]
            } else {
                R[x, y] = FLAKE[0]; R[x, y + 1] = FLAKE[2]
            }
        }
    }

    private fun bolt(R: Raster) {
        val path = listOf(17.5 to 20.0, 13.0 to 25.0, 17.5 to 25.0, 12.5 to 31.5)
        val m = R.mask { x, y ->
            path.zipWithNext().any { (a, b) ->
                val (x1, y1) = a; val (x2, y2) = b
                val px = x + 0.5; val py = y + 0.5
                val dx = x2 - x1; val dy = y2 - y1
                val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
                val qx = x1 + t * dx - px; val qy = y1 + t * dy - py
                qx * qx + qy * qy <= 1.3
            }
        }
        R.paint(m, { x, y -> Px.band(BOLT, ((x - 12) / 7f) * 0.7f + ((y - 20) / 12f) * 0.3f) }) { _, _, _, _ -> BOLT_LINE }
    }

    private fun fog(R: Raster, f: Int) {
        for ((row, span) in listOf(19 to (3..28), 23 to (6..30), 27 to (2..25))) {
            for (x in span) {
                val xx = (x + f) % 32
                R[xx, row] = FOG[0]; R[xx, row + 1] = if ((x + row) % 2 == 0) FOG[1] else FOG[2]
            }
        }
    }

    private fun draw(kind: String, day: Boolean, f: Int): Raster {
        val R = Raster(SIZE, SIZE)
        val bob = listOf(0.0, 0.5, 1.0, 0.5)[f]
        fun sky(cx: Double, cy: Double, r: Double) { if (day) sun(R, cx, cy, r, f) else { moon(R, cx, cy, r + 0.8); stars(R, f) } }
        val rain = listOf(8 to 24, 13 to 28, 18 to 23, 23 to 27, 27 to 24, 11 to 31, 21 to 31)
        val drizzle = listOf(9 to 25, 15 to 28, 21 to 24, 26 to 29)
        val snow = listOf(8 to 25, 15 to 29, 22 to 24, 27 to 30, 12 to 22)
        when (kind) {
            "mostly" -> { sky(14.0, 13.0, 7.5); cloud(R, 8.0 + bob, 9.5, 0.72, false) }
            "partly" -> { sky(11.5, 11.0, 7.0); cloud(R, 3.0 + bob, 4.5, 0.95, false) }
            "cloudy" -> { cloud(R, -4.0 + bob, -4.0, 0.85, true); cloud(R, 2.5, 3.5, 0.95, false) }
            "fog" -> { cloud(R, 1.0 + bob / 2, -5.0, 0.95, false); fog(R, f) }
            "drizzle" -> { cloud(R, 0.5, -4.5, 1.0, false); drops(R, drizzle, f, 2, DROP) }
            "rain" -> { cloud(R, 0.5, -4.5, 1.0, true); drops(R, rain, f, 3, DROP) }
            "freezing" -> { cloud(R, 0.5, -4.5, 1.0, true); drops(R, drizzle, f, 2, ICE); flakes(R, listOf(24 to 27, 12 to 30), f) }
            "showers" -> { sky(9.0, 8.5, 5.8); cloud(R, 3.5, -1.5, 0.92, false); drops(R, rain.filter { it.first > 9 }, f, 3, DROP) }
            "snow" -> { cloud(R, 0.5, -4.5, 1.0, false); flakes(R, snow, f) }
            "thunder" -> { cloud(R, 0.5, -4.5, 1.0, true); drops(R, listOf(8 to 25, 25 to 26, 28 to 30, 6 to 30), f, 3, DROP); if (f < 2) bolt(R) }
            else -> sky(16.0, 16.0, 8.5)
        }
        return R
    }

    /** Moon phase picture (phase 0 new, 0.5 full) for the moon card. */
    @Synchronized
    fun moonPhase(phase: Double): IntArray = cache.getOrPut("phase" + (phase * 64).toInt()) {
        val R = Raster(SIZE, SIZE)
        val cx = 16.0; val cy = 16.0; val r = 12.5
        val k = cos(phase * 2 * PI)
        val m = R.mask { x, y -> disc(cx, cy, r, x, y) }
        R.paint(m, { x, y ->
            val nx = (x + 0.5 - cx) / r
            val lit = if (phase < 0.5) nx > k else nx < -k
            if (!lit) return@paint if ((x + y) % 2 == 0) 0xFF2A2A4A.toInt() else 0xFF26263F.toInt()
            val d = sqrt((x + 0.5 - (cx - 4)).let { it * it } + (y + 0.5 - (cy - 4)).let { it * it }) / (r * 1.7)
            val c = Px.ramp(MOON, d.toFloat(), x, y, 0.3f)
            // a few craters
            if ((x == 12 && y == 13) || (x == 19 && y == 20) || (x == 20 && y == 11) || (x == 13 && y == 22)) MOON[3] else c
        }) { _, _, _, _ -> MOON_LINE }
        R.px
    }
}
