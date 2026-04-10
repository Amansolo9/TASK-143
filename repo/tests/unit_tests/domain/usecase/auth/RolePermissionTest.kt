package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.domain.model.DefaultRolePermissions
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RolePermissionTest {

    @Test
    fun `administrator has all permissions`() {
        val adminPerms = DefaultRolePermissions.permissionsForRole(RoleType.ADMINISTRATOR)
        assertThat(adminPerms).containsExactlyElementsIn(Permission.entries)
    }

    @Test
    fun `learner can only create orders and request enrollment`() {
        val learnerPerms = DefaultRolePermissions.permissionsForRole(RoleType.LEARNER)
        assertThat(learnerPerms).containsExactly(
            Permission.ENROLLMENT_REQUEST,
            Permission.ORDER_CREATE
        )
    }

    @Test
    fun `finance clerk has payment and refund permissions`() {
        val financePerms = DefaultRolePermissions.permissionsForRole(RoleType.FINANCE_CLERK)
        assertThat(financePerms).contains(Permission.PAYMENT_RECORD)
        assertThat(financePerms).contains(Permission.REFUND_ISSUE)
        assertThat(financePerms).contains(Permission.PAYMENT_RECONCILE)
    }

    @Test
    fun `finance clerk cannot manage users`() {
        val financePerms = DefaultRolePermissions.permissionsForRole(RoleType.FINANCE_CLERK)
        assertThat(financePerms).doesNotContain(Permission.USER_MANAGE)
    }

    @Test
    fun `registrar has enrollment review permission`() {
        val registrarPerms = DefaultRolePermissions.permissionsForRole(RoleType.REGISTRAR)
        assertThat(registrarPerms).contains(Permission.ENROLLMENT_REVIEW)
        assertThat(registrarPerms).contains(Permission.ENROLLMENT_OVERRIDE_CAPACITY)
    }

    @Test
    fun `instructor has assessment permissions`() {
        val instructorPerms = DefaultRolePermissions.permissionsForRole(RoleType.INSTRUCTOR)
        assertThat(instructorPerms).contains(Permission.ASSESSMENT_CREATE)
        assertThat(instructorPerms).contains(Permission.ASSESSMENT_GRADE)
        assertThat(instructorPerms).contains(Permission.ASSESSMENT_REOPEN)
    }

    @Test
    fun `teaching assistant has limited grading only`() {
        val taPerms = DefaultRolePermissions.permissionsForRole(RoleType.TEACHING_ASSISTANT)
        assertThat(taPerms).containsExactly(Permission.ASSESSMENT_GRADE)
    }

    @Test
    fun `registrar cannot record payments`() {
        val registrarPerms = DefaultRolePermissions.permissionsForRole(RoleType.REGISTRAR)
        assertThat(registrarPerms).doesNotContain(Permission.PAYMENT_RECORD)
    }

    @Test
    fun `permission fromCapability resolves correctly`() {
        val perm = Permission.fromCapability("catalog.manage")
        assertThat(perm).isEqualTo(Permission.CATALOG_MANAGE)
    }

    @Test
    fun `permission fromCapability returns null for unknown`() {
        val perm = Permission.fromCapability("nonexistent.capability")
        assertThat(perm).isNull()
    }

    @Test
    fun `every role type has at least one permission`() {
        RoleType.entries.forEach { roleType ->
            val perms = DefaultRolePermissions.permissionsForRole(roleType)
            assertThat(perms).isNotEmpty()
        }
    }
}
