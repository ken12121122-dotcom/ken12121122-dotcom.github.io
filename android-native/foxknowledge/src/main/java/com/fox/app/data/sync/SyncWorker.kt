package com.fox.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fox.app.FoxDependencies
import java.util.concurrent.TimeUnit

/**
 * Periodic read-only sync for every configured FOX knowledge save slot.
 *
 * Profiles without a persisted SAF source are skipped. Each profile owns its
 * own Room database, so background refresh cannot mix nodes across knowledge bases.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = FoxDependencies.get(applicationContext)
        val profiles = deps.profileStore.listProfiles().filter { !it.treeUri.isNullOrBlank() }
        if (profiles.isEmpty()) return Result.success()

        var failures = 0
        for (profile in profiles) {
            try {
                deps.syncRepositoryFor(profile.id).syncOnce()
            } catch (_: Exception) {
                failures++
            }
        }

        return if (failures == profiles.size) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "fox-drive-sync"

        fun schedulePeriodic(context: Context, intervalHours: Long = 6) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
