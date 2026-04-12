package com.learnmart.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnmart.app.domain.usecase.operations.ReconciliationUseCase
import com.learnmart.app.util.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Dedicated worker for reconciliation jobs. Delegates to [ReconciliationUseCase]
 * which performs matching, discrepancy creation, idempotent payment-status updates,
 * and audit logging — all within a single Room transaction.
 *
 * This ensures the worker and the use-case share a single source of truth for
 * reconciliation logic, preventing drift between the two execution paths.
 *
 * Scheduled with idle+charging constraints via [WorkScheduler].
 */
@HiltWorker
class ReconciliationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reconciliationUseCase: ReconciliationUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val batchId = inputData.getString("batchId") ?: return Result.failure()

        return when (val result = reconciliationUseCase.runReconciliation(batchId, systemCaller = true)) {
            is AppResult.Success -> Result.success()
            is AppResult.SystemError -> {
                if (result.retryable && runAttemptCount < 4) Result.retry()
                else Result.failure()
            }
            is AppResult.NotFoundError -> Result.failure()
            is AppResult.ValidationError -> Result.failure()
            is AppResult.PermissionError -> {
                // Worker runs as SYSTEM; permission errors shouldn't occur but fail gracefully
                Result.failure()
            }
            is AppResult.ConflictError -> Result.failure()
        }
    }
}
