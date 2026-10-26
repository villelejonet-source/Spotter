package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Background sync with WorkManager: every few hours, and soon after workouts. */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Runs after any sync already in flight instead of cancelling it (APPEND_OR_REPLACE). */
    override fun syncSoon() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-soon",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }
}

/** Made by Koin's worker factory (`workerOf(::SyncWorker)` in [androidDataModule]). */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val sync: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sync.syncNow()) Result.success() else Result.retry()
}
