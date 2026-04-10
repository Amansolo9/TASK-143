package com.learnmart.app.ui.screens.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.ClassOffering
import com.learnmart.app.domain.model.ClassOfferingStatus
import com.learnmart.app.domain.model.ClassSession
import com.learnmart.app.domain.model.ClassStaffAssignment
import com.learnmart.app.domain.model.Course
import com.learnmart.app.domain.model.CourseStatus
import com.learnmart.app.domain.usecase.course.ManageClassUseCase
import com.learnmart.app.domain.usecase.course.ManageCourseUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseDetailUiState(
    val course: Course? = null,
    val classOfferings: List<ClassOffering> = emptyList(),
    val sessions: Map<String, List<ClassSession>> = emptyMap(),
    val staff: Map<String, List<ClassStaffAssignment>> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageCourseUseCase: ManageCourseUseCase,
    private val manageClassUseCase: ManageClassUseCase
) : ViewModel() {

    private val courseId: String = checkNotNull(savedStateHandle["courseId"])

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    init {
        loadCourseDetail()
    }

    private fun loadCourseDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val courseResult = manageCourseUseCase.getCourseById(courseId)) {
                is AppResult.Success -> {
                    val course = courseResult.data
                    val offerings = manageClassUseCase.searchClassOfferings("")
                        .filter { it.courseId == courseId }

                    val sessionsMap = mutableMapOf<String, List<ClassSession>>()
                    val staffMap = mutableMapOf<String, List<ClassStaffAssignment>>()

                    for (offering in offerings) {
                        sessionsMap[offering.id] = manageClassUseCase.getSessionsForClass(offering.id)
                        staffMap[offering.id] = manageClassUseCase.getStaffForClass(offering.id)
                    }

                    _uiState.update {
                        it.copy(
                            course = course,
                            classOfferings = offerings,
                            sessions = sessionsMap,
                            staff = staffMap,
                            isLoading = false
                        )
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Course not found")
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = courseResult.globalErrors.firstOrNull() ?: "Validation error"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = courseResult.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = courseResult.message)
                    }
                }
            }
        }
    }

    fun publishCourse() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageCourseUseCase.publishCourse(courseId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            course = result.data,
                            isLoading = false,
                            actionMessage = "Course published successfully"
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot publish course"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Course not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun unpublishCourse(reason: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageCourseUseCase.unpublishCourse(courseId, reason)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            course = result.data,
                            isLoading = false,
                            actionMessage = "Course unpublished successfully"
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot unpublish course"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Course not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun archiveCourse(reason: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageCourseUseCase.archiveCourse(courseId, reason)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            course = result.data,
                            isLoading = false,
                            actionMessage = "Course archived successfully"
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot archive course"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Course not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun openClassForEnrollment(classOfferingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageClassUseCase.openClassForEnrollment(classOfferingId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            actionMessage = "Class opened for enrollment"
                        )
                    }
                    loadCourseDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot open class for enrollment"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Class not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun transitionClassStatus(classOfferingId: String, targetStatus: ClassOfferingStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageClassUseCase.transitionClassStatus(classOfferingId, targetStatus, null)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            actionMessage = "Class status changed to ${targetStatus.name}"
                        )
                    }
                    loadCourseDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot change class status"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Class not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
