package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.ClassOfferingEntity
import com.learnmart.app.data.local.entity.ClassSessionEntity
import com.learnmart.app.data.local.entity.ClassStaffAssignmentEntity
import com.learnmart.app.data.local.entity.CapacityOverrideEntity
import com.learnmart.app.data.local.entity.CourseEntity
import com.learnmart.app.data.local.entity.CourseMaterialEntity
import com.learnmart.app.data.local.entity.CourseVersionEntity
import com.learnmart.app.data.local.entity.PublicationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    // --- Course ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourse(course: CourseEntity)

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE status != 'ARCHIVED' ORDER BY title ASC")
    fun getAllActiveCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getCoursesPaged(limit: Int, offset: Int): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE status = :status ORDER BY title ASC")
    suspend fun getCoursesByStatus(status: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE title LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%'")
    suspend fun searchCourses(query: String): List<CourseEntity>

    @Query("""
        UPDATE courses SET status = :status, updated_at = :updatedAt, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updateCourseStatus(id: String, status: String, updatedAt: Long, currentVersion: Int): Int

    // --- CourseVersion ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourseVersion(version: CourseVersionEntity)

    @Query("SELECT * FROM course_versions WHERE course_id = :courseId ORDER BY version_number DESC")
    suspend fun getVersionsForCourse(courseId: String): List<CourseVersionEntity>

    @Query("SELECT * FROM course_versions WHERE id = :id")
    suspend fun getCourseVersionById(id: String): CourseVersionEntity?

    // --- CourseMaterial ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourseMaterial(material: CourseMaterialEntity)

    @Update
    suspend fun updateCourseMaterial(material: CourseMaterialEntity)

    @Query("SELECT * FROM course_materials WHERE course_id = :courseId ORDER BY title ASC")
    suspend fun getMaterialsForCourse(courseId: String): List<CourseMaterialEntity>

    @Query("SELECT * FROM course_materials WHERE id = :id")
    suspend fun getCourseMaterialById(id: String): CourseMaterialEntity?

    // --- PublicationEvent ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPublicationEvent(event: PublicationEventEntity)

    @Query("SELECT * FROM publication_events WHERE course_id = :courseId ORDER BY published_at DESC")
    suspend fun getPublicationEventsForCourse(courseId: String): List<PublicationEventEntity>

    // --- ClassOffering ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClassOffering(classOffering: ClassOfferingEntity)

    @Update
    suspend fun updateClassOffering(classOffering: ClassOfferingEntity)

    @Query("SELECT * FROM class_offerings WHERE id = :id")
    suspend fun getClassOfferingById(id: String): ClassOfferingEntity?

    @Query("SELECT * FROM class_offerings WHERE course_id = :courseId ORDER BY schedule_start DESC")
    suspend fun getClassOfferingsForCourse(courseId: String): List<ClassOfferingEntity>

    @Query("SELECT * FROM class_offerings WHERE status NOT IN ('ARCHIVED', 'CANCELLED') ORDER BY schedule_start ASC")
    fun getAllActiveClassOfferings(): Flow<List<ClassOfferingEntity>>

    @Query("SELECT * FROM class_offerings WHERE status = :status ORDER BY schedule_start ASC")
    suspend fun getClassOfferingsByStatus(status: String): List<ClassOfferingEntity>

    @Query("""
        UPDATE class_offerings SET status = :status, updated_at = :updatedAt, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updateClassOfferingStatus(id: String, status: String, updatedAt: Long, currentVersion: Int): Int

    @Query("""
        UPDATE class_offerings SET enrolled_count = enrolled_count + :delta, updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun adjustEnrolledCount(id: String, delta: Int, updatedAt: Long)

    @Query("SELECT * FROM class_offerings WHERE title LIKE '%' || :query || '%' OR id = :query")
    suspend fun searchClassOfferings(query: String): List<ClassOfferingEntity>

    // --- ClassSession ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClassSession(session: ClassSessionEntity)

    @Update
    suspend fun updateClassSession(session: ClassSessionEntity)

    @Query("SELECT * FROM class_sessions WHERE class_offering_id = :classOfferingId ORDER BY session_order ASC")
    suspend fun getSessionsForClassOffering(classOfferingId: String): List<ClassSessionEntity>

    @Query("SELECT * FROM class_sessions WHERE id = :id")
    suspend fun getClassSessionById(id: String): ClassSessionEntity?

    @Query("SELECT COUNT(*) FROM class_sessions WHERE class_offering_id = :classOfferingId")
    suspend fun countSessionsForClassOffering(classOfferingId: String): Int

    @Query("DELETE FROM class_sessions WHERE id = :id")
    suspend fun deleteClassSession(id: String)

    // --- ClassStaffAssignment ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStaffAssignment(assignment: ClassStaffAssignmentEntity)

    @Query("SELECT * FROM class_staff_assignments WHERE class_offering_id = :classOfferingId")
    suspend fun getStaffForClassOffering(classOfferingId: String): List<ClassStaffAssignmentEntity>

    @Query("SELECT * FROM class_staff_assignments WHERE user_id = :userId")
    suspend fun getClassAssignmentsForUser(userId: String): List<ClassStaffAssignmentEntity>

    @Query("SELECT COUNT(*) FROM class_staff_assignments WHERE class_offering_id = :classOfferingId AND staff_role = 'INSTRUCTOR'")
    suspend fun countInstructorsForClass(classOfferingId: String): Int

    @Query("DELETE FROM class_staff_assignments WHERE id = :id")
    suspend fun removeStaffAssignment(id: String)

    @Query("SELECT COUNT(*) FROM class_staff_assignments WHERE class_offering_id = :classOfferingId AND user_id = :userId AND staff_role = :staffRole")
    suspend fun hasStaffAssignment(classOfferingId: String, userId: String, staffRole: String): Int

    // --- CapacityOverride ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCapacityOverride(override: CapacityOverrideEntity)

    @Query("SELECT * FROM capacity_overrides WHERE class_offering_id = :classOfferingId ORDER BY approved_at DESC")
    suspend fun getCapacityOverridesForClass(classOfferingId: String): List<CapacityOverrideEntity>
}
