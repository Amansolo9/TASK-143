package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.CourseDao
import com.learnmart.app.data.local.entity.CapacityOverrideEntity
import com.learnmart.app.data.local.entity.ClassOfferingEntity
import com.learnmart.app.data.local.entity.ClassSessionEntity
import com.learnmart.app.data.local.entity.ClassStaffAssignmentEntity
import com.learnmart.app.data.local.entity.CourseEntity
import com.learnmart.app.data.local.entity.CourseMaterialEntity
import com.learnmart.app.data.local.entity.CourseVersionEntity
import com.learnmart.app.data.local.entity.PublicationEventEntity
import com.learnmart.app.domain.model.CapacityOverride
import com.learnmart.app.domain.model.ClassOffering
import com.learnmart.app.domain.model.ClassOfferingStatus
import com.learnmart.app.domain.model.ClassSession
import com.learnmart.app.domain.model.ClassStaffAssignment
import com.learnmart.app.domain.model.Course
import com.learnmart.app.domain.model.CourseMaterial
import com.learnmart.app.domain.model.CourseStatus
import com.learnmart.app.domain.model.CourseVersion
import com.learnmart.app.domain.model.MaterialType
import com.learnmart.app.domain.model.PublicationEvent
import com.learnmart.app.domain.model.StaffRole
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepositoryImpl @Inject constructor(
    private val courseDao: CourseDao
) : CourseRepository {

    // ==================== Course ====================

    override suspend fun createCourse(course: Course): Course {
        val entity = course.toEntity()
        courseDao.insertCourse(entity)
        return entity.toDomain()
    }

    override suspend fun updateCourse(course: Course): Boolean {
        val existing = courseDao.getCourseById(course.id) ?: return false
        val updated = existing.copy(
            title = course.title,
            description = course.description,
            code = course.code,
            status = course.status.name,
            currentVersionId = course.currentVersionId,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        courseDao.updateCourse(updated)
        return true
    }

    override suspend fun getCourseById(id: String): Course? =
        courseDao.getCourseById(id)?.toDomain()

    override fun getAllActiveCourses(): Flow<List<Course>> =
        courseDao.getAllActiveCourses().map { list -> list.map { it.toDomain() } }

    override suspend fun getCoursesPaged(limit: Int, offset: Int): List<Course> =
        courseDao.getCoursesPaged(limit, offset).map { it.toDomain() }

    override suspend fun getCoursesByStatus(status: CourseStatus): List<Course> =
        courseDao.getCoursesByStatus(status.name).map { it.toDomain() }

    override suspend fun searchCourses(query: String): List<Course> =
        courseDao.searchCourses(query).map { it.toDomain() }

    override suspend fun updateCourseStatus(id: String, status: CourseStatus, currentVersion: Int): Boolean {
        val rows = courseDao.updateCourseStatus(
            id = id,
            status = status.name,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            currentVersion = currentVersion
        )
        return rows > 0
    }

    // ==================== CourseVersion ====================

    override suspend fun createCourseVersion(version: CourseVersion): CourseVersion {
        val entity = version.toEntity()
        courseDao.insertCourseVersion(entity)
        return entity.toDomain()
    }

    override suspend fun getVersionsForCourse(courseId: String): List<CourseVersion> =
        courseDao.getVersionsForCourse(courseId).map { it.toDomain() }

    // ==================== CourseMaterial ====================

    override suspend fun createCourseMaterial(material: CourseMaterial): CourseMaterial {
        val entity = material.toEntity()
        courseDao.insertCourseMaterial(entity)
        return entity.toDomain()
    }

    override suspend fun updateCourseMaterial(material: CourseMaterial): Boolean {
        val existing = courseDao.getCourseMaterialById(material.id) ?: return false
        val updated = existing.copy(
            title = material.title,
            description = material.description,
            materialType = material.materialType.name,
            isRequired = material.isRequired,
            price = material.price.toPlainString(),
            updatedAt = TimeUtils.nowUtc().toEpochMilli()
        )
        courseDao.updateCourseMaterial(updated)
        return true
    }

    override suspend fun getMaterialsForCourse(courseId: String): List<CourseMaterial> =
        courseDao.getMaterialsForCourse(courseId).map { it.toDomain() }

    // ==================== PublicationEvent ====================

    override suspend fun logPublicationEvent(event: PublicationEvent) {
        courseDao.insertPublicationEvent(event.toEntity())
    }

    override suspend fun getPublicationEventsForCourse(courseId: String): List<PublicationEvent> =
        courseDao.getPublicationEventsForCourse(courseId).map { it.toDomain() }

    // ==================== ClassOffering ====================

    override suspend fun createClassOffering(classOffering: ClassOffering): ClassOffering {
        val entity = classOffering.toEntity()
        courseDao.insertClassOffering(entity)
        return entity.toDomain()
    }

    override suspend fun updateClassOffering(classOffering: ClassOffering): Boolean {
        val existing = courseDao.getClassOfferingById(classOffering.id) ?: return false
        val updated = existing.copy(
            title = classOffering.title,
            description = classOffering.description,
            status = classOffering.status.name,
            hardCapacity = classOffering.hardCapacity,
            enrolledCount = classOffering.enrolledCount,
            waitlistEnabled = classOffering.waitlistEnabled,
            scheduleStart = classOffering.scheduleStart.toEpochMilli(),
            scheduleEnd = classOffering.scheduleEnd.toEpochMilli(),
            location = classOffering.location,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        courseDao.updateClassOffering(updated)
        return true
    }

    override suspend fun getClassOfferingById(id: String): ClassOffering? =
        courseDao.getClassOfferingById(id)?.toDomain()

    override suspend fun getClassOfferingsForCourse(courseId: String): List<ClassOffering> =
        courseDao.getClassOfferingsForCourse(courseId).map { it.toDomain() }

    override fun getAllActiveClassOfferings(): Flow<List<ClassOffering>> =
        courseDao.getAllActiveClassOfferings().map { list -> list.map { it.toDomain() } }

    override suspend fun updateClassOfferingStatus(id: String, status: ClassOfferingStatus, currentVersion: Int): Boolean {
        val rows = courseDao.updateClassOfferingStatus(
            id = id,
            status = status.name,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            currentVersion = currentVersion
        )
        return rows > 0
    }

    override suspend fun adjustEnrolledCount(id: String, delta: Int) {
        courseDao.adjustEnrolledCount(id, delta, TimeUtils.nowUtc().toEpochMilli())
    }

    override suspend fun searchClassOfferings(query: String): List<ClassOffering> =
        courseDao.searchClassOfferings(query).map { it.toDomain() }

    // ==================== ClassSession ====================

    override suspend fun createClassSession(session: ClassSession): ClassSession {
        val entity = session.toEntity()
        courseDao.insertClassSession(entity)
        return entity.toDomain()
    }

    override suspend fun updateClassSession(session: ClassSession): Boolean {
        val existing = courseDao.getClassSessionById(session.id) ?: return false
        val updated = existing.copy(
            sessionOrder = session.sessionOrder,
            title = session.title,
            sessionTime = session.sessionTime.toEpochMilli(),
            durationMinutes = session.durationMinutes,
            location = session.location,
            notes = session.notes
        )
        courseDao.updateClassSession(updated)
        return true
    }

    override suspend fun getSessionsForClassOffering(classOfferingId: String): List<ClassSession> =
        courseDao.getSessionsForClassOffering(classOfferingId).map { it.toDomain() }

    override suspend fun countSessionsForClassOffering(classOfferingId: String): Int =
        courseDao.countSessionsForClassOffering(classOfferingId)

    override suspend fun deleteClassSession(id: String) {
        courseDao.deleteClassSession(id)
    }

    // ==================== StaffAssignment ====================

    override suspend fun assignStaff(assignment: ClassStaffAssignment): ClassStaffAssignment {
        val entity = assignment.toEntity()
        courseDao.insertStaffAssignment(entity)
        return entity.toDomain()
    }

    override suspend fun getStaffForClassOffering(classOfferingId: String): List<ClassStaffAssignment> =
        courseDao.getStaffForClassOffering(classOfferingId).map { it.toDomain() }

    override suspend fun getClassAssignmentsForUser(userId: String): List<ClassStaffAssignment> =
        courseDao.getClassAssignmentsForUser(userId).map { it.toDomain() }

    override suspend fun countInstructorsForClass(classOfferingId: String): Int =
        courseDao.countInstructorsForClass(classOfferingId)

    override suspend fun removeStaffAssignment(id: String) {
        courseDao.removeStaffAssignment(id)
    }

    override suspend fun hasStaffAssignment(classOfferingId: String, userId: String, staffRole: StaffRole): Boolean =
        courseDao.hasStaffAssignment(classOfferingId, userId, staffRole.name) > 0

    // ==================== CapacityOverride ====================

    override suspend fun addCapacityOverride(override: CapacityOverride): CapacityOverride {
        val entity = override.toEntity()
        courseDao.insertCapacityOverride(entity)
        return entity.toDomain()
    }

    override suspend fun getCapacityOverridesForClass(classOfferingId: String): List<CapacityOverride> =
        courseDao.getCapacityOverridesForClass(classOfferingId).map { it.toDomain() }

    // ==================== Entity <-> Domain Mapping ====================

    private fun CourseEntity.toDomain() = Course(
        id = id,
        title = title,
        description = description,
        code = code,
        status = CourseStatus.valueOf(status),
        currentVersionId = currentVersionId,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun Course.toEntity() = CourseEntity(
        id = id,
        title = title,
        description = description,
        code = code,
        status = status.name,
        currentVersionId = currentVersionId,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    private fun CourseVersionEntity.toDomain() = CourseVersion(
        id = id,
        courseId = courseId,
        versionNumber = versionNumber,
        syllabus = syllabus,
        objectives = objectives,
        prerequisites = prerequisites,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun CourseVersion.toEntity() = CourseVersionEntity(
        id = id,
        courseId = courseId,
        versionNumber = versionNumber,
        syllabus = syllabus,
        objectives = objectives,
        prerequisites = prerequisites,
        createdAt = createdAt.toEpochMilli()
    )

    private fun CourseMaterialEntity.toDomain() = CourseMaterial(
        id = id,
        courseId = courseId,
        title = title,
        description = description,
        materialType = MaterialType.valueOf(materialType),
        isRequired = isRequired,
        price = BigDecimal(price),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun CourseMaterial.toEntity() = CourseMaterialEntity(
        id = id,
        courseId = courseId,
        title = title,
        description = description,
        materialType = materialType.name,
        isRequired = isRequired,
        price = price.toPlainString(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun PublicationEventEntity.toDomain() = PublicationEvent(
        id = id,
        courseId = courseId,
        fromStatus = CourseStatus.valueOf(fromStatus),
        toStatus = CourseStatus.valueOf(toStatus),
        publishedBy = publishedBy,
        publishedAt = Instant.ofEpochMilli(publishedAt),
        reason = reason
    )

    private fun PublicationEvent.toEntity() = PublicationEventEntity(
        id = id,
        courseId = courseId,
        fromStatus = fromStatus.name,
        toStatus = toStatus.name,
        publishedBy = publishedBy,
        publishedAt = publishedAt.toEpochMilli(),
        reason = reason
    )

    private fun ClassOfferingEntity.toDomain() = ClassOffering(
        id = id,
        courseId = courseId,
        title = title,
        description = description,
        status = ClassOfferingStatus.valueOf(status),
        hardCapacity = hardCapacity,
        enrolledCount = enrolledCount,
        waitlistEnabled = waitlistEnabled,
        scheduleStart = Instant.ofEpochMilli(scheduleStart),
        scheduleEnd = Instant.ofEpochMilli(scheduleEnd),
        location = location,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun ClassOffering.toEntity() = ClassOfferingEntity(
        id = id,
        courseId = courseId,
        title = title,
        description = description,
        status = status.name,
        hardCapacity = hardCapacity,
        enrolledCount = enrolledCount,
        waitlistEnabled = waitlistEnabled,
        scheduleStart = scheduleStart.toEpochMilli(),
        scheduleEnd = scheduleEnd.toEpochMilli(),
        location = location,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    private fun ClassSessionEntity.toDomain() = ClassSession(
        id = id,
        classOfferingId = classOfferingId,
        sessionOrder = sessionOrder,
        title = title,
        sessionTime = Instant.ofEpochMilli(sessionTime),
        durationMinutes = durationMinutes,
        location = location,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun ClassSession.toEntity() = ClassSessionEntity(
        id = id,
        classOfferingId = classOfferingId,
        sessionOrder = sessionOrder,
        title = title,
        sessionTime = sessionTime.toEpochMilli(),
        durationMinutes = durationMinutes,
        location = location,
        notes = notes,
        createdAt = createdAt.toEpochMilli()
    )

    private fun ClassStaffAssignmentEntity.toDomain() = ClassStaffAssignment(
        id = id,
        classOfferingId = classOfferingId,
        userId = userId,
        staffRole = StaffRole.valueOf(staffRole),
        assignedBy = assignedBy,
        assignedAt = Instant.ofEpochMilli(assignedAt)
    )

    private fun ClassStaffAssignment.toEntity() = ClassStaffAssignmentEntity(
        id = id,
        classOfferingId = classOfferingId,
        userId = userId,
        staffRole = staffRole.name,
        assignedBy = assignedBy,
        assignedAt = assignedAt.toEpochMilli()
    )

    private fun CapacityOverrideEntity.toDomain() = CapacityOverride(
        id = id,
        classOfferingId = classOfferingId,
        previousCapacity = previousCapacity,
        newCapacity = newCapacity,
        reason = reason,
        approvedBy = approvedBy,
        approvedAt = Instant.ofEpochMilli(approvedAt)
    )

    private fun CapacityOverride.toEntity() = CapacityOverrideEntity(
        id = id,
        classOfferingId = classOfferingId,
        previousCapacity = previousCapacity,
        newCapacity = newCapacity,
        reason = reason,
        approvedBy = approvedBy,
        approvedAt = approvedAt.toEpochMilli()
    )
}
