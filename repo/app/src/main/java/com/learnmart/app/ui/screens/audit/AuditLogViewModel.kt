package com.learnmart.app.ui.screens.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.usecase.audit.ViewAuditLogUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(
    val events: List<AuditEvent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val viewAuditLogUseCase: ViewAuditLogUseCase
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        loadPage(0)
    }

    fun loadPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = viewAuditLogUseCase.getEventsPaged(PAGE_SIZE, page * PAGE_SIZE)) {
                is AppResult.Success -> {
                    val newEvents = if (page == 0) {
                        result.data
                    } else {
                        _uiState.value.events + result.data
                    }
                    _uiState.value = _uiState.value.copy(
                        events = newEvents,
                        isLoading = false,
                        currentPage = page,
                        hasMore = result.data.size == PAGE_SIZE
                    )
                }
                is AppResult.PermissionError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Permission denied: requires audit.view"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load audit events"
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        if (!_uiState.value.isLoading && _uiState.value.hasMore) {
            loadPage(_uiState.value.currentPage + 1)
        }
    }

    fun refresh() {
        loadPage(0)
    }
}
