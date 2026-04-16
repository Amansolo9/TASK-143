package com.learnmart.app.domain.usecase.policy

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ManagePolicyUseCaseTest {
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManagePolicyUseCase

    @Before
    fun setUp() {
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManagePolicyUseCase(policyRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `getAllActivePolicies requires POLICY_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns false
        val result = useCase.getAllActivePolicies()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createPolicy requires POLICY_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns false
        val result = useCase.createPolicy(PolicyType.COMMERCE, "key", "val", "desc")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createPolicy rejects blank key`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns true
        val result = useCase.createPolicy(PolicyType.COMMERCE, "", "val", "desc")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createPolicy rejects duplicate`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns true
        coEvery { policyRepository.getActivePolicy(PolicyType.COMMERCE, "min_order") } returns Policy(
            "p1", PolicyType.COMMERCE, "min_order", "25", "desc", 1, true,
            Instant.now(), null, "admin", Instant.now(), Instant.now()
        )
        val result = useCase.createPolicy(PolicyType.COMMERCE, "min_order", "30", "desc")
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `updatePolicy requires POLICY_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns false
        val result = useCase.updatePolicy("p1", "new", null)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getPolicyById requires POLICY_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns false
        val result = useCase.getPolicyById("p1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getPolicyValue requires POLICY_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.POLICY_MANAGE) } returns false
        val result = useCase.getPolicyValue(PolicyType.COMMERCE, "key", "default")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }
}
