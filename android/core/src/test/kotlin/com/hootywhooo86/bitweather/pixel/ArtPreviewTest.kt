package com.hootywhooo86.bitweather.pixel

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Renders the pixel art to build/art-previews for review (icons at 32x32, scenes at 128 wide), scaled up crisply. */
class ArtPreviewTest {
    private val out = File("build/art-previews").apply { mkdirs() }

    private fun png(name: String, w: Int, h: Int, px: IntArray, scale: Int, bg: Int = 0xFF1A1A2E.toInt()) {
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) {
            val c = px[(y / scale) * w + x / scale]
            img.setRGB(x, y, if ((c ushr 24) == 0) bg else c)
        }
        ImageIO.write(img, "png", File(out, "$name.png"))
    }

    @Test
    fun icons() {
        val kinds = listOf("clear", "mostly", "partly", "cloudy", "fog", "drizzle", "rain", "freezing", "showers", "snow", "thunder")
        val cols = 8; val s = IconArt.SIZE; val gap = 4
        val W = cols * (s + gap) + gap; val H = kinds.size * (s + gap) + gap
        val sheet = IntArray(W * H) { 0xFF1A1A2E.toInt() }
        kinds.forEachIndexed { r, k ->
            for (night in 0..1) for (f in 0 until 4) {
                val g = IconArt.icon(k, night == 0, f)
                val ox = gap + (night * 4 + f) * (s + gap); val oy = gap + r * (s + gap)
                for (y in 0 until s) for (x in 0 until s) { val c = g[y * s + x]; if ((c ushr 24) != 0) sheet[(oy + y) * W + ox + x] = c }
            }
        }
        png("icons", W, H, sheet, 4)
    }

    @Test
    fun scenes() {
        val specs = listOf(
            "clear-day" to SceneArt.Spec("clear", true, 0.45, 0f, false, 0.3),
            "partly-dusk" to SceneArt.Spec("partly", true, 0.97, 0.9f, false, 0.3),
            "snow-day" to SceneArt.Spec("snow", true, 0.3, 0f, true, 0.3),
            "rain-day" to SceneArt.Spec("rain", true, 0.6, 0f, false, 0.3),
            "clear-night" to SceneArt.Spec("clear", false, 0.4, 0f, true, 0.42),
            "thunder" to SceneArt.Spec("thunder", true, 0.5, 0f, false, 0.3),
            "fog" to SceneArt.Spec("fog", true, 0.2, 0f, true, 0.3),
            "cloudy-night" to SceneArt.Spec("cloudy", false, 0.6, 0f, true, 0.7),
        )
        for ((name, spec) in specs) png("scene-$name", 128, 72, SceneArt.render(128, 72, spec, 0), 5)
    }
}
