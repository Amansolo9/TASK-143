package com.learnmart.app.ui.screens.admin.policies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.Policy
import com.learnmart.app.domain.usecase.policy.ManagePolicyUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PolicyEditUiState(
    val policy: Policy? = null,
    val newValue: String = "",
    val reason: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class PolicyEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val managePolicyUseCase: ManagePolicyUseCase
) : ViewModel() {

    private val policyId: String = savedStateHandle["policyId"] ?: ""

    private val _uiState = MutableStateFlow(PolicyEditUiState())
    val uiState: StateFlow<PolicyEditUiState> = _uiState.asStateFlow()

    init {
        loadPolicy()
    }

    private fun loadPolicy() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = managePolicyUseCase.getPolicyById(policyId)) {
                is AppResult.Success -> {
                    _uiState.value = PolicyEditUiState(
                        policy = result.data,
                        newValue = result.data.value,
                        isLoading = false
                    )
                }
                is AppResult.PermissionError -> {
                    _uiState.value = PolicyEditUiState(
                        isLoading = false,
                        errorMessage = "Permission denied: requires policy.manage"
                    )
                }
                is AppResult.NotFoundError -> {
                    _uiState.value = PolicyEditUiState(
                        isLoading = false,
                        errorMessage = "Policy not found"
                    )
                }
                else -> {
                    _uiState.value = PolicyEditUiState(
                        isLoading = false,
                        errorMessage = "Failed to load policy"
                    )
                }
            }
        }
    }

    fun onValueChanged(value: String) {
        _uiState.value = _uiState.value.copy(newValue = value, errorMessage = null)
    }

    fun onReasonChanged(reason: String) {
        _uiState.value = _uiState.value.copy(reason = reason)
    }

    fun save() {
        val state = _uiState.value
        val policy = state.policy ?: return

        if (state.newValue.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Value cannot be empty")
            return
        }

        if (state.newValue == policy.value) {
            _uiState.value = state.copy(errorMessage = "No changes made")
            return
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = managePolicyUseCase.updatePolicy(
                policyId = policy.id,
                newValue = state.newValue,
                reason = state.reason.ifBlank { null }
            )) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                }
                is AppResult.PermissionError -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "Permission denied: requires policy.manage"
                    )
                }
                is AppResult.ValidationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.globalErrors.firstOrNull()
                            ?: result.fieldErrors.values.firstOrNull()
                            ?: "Validation error"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "Failed to update policy"
                    )
                }
            }
        }
    }
}
