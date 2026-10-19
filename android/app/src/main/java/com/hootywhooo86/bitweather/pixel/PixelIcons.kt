package com.hootywhooo86.bitweather.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Procedural 16x16 pixel-art weather icons (same art as the web version), outlined and rendered crisp. */
object PixelIcons {
    private const val OL = 0xFF0D0D1A.toInt()
    private const val SUN = 0xFFFFC107.toInt(); private const val SUN_HI = 0xFFFFE082.toInt(); private const val SUN_LO = 0xFFFF9F1C.toInt()
    private const val MOON = 0xFFE8E3FF.toInt(); private const val MOON_LO = 0xFFB388FF.toInt()
    private const val CLOUD = 0xFFECEAF6.toInt(); private const val CLOUD_LO = 0xFFA7A7C2.toInt()
    private const val DARK = 0xFF8A8AA8.toInt(); private const val DARK_LO = 0xFF5C5C7A.toInt()
    private const val RAIN = 0xFF4FC3F7.toInt(); private const val SNOW = 0xFFFFFFFF.toInt(); private const val BOLT = 0xFFFFEB3B.toInt()
    private const val FOG = 0xFFB0B0C8.toInt(); private const val ICE = 0xFF80DEEA.toInt()

    private class Layer(val px: Set<Int>, val fill: (Int, Int) -> Int)

    private fun key(x: Int, y: Int) = y * 16 + x
    private fun inb(x: Int, y: Int) = x in 0..15 && y in 0..15

    private fun disc(cx: Double, cy: Double, r: Double): Set<Int> {
        val s = HashSet<Int>()
        for (y in 0..15) for (x in 0..15) {
            val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
            if (dx * dx + dy * dy <= r * r) s += key(x, y)
        }
        return s
    }
    private fun pts(list: List<Pair<Int, Int>>) = list.filter { inb(it.first, it.second) }.map { key(it.first, it.second) }.toSet()

    private fun sun(cx: Double, cy: Double, r: Double, ray: Double): List<Layer> {
        val rays = HashSet<Int>()
        for (k in 0 until 8) {
            val a = k * PI / 4
            var d = r + 1.6
            while (d < r + 1.6 + ray) {
                val x = floor(cx + cos(a) * d).toInt(); val y = floor(cy + sin(a) * d).toInt()
                if (inb(x, y)) rays += key(x, y)
                d += 0.9
            }
        }
        return listOf(
            Layer(rays) { _, _ -> SUN_LO },
            Layer(disc(cx, cy, r)) { x, y ->
                when {
                    x + 0.5 < cx - r * 0.2 && y + 0.5 < cy - r * 0.2 -> SUN_HI
                    y + 0.5 > cy + r * 0.45 -> SUN_LO
                    else -> SUN
                }
            },
        )
    }
    private fun moon(cx: Double, cy: Double, r: Double): Layer {
        val px = disc(cx, cy, r) - disc(cx + r * 0.65, cy - r * 0.45, r * 0.85)
        return Layer(px) { x, _ -> if (x + 0.5 < cx - r * 0.45) MOON else MOON_LO }
    }
    private fun cloud(ox: Double, oy: Double, s: Double, dark: Boolean = false): Layer {
        val bottom = oy + 12 * s
        val px = (disc(ox + 5 * s, oy + 9.5 * s, 3 * s) + disc(ox + 9.5 * s, oy + 7.5 * s, 4 * s) +
            disc(ox + 12.6 * s, oy + 10 * s, 2.6 * s) + disc(ox + 4.5 * s, oy + 11 * s, 1.8 * s))
            .filter { (it / 16) + 0.5 <= bottom }.toSet()
        val hi = if (dark) DARK else CLOUD; val lo = if (dark) DARK_LO else CLOUD_LO
        return Layer(px) { _, y -> if (y + 0.5 > bottom - 1.5) lo else hi }
    }
    private fun color(px: Set<Int>, c: Int) = Layer(px) { _, _ -> c }

    private val RAIN_PTS = pts(listOf(4 to 11, 3 to 12, 8 to 11, 7 to 12, 12 to 11, 11 to 12, 6 to 14, 5 to 15, 10 to 14, 9 to 15))
    private val DRIZZLE_PTS = pts(listOf(4 to 11, 8 to 12, 12 to 11, 6 to 14, 10 to 14))
    private fun flake(x: Int, y: Int) = listOf(x to y, x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
    private val SNOW_PTS = pts(flake(4, 12) + flake(9, 13) + flake(13, 11))
    private val BOLT_PTS = pts(listOf(8 to 9, 9 to 9, 7 to 10, 8 to 10, 6 to 11, 7 to 11, 8 to 11, 9 to 11, 8 to 12, 7 to 13, 6 to 14))
    private val FOG_PTS = pts(buildList {
        for ((y, a, b) in listOf(Triple(10, 1, 14), Triple(12, 3, 12), Triple(14, 2, 15))) for (x in a..b) if ((x + y) % 5 != 0) add(x to y)
    })

    private fun layers(kind: String, day: Boolean): List<Layer> {
        fun sky(cx: Double, cy: Double, r: Double, ray: Double) = if (day) sun(cx, cy, r, ray) else listOf(moon(cx, cy, r + 0.6))
        val up = -3.0
        return when (kind) {
            "mostly" -> sky(7.0, 7.0, 3.8, 1.8) + cloud(5.0, 6.5, 0.72)
            "partly" -> sky(6.0, 6.0, 3.4, 1.6) + cloud(1.5, 3.0, 1.0)
            "cloudy" -> listOf(cloud(-1.5, -2.5, 0.85, true), cloud(1.0, 2.0, 1.0))
            "fog" -> listOf(cloud(0.0, -4.0, 1.0), color(FOG_PTS, FOG))
            "drizzle" -> listOf(cloud(0.0, up, 1.0), color(DRIZZLE_PTS, RAIN))
            "rain" -> listOf(cloud(0.0, up, 1.0, true), color(RAIN_PTS, RAIN))
            "freezing" -> listOf(cloud(0.0, up, 1.0, true), color(DRIZZLE_PTS, ICE), color(pts(listOf(12 to 13, 11 to 14, 12 to 15, 13 to 14)), SNOW))
            "showers" -> sky(4.5, 4.5, 3.0, 1.4) + listOf(cloud(1.0, -1.5, 0.9), color(RAIN_PTS.filter { it % 16 > 4 }.toSet(), RAIN))
            "snow" -> listOf(cloud(0.0, up, 1.0), color(SNOW_PTS, SNOW))
            "thunder" -> listOf(cloud(0.0, up, 1.0, true), color(pts(listOf(3 to 11, 2 to 12, 12 to 11, 11 to 12, 13 to 14, 12 to 15)), RAIN), color(BOLT_PTS, BOLT))
            else -> sky(8.0, 8.0, 4.4, 2.2)
        }
    }

    private fun paint(ls: List<Layer>): IntArray {
        val g = IntArray(256)
        for (l in ls) {
            for (k in l.px) {
                val x = k % 16; val y = k / 16
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (!inb(nx, ny) || key(nx, ny) in l.px) continue
                    g[key(nx, ny)] = OL
                }
            }
            for (k in l.px) g[k] = l.fill(k % 16, k / 16)
        }
        return g
    }

    private val cache = HashMap<String, IntArray>()

    @Synchronized
    fun grid(kind: String, day: Boolean): IntArray = cache.getOrPut(kind + day) { paint(layers(kind, day)) }

    fun moonGrid(phase: Double): IntArray {
        val px = disc(8.0, 8.0, 6.0)
        val g = IntArray(256)
        for (k in px) {
            val x = k % 16; val y = k / 16
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (inb(nx, ny) && key(nx, ny) !in px) g[key(nx, ny)] = OL
            }
        }
        val kk = cos(phase * 2 * PI)
        for (k in px) {
            val x = k % 16; val y = k / 16
            val nx = (x + 0.5 - 8) / 6
            val lit = if (phase < 0.5) nx > kk else nx < -kk
            g[k] = if (lit) (if ((x + y) % 7 == 0) MOON_LO else MOON) else 0xFF2A2A44.toInt()
        }
        return g
    }

    /** Draw a 16x16 grid into a square of side [size] at (x, y). */
    fun draw(c: Canvas, g: IntArray, x: Float, y: Float, size: Float) {
        val p = Paint().apply { isAntiAlias = false }
        val u = size / 16f
        for (row in 0..15) {
            var col = 0
            while (col < 16) {
                val v = g[row * 16 + col]
                if (v == 0) { col++; continue }
                var w = 1
                while (col + w < 16 && g[row * 16 + col + w] == v) w++
                p.color = v
                c.drawRect(x + col * u, y + row * u, x + (col + w) * u + 0.5f, y + (row + 1) * u + 0.5f, p)
                col += w
            }
        }
    }

    fun bitmap(kind: String, day: Boolean, size: Int): Bitmap = gridBitmap(grid(kind, day), size)

    fun gridBitmap(g: IntArray, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), g, 0f, 0f, size.toFloat())
        return b
    }

    /** Raw 16x16 bitmap — scale it up with nearest-neighbour filtering in Compose. */
    fun raw(g: IntArray): Bitmap = Bitmap.createBitmap(g, 16, 16, Bitmap.Config.ARGB_8888)
}
