package com.hootywhooo86.bitweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.BuildConfig
import com.hootywhooo86.bitweather.data.Api
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.LocationHelper
import com.hootywhooo86.bitweather.data.Place
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.data.Updates
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.data.parseInstant
import com.hootywhooo86.bitweather.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
private fun Sheet(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PxButton("←", symbol = true) { AppModel.screen = Screen.Home }
            Spacer(Modifier.width(12.dp))
            T(title, 11.sp, C.purple)
        }
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, number: Boolean = false) {
    BasicTextField(
        value, onChange, modifier.background(C.panel, RoundedCornerShape(8.dp)).border(3.dp, C.edge, RoundedCornerShape(8.dp)).padding(12.dp),
        textStyle = TextStyle(fontFamily = Pixel, fontSize = 10.sp, color = Color.White),
        cursorBrush = SolidColor(C.purple), singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Text) else KeyboardOptions.Default,
        decorationBox = { inner -> Box { if (value.isEmpty()) T(placeholder, 10.sp, C.mute); inner() } },
    )
}

// ---------------------------------------------------------------- places
@Composable
fun PlacesScreen(act: MainActivity) {
    @Suppress("UNUSED_VARIABLE") val tick = AppModel.tick
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350); searching = true
        results = withContext(Dispatchers.IO) { runCatching { Api.search(q.trim()) }.getOrDefault(emptyList()) }
        searching = false
    }
    LaunchedEffect(Unit) {
        // fill in temperatures for places we haven't loaded yet
        Prefs.places.filter { Repo.cached(ctx, it.id) == null }.forEach { runCatching { Repo.fetch(ctx, it) }; AppModel.bump() }
    }
    Sheet("SAVED PLACES") {
        Field(q, { q = it }, "Search city or town…", Modifier.fillMaxWidth())
        if (searching) T("Searching…", 8.sp, C.dim, Modifier.padding(top = 10.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!searching && q.trim().length >= 2 && results.isEmpty()) T("No places found.", 8.sp, C.dim)
            results.forEach { r ->
                Row(Modifier.fillMaxWidth().background(C.panel, RoundedCornerShape(12.dp)).border(3.dp, C.edge, RoundedCornerShape(12.dp))
                    .clickable {
                        var p = Prefs.places.firstOrNull { !it.gps && Math.abs(it.lat - r.lat) < 0.01 && Math.abs(it.lon - r.lon) < 0.01 }
                        if (p == null) { p = r.copy(id = "p" + System.currentTimeMillis().toString(36)); Prefs.places.add(p) }
                        Prefs.activeId = p.id
                        act.changed(refetch = true)
                        AppModel.screen = Screen.Home
                    }.padding(12.dp)) {
                    Column { T("+ ${r.name}", 9.sp); T(r.subtitle, 7.sp, C.dim, Modifier.padding(top = 6.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PxButton(if (locating) "LOCATING…" else "◎ USE MY LOCATION", primary = true) {
            if (!LocationHelper.hasPermission(ctx)) { act.requestLocation(); return@PxButton }
            locating = true
            scope.launch {
                val p = Repo.locate(ctx, select = true)
                locating = false
                if (p == null) AppModel.say("Could not get your location") else { act.changed(refetch = true); AppModel.screen = Screen.Home }
            }
        }
        Section("YOUR PLACES (${Prefs.places.size})")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Prefs.places.forEachIndexed { i, p ->
                val cb = Repo.cached(ctx, p.id)
                val on = p.id == Prefs.activeId
                Row(
                    Modifier.fillMaxWidth().background(C.panel, RoundedCornerShape(12.dp)).border(3.dp, if (on) C.purple else C.edge, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cb != null) PixelIcon(cb.weather.current.code, cb.weather.current.isDay, 36.dp) else Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { Prefs.activeId = p.id; act.changed(refetch = true); AppModel.screen = Screen.Home }) {
                        T((if (p.gps) "◎ " else "") + p.name, 9.sp, lines = 1)
                        val d = cb?.weather?.d0() ?: 0
                        T(p.subtitle + (cb?.let { " · ${Fmt.t(it.weather.daily.tmax.at(d))}/${Fmt.t(it.weather.daily.tmin.at(d))}" } ?: ""), 7.sp, C.dim, Modifier.padding(top = 6.dp), lines = 1)
                    }
                    if (cb != null) T(Fmt.t(cb.weather.current.temp), 13.sp, modifier = Modifier.padding(end = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PxButton("▲", symbol = true, enabled = i > 0) { val x = Prefs.places.removeAt(i); Prefs.places.add(i - 1, x); act.changed() }
                        PxButton("▼", symbol = true, enabled = i < Prefs.places.size - 1) { val x = Prefs.places.removeAt(i); Prefs.places.add(i + 1, x); act.changed() }
                        PxButton("✕", symbol = true, warn = true) {
                            if (Prefs.places.size == 1) { AppModel.say("Keep at least one place."); return@PxButton }
                            Prefs.places.removeAt(i); Repo.drop(ctx, p.id)
                            if (Prefs.activeId == p.id) Prefs.activeId = Prefs.places.first().id
                            act.changed(refetch = true)
                        }
                    }
                }
            }
        }
        Note("Swipe the big weather card on the home screen to flip between places. Home-screen widgets and notifications follow your current location (◎).")
    }
}

// ---------------------------------------------------------------- settings
@Composable
fun SettingsScreen(act: MainActivity) {
    @Suppress("UNUSED_VARIABLE") val tick = AppModel.tick
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = Prefs.units
    @Composable
    fun opt(get: () -> String, set: (String) -> Unit, vararg choices: Pair<String, String>) = Chips {
        choices.forEach { (v, label) -> PxButton(label, on = get() == v) { set(v); act.changed() } }
    }
    Sheet("SETTINGS") {
        Section("TEMPERATURE"); opt({ u.temp }, { u.temp = it }, "C" to "°C", "F" to "°F")
        Section("WIND"); opt({ u.wind }, { u.wind = it }, "kmh" to "km/h", "mph" to "mph", "ms" to "m/s", "kn" to "knots")
        Section("PRECIPITATION"); opt({ u.precip }, { u.precip = it }, "mm" to "mm", "in" to "in")
        Section("PRESSURE"); opt({ u.pressure }, { u.pressure = it }, "hPa" to "hPa", "kPa" to "kPa", "inHg" to "inHg")
        Section("DISTANCE"); opt({ u.dist }, { u.dist = it }, "km" to "km", "mi" to "mi")
        Section("CLOCK"); opt({ u.clock }, { u.clock = it }, "24" to "24 h", "12" to "12 h")
        Section("AIR QUALITY SCALE"); opt({ Prefs.aqScale }, { Prefs.aqScale = it }, "aqhi" to "AQHI (Canada)", "us" to "US AQI")

        Section("CURRENT LOCATION")
        Chips {
            PxButton("◎ Follow my location: ${if (Prefs.followGps) "ON" else "OFF"}", on = Prefs.followGps) {
                Prefs.followGps = !Prefs.followGps; act.changed(); if (Prefs.followGps) act.refresh(true)
            }
        }
        val fg = LocationHelper.hasPermission(ctx); val bg = LocationHelper.hasBackground(ctx)
        T(
            when { !fg -> "Location permission: OFF"; !bg -> "Location: only while using the app"; else -> "Location: all the time ✓" },
            8.sp, if (fg && bg) C.green else C.yellow, Modifier.padding(top = 10.dp, bottom = 10.dp),
            family = androidx.compose.ui.text.font.FontFamily.Default,
        )
        Chips {
            if (!fg) PxButton("ALLOW LOCATION", primary = true) { act.requestLocation() }
            else if (!bg) PxButton("ALLOW ALL THE TIME", primary = true) { act.requestBackgroundLocation() }
            PxButton("BATTERY: LET IT RUN") { act.openBatterySettings() }
        }
        Note("Your location syncs when you open the app and every 15 minutes in the background, so widgets show where you are. Background location needs \"Allow all the time\". If updates stop, set Bitweather's battery use to Unrestricted.")

        Section("NOTIFICATIONS")
        val allowed = Notifier.allowed(ctx)
        T(if (allowed) "✓ Notifications are on" else "Notifications are off", 8.sp, if (allowed) C.green else C.yellow, Modifier.padding(bottom = 10.dp), family = androidx.compose.ui.text.font.FontFamily.Default)
        if (!allowed) Chips { PxButton("TURN ON NOTIFICATIONS", primary = true) { act.requestNotifications() } }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton("! Weather alerts: ${if (Prefs.notifyAlerts) "ON" else "OFF"}", on = Prefs.notifyAlerts) { Prefs.notifyAlerts = !Prefs.notifyAlerts; act.changed() }
            PxButton("Forecast every 6 hours: ${if (Prefs.notifyConditions) "ON" else "OFF"}", on = Prefs.notifyConditions) {
                Prefs.notifyConditions = !Prefs.notifyConditions; act.changed()
                com.hootywhooo86.bitweather.notify.SummaryAlarm.schedule(ctx)
            }
            PxButton("App updates: ${if (Prefs.notifyUpdates) "ON" else "OFF"}", on = Prefs.notifyUpdates) { Prefs.notifyUpdates = !Prefs.notifyUpdates; act.changed() }
        }
        if (allowed) Chips { PxButton("SEND TEST") { Notifier.test(ctx, Repo.cached(ctx, Prefs.home().id)) } }
        Note("Alerts arrive as soon as they're issued: official warnings (Environment Canada / US NWS) plus your custom alerts below, each once. The forecast summary comes every 6 hours. Nothing stays pinned in your notifications.")

        Section("CUSTOM ALERTS (NEXT 48 H, METRIC)")
        val t = Prefs.thresholds
        Threshold("Temp drops to", "°C", t.cold) { t.cold = it; act.changed() }
        Threshold("Temp reaches", "°C", t.hot) { t.hot = it; act.changed() }
        Threshold("Wind gusts over", "km/h", t.gust) { t.gust = it; act.changed() }
        Threshold("Snowfall over", "cm", t.snow) { t.snow = it; act.changed() }
        Note("Leave a box empty to turn that alert off.")

        Section("APP")
        T("Version ${BuildConfig.VERSION_NAME}", 8.sp, C.dim)
        Chips {
            PxButton("CHECK FOR UPDATES") {
                scope.launch {
                    val r = Updates.check(ctx, force = true)
                    if (r == null) AppModel.say("You're on the latest version") else act.openUrl(r.apkUrl ?: r.pageUrl)
                }
            }
            PxButton("APP SETTINGS") { act.openAppSettings() }
        }
        Note("Forecast & air quality: Open-Meteo. Alerts: Environment and Climate Change Canada, US National Weather Service. Places: Photon / OpenStreetMap. Radar: RainViewer. Your location is only used to fetch your forecast.")
    }
}

@Composable
private fun Threshold(label: String, unit: String, value: Double?, set: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 8.sp, C.dim, Modifier.weight(1f))
        Field(text, { v -> text = v.filter { it.isDigit() || it == '-' || it == '.' }; set(text.toDoubleOrNull()) }, "off", Modifier.width(96.dp), number = true)
        T(unit, 8.sp, C.dim, Modifier.width(48.dp).padding(start = 8.dp))
    }
}

// ---------------------------------------------------------------- day detail & alerts
@Composable
fun DayScreen(k: Int) {
    val ctx = LocalContext.current
    val b = Repo.cached(ctx, Prefs.active().id) ?: return Sheet("DAY") { T("No data", 9.sp) }
    val w = b.weather; val D = w.daily
    Sheet("${if (k == w.d0()) "TODAY" else Fmt.dow(D.time[k]).uppercase()} ${D.time[k].substring(5)}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(D.code.at(k), true, 64.dp)
            Column(Modifier.padding(start = 12.dp)) {
                T("${Fmt.t(D.tmax.at(k))} / ${Fmt.t(D.tmin.at(k))}", 20.sp)
                T(Wmo.name(D.code.at(k)), 9.sp, C.dim, Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        val idx = Derive.dayHours(w, k)
        Box(Modifier.fillMaxWidth().height(160.dp).background(C.panel, RoundedCornerShape(16.dp)).border(4.dp, C.edge, RoundedCornerShape(16.dp)).padding(12.dp)) {
            TempChart(w, idx, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(idx.size) { n ->
                val i = idx[n]
                Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(Fmt.t(w.hourly.temp.at(i)), 9.sp)
                    PixelIcon(w.hourly.code.at(i), w.hourly.isDay.at(i) == 1, 28.dp)
                    T(if (w.hourly.pop.at(i) > 0) "${w.hourly.pop.at(i)}%" else " ", 7.sp, C.cyan)
                    T(Fmt.clock(w.hourly.time[i]), 7.sp, C.dim)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val rows = listOf(
            "Precipitation" to "${Fmt.p(D.precipSum.at(k).let { if (it.isNaN()) 0.0 else it })} ${Fmt.pUnit} · ${D.popMax.at(k)}%",
            "Snowfall" to "${"%.1f".format(D.snowSum.at(k).let { if (it.isNaN()) 0.0 else it })} cm",
            "Wind" to "${Fmt.w(D.windMax.at(k))} ${Fmt.compass8(D.windDir.at(k))}",
            "Gusts" to Fmt.w(D.gustMax.at(k)),
            "Feels like" to "${Fmt.t(D.fmax.at(k))} / ${Fmt.t(D.fmin.at(k))}",
            "UV max" to "${D.uvMax.at(k).let { if (it.isNaN()) 0 else it.roundToInt() }} ${Derive.uvInfo(D.uvMax.at(k).let { if (it.isNaN()) 0.0 else it }).first}",
            "Sunrise" to Fmt.clock(D.sunrise.getOrElse(k) { "" }),
            "Sunset" to Fmt.clock(D.sunset.getOrElse(k) { "" }),
        )
        rows.chunked(2).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                r.forEach { (a, v) -> Column(Modifier.weight(1f)) { T(a, 7.sp, C.dim); T(v, 9.sp, modifier = Modifier.padding(top = 4.dp)) } }
            }
        }
    }
}

@Composable
fun AlertsScreen(focus: Int) {
    val ctx = LocalContext.current
    val b = Repo.cached(ctx, Prefs.active().id)
    Sheet("ALERTS") {
        val a = b?.let { Derive.allAlerts(it) } ?: emptyList()
        if (a.isEmpty()) T("All clear.", 9.sp)
        a.forEachIndexed { i, x ->
            val col = when (x.type) { "warning" -> C.red; "watch" -> C.orange; "custom" -> C.purple; else -> C.yellow }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(C.panel2).then(if (i == focus) Modifier.border(2.dp, Color.White) else Modifier)) {
                Box(Modifier.width(6.dp).height(40.dp).background(col))
                Column(Modifier.padding(10.dp)) {
                    T(x.title, 9.sp)
                    val until = parseInstant(x.until)?.let { " · until " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.toEpochMilli())) } ?: ""
                    T(x.name + until, 7.sp, C.dim, Modifier.padding(top = 6.dp), lh = 13.sp)
                    if (x.text.isNotBlank()) T(x.text, 8.sp, Color(0xFFDDDDDD), Modifier.padding(top = 8.dp), lh = 15.sp)
                    x.src?.let { T("Source: $it", 7.sp, C.dim, Modifier.padding(top = 6.dp)) }
                }
            }
        }
        Note("Custom alerts come from your thresholds in Settings.")
    }
}
