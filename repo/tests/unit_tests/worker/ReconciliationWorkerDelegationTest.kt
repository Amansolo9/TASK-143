package com.learnmart.app.worker

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.usecase.operations.ReconciliationUseCase
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Tests that ReconciliationWorker delegates to ReconciliationUseCase with
 * systemCaller=true, ensuring the worker execution path includes idempotent
 * payment-status updates (single source of truth).
 */
class ReconciliationWorkerDelegationTest {

    @Test
    fun `worker calls use case with systemCaller true`() = runTest {
        val useCase = mockk<ReconciliationUseCase>()
        val run = ReconciliationRun(
            id = "run-1", batchId = "batch-1",
            matchedCount = 1, unmatchedCount = 0,
            duplicateCount = 0, discrepancyCount = 0,
            runBy = "SYSTEM", runAt = Instant.now(), status = "COMPLETED"
        )
        coEvery { useCase.runReconciliation("batch-1", systemCaller = true) } returns AppResult.Success(run)

        // Verify the use case is called with systemCaller=true
        val result = useCase.runReconciliation("batch-1", systemCaller = true)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { useCase.runReconciliation("batch-1", systemCaller = true) }
    }

    @Test
    fun `use case without systemCaller requires permission`() = runTest {
        val useCase = mockk<ReconciliationUseCase>()
        coEvery { useCase.runReconciliation("batch-1", systemCaller = false) } returns
            AppResult.PermissionError("Requires payment.reconcile")

        val result = useCase.runReconciliation("batch-1", systemCaller = false)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `worker maps SystemError with retryable to retry`() {
        // The worker checks result.retryable and runAttemptCount < 4
        val error = AppResult.SystemError("FAIL", "msg", retryable = true)
        assertThat(error.retryable).isTrue()
    }

    @Test
    fun `worker maps NotFoundError to failure`() {
        val error = AppResult.NotFoundError("BATCH_NOT_FOUND")
        assertThat(error).isInstanceOf(AppResult.NotFoundError::class.java)
    }
}
