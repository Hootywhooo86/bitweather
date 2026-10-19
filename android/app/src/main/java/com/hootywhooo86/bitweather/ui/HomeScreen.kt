package com.hootywhooo86.bitweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.Place
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.data.Slot
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import java.text.DateFormat
import java.util.Date

val SIZE_NAMES = mapOf("T" to "THIN", "S" to "SMALL", "W" to "WIDE", "L" to "LARGE")

@Composable
fun HomeScreen(act: MainActivity) {
    @Suppress("UNUSED_VARIABLE") val tick = AppModel.tick
    val ctx = LocalContext.current
    val place = Prefs.active()
    val b = Repo.cached(ctx, place.id)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 10.dp
        val cell = (maxWidth - 24.dp - gap) / 2
        val visible = Prefs.layout.filter { !it.hidden }
        val rows = pack(visible)
        LazyColumn(Modifier.fillMaxSize()) {
            item { TopBar(act, place, b) }
            item { Hero(act, place, b) }
            if (b == null) {
                item { T(if (AppModel.loading) "Reading the skies…" else "No weather yet — pull ⟳ to retry", 9.sp, C.dim, Modifier.padding(24.dp)) }
            } else {
                items(rows) { row ->
                    Row(Modifier.padding(horizontal = 12.dp).padding(top = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { slot ->
                            val w = if (slot.size == "S") cell else cell * 2 + gap
                            val h = when (slot.size) { "T" -> cell / 2; "L" -> cell * 2 + gap; else -> cell }
                            WidgetCard(act, slot, b, w, h, visible.indexOf(slot))
                        }
                    }
                }
            }
            if (AppModel.editing) item { Tray(act) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/** Two small widgets share a row; the next small one is pulled forward to fill gaps. */
fun pack(slots: List<Slot>): List<List<Slot>> {
    val rest = slots.toMutableList()
    val rows = ArrayList<List<Slot>>()
    while (rest.isNotEmpty()) {
        val s = rest.removeAt(0)
        if (s.size == "S") {
            val j = rest.indexOfFirst { it.size == "S" }
            rows += if (j >= 0) listOf(s, rest.removeAt(j)) else listOf(s)
        } else rows += listOf(s)
    }
    return rows
}

@Composable
private fun TopBar(act: MainActivity, place: Place, b: WeatherBundle?) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PxButton("☰", symbol = true) { AppModel.screen = Screen.Places }
        Column(Modifier.weight(1f)) {
            T((if (place.gps) "◎ " else "") + place.name.uppercase(), 12.sp, C.purple, lines = 1)
            val upd = when {
                AppModel.loading && b == null -> "Loading…"
                b != null -> "${if (place.admin.isNotBlank()) place.admin + " · " else ""}Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.fetchedAt))}${if (b.stale) " (offline)" else " · every 30 min"}"
                else -> ""
            }
            T(upd, 7.sp, C.dim, Modifier.padding(top = 6.dp), lines = 1)
        }
        PxButton("⟳", symbol = true, modifier = Modifier.alpha(if (AppModel.loading) 0.5f else 1f)) { act.refresh(true) }
        PxButton("✎", symbol = true, on = AppModel.editing) { AppModel.editing = !AppModel.editing }
        PxButton("⚙", symbol = true) { AppModel.screen = Screen.Settings }
    }
}

fun skyBands(b: WeatherBundle?): List<Color> {
    val cur = b?.weather?.current
    val k = Wmo.kind(cur?.code ?: 2)
    val l = when {
        cur != null && !cur.isDay -> listOf(0xFF0B0B24, 0xFF141436, 0xFF1D1D4A, 0xFF28285E)
        k == "clear" || k == "mostly" -> listOf(0xFF1E3A8A, 0xFF2850B0, 0xFF3A6AD0, 0xFF5A8AE6)
        k == "partly" -> listOf(0xFF283593, 0xFF3949AB, 0xFF5463B8, 0xFF7280C4)
        k == "thunder" -> listOf(0xFF1A1030, 0xFF2A1A48, 0xFF3A2860, 0xFF4A3878)
        k == "snow" -> listOf(0xFF4A5578, 0xFF5D6890, 0xFF7480A6, 0xFF8E98BA)
        else -> listOf(0xFF2C3350, 0xFF3A4266, 0xFF4A547A, 0xFF5C668E)
    }
    return l.map { Color(it) }
}

@Composable
private fun Hero(act: MainActivity, place: Place, b: WeatherBundle?) {
    val bands = skyBands(b)
    val stops = bands.flatMapIndexed { i, c -> listOf(i / 4f to c, (i + 1) / 4f - 0.0001f to c) }.toTypedArray()
    val shape = RoundedCornerShape(18.dp)
    var drag = 0f
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .background(Brush.verticalGradient(*stops), shape).border(4.dp, C.edge, shape)
            .pointerInput(Prefs.places.size) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) > 80) step(act, if (drag < 0) 1 else -1) },
                ) { _, d -> drag += d }
            }
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (b == null) {
            T("Reading the skies…", 9.sp, Color.White, Modifier.padding(30.dp))
        } else {
            val w = b.weather; val c = w.current; val d = w.d0()
            PixelIcon(c.code, c.isDay, 96.dp)
            T(Fmt.t(c.temp), 42.sp, Color.White, Modifier.padding(top = 4.dp))
            T(Wmo.name(c.code), 11.sp, Color.White, Modifier.padding(top = 12.dp))
            T("Feels like ${Fmt.t(c.feels)}", 9.sp, Color.White.copy(alpha = 0.85f), Modifier.padding(top = 10.dp))
            T("High ${Fmt.t(w.daily.tmax.at(d))} · Low ${Fmt.t(w.daily.tmin.at(d))}", 9.sp, Color.White.copy(alpha = 0.85f), Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Prefs.places.forEach { p ->
                val on = p.id == place.id
                Box(
                    Modifier.size(8.dp).then(
                        if (p.gps && !on) Modifier.border(2.dp, Color.White.copy(alpha = 0.6f))
                        else Modifier.background(if (on) Color.White else Color.White.copy(alpha = 0.35f)),
                    ),
                )
            }
        }
        if (Prefs.places.size > 1) T("◀ swipe for saved places ▶", 7.sp, Color.White.copy(alpha = 0.6f), Modifier.padding(top = 8.dp), align = TextAlign.Center, family = androidx.compose.ui.text.font.FontFamily.Default)
    }
}

private fun step(act: MainActivity, dir: Int) {
    val n = Prefs.places.size
    if (n < 2) return
    val i = Prefs.places.indexOfFirst { it.id == Prefs.activeId }
    Prefs.activeId = Prefs.places[(i + dir + n) % n].id
    act.changed(refetch = true)
}

@Composable
private fun WidgetCard(act: MainActivity, slot: Slot, b: WeatherBundle, w: Dp, h: Dp, index: Int) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.width(w).height(h).background(Color.Black, shape).padding(bottom = 4.dp).background(C.panel, shape).border(4.dp, C.edge, shape)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            CardHead(slot.id)
            Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(act, slot.id, slot.size, b) }
        }
        if (AppModel.editing) {
            Column(
                Modifier.matchParentSize().background(C.bg.copy(alpha = 0.8f), shape).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                T("${CARD_NAMES[slot.id]} · ${SIZE_NAMES[slot.size]}", 7.sp, C.orange, align = TextAlign.Center, lines = 2)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PxButton("◀", symbol = true) { move(act, index, -1) }
                    if ((CARD_SIZES[slot.id]?.size ?: 1) > 1) PxButton("SIZE") {
                        val s = CARD_SIZES[slot.id]!!
                        slot.size = s[(s.indexOf(slot.size) + 1) % s.size]
                        act.changed()
                    }
                    PxButton("▶", symbol = true) { move(act, index, 1) }
                    PxButton("✕", symbol = true, warn = true) { slot.hidden = true; act.changed() }
                }
            }
        }
    }
}

private fun move(act: MainActivity, index: Int, dir: Int) {
    val vis = Prefs.layout.filter { !it.hidden }
    val a = vis.getOrNull(index) ?: return
    val b = vis.getOrNull(index + dir) ?: return
    val i = Prefs.layout.indexOf(a); val j = Prefs.layout.indexOf(b)
    Prefs.layout[i] = b; Prefs.layout[j] = a
    act.changed()
}

@Composable
private fun Tray(act: MainActivity) {
    Column(Modifier.padding(12.dp).fillMaxWidth().border(3.dp, C.edge, RoundedCornerShape(12.dp)).padding(12.dp)) {
        T("+ ADD WIDGETS", 9.sp, C.orange, Modifier.padding(bottom = 12.dp))
        val hidden = Prefs.layout.filter { it.hidden }
        Chips {
            if (hidden.isEmpty()) T("All widgets are on screen.", 8.sp, C.dim)
            hidden.forEach { s ->
                PxButton("+ ${CARD_NAMES[s.id]}") {
                    s.hidden = false; Prefs.layout.remove(s); Prefs.layout.add(s); act.changed()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Chips {
            PxButton("DONE", primary = true) { AppModel.editing = false }
            PxButton("RESET LAYOUT") { Prefs.layout = Prefs.defaultLayout(); act.changed() }
        }
        Note("Tap SIZE to cycle thin / small / wide / large. Arrows reorder. ✕ removes. Home-screen widgets: long-press your home screen → Widgets → Bitweather.")
    }
}
