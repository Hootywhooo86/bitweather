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
    // small 2dp steps: smooth-looking rounded corners that are still made of pixels
    val steps = (radius.value / 2.5f).toInt().coerceIn(1, 6)
    return PixelShape(radius / steps / 1.6f, steps)
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
        T(text.removeSuffix("°"), size, color, shadow = true)
        Glyph("degree", (size.value * 0.3f).dp, color, Modifier.padding(start = (size.value * 0.08f).dp, top = (size.value * 0.25f).dp))
    }
}

/** CRT scanlines like the 128bit landing page. */
fun Modifier.scanlines(alpha: Float = 0.05f) = drawWithContent {
    drawContent()
    val line = 1.dp.toPx(); val gap = 3.dp.toPx()
    var y = 0f
    while (y < size.height) { drawRect(Color.Black.copy(alpha = alpha), Offset(0f, y), Size(size.width, line)); y += gap }
}

private fun rnd(i: Int, salt: Int): Float {
    val v = sin((i * 12.9898 + salt * 78.233)) * 43758.5453
    return (v - floor(v)).toFloat()
}

/** Off in screenshot tests so the endless animation doesn't stall rendering. */
var animateFx = true

// ---------------------------------------------------------------- animation helpers

/** 0→1 when first shown (and again when [key] changes), so bars, gauges and arcs sweep in. Instant in tests. */
@Composable
fun rememberReveal(key: Any? = Unit, millis: Int = 700): Float {
    if (!animateFx) return 1f
    val a = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) { a.animateTo(1f, tween(millis, easing = LinearEasing)) }
    // stepped, so it moves in chunky pixel increments rather than smoothly
    return floor(a.value * 12f) / 12f
}

/** Arcade-style number that ticks up/down to [target] one step at a time. */
@Composable
fun rollingInt(target: Int): Int {
    if (!animateFx) return target
    val shown = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(target) {
        val steps = kotlin.math.abs(target - shown.intValue).coerceAtLeast(1)
        val pause = (600L / steps).coerceIn(15L, 70L)
        while (shown.intValue != target) {
            shown.intValue += if (target > shown.intValue) 1 else -1
            kotlinx.coroutines.delay(pause)
        }
    }
    return shown.intValue
}

/** Big temperature that rolls up like a score counter. */
@Composable
fun RollingTemp(celsius: Double, size: androidx.compose.ui.unit.TextUnit, color: Color = Color.White, modifier: Modifier = Modifier) {
    if (celsius.isNaN()) { BigTemp("--°", size, color, modifier); return }
    val unit = com.hootywhooo86.bitweather.data.Fmt.t(celsius).removeSuffix("°").toIntOrNull()
    if (unit == null) { BigTemp(com.hootywhooo86.bitweather.data.Fmt.t(celsius), size, color, modifier); return }
    BigTemp("${rollingInt(unit)}°", size, color, modifier)
}

/** Screen change / place swipe: the new screen dissolves in block by block. */
@Composable
fun PixelWipe(key: Any, modifier: Modifier = Modifier) {
    if (!animateFx) return
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    val first = remember { booleanArrayOf(true) } // no wipe when the app first opens
    androidx.compose.runtime.LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        p.snapTo(1f); p.animateTo(0f, tween(320, easing = LinearEasing))
    }
    if (p.value <= 0f) return
    Canvas(modifier) {
        val b = 18.dp.toPx()
        val cols = (size.width / b).toInt() + 1; val rows = (size.height / b).toInt() + 1
        for (y in 0 until rows) for (x in 0 until cols) {
            if (rnd(x * 131 + y, 11) < p.value) drawRect(C.bg, Offset(x * b, y * b), Size(b + 1, b + 1))
        }
    }
}

/** Retro loading bar: a block bouncing along while weather refreshes. */
@Composable
fun PixelLoadingBar(modifier: Modifier = Modifier) {
    val n = 24
    val frame = com.hootywhooo86.bitweather.ui.AppModel.frame
    val pos = frame % (2 * n - 2)
    val head = if (pos < n) pos else 2 * n - 2 - pos
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        for (i in 0 until n) {
            val d = kotlin.math.abs(i - head)
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).size(6.dp)
                    .then(Modifier.drawWithContent {
                        drawRect(if (d == 0) C.orange else if (d == 1) C.purple else C.edge)
                    }),
            )
        }
    }
}
