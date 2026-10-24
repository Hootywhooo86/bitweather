package com.hootywhooo86.bitweather.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hootywhooo86.bitweather.data.Repo
import java.util.concurrent.TimeUnit

/** Safety net every 15 minutes (WorkManager's minimum) in case the 10-minute ticker was held back. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching {
        val ctx = applicationContext
        com.hootywhooo86.bitweather.data.Prefs.init(ctx)
        if (!Repo.isFresh(Repo.cached(ctx, com.hootywhooo86.bitweather.data.Prefs.home().id))) {
            com.hootywhooo86.bitweather.notify.Ticker.tick(ctx)
        }
        // revive the 10-minute heartbeat if the system dropped it
        com.hootywhooo86.bitweather.notify.Ticker.schedule(ctx)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork("bw-30min")
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES).setConstraints(net).build()
            wm.enqueueUniquePeriodicWork("bw-15min", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun now(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("bw-now", ExistingWorkPolicy.KEEP, req)
        }
    }
}
