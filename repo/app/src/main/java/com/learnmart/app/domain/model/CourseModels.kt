package com.learnmart.app.domain.model

import java.time.Instant

// --- Course Lifecycle State Machine ---
enum class CourseStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED,
    ARCHIVED;

    fun allowedTransitions(): Set<CourseStatus> = when (this) {
        DRAFT -> setOf(PUBLISHED, ARCHIVED)
        PUBLISHED -> setOf(UNPUBLISHED, ARCHIVED)
        UNPUBLISHED -> setOf(PUBLISHED, ARCHIVED)
        ARCHIVED -> emptySet()
    }

    fun canTransitionTo(target: CourseStatus): Boolean = target in allowedTransitions()
}

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val status: CourseStatus,
    val currentVersionId: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class CourseVersion(
    val id: String,
    val courseId: String,
    val versionNumber: Int,
    val syllabus: String,
    val objectives: String,
    val prerequisites: String,
    val createdAt: Instant
)

data class CourseMaterial(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val materialType: MaterialType,
    val isRequired: Boolean,
    val price: java.math.BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class MaterialType {
    TEXTBOOK,
    WORKBOOK,
    SUPPLY_KIT,
    PRINTED_MANUAL,
    OTHER
}

data class PublicationEvent(
    val id: String,
    val courseId: String,
    val fromStatus: CourseStatus,
    val toStatus: CourseStatus,
    val publishedBy: String,
    val publishedAt: Instant,
    val reason: String?
)

// --- Class Offering Lifecycle State Machine ---
enum class ClassOfferingStatus {
    PLANNED,
    OPEN,
    CLOSED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    fun allowedTransitions(): Set<ClassOfferingStatus> = when (this) {
        PLANNED -> setOf(OPEN, CANCELLED)
        OPEN -> setOf(CLOSED, IN_PROGRESS, CANCELLED)
        CLOSED -> setOf(IN_PROGRESS, COMPLETED, CANCELLED)
        IN_PROGRESS -> setOf(COMPLETED, CANCELLED)
        COMPLETED -> setOf(ARCHIVED)
        CANCELLED -> setOf(ARCHIVED)
        ARCHIVED -> emptySet()
    }

    fun canTransitionTo(target: ClassOfferingStatus): Boolean = target in allowedTransitions()
}

data class ClassOffering(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val status: ClassOfferingStatus,
    val hardCapacity: Int,
    val enrolledCount: Int,
    val waitlistEnabled: Boolean,
    val scheduleStart: Instant,
    val scheduleEnd: Instant,
    val location: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class ClassSession(
    val id: String,
    val classOfferingId: String,
    val sessionOrder: Int,
    val title: String,
    val sessionTime: Instant,
    val durationMinutes: Int,
    val location: String?,
    val notes: String?,
    val createdAt: Instant
)

enum class StaffRole {
    INSTRUCTOR,
    TEACHING_ASSISTANT
}

data class ClassStaffAssignment(
    val id: String,
    val classOfferingId: String,
    val userId: String,
    val staffRole: StaffRole,
    val assignedBy: String,
    val assignedAt: Instant
)

data class CapacityOverride(
    val id: String,
    val classOfferingId: String,
    val previousCapacity: Int,
    val newCapacity: Int,
    val reason: String,
    val approvedBy: String,
    val approvedAt: Instant
)
