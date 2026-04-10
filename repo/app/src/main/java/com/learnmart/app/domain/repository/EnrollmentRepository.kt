package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface EnrollmentRepository {
    // EnrollmentRequest
    suspend fun createRequest(request: EnrollmentRequest): EnrollmentRequest
    suspend fun updateRequest(request: EnrollmentRequest): Boolean
    suspend fun getRequestById(id: String): EnrollmentRequest?
    suspend fun getRequestsForClassOffering(classOfferingId: String): List<EnrollmentRequest>
    suspend fun getRequestsForLearner(learnerId: String): List<EnrollmentRequest>
    suspend fun getRequestsByClassAndStatus(classOfferingId: String, status: EnrollmentRequestStatus): List<EnrollmentRequest>
    suspend fun countActivePendingRequests(learnerId: String, classOfferingId: String): Int
    suspend fun getExpiredRequests(currentTime: Long): List<EnrollmentRequest>
    suspend fun updateRequestStatus(id: String, status: EnrollmentRequestStatus, currentVersion: Int): Boolean
    fun getPendingRequests(): Flow<List<EnrollmentRequest>>

    // EligibilitySnapshot
    suspend fun createEligibilitySnapshot(snapshot: EnrollmentEligibilitySnapshot)
    suspend fun getEligibilitySnapshot(requestId: String): EnrollmentEligibilitySnapshot?

    // ApprovalFlow
    suspend fun createApprovalFlowDefinition(flow: ApprovalFlowDefinition): ApprovalFlowDefinition
    suspend fun getApprovalFlowById(id: String): ApprovalFlowDefinition?
    suspend fun getApprovalFlowsForClass(classOfferingId: String): List<ApprovalFlowDefinition>
    suspend fun getDefaultApprovalFlow(): ApprovalFlowDefinition?

    // ApprovalStep
    suspend fun createApprovalStepDefinitions(steps: List<ApprovalStepDefinition>)
    suspend fun getStepsForFlow(flowId: String): List<ApprovalStepDefinition>

    // ApprovalTask
    suspend fun createApprovalTask(task: EnrollmentApprovalTask)
    suspend fun createApprovalTasks(tasks: List<EnrollmentApprovalTask>)
    suspend fun updateApprovalTask(task: EnrollmentApprovalTask)
    suspend fun getApprovalTaskById(id: String): EnrollmentApprovalTask?
    suspend fun getTasksForRequest(requestId: String): List<EnrollmentApprovalTask>
    suspend fun getPendingTasksForRequest(requestId: String): List<EnrollmentApprovalTask>
    suspend fun getPendingTasksByRole(roleType: RoleType): List<EnrollmentApprovalTask>
    suspend fun getPendingTasksForUser(userId: String): List<EnrollmentApprovalTask>
    suspend fun getExpiredTasks(currentTime: Long): List<EnrollmentApprovalTask>

    // DecisionEvent
    suspend fun createDecisionEvent(event: EnrollmentDecisionEvent)
    suspend fun getDecisionEventsForRequest(requestId: String): List<EnrollmentDecisionEvent>

    // Waitlist
    suspend fun createWaitlistEntry(entry: WaitlistEntry)
    suspend fun updateWaitlistEntry(entry: WaitlistEntry)
    suspend fun getWaitlistEntryById(id: String): WaitlistEntry?
    suspend fun getActiveWaitlistForClass(classOfferingId: String): List<WaitlistEntry>
    suspend fun getNextWaitlistEntry(classOfferingId: String): WaitlistEntry?
    suspend fun countActiveWaitlistForClass(classOfferingId: String): Int
    suspend fun getActiveWaitlistForLearnerAndClass(learnerId: String, classOfferingId: String): WaitlistEntry?
    suspend fun getExpiredOffers(currentTime: Long): List<WaitlistEntry>
    suspend fun getMaxWaitlistPosition(classOfferingId: String): Int

    // EnrollmentRecord
    suspend fun createEnrollmentRecord(record: EnrollmentRecord)
    suspend fun updateEnrollmentRecord(record: EnrollmentRecord)
    suspend fun getEnrollmentRecordById(id: String): EnrollmentRecord?
    suspend fun getActiveEnrollment(learnerId: String, classOfferingId: String): EnrollmentRecord?
    suspend fun getActiveEnrollmentsForClass(classOfferingId: String): List<EnrollmentRecord>
    suspend fun countActiveEnrollmentsForClass(classOfferingId: String): Int
    suspend fun getEnrollmentsForLearner(learnerId: String): List<EnrollmentRecord>

    // EnrollmentException
    suspend fun createEnrollmentException(exception: EnrollmentException)
    suspend fun getExceptionsForRequest(requestId: String): List<EnrollmentException>
}
