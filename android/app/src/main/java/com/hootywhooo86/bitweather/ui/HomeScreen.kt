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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.hootywhooo86.bitweather.pixel.PixelIcons
import com.hootywhooo86.bitweather.pixel.SceneArt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.LocationHelper
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
            item {
                // retro loading bar while refreshing; the space is kept so the screen doesn't jump
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(10.dp)) {
                    if (AppModel.loading) PixelLoadingBar(Modifier.fillMaxWidth())
                }
            }
            item { Hero(act, place, b) }
            if (Prefs.followGps && !(LocationHelper.hasPermission(ctx) && LocationHelper.hasBackground(ctx))) item { FollowBanner(act) }
            if (b == null) {
                item { T(if (AppModel.loading) "Reading the skies…" else "No weather yet — pull ⟳ to retry", 9.sp, C.dim, Modifier.padding(24.dp)) }
            } else {
                items(rows) { row ->
                    Row(Modifier.padding(horizontal = 12.dp).padding(top = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { slot ->
                            val w = if (slot.size == "S") cell else cell * 2 + gap
                            val h = when (slot.size) { "T" -> cell / 2; "L" -> cell * 2 + gap; else -> cell }
                            WidgetCard(act, slot, b, w, h, visible.indexOf(slot), b.fetchedAt)
                        }
                    }
                }
            }
            if (AppModel.editing) item { Tray(act) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/** Shown until "Allow all the time" is on — without it widgets can't follow you while the app is closed. */
@Composable
private fun FollowBanner(act: MainActivity) {
    val shape = pixelShape(12.dp)
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth()
            .background(C.panel, shape).border(2.dp, C.orange, shape).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("pin", 12.dp, C.orange); Spacer(Modifier.width(8.dp))
            T("WIDGETS CAN'T FOLLOW YOU YET", 8.sp, C.orange)
        }
        T("Allow location \"all the time\" so your widgets update to where you are every 10 minutes, without opening the app.",
            8.sp, C.ink, Modifier.padding(top = 8.dp), lh = 14.sp)
        Spacer(Modifier.height(10.dp))
        PxButton("ALLOW ALL THE TIME", primary = true) { act.requestBackgroundLocation() }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (place.gps) { Glyph("pin", 12.dp, C.orange); Spacer(Modifier.width(6.dp)) }
                T(place.name.uppercase(), 12.sp, C.purple, lines = 1)
            }
            val upd = when {
                AppModel.loading && b == null -> "Loading…"
                b != null -> "${if (place.admin.isNotBlank()) place.admin + " · " else ""}Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.fetchedAt))}${if (b.stale) " (offline)" else " · every 10 min"}"
                else -> ""
            }
            T(upd, 7.sp, C.dim, Modifier.padding(top = 6.dp), lines = 1)
        }
        PxButton("⟳", symbol = true, modifier = Modifier.alpha(if (AppModel.loading) 0.5f else 1f)) { act.refresh(true) }
        PxButton("✎", symbol = true, on = AppModel.editing) { AppModel.editing = !AppModel.editing }
        PxButton("⚙", symbol = true) { AppModel.screen = Screen.Settings }
    }
}


@Composable
private fun Hero(act: MainActivity, place: Place, b: WeatherBundle?) {
    val shape = pixelShape(12.dp)
    var drag = 0f
    // the 128-pixel landscape, re-drawn each animation frame (rain, snow, drifting clouds, smoke, lightning)
    val spec = remember(b?.fetchedAt) { b?.weather?.let { SceneArt.specFor(it) } ?: SceneArt.Spec("partly", true, 0.4, 0f, false, 0.3) }
    val frame = AppModel.frame
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .background(Color.Black, shape).padding(bottom = 2.dp)
            .background(C.panel, shape).clip(shape).border(2.dp, C.edge, shape)
            .pointerInput(Prefs.places.size) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) > 80) step(act, if (drag < 0) 1 else -1) },
                ) { _, d -> drag += d }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(128f / 72f)) {
            // about 128 art pixels across, each drawn as exactly k x k screen pixels
            val d = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = with(d) { maxWidth.toPx() }
            val k = kotlin.math.max(1, kotlin.math.round(widthPx / 128f).toInt())
            val sw = (widthPx / k).toInt(); val sh = (with(d) { maxHeight.toPx() } / k).toInt() + 1
            val scene = remember(spec, frame, sw, sh) { PixelIcons.scene(sw, sh, spec, frame).asImageBitmap() }
            Image(scene, null, Modifier.size(with(d) { (sw * k).toDp() }, with(d) { (sh * k).toDp() }),
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            if (b != null) {
                val c = b.weather.current
                Column(Modifier.padding(start = 14.dp, top = 10.dp)) {
                    RollingTemp(c.temp, 40.sp, Color.White)
                    T(Wmo.name(c.code), 12.sp, Color.White, shadow = true)
                }
            } else {
                T("Reading the skies…", 10.sp, Color.White, Modifier.align(Alignment.Center), shadow = true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (b != null) {
                val w = b.weather; val c = w.current; val d = w.d0()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    T("Feels like ${Fmt.t(c.feels)}", 9.sp, C.ink)
                    T("H ${Fmt.t(w.daily.tmax.at(d))}  L ${Fmt.t(w.daily.tmin.at(d))}", 9.sp, C.ink)
                }
            }
            if (Prefs.places.size > 1) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("left", 7.dp, C.dim)
                    Prefs.places.forEach { p ->
                        val on = p.id == place.id
                        Box(
                            Modifier.size(6.dp).then(
                                if (p.gps && !on) Modifier.border(1.dp, C.dim)
                                else Modifier.background(if (on) C.ink else C.mute),
                            ),
                        )
                    }
                    Glyph("right", 7.dp, C.dim)
                }
            }
        }
    }
}

private fun step(act: MainActivity, dir: Int) {
    val n = Prefs.places.size
    if (n < 2) return
    val i = Prefs.places.indexOfFirst { it.id == Prefs.activeId }
    Prefs.activeId = Prefs.places[(i + dir + n) % n].id
    AppModel.wipe++
    act.changed(refetch = true)
}

@Composable
private fun WidgetCard(act: MainActivity, slot: Slot, b: WeatherBundle, w: Dp, h: Dp, index: Int, refreshKey: Long) {
    val shape = pixelShape(16.dp)
    // cards drop in one after another (on open and after each refresh), in chunky steps
    val shown = if (!animateFx) 1f else {
        val a = remember(refreshKey) { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(refreshKey) {
            kotlinx.coroutines.delay((index * 45L).coerceAtMost(500L))
            a.animateTo(1f, androidx.compose.animation.core.tween(240))
        }
        kotlin.math.floor(a.value * 4f) / 4f
    }
    Box(Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 24.dp.toPx() }
        .width(w).height(h).background(Color.Black, shape).padding(bottom = 2.dp).background(C.panel, shape).border(2.dp, C.edge, shape)) {
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
    Column(Modifier.padding(12.dp).fillMaxWidth().border(2.dp, C.edge, pixelShape(12.dp)).padding(12.dp)) {
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
