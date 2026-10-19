package com.hootywhooo86.bitweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.data.Weather
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.data.naive
import com.hootywhooo86.bitweather.pixel.PixelIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val CARD_NAMES = mapOf(
    "alerts" to "Alerts", "brief" to "Weather brief", "insight" to "Weather insight", "hourly" to "Hourly forecast",
    "daily" to "10-day forecast", "precip" to "Precipitation", "wind" to "Wind", "sun" to "Sunrise & sunset",
    "uv" to "UV index", "aqi" to "Air quality", "visibility" to "Visibility", "humidity" to "Humidity",
    "pressure" to "Pressure", "feels" to "Feels like", "moon" to "Moon", "radar" to "Radar",
)
val CARD_SIZES = mapOf(
    "alerts" to listOf("T", "W", "S"), "brief" to listOf("W", "T", "L"), "insight" to listOf("T", "S"),
    "hourly" to listOf("W", "L"), "daily" to listOf("L", "W"), "precip" to listOf("S", "W"), "wind" to listOf("S", "W"),
    "sun" to listOf("S", "W"), "uv" to listOf("S", "W"), "aqi" to listOf("S", "W"), "visibility" to listOf("S", "W"),
    "humidity" to listOf("S", "W"), "pressure" to listOf("S", "W"), "feels" to listOf("S", "W"), "moon" to listOf("S", "W"),
    "radar" to listOf("L", "W"),
)
private val GLYPH = mapOf(
    "alerts" to "!", "brief" to "▤", "insight" to "↗", "hourly" to "◷", "daily" to "▦", "precip" to "⋮", "wind" to "≈",
    "sun" to "☼", "uv" to "☀", "aqi" to "≋", "visibility" to "◉", "humidity" to "●", "pressure" to "⇕", "feels" to "♨",
    "moon" to "☾", "radar" to "◎",
)

@Composable
fun CardHead(id: String) {
    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(GLYPH[id] ?: "", 11.sp, C.purple)
        Spacer(Modifier.width(8.dp))
        T(CARD_NAMES[id]!!.uppercase(), 8.sp, C.orange, lines = 1)
    }
}

@Composable
private fun Big(v: String, unit: String = "", size: TextUnit = 24.sp) = Row(verticalAlignment = Alignment.Bottom) {
    T(v, size, Color.White)
    if (unit.isNotEmpty()) T(unit, 10.sp, C.dim, Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
private fun Lbl(s: String, color: Color = C.dim, modifier: Modifier = Modifier) = T(s, 8.sp, color, modifier, lh = 14.sp)

@Composable
private fun Kv(items: List<Pair<String, String>>, cols: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) { T(k, 7.sp, C.dim, lines = 1); T(v, 9.sp, Color.White, Modifier.padding(top = 4.dp), lines = 1) }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Bars(values: List<Double>, max: Double, modifier: Modifier = Modifier, color: Color = C.cyan) {
    Canvas(modifier.fillMaxWidth()) {
        if (values.isEmpty()) return@Canvas
        val m = if (max > 0) max else (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val bw = size.width / values.size
        values.forEachIndexed { i, v0 ->
            val v = if (v0.isNaN()) 0.0 else v0
            val h = max(2f, (v / m * size.height).toFloat().coerceAtMost(size.height))
            drawRect(if (v <= 0) C.edge else color, Offset(i * bw, size.height - h), Size(bw - 2f, h))
        }
    }
}

@Composable
private fun Axis(vararg labels: String) = Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    labels.forEach { T(it, 6.sp, C.mute) }
}

@Composable
fun CardBody(act: MainActivity, id: String, sz: String, b: WeatherBundle) {
    val w = b.weather
    when (id) {
        "alerts" -> AlertsCard(b, sz)
        "brief" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            T(Derive.brief(w), 9.sp, Color(0xFFDDDDDD), lh = 16.sp)
            if (sz == "L") T(Derive.briefExtra(w).joinToString("\n"), 9.sp, C.dim, Modifier.padding(top = 12.dp), lh = 16.sp)
        }
        "insight" -> {
            val (g, t) = Derive.insight(b)
            if (sz == "S") Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Sym(g, 28.sp, C.orange); T(t, 8.sp, Color.White, Modifier.padding(top = 8.dp), align = TextAlign.Center, lh = 14.sp)
            } else T(t, 9.sp, Color.White, lh = 16.sp)
        }
        "hourly" -> HourlyCard(w, sz)
        "daily" -> DailyCard(w, sz)
        "precip" -> PrecipCard(w, sz)
        "wind" -> WindCard(w, sz)
        "sun" -> SunCard(w, sz)
        "uv" -> UvCard(w, sz)
        "aqi" -> AqiCard(b, sz)
        "visibility" -> {
            val m = w.current.visibility; val (v, u) = Fmt.dist(m)
            Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
                Big(v, u); Lbl(Derive.visibilityDesc(m), modifier = Modifier.padding(top = 8.dp))
                if (sz != "S") {
                    Bars(Derive.hoursAhead(w, 24).map { min(w.hourly.vis.at(it), 30000.0) }, 30000.0, Modifier.weight(1f).padding(top = 8.dp))
                    Axis("Now", "Next 24 h", "+24h")
                }
            }
        }
        "humidity" -> {
            val h = w.current.humidity
            Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
                Big("${if (h.isNaN()) "--" else h.roundToInt()}%")
                Spacer(Modifier.height(8.dp))
                SegBar(((if (h.isNaN()) 0.0 else h) / 100 * 8).roundToInt(), 8, listOf(C.cyan), height = 14.dp)
                Lbl("Dew point ${Fmt.t(w.current.dew)}", modifier = Modifier.padding(top = 8.dp))
                if (sz != "S") {
                    val dp = w.current.dew
                    Lbl(when { dp < 10 -> "Dry and comfortable"; dp < 16 -> "Comfortable"; dp < 20 -> "Slightly humid"; dp < 24 -> "Humid"; else -> "Oppressive" })
                    Bars(Derive.hoursAhead(w, 24).map { w.hourly.humidity.at(it) }, 100.0, Modifier.weight(1f).padding(top = 6.dp))
                    Axis("Now", "+24h")
                }
            }
        }
        "pressure" -> PressureCard(w, sz)
        "feels" -> Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
            Big(Fmt.t(w.current.feels))
            Lbl("Actual ${Fmt.t(w.current.temp)}\n${Derive.feelsWhy(w.current)}", modifier = Modifier.padding(top = 8.dp))
            if (sz != "S") {
                Spacer(Modifier.weight(1f))
                val d = w.d0()
                Kv(listOf("Feels-like high" to Fmt.t(w.daily.fmax.at(d)), "Feels-like low" to Fmt.t(w.daily.fmin.at(d))))
            }
        }
        "moon" -> {
            val m = Derive.moon()
            if (sz == "S") Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GridIcon(PixelIcons.moonGrid(m.phase), 56.dp)
                T(m.name, 8.sp, Color.White, Modifier.padding(top = 8.dp), align = TextAlign.Center)
                Lbl("${m.illum}% lit")
            } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GridIcon(PixelIcons.moonGrid(m.phase), 72.dp)
                Spacer(Modifier.width(14.dp))
                Kv(listOf("Phase" to m.name, "Illumination" to "${m.illum}%", "Next full moon" to if (m.nextFull == 0) "Tonight" else "in ${m.nextFull} days"), 1)
            }
        }
        "radar" -> RadarCard(b.place)
    }
}

@Composable
private fun AlertsCard(b: WeatherBundle, sz: String) {
    val a = Derive.allAlerts(b)
    if (a.isEmpty()) {
        T("✓ All clear — no alerts${if (b.place.cc == "CA" || b.place.cc == "US") "" else " (official alerts: Canada & US)"}.", 9.sp, Color(0xFFDDDDDD), lh = 16.sp, family = androidx.compose.ui.text.font.FontFamily.Default)
        return
    }
    val list = if (sz == "W") a else a.take(1)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEachIndexed { i, x ->
            val col = when (x.type) { "warning" -> C.red; "watch" -> C.orange; "custom" -> C.purple; else -> C.yellow }
            Row(Modifier.fillMaxWidth().background(C.panel2).clickable { AppModel.screen = Screen.Alerts(i) }) {
                Box(Modifier.width(6.dp).height(if (sz == "T") 30.dp else 44.dp).background(col))
                Column(Modifier.padding(8.dp)) {
                    T(x.title + if (sz != "W" && a.size > 1) "  +${a.size - 1} more" else "", 8.sp, Color.White, lines = 1)
                    if (sz != "T") T(x.name, 7.sp, C.dim, Modifier.padding(top = 4.dp), lines = 2)
                }
            }
        }
    }
}

@Composable
private fun HourStrip(w: Weather, idx: List<Int>, modifier: Modifier = Modifier) {
    val H = w.hourly; val first = w.i0()
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(idx) { i ->
            Column(Modifier.width(50.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                T(Fmt.t(H.temp.at(i)), 9.sp)
                PixelIcon(H.code.at(i), H.isDay.at(i) == 1, 28.dp)
                T(if (H.pop.at(i) > 0) "${H.pop.at(i)}%" else " ", 7.sp, C.cyan)
                T(if (i == first) "Now" else Fmt.clock(H.time[i]), 7.sp, if (i == first) C.orange else C.dim)
            }
        }
    }
}

@Composable
private fun HourlyCard(w: Weather, sz: String) {
    if (sz != "L") { HourStrip(w, Derive.hoursAhead(w, 24), Modifier.fillMaxSize()); return }
    Column(Modifier.fillMaxSize()) {
        TempChart(w, Derive.hoursAhead(w, 24), Modifier.weight(1f))
        Axis("Now", "+6h", "+12h", "+18h", "+24h")
        HourStrip(w, Derive.hoursAhead(w, 48), Modifier.weight(1f).padding(top = 8.dp))
    }
}

@Composable
fun TempChart(w: Weather, idx: List<Int>, modifier: Modifier = Modifier) {
    val t = idx.map { w.hourly.temp.at(it) }
    val p = idx.map { w.hourly.pop.at(it) }
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxSize()) {
            if (t.isEmpty()) return@Canvas
            val lo = t.min(); val hi = t.max(); val span = max(1.0, hi - lo)
            val bw = size.width / t.size
            p.forEachIndexed { k, v -> if (v > 0) drawRect(C.cyan.copy(alpha = 0.5f), Offset(k * bw, size.height - v / 100f * size.height * 0.3f), Size(bw - 1, v / 100f * size.height * 0.3f)) }
            t.forEachIndexed { k, v ->
                val y = 14.dp.toPx() + ((1 - (v - lo) / span) * (size.height - 40.dp.toPx())).toFloat()
                drawRect(C.orange, Offset(k * bw, y), Size(bw, 4.dp.toPx()))
            }
        }
        // labels every 6 hours
        Row(Modifier.fillMaxWidth()) {
            t.chunked(6).forEach { ch -> T(Fmt.t(ch.first()), 7.sp, Color.White, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DailyCard(w: Weather, sz: String) {
    val D = w.daily; val ds = Derive.days(w)
    if (sz == "W") {
        LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ds) { k ->
                Column(
                    Modifier.width(56.dp).fillMaxHeight().background(C.panel2, RoundedCornerShape(28.dp)).border(3.dp, C.edge, RoundedCornerShape(28.dp))
                        .clickable { AppModel.screen = Screen.Day(k) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    T(Fmt.t(D.tmax.at(k)), 9.sp); T(Fmt.t(D.tmin.at(k)), 8.sp, C.dim)
                    PixelIcon(D.code.at(k), true, 26.dp)
                    T(if (D.popMax.at(k) > 0) "${D.popMax.at(k)}%" else " ", 7.sp, C.cyan)
                    T(if (k == w.d0()) "Today" else Fmt.dow(D.time[k]), 7.sp)
                    T(D.time[k].substring(5), 6.sp, C.dim)
                }
            }
        }
        return
    }
    val lo = ds.minOf { D.tmin.at(it) }; val hi = ds.maxOf { D.tmax.at(it) }; val span = max(1.0, hi - lo)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        ds.forEach { k ->
            Row(Modifier.fillMaxWidth().clickable { AppModel.screen = Screen.Day(k) }.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (k == w.d0()) "Today" else Fmt.dow(D.time[k]), 8.sp, modifier = Modifier.width(46.dp))
                PixelIcon(D.code.at(k), true, 22.dp)
                T(if (D.popMax.at(k) > 0) "${D.popMax.at(k)}%" else "", 7.sp, C.cyan, Modifier.width(34.dp).padding(start = 4.dp))
                T(Fmt.t(D.tmin.at(k)), 8.sp, C.dim, Modifier.width(34.dp), align = TextAlign.End)
                Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 6.dp)) {
                    drawRect(Color(0xFF101024))
                    val a = ((D.tmin.at(k) - lo) / span * size.width).toFloat()
                    val bb = ((D.tmax.at(k) - lo) / span * size.width).toFloat()
                    val segs = max(1, ((bb - a) / 6.dp.toPx()).toInt())
                    for (s in 0 until segs) {
                        val f = s.toFloat() / segs
                        drawRect(if (f < 0.33f) C.cyan else if (f < 0.66f) C.yellow else C.orange, Offset(a + (bb - a) * s / segs, 0f), Size((bb - a) / segs, size.height))
                    }
                    if (k == w.d0()) {
                        val x = ((w.current.temp - lo) / span * size.width).toFloat()
                        drawRect(Color.White, Offset(x - 2, -2.dp.toPx()), Size(4.dp.toPx(), size.height + 4.dp.toPx()))
                    }
                }
                T(Fmt.t(D.tmax.at(k)), 8.sp, modifier = Modifier.width(34.dp), align = TextAlign.End)
            }
        }
    }
}

@Composable
private fun PrecipCard(w: Weather, sz: String) {
    val D = w.daily; val d = w.d0()
    val sum = D.precipSum.at(d).let { if (it.isNaN()) 0.0 else it }
    val snow = D.snowSum.at(d).let { if (it.isNaN()) 0.0 else it }
    val kind = if (snow > 0 && snow * 10 >= D.rainSum.at(d).let { if (it.isNaN()) 0.0 else it }) "snow" else "rain"
    Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
        Big((if (sum > 0 && sum < 0.1) "<" else "") + Fmt.p(sum), Fmt.pUnit)
        Lbl("Total $kind for the day\n${D.popMax.at(d)}% chance${if (snow > 0) " · ${"%.1f".format(snow)} cm snow" else ""}", modifier = Modifier.padding(top = 8.dp))
        if (sz != "S") {
            Bars(Derive.hoursAhead(w, 24).map { w.hourly.pop.at(it).toDouble() }, 100.0, Modifier.weight(1f).padding(top = 8.dp))
            Axis("Now", "Chance next 24 h", "+24h")
        }
    }
}

@Composable
private fun Compass(deg: Double, speed: String, unit: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2 * 0.86f
            val cx = size.width / 2; val cy = size.height / 2
            for (i in 0 until 16) {
                val a = i * 22.5 * PI / 180; val big = i % 4 == 0
                val s = if (big) 6.dp.toPx() else 4.dp.toPx()
                drawRect(if (big) C.purple else C.edge, Offset(cx + (sin(a) * r).toFloat() - s / 2, cy - (cos(a) * r).toFloat() - s / 2), Size(s, s))
            }
            if (!deg.isNaN()) {
                val to = (deg + 180) % 360 * PI / 180
                val s = 4.dp.toPx()
                var d = -r * 0.75f
                while (d <= r * 0.75f) {
                    drawRect(C.orange, Offset(cx + (sin(to) * d).toFloat() - s / 2, cy - (cos(to) * d).toFloat() - s / 2), Size(s, s))
                    d += s * 1.2f
                }
                val hx = cx + (sin(to) * r * 0.78).toFloat(); val hy = cy - (cos(to) * r * 0.78).toFloat()
                val p = Path().apply {
                    moveTo(hx, hy)
                    lineTo(cx + (sin(to - 0.35) * r * 0.5).toFloat(), cy - (cos(to - 0.35) * r * 0.5).toFloat())
                    lineTo(cx + (sin(to + 0.35) * r * 0.5).toFloat(), cy - (cos(to + 0.35) * r * 0.5).toFloat()); close()
                }
                drawPath(p, C.orange)
            }
        }
        Column(Modifier.background(C.panel).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(speed, 14.sp); T(unit, 6.sp, C.dim, Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun WindCard(w: Weather, sz: String) {
    val c = w.current
    val comp: @Composable (Modifier) -> Unit = { m ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Compass(c.windDir, Fmt.wNum(c.wind), Fmt.windUnit, Modifier.weight(1f).fillMaxWidth())
            Lbl("From ${Fmt.compass(c.windDir)}")
        }
    }
    if (sz == "S") { comp(Modifier.fillMaxSize()); return }
    val d = w.d0()
    Row(Modifier.fillMaxSize()) {
        comp(Modifier.weight(1f).fillMaxHeight())
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            Kv(listOf("Now" to Derive.beaufort(c.wind), "Gusts" to Fmt.w(c.gust), "Today max" to Fmt.w(w.daily.windMax.at(d)),
                "Peak gust" to "${Fmt.w(w.daily.gustMax.at(d))} ${Fmt.compass8(w.daily.windDir.at(d))}"), 1)
        }
    }
}

@Composable
private fun SunCard(w: Weather, sz: String) {
    val D = w.daily; val d = w.d0()
    val rise = D.sunrise.getOrNull(d) ?: return; val set = D.sunset.getOrNull(d) ?: return
    val f = ((w.nowLocal() - naive(rise)).toDouble() / (naive(set) - naive(rise))).toFloat()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val base = size.height * 0.85f; val s = 4.dp.toPx()
                drawRect(C.mute, Offset(0f, base), Size(size.width, 2.dp.toPx()))
                for (i in 0..24) {
                    val t = i / 24f
                    val x = s + t * (size.width - 2 * s); val y = base - sin(t * PI).toFloat() * (base - s * 2)
                    drawRect(if (t <= f) C.orange else C.edge, Offset(x - s / 2, y - s / 2), Size(s, s))
                }
                if (f in 0f..1f) {
                    val x = s + f * (size.width - 2 * s); val y = base - sin(f * PI).toFloat() * (base - s * 2); val z = 12.dp.toPx()
                    drawRect(C.bg, Offset(x - z / 2 - 2, y - z / 2 - 2), Size(z + 4, z + 4))
                    drawRect(Color(0xFFFFC107), Offset(x - z / 2, y - z / 2), Size(z, z))
                }
            }
            if (f !in 0f..1f) T(if (f < 0) "BEFORE DAWN" else "AFTER DUSK", 7.sp, C.purple)
        }
        if (sz == "S") {
            Row(verticalAlignment = Alignment.CenterVertically) { Sym("☼ ", 10.sp); T(Fmt.clock(rise), 8.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Sym("☾ ", 10.sp); T(Fmt.clock(set), 8.sp) }
        } else {
            val len = D.daylight.at(d); val dl = if (d + 1 < D.time.size) ((D.daylight.at(d + 1) - len) / 60).roundToInt() else 0
            Kv(listOf("Sunrise" to Fmt.clock(rise), "Sunset" to Fmt.clock(set), "Daylight" to "${(len / 3600).toInt()}h ${((len % 3600) / 60).roundToInt()}m"), 3)
            Lbl(if (dl == 0) "Same daylight tomorrow" else "${kotlin.math.abs(dl)} min ${if (dl > 0) "more" else "less"} daylight tomorrow", modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun UvCard(w: Weather, sz: String) {
    val v = w.current.uv.let { if (it.isNaN()) 0.0 else it }
    val cols = (0 until 12).map { Color(Derive.uvInfo(it.toDouble()).second) }
    Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
        Big("${v.roundToInt()}")
        Lbl(Derive.uvInfo(v).first)
        Spacer(Modifier.height(8.dp))
        SegBar(0, 12, cols, mark = min(11, v.roundToInt()))
        if (sz == "S") Lbl("Max today ${w.daily.uvMax.at(w.d0()).let { if (it.isNaN()) 0 else it.roundToInt() }}", modifier = Modifier.padding(top = 8.dp))
        else {
            val idx = Derive.dayHours(w, w.d0()).filter { w.hourly.time[it].substring(11, 13).toInt() in 6..20 }
            if (idx.isNotEmpty()) {
                val peak = idx.maxBy { w.hourly.uv.at(it).let { u -> if (u.isNaN()) 0.0 else u } }
                Bars(idx.map { w.hourly.uv.at(it) }, 11.0, Modifier.weight(1f).padding(top = 8.dp))
                Axis("6:00", "Peak ${w.hourly.uv.at(peak).roundToInt()} at ${Fmt.clock(w.hourly.time[peak])}", "20:00")
            }
        }
    }
}

@Composable
private fun AqiCard(b: WeatherBundle, sz: String) {
    val a = Derive.aqInfo(b.air)
    if (a == null) { Lbl("Air quality data unavailable"); return }
    Column(Modifier.fillMaxSize(), verticalArrangement = if (sz == "S") Arrangement.Center else Arrangement.Top) {
        Big(a.value, a.scale)
        Spacer(Modifier.height(8.dp))
        SegBar(a.max, a.max, a.colors.map { Color(it) }, mark = a.n - 1)
        Lbl(a.label, modifier = Modifier.padding(top = 8.dp))
        if (sz != "S") {
            val air = b.air!!
            Spacer(Modifier.weight(1f))
            fun r(v: Double) = if (v.isNaN()) "--" else "${v.roundToInt()}"
            Kv(listOf("PM2.5" to r(air.pm25), "PM10" to r(air.pm10), "O₃" to r(air.o3), "NO₂" to r(air.no2), "SO₂" to r(air.so2), "CO" to r(air.co)), 3)
            Axis("µg/m³", "US AQI ${r(air.usAqi)}")
        }
    }
}

@Composable
private fun PressureCard(w: Weather, sz: String) {
    val p = w.current.pressure
    val f = ((p - 960) / 90).coerceIn(0.0, 1.0).toFloat()
    val gauge: @Composable (Modifier) -> Unit = { m ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = min(size.width, size.height) / 2 * 0.86f; val s = 6.dp.toPx()
                    val cx = size.width / 2; val cy = size.height / 2
                    val n = 22
                    for (i in 0 until n) {
                        val a = (135 + i * 270.0 / (n - 1)) * PI / 180
                        drawRect(if (i / (n - 1f) <= f) C.purple else Color(0xFF2C2C48), Offset(cx + (cos(a) * r).toFloat() - s / 2, cy + (sin(a) * r).toFloat() - s / 2), Size(s, s))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T(Fmt.pr(p), 11.sp); T(Prefs.units.pressure, 6.sp, C.dim, Modifier.padding(top = 4.dp)) }
            }
            Lbl(Derive.pressureTrend(w))
        }
    }
    if (sz == "S") { gauge(Modifier.fillMaxSize()); return }
    val i0 = w.i0()
    val idx = (max(0, i0 - 24)..min(w.hourly.time.size - 1, i0 + 24)).toList()
    val vals = idx.map { w.hourly.pressure.at(it) }.filter { !it.isNaN() }
    Row(Modifier.fillMaxSize()) {
        gauge(Modifier.weight(0.9f).fillMaxHeight())
        Column(Modifier.weight(1.1f).fillMaxHeight().padding(start = 8.dp)) {
            if (vals.isNotEmpty()) {
                val lo = vals.min() - 1
                Bars(vals.map { it - lo }, vals.max() - lo, Modifier.weight(1f), C.purple)
            }
            Axis("-24h", "now", "+24h")
        }
    }
}
