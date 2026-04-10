package com.learnmart.app.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.EnrollmentApprovalTask
import com.learnmart.app.domain.model.EnrollmentRecord
import com.learnmart.app.domain.model.EnrollmentRequest
import com.learnmart.app.domain.usecase.enrollment.ManageEnrollmentUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnrollmentListUiState(
    val pendingRequests: List<EnrollmentRequest> = emptyList(),
    val myRequests: List<EnrollmentRequest> = emptyList(),
    val myEnrollments: List<EnrollmentRecord> = emptyList(),
    val pendingTasks: List<EnrollmentApprovalTask> = emptyList(),
    val hasReviewPermission: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class EnrollmentListViewModel @Inject constructor(
    private val manageEnrollmentUseCase: ManageEnrollmentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentListUiState())
    val uiState: StateFlow<EnrollmentListUiState> = _uiState.asStateFlow()

    init {
        loadPendingRequestsFlow()
        loadMyRequests()
        loadMyEnrollments()
        loadPendingApprovalTasks()
    }

    private fun loadPendingRequestsFlow() {
        viewModelScope.launch {
            when (val result = manageEnrollmentUseCase.getPendingRequests()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(hasReviewPermission = true) }
                    result.data.collect { requests ->
                        _uiState.update { it.copy(pendingRequests = requests) }
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update { it.copy(hasReviewPermission = false, pendingRequests = emptyList()) }
                }
                else -> { /* no-op */ }
            }
        }
    }

    private fun loadMyRequests() {
        viewModelScope.launch {
            val requests = manageEnrollmentUseCase.getMyRequests()
            _uiState.update { it.copy(myRequests = requests, isLoading = false) }
        }
    }

    private fun loadMyEnrollments() {
        viewModelScope.launch {
            val enrollments = manageEnrollmentUseCase.getMyEnrollments()
            _uiState.update { it.copy(myEnrollments = enrollments) }
        }
    }

    private fun loadPendingApprovalTasks() {
        viewModelScope.launch {
            val tasks = manageEnrollmentUseCase.getPendingTasksForCurrentUser()
            _uiState.update { it.copy(pendingTasks = tasks) }
        }
    }

    fun cancelRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageEnrollmentUseCase.cancelRequest(requestId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            actionMessage = "Request cancelled successfully"
                        )
                    }
                    loadMyRequests()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot cancel request"
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
                        it.copy(isLoading = false, errorMessage = "Request not found")
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

    fun withdrawEnrollment(recordId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageEnrollmentUseCase.withdrawEnrollment(recordId, reason)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            actionMessage = "Enrollment withdrawn successfully"
                        )
                    }
                    loadMyEnrollments()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot withdraw enrollment"
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
                        it.copy(isLoading = false, errorMessage = "Enrollment not found")
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
