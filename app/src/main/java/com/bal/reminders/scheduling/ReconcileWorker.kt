package com.bal.reminders.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Non-exact defence-in-depth: once a day, re-derive every alarm from the
 * database in case an OEM battery manager silently dropped one. User-facing
 * trigger times never rely on WorkManager.
 */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        scheduler.rescheduleAll(fireMissed = true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "reconcile_alarms"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReconcileWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
