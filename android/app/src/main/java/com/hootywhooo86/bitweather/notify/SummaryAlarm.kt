package com.hootywhooo86.bitweather.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.work.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes the phone every 6 hours for the forecast notification. An alarm (allowed while idle) fires
 * reliably even when Android is holding background work back.
 */
object SummaryAlarm {
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 6, Intent(ctx, SummaryReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (!Prefs.notifyConditions) { am.cancel(pi(ctx)); return }
        val at = maxOf(System.currentTimeMillis() + 60_000L, Prefs.lastSummary + Notifier.SUMMARY_MS)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx)) }
    }
}

/** Fires every 6 hours: refresh (with current location when allowed) and post the forecast. */
class SummaryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Prefs.init(ctx)
                withTimeoutOrNull(45_000) { Repo.cycle(ctx, force = true, foreground = false, relocate = true) }
                // offline or slow network: still send the summary from the last forecast we have
                if (Notifier.summaryDue()) Repo.cached(ctx, Prefs.home().id)?.let { Notifier.summary(ctx, it) }
                SummaryAlarm.schedule(ctx)
            } finally {
                done.finish()
            }
        }
    }
}

/** Alarms and work are cleared on reboot or update; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        runCatching { UpdateWorker.schedule(ctx) }
        SummaryAlarm.schedule(ctx)
    }
}
