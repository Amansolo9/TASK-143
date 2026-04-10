package com.learnmart.app.domain.usecase.course

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

data class CreateClassOfferingRequest(
    val courseId: String,
    val title: String,
    val description: String,
    val hardCapacity: Int,
    val waitlistEnabled: Boolean,
    val scheduleStart: Instant,
    val scheduleEnd: Instant,
    val location: String
)

data class CreateClassSessionRequest(
    val classOfferingId: String,
    val sessionOrder: Int,
    val title: String,
    val sessionTime: Instant,
    val durationMinutes: Int,
    val location: String?,
    val notes: String?
)

class ManageClassUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    fun getAllActiveClassOfferings(): Flow<List<ClassOffering>> =
        courseRepository.getAllActiveClassOfferings()

    suspend fun getClassOfferingById(id: String): AppResult<ClassOffering> {
        val offering = courseRepository.getClassOfferingById(id)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")
        return AppResult.Success(offering)
    }

    suspend fun createClassOffering(request: CreateClassOfferingRequest): AppResult<ClassOffering> {
        if (!checkPermission.hasPermission(Permission.CLASS_MANAGE)) {
            return AppResult.PermissionError("Requires class.manage")
        }

        // Validate
        val errors = mutableMapOf<String, String>()
        if (request.title.isBlank()) errors["title"] = "Title is required"
        if (request.hardCapacity < 1) errors["hardCapacity"] = "Capacity must be >= 1"
        if (request.scheduleEnd.isBefore(request.scheduleStart)) {
            errors["scheduleEnd"] = "End must be after start"
        }
        if (errors.isNotEmpty()) return AppResult.ValidationError(fieldErrors = errors)

        // Verify course exists
        val course = courseRepository.getCourseById(request.courseId)
            ?: return AppResult.NotFoundError("COURSE_NOT_FOUND")

        val now = TimeUtils.nowUtc()
        val offering = ClassOffering(
            id = IdGenerator.newId(),
            courseId = request.courseId,
            title = request.title.trim(),
            description = request.description.trim(),
            status = ClassOfferingStatus.PLANNED,
            hardCapacity = request.hardCapacity,
            enrolledCount = 0,
            waitlistEnabled = request.waitlistEnabled,
            scheduleStart = request.scheduleStart,
            scheduleEnd = request.scheduleEnd,
            location = request.location.trim(),
            createdBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        courseRepository.createClassOffering(offering)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.CLASS_CREATED,
            targetEntityType = "ClassOffering",
            targetEntityId = offering.id,
            beforeSummary = null,
            afterSummary = "title=${offering.title}, capacity=${offering.hardCapacity}",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(offering)
    }

    suspend fun openClassForEnrollment(classOfferingId: String): AppResult<ClassOffering> {
        if (!checkPermission.hasAnyPermission(Permission.CLASS_MANAGE, Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError()
        }

        val offering = courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        if (!offering.status.canTransitionTo(ClassOfferingStatus.OPEN)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot open class from ${offering.status} state")
            )
        }

        // Validate: course must be published
        val course = courseRepository.getCourseById(offering.courseId)
        if (course == null || course.status != CourseStatus.PUBLISHED) {
            return AppResult.ValidationError(
                globalErrors = listOf("Course must be published before opening class for enrollment")
            )
        }

        // Validate: at least one session
        val sessionCount = courseRepository.countSessionsForClassOffering(classOfferingId)
        if (sessionCount == 0) {
            return AppResult.ValidationError(
                globalErrors = listOf("Class must have at least one session before opening")
            )
        }

        // Validate: at least one instructor
        val instructorCount = courseRepository.countInstructorsForClass(classOfferingId)
        if (instructorCount == 0) {
            return AppResult.ValidationError(
                globalErrors = listOf("Class must have at least one instructor assigned")
            )
        }

        val success = courseRepository.updateClassOfferingStatus(
            classOfferingId, ClassOfferingStatus.OPEN, offering.version
        )
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Class was modified concurrently")

        val now = TimeUtils.nowUtc()
        logClassTransition(classOfferingId, offering.status.name, ClassOfferingStatus.OPEN.name, now)

        return AppResult.Success(courseRepository.getClassOfferingById(classOfferingId)!!)
    }

    suspend fun transitionClassStatus(
        classOfferingId: String,
        targetStatus: ClassOfferingStatus,
        reason: String?
    ): AppResult<ClassOffering> {
        if (!checkPermission.hasPermission(Permission.CLASS_MANAGE)) {
            return AppResult.PermissionError()
        }

        val offering = courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        if (!offering.status.canTransitionTo(targetStatus)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot transition from ${offering.status} to $targetStatus")
            )
        }

        // OPEN requires special validation (use openClassForEnrollment instead)
        if (targetStatus == ClassOfferingStatus.OPEN) {
            return openClassForEnrollment(classOfferingId)
        }

        val success = courseRepository.updateClassOfferingStatus(
            classOfferingId, targetStatus, offering.version
        )
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Class was modified concurrently")

        logClassTransition(classOfferingId, offering.status.name, targetStatus.name, TimeUtils.nowUtc())

        return AppResult.Success(courseRepository.getClassOfferingById(classOfferingId)!!)
    }

    suspend fun addSession(request: CreateClassSessionRequest): AppResult<ClassSession> {
        if (!checkPermission.hasPermission(Permission.CLASS_MANAGE)) {
            return AppResult.PermissionError()
        }

        val errors = mutableMapOf<String, String>()
        if (request.title.isBlank()) errors["title"] = "Title is required"
        if (request.durationMinutes < 1) errors["durationMinutes"] = "Duration must be >= 1"
        if (request.sessionOrder < 1) errors["sessionOrder"] = "Session order must be >= 1"
        if (errors.isNotEmpty()) return AppResult.ValidationError(fieldErrors = errors)

        // Verify class exists
        courseRepository.getClassOfferingById(request.classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        // Check for duplicate session order
        val existingSessions = courseRepository.getSessionsForClassOffering(request.classOfferingId)
        if (existingSessions.any { it.sessionOrder == request.sessionOrder }) {
            return AppResult.ConflictError("DUPLICATE_SESSION_ORDER",
                "Session order ${request.sessionOrder} already exists")
        }

        val session = ClassSession(
            id = IdGenerator.newId(),
            classOfferingId = request.classOfferingId,
            sessionOrder = request.sessionOrder,
            title = request.title.trim(),
            sessionTime = request.sessionTime,
            durationMinutes = request.durationMinutes,
            location = request.location?.trim(),
            notes = request.notes?.trim(),
            createdAt = TimeUtils.nowUtc()
        )

        courseRepository.createClassSession(session)
        return AppResult.Success(session)
    }

    suspend fun getSessionsForClass(classOfferingId: String): List<ClassSession> =
        courseRepository.getSessionsForClassOffering(classOfferingId)

    suspend fun assignStaff(
        classOfferingId: String,
        userId: String,
        staffRole: StaffRole
    ): AppResult<ClassStaffAssignment> {
        if (!checkPermission.hasPermission(Permission.CLASS_STAFF_ASSIGN)) {
            return AppResult.PermissionError("Requires class.staff.assign")
        }

        courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        if (courseRepository.hasStaffAssignment(classOfferingId, userId, staffRole)) {
            return AppResult.ConflictError("ALREADY_ASSIGNED",
                "Staff member already assigned with role $staffRole")
        }

        val now = TimeUtils.nowUtc()
        val assignment = ClassStaffAssignment(
            id = IdGenerator.newId(),
            classOfferingId = classOfferingId,
            userId = userId,
            staffRole = staffRole,
            assignedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            assignedAt = now
        )

        courseRepository.assignStaff(assignment)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.STAFF_ASSIGNED,
            targetEntityType = "ClassOffering",
            targetEntityId = classOfferingId,
            beforeSummary = null,
            afterSummary = "userId=$userId, role=$staffRole",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(assignment)
    }

    suspend fun getStaffForClass(classOfferingId: String): List<ClassStaffAssignment> =
        courseRepository.getStaffForClassOffering(classOfferingId)

    suspend fun overrideCapacity(
        classOfferingId: String,
        newCapacity: Int,
        reason: String
    ): AppResult<ClassOffering> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY)) {
            return AppResult.PermissionError("Requires enrollment.override_capacity")
        }

        if (newCapacity < 1) {
            return AppResult.ValidationError(fieldErrors = mapOf("newCapacity" to "Must be >= 1"))
        }
        if (reason.isBlank()) {
            return AppResult.ValidationError(fieldErrors = mapOf("reason" to "Reason is required for capacity override"))
        }

        val offering = courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        val now = TimeUtils.nowUtc()

        // Record override
        courseRepository.addCapacityOverride(CapacityOverride(
            id = IdGenerator.newId(),
            classOfferingId = classOfferingId,
            previousCapacity = offering.hardCapacity,
            newCapacity = newCapacity,
            reason = reason,
            approvedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            approvedAt = now
        ))

        // Update the offering
        val updated = offering.copy(hardCapacity = newCapacity, updatedAt = now, version = offering.version + 1)
        courseRepository.updateClassOffering(updated)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.CAPACITY_OVERRIDE,
            targetEntityType = "ClassOffering",
            targetEntityId = classOfferingId,
            beforeSummary = "capacity=${offering.hardCapacity}",
            afterSummary = "capacity=$newCapacity",
            reason = reason,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(courseRepository.getClassOfferingById(classOfferingId)!!)
    }

    suspend fun searchClassOfferings(query: String): List<ClassOffering> =
        courseRepository.searchClassOfferings(query)

    private suspend fun logClassTransition(
        classOfferingId: String, fromState: String, toState: String, now: Instant
    ) {
        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "ClassOffering",
            entityId = classOfferingId,
            fromState = fromState,
            toState = toState,
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = null,
            timestamp = now
        ))
        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.CLASS_STATE_CHANGED,
            targetEntityType = "ClassOffering",
            targetEntityId = classOfferingId,
            beforeSummary = "status=$fromState",
            afterSummary = "status=$toState",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))
    }
}
