package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["code"], unique = true)
    ]
)
data class CourseEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "current_version_id")
    val currentVersionId: String? = null,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

@Entity(
    tableName = "course_versions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["course_id", "version_number"], unique = true),
        Index(value = ["course_id"])
    ]
)
data class CourseVersionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "version_number")
    val versionNumber: Int,
    @ColumnInfo(name = "syllabus")
    val syllabus: String,
    @ColumnInfo(name = "objectives")
    val objectives: String,
    @ColumnInfo(name = "prerequisites")
    val prerequisites: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "course_materials",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["course_id"])
    ]
)
data class CourseMaterialEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "material_type")
    val materialType: String,
    @ColumnInfo(name = "is_required")
    val isRequired: Boolean,
    @ColumnInfo(name = "price")
    val price: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "publication_events",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["course_id"])
    ]
)
data class PublicationEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "from_status")
    val fromStatus: String,
    @ColumnInfo(name = "to_status")
    val toStatus: String,
    @ColumnInfo(name = "published_by")
    val publishedBy: String,
    @ColumnInfo(name = "published_at")
    val publishedAt: Long,
    @ColumnInfo(name = "reason")
    val reason: String? = null
)

@Entity(
    tableName = "class_offerings",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["course_id"]),
        Index(value = ["status"])
    ]
)
data class ClassOfferingEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "hard_capacity")
    val hardCapacity: Int,
    @ColumnInfo(name = "enrolled_count")
    val enrolledCount: Int = 0,
    @ColumnInfo(name = "waitlist_enabled")
    val waitlistEnabled: Boolean,
    @ColumnInfo(name = "schedule_start")
    val scheduleStart: Long,
    @ColumnInfo(name = "schedule_end")
    val scheduleEnd: Long,
    @ColumnInfo(name = "location")
    val location: String,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

@Entity(
    tableName = "class_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ClassOfferingEntity::class,
            parentColumns = ["id"],
            childColumns = ["class_offering_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["class_offering_id", "session_order"], unique = true),
        Index(value = ["class_offering_id", "session_time"])
    ]
)
data class ClassSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "session_order")
    val sessionOrder: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "session_time")
    val sessionTime: Long,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    @ColumnInfo(name = "location")
    val location: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "class_staff_assignments",
    foreignKeys = [
        ForeignKey(
            entity = ClassOfferingEntity::class,
            parentColumns = ["id"],
            childColumns = ["class_offering_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["class_offering_id", "user_id", "staff_role"], unique = true),
        Index(value = ["user_id"])
    ]
)
data class ClassStaffAssignmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "staff_role")
    val staffRole: String,
    @ColumnInfo(name = "assigned_by")
    val assignedBy: String,
    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long
)

@Entity(
    tableName = "capacity_overrides",
    foreignKeys = [
        ForeignKey(
            entity = ClassOfferingEntity::class,
            parentColumns = ["id"],
            childColumns = ["class_offering_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["class_offering_id"])
    ]
)
data class CapacityOverrideEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "previous_capacity")
    val previousCapacity: Int,
    @ColumnInfo(name = "new_capacity")
    val newCapacity: Int,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "approved_by")
    val approvedBy: String,
    @ColumnInfo(name = "approved_at")
    val approvedAt: Long
)
