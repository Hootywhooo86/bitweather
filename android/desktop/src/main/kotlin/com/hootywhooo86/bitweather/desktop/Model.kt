package com.hootywhooo86.bitweather.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hootywhooo86.bitweather.data.Api
import com.hootywhooo86.bitweather.data.Config
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Place
import com.hootywhooo86.bitweather.data.Weather
import com.hootywhooo86.bitweather.data.WeatherBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** A desktop widget the user placed: type, size and where it sits on screen. */
data class DeskWidget(val id: String, val type: String, var size: String, var x: Int, var y: Int, var onTop: Boolean = false)

/** All app state for the Windows version (settings are saved as JSON in the user's app-data folder). */
object Model {
    val dir: File = File(
        System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config"), "Bitweather",
    ).apply { mkdirs() }
    private val settingsFile = File(dir, "settings.json")
    private val weatherDir = File(dir, "weather").apply { mkdirs() }

    const val UPDATE_MS = 10 * 60_000L
    val version: String = System.getProperty("bitweather.version") ?: "1.0.0-dev"
    val build: Int = System.getProperty("bitweather.build")?.toIntOrNull() ?: 0

    val DEFAULT_PLACE = Place("edson-ab", "Edson", "Alberta", "Canada", "CA", 53.5859, -116.4428)

    // ---- reactive state the UI reads
    var tick by mutableIntStateOf(0)
    var frame by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var screen by mutableStateOf("home")
    var mainOpen by mutableStateOf(true)
    var updateUrl by mutableStateOf<String?>(null)
    val widgets = mutableStateListOf<DeskWidget>()

    // ---- settings
    var places = mutableListOf(DEFAULT_PLACE)
    var activeId = DEFAULT_PLACE.id
    var followLocation = true
    var notifyAlerts = true
    var notifySummary = true
    var startWithWindows = false
    var animate = true
    var lastSummary = 0L
    val seen = LinkedHashSet<String>()
    /** True until the first time we find this PC's location; then it replaces the starter place. */
    var firstRun = true

    fun bump() { tick++ }
    fun active(): Place = places.firstOrNull { it.id == activeId } ?: places.first()
    fun home(): Place = places.firstOrNull { it.gps } ?: places.first()

    fun load() {
        if (!settingsFile.exists()) {
            widgets += DeskWidget("w1", "now", "M", 60, 60)
            return
        }
        firstRun = false
        runCatching {
            val o = JSONObject(settingsFile.readText())
            o.optJSONArray("places")?.let { a -> (0 until a.length()).map { Place.from(a.getJSONObject(it)) }.takeIf { it.isNotEmpty() }?.let { places = it.toMutableList() } }
            activeId = o.optString("activeId", places.first().id)
            o.optJSONObject("units")?.let { u ->
                Config.units.temp = u.optString("temp", "C"); Config.units.wind = u.optString("wind", "kmh")
                Config.units.precip = u.optString("precip", "mm"); Config.units.pressure = u.optString("pressure", "hPa")
                Config.units.dist = u.optString("dist", "km"); Config.units.clock = u.optString("clock", "24")
            }
            Config.aqScale = o.optString("aqScale", "aqhi")
            o.optJSONObject("thresholds")?.let { t ->
                fun g(k: String) = if (!t.has(k) || t.isNull(k)) null else t.optDouble(k)
                Config.thresholds.cold = g("cold"); Config.thresholds.hot = g("hot"); Config.thresholds.gust = g("gust"); Config.thresholds.snow = g("snow")
            }
            followLocation = o.optBoolean("followLocation", true)
            notifyAlerts = o.optBoolean("notifyAlerts", true)
            notifySummary = o.optBoolean("notifySummary", true)
            startWithWindows = o.optBoolean("startWithWindows", false)
            animate = o.optBoolean("animate", true)
            lastSummary = o.optLong("lastSummary")
            o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length()) seen += a.getString(i) }
            o.optJSONArray("widgets")?.let { a ->
                for (i in 0 until a.length()) {
                    val w = a.getJSONObject(i)
                    widgets += DeskWidget(w.getString("id"), w.getString("type"), w.optString("size", "M"), w.optInt("x"), w.optInt("y"), w.optBoolean("onTop"))
                }
            }
        }
        if (places.none { it.id == activeId }) activeId = places.first().id
    }

    @Synchronized
    fun save() {
        while (seen.size > 300) seen.remove(seen.first())
        val u = Config.units; val t = Config.thresholds
        val o = JSONObject()
            .put("places", JSONArray().apply { places.forEach { put(it.toJson()) } })
            .put("activeId", activeId)
            .put("units", JSONObject().put("temp", u.temp).put("wind", u.wind).put("precip", u.precip).put("pressure", u.pressure).put("dist", u.dist).put("clock", u.clock))
            .put("aqScale", Config.aqScale)
            .put("thresholds", JSONObject().put("cold", t.cold ?: JSONObject.NULL).put("hot", t.hot ?: JSONObject.NULL).put("gust", t.gust ?: JSONObject.NULL).put("snow", t.snow ?: JSONObject.NULL))
            .put("followLocation", followLocation).put("notifyAlerts", notifyAlerts).put("notifySummary", notifySummary)
            .put("startWithWindows", startWithWindows).put("animate", animate).put("lastSummary", lastSummary)
            .put("seen", JSONArray(seen.toList()))
            .put("widgets", JSONArray().apply { widgets.forEach { put(JSONObject().put("id", it.id).put("type", it.type).put("size", it.size).put("x", it.x).put("y", it.y).put("onTop", it.onTop)) } })
        runCatching { settingsFile.writeText(o.toString(2)) }
    }

    // ---------------------------------------------------------------- weather cache + fetch
    private val mem = HashMap<String, WeatherBundle>()
    private val lock = Mutex()

    private fun file(id: String) = File(weatherDir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun cached(id: String): WeatherBundle? {
        mem[id]?.let { return it }
        val f = file(id)
        if (!f.exists()) return null
        return runCatching { WeatherBundle.from(JSONObject(f.readText())) }.getOrNull()?.also { mem[id] = it }
    }

    private fun fresh(b: WeatherBundle?) = b != null && !b.stale && System.currentTimeMillis() - b.fetchedAt < UPDATE_MS

    suspend fun fetch(p: Place, force: Boolean): WeatherBundle? = withContext(Dispatchers.IO) {
        val c = cached(p.id)
        if (!force && fresh(c)) return@withContext c
        coroutineScope {
            val w = async { runCatching { Api.get(Api.forecastUrl(p)) }.getOrNull() }
            val aq = async { runCatching { Api.get(Api.airUrl(p)) }.getOrNull() }
            val al = async { runCatching { Api.alerts(p) }.getOrNull() }
            val wr = w.await() ?: return@coroutineScope c?.asStale()
            val b = WeatherBundle(p, System.currentTimeMillis(), Weather(wr),
                aq.await()?.let { runCatching { com.hootywhooo86.bitweather.data.Air(it) }.getOrNull() } ?: c?.air,
                al.await() ?: c?.alerts ?: emptyList())
            mem[p.id] = b
            runCatching { file(p.id).writeText(b.toJson().toString()) }
            b
        }
    }

    // ---------------------------------------------------------------- location
    /**
     * Where this PC is: Windows Location (precise, if location services are on), otherwise an
     * approximate position from the internet connection.
     */
    suspend fun locate(): Place? = withContext(Dispatchers.IO) {
        val (lat, lon) = windowsLocation() ?: ipLocation() ?: return@withContext null
        val la = Math.round(lat * 10000) / 10000.0; val lo = Math.round(lon * 10000) / 10000.0
        val prev = places.firstOrNull { it.gps }
        val moved = prev == null || Math.abs(prev.lat - la) > 0.01 || Math.abs(prev.lon - lo) > 0.01
        val place = if (moved) (Api.reverse(la, lo) ?: Place("gps", "My location", "", "", "", la, lo, true)).copy(id = "gps", lat = la, lon = lo, gps = true) else prev!!
        synchronized(this@Model) {
            val i = places.indexOfFirst { it.gps }
            if (i >= 0) places[i] = place else places.add(0, place)
            if (firstRun) { places.removeAll { it.id == DEFAULT_PLACE.id }; activeId = "gps"; firstRun = false }
        }
        if (moved) { mem.remove("gps"); file("gps").delete() }
        save()
        place
    }

    private fun windowsLocation(): Pair<Double, Double>? {
        if (!System.getProperty("os.name").lowercase().contains("win")) return null
        val script = "Add-Type -AssemblyName System.Device;" +
            "\$w=New-Object System.Device.Location.GeoCoordinateWatcher;\$w.Start();" +
            "\$t=0;while((\$w.Status -ne 'Ready') -and (\$t -lt 60)){Start-Sleep -Milliseconds 200;\$t++};" +
            "\$c=\$w.Position.Location;if(-not \$c.IsUnknown){Write-Output (\"\$(\$c.Latitude),\$(\$c.Longitude)\")}"
        return runCatching {
            val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
            if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
            val line = p.inputStream.bufferedReader().readText().trim().lines().lastOrNull { it.contains(",") } ?: return null
            val (a, b) = line.split(",").map { it.trim().replace(" ", "").toDouble() }
            if (a == 0.0 && b == 0.0) null else a to b
        }.getOrNull()
    }

    private fun ipLocation(): Pair<Double, Double>? = runCatching {
        val o = JSONObject(Api.get("https://ipwho.is/", 8000))
        if (!o.optBoolean("success", true)) null else o.getDouble("latitude") to o.getDouble("longitude")
    }.getOrNull()

    // ---------------------------------------------------------------- the 10-minute update
    /** Returns notifications to show: (title, message, isAlert). */
    suspend fun cycle(force: Boolean, relocate: Boolean): List<Triple<String, String, Boolean>> = lock.withLock {
        loading = true
        val out = ArrayList<Triple<String, String, Boolean>>()
        try {
            if (followLocation && relocate) runCatching { locate() }
            val hb = fetch(home(), force)
            if (active().id != home().id) fetch(active(), force)
            if (hb != null && !hb.stale) {
                for (a in Derive.allAlerts(hb)) {
                    if (!seen.add(a.key)) continue
                    if (notifyAlerts) out += Triple("${a.title} — ${hb.place.name}", a.name.ifBlank { a.text.take(160) }, true)
                }
                val now = System.currentTimeMillis()
                if (notifySummary && now - lastSummary >= 6 * 3600_000L - 10 * 60_000L) {
                    lastSummary = now
                    out += Triple("${hb.place.name}  ${com.hootywhooo86.bitweather.data.Fmt.t(hb.weather.current.temp)}", Derive.nowSummary(hb.weather), false)
                }
                save()
            }
            runCatching { checkUpdate() }
            status = if (hb == null) "Could not load weather — check your connection" else if (hb.stale) "Offline — showing saved forecast" else null
        } finally {
            loading = false
            bump()
        }
        out
    }

    private var lastUpdateCheck = 0L

    /** Looks for a newer Windows installer on GitHub Releases (every 6 hours). */
    private fun checkUpdate() {
        if (System.currentTimeMillis() - lastUpdateCheck < 6 * 3600_000L) return
        lastUpdateCheck = System.currentTimeMillis()
        val j = JSONObject(Api.get("https://api.github.com/repos/${Api.REPO}/releases/latest", 10000))
        val build = Regex("(\\d+)$").find(j.optString("tag_name"))?.groupValues?.get(1)?.toInt() ?: return
        if (build <= this.build || this.build == 0) return
        val assets = j.optJSONArray("assets") ?: return
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".msi")) updateUrl = a.optString("browser_download_url")
        }
    }

    // ---------------------------------------------------------------- start with Windows
    fun applyStartup() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val exe = System.getProperty("jpackage.app-path") ?: return
        val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        val cmd = if (startWithWindows) listOf("reg", "add", key, "/v", "Bitweather", "/t", "REG_SZ", "/d", "\"$exe\" --tray", "/f")
        else listOf("reg", "delete", key, "/v", "Bitweather", "/f")
        runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS) }
    }
}
