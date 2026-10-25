package com.hootywhooo86.bitweather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hootywhooo86.bitweather.pixel.PixelGlyphs
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Rounded corners drawn as pixel staircases. */
class PixelShape(private val step: Dp, private val steps: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val u = with(density) { step.toPx() }
        val n = steps
        val tl = buildList {
            for (k in 0 until n) { add(k to n - k); add(k + 1 to n - k) }
            add(n to 0)
        }
        val w = size.width; val h = size.height
        val p = Path()
        fun pt(x: Float, y: Float, first: Boolean) = if (first) p.moveTo(x, y) else p.lineTo(x, y)
        tl.forEachIndexed { i, (x, y) -> pt(x * u, y * u, i == 0) }
        tl.reversed().forEach { (x, y) -> pt(w - x * u, y * u, false) }
        tl.forEach { (x, y) -> pt(w - x * u, h - y * u, false) }
        tl.reversed().forEach { (x, y) -> pt(x * u, h - y * u, false) }
        p.close()
        return Outline.Generic(p)
    }
}

/** Pixel staircase roughly matching a corner radius. */
fun pixelShape(radius: Dp): Shape {
    val steps = (radius.value / 4f).toInt().coerceIn(1, 4)
    return PixelShape(radius / steps, steps)
}

@Composable
fun Glyph(name: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val g = PixelGlyphs.of(name) ?: return
    Canvas(modifier.size(size)) {
        val u = floor(minOf(this.size.width / g.w, this.size.height / g.h))
        val ox = floor((this.size.width - u * g.w) / 2); val oy = floor((this.size.height - u * g.h) / 2)
        for (y in 0 until g.h) for (x in 0 until g.w) if (g.on[y * g.w + x]) {
            drawRect(color, Offset(ox + x * u, oy + y * u), Size(u, u))
        }
    }
}

/** Big temperature with a pixel degree ring hugging the digits. */
@Composable
fun BigTemp(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color = Color.White, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier) {
        T(text.removeSuffix("°"), size, color)
        Glyph("degree", (size.value * 0.28f).dp, color, Modifier.padding(start = (size.value * 0.06f).dp))
    }
}

/** CRT scanlines like the 128bit landing page. */
fun Modifier.scanlines(alpha: Float = 0.14f) = drawWithContent {
    drawContent()
    val line = 2.dp.toPx(); val gap = 4.dp.toPx()
    var y = 0f
    while (y < size.height) { drawRect(Color.Black.copy(alpha = alpha), Offset(0f, y), Size(size.width, line)); y += gap }
}

private fun rnd(i: Int, salt: Int): Float {
    val v = sin((i * 12.9898 + salt * 78.233)) * 43758.5453
    return (v - floor(v)).toFloat()
}

/** Off in screenshot tests so the endless animation doesn't stall rendering. */
var animateFx = true

/** Animated pixel weather behind the hero: snow, rain, stars, drifting clouds, lightning. */
@Composable
fun PixelWeatherFx(kind: String, day: Boolean, modifier: Modifier = Modifier) {
    val t = if (animateFx) {
        val tr = rememberInfiniteTransition(label = "fx")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "t").value
    } else 0.37f
    val cloud = remember {
        listOf("..##......", ".####.##..", "##########", ".########.")
    }
    Canvas(modifier) {
        val u = 3.dp.toPx()
        fun q(v: Float) = floor(v / u) * u
        val w = size.width; val h = size.height
        val wet = kind in setOf("rain", "drizzle", "showers", "thunder", "freezing")
        when {
            kind == "snow" -> for (i in 0 until 46) {
                val y = ((rnd(i, 1) + t * (1.5f + rnd(i, 2))) % 1f) * h
                val x = rnd(i, 3) * w + sin((t * 6 + rnd(i, 4)) * 2 * PI).toFloat() * u * 2
                val s = if (i % 5 == 0) 2 * u else u
                drawRect(Color.White.copy(alpha = 0.75f), Offset(q(x), q(y)), Size(s, s))
            }
            wet -> for (i in 0 until (if (kind == "drizzle") 34 else 60)) {
                val y = ((rnd(i, 1) + t * (6f + 3 * rnd(i, 2))) % 1f) * h
                val x = rnd(i, 3) * w - y * 0.15f
                drawRect(Color(0xFF4FC3F7).copy(alpha = 0.55f), Offset(q(x), q(y)), Size(u, u * 3))
            }
        }
        if (!day && !wet && kind != "snow") for (i in 0 until 34) {
            val tw = sin((t * 8 + rnd(i, 5)) * 2 * PI)
            if (tw > -0.3) drawRect(Color.White.copy(alpha = if (tw > 0.6) 0.9f else 0.45f),
                Offset(q(rnd(i, 6) * w), q(rnd(i, 7) * h * 0.6f)), Size(u, u))
        }
        if (kind in setOf("partly", "mostly", "cloudy", "fog") || (kind == "clear" && day)) {
            val count = when (kind) { "cloudy", "fog" -> 4; "clear" -> 1; else -> 2 }
            for (c in 0 until count) {
                val speed = 0.5f + rnd(c, 8) * 0.5f
                val x0 = ((rnd(c, 9) + t * speed) % 1.3f - 0.2f) * w
                val y0 = q(h * (0.08f + 0.5f * rnd(c, 10)))
                val s = u * (if (c % 2 == 0) 2 else 1)
                cloud.forEachIndexed { ry, row -> row.forEachIndexed { rx, ch ->
                    if (ch == '#') drawRect(Color.White.copy(alpha = if (kind == "fog") 0.18f else 0.12f), Offset(q(x0) + rx * s, y0 + ry * s), Size(s, s))
                } }
            }
        }
        if (kind == "thunder" && (t * 9) % 1f < 0.035f) drawRect(Color.White.copy(alpha = 0.28f))
    }
}
