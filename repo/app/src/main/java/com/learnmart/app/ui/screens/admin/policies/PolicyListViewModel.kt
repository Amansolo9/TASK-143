package com.learnmart.app.ui.screens.admin.policies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.Policy
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.usecase.policy.ManagePolicyUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PolicyListUiState(
    val policies: List<Policy> = emptyList(),
    val filteredPolicies: List<Policy> = emptyList(),
    val selectedType: PolicyType? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PolicyListViewModel @Inject constructor(
    private val managePolicyUseCase: ManagePolicyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PolicyListUiState())
    val uiState: StateFlow<PolicyListUiState> = _uiState.asStateFlow()

    init {
        loadPolicies()
    }

    private fun loadPolicies() {
        viewModelScope.launch {
            when (val result = managePolicyUseCase.getAllActivePolicies()) {
                is AppResult.Success -> {
                    result.data.collect { policies ->
                        _uiState.value = _uiState.value.copy(
                            policies = policies,
                            filteredPolicies = applyFilter(policies, _uiState.value.selectedType),
                            isLoading = false
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Permission denied: requires policy.manage"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load policies"
                    )
                }
            }
        }
    }

    fun filterByType(type: PolicyType?) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedType = type,
            filteredPolicies = applyFilter(current.policies, type)
        )
    }

    private fun applyFilter(policies: List<Policy>, type: PolicyType?): List<Policy> {
        return if (type == null) policies
        else policies.filter { it.type == type }
    }
}
