package com.learnmart.app.ui.screens.enrollment

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.ApprovalTaskStatus
import com.learnmart.app.domain.model.EnrollmentApprovalTask
import com.learnmart.app.domain.model.EnrollmentDecisionEvent
import com.learnmart.app.domain.model.EnrollmentEligibilitySnapshot
import com.learnmart.app.domain.model.EnrollmentRequest
import com.learnmart.app.domain.model.EnrollmentRequestStatus
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.usecase.enrollment.ManageEnrollmentUseCase
import com.learnmart.app.domain.usecase.enrollment.ResolveApprovalUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class ApprovalTaskUiState(
    val task: EnrollmentApprovalTask? = null,
    val request: EnrollmentRequest? = null,
    val eligibilitySnapshot: EnrollmentEligibilitySnapshot? = null,
    val decisionHistory: List<EnrollmentDecisionEvent> = emptyList(),
    val notes: String = "",
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val isResolved: Boolean = false,
    val resultStatus: EnrollmentRequestStatus? = null,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ApprovalTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageEnrollmentUseCase: ManageEnrollmentUseCase,
    private val resolveApprovalUseCase: ResolveApprovalUseCase,
    private val enrollmentRepository: EnrollmentRepository
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow(ApprovalTaskUiState())
    val uiState: StateFlow<ApprovalTaskUiState> = _uiState.asStateFlow()

    init {
        loadTask()
    }

    private fun loadTask() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val task = enrollmentRepository.getApprovalTaskById(taskId)
            if (task == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Approval task not found")
                }
                return@launch
            }

            val requestResult = manageEnrollmentUseCase.getRequestById(task.enrollmentRequestId)
            val request = when (requestResult) {
                is AppResult.Success -> requestResult.data
                else -> null
            }

            val eligibilitySnapshot = if (request?.eligibilitySnapshotId != null) {
                enrollmentRepository.getEligibilitySnapshot(request.id)
            } else {
                null
            }

            val decisionHistory = manageEnrollmentUseCase.getDecisionHistory(task.enrollmentRequestId)

            _uiState.update {
                it.copy(
                    task = task,
                    request = request,
                    eligibilitySnapshot = eligibilitySnapshot,
                    decisionHistory = decisionHistory,
                    isLoading = false
                )
            }
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes, errorMessage = null) }
    }

    fun approve() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val notes = _uiState.value.notes.ifBlank { null }
            when (val result = resolveApprovalUseCase.approve(taskId, notes)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isResolved = true,
                            resultStatus = result.data.status,
                            warnings = result.warnings
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = "Permission denied. Requires enrollment.review permission.")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = result.message)
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = "Task or request not found")
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun reject() {
        viewModelScope.launch {
            val notes = _uiState.value.notes
            if (notes.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Notes are required when rejecting a request")
                }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            when (val result = resolveApprovalUseCase.reject(taskId, notes)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isResolved = true,
                            resultStatus = result.data.status,
                            warnings = result.warnings
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = "Permission denied. Requires enrollment.review permission.")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = result.message)
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = "Task or request not found")
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = result.message)
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

private val approvalDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApprovalTaskViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Enrollment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.task == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Approval task not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.isResolved -> {
                ResolvedContent(
                    resultStatus = state.resultStatus,
                    warnings = state.warnings,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.padding(padding)
                )
            }

            else -> {
                ApprovalTaskContent(
                    task = state.task!!,
                    request = state.request,
                    eligibilitySnapshot = state.eligibilitySnapshot,
                    decisionHistory = state.decisionHistory,
                    notes = state.notes,
                    isProcessing = state.isProcessing,
                    onNotesChanged = viewModel::onNotesChanged,
                    onApprove = viewModel::approve,
                    onReject = viewModel::reject,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ApprovalTaskContent(
    task: EnrollmentApprovalTask,
    request: EnrollmentRequest?,
    eligibilitySnapshot: EnrollmentEligibilitySnapshot?,
    decisionHistory: List<EnrollmentDecisionEvent>,
    notes: String,
    isProcessing: Boolean,
    onNotesChanged: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Task info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Task Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                TaskInfoRow(label = "Task ID", value = task.id.take(8) + "...")
                TaskInfoRow(label = "Status", value = task.status.name)
                TaskInfoRow(label = "Assigned Role", value = task.assignedToRoleType.name)
                TaskInfoRow(label = "Created", value = approvalDateTimeFormatter.format(task.createdAt))
                TaskInfoRow(label = "Expires", value = approvalDateTimeFormatter.format(task.expiresAt))

                if (task.assignedToUserId != null) {
                    TaskInfoRow(label = "Assigned To", value = task.assignedToUserId.take(8) + "...")
                }
            }
        }

        // Request details card
        if (request != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Request Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    TaskInfoRow(label = "Request ID", value = request.id.take(8) + "...")
                    TaskInfoRow(label = "Learner ID", value = request.learnerId.take(8) + "...")
                    TaskInfoRow(label = "Class Offering", value = request.classOfferingId.take(8) + "...")
                    TaskInfoRow(label = "Request Status", value = request.status.name)
                    TaskInfoRow(label = "Priority Tier", value = request.priorityTier.toString())

                    if (request.submittedAt != null) {
                        TaskInfoRow(
                            label = "Submitted",
                            value = approvalDateTimeFormatter.format(request.submittedAt)
                        )
                    }

                    if (request.expiresAt != null) {
                        TaskInfoRow(
                            label = "Expires",
                            value = approvalDateTimeFormatter.format(request.expiresAt)
                        )
                    }
                }
            }
        }

        // Eligibility info card
        if (eligibilitySnapshot != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Eligibility Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Eligible",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        EligibilityChip(isEligible = eligibilitySnapshot.isEligible)
                    }

                    TaskInfoRow(
                        label = "Evaluated At",
                        value = approvalDateTimeFormatter.format(eligibilitySnapshot.evaluatedAt)
                    )

                    Text(
                        text = "Flags: ${eligibilitySnapshot.eligibilityFlags}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Decision history card
        if (decisionHistory.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Decision History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    decisionHistory.forEach { event ->
                        DecisionEventRow(event = event)
                        if (event != decisionHistory.last()) {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // Action section
        if (task.status == ApprovalTaskStatus.PENDING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Decision",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    OutlinedTextField(
                        value = notes,
                        onValueChange = onNotesChanged,
                        label = { Text("Notes (required for rejection)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Approve")
                            }
                        }

                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskInfoRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EligibilityChip(isEligible: Boolean) {
    val (label, containerColor, contentColor) = if (isEligible) {
        Triple(
            "Eligible",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
    } else {
        Triple(
            "Not Eligible",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
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

@Composable
private fun DecisionEventRow(event: EnrollmentDecisionEvent) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.decision,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = approvalDateTimeFormatter.format(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (event.decidedBy != null) {
            Text(
                text = "By: ${event.decidedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (event.reason != null) {
            Text(
                text = "Reason: ${event.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResolvedContent(
    resultStatus: EnrollmentRequestStatus?,
    warnings: List<String>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Decision Recorded",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                if (resultStatus != null) {
                    val description = when (resultStatus) {
                        EnrollmentRequestStatus.ENROLLED -> "The enrollment request has been approved and the learner is now enrolled."
                        EnrollmentRequestStatus.REJECTED -> "The enrollment request has been rejected."
                        EnrollmentRequestStatus.WAITLISTED -> "The request was approved but the class is at capacity. The learner has been added to the waitlist."
                        EnrollmentRequestStatus.PENDING_APPROVAL -> "This approval step is complete. The request is awaiting additional approvals."
                        else -> "The request status has been updated to ${resultStatus.name}."
                    }

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Result Status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResolvedStatusChip(status = resultStatus)
                    }
                }

                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    warnings.forEach { warning ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = warning,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ResolvedStatusChip(status: EnrollmentRequestStatus) {
    val (label, containerColor, contentColor) = when (status) {
        EnrollmentRequestStatus.ENROLLED -> Triple(
            "Enrolled",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        EnrollmentRequestStatus.REJECTED -> Triple(
            "Rejected",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        EnrollmentRequestStatus.WAITLISTED -> Triple(
            "Waitlisted",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        EnrollmentRequestStatus.PENDING_APPROVAL -> Triple(
            "Pending Further Approval",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            status.name,
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
