package com.learnmart.app.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.DiscrepancyCase
import com.learnmart.app.domain.model.DiscrepancyCaseStatus
import com.learnmart.app.domain.model.ReconciliationRun
import com.learnmart.app.domain.usecase.operations.ReconciliationUseCase
import com.learnmart.app.util.AppResult
import com.learnmart.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class ReconciliationUiState(
    val batchId: String = "",
    val isRunning: Boolean = false,
    val isEnqueued: Boolean = false,
    val isCompleted: Boolean = false,
    val reconciliationRun: ReconciliationRun? = null,
    val discrepancyCases: List<DiscrepancyCase> = emptyList(),
    val isResolvingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@HiltViewModel
class ReconciliationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reconciliationUseCase: ReconciliationUseCase,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val batchId: String = checkNotNull(savedStateHandle["batchId"])

    private val _uiState = MutableStateFlow(ReconciliationUiState(batchId = batchId))
    val uiState: StateFlow<ReconciliationUiState> = _uiState.asStateFlow()

    /**
     * Enqueues reconciliation via WorkManager instead of running inline.
     * The heavy matching/write logic runs in ReconciliationWorker under idle+charging constraints.
     */
    fun enqueueReconciliation() {
        _uiState.update { it.copy(isRunning = true, isEnqueued = true, errorMessage = null, infoMessage = null) }
        workScheduler.enqueueReconciliationJob(batchId)
        _uiState.update {
            it.copy(
                isRunning = false,
                infoMessage = "Reconciliation job enqueued. It will run when the device is idle and charging."
            )
        }
    }

    /**
     * After the worker completes (observed externally or on re-entry), load results.
     */
    fun loadResults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, errorMessage = null) }
            val discrepancies = reconciliationUseCase.getOpenDiscrepancies()
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isCompleted = discrepancies.isNotEmpty(),
                    discrepancyCases = discrepancies
                )
            }
        }
    }

    fun resolveDiscrepancy(caseId: String, resolutionNote: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingId = caseId, errorMessage = null) }
            when (val result = reconciliationUseCase.resolveDiscrepancy(caseId, resolutionNote)) {
                is AppResult.Success -> {
                    val updatedCases = _uiState.value.discrepancyCases.map { case_ ->
                        if (case_.id == caseId) result.data else case_
                    }
                    _uiState.update {
                        it.copy(isResolvingId = null, discrepancyCases = updatedCases)
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isResolvingId = null,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isResolvingId = null, errorMessage = "Permission denied: ${result.code}")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isResolvingId = null, errorMessage = "Discrepancy case not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isResolvingId = null, errorMessage = result.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isResolvingId = null, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

private val reconciliationDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReconciliationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearInfo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reconciliation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Batch info
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Settlement Batch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Batch ID: ${state.batchId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enqueue reconciliation (WorkManager-driven, not inline)
            if (!state.isEnqueued) {
                Button(
                    onClick = viewModel::enqueueReconciliation,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRunning
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Run Reconciliation")
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Reconciliation Enqueued",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The job will execute when the device is idle and charging. " +
                                "Tap 'Load Results' after the job completes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = viewModel::loadResults,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRunning
                ) {
                    Text("Load Results")
                }
            }

            if (state.discrepancyCases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Discrepancy Cases (${state.discrepancyCases.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.discrepancyCases, key = { it.id }) { case_ ->
                        ReconciliationDiscrepancyCard(
                            case_ = case_,
                            isResolving = state.isResolvingId == case_.id,
                            onResolve = { note ->
                                viewModel.resolveDiscrepancy(case_.id, note)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            } else if (state.isEnqueued && !state.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No discrepancy cases loaded yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReconciliationStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReconciliationDiscrepancyCard(
    case_: DiscrepancyCase,
    isResolving: Boolean,
    onResolve: (String) -> Unit
) {
    var showResolveDialog by remember { mutableStateOf(false) }
    var resolutionNote by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = case_.discrepancyType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ReconciliationDiscrepancyStatusChip(status = case_.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = case_.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Created: ${reconciliationDateFormatter.format(case_.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (case_.status == DiscrepancyCaseStatus.OPEN || case_.status == DiscrepancyCaseStatus.INVESTIGATING) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResolveDialog = true },
                    enabled = !isResolving
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Resolve")
                }
            }

            if (case_.status == DiscrepancyCaseStatus.RESOLVED && case_.resolutionNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resolution: ${case_.resolutionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title = { Text("Resolve Discrepancy") },
            text = {
                Column {
                    Text(
                        text = "Provide a resolution note for this discrepancy case.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolutionNote,
                        onValueChange = { resolutionNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Resolution Note") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResolveDialog = false
                        onResolve(resolutionNote)
                        resolutionNote = ""
                    },
                    enabled = resolutionNote.isNotBlank()
                ) {
                    Text("Resolve")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResolveDialog = false
                    resolutionNote = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReconciliationDiscrepancyStatusChip(status: DiscrepancyCaseStatus) {
    val (label, containerColor, contentColor) = when (status) {
        DiscrepancyCaseStatus.OPEN -> Triple(
            "Open",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        DiscrepancyCaseStatus.INVESTIGATING -> Triple(
            "Investigating",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        DiscrepancyCaseStatus.RESOLVED -> Triple(
            "Resolved",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        DiscrepancyCaseStatus.CLOSED -> Triple(
            "Closed",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
