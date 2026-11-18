package com.hootywhooo86.bitweather.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hootywhooo86.bitweather.data.Air
import com.hootywhooo86.bitweather.data.Alert
import com.hootywhooo86.bitweather.data.Weather
import com.hootywhooo86.bitweather.data.WeatherBundle
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/** Renders the Windows widgets and main window headlessly to build/desktop-previews. */
class RenderDesktopTest {
    private val out = File("build/desktop-previews").apply { mkdirs() }

    /** Fixture JSON with dates moved so "now" is today. */
    private fun res(name: String): String {
        val raw = javaClass.classLoader!!.getResource(name)!!.readText()
        val o = org.json.JSONObject(raw)
        val off = o.optInt("utc_offset_seconds")
        val fixtureDay = java.time.LocalDate.parse(o.getJSONObject("current").getString("time").take(10))
        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis() + off * 1000L).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val shift = java.time.temporal.ChronoUnit.DAYS.between(fixtureDay, today)
        return Regex("\\d{4}-\\d{2}-\\d{2}").replace(raw) { java.time.LocalDate.parse(it.value).plusDays(shift).toString() }
    }

    private fun seed() {
        System.setProperty("user.home", File("build/test-home").absolutePath)
        Model.animate = false
        val b = WeatherBundle(Model.DEFAULT_PLACE, System.currentTimeMillis(), Weather(res("forecast.json")), Air(res("air.json")),
            listOf(Alert("warning", "Snowfall warning", "Snowfall warning in effect", "Up to 15 cm of snow.", null, "ECCC")))
        File(Model.dir, "weather").mkdirs()
        File(File(Model.dir, "weather"), "edson-ab.json").writeText(b.toJson().toString())
    }

    private fun shot(name: String, w: Int, h: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(w * 2, h * 2, Density(2f), content = content)
        scene.render() // first frame lays out
        val img = scene.render()
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test
    fun renderAll() {
        seed()
        val b = Model.cached(Model.DEFAULT_PLACE.id)
        for ((type, _) in WIDGET_TYPES) for (size in listOf("S", "M", "L")) {
            val (w, h) = widgetSize(type, size)
            shot("widget-$type-$size", w, h) { Box(Modifier.size(w.dp, h.dp)) { WidgetFace(type, size, b) } }
        }
        shot("main-home", 480, 1400) { MainScreen {} }
        Model.screen = "settings"
        shot("main-settings", 480, 900) { MainScreen {} }
        Model.screen = "home"
    }
}
