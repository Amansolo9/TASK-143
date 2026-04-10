package com.learnmart.app.ui.screens.commerce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.usecase.commerce.IssueRefundRequest
import com.learnmart.app.domain.usecase.commerce.IssueRefundUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class RefundUiState(
    val amount: String = "",
    val reason: String = "",
    val overrideNote: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showOverride: Boolean = false,
    val isComplete: Boolean = false
)

@HiltViewModel
class RefundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val issueRefundUseCase: IssueRefundUseCase
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])
    private val paymentId: String = checkNotNull(savedStateHandle["paymentId"])

    private val _uiState = MutableStateFlow(RefundUiState())
    val uiState: StateFlow<RefundUiState> = _uiState.asStateFlow()

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateReason(value: String) {
        _uiState.update { it.copy(reason = value) }
    }

    fun updateOverrideNote(value: String) {
        _uiState.update { it.copy(overrideNote = value) }
    }

    fun submit() {
        val currentState = _uiState.value
        val parsedAmount = try {
            BigDecimal(currentState.amount)
        } catch (e: NumberFormatException) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount") }
            return
        }

        if (currentState.reason.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Reason is required") }
            return
        }

        val request = IssueRefundRequest(
            orderId = orderId,
            paymentId = paymentId,
            amount = parsedAmount,
            reason = currentState.reason,
            overrideNote = currentState.overrideNote.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = issueRefundUseCase(request)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                }
                is AppResult.ValidationError -> {
                    val dailyLimitExceeded = result.fieldErrors.containsKey("overrideNote") ||
                            result.globalErrors.any { it.contains("Daily refund limit") }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation failed",
                            showOverride = dailyLimitExceeded || it.showOverride
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied: requires refund.issue")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Order or payment not found")
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
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundScreen(
    orderId: String,
    paymentId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RefundViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onComplete()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Issue Refund") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Order: #${orderId.takeLast(8)}",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Payment: #${paymentId.takeLast(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Amount field
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::updateAmount,
                    label = { Text("Refund Amount") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                // Reason field (required)
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = viewModel::updateReason,
                    label = { Text("Reason (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Override note field (shown when showOverride is true)
                if (state.showOverride) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Daily refund limit exceeded. An override note is required to proceed.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = state.overrideNote,
                        onValueChange = viewModel::updateOverrideNote,
                        label = { Text("Override Note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.amount.isNotBlank() && state.reason.isNotBlank()
                ) {
                    Text("Submit Refund")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
