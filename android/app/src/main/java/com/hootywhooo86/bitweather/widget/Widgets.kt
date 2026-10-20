package com.hootywhooo86.bitweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.hootywhooo86.bitweather.R
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Fmt
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.pixel.PixelIcons
import com.hootywhooo86.bitweather.ui.MainActivity
import com.hootywhooo86.bitweather.work.UpdateWorker
import java.text.DateFormat
import java.util.Date
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

abstract class BaseWidget : AppWidgetProvider() {
    abstract val kind: String

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Prefs.init(ctx)
        ids.forEach { Widgets.render(ctx, mgr, it, kind) }
        if (!Repo.isFresh(Repo.cached(ctx, Prefs.home().id))) runCatching { UpdateWorker.now(ctx) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        Prefs.init(ctx)
        Widgets.render(ctx, mgr, id, kind)
    }
}

class NowWidget : BaseWidget() { override val kind = "now" }
class HourlyWidget : BaseWidget() { override val kind = "hourly" }
class DailyWidget : BaseWidget() { override val kind = "daily" }
class DetailsWidget : BaseWidget() { override val kind = "details" }

object Widgets {
    private val providers = mapOf(
        "now" to NowWidget::class.java, "hourly" to HourlyWidget::class.java,
        "daily" to DailyWidget::class.java, "details" to DetailsWidget::class.java,
    )
    private val defaults = mapOf("now" to (250 to 110), "hourly" to (250 to 110), "daily" to (250 to 180), "details" to (110 to 110))

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        providers.forEach { (k, cls) ->
            runCatching { mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { render(ctx, mgr, it, k) } }
        }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int, kind: String) {
        val o = mgr.getAppWidgetOptions(id)
        val (dw, dh) = defaults[kind]!!
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: dw
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: dh
        var scale = ctx.resources.displayMetrics.density
        while (wDp * scale * hDp * scale > 1_300_000) scale *= 0.85f
        val bmp = Bitmap.createBitmap(max(1, (wDp * scale).roundToInt()), max(1, (hDp * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val b = Repo.cached(ctx, Prefs.home().id)
        runCatching { WidgetPainter(ctx, c, wDp.toFloat(), hDp.toFloat()).draw(kind, b) }
        val rv = RemoteViews(ctx.packageName, R.layout.widget_image)
        rv.setImageViewBitmap(R.id.img, bmp)
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        rv.setOnClickPendingIntent(R.id.root, pi)
        runCatching { mgr.updateAppWidget(id, rv) }
    }
}

/** Draws the pixel-style widgets in dp coordinates. */
class WidgetPainter(private val ctx: Context, private val c: Canvas, private val W: Float, private val H: Float) {
    companion object {
        const val BG = 0xFF1A1A2E.toInt(); const val EDGE = 0xFF3D3D5C.toInt(); const val PANEL2 = 0xFF24243C.toInt()
        const val INK = 0xFFF5F5F5.toInt(); const val DIM = 0xFF9E9E9E.toInt(); const val ORANGE = 0xFFFF9F1C.toInt()
        const val PURPLE = 0xFFB388FF.toInt(); const val CYAN = 0xFF4FC3F7.toInt(); const val RED = 0xFFFF5252.toInt()
        const val YELLOW = 0xFFFFEB3B.toInt()
        private var font: Typeface? = null
    }

    private val tf: Typeface = font ?: (runCatching { ResourcesCompat.getFont(ctx, R.font.press_start_2p) }.getOrNull() ?: Typeface.MONOSPACE).also { font = it }
    private val tp = Paint().apply { typeface = tf; isAntiAlias = false }
    private val fp = Paint().apply { isAntiAlias = false }
    private val pad = 10f

    // ---------------------------------------------------------------- primitives
    private fun stair(l: Float, t: Float, r: Float, b: Float, u: Float) = Path().apply {
        moveTo(l, t + 2 * u); lineTo(l + u, t + 2 * u); lineTo(l + u, t + u); lineTo(l + 2 * u, t + u); lineTo(l + 2 * u, t)
        lineTo(r - 2 * u, t); lineTo(r - 2 * u, t + u); lineTo(r - u, t + u); lineTo(r - u, t + 2 * u); lineTo(r, t + 2 * u)
        lineTo(r, b - 2 * u); lineTo(r - u, b - 2 * u); lineTo(r - u, b - u); lineTo(r - 2 * u, b - u); lineTo(r - 2 * u, b)
        lineTo(l + 2 * u, b); lineTo(l + 2 * u, b - u); lineTo(l + u, b - u); lineTo(l + u, b - 2 * u); lineTo(l, b - 2 * u); close()
    }

    private fun panel(sky: IntArray? = null) {
        fp.color = EDGE
        c.drawPath(stair(0f, 0f, W, H, 4f), fp)
        val inner = stair(3f, 3f, W - 3f, H - 3f, 3f)
        if (sky == null) {
            fp.color = BG; c.drawPath(inner, fp)
        } else {
            c.save(); c.clipPath(inner)
            val bh = (H - 6f) / sky.size
            sky.forEachIndexed { i, col -> fp.color = col; c.drawRect(3f, 3f + i * bh, W - 3f, 3f + (i + 1) * bh + 1f, fp) }
            c.restore()
        }
    }

    private fun box(l: Float, t: Float, r: Float, b: Float, color: Int) { fp.color = color; c.drawRect(l, t, r, b, fp) }

    private fun width(s: String, size: Float): Float { tp.textSize = size; return tp.measureText(s) }

    /** Draws text with its top edge at [top]. align: -1 left, 0 centre, 1 right. Returns the text width. */
    private fun text(s: String, x: Float, top: Float, size: Float, color: Int, align: Int = -1, maxW: Float = Float.MAX_VALUE, shadow: Boolean = false): Float {
        var str = s
        tp.textSize = size
        if (tp.measureText(str) > maxW) {
            var base = str
            while (base.length > 1 && tp.measureText(base.trimEnd(' ', '·') + "..") > maxW) base = base.dropLast(1)
            str = base.trimEnd(' ', '·') + ".."
        }
        val w = tp.measureText(str)
        val sx = when (align) { 0 -> x - w / 2; 1 -> x - w; else -> x }
        val base = top + size * 0.875f
        if (shadow) { tp.color = 0x73000000; c.drawText(str, sx + size * 0.12f, base + size * 0.12f, tp) }
        tp.color = color
        c.drawText(str, sx, base, tp)
        return w
    }

    /** Big temperature: digits plus a pixel-ring degree sign hugging the last digit. Returns width. */
    private fun bigTemp(temp: Double, x: Float, top: Float, size: Float, align: Int = -1): Float {
        val num = Fmt.t(temp).removeSuffix("°")
        tp.textSize = size
        val bounds = android.graphics.Rect()
        tp.getTextBounds(num, 0, num.length, bounds)
        val u = size / 10f
        val total = bounds.right + u + 3 * u
        val sx = if (align == 0) x - total / 2 else x
        text(num, sx, top, size, INK, shadow = true)
        val dx = sx + bounds.right + u; val dy = top + size * 0.875f + bounds.top
        for ((col, off) in listOf(0x73000000 to size * 0.12f, INK to 0f)) {
            fp.color = col
            c.drawRect(dx + off, dy + off, dx + 3 * u + off, dy + u + off, fp)
            c.drawRect(dx + off, dy + 2 * u + off, dx + 3 * u + off, dy + 3 * u + off, fp)
            c.drawRect(dx + off, dy + u + off, dx + u + off, dy + 2 * u + off, fp)
            c.drawRect(dx + 2 * u + off, dy + u + off, dx + 3 * u + off, dy + 2 * u + off, fp)
        }
        return total
    }

    private fun icon(code: Int, day: Boolean, x: Float, y: Float, size: Float) =
        PixelIcons.draw(c, PixelIcons.grid(Wmo.kind(code), day), x, y, size)

    private fun skyFor(b: WeatherBundle): IntArray {
        val cur = b.weather.current
        val k = Wmo.kind(cur.code)
        return when {
            !cur.isDay -> intArrayOf(0xFF0B0B24.toInt(), 0xFF141436.toInt(), 0xFF1D1D4A.toInt(), 0xFF28285E.toInt())
            k == "clear" || k == "mostly" -> intArrayOf(0xFF1E3A8A.toInt(), 0xFF2850B0.toInt(), 0xFF3A6AD0.toInt(), 0xFF5A8AE6.toInt())
            k == "partly" -> intArrayOf(0xFF283593.toInt(), 0xFF3949AB.toInt(), 0xFF5463B8.toInt(), 0xFF7280C4.toInt())
            k == "thunder" -> intArrayOf(0xFF1A1030.toInt(), 0xFF2A1A48.toInt(), 0xFF3A2860.toInt(), 0xFF4A3878.toInt())
            k == "snow" -> intArrayOf(0xFF4A5578.toInt(), 0xFF5D6890.toInt(), 0xFF7480A6.toInt(), 0xFF8E98BA.toInt())
            else -> intArrayOf(0xFF2C3350.toInt(), 0xFF3A4266.toInt(), 0xFF4A547A.toInt(), 0xFF5C668E.toInt())
        }
    }

    private fun updated(b: WeatherBundle) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(b.fetchedAt)) + if (b.stale) "*" else ""

    // ---------------------------------------------------------------- widgets
    fun draw(kind: String, b: WeatherBundle?) {
        if (b == null) {
            panel()
            text("BITWEATHER", W / 2, H / 2 - 14, 10f, PURPLE, 0)
            text("Open the app to load", W / 2, H / 2 + 4, 7f, DIM, 0, W - 16)
            return
        }
        when (kind) {
            "now" -> now(b); "hourly" -> hourly(b); "daily" -> daily(b); else -> details(b)
        }
    }

    private fun header(title: String, b: WeatherBundle, color: Int = ORANGE) {
        val right = updated(b)
        val rw = width(right, 7f)
        text(title, pad, pad, 8f, color, maxW = W - 2 * pad - rw - 6)
        text(right, W - pad, pad + 1, 7f, DIM, 1)
    }

    private fun now(b: WeatherBundle) {
        val w = b.weather; val cur = w.current; val D = w.daily; val d = w.d0()
        panel(skyFor(b))
        val alerts = Derive.allAlerts(b)
        var y = pad
        // header: place + time
        header((if (b.place.gps) "@ " else "") + b.place.name.uppercase(), b, INK)
        y += 14f
        if (alerts.isNotEmpty() && H >= 100) {
            val a = alerts[0]
            val col = when (a.type) { "warning" -> RED; "watch" -> ORANGE; "custom" -> PURPLE; else -> YELLOW }
            box(pad, y, W - pad, y + 14, 0xCC0D0D1A.toInt()); box(pad, y, pad + 4, y + 14, col)
            text("! " + a.title + if (alerts.size > 1) " +${alerts.size - 1}" else "", pad + 8, y + 3, 7f, INK, maxW = W - 2 * pad - 12)
            y += 18f
        }
        val tiny = W < 160 || H < 95
        val mainH = if (tiny) H - y - pad else min(76f, H - y - pad)
        val iconS = min(mainH, if (tiny) 48f else 68f)
        icon(cur.code, cur.isDay, pad, y + (mainH - iconS) / 2, iconS)
        val tx = pad + iconS + 8
        val tempSize = if (tiny) min(22f, mainH * 0.5f) else min(30f, mainH * 0.4f)
        if (tiny) {
            bigTemp(cur.temp, tx, y + (mainH - tempSize) / 2 - 4, tempSize)
            text("${Fmt.t(D.tmax.at(d))}/${Fmt.t(D.tmin.at(d))}", tx, y + (mainH + tempSize) / 2 + 2, 7f, INK, maxW = W - tx - pad)
            return
        }
        var ty = y + max(0f, (mainH - (tempSize + 36)) / 2)
        val tw = bigTemp(cur.temp, tx, ty, tempSize)
        val rx = tx + tw + 12
        if (W - rx > 70) {
            text("H ${Fmt.t(D.tmax.at(d))}", rx, ty + 2, 8f, INK)
            text("L ${Fmt.t(D.tmin.at(d))}", rx, ty + 14, 8f, INK)
        }
        ty += tempSize + 8
        text(Wmo.name(cur.code), tx, ty, 8f, INK, maxW = W - tx - pad)
        ty += 13
        text("Feels ${Fmt.t(cur.feels)}  ${Fmt.w(cur.wind)} ${Fmt.compass8(cur.windDir)}", tx, ty, 7f, 0xD9FFFFFF.toInt(), maxW = W - tx - pad)
        y += mainH + 10
        // hourly strip if there's room
        if (H - y - pad >= 54) {
            box(pad, y - 2, W - pad, y - 1, 0x40FFFFFF)
            val stripH = 52f
            val n = floor((W - 2 * pad) / 44f).toInt().coerceIn(1, 12)
            val cw = (W - 2 * pad) / n
            val hrs = Derive.hoursAhead(w, n)
            hrs.forEachIndexed { k, i ->
                val cx = pad + cw * k + cw / 2
                text(Fmt.t(w.hourly.temp.at(i)), cx, y + 2, 8f, INK, 0)
                val s = 24f
                icon(w.hourly.code.at(i), w.hourly.isDay.at(i) == 1, cx - s / 2, y + 13, s)
                text(if (k == 0) "Now" else Fmt.clock(w.hourly.time[i]), cx, y + 41, 7f, if (k == 0) ORANGE else DIM, 0)
            }
            y += stripH + 8
        }
        // daily rows if there's still room
        if (H - y - pad >= 40) {
            box(pad, y - 2, W - pad, y - 1, 0x40FFFFFF)
            dailyRows(w, pad, y + 2, W - pad, H - pad, 20f)
        }
    }

    private fun dailyRows(w: com.hootywhooo86.bitweather.data.Weather, l: Float, t: Float, r: Float, bottom: Float, minRow: Float) {
        val D = w.daily
        val n = min(10, floor((bottom - t) / minRow).toInt())
        if (n <= 0) return
        val ds = Derive.days(w, n)
        val rowH = (bottom - t) / n
        val lo = ds.minOf { D.tmin.at(it) }; val hi = ds.maxOf { D.tmax.at(it) }; val span = max(1.0, hi - lo)
        val ts = min(8f, rowH * 0.45f)
        val hasPop = r - l > 200 && ds.any { D.popMax.at(it) > 0 }
        ds.forEachIndexed { k, i ->
            val y = t + k * rowH
            val mid = y + rowH / 2
            text(if (i == w.d0()) "Today" else Fmt.dow(D.time[i]), l, mid - ts / 2, ts, INK)
            val iconS = min(rowH - 2, 20f)
            icon(D.code.at(i), true, l + 46, mid - iconS / 2, iconS)
            // columns pack left: day, icon, rain chance (only if any day has one), low, bar, high
            val popX = l + 46 + iconS + 6
            val pop = D.popMax.at(i)
            if (hasPop && pop > 0) text("$pop%", popX, mid - 3.5f, 7f, CYAN)
            val loRight = popX + (if (hasPop) 34 else 0) + 30
            val barL = loRight + 8
            val barR = r - 36
            text(Fmt.t(D.tmin.at(i)), loRight, mid - ts / 2, ts, 0xFFC5CAE9.toInt(), 1)
            text(Fmt.t(D.tmax.at(i)), r, mid - ts / 2, ts, INK, 1)
            if (barR - barL > 20) {
                box(barL, mid - 3, barR, mid + 3, 0xFF101024.toInt())
                val a = barL + ((D.tmin.at(i) - lo) / span * (barR - barL)).toFloat()
                val bb = barL + ((D.tmax.at(i) - lo) / span * (barR - barL)).toFloat()
                val segs = max(1, ((bb - a) / 4).toInt())
                for (s in 0 until segs) {
                    val f = s.toFloat() / segs
                    val col = if (f < 0.33f) CYAN else if (f < 0.66f) YELLOW else ORANGE
                    box(a + (bb - a) * s / segs, mid - 3, a + (bb - a) * (s + 1) / segs, mid + 3, col)
                }
                if (i == w.d0()) {
                    val x = barL + ((w.current.temp - lo) / span * (barR - barL)).toFloat()
                    box(x - 1.5f, mid - 5, x + 1.5f, mid + 5, INK)
                }
            }
        }
    }

    private fun hourly(b: WeatherBundle) {
        val w = b.weather; val Hh = w.hourly
        panel()
        header("HOURLY · " + b.place.name.uppercase(), b)
        var top = pad + 16
        val bottom = H - pad
        val stripH = min(78f, bottom - top)
        // temperature chart above the strip when tall
        if (bottom - top - stripH >= 50) {
            val chartB = bottom - stripH - 8
            val idx = Derive.hoursAhead(w, 24)
            val ts = idx.map { Hh.temp.at(it) }
            val lo = ts.min(); val hi = ts.max(); val span = max(1.0, hi - lo)
            val bw = (W - 2 * pad) / idx.size
            idx.forEachIndexed { k, i ->
                val pop = Hh.pop.at(i)
                if (pop > 0) box(pad + k * bw, chartB - pop / 100f * 18, pad + (k + 1) * bw - 1, chartB, 0x804FC3F7.toInt())
                val y = top + 10 + ((1 - (ts[k] - lo) / span) * (chartB - top - 24)).toFloat()
                box(pad + k * bw, y, pad + (k + 1) * bw, y + 4, ORANGE)
                if (k % 6 == 0) text(Fmt.t(ts[k]), pad + k * bw, y - 10, 7f, INK)
            }
            top = chartB + 8
        }
        val n = floor((W - 2 * pad) / 44f).toInt().coerceIn(1, 24)
        val cw = (W - 2 * pad) / n
        val s = min(28f, stripH - 40)
        Derive.hoursAhead(w, n).forEachIndexed { k, i ->
            val cx = pad + cw * k + cw / 2
            text(Fmt.t(Hh.temp.at(i)), cx, top, 9f, INK, 0)
            icon(Hh.code.at(i), Hh.isDay.at(i) == 1, cx - s / 2, top + 13, s)
            val pop = Hh.pop.at(i)
            if (pop > 0) text("$pop%", cx, top + 16 + s, 7f, CYAN, 0)
            text(if (k == 0) "Now" else Fmt.clock(Hh.time[i]), cx, top + 27 + s, 7f, if (k == 0) ORANGE else DIM, 0)
        }
    }

    private fun daily(b: WeatherBundle) {
        val w = b.weather; val D = w.daily
        panel()
        header("10-DAY · " + b.place.name.uppercase(), b)
        val top = pad + 16
        if (H < 150) {
            // short and wide: pills
            val n = floor((W - 2 * pad) / 46f).toInt().coerceIn(1, 10)
            val cw = (W - 2 * pad) / n
            val h = H - pad - top
            Derive.days(w, n).forEachIndexed { k, i ->
                val l = pad + cw * k + 2; val r = l + cw - 4
                fp.color = EDGE; c.drawRoundRect(RectF(l, top, r, top + h), 10f, 10f, fp)
                fp.color = PANEL2; c.drawRoundRect(RectF(l + 2, top + 2, r - 2, top + h - 2), 8f, 8f, fp)
                val cx = (l + r) / 2
                text(Fmt.t(D.tmax.at(i)), cx, top + 6, 8f, INK, 0)
                text(Fmt.t(D.tmin.at(i)), cx, top + 17, 7f, DIM, 0)
                val s = min(22f, h - 52)
                if (s > 8) icon(D.code.at(i), true, cx - s / 2, top + 28, s)
                text(if (i == w.d0()) "Tdy" else Fmt.dow(D.time[i]), cx, top + h - 12, 7f, INK, 0)
            }
            return
        }
        dailyRows(w, pad, top, W - pad, H - pad, 22f)
    }

    private fun details(b: WeatherBundle) {
        val w = b.weather; val cur = w.current; val D = w.daily; val d = w.d0()
        panel()
        header(b.place.name.uppercase(), b)
        val top = pad + 16
        val cols = max(1, floor((W - 2 * pad) / 96f).toInt())
        val rows = max(1, floor((H - pad - top) / 58f).toInt())
        val aq = Derive.aqInfo(b.air)
        val uv = if (cur.uv.isNaN()) 0.0 else cur.uv
        val tiles = listOf(
            Triple("WIND", "${Fmt.wNum(cur.wind)} ${Fmt.windUnit}", "From ${Fmt.compass(cur.windDir)} · G ${Fmt.wNum(cur.gust)}"),
            Triple("HUMIDITY", "${cur.humidity.roundToInt()}%", "Dew ${Fmt.t(cur.dew)}"),
            Triple("UV INDEX", "${uv.roundToInt()}", Derive.uvInfo(uv).first),
            Triple("AIR", aq?.value ?: "--", aq?.let { "${it.scale} " + it.label.substringBefore(" health") } ?: "No data"),
            Triple("SUN", Fmt.clock(D.sunrise.getOrElse(d) { "" }), "Set ${Fmt.clock(D.sunset.getOrElse(d) { "" })}"),
            Triple("PRESSURE", Fmt.pr(cur.pressure), Prefs.units.pressure + " " + Derive.pressureTrend(w).substringAfter(' ')),
            Triple("PRECIP", "${Fmt.p(D.precipSum.at(d).takeUnless { it.isNaN() } ?: 0.0)}${Fmt.pUnit}", "${D.popMax.at(d)}% today"),
            Triple("VISIBILITY", Fmt.dist(cur.visibility).let { "${it.first}${it.second}" }, Derive.visibilityDesc(cur.visibility)),
            Triple("FEELS", Fmt.t(cur.feels), "Actual ${Fmt.t(cur.temp)}"),
            Triple("CONDITIONS", Fmt.t(cur.temp), Wmo.name(cur.code)),
        ).take(cols * rows)
        val tw = (W - 2 * pad) / cols; val th = (H - pad - top) / rows
        tiles.forEachIndexed { k, (label, value, sub) ->
            val l = pad + (k % cols) * tw; val t = top + (k / cols) * th
            box(l + 2, t + 2, l + tw - 2, t + th - 2, PANEL2)
            text(label, l + 7, t + 7, 7f, ORANGE, maxW = tw - 14)
            val vs = min(14f, th * 0.26f)
            text(value, l + 7, t + 7 + 12, vs, INK, maxW = tw - 14)
            text(sub, l + 7, t + th - 14, 7f, DIM, maxW = tw - 14)
        }
    }
}
