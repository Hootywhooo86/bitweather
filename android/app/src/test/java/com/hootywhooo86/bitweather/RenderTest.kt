package com.hootywhooo86.bitweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.hootywhooo86.bitweather.data.Air
import com.hootywhooo86.bitweather.data.Alert
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Weather
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.ui.AppModel
import com.hootywhooo86.bitweather.ui.MainActivity
import com.hootywhooo86.bitweather.ui.Screen
import com.hootywhooo86.bitweather.widget.WidgetPainter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File

/**
 * Renders the home-screen widgets and app screens from a real forecast fixture into PNGs
 * (build/previews), and sanity-checks the derived weather text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2600dp-xxhdpi")
class RenderTest {
    private lateinit var ctx: Context
    private lateinit var bundle: WeatherBundle
    private val out = File("build/previews").apply { mkdirs() }

    /**
     * Fixture JSON with every date moved forward so the forecast's "now" is today: the tests must not
     * go stale as the saved forecast ages.
     */
    private fun res(name: String): String {
        val raw = javaClass.classLoader!!.getResource(name).readText()
        val o = org.json.JSONObject(raw)
        val off = o.optInt("utc_offset_seconds")
        val fixtureDay = java.time.LocalDate.parse(o.getJSONObject("current").getString("time").take(10))
        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis() + off * 1000L).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val shift = java.time.temporal.ChronoUnit.DAYS.between(fixtureDay, today)
        return Regex("\\d{4}-\\d{2}-\\d{2}").replace(raw) { java.time.LocalDate.parse(it.value).plusDays(shift).toString() }
    }

    @Before
    fun seed() {
        ctx = ApplicationProvider.getApplicationContext()
        com.hootywhooo86.bitweather.ui.animateFx = false
        Prefs.init(ctx)
        Prefs.firstRun = false
        Prefs.followGps = false
        Prefs.places = mutableListOf(Prefs.DEFAULT_PLACE, Prefs.DEFAULT_PLACE.copy(id = "yeg", name = "Edmonton", lat = 53.5461, lon = -113.4938))
        Prefs.activeId = Prefs.DEFAULT_PLACE.id
        Prefs.thresholds.cold = -1.0
        Prefs.save()
        bundle = WeatherBundle(
            Prefs.DEFAULT_PLACE, System.currentTimeMillis(), Weather(res("forecast.json")), Air(res("air.json")),
            listOf(Alert("warning", "Snowfall warning", "Snowfall warning in effect", "Up to 15 cm of snow expected.\n\nWhere: Edson region.", null, "Environment and Climate Change Canada")),
        )
        File(File(ctx.filesDir, "weather").apply { mkdirs() }, "edson-ab.json").writeText(bundle.toJson().toString())
    }

    @Test
    fun derivedWeather() {
        val w = bundle.weather
        assertTrue(w.hourly.time.isNotEmpty())
        assertTrue(Derive.brief(w).startsWith("Expect"))
        assertNotNull(Derive.aqhi(bundle.air))
        assertTrue(Derive.days(w).size >= 9)
        assertTrue(Derive.customAlerts(w).any { it.type == "custom" })
        println("BRIEF: " + Derive.brief(w))
        println("INSIGHT: " + Derive.insight(bundle))
        println("AQHI: " + Derive.aqhi(bundle.air))
    }

    @Test
    fun sixHourSummaryPopsUpAndTickerRunsEvery10Min() {
        Prefs.notifyConditions = true
        Prefs.lastSummary = 0
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        com.hootywhooo86.bitweather.notify.Notifier.channels(ctx)
        val ch = nm.getNotificationChannel(com.hootywhooo86.bitweather.notify.Notifier.CH_SUMMARY)
        assertTrue("summary channel must pop up", ch.importance == android.app.NotificationManager.IMPORTANCE_HIGH)
        assertTrue(com.hootywhooo86.bitweather.notify.Notifier.summaryDue())
        com.hootywhooo86.bitweather.notify.Notifier.process(ctx, bundle)
        val posted = shadowOf(nm).allNotifications
        assertTrue("summary posted", posted.any { it.channelId == com.hootywhooo86.bitweather.notify.Notifier.CH_SUMMARY })
        // not due again until 6 hours later
        org.junit.Assert.assertFalse(com.hootywhooo86.bitweather.notify.Notifier.summaryDue())
        // the 10-minute heartbeat that refreshes widgets and sends the forecast when due
        com.hootywhooo86.bitweather.notify.Ticker.schedule(ctx)
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        val next = shadowOf(am).nextScheduledAlarm
        assertNotNull("next tick scheduled", next)
        val delta = next!!.triggerAtTime - System.currentTimeMillis()
        assertTrue("tick ~10 min out, was $delta", delta in (10 * 60_000L - 30_000L)..(10 * 60_000L + 30_000L))
    }

    @Test
    fun animationFrames() {
        // every icon kind, day and night, all 4 frames
        val kinds = listOf("clear", "mostly", "partly", "cloudy", "fog", "drizzle", "rain", "freezing", "showers", "snow", "thunder")
        val cell = 64; val gap = 8
        val sheet = Bitmap.createBitmap(4 * 2 * (cell + gap) + gap, kinds.size * (cell + gap) + gap, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet); c.drawColor(Color.rgb(0x1a, 0x1a, 0x2e))
        kinds.forEachIndexed { r, k ->
            for (night in 0..1) for (f in 0 until 4) {
                val g = com.hootywhooo86.bitweather.pixel.PixelIcons.grid(k, night == 0, f)
                com.hootywhooo86.bitweather.pixel.PixelIcons.draw(c, g, (gap + (night * 4 + f) * (cell + gap)).toFloat(), (gap + r * (cell + gap)).toFloat(), cell.toFloat())
            }
        }
        File(out, "icon_frames.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // the Now widget's 4 frames side by side
        val scale = 2f; val w = 290; val h = 200
        val strip = Bitmap.createBitmap((4 * w * scale).toInt() + 3 * 12, (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val sc = Canvas(strip)
        for (f in 0 until 4) {
            val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
            val cc = Canvas(bmp); cc.scale(scale, scale)
            WidgetPainter(ctx, cc, w.toFloat(), h.toFloat(), f).draw("now", bundle)
            sc.drawBitmap(bmp, f * (w * scale + 12), 0f, Paint())
        }
        File(out, "widget_now_frames.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // different frames must actually differ (otherwise nothing animates)
        val a = com.hootywhooo86.bitweather.pixel.PixelIcons.grid("rain", true, 0)
        val b = com.hootywhooo86.bitweather.pixel.PixelIcons.grid("rain", true, 1)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun widgets() {
        val sizes = listOf(
            "now" to (250 to 110), "now" to (320 to 200), "now" to (330 to 400), "now" to (290 to 280), "now" to (130 to 70),
            "hourly" to (320 to 120), "hourly" to (330 to 260),
            "daily" to (320 to 130), "daily" to (330 to 300),
            "details" to (160 to 160), "details" to (330 to 200),
        )
        val scale = 3f
        val pad = 24
        // contact sheet
        val sheetW = 2 * (330 * scale).toInt() + 3 * pad
        val rendered = sizes.map { (kind, wh) ->
            val (w, h) = wh
            val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.scale(scale, scale)
            WidgetPainter(ctx, c, w.toFloat(), h.toFloat()).draw(kind, bundle)
            File(out, "widget_${kind}_${w}x$h.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp
        }
        // lay out two columns
        var x = pad; var y = pad; var rowH = 0
        val pos = rendered.map { b ->
            if (x + b.width + pad > sheetW) { x = pad; y += rowH + pad; rowH = 0 }
            val p = x to y; x += b.width + pad; rowH = maxOf(rowH, b.height); p
        }
        val sheet = Bitmap.createBitmap(sheetW, y + rowH + pad, Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        sc.drawColor(Color.rgb(0x5a, 0x6a, 0x8e))
        rendered.forEachIndexed { i, b -> sc.drawBitmap(b, pos[i].first.toFloat(), pos[i].second.toFloat(), Paint()) }
        File(out, "widgets_sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, setup: () -> Unit = {}) {
        val ac = Robolectric.buildActivity(MainActivity::class.java).setup()
        setup()
        repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(50) }
        val v = ac.get().window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ac.pause().stop().destroy()
    }

    @Test
    fun screens() {
        AppModel.screen = Screen.Home
        shot("app_home")
        shot("app_edit") { AppModel.editing = true; AppModel.bump() }
        AppModel.editing = false
        shot("app_places") { AppModel.screen = Screen.Places }
        shot("app_settings") { AppModel.screen = Screen.Settings }
        shot("app_day") { AppModel.screen = Screen.Day(bundle.weather.d0() + 1) }
        AppModel.screen = Screen.Home
    }
}
