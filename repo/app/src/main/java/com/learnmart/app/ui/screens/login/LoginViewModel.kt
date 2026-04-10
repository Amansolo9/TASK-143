package com.learnmart.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.LoginRequest
import com.learnmart.app.domain.usecase.auth.LoginUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val credential: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onCredentialChanged(credential: String) {
        _uiState.update { it.copy(credential = credential, errorMessage = null) }
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val request = LoginRequest(
                username = currentState.username.trim(),
                credential = currentState.credential
            )

            when (val result = loginUseCase(request)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, isLoggedIn = true)
                    }
                }

                is AppResult.ValidationError -> {
                    val message = result.globalErrors.firstOrNull()
                        ?: result.fieldErrors.values.firstOrNull()
                        ?: "Validation failed"
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = message)
                    }
                }

                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }

                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Access denied")
                    }
                }

                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "User not found")
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
}
