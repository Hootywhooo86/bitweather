package com.hootywhooo86.bitweather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ---------------------------------------------------------------- json helpers
internal fun JSONObject.d(k: String): Double = if (!has(k) || isNull(k)) Double.NaN else optDouble(k, Double.NaN)
internal fun JSONObject.dArr(k: String): DoubleArray {
    val a = optJSONArray(k) ?: return DoubleArray(0)
    return DoubleArray(a.length()) { if (a.isNull(it)) Double.NaN else a.optDouble(it, Double.NaN) }
}
internal fun JSONObject.iArr(k: String): IntArray {
    val a = optJSONArray(k) ?: return IntArray(0)
    return IntArray(a.length()) { if (a.isNull(it)) 0 else a.optInt(it, 0) }
}
internal fun JSONObject.sArr(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return List(a.length()) { a.optString(it) }
}
internal fun DoubleArray.at(i: Int): Double = if (i in indices) this[i] else Double.NaN
internal fun IntArray.at(i: Int): Int = if (i in indices) this[i] else 0

/** Open-Meteo local times ("2026-10-19T16:00") as epoch ms, treating local wall time as UTC. */
fun naive(s: String): Long = if (s.length == 10) LocalDate.parse(s).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
else LocalDateTime.parse(s).toEpochSecond(ZoneOffset.UTC) * 1000

fun parseInstant(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching { Instant.parse(s) }.getOrNull() ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
}

// ---------------------------------------------------------------- places
data class Place(
    val id: String, val name: String, val admin: String = "", val country: String = "", val cc: String = "",
    val lat: Double, val lon: Double, val gps: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("admin", admin).put("country", country)
        .put("cc", cc).put("lat", lat).put("lon", lon).put("gps", gps)

    val subtitle: String get() = listOf(admin, country).filter { it.isNotBlank() }.joinToString(", ")

    companion object {
        fun from(o: JSONObject) = Place(
            o.getString("id"), o.optString("name", "Place"), o.optString("admin"), o.optString("country"),
            o.optString("cc"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("gps"),
        )
    }
}

data class Alert(
    val type: String, val title: String, val name: String, val text: String,
    val until: String? = null, val src: String? = null, val key: String = "$title|$name|$until",
) {
    fun toJson(): JSONObject = JSONObject().put("type", type).put("title", title).put("name", name).put("text", text)
        .put("until", until ?: JSONObject.NULL).put("src", src ?: JSONObject.NULL).put("key", key)

    companion object {
        fun from(o: JSONObject) = Alert(
            o.optString("type"), o.optString("title"), o.optString("name"), o.optString("text"),
            o.optString("until").takeIf { !o.isNull("until") && it.isNotEmpty() },
            o.optString("src").takeIf { !o.isNull("src") && it.isNotEmpty() },
            o.optString("key"),
        )
    }
}

// ---------------------------------------------------------------- weather
class Current(o: JSONObject) {
    val temp = o.d("temperature_2m"); val humidity = o.d("relative_humidity_2m"); val feels = o.d("apparent_temperature")
    val isDay = o.optInt("is_day", 1) == 1; val precip = o.d("precipitation"); val code = o.optInt("weather_code")
    val cloud = o.d("cloud_cover"); val pressure = o.d("pressure_msl"); val wind = o.d("wind_speed_10m")
    val windDir = o.d("wind_direction_10m"); val gust = o.d("wind_gusts_10m"); val visibility = o.d("visibility")
    val dew = o.d("dew_point_2m"); val uv = o.d("uv_index")
}

class Hourly(o: JSONObject) {
    val time = o.sArr("time"); val temp = o.dArr("temperature_2m"); val feels = o.dArr("apparent_temperature")
    val humidity = o.dArr("relative_humidity_2m"); val dew = o.dArr("dew_point_2m")
    val pop = o.iArr("precipitation_probability"); val precip = o.dArr("precipitation"); val snow = o.dArr("snowfall")
    val code = o.iArr("weather_code"); val pressure = o.dArr("pressure_msl"); val vis = o.dArr("visibility")
    val wind = o.dArr("wind_speed_10m"); val windDir = o.dArr("wind_direction_10m"); val gust = o.dArr("wind_gusts_10m")
    val uv = o.dArr("uv_index"); val isDay = o.iArr("is_day")
}

class Daily(o: JSONObject) {
    val time = o.sArr("time"); val code = o.iArr("weather_code")
    val tmax = o.dArr("temperature_2m_max"); val tmin = o.dArr("temperature_2m_min")
    val fmax = o.dArr("apparent_temperature_max"); val fmin = o.dArr("apparent_temperature_min")
    val sunrise = o.sArr("sunrise"); val sunset = o.sArr("sunset"); val daylight = o.dArr("daylight_duration")
    val uvMax = o.dArr("uv_index_max"); val precipSum = o.dArr("precipitation_sum"); val rainSum = o.dArr("rain_sum")
    val snowSum = o.dArr("snowfall_sum"); val popMax = o.iArr("precipitation_probability_max")
    val windMax = o.dArr("wind_speed_10m_max"); val gustMax = o.dArr("wind_gusts_10m_max")
    val windDir = o.dArr("wind_direction_10m_dominant")
}

class Weather(val raw: String) {
    private val o = JSONObject(raw)
    val utcOffset: Int = o.optInt("utc_offset_seconds")
    val current = Current(o.optJSONObject("current") ?: JSONObject())
    val hourly = Hourly(o.optJSONObject("hourly") ?: JSONObject())
    val daily = Daily(o.optJSONObject("daily") ?: JSONObject())

    /** "Now" in the location's local wall time, as naive epoch ms. */
    fun nowLocal(): Long = System.currentTimeMillis() + utcOffset * 1000L

    /** Index of the hourly slot that contains now. */
    fun i0(): Int {
        val now = nowLocal()
        val i = hourly.time.indexOfFirst { naive(it) > now } - 1
        return when {
            i >= 0 -> i
            hourly.time.isNotEmpty() && i == -2 -> hourly.time.size - 1
            else -> 0
        }
    }

    /** Index of today in the daily arrays. */
    fun d0(): Int {
        val today = Instant.ofEpochMilli(nowLocal()).toString().substring(0, 10)
        return daily.time.indexOf(today).coerceAtLeast(0)
    }
}

class Air(val raw: String) {
    private val o = JSONObject(raw)
    val utcOffset: Int = o.optInt("utc_offset_seconds")
    private val c = o.optJSONObject("current") ?: JSONObject()
    val usAqi = c.d("us_aqi"); val pm25 = c.d("pm2_5"); val pm10 = c.d("pm10"); val o3 = c.d("ozone")
    val no2 = c.d("nitrogen_dioxide"); val so2 = c.d("sulphur_dioxide"); val co = c.d("carbon_monoxide")
    private val h = o.optJSONObject("hourly") ?: JSONObject()
    val hTime = h.sArr("time"); val hPm25 = h.dArr("pm2_5"); val hO3 = h.dArr("ozone"); val hNo2 = h.dArr("nitrogen_dioxide")
}

/** Everything we know about one place, as cached on disk. */
class WeatherBundle(
    val place: Place, val fetchedAt: Long, val weather: Weather, val air: Air?, val alerts: List<Alert>,
    val stale: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("place", place.toJson()).put("t", fetchedAt).put("w", weather.raw)
        .put("aq", air?.raw ?: JSONObject.NULL).put("alerts", JSONArray().apply { alerts.forEach { put(it.toJson()) } })

    fun asStale() = WeatherBundle(place, fetchedAt, weather, air, alerts, true)

    companion object {
        fun from(o: JSONObject): WeatherBundle {
            val a = o.optJSONArray("alerts") ?: JSONArray()
            return WeatherBundle(
                Place.from(o.getJSONObject("place")), o.getLong("t"), Weather(o.getString("w")),
                if (o.isNull("aq")) null else runCatching { Air(o.getString("aq")) }.getOrNull(),
                List(a.length()) { Alert.from(a.getJSONObject(it)) },
            )
        }
    }
}
