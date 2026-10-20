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

    private fun res(name: String) = javaClass.classLoader!!.getResource(name).readText()

    @Before
    fun seed() {
        ctx = ApplicationProvider.getApplicationContext()
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
