package com.hootywhooo86.bitweather.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hootywhooo86.bitweather.data.Api
import com.hootywhooo86.bitweather.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Rain radar: OpenStreetMap base (darkened) + RainViewer frames, composed into bitmaps. */
object Radar {
    class Frame(val time: Long, val image: ImageBitmap)

    private var cache: Triple<String, Long, List<Frame>>? = null
    private const val Z = 6

    suspend fun load(p: Place): List<Frame> = withContext(Dispatchers.IO) {
        cache?.let { (id, t, f) -> if (id == p.id && System.currentTimeMillis() - t < 10 * 60_000) return@withContext f }
        val meta = JSONObject(Api.get("https://api.rainviewer.com/public/weather-maps.json"))
        val host = meta.getString("host")
        val past = meta.getJSONObject("radar").getJSONArray("past")
        val frames = (maxOf(0, past.length() - 6) until past.length()).map { past.getJSONObject(it) }
        val n = 1 shl Z
        val latR = p.lat * PI / 180
        val xt = (p.lon + 180) / 360 * n
        val yt = (1 - ln(tan(latR) + 1 / cos(latR)) / PI) / 2 * n
        val x0 = floor(xt).toInt() - 1; val y0 = floor(yt).toInt() - 1
        val px = ((xt - x0) * 256).toInt(); val py = ((yt - y0) * 256).toInt()

        coroutineScope {
            // fetch base tiles once
            val base = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
            val bc = Canvas(base)
            val dark = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    -0.55f, 0f, 0f, 0f, 150f,
                    0f, -0.55f, 0f, 0f, 155f,
                    0f, 0f, -0.5f, 0f, 175f,
                    0f, 0f, 0f, 1f, 0f,
                )))
            }
            val baseTiles = (0..8).map { k ->
                async {
                    val dx = k % 3; val dy = k / 3
                    val tx = ((x0 + dx) % n + n) % n; val ty = y0 + dy
                    if (ty !in 0 until n) null else runCatching {
                        val b = Api.bytes("https://tile.openstreetmap.org/$Z/$tx/$ty.png")
                        Triple(dx, dy, BitmapFactory.decodeByteArray(b, 0, b.size))
                    }.getOrNull()
                }
            }.awaitAll()
            baseTiles.filterNotNull().forEach { (dx, dy, bmp) -> bc.drawBitmap(bmp, dx * 256f, dy * 256f, dark) }

            val radarPaint = Paint().apply { alpha = 210 }
            val result = frames.map { f ->
                async {
                    val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
                    val c = Canvas(bmp)
                    (0..8).map { k ->
                        async {
                            val dx = k % 3; val dy = k / 3
                            val tx = ((x0 + dx) % n + n) % n; val ty = y0 + dy
                            if (ty !in 0 until n) null else runCatching {
                                val b = Api.bytes("$host${f.getString("path")}/256/$Z/$tx/$ty/2/1_1.png")
                                Triple(dx, dy, BitmapFactory.decodeByteArray(b, 0, b.size))
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull().forEach { (dx, dy, t) -> c.drawBitmap(t, dx * 256f, dy * 256f, radarPaint) }
                    // crop 512x512 around the place, with a pixel pin
                    val cx = (px - 256).coerceIn(0, 256); val cy = (py - 256).coerceIn(0, 256)
                    val crop = Bitmap.createBitmap(bmp, cx, cy, 512, 512)
                    val cc = Canvas(crop)
                    val pin = Paint()
                    val ppx = (px - cx).toFloat(); val ppy = (py - cy).toFloat()
                    pin.color = 0xFF0D0D1A.toInt(); cc.drawRect(ppx - 9, ppy - 9, ppx + 9, ppy + 9, pin)
                    pin.color = 0xFFFF9F1C.toInt(); cc.drawRect(ppx - 6, ppy - 6, ppx + 6, ppy + 6, pin)
                    Frame(f.getLong("time") * 1000, crop.asImageBitmap())
                }
            }.awaitAll()
            cache = Triple(p.id, System.currentTimeMillis(), result)
            result
        }
    }
}

@Composable
fun RadarCard(place: Place) {
    var frames by remember(place.id) { mutableStateOf<List<Radar.Frame>>(emptyList()) }
    var status by remember(place.id) { mutableStateOf("Loading radar…") }
    var idx by remember(place.id) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(place.id) {
        runCatching { Radar.load(place) }
            .onSuccess { frames = it; idx = it.lastIndex; if (it.isEmpty()) status = "No radar frames" }
            .onFailure { status = "Radar unavailable offline" }
    }
    LaunchedEffect(playing, frames.size) {
        while (playing && frames.isNotEmpty()) { delay(650); idx = (idx + 1) % frames.size }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color(0xFF101024))) {
        val f = frames.getOrNull(idx)
        if (f != null) Image(f.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (frames.isNotEmpty()) PxButton(if (playing) "❚❚" else "▶", symbol = true) { playing = !playing }
            T(f?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.time)) } ?: status, 7.sp, C.ink,
                Modifier.background(C.bg.copy(alpha = 0.8f)).padding(6.dp))
        }
        T("© OpenStreetMap · RainViewer", 5.sp, C.dim, Modifier.align(Alignment.BottomEnd).background(C.bg.copy(alpha = 0.7f)).padding(3.dp))
    }
}
