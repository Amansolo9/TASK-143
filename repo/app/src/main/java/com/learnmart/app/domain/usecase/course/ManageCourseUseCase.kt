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

class ManageCourseUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    fun getAllActiveCourses(): Flow<List<Course>> = courseRepository.getAllActiveCourses()

    suspend fun getCourseById(id: String): AppResult<Course> {
        val course = courseRepository.getCourseById(id)
            ?: return AppResult.NotFoundError("COURSE_NOT_FOUND")
        return AppResult.Success(course)
    }

    suspend fun createCourse(
        title: String,
        description: String,
        code: String
    ): AppResult<Course> {
        if (!checkPermission.hasPermission(Permission.CATALOG_MANAGE)) {
            return AppResult.PermissionError("Requires catalog.manage")
        }

        val errors = mutableMapOf<String, String>()
        if (title.isBlank()) errors["title"] = "Title is required"
        if (code.isBlank()) errors["code"] = "Code is required"
        if (errors.isNotEmpty()) return AppResult.ValidationError(fieldErrors = errors)

        val now = TimeUtils.nowUtc()
        val course = Course(
            id = IdGenerator.newId(),
            title = title.trim(),
            description = description.trim(),
            code = code.trim().uppercase(),
            status = CourseStatus.DRAFT,
            currentVersionId = null,
            createdBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        courseRepository.createCourse(course)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.COURSE_CREATED,
            targetEntityType = "Course",
            targetEntityId = course.id,
            beforeSummary = null,
            afterSummary = "title=$title, code=$code, status=DRAFT",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(course)
    }

    suspend fun publishCourse(courseId: String): AppResult<Course> {
        if (!checkPermission.hasPermission(Permission.CATALOG_PUBLISH)) {
            return AppResult.PermissionError("Requires catalog.publish")
        }

        val course = courseRepository.getCourseById(courseId)
            ?: return AppResult.NotFoundError("COURSE_NOT_FOUND")

        if (!course.status.canTransitionTo(CourseStatus.PUBLISHED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot publish course from ${course.status} state")
            )
        }

        val success = courseRepository.updateCourseStatus(courseId, CourseStatus.PUBLISHED, course.version)
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Course was modified concurrently")

        val now = TimeUtils.nowUtc()
        courseRepository.logPublicationEvent(PublicationEvent(
            id = IdGenerator.newId(),
            courseId = courseId,
            fromStatus = course.status,
            toStatus = CourseStatus.PUBLISHED,
            publishedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            publishedAt = now,
            reason = null
        ))

        logStateTransition(courseId, "Course", course.status.name, CourseStatus.PUBLISHED.name,
            AuditActionType.COURSE_PUBLISHED, now)

        return AppResult.Success(courseRepository.getCourseById(courseId)!!)
    }

    suspend fun unpublishCourse(courseId: String, reason: String?): AppResult<Course> {
        if (!checkPermission.hasPermission(Permission.CATALOG_PUBLISH)) {
            return AppResult.PermissionError("Requires catalog.publish")
        }

        val course = courseRepository.getCourseById(courseId)
            ?: return AppResult.NotFoundError("COURSE_NOT_FOUND")

        if (!course.status.canTransitionTo(CourseStatus.UNPUBLISHED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot unpublish course from ${course.status} state")
            )
        }

        val success = courseRepository.updateCourseStatus(courseId, CourseStatus.UNPUBLISHED, course.version)
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Course was modified concurrently")

        val now = TimeUtils.nowUtc()
        courseRepository.logPublicationEvent(PublicationEvent(
            id = IdGenerator.newId(),
            courseId = courseId,
            fromStatus = course.status,
            toStatus = CourseStatus.UNPUBLISHED,
            publishedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            publishedAt = now,
            reason = reason
        ))

        logStateTransition(courseId, "Course", course.status.name, CourseStatus.UNPUBLISHED.name,
            AuditActionType.COURSE_UNPUBLISHED, now)

        return AppResult.Success(courseRepository.getCourseById(courseId)!!)
    }

    suspend fun archiveCourse(courseId: String, reason: String?): AppResult<Course> {
        if (!checkPermission.hasPermission(Permission.CATALOG_MANAGE)) {
            return AppResult.PermissionError("Requires catalog.manage")
        }

        val course = courseRepository.getCourseById(courseId)
            ?: return AppResult.NotFoundError("COURSE_NOT_FOUND")

        if (!course.status.canTransitionTo(CourseStatus.ARCHIVED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot archive course from ${course.status} state")
            )
        }

        val success = courseRepository.updateCourseStatus(courseId, CourseStatus.ARCHIVED, course.version)
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Course was modified concurrently")

        logStateTransition(courseId, "Course", course.status.name, CourseStatus.ARCHIVED.name,
            AuditActionType.COURSE_ARCHIVED, TimeUtils.nowUtc())

        return AppResult.Success(courseRepository.getCourseById(courseId)!!)
    }

    suspend fun searchCourses(query: String): List<Course> = courseRepository.searchCourses(query)

    private suspend fun logStateTransition(
        entityId: String, entityType: String,
        fromState: String, toState: String,
        actionType: AuditActionType, now: Instant
    ) {
        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = entityType,
            entityId = entityId,
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
            actionType = actionType,
            targetEntityType = entityType,
            targetEntityId = entityId,
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
