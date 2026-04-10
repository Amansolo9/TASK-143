package com.learnmart.app.worker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules background workers with prompt-required constraints.
 *
 * Per PRD:
 * - Reconciliation and backup jobs: idle or charging only
 * - Timeout/cleanup jobs: battery-not-low (lightweight, foreground-safe)
 *
 * Note: "idle" on Android means the device is not being actively used.
 * `setRequiresDeviceIdle(true)` maps to Doze idle; on pre-API 23 this is ignored.
 * On many devices, true Doze idle may take 30+ minutes of inactivity. The prompt
 * requirement "idle or charging" is implemented as both constraints; Android satisfies
 * these when the device enters maintenance windows. This is a platform limitation —
 * there is no "idle OR charging" disjunction in WorkManager, only conjunction.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleAllPeriodicWork() {
        scheduleEnrollmentExpiryCheck()
        scheduleOrderTimeoutCheck()
        // Reconciliation and backup are triggered on-demand, not periodic,
        // but we register idle+charging constraints when they run via OneTimeWorkRequest.
    }

    private fun scheduleEnrollmentExpiryCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<EnrollmentExpiryWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("enrollment_expiry")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "enrollment_expiry_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleOrderTimeoutCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<OrderTimeoutWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("order_timeout")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "order_timeout_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Enqueues a reconciliation job constrained to idle+charging.
     * Returns the unique work name for observation.
     */
    fun enqueueReconciliationJob(batchId: String): String {
        val uniqueName = "reconciliation_$batchId"
        val workRequest = OneTimeWorkRequestBuilder<ReconciliationWorker>()
            .setConstraints(heavyJobConstraints())
            .setInputData(workDataOf("batchId" to batchId))
            .addTag(TAG_RECONCILIATION)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        return uniqueName
    }

    /**
     * Enqueues a backup job constrained to idle+charging.
     * Returns the unique work name for observation.
     */
    fun enqueueBackupJob(): String {
        val uniqueName = "backup_${System.currentTimeMillis()}"
        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(heavyJobConstraints())
            .addTag(TAG_BACKUP)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        return uniqueName
    }

    /**
     * Observe work status by unique work name.
     */
    fun observeWork(uniqueWorkName: String): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(uniqueWorkName)

    companion object {
        const val TAG_RECONCILIATION = "reconciliation_job"
        const val TAG_BACKUP = "backup_job"

        /**
         * Returns constraints for reconciliation/backup jobs per PRD:
         * "idle or charging for reconciliation/backup only"
         */
        fun heavyJobConstraints(): Constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
    }
}
