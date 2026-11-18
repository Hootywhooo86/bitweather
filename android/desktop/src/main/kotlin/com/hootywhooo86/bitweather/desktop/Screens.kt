package com.hootywhooo86.bitweather.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Api
import com.hootywhooo86.bitweather.data.Config
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.Place
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.pixel.SceneArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun MainScreen(refresh: (Boolean) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = Model.tick
    Column(Modifier.fillMaxSize().background(C.bg)) {
        TopBar(refresh)
        when (Model.screen) {
            "places" -> PlacesScreen(refresh)
            "settings" -> SettingsScreen()
            else -> HomeScreen()
        }
    }
}

@Composable
private fun TopBar(refresh: (Boolean) -> Unit) {
    val p = Model.active(); val b = Model.cached(p.id)
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (Model.screen != "home") PxButton("", glyph = "back") { Model.screen = "home" }
        else PxButton("", glyph = "menu") { Model.screen = "places" }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.gps) { Glyph("pin", 12.dp, C.orange); Spacer(Modifier.width(6.dp)) }
                T(p.name.uppercase(), 18.sp, C.purple, lines = 1, bold = true)
            }
            T(
                when {
                    Model.loading && b == null -> "Loading…"
                    Model.status != null -> Model.status!!
                    b != null -> "${if (p.admin.isNotBlank()) p.admin + " · " else ""}Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.fetchedAt))} · every 10 min"
                    else -> ""
                }, 11.sp, C.dim, lines = 1,
            )
        }
        PxButton("", glyph = "refresh") { refresh(true) }
        PxButton("", glyph = "settings", on = Model.screen == "settings") { Model.screen = if (Model.screen == "settings") "home" else "settings" }
    }
    // retro loading bar
    Box(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 10.dp)) {
        if (Model.loading) {
            val n = 30; val pos = Model.frame % (2 * n - 2); val head = if (pos < n) pos else 2 * n - 2 - pos
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until n) Box(Modifier.weight(1f).height(4.dp).background(if (i == head) C.orange else if (kotlin.math.abs(i - head) == 1) C.purple else C.edge))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen() {
    val b = Model.cached(Model.active().id)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (b == null) { T("Reading the skies…", 14.sp, C.dim, Modifier.padding(20.dp)); return@Column }
        val w = b.weather; val c = w.current; val d = w.d0()
        // the 128-pixel landscape with the current temperature over it
        BoxWithConstraints(Modifier.fillMaxWidth().panel().clip(Corners)) {
            val scale = 3
            val sw = (maxWidth.value / scale).toInt() + 1
            Scene(SceneArt.specFor(w), sw, sw * 9 / 16, scale)
            Column(Modifier.padding(16.dp)) {
                Row {
                    T(Fmt.t(c.temp).removeSuffix("°"), 56.sp, C.ink, shadow = true, bold = true)
                    Glyph("degree", 14.dp, C.ink, Modifier.padding(start = 4.dp, top = 14.dp))
                }
                T(Wmo.name(c.code), 18.sp, C.ink, shadow = true)
                T("Feels like ${Fmt.t(c.feels)}  ·  High ${Fmt.t(w.daily.tmax.at(d))}  Low ${Fmt.t(w.daily.tmin.at(d))}", 13.sp, C.ink, shadow = true)
            }
        }
        val alerts = Derive.allAlerts(b)
        Card("alerts", "ALERTS") {
            if (alerts.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Glyph("check", 12.dp, C.green); T("  All clear — no alerts.", 13.sp) }
            alerts.forEach { a ->
                val col = when (a.type) { "warning" -> C.red; "watch" -> C.orange; "custom" -> C.purple; else -> C.yellow }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(C.panel2)) {
                    Box(Modifier.width(5.dp).height(40.dp).background(col))
                    Column(Modifier.padding(6.dp)) { T(a.title, 13.sp, bold = true); T(a.name, 11.sp, C.dim, lines = 2) }
                }
            }
        }
        Card("brief", "WEATHER BRIEF") { T(Derive.brief(w), 13.sp); Derive.briefExtra(w).forEach { T(it, 12.sp, C.dim) } }
        Card("hourly", "HOURLY FORECAST") {
            TempLine(w, Modifier.fillMaxWidth().height(60.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Derive.hoursAhead(w, 8).forEachIndexed { k, i -> HourCell(w, i, k == 0) }
            }
        }
        Card("daily", "10-DAY FORECAST") { Derive.days(w).forEach { DayRow(w, it) } }
        Card("details", "DETAILS") {
            val aq = Derive.aqInfo(b.air)
            val uv = if (c.uv.isNaN()) 0.0 else c.uv
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((k, v) in listOf(
                    "Wind" to "${Fmt.w(c.wind)} ${Fmt.compass8(c.windDir)}", "Gusts" to Fmt.w(c.gust),
                    "Humidity" to "${c.humidity.toInt()}%", "Dew point" to Fmt.t(c.dew),
                    "UV index" to "${uv.toInt()} ${Derive.uvInfo(uv).first}", "Air quality" to (aq?.let { "${it.value} ${it.scale}" } ?: "--"),
                    "Pressure" to "${Fmt.pr(c.pressure)} ${Config.units.pressure}", "Visibility" to Fmt.dist(c.visibility).let { "${it.first} ${it.second}" },
                    "Sunrise" to Fmt.clock(w.daily.sunrise.getOrElse(d) { "" }), "Sunset" to Fmt.clock(w.daily.sunset.getOrElse(d) { "" }),
                )) {
                    Column(Modifier.width(128.dp).background(C.panel2).padding(6.dp)) { T(k, 11.sp, C.dim); T(v, 14.sp, bold = true) }
                }
            }
        }
        Card("moon", "MOON") {
            val m = Derive.moon()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonIcon(m.phase, 2)
                Column(Modifier.padding(start = 12.dp)) { T(m.name, 14.sp, bold = true); T("${m.illum}% lit · full moon ${if (m.nextFull == 0) "tonight" else "in ${m.nextFull} days"}", 12.sp, C.dim) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Card(glyph: String, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().panel().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Glyph(glyph, 12.dp, C.purple); Spacer(Modifier.width(8.dp)); T(title, 12.sp, C.orange, bold = true) }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value, onChange, modifier.panel(C.panel).padding(10.dp),
        textStyle = TextStyle(fontFamily = Pixel, fontSize = 14.sp, color = C.ink), cursorBrush = SolidColor(C.purple), singleLine = true,
        decorationBox = { inner -> Box { if (value.isEmpty()) T(placeholder, 14.sp, C.mute); inner() } },
    )
}

@Composable
private fun PlacesScreen(refresh: (Boolean) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = Model.tick
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        results = withContext(Dispatchers.IO) { runCatching { Api.search(q.trim()) }.getOrDefault(emptyList()) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(q, { q = it }, "Search city or town…", Modifier.fillMaxWidth())
        results.forEach { r ->
            Column(Modifier.fillMaxWidth().panel().clickable {
                var p = Model.places.firstOrNull { !it.gps && Math.abs(it.lat - r.lat) < 0.01 && Math.abs(it.lon - r.lon) < 0.01 }
                if (p == null) { p = r.copy(id = "p" + System.currentTimeMillis().toString(36)); Model.places.add(p) }
                Model.activeId = p.id; Model.save(); Model.screen = "home"; refresh(false)
            }.padding(10.dp)) { T("+ ${r.name}", 14.sp); T(r.subtitle, 11.sp, C.dim) }
        }
        PxButton("USE MY LOCATION", glyph = "pin") {
            scope.launch { Model.followLocation = true; Model.save(); refresh(true); Model.activeId = Model.home().id; Model.save(); Model.screen = "home" }
        }
        T("YOUR PLACES", 12.sp, C.orange, Modifier.padding(top = 8.dp), bold = true)
        Model.places.toList().forEachIndexed { i, p ->
            val cb = Model.cached(p.id)
            Row(Modifier.fillMaxWidth().panel(edge = if (p.id == Model.activeId) C.purple else C.edge).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cb != null) PixelIcon(cb.weather.current.code, cb.weather.current.isDay, 1)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp).clickable { Model.activeId = p.id; Model.save(); Model.screen = "home"; refresh(false) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { if (p.gps) { Glyph("pin", 10.dp, C.orange); Spacer(Modifier.width(5.dp)) }; T(p.name, 14.sp, lines = 1) }
                    T(p.subtitle, 11.sp, C.dim, lines = 1)
                }
                if (cb != null) T(Fmt.t(cb.weather.current.temp), 18.sp, modifier = Modifier.padding(end = 8.dp))
                PxButton("", glyph = "up") { if (i > 0) { val x = Model.places.removeAt(i); Model.places.add(i - 1, x); Model.save(); Model.bump() } }
                Spacer(Modifier.width(4.dp))
                PxButton("", glyph = "down") { if (i < Model.places.size - 1) { val x = Model.places.removeAt(i); Model.places.add(i + 1, x); Model.save(); Model.bump() } }
                Spacer(Modifier.width(4.dp))
                PxButton("", glyph = "close") {
                    if (Model.places.size > 1) { Model.places.removeAt(i); if (Model.activeId == p.id) Model.activeId = Model.places.first().id; Model.save(); Model.bump() }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen() {
    @Suppress("UNUSED_VARIABLE") val t = Model.tick
    val u = Config.units
    @Composable
    fun Section(s: String) = T(s, 12.sp, C.orange, Modifier.padding(top = 14.dp, bottom = 6.dp), bold = true)
    @Composable
    fun opt(get: () -> String, set: (String) -> Unit, vararg choices: Pair<String, String>) =
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (v, label) -> PxButton(label, on = get() == v) { set(v); Model.save(); Model.bump() } }
        }
    @Composable
    fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        PxButton("$label: ${if (get()) "ON" else "OFF"}", on = get()) { set(!get()); Model.save(); Model.bump() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Section("DESKTOP WIDGETS")
        T("Drag widgets anywhere on your desktop. Right-click a widget to resize it, keep it on top, or remove it.", 12.sp, C.dim)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WIDGET_TYPES.forEach { (type, name) ->
                PxButton("+ $name", glyph = "plus") {
                    val n = Model.widgets.size
                    Model.widgets += DeskWidget("w" + System.currentTimeMillis().toString(36), type, "M", 80 + n * 30, 80 + n * 30)
                    Model.save()
                }
            }
        }
        Model.widgets.toList().forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).panel().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(WIDGET_TYPES.first { it.first == w.type }.second, 13.sp, modifier = Modifier.weight(1f))
                for (s in listOf("S", "M", "L")) {
                    PxButton(s, on = w.size == s) { val i = Model.widgets.indexOf(w); if (i >= 0) { Model.widgets[i] = w.copy(size = s); Model.save() } }
                    Spacer(Modifier.width(4.dp))
                }
                PxButton("", glyph = "close") { Model.widgets.remove(w); Model.save() }
            }
        }

        Section("LOCATION")
        toggle("Follow my location", { Model.followLocation }, { Model.followLocation = it })
        T("Uses Windows Location when it's turned on (Settings → Privacy & security → Location), otherwise an approximate location from your internet connection. Updates every 10 minutes.", 11.sp, C.dim, Modifier.padding(top = 6.dp))

        Section("NOTIFICATIONS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            toggle("Weather alerts", { Model.notifyAlerts }, { Model.notifyAlerts = it })
            toggle("Forecast every 6 hours", { Model.notifySummary }, { Model.notifySummary = it })
        }

        Section("WINDOWS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            toggle("Start with Windows", { Model.startWithWindows }, { Model.startWithWindows = it; Model.applyStartup() })
            toggle("Animations", { Model.animate }, { Model.animate = it })
        }

        Section("TEMPERATURE"); opt({ u.temp }, { u.temp = it }, "C" to "°C", "F" to "°F")
        Section("WIND"); opt({ u.wind }, { u.wind = it }, "kmh" to "km/h", "mph" to "mph", "ms" to "m/s", "kn" to "knots")
        Section("PRECIPITATION"); opt({ u.precip }, { u.precip = it }, "mm" to "mm", "in" to "in")
        Section("PRESSURE"); opt({ u.pressure }, { u.pressure = it }, "hPa" to "hPa", "kPa" to "kPa", "inHg" to "inHg")
        Section("DISTANCE"); opt({ u.dist }, { u.dist = it }, "km" to "km", "mi" to "mi")
        Section("CLOCK"); opt({ u.clock }, { u.clock = it }, "24" to "24 h", "12" to "12 h")
        Section("AIR QUALITY"); opt({ Config.aqScale }, { Config.aqScale = it }, "aqhi" to "AQHI (Canada)", "us" to "US AQI")

        Section("APP")
        T("Bitweather ${Model.version}", 12.sp, C.dim)
        Model.updateUrl?.let { url ->
            Spacer(Modifier.height(6.dp))
            PxButton("UPDATE AVAILABLE — DOWNLOAD", on = true) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }
        }
        T("Forecast & air quality: Open-Meteo. Alerts: Environment and Climate Change Canada, US National Weather Service. Places: Photon / OpenStreetMap.", 11.sp, C.dim, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp))
    }
}
