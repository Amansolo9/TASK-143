package com.learnmart.app.ui.screens.commerce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.TenderType
import com.learnmart.app.domain.usecase.commerce.RecordPaymentRequest
import com.learnmart.app.domain.usecase.commerce.RecordPaymentUseCase
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

data class RecordPaymentUiState(
    val amount: String = "",
    val selectedTenderType: TenderType = TenderType.CASH,
    val externalReference: String = "",
    val notes: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class RecordPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordPaymentUseCase: RecordPaymentUseCase
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private val _uiState = MutableStateFlow(RecordPaymentUiState())
    val uiState: StateFlow<RecordPaymentUiState> = _uiState.asStateFlow()

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateSelectedTenderType(value: TenderType) {
        _uiState.update { it.copy(selectedTenderType = value) }
    }

    fun updateExternalReference(value: String) {
        _uiState.update { it.copy(externalReference = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun submit() {
        val currentState = _uiState.value
        val parsedAmount = try {
            BigDecimal(currentState.amount)
        } catch (e: NumberFormatException) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount") }
            return
        }

        if (parsedAmount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(errorMessage = "Amount must be greater than zero") }
            return
        }

        val request = RecordPaymentRequest(
            orderId = orderId,
            amount = parsedAmount,
            tenderType = currentState.selectedTenderType,
            externalReference = currentState.externalReference.ifBlank { null },
            notes = currentState.notes.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = recordPaymentUseCase(request)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation failed"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied: requires payment.record")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Order not found")
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
fun RecordPaymentScreen(
    orderId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RecordPaymentViewModel = hiltViewModel()
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
                title = { Text("Record Payment") },
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

                // Amount field
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::updateAmount,
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                // Tender Type dropdown
                TenderTypeDropdown(
                    selectedType = state.selectedTenderType,
                    onTypeSelected = viewModel::updateSelectedTenderType
                )

                // External Reference field (shown for CHECK and EXTERNAL_CARD_TERMINAL_REFERENCE)
                val requiresReference = state.selectedTenderType == TenderType.CHECK ||
                        state.selectedTenderType == TenderType.EXTERNAL_CARD_TERMINAL_REFERENCE
                if (requiresReference) {
                    val referenceLabel = when (state.selectedTenderType) {
                        TenderType.CHECK -> "Check Number / Reference"
                        TenderType.EXTERNAL_CARD_TERMINAL_REFERENCE -> "Terminal Reference"
                        else -> "External Reference"
                    }
                    OutlinedTextField(
                        value = state.externalReference,
                        onValueChange = viewModel::updateExternalReference,
                        label = { Text(referenceLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Notes field
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.amount.isNotBlank()
                ) {
                    Text("Submit Payment")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenderTypeDropdown(
    selectedType: TenderType,
    onTypeSelected: (TenderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val displayName: (TenderType) -> String = { type ->
        when (type) {
            TenderType.CASH -> "Cash"
            TenderType.CHECK -> "Check"
            TenderType.EXTERNAL_CARD_TERMINAL_REFERENCE -> "External Card Terminal"
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayName(selectedType),
            onValueChange = {},
            readOnly = true,
            label = { Text("Tender Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TenderType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(displayName(type)) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}
