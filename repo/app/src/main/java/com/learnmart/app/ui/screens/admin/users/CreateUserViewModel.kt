package com.learnmart.app.ui.screens.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.CredentialType
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.usecase.user.CreateUserRequest
import com.learnmart.app.domain.usecase.user.CreateUserUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateUserUiState(
    val username: String = "",
    val displayName: String = "",
    val credential: String = "",
    val confirmCredential: String = "",
    val selectedCredentialType: CredentialType = CredentialType.PASSWORD,
    val selectedRole: RoleType = RoleType.LEARNER,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isCreated: Boolean = false
)

@HiltViewModel
class CreateUserViewModel @Inject constructor(
    private val createUserUseCase: CreateUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUserUiState())
    val uiState: StateFlow<CreateUserUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                fieldErrors = it.fieldErrors - "username"
            )
        }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update {
            it.copy(
                displayName = value,
                fieldErrors = it.fieldErrors - "displayName"
            )
        }
    }

    fun onCredentialChanged(value: String) {
        _uiState.update {
            it.copy(
                credential = value,
                fieldErrors = it.fieldErrors - "credential"
            )
        }
    }

    fun onConfirmCredentialChanged(value: String) {
        _uiState.update {
            it.copy(
                confirmCredential = value,
                fieldErrors = it.fieldErrors - "confirmCredential"
            )
        }
    }

    fun onCredentialTypeChanged(type: CredentialType) {
        _uiState.update {
            it.copy(
                selectedCredentialType = type,
                fieldErrors = it.fieldErrors - "credential" - "confirmCredential"
            )
        }
    }

    fun onRoleChanged(role: RoleType) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun createUser() {
        val state = _uiState.value

        // Local validation
        val localErrors = mutableMapOf<String, String>()
        if (state.username.isBlank()) {
            localErrors["username"] = "Username is required"
        }
        if (state.displayName.isBlank()) {
            localErrors["displayName"] = "Display name is required"
        }
        if (state.credential.isBlank()) {
            localErrors["credential"] = "Credential is required"
        }
        if (state.confirmCredential.isBlank()) {
            localErrors["confirmCredential"] = "Please confirm the credential"
        } else if (state.credential != state.confirmCredential) {
            localErrors["confirmCredential"] = "Credentials do not match"
        }

        if (localErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = localErrors) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, fieldErrors = emptyMap())
            }

            val request = CreateUserRequest(
                username = state.username.trim(),
                displayName = state.displayName.trim(),
                credential = state.credential,
                credentialType = state.selectedCredentialType,
                roleType = state.selectedRole
            )

            when (val result = createUserUseCase(request)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, isCreated = true)
                    }
                }

                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fieldErrors = result.fieldErrors,
                            errorMessage = result.globalErrors.firstOrNull()
                        )
                    }
                }

                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }

                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "You do not have permission to create users"
                        )
                    }
                }

                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Required resource not found"
                        )
                    }
                }

                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}
