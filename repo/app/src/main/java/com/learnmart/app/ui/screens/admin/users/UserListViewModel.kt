package com.learnmart.app.ui.screens.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.User
import com.learnmart.app.domain.usecase.user.ManageUserUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserListUiState(
    val users: List<User> = emptyList(),
    val filteredUsers: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val manageUserUseCase: ManageUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadUsers()
    }

    private fun loadUsers() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageUserUseCase.getAllActiveUsers()) {
                is AppResult.Success -> {
                    result.data
                        .onStart {
                            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                        }
                        .catch { e ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = e.message ?: "Failed to load users"
                                )
                            }
                        }
                        .collect { users ->
                            _uiState.update { state ->
                                state.copy(
                                    users = users,
                                    filteredUsers = filterUsers(users, state.searchQuery),
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied: requires user.manage")
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Failed to load users")
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredUsers = filterUsers(state.users, query)
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun filterUsers(users: List<User>, query: String): List<User> {
        if (query.isBlank()) return users
        val lowerQuery = query.lowercase()
        return users.filter { user ->
            user.displayName.lowercase().contains(lowerQuery) ||
                user.username.lowercase().contains(lowerQuery)
        }
    }
}
