package com.learnmart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.learnmart.app.data.local.dao.AuditDao
import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.data.local.dao.AssessmentDao
import com.learnmart.app.data.local.dao.OperationsDao
import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.dao.CourseDao
import com.learnmart.app.data.local.dao.EnrollmentDao
import com.learnmart.app.data.local.dao.PaymentDao
import com.learnmart.app.data.local.dao.PolicyDao
import com.learnmart.app.data.local.dao.RoleDao
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.data.local.dao.UserDao
import com.learnmart.app.data.local.entity.*

@Database(
    entities = [
        // Phase 1: Identity, Policy, Audit
        UserEntity::class,
        RoleEntity::class,
        RolePermissionEntity::class,
        UserRoleAssignmentEntity::class,
        SessionEntity::class,
        AuditEventEntity::class,
        StateTransitionLogEntity::class,
        PolicyEntity::class,
        PolicyHistoryEntity::class,
        BlacklistFlagEntity::class,
        // Phase 2: Course, Class, Enrollment
        CourseEntity::class,
        CourseVersionEntity::class,
        CourseMaterialEntity::class,
        PublicationEventEntity::class,
        ClassOfferingEntity::class,
        ClassSessionEntity::class,
        ClassStaffAssignmentEntity::class,
        CapacityOverrideEntity::class,
        EnrollmentRequestEntity::class,
        EnrollmentEligibilitySnapshotEntity::class,
        ApprovalFlowDefinitionEntity::class,
        ApprovalStepDefinitionEntity::class,
        EnrollmentApprovalTaskEntity::class,
        EnrollmentDecisionEventEntity::class,
        WaitlistEntryEntity::class,
        EnrollmentRecordEntity::class,
        EnrollmentExceptionEntity::class,
        // Phase 3: Commerce, Orders, Payments
        CartEntity::class,
        CartLineItemEntity::class,
        QuoteSnapshotEntity::class,
        OrderEntity::class,
        OrderLineItemEntity::class,
        OrderPriceComponentEntity::class,
        InventoryItemEntity::class,
        InventoryLockEntity::class,
        FulfillmentRecordEntity::class,
        DeliveryConfirmationEntity::class,
        ReturnExchangeRecordEntity::class,
        PaymentRecordEntity::class,
        PaymentAllocationEntity::class,
        RefundRecordEntity::class,
        LedgerEntryEntity::class,
        IdempotencyTokenEntity::class,
        // Phase 4: Assessment, Grading, Similarity
        QuestionBankEntity::class,
        KnowledgeTagEntity::class,
        QuestionEntity::class,
        QuestionChoiceEntity::class,
        AssignmentEntity::class,
        AssessmentReleaseWindowEntity::class,
        SubmissionEntity::class,
        SubmissionAnswerEntity::class,
        ObjectiveGradeResultEntity::class,
        SubjectiveGradeQueueItemEntity::class,
        GradeDecisionEntity::class,
        WrongAnswerExplanationLinkEntity::class,
        SimilarityFingerprintEntity::class,
        SimilarityMatchResultEntity::class,
        // Phase 5: Operations
        ImportJobEntity::class,
        SettlementImportBatchEntity::class,
        SettlementImportRowEntity::class,
        ReconciliationRunEntity::class,
        ReconciliationMatchEntity::class,
        DiscrepancyCaseEntity::class,
        ExportJobEntity::class,
        BackupArchiveEntity::class,
        RestoreRunEntity::class,
        MaintenanceJobRunEntity::class,
        DataIntegrityIssueEntity::class,
        SettlementPaymentUpdateEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class LearnMartRoomDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun roleDao(): RoleDao
    abstract fun sessionDao(): SessionDao
    abstract fun auditDao(): AuditDao
    abstract fun policyDao(): PolicyDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun courseDao(): CourseDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun commerceDao(): CommerceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun operationsDao(): OperationsDao
}
