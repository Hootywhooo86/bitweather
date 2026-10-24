package com.hootywhooo86.bitweather.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.work.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The 10-minute heartbeat: current location, weather, widgets, alerts, and the 6-hour forecast when due.
 * WorkManager can't run more often than every 15 minutes, so this is an alarm allowed while the phone is idle.
 */
object Ticker {
    const val INTERVAL_MS = 10 * 60_000L

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 10, Intent(ctx, TickReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context, delayMs: Long = INTERVAL_MS) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMs
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        }
    }

    /** Runs one tick: refresh with current location, then make sure a due 6-hour forecast goes out. */
    suspend fun tick(ctx: Context) {
        Prefs.init(ctx)
        withTimeoutOrNull(45_000) { Repo.cycle(ctx, force = true, foreground = false, relocate = true) }
        // offline or slow network: still send the forecast from the last data we have
        if (Notifier.summaryDue()) Repo.cached(ctx, Prefs.home().id)?.let { Notifier.summary(ctx, it) }
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Ticker.schedule(ctx) // chain the next tick first, so a failed run never stops the heartbeat
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Ticker.tick(ctx) } finally { done.finish() }
        }
    }
}

/** Alarms and work are cleared on reboot or update; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        runCatching { UpdateWorker.schedule(ctx) }
        Ticker.schedule(ctx, 30_000L)
    }
}
