package com.learnmart.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.domain.usecase.auth.LogoutUseCase
import com.learnmart.app.domain.usecase.audit.ViewAuditLogUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val displayName: String = "",
    val roles: List<RoleType> = emptyList(),
    val recentAuditEvents: List<AuditEvent> = emptyList(),
    val hasAuditPermission: Boolean = false,
    val isLoading: Boolean = true,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val checkPermissionUseCase: CheckPermissionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val viewAuditLogUseCase: ViewAuditLogUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val userId = sessionManager.getCurrentUserId()
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, isLoggedOut = true) }
                return@launch
            }

            val user = userRepository.getUserById(userId)
            val displayName = user?.displayName ?: ""

            val roles = checkPermissionUseCase.getCurrentUserRoles()
            val hasAuditPerm = checkPermissionUseCase.hasPermission(Permission.AUDIT_VIEW)

            _uiState.update {
                it.copy(
                    displayName = displayName,
                    roles = roles,
                    hasAuditPermission = hasAuditPerm
                )
            }

            // Only collect audit events if the user has permission
            if (hasAuditPerm) {
                collectRecentAuditEvents()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun collectRecentAuditEvents() {
        viewModelScope.launch {
            when (val result = viewAuditLogUseCase.getRecentEvents(limit = 10)) {
                is AppResult.Success -> {
                    result.data.collect { events ->
                        _uiState.update {
                            it.copy(
                                recentAuditEvents = events,
                                isLoading = false
                            )
                        }
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(
                            recentAuditEvents = emptyList(),
                            hasAuditPermission = false,
                            isLoading = false
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    suspend fun hasPermission(permission: Permission): Boolean {
        return checkPermissionUseCase.hasPermission(permission)
    }
}
