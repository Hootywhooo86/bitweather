package com.hootywhooo86.bitweather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.pixel.SceneArt
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

val WIDGET_TYPES = listOf("now" to "Now", "hourly" to "Hourly", "daily" to "10-day", "details" to "Details")

/** Widget sizes in dp: Small / Medium / Large. */
fun widgetSize(type: String, size: String): Pair<Int, Int> = when (type) {
    "now" -> mapOf("S" to (200 to 112), "M" to (292 to 168), "L" to (312 to 340))
    "hourly" -> mapOf("S" to (264 to 118), "M" to (360 to 132), "L" to (420 to 214))
    "daily" -> mapOf("S" to (250 to 196), "M" to (300 to 300), "L" to (330 to 404))
    else -> mapOf("S" to (210 to 140), "M" to (320 to 204), "L" to (420 to 270))
}[size] ?: (292 to 168)

/** One desktop widget: a borderless window you can drag anywhere, kept out of the taskbar. Right-click for options. */
@Composable
fun DesktopWidget(w: DeskWidget) {
    val (wd, ht) = widgetSize(w.type, w.size)
    val state = rememberDialogState(position = WindowPosition(w.x.dp, w.y.dp), size = DpSize(wd.dp, ht.dp))
    LaunchedEffect(w.size) { state.size = DpSize(wd.dp, ht.dp) }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { p ->
            // look the widget up by id: resizing replaces the list entry with a copy
            if (p is WindowPosition.Absolute) Model.widgets.firstOrNull { it.id == w.id }?.let { cur ->
                cur.x = p.x.value.roundToInt(); cur.y = p.y.value.roundToInt(); Model.save()
            }
        }
    }
    DialogWindow(
        onCloseRequest = { Model.widgets.remove(w); Model.save() }, state = state, title = "Bitweather",
        undecorated = true, transparent = true, resizable = false,
    ) {
        LaunchedEffect(w.onTop) { window.isAlwaysOnTop = w.onTop }
        fun resize(s: String) { val i = Model.widgets.indexOf(w); if (i >= 0) { Model.widgets[i] = w.copy(size = s); Model.save() } }
        ContextMenuArea(items = {
            listOf(
                ContextMenuItem("Small") { resize("S") },
                ContextMenuItem("Medium") { resize("M") },
                ContextMenuItem("Large") { resize("L") },
                ContextMenuItem(if (w.onTop) "Don't keep on top" else "Keep on top") {
                    val i = Model.widgets.indexOf(w); if (i >= 0) { Model.widgets[i] = w.copy(onTop = !w.onTop); Model.save() }
                },
                ContextMenuItem("Open Bitweather") { Model.mainOpen = true },
                ContextMenuItem("Remove widget") { Model.widgets.remove(w); Model.save() },
            )
        }) {
            WindowDraggableArea { WidgetFace(w.type, w.size, Model.cached(Model.home().id)) }
        }
    }
}

/** The widget's pixel-art face (also used for the previews in Settings). */
@Composable
fun WidgetFace(type: String, size: String, b: WeatherBundle?) {
    @Suppress("UNUSED_VARIABLE") val t = Model.tick
    val (wd, ht) = widgetSize(type, size)
    Box(Modifier.size(wd.dp, ht.dp).panel().clip(Corners)) {
        if (b == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                T("BITWEATHER", 15.sp, C.purple, bold = true); T("Loading weather…", 12.sp, C.dim)
            }
            return@Box
        }
        when (type) {
            "now" -> NowFace(b, size, wd, ht)
            "hourly" -> HourlyFace(b, size)
            "daily" -> DailyFace(b)
            else -> DetailsFace(b)
        }
    }
}

@Composable
private fun Header(b: WeatherBundle, title: String, color: Color = C.orange, shadow: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (b.place.gps) { Glyph("pin", 10.dp, C.orange); Spacer(Modifier.width(5.dp)) }
        T(title, 12.sp, color, Modifier.weight(1f), lines = 1, shadow = shadow, bold = true)
        T(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.fetchedAt)), 11.sp, C.dim, shadow = shadow)
    }
}

@Composable
private fun NowFace(b: WeatherBundle, size: String, wd: Int, ht: Int) {
    val w = b.weather; val c = w.current; val d = w.d0()
    // the 128-pixel landscape behind everything (2 dp per art pixel)
    Scene(SceneArt.specFor(w), wd / 2 + 1, ht / 2 + 1, 2)
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Header(b, b.place.name.uppercase(), C.ink, shadow = true)
        val alerts = Derive.allAlerts(b)
        if (alerts.isNotEmpty() && size != "S") {
            val a = alerts[0]
            val col = when (a.type) { "warning" -> C.red; "watch" -> C.orange; "custom" -> C.purple; else -> C.yellow }
            val blink = (a.type == "warning" || a.type == "watch") && Model.animate && Model.frame % 8 >= 4
            Row(Modifier.padding(top = 4.dp).fillMaxWidth().background(Color(0xCC0D0D1A)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 18.dp).background(if (blink) col.copy(alpha = 0.3f) else col))
                T("  " + a.title + if (alerts.size > 1) "  +${alerts.size - 1}" else "", 11.sp, C.ink, lines = 1)
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    T(Fmt.t(c.temp).removeSuffix("°"), if (size == "S") 30.sp else 40.sp, C.ink, shadow = true, bold = true)
                    Glyph("degree", if (size == "S") 8.dp else 10.dp, C.ink, Modifier.padding(start = 3.dp, top = 8.dp))
                }
                T(Wmo.name(c.code), 13.sp, C.ink, shadow = true, lines = 1)
                if (size != "S") T("Feels ${Fmt.t(c.feels)} · H ${Fmt.t(w.daily.tmax.at(d))} L ${Fmt.t(w.daily.tmin.at(d))}", 11.sp, C.ink, shadow = true, lines = 1)
            }
            PixelIcon(c.code, c.isDay, if (size == "S") 1 else 2)
        }
        if (size == "L") {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xA60D0D1A)).padding(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Derive.hoursAhead(w, 6).forEachIndexed { k, i -> HourCell(w, i, k == 0) }
                }
                Spacer(Modifier.height(4.dp))
                Derive.days(w, 3).forEach { k -> DayRow(w, k, compact = true) }
            }
        }
    }
}

@Composable
fun HourCell(w: com.hootywhooo86.bitweather.data.Weather, i: Int, now: Boolean) {
    val H = w.hourly
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(Fmt.t(H.temp.at(i)), 12.sp, C.ink)
        PixelIcon(H.code.at(i), H.isDay.at(i) == 1, 1)
        T(if (H.pop.at(i) > 0) "${H.pop.at(i)}%" else " ", 10.sp, C.cyan)
        T(if (now) "Now" else Fmt.clock(H.time[i]), 10.sp, if (now) C.orange else C.dim)
    }
}

@Composable
fun DayRow(w: com.hootywhooo86.bitweather.data.Weather, k: Int, compact: Boolean = false) {
    val D = w.daily
    val ds = Derive.days(w)
    val lo = ds.minOf { D.tmin.at(it) }; val hi = ds.maxOf { D.tmax.at(it) }; val span = max(1.0, hi - lo)
    Row(Modifier.fillMaxWidth().height(if (compact) 30.dp else 34.dp), verticalAlignment = Alignment.CenterVertically) {
        T(if (k == w.d0()) "Today" else Fmt.dow(D.time[k]), 12.sp, C.ink, Modifier.width(48.dp))
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { PixelIcon(D.code.at(k), true, 1) }
        T(if (D.popMax.at(k) > 0) "${D.popMax.at(k)}%" else "", 10.sp, C.cyan, Modifier.width(34.dp).padding(start = 4.dp))
        T(Fmt.t(D.tmin.at(k)), 12.sp, Color(0xFFC5CAE9), Modifier.width(34.dp))
        Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 4.dp)) {
            drawRect(Color(0xFF101024))
            val a = ((D.tmin.at(k) - lo) / span * size.width).toFloat()
            val e = ((D.tmax.at(k) - lo) / span * size.width).toFloat()
            val segs = max(1, ((e - a) / 4).toInt())
            for (s in 0 until segs) {
                val f = s.toFloat() / segs
                drawRect(if (f < 0.33f) C.cyan else if (f < 0.66f) C.yellow else C.orange, Offset(a + (e - a) * s / segs, 0f), Size((e - a) / segs, size.height))
            }
        }
        T(Fmt.t(D.tmax.at(k)), 12.sp, C.ink, Modifier.width(30.dp))
    }
}

@Composable
private fun HourlyFace(b: WeatherBundle, size: String) {
    val w = b.weather
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Header(b, "HOURLY · " + b.place.name.uppercase())
        if (size == "L") TempLine(w, Modifier.fillMaxWidth().height(70.dp).padding(vertical = 6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val n = (maxWidth.value / 52).toInt().coerceIn(3, 12)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Derive.hoursAhead(w, n).forEachIndexed { k, i -> HourCell(w, i, k == 0) }
            }
        }
    }
}

@Composable
fun TempLine(w: com.hootywhooo86.bitweather.data.Weather, modifier: Modifier) {
    val idx = Derive.hoursAhead(w, 24)
    val t = idx.map { w.hourly.temp.at(it) }
    val p = idx.map { w.hourly.pop.at(it) }
    Canvas(modifier) {
        if (t.isEmpty()) return@Canvas
        val lo = t.min(); val hi = t.max(); val span = max(1.0, hi - lo)
        val bw = size.width / t.size
        p.forEachIndexed { k, v -> if (v > 0) drawRect(C.cyan.copy(alpha = 0.5f), Offset(k * bw, size.height - v / 100f * size.height * 0.35f), Size(bw - 1, v / 100f * size.height * 0.35f)) }
        t.forEachIndexed { k, v ->
            val y = ((1 - (v - lo) / span) * (size.height - 6)).toFloat()
            drawRect(C.orange, Offset(k * bw, y), Size(bw, 3f))
        }
    }
}

@Composable
private fun DailyFace(b: WeatherBundle) {
    val w = b.weather
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Header(b, "10-DAY · " + b.place.name.uppercase())
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
            val n = (maxHeight.value / 34).toInt().coerceIn(1, 10)
            Column { Derive.days(w, n).forEach { DayRow(w, it) } }
        }
    }
}

@Composable
private fun DetailsFace(b: WeatherBundle) {
    val w = b.weather; val c = w.current; val D = w.daily; val d = w.d0()
    val aq = Derive.aqInfo(b.air)
    val uv = if (c.uv.isNaN()) 0.0 else c.uv
    val tiles = listOf(
        Triple("WIND", "${Fmt.wNum(c.wind)} ${Fmt.windUnit}", "From ${Fmt.compass(c.windDir)} · gust ${Fmt.wNum(c.gust)}"),
        Triple("HUMIDITY", "${c.humidity.roundToInt()}%", "Dew point ${Fmt.t(c.dew)}"),
        Triple("UV INDEX", "${uv.roundToInt()}", Derive.uvInfo(uv).first),
        Triple("AIR", aq?.value ?: "--", aq?.let { "${it.scale} ${it.label.substringBefore(" health")}" } ?: "No data"),
        Triple("SUN", Fmt.clock(D.sunrise.getOrElse(d) { "" }), "Sets ${Fmt.clock(D.sunset.getOrElse(d) { "" })}"),
        Triple("PRESSURE", Fmt.pr(c.pressure), Derive.pressureTrend(w).substringAfter(' ')),
        Triple("PRECIP", "${Fmt.p(D.precipSum.at(d).takeUnless { it.isNaN() } ?: 0.0)}${Fmt.pUnit}", "${D.popMax.at(d)}% today"),
        Triple("VISIBILITY", Fmt.dist(c.visibility).let { "${it.first} ${it.second}" }, Derive.visibilityDesc(c.visibility)),
    )
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Header(b, b.place.name.uppercase())
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 6.dp)) {
            val mw = maxWidth.value
            val cols = max(1, (mw / 100).toInt())
            val rows = max(1, (maxHeight.value / 56).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tiles.take(cols * rows).chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (label, value, sub) ->
                            Column(Modifier.width((mw / cols - 4).dp).height(52.dp).background(C.panel2).padding(5.dp)) {
                                T(label, 10.sp, C.orange, lines = 1)
                                T(value, 16.sp, C.ink, lines = 1, bold = true)
                                T(sub, 10.sp, C.dim, lines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
