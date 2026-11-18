package com.hootywhooo86.bitweather.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Android side of the shared pixel art: turns the 32x32 icons and 128-pixel scenes into crisp bitmaps. */
object PixelIcons {
    const val FRAMES = IconArt.FRAMES

    fun grid(kind: String, day: Boolean, frame: Int = 0): IntArray = IconArt.icon(kind, day, frame)

    fun moonGrid(phase: Double): IntArray = IconArt.moonPhase(phase)

    private fun side(g: IntArray) = kotlin.math.sqrt(g.size.toDouble()).toInt()

    /** Draw a square icon grid into a square of [size] at (x, y), pixel-perfect (no smoothing). */
    fun draw(c: Canvas, g: IntArray, x: Float, y: Float, size: Float) {
        val n = side(g)
        val p = Paint().apply { isAntiAlias = false }
        val u = size / n
        for (row in 0 until n) {
            var col = 0
            while (col < n) {
                val v = g[row * n + col]
                if ((v ushr 24) == 0) { col++; continue }
                var w = 1
                while (col + w < n && g[row * n + col + w] == v) w++
                p.color = v
                c.drawRect(x + col * u, y + row * u, x + (col + w) * u + 0.35f, y + (row + 1) * u + 0.35f, p)
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

    /** Raw 1:1 bitmap — scale it up with nearest-neighbour filtering. */
    fun raw(g: IntArray): Bitmap { val n = side(g); return Bitmap.createBitmap(g, n, n, Bitmap.Config.ARGB_8888) }

    /** A 128-pixel scene as a 1:1 bitmap. */
    fun scene(w: Int, h: Int, spec: SceneArt.Spec, frame: Int): Bitmap =
        Bitmap.createBitmap(SceneArt.render(w, h, spec, frame), w, h, Bitmap.Config.ARGB_8888)
}
