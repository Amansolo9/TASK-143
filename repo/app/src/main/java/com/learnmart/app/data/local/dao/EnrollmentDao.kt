package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.learnmart.app.data.local.entity.ApprovalFlowDefinitionEntity
import com.learnmart.app.data.local.entity.ApprovalStepDefinitionEntity
import com.learnmart.app.data.local.entity.EnrollmentApprovalTaskEntity
import com.learnmart.app.data.local.entity.EnrollmentDecisionEventEntity
import com.learnmart.app.data.local.entity.EnrollmentEligibilitySnapshotEntity
import com.learnmart.app.data.local.entity.EnrollmentExceptionEntity
import com.learnmart.app.data.local.entity.EnrollmentRecordEntity
import com.learnmart.app.data.local.entity.EnrollmentRequestEntity
import com.learnmart.app.data.local.entity.WaitlistEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrollmentDao {

    // --- EnrollmentRequest ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEnrollmentRequest(request: EnrollmentRequestEntity)

    @Update
    suspend fun updateEnrollmentRequest(request: EnrollmentRequestEntity)

    @Query("SELECT * FROM enrollment_requests WHERE id = :id")
    suspend fun getEnrollmentRequestById(id: String): EnrollmentRequestEntity?

    @Query("""
        SELECT * FROM enrollment_requests
        WHERE class_offering_id = :classOfferingId
        ORDER BY created_at DESC
    """)
    suspend fun getRequestsForClassOffering(classOfferingId: String): List<EnrollmentRequestEntity>

    @Query("""
        SELECT * FROM enrollment_requests
        WHERE learner_id = :learnerId
        ORDER BY created_at DESC
    """)
    suspend fun getRequestsForLearner(learnerId: String): List<EnrollmentRequestEntity>

    @Query("""
        SELECT * FROM enrollment_requests
        WHERE class_offering_id = :classOfferingId AND status = :status
        ORDER BY created_at ASC
    """)
    suspend fun getRequestsByClassAndStatus(classOfferingId: String, status: String): List<EnrollmentRequestEntity>

    @Query("""
        SELECT COUNT(*) FROM enrollment_requests
        WHERE learner_id = :learnerId
        AND class_offering_id = :classOfferingId
        AND status IN ('DRAFT', 'SUBMITTED', 'PENDING_APPROVAL', 'WAITLISTED', 'OFFERED', 'APPROVED')
    """)
    suspend fun countActivePendingRequestsForLearnerAndClass(learnerId: String, classOfferingId: String): Int

    @Query("""
        SELECT * FROM enrollment_requests
        WHERE status IN ('SUBMITTED', 'PENDING_APPROVAL', 'OFFERED')
        AND expires_at IS NOT NULL
        AND expires_at < :currentTime
    """)
    suspend fun getExpiredRequests(currentTime: Long): List<EnrollmentRequestEntity>

    @Query("""
        UPDATE enrollment_requests
        SET status = :status, updated_at = :updatedAt, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updateRequestStatus(id: String, status: String, updatedAt: Long, currentVersion: Int): Int

    @Query("""
        SELECT * FROM enrollment_requests
        WHERE status IN ('PENDING_APPROVAL', 'SUBMITTED')
        ORDER BY created_at ASC
    """)
    fun getPendingRequests(): Flow<List<EnrollmentRequestEntity>>

    // --- EligibilitySnapshot ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEligibilitySnapshot(snapshot: EnrollmentEligibilitySnapshotEntity)

    @Query("SELECT * FROM enrollment_eligibility_snapshots WHERE enrollment_request_id = :requestId")
    suspend fun getEligibilitySnapshot(requestId: String): EnrollmentEligibilitySnapshotEntity?

    // --- ApprovalFlow ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalFlowDefinition(flow: ApprovalFlowDefinitionEntity)

    @Query("SELECT * FROM approval_flow_definitions WHERE id = :id")
    suspend fun getApprovalFlowById(id: String): ApprovalFlowDefinitionEntity?

    @Query("SELECT * FROM approval_flow_definitions WHERE class_offering_id = :classOfferingId")
    suspend fun getApprovalFlowsForClass(classOfferingId: String): List<ApprovalFlowDefinitionEntity>

    @Query("SELECT * FROM approval_flow_definitions WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultApprovalFlow(): ApprovalFlowDefinitionEntity?

    // --- ApprovalStep ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalStepDefinition(step: ApprovalStepDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalStepDefinitions(steps: List<ApprovalStepDefinitionEntity>)

    @Query("SELECT * FROM approval_step_definitions WHERE flow_id = :flowId ORDER BY step_order ASC")
    suspend fun getStepsForFlow(flowId: String): List<ApprovalStepDefinitionEntity>

    // --- ApprovalTask ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalTask(task: EnrollmentApprovalTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalTasks(tasks: List<EnrollmentApprovalTaskEntity>)

    @Update
    suspend fun updateApprovalTask(task: EnrollmentApprovalTaskEntity)

    @Query("SELECT * FROM enrollment_approval_tasks WHERE id = :id")
    suspend fun getApprovalTaskById(id: String): EnrollmentApprovalTaskEntity?

    @Query("SELECT * FROM enrollment_approval_tasks WHERE enrollment_request_id = :requestId ORDER BY created_at ASC")
    suspend fun getTasksForRequest(requestId: String): List<EnrollmentApprovalTaskEntity>

    @Query("""
        SELECT * FROM enrollment_approval_tasks
        WHERE enrollment_request_id = :requestId AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingTasksForRequest(requestId: String): List<EnrollmentApprovalTaskEntity>

    @Query("""
        SELECT * FROM enrollment_approval_tasks
        WHERE assigned_to_role_type = :roleType AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingTasksByRole(roleType: String): List<EnrollmentApprovalTaskEntity>

    @Query("""
        SELECT * FROM enrollment_approval_tasks
        WHERE assigned_to_user_id = :userId AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingTasksForUser(userId: String): List<EnrollmentApprovalTaskEntity>

    @Query("""
        SELECT * FROM enrollment_approval_tasks
        WHERE status = 'PENDING' AND expires_at < :currentTime
    """)
    suspend fun getExpiredTasks(currentTime: Long): List<EnrollmentApprovalTaskEntity>

    // --- DecisionEvent ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecisionEvent(event: EnrollmentDecisionEventEntity)

    @Query("SELECT * FROM enrollment_decision_events WHERE enrollment_request_id = :requestId ORDER BY timestamp ASC")
    suspend fun getDecisionEventsForRequest(requestId: String): List<EnrollmentDecisionEventEntity>

    // --- Waitlist ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWaitlistEntry(entry: WaitlistEntryEntity)

    @Update
    suspend fun updateWaitlistEntry(entry: WaitlistEntryEntity)

    @Query("SELECT * FROM waitlist_entries WHERE id = :id")
    suspend fun getWaitlistEntryById(id: String): WaitlistEntryEntity?

    @Query("""
        SELECT * FROM waitlist_entries
        WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'
        ORDER BY priority_tier ASC, added_at ASC, id ASC
    """)
    suspend fun getActiveWaitlistForClass(classOfferingId: String): List<WaitlistEntryEntity>

    @Query("""
        SELECT * FROM waitlist_entries
        WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'
        ORDER BY priority_tier ASC, added_at ASC, id ASC
        LIMIT 1
    """)
    suspend fun getNextWaitlistEntry(classOfferingId: String): WaitlistEntryEntity?

    @Query("SELECT COUNT(*) FROM waitlist_entries WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'")
    suspend fun countActiveWaitlistForClass(classOfferingId: String): Int

    @Query("""
        SELECT * FROM waitlist_entries
        WHERE learner_id = :learnerId AND class_offering_id = :classOfferingId AND status = 'ACTIVE'
    """)
    suspend fun getActiveWaitlistForLearnerAndClass(learnerId: String, classOfferingId: String): WaitlistEntryEntity?

    @Query("""
        SELECT * FROM waitlist_entries
        WHERE status = 'OFFERED' AND offer_expires_at IS NOT NULL AND offer_expires_at < :currentTime
    """)
    suspend fun getExpiredOffers(currentTime: Long): List<WaitlistEntryEntity>

    @Query("""
        SELECT MAX(position) FROM waitlist_entries
        WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'
    """)
    suspend fun getMaxWaitlistPosition(classOfferingId: String): Int?

    // --- EnrollmentRecord ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEnrollmentRecord(record: EnrollmentRecordEntity)

    @Update
    suspend fun updateEnrollmentRecord(record: EnrollmentRecordEntity)

    @Query("SELECT * FROM enrollment_records WHERE id = :id")
    suspend fun getEnrollmentRecordById(id: String): EnrollmentRecordEntity?

    @Query("SELECT * FROM enrollment_records WHERE learner_id = :learnerId AND class_offering_id = :classOfferingId AND status = 'ACTIVE'")
    suspend fun getActiveEnrollment(learnerId: String, classOfferingId: String): EnrollmentRecordEntity?

    @Query("SELECT * FROM enrollment_records WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'")
    suspend fun getActiveEnrollmentsForClass(classOfferingId: String): List<EnrollmentRecordEntity>

    @Query("SELECT COUNT(*) FROM enrollment_records WHERE class_offering_id = :classOfferingId AND status = 'ACTIVE'")
    suspend fun countActiveEnrollmentsForClass(classOfferingId: String): Int

    @Query("SELECT * FROM enrollment_records WHERE learner_id = :learnerId ORDER BY enrolled_at DESC")
    suspend fun getEnrollmentsForLearner(learnerId: String): List<EnrollmentRecordEntity>

    // --- EnrollmentException ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEnrollmentException(exception: EnrollmentExceptionEntity)

    @Query("SELECT * FROM enrollment_exceptions WHERE enrollment_request_id = :requestId")
    suspend fun getExceptionsForRequest(requestId: String): List<EnrollmentExceptionEntity>
}
