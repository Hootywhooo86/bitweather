package com.hootywhooo86.bitweather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.pixel.IconArt
import com.hootywhooo86.bitweather.pixel.PixelGlyphs
import com.hootywhooo86.bitweather.pixel.SceneArt
import java.awt.image.BufferedImage
import kotlin.math.floor

object C {
    val bg = Color(0xFF0D0D1A); val panel = Color(0xFF1A1A2E); val panel2 = Color(0xFF24243C); val edge = Color(0xFF3D3D5C)
    val ink = Color(0xFFF5F5F5); val dim = Color(0xFF9E9E9E); val mute = Color(0xFF55556E)
    val purple = Color(0xFFB388FF); val orange = Color(0xFFFF9F1C); val cyan = Color(0xFF4FC3F7)
    val green = Color(0xFF69F0AE); val red = Color(0xFFFF5252); val yellow = Color(0xFFFFEB3B)
}

val Pixel = FontFamily(
    Font("fonts/pixelify_sans.ttf", FontWeight.Normal),
    Font("fonts/pixelify_sans_bold.ttf", FontWeight.Bold),
)

@Composable
fun T(s: String, size: TextUnit = 13.sp, color: Color = C.ink, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE, shadow: Boolean = false, bold: Boolean = false) {
    BasicText(
        s, modifier,
        style = TextStyle(
            fontFamily = Pixel, fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            shadow = if (shadow) Shadow(Color(0xCC0D0D1A), Offset(2f, 2f), 0f) else null,
        ),
        maxLines = lines, overflow = TextOverflow.Ellipsis,
    )
}

/** Rounded corners drawn as small pixel staircases. */
class PixelShape(private val step: Dp, private val steps: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val u = with(density) { step.toPx() }
        val n = steps
        val tl = buildList { for (k in 0 until n) { add(k to n - k); add(k + 1 to n - k) }; add(n to 0) }
        val w = size.width; val h = size.height
        val p = Path()
        tl.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x * u, y * u) else p.lineTo(x * u, y * u) }
        tl.reversed().forEach { (x, y) -> p.lineTo(w - x * u, y * u) }
        tl.forEach { (x, y) -> p.lineTo(w - x * u, h - y * u) }
        tl.reversed().forEach { (x, y) -> p.lineTo(x * u, h - y * u) }
        p.close()
        return Outline.Generic(p)
    }
}

val Corners = PixelShape(2.dp, 3)

fun Modifier.panel(color: Color = C.panel, edge: Color = C.edge) =
    this.background(Color.Black, Corners).padding(bottom = 2.dp).background(color, Corners).border(2.dp, edge, Corners)

@Composable
fun Glyph(name: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val g = PixelGlyphs.of(name) ?: return
    Canvas(modifier.size(size)) {
        val u = floor(minOf(this.size.width / g.w, this.size.height / g.h))
        val ox = floor((this.size.width - u * g.w) / 2); val oy = floor((this.size.height - u * g.h) / 2)
        for (y in 0 until g.h) for (x in 0 until g.w) if (g.on[y * g.w + x]) drawRect(color, Offset(ox + x * u, oy + y * u), Size(u, u))
    }
}

@Composable
fun PxButton(label: String, modifier: Modifier = Modifier, on: Boolean = false, glyph: String? = null, onClick: () -> Unit) {
    val bgc = if (on) C.purple else C.panel
    val fg = if (on) C.bg else C.ink
    Box(
        modifier.defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
            .panel(bgc, if (on) C.purple else C.edge)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) Glyph(glyph, 12.dp, fg)
            if (glyph != null && label.isNotEmpty()) Box(Modifier.size(6.dp))
            if (label.isNotEmpty()) T(label, 13.sp, fg)
        }
    }
}

fun pixels(px: IntArray, w: Int, h: Int): ImageBitmap {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, w, h, px, 0, w)
    return img.toComposeImageBitmap()
}

private val iconCache = HashMap<String, ImageBitmap>()

/** A 32-pixel weather icon, animated from the shared frame clock, scaled by a whole number. */
@Composable
fun PixelIcon(code: Int, day: Boolean, scale: Int, modifier: Modifier = Modifier) {
    val f = if (Model.animate) (Model.frame / 2) % IconArt.FRAMES else 0
    val kind = Wmo.kind(code)
    val bmp = remember(kind, day, f) { iconCache.getOrPut("$kind$day$f") { pixels(IconArt.icon(kind, day, f), 32, 32) } }
    Image(bmp, null, modifier.size((32 * scale).dp), filterQuality = FilterQuality.None)
}

@Composable
fun MoonIcon(phase: Double, scale: Int) {
    val bmp = remember((phase * 64).toInt()) { pixels(IconArt.moonPhase(phase), 32, 32) }
    Image(bmp, null, Modifier.size((32 * scale).dp), filterQuality = FilterQuality.None)
}

/** The 128-pixel landscape at a whole-number scale; re-drawn per animation frame. */
@Composable
fun Scene(spec: SceneArt.Spec, w: Int, h: Int, scale: Int, modifier: Modifier = Modifier) {
    val f = if (Model.animate) Model.frame else 0
    val bmp = remember(spec, f, w, h) { pixels(SceneArt.render(w, h, spec, f), w, h) }
    Image(bmp, null, modifier.size((w * scale).dp, (h * scale).dp), filterQuality = FilterQuality.None)
}

