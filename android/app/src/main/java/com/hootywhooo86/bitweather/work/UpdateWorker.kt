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

/** Background update every 15 minutes (Android's minimum): current location, weather, widgets, alerts. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching {
        Repo.cycle(applicationContext, force = true, foreground = false, relocate = true)
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
