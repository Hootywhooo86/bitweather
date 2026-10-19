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

/** Background update every 30 minutes: location, weather, widgets, notifications. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching {
        Repo.cycle(applicationContext, force = inputData.getBoolean("force", false), foreground = false)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(30, TimeUnit.MINUTES).setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("bw-30min", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun now(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("bw-now", ExistingWorkPolicy.KEEP, req)
        }
    }
}
