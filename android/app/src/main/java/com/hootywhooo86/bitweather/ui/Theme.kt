package com.hootywhooo86.bitweather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.R
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.pixel.PixelIcons

object C {
    val bg = Color(0xFF0D0D1A); val panel = Color(0xFF1A1A2E); val panel2 = Color(0xFF24243C); val edge = Color(0xFF3D3D5C)
    val ink = Color(0xFFF5F5F5); val dim = Color(0xFF9E9E9E); val mute = Color(0xFF55556E)
    val purple = Color(0xFFB388FF); val purpleD = Color(0xFF4A148C); val orange = Color(0xFFFF9F1C)
    val cyan = Color(0xFF4FC3F7); val green = Color(0xFF69F0AE); val red = Color(0xFFFF5252); val yellow = Color(0xFFFFEB3B)
}

val Pixel = FontFamily(Font(R.font.press_start_2p))

@Composable
fun T(
    s: String, size: TextUnit = 9.sp, color: Color = C.ink, modifier: Modifier = Modifier,
    align: TextAlign? = null, lines: Int = Int.MAX_VALUE, lh: TextUnit = TextUnit.Unspecified, family: FontFamily = Pixel,
) {
    BasicText(
        s, modifier,
        style = TextStyle(fontFamily = family, fontSize = size, color = color, textAlign = align ?: TextAlign.Start, lineHeight = lh),
        maxLines = lines, overflow = TextOverflow.Ellipsis,
    )
}

/** Symbols the pixel font doesn't have (arrows, ☰, ⚙ …) use the system font. */
@Composable
fun Sym(s: String, size: TextUnit = 14.sp, color: Color = C.ink, modifier: Modifier = Modifier) =
    T(s, size, color, modifier, family = FontFamily.Default)

@Composable
fun PixelIcon(code: Int, day: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(code, day) { PixelIcons.raw(PixelIcons.grid(Wmo.kind(code), day)).asImageBitmap() }
    Image(bmp, null, modifier.size(size), filterQuality = FilterQuality.None)
}

@Composable
fun GridIcon(grid: IntArray, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(grid) { PixelIcons.raw(grid).asImageBitmap() }
    Image(bmp, null, modifier.size(size), filterQuality = FilterQuality.None)
}

@Composable
fun PxButton(
    label: String, modifier: Modifier = Modifier, on: Boolean = false, primary: Boolean = false, warn: Boolean = false,
    enabled: Boolean = true, symbol: Boolean = false, onClick: () -> Unit,
) {
    val bgc = if (on || primary) C.purple else C.panel
    val fg = when { on || primary -> C.bg; warn -> C.red; else -> C.ink }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
            .background(Color.Black, shape)
            .padding(bottom = 3.dp)
            .background(bgc, shape)
            .border(3.dp, if (on || primary) C.purple else C.edge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol) Sym(label, 15.sp, fg) else T(label, 8.sp, fg)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Chips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
fun Section(s: String) = T(s, 8.sp, C.orange, Modifier.padding(top = 20.dp, bottom = 10.dp))

@Composable
fun Note(s: String) = T(s, 7.sp, C.dim, Modifier.padding(top = 8.dp), lh = 14.sp)

@Composable
fun SegBar(n: Int, max: Int, colors: List<Color>, mark: Int? = null, height: Dp = 10.dp) {
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until max) {
            val col = colors[minOf(i, colors.size - 1)]
            Box(
                Modifier.weight(1f).height(height)
                    .then(if (mark == i) Modifier.border(2.dp, Color.White) else Modifier)
                    .background(col.copy(alpha = if (i < n || mark == i) 1f else 0.28f)),
            )
        }
    }
}
