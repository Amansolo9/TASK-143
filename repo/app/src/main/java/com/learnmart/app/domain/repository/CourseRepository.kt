package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    // Course
    suspend fun createCourse(course: Course): Course
    suspend fun updateCourse(course: Course): Boolean
    suspend fun getCourseById(id: String): Course?
    fun getAllActiveCourses(): Flow<List<Course>>
    suspend fun getCoursesPaged(limit: Int, offset: Int): List<Course>
    suspend fun getCoursesByStatus(status: CourseStatus): List<Course>
    suspend fun searchCourses(query: String): List<Course>
    suspend fun updateCourseStatus(id: String, status: CourseStatus, currentVersion: Int): Boolean

    // CourseVersion
    suspend fun createCourseVersion(version: CourseVersion): CourseVersion
    suspend fun getVersionsForCourse(courseId: String): List<CourseVersion>

    // CourseMaterial
    suspend fun createCourseMaterial(material: CourseMaterial): CourseMaterial
    suspend fun updateCourseMaterial(material: CourseMaterial): Boolean
    suspend fun getMaterialsForCourse(courseId: String): List<CourseMaterial>

    // PublicationEvent
    suspend fun logPublicationEvent(event: PublicationEvent)
    suspend fun getPublicationEventsForCourse(courseId: String): List<PublicationEvent>

    // ClassOffering
    suspend fun createClassOffering(classOffering: ClassOffering): ClassOffering
    suspend fun updateClassOffering(classOffering: ClassOffering): Boolean
    suspend fun getClassOfferingById(id: String): ClassOffering?
    suspend fun getClassOfferingsForCourse(courseId: String): List<ClassOffering>
    fun getAllActiveClassOfferings(): Flow<List<ClassOffering>>
    suspend fun updateClassOfferingStatus(id: String, status: ClassOfferingStatus, currentVersion: Int): Boolean
    suspend fun adjustEnrolledCount(id: String, delta: Int)
    suspend fun searchClassOfferings(query: String): List<ClassOffering>

    // ClassSession
    suspend fun createClassSession(session: ClassSession): ClassSession
    suspend fun updateClassSession(session: ClassSession): Boolean
    suspend fun getSessionsForClassOffering(classOfferingId: String): List<ClassSession>
    suspend fun countSessionsForClassOffering(classOfferingId: String): Int
    suspend fun deleteClassSession(id: String)

    // StaffAssignment
    suspend fun assignStaff(assignment: ClassStaffAssignment): ClassStaffAssignment
    suspend fun getStaffForClassOffering(classOfferingId: String): List<ClassStaffAssignment>
    suspend fun getClassAssignmentsForUser(userId: String): List<ClassStaffAssignment>
    suspend fun countInstructorsForClass(classOfferingId: String): Int
    suspend fun removeStaffAssignment(id: String)
    suspend fun hasStaffAssignment(classOfferingId: String, userId: String, staffRole: StaffRole): Boolean

    // CapacityOverride
    suspend fun addCapacityOverride(override: CapacityOverride): CapacityOverride
    suspend fun getCapacityOverridesForClass(classOfferingId: String): List<CapacityOverride>
}
