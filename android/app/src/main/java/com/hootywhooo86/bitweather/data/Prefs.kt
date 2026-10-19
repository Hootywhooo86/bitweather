package com.hootywhooo86.bitweather.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Units(
    var temp: String = "C", var wind: String = "kmh", var precip: String = "mm",
    var pressure: String = "hPa", var dist: String = "km", var clock: String = "24",
)

/** Custom alert thresholds in metric (°C, km/h, cm). null = off. */
data class Thresholds(var cold: Double? = -20.0, var hot: Double? = 30.0, var gust: Double? = 70.0, var snow: Double? = 10.0)

data class Slot(val id: String, var size: String, var hidden: Boolean = false)

object Prefs {
    private lateinit var sp: SharedPreferences

    val DEFAULT_PLACE = Place("edson-ab", "Edson", "Alberta", "Canada", "CA", 53.5859, -116.4428)

    var places: MutableList<Place> = mutableListOf(DEFAULT_PLACE)
    var activeId: String = DEFAULT_PLACE.id
    val units = Units()
    var aqScale = "aqhi"
    val thresholds = Thresholds()
    var followGps = true
    var notifyAlerts = true
    var notifyConditions = true
    var notifyUpdates = true
    var layout: MutableList<Slot> = defaultLayout()
    val seen: LinkedHashSet<String> = LinkedHashSet()
    var firstRun = true
    var lastUpdateCheck = 0L
    var lastUpdateNotified = 0

    fun defaultLayout() = mutableListOf(
        Slot("alerts", "T"), Slot("brief", "W"), Slot("insight", "T"), Slot("hourly", "W"), Slot("daily", "L"),
        Slot("precip", "S"), Slot("wind", "S"), Slot("sun", "S"), Slot("uv", "S"), Slot("aqi", "S"),
        Slot("visibility", "S"), Slot("humidity", "S"), Slot("pressure", "S"), Slot("feels", "S"),
        Slot("moon", "S"), Slot("radar", "L"),
    )

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("bitweather", Context.MODE_PRIVATE)
        val raw = sp.getString("state", null) ?: return
        runCatching {
            val o = JSONObject(raw)
            o.optJSONArray("places")?.let { a ->
                val list = (0 until a.length()).map { Place.from(a.getJSONObject(it)) }
                if (list.isNotEmpty()) places = list.toMutableList()
            }
            activeId = o.optString("activeId", places.first().id)
            o.optJSONObject("units")?.let { u ->
                units.temp = u.optString("temp", "C"); units.wind = u.optString("wind", "kmh")
                units.precip = u.optString("precip", "mm"); units.pressure = u.optString("pressure", "hPa")
                units.dist = u.optString("dist", "km"); units.clock = u.optString("clock", "24")
            }
            aqScale = o.optString("aqScale", "aqhi")
            o.optJSONObject("thresholds")?.let { t ->
                fun g(k: String) = if (!t.has(k) || t.isNull(k)) null else t.optDouble(k)
                thresholds.cold = g("cold"); thresholds.hot = g("hot"); thresholds.gust = g("gust"); thresholds.snow = g("snow")
            }
            followGps = o.optBoolean("followGps", true)
            notifyAlerts = o.optBoolean("notifyAlerts", true)
            notifyConditions = o.optBoolean("notifyConditions", true)
            notifyUpdates = o.optBoolean("notifyUpdates", true)
            o.optJSONArray("layout")?.let { a ->
                val known = defaultLayout()
                val list = (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { j -> known.any { it.id == j.optString("id") } }
                    .map { Slot(it.getString("id"), it.optString("size", "S"), it.optBoolean("hidden")) }.toMutableList()
                known.forEach { k -> if (list.none { it.id == k.id }) list += k.copy(hidden = true) }
                layout = list
            }
            o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length()) seen += a.getString(i) }
            firstRun = o.optBoolean("firstRun", false)
            lastUpdateCheck = o.optLong("lastUpdateCheck")
            lastUpdateNotified = o.optInt("lastUpdateNotified")
        }
        if (places.none { it.id == activeId }) activeId = places.first().id
    }

    @Synchronized
    fun save() {
        while (seen.size > 300) seen.remove(seen.first())
        val o = JSONObject()
            .put("places", JSONArray().apply { places.forEach { put(it.toJson()) } })
            .put("activeId", activeId)
            .put("units", JSONObject().put("temp", units.temp).put("wind", units.wind).put("precip", units.precip)
                .put("pressure", units.pressure).put("dist", units.dist).put("clock", units.clock))
            .put("aqScale", aqScale)
            .put("thresholds", JSONObject().put("cold", thresholds.cold ?: JSONObject.NULL).put("hot", thresholds.hot ?: JSONObject.NULL)
                .put("gust", thresholds.gust ?: JSONObject.NULL).put("snow", thresholds.snow ?: JSONObject.NULL))
            .put("followGps", followGps).put("notifyAlerts", notifyAlerts).put("notifyConditions", notifyConditions)
            .put("notifyUpdates", notifyUpdates)
            .put("layout", JSONArray().apply { layout.forEach { put(JSONObject().put("id", it.id).put("size", it.size).put("hidden", it.hidden)) } })
            .put("seen", JSONArray(seen.toList()))
            .put("firstRun", firstRun).put("lastUpdateCheck", lastUpdateCheck).put("lastUpdateNotified", lastUpdateNotified)
        sp.edit().putString("state", o.toString()).apply()
    }

    fun active(): Place = places.firstOrNull { it.id == activeId } ?: places.first()

    /** Widgets and notifications follow your current location when we have one. */
    fun home(): Place = places.firstOrNull { it.gps } ?: places.first()
}
