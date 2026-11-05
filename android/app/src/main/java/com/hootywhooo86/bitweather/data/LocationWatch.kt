package com.hootywhooo86.bitweather.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks Android to report when you move (about 500 m, at most every 5 minutes), so widgets follow you
 * without opening the app. The passive provider reuses fixes other apps already get, so it costs
 * almost no battery. Needs "Allow all the time".
 */
object LocationWatch {
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 20, Intent(ctx, LocationReceiver::class.java),
        // the system adds the location to this intent, so it must stay mutable
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    )

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        if (!Prefs.followGps || !LocationHelper.hasPermission(ctx) || !LocationHelper.hasBackground(ctx)) {
            runCatching { lm.removeUpdates(pi(ctx)) }
            return
        }
        for (p in listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (p != LocationManager.PASSIVE_PROVIDER && runCatching { !lm.isProviderEnabled(p) }.getOrDefault(true)) continue
            runCatching { lm.requestLocationUpdates(p, 5 * 60_000L, 500f, pi(ctx)) }
        }
    }
}

class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val loc: Location = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)) ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { withTimeoutOrNull(40_000) { Repo.onMoved(ctx, loc) } } finally { done.finish() }
        }
    }
}
