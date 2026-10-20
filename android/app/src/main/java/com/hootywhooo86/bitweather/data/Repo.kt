package com.hootywhooo86.bitweather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.hootywhooo86.bitweather.notify.Notifier
import com.hootywhooo86.bitweather.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

object LocationHelper {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val loc = runCatching {
                withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val cs = CancellationSignal()
                            lm.getCurrentLocation(p, cs, ctx.mainExecutor) { if (cont.isActive) cont.resume(it) }
                            cont.invokeOnCancellation { cs.cancel() }
                        } else {
                            val l = object : LocationListener {
                                override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resume(location) }
                                @Deprecated("old api") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                override fun onProviderEnabled(provider: String) {}
                                override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
                            }
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(p, l, Looper.getMainLooper())
                            cont.invokeOnCancellation { lm.removeUpdates(l) }
                        }
                    }
                }
            }.getOrNull()
            if (loc != null) return loc
        }
        return runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
    }
}

object Repo {
    const val UPDATE_MS = 30 * 60 * 1000L
    private val mem = ConcurrentHashMap<String, WeatherBundle>()
    private val cycleLock = Mutex()

    private fun file(ctx: Context, id: String) = File(File(ctx.filesDir, "weather").apply { mkdirs() }, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    fun cached(ctx: Context, id: String): WeatherBundle? {
        mem[id]?.let { return it }
        val f = file(ctx, id)
        if (!f.exists()) return null
        return runCatching { WeatherBundle.from(JSONObject(f.readText())) }.getOrNull()?.also { mem[id] = it }
    }

    fun drop(ctx: Context, id: String) {
        mem.remove(id); file(ctx, id).delete()
    }

    fun isFresh(b: WeatherBundle?) = b != null && !b.stale && System.currentTimeMillis() - b.fetchedAt < UPDATE_MS

    /** Forecast + air quality + official alerts for one place, cached to disk. */
    suspend fun fetch(ctx: Context, p: Place, force: Boolean = false): WeatherBundle = withContext(Dispatchers.IO) {
        val c = cached(ctx, p.id)
        if (!force && isFresh(c)) return@withContext c!!
        coroutineScope {
            val w = async { runCatching { Api.get(Api.forecastUrl(p)) } }
            val aq = async { runCatching { Api.get(Api.airUrl(p)) } }
            val al = async { runCatching { Api.alerts(p) } }
            val wr = w.await().getOrNull()
            if (wr == null) {
                c?.asStale()?.also { mem[p.id] = it } ?: throw w.await().exceptionOrNull() ?: IllegalStateException("No data")
            } else {
                val b = WeatherBundle(
                    p, System.currentTimeMillis(), Weather(wr),
                    aq.await().getOrNull()?.let { runCatching { Air(it) }.getOrNull() } ?: c?.air,
                    al.await().getOrNull() ?: c?.alerts ?: emptyList(),
                )
                mem[p.id] = b
                runCatching { file(ctx, p.id).writeText(b.toJson().toString()) }
                b
            }
        }
    }

    /** Refresh the GPS place. Returns the place, or null if location isn't available. */
    suspend fun locate(ctx: Context, select: Boolean): Place? = withContext(Dispatchers.IO) {
        val loc = LocationHelper.current(ctx) ?: return@withContext null
        val lat = Math.round(loc.latitude * 10000) / 10000.0
        val lon = Math.round(loc.longitude * 10000) / 10000.0
        val prev = Prefs.places.firstOrNull { it.gps }
        val moved = prev == null || Math.abs(prev.lat - lat) > 0.01 || Math.abs(prev.lon - lon) > 0.01
        val place = if (moved) {
            (Api.reverse(lat, lon) ?: Place("gps", "My location", "%.2f, %.2f".format(lat, lon), "", "", lat, lon, true))
                .copy(id = "gps", lat = lat, lon = lon, gps = true)
        } else prev!!
        synchronized(Prefs) {
            val i = Prefs.places.indexOfFirst { it.gps }
            if (i >= 0) Prefs.places[i] = place else Prefs.places.add(0, place)
            if (Prefs.firstRun) {
                Prefs.places.removeAll { it.id == Prefs.DEFAULT_PLACE.id }
                Prefs.firstRun = false
            }
            if (select) Prefs.activeId = "gps"
            Prefs.save()
        }
        if (moved) drop(ctx, "gps")
        place
    }

    /**
     * The update: optionally sync the current location, fetch weather for it (and the place on screen),
     * redraw the home-screen widgets and send notifications.
     */
    suspend fun cycle(ctx: Context, force: Boolean, foreground: Boolean, relocate: Boolean = false): WeatherBundle? = cycleLock.withLock {
        Prefs.init(ctx)
        // Location syncs every time the app is opened; background runs reuse the last synced spot.
        if ((relocate && Prefs.followGps) || Prefs.firstRun) {
            runCatching { locate(ctx, select = foreground && (Prefs.activeId == "gps" || Prefs.firstRun)) }
        }
        val home = Prefs.home()
        val hb = runCatching { fetch(ctx, home, force) }.getOrNull()
        if (foreground && Prefs.activeId != home.id) runCatching { fetch(ctx, Prefs.active(), force) }
        Widgets.updateAll(ctx)
        if (hb != null && !hb.stale) Notifier.process(ctx, hb)
        runCatching { Updates.check(ctx, force = false) }
        hb
    }
}

object Updates {
    /** Checks GitHub Releases for a newer APK (at most every 6 hours unless forced). */
    suspend fun check(ctx: Context, force: Boolean): Api.Release? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - Prefs.lastUpdateCheck < 6 * 3600_000L) return@withContext null
        Prefs.lastUpdateCheck = now
        Prefs.save()
        val rel = Api.latestRelease() ?: return@withContext null
        val mine = com.hootywhooo86.bitweather.BuildConfig.VERSION_CODE
        if (rel.code > mine) {
            if (Prefs.notifyUpdates && Prefs.lastUpdateNotified != rel.code) {
                Notifier.appUpdate(ctx, rel)
                Prefs.lastUpdateNotified = rel.code
                Prefs.save()
            }
            rel
        } else null
    }
}
