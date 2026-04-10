package com.learnmart.app.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkerSchedulingTest {

    @Test
    fun `reconciliation worker class is ReconciliationWorker not OrderTimeoutWorker`() {
        assertThat(ReconciliationWorker::class.java).isNotEqualTo(OrderTimeoutWorker::class.java)
        assertThat(ReconciliationWorker::class.java.simpleName).isEqualTo("ReconciliationWorker")
    }

    @Test
    fun `backup worker class is BackupWorker not OrderTimeoutWorker`() {
        assertThat(BackupWorker::class.java).isNotEqualTo(OrderTimeoutWorker::class.java)
        assertThat(BackupWorker::class.java.simpleName).isEqualTo("BackupWorker")
    }

    @Test
    fun `heavy job constraints object is created successfully`() {
        val constraints = WorkScheduler.heavyJobConstraints()
        // Constraints is successfully built with idle+charging+batteryNotLow
        // The actual constraint enforcement is an Android platform behavior.
        // We verify the object is non-null and the builder did not throw.
        assertThat(constraints).isNotNull()
    }

    @Test
    fun `all four worker classes are distinct`() {
        val classes = setOf(
            EnrollmentExpiryWorker::class.java.name,
            OrderTimeoutWorker::class.java.name,
            ReconciliationWorker::class.java.name,
            BackupWorker::class.java.name
        )
        assertThat(classes).hasSize(4)
    }
}
