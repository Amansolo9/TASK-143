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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.learnmart.app.domain.model.ClassOffering
import com.learnmart.app.domain.model.EnrollmentRequest
import com.learnmart.app.domain.model.EnrollmentRequestStatus
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.usecase.enrollment.SubmitEnrollmentUseCase
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

data class EnrollmentRequestUiState(
    val classOffering: ClassOffering? = null,
    val submittedRequest: EnrollmentRequest? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val resultStatus: EnrollmentRequestStatus? = null,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class EnrollmentRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courseRepository: CourseRepository,
    private val submitEnrollmentUseCase: SubmitEnrollmentUseCase
) : ViewModel() {

    private val classOfferingId: String = checkNotNull(savedStateHandle["classOfferingId"])

    private val _uiState = MutableStateFlow(EnrollmentRequestUiState())
    val uiState: StateFlow<EnrollmentRequestUiState> = _uiState.asStateFlow()

    init {
        loadClassOffering()
    }

    private fun loadClassOffering() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val classOffering = courseRepository.getClassOfferingById(classOfferingId)
            if (classOffering != null) {
                _uiState.update {
                    it.copy(classOffering = classOffering, isLoading = false)
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Class offering not found")
                }
            }
        }
    }

    fun submitEnrollment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = submitEnrollmentUseCase(classOfferingId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isSubmitted = true,
                            submittedRequest = result.data,
                            resultStatus = result.data.status,
                            warnings = result.warnings
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Permission denied. Please log in.")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Class offering not found")
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
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

private val screenDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentRequestScreen(
    onNavigateBack: () -> Unit,
    viewModel: EnrollmentRequestViewModel = hiltViewModel()
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
                title = { Text("Enroll in Class") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.isSubmitted && state.submittedRequest != null -> {
                    EnrollmentResultContent(
                        request = state.submittedRequest!!,
                        resultStatus = state.resultStatus,
                        warnings = state.warnings,
                        onNavigateBack = onNavigateBack
                    )
                }

                state.classOffering != null -> {
                    ClassOfferingInfoCard(classOffering = state.classOffering!!)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Confirm Enrollment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "By submitting, you are requesting enrollment in this class. " +
                                "Your request may be auto-approved, placed on a waitlist, or " +
                                "routed for manual approval depending on class capacity and policies.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = viewModel::submitEnrollment,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Submit Enrollment Request")
                        }
                    }
                }

                state.errorMessage == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Class offering not found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassOfferingInfoCard(classOffering: ClassOffering) {
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
                text = classOffering.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Divider()

            if (classOffering.description.isNotBlank()) {
                Text(
                    text = classOffering.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ClassInfoRow(label = "Status", value = classOffering.status.name)
            ClassInfoRow(label = "Location", value = classOffering.location)
            ClassInfoRow(
                label = "Schedule",
                value = "${screenDateTimeFormatter.format(classOffering.scheduleStart)} - ${screenDateTimeFormatter.format(classOffering.scheduleEnd)}"
            )
            ClassInfoRow(
                label = "Capacity",
                value = "${classOffering.enrolledCount} / ${classOffering.hardCapacity}"
            )
            ClassInfoRow(
                label = "Waitlist",
                value = if (classOffering.waitlistEnabled) "Enabled" else "Disabled"
            )
        }
    }
}

@Composable
private fun ClassInfoRow(label: String, value: String) {
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
private fun EnrollmentResultContent(
    request: EnrollmentRequest,
    resultStatus: EnrollmentRequestStatus?,
    warnings: List<String>,
    onNavigateBack: () -> Unit
) {
    val (title, description) = when (resultStatus) {
        EnrollmentRequestStatus.ENROLLED -> Pair(
            "Successfully Enrolled",
            "You have been enrolled in this class."
        )
        EnrollmentRequestStatus.WAITLISTED -> Pair(
            "Added to Waitlist",
            "The class is currently at capacity. You have been added to the waitlist and will be notified when a spot becomes available."
        )
        EnrollmentRequestStatus.PENDING_APPROVAL -> Pair(
            "Pending Approval",
            "Your enrollment request has been submitted and is awaiting approval. You will be notified once a decision has been made."
        )
        EnrollmentRequestStatus.SUBMITTED -> Pair(
            "Request Submitted",
            "Your enrollment request has been submitted and is being processed."
        )
        EnrollmentRequestStatus.APPROVED -> Pair(
            "Request Approved",
            "Your enrollment request has been approved."
        )
        else -> Pair(
            "Request Submitted",
            "Your enrollment request has been submitted with status: ${resultStatus?.name ?: "UNKNOWN"}."
        )
    }

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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Divider()

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (resultStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ResultStatusChip(status = resultStatus)
                }
            }

            if (request.submittedAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Submitted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = screenDateTimeFormatter.format(request.submittedAt),
                        style = MaterialTheme.typography.bodyMedium
                    )
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

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onNavigateBack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Done")
    }
}

@Composable
private fun ResultStatusChip(status: EnrollmentRequestStatus) {
    val (label, containerColor, contentColor) = when (status) {
        EnrollmentRequestStatus.ENROLLED -> Triple(
            "Enrolled",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        EnrollmentRequestStatus.WAITLISTED -> Triple(
            "Waitlisted",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        EnrollmentRequestStatus.PENDING_APPROVAL -> Triple(
            "Pending Approval",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        EnrollmentRequestStatus.APPROVED -> Triple(
            "Approved",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        EnrollmentRequestStatus.SUBMITTED -> Triple(
            "Submitted",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
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
