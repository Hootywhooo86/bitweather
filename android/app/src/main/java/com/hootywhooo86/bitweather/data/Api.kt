package com.hootywhooo86.bitweather.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Free, key-less weather sources: Open-Meteo, Environment Canada, US NWS, Photon/OSM, RainViewer, GitHub releases. */
object Api {
    private const val UA = "Bitweather-Android (github.com/Hootywhooo86/bitweather)"
    const val REPO = "Hootywhooo86/bitweather"

    fun bytes(url: String, timeoutMs: Int = 15000): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json, application/geo+json, image/png, */*")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun get(url: String, timeoutMs: Int = 15000): String = String(bytes(url, timeoutMs), Charsets.UTF_8)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun f(v: Double) = String.format(Locale.US, "%.4f", v)

    fun forecastUrl(p: Place) = "https://api.open-meteo.com/v1/forecast?latitude=${f(p.lat)}&longitude=${f(p.lon)}" +
        "&timezone=auto&forecast_days=10&past_days=1" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,dew_point_2m,uv_index" +
        "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation_probability,precipitation,snowfall,weather_code,pressure_msl,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum,rain_sum,snowfall_sum,precipitation_hours,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant"

    fun airUrl(p: Place) = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${f(p.lat)}&longitude=${f(p.lon)}" +
        "&timezone=auto&past_days=1&forecast_days=2" +
        "&current=us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide" +
        "&hourly=pm2_5,ozone,nitrogen_dioxide,us_aqi"

    /** Official alerts: Environment and Climate Change Canada or US National Weather Service. */
    fun alerts(p: Place): List<Alert> {
        val now = System.currentTimeMillis()
        if (p.cc == "CA") {
            val d = 0.02
            val bbox = listOf(p.lon - d, p.lat - d, p.lon + d, p.lat + d).joinToString(",") { f(it) }
            val j = JSONObject(get("https://api.weather.gc.ca/collections/weather-alerts/items?f=json&lang=en&limit=50&bbox=$bbox", 20000))
            val feats = j.optJSONArray("features") ?: return emptyList()
            val seen = HashSet<String>()
            val out = ArrayList<Alert>()
            for (i in 0 until feats.length()) {
                val pr = feats.getJSONObject(i).optJSONObject("properties") ?: continue
                val end = pr.optString("expiration_datetime").ifEmpty { pr.optString("event_end_datetime") }
                val exp = parseInstant(end)?.toEpochMilli()
                val name = pr.optString("alert_name_en")
                val text = pr.optString("alert_text_en")
                val k = name + "|" + text.take(80)
                if ((exp != null && exp < now) || !seen.add(k) || pr.optString("status_en").contains("cancel", true)) continue
                val title = pr.optString("alert_short_name_en").ifEmpty { name }.ifEmpty { "Weather alert" }
                    .replaceFirstChar { it.uppercase() }
                val until = pr.optString("event_end_datetime").ifEmpty { end }.ifEmpty { null }
                out += Alert(pr.optString("alert_type", "statement"), title, name, text, until, "Environment and Climate Change Canada")
            }
            return out
        }
        if (p.cc == "US") {
            val j = JSONObject(get("https://api.weather.gov/alerts/active?point=${f(p.lat)},${f(p.lon)}"))
            val feats = j.optJSONArray("features") ?: return emptyList()
            return (0 until feats.length()).mapNotNull { i ->
                val pr = feats.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
                val ev = pr.optString("event")
                val type = when {
                    ev.contains("warning", true) -> "warning"
                    ev.contains("watch", true) -> "watch"
                    else -> "advisory"
                }
                val text = listOf(pr.optString("description"), pr.optString("instruction")).filter { it.isNotBlank() && it != "null" }.joinToString("\n\n")
                val until = pr.optString("ends").takeIf { it.isNotBlank() && it != "null" } ?: pr.optString("expires")
                Alert(type, ev, pr.optString("headline"), text, until, "US National Weather Service")
            }
        }
        return emptyList()
    }

    /** Place search: Photon (OpenStreetMap) merged with Open-Meteo geocoding. */
    fun search(q: String): List<Place> {
        val out = ArrayList<Place>()
        fun add(p: Place) {
            if (out.none { Math.abs(it.lat - p.lat) < 0.05 && Math.abs(it.lon - p.lon) < 0.05 }) out += p
        }
        runCatching {
            val j = JSONObject(get("https://photon.komoot.io/api/?q=${enc(q)}&limit=8&lang=en&osm_tag=place", 8000))
            val feats = j.optJSONArray("features")
            for (i in 0 until (feats?.length() ?: 0)) {
                val ft = feats!!.getJSONObject(i)
                val pr = ft.getJSONObject("properties")
                val kind = pr.optString("osm_value") + " " + pr.optString("type")
                if (!Regex("city|town|village|hamlet|locality|suburb|county|state|island|district|borough|municipality").containsMatchIn(kind)) continue
                val c = ft.getJSONObject("geometry").getJSONArray("coordinates")
                add(Place("s$i", pr.optString("name"), pr.optString("state"), pr.optString("country"),
                    pr.optString("countrycode").uppercase(), c.getDouble(1), c.getDouble(0)))
            }
        }
        runCatching {
            val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=8&language=en&format=json", 8000))
            val rs = j.optJSONArray("results")
            val first = q.trim().lowercase().split(' ', ',').first()
            for (i in 0 until (rs?.length() ?: 0)) {
                val r = rs!!.getJSONObject(i)
                if (!r.optString("name").lowercase().startsWith(first)) continue
                add(Place("o$i", r.optString("name"), r.optString("admin1"), r.optString("country"),
                    r.optString("country_code").uppercase(), r.getDouble("latitude"), r.getDouble("longitude")))
            }
        }
        return out.take(12)
    }

    /** Town name for coordinates (Photon). */
    fun reverse(lat: Double, lon: Double): Place? = runCatching {
        val j = JSONObject(get("https://photon.komoot.io/reverse?lat=${f(lat)}&lon=${f(lon)}&lang=en", 8000))
        val pr = j.getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        val name = listOf("city", "town", "village", "locality", "county", "name").map { pr.optString(it) }.firstOrNull { it.isNotBlank() }
            ?: "My location"
        Place("gps", name, pr.optString("state"), pr.optString("country"), pr.optString("countrycode").uppercase(), lat, lon, true)
    }.getOrNull()

    data class Release(val code: Int, val name: String, val apkUrl: String?, val pageUrl: String, val notes: String)

    /** Latest published APK. Tags look like v1.0.<build>; versionCode = 100 + build. */
    fun latestRelease(): Release? = runCatching {
        val j = JSONObject(get("https://api.github.com/repos/$REPO/releases/latest", 10000))
        val tag = j.optString("tag_name")
        val build = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toInt() ?: return null
        val assets = j.optJSONArray("assets")
        var apk: String? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
        }
        Release(100 + build, tag, apk, j.optString("html_url"), j.optString("body"))
    }.getOrNull()
}
