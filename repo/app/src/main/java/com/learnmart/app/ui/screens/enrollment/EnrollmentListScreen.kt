package com.learnmart.app.ui.screens.enrollment

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.learnmart.app.domain.model.ApprovalTaskStatus
import com.learnmart.app.domain.model.EnrollmentApprovalTask
import com.learnmart.app.domain.model.EnrollmentRecord
import com.learnmart.app.domain.model.EnrollmentRecordStatus
import com.learnmart.app.domain.model.EnrollmentRequest
import com.learnmart.app.domain.model.EnrollmentRequestStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentListScreen(
    onNavigateToEnrollmentDetail: (String) -> Unit,
    onNavigateToApprovalTask: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EnrollmentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showWithdrawDialog by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionMessage()
        }
    }

    if (showCancelDialog != null) {
        CancelRequestDialog(
            onConfirm = {
                val requestId = showCancelDialog!!
                showCancelDialog = null
                viewModel.cancelRequest(requestId)
            },
            onDismiss = { showCancelDialog = null }
        )
    }

    if (showWithdrawDialog != null) {
        WithdrawEnrollmentDialog(
            onConfirm = { reason ->
                val recordId = showWithdrawDialog!!
                showWithdrawDialog = null
                viewModel.withdrawEnrollment(recordId, reason)
            },
            onDismiss = { showWithdrawDialog = null }
        )
    }

    val tabTitles = listOf("My Requests", "My Enrollments", "Pending Approvals")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enrollments") },
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
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTabIndex) {
                    0 -> MyRequestsTab(
                        requests = state.myRequests,
                        onRequestClick = { onNavigateToEnrollmentDetail(it.id) },
                        onCancelRequest = { showCancelDialog = it.id }
                    )
                    1 -> MyEnrollmentsTab(
                        enrollments = state.myEnrollments,
                        onEnrollmentClick = { onNavigateToEnrollmentDetail(it.enrollmentRequestId) },
                        onWithdraw = { showWithdrawDialog = it.id }
                    )
                    2 -> PendingApprovalsTab(
                        tasks = state.pendingTasks,
                        onTaskClick = { onNavigateToApprovalTask(it.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyRequestsTab(
    requests: List<EnrollmentRequest>,
    onRequestClick: (EnrollmentRequest) -> Unit,
    onCancelRequest: (EnrollmentRequest) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No enrollment requests",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(requests, key = { it.id }) { request ->
                EnrollmentRequestCard(
                    request = request,
                    onClick = { onRequestClick(request) },
                    onCancel = if (request.status.canTransitionTo(EnrollmentRequestStatus.CANCELLED)) {
                        { onCancelRequest(request) }
                    } else {
                        null
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun EnrollmentRequestCard(
    request: EnrollmentRequest,
    onClick: () -> Unit,
    onCancel: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Class: ${request.classOfferingId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                RequestStatusChip(status = request.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Submitted: ${request.submittedAt?.let { dateTimeFormatter.format(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Priority: ${request.priorityTier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (request.expiresAt != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Expires: ${dateTimeFormatter.format(request.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (onCancel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Request")
                }
            }
        }
    }
}

@Composable
private fun MyEnrollmentsTab(
    enrollments: List<EnrollmentRecord>,
    onEnrollmentClick: (EnrollmentRecord) -> Unit,
    onWithdraw: (EnrollmentRecord) -> Unit
) {
    if (enrollments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No active enrollments",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(enrollments, key = { it.id }) { record ->
                EnrollmentRecordCard(
                    record = record,
                    onClick = { onEnrollmentClick(record) },
                    onWithdraw = if (record.status == EnrollmentRecordStatus.ACTIVE) {
                        { onWithdraw(record) }
                    } else {
                        null
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun EnrollmentRecordCard(
    record: EnrollmentRecord,
    onClick: () -> Unit,
    onWithdraw: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Class: ${record.classOfferingId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                RecordStatusChip(status = record.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enrolled: ${dateTimeFormatter.format(record.enrolledAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (record.completedAt != null) {
                Text(
                    text = "Completed: ${dateTimeFormatter.format(record.completedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.withdrawnAt != null) {
                Text(
                    text = "Withdrawn: ${dateTimeFormatter.format(record.withdrawnAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (onWithdraw != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Withdraw")
                }
            }
        }
    }
}

@Composable
private fun PendingApprovalsTab(
    tasks: List<EnrollmentApprovalTask>,
    onTaskClick: (EnrollmentApprovalTask) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No pending approval tasks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(tasks, key = { it.id }) { task ->
                ApprovalTaskCard(
                    task = task,
                    onClick = { onTaskClick(task) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ApprovalTaskCard(
    task: EnrollmentApprovalTask,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Request: ${task.enrollmentRequestId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ApprovalTaskStatusChip(status = task.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Role: ${task.assignedToRoleType.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Created: ${dateTimeFormatter.format(task.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Expires: ${dateTimeFormatter.format(task.expiresAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun RequestStatusChip(status: EnrollmentRequestStatus) {
    val (label, containerColor, contentColor) = when (status) {
        EnrollmentRequestStatus.DRAFT -> Triple(
            "Draft",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        EnrollmentRequestStatus.SUBMITTED -> Triple(
            "Submitted",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        EnrollmentRequestStatus.PENDING_APPROVAL -> Triple(
            "Pending Approval",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        EnrollmentRequestStatus.WAITLISTED -> Triple(
            "Waitlisted",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        EnrollmentRequestStatus.OFFERED -> Triple(
            "Offered",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        EnrollmentRequestStatus.APPROVED -> Triple(
            "Approved",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
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
        EnrollmentRequestStatus.EXPIRED -> Triple(
            "Expired",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        EnrollmentRequestStatus.CANCELLED -> Triple(
            "Cancelled",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        EnrollmentRequestStatus.DECLINED -> Triple(
            "Declined",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        EnrollmentRequestStatus.WITHDRAWN -> Triple(
            "Withdrawn",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        EnrollmentRequestStatus.COMPLETED -> Triple(
            "Completed",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
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
private fun RecordStatusChip(status: EnrollmentRecordStatus) {
    val (label, containerColor, contentColor) = when (status) {
        EnrollmentRecordStatus.ACTIVE -> Triple(
            "Active",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        EnrollmentRecordStatus.COMPLETED -> Triple(
            "Completed",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        EnrollmentRecordStatus.WITHDRAWN -> Triple(
            "Withdrawn",
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
private fun ApprovalTaskStatusChip(status: ApprovalTaskStatus) {
    val (label, containerColor, contentColor) = when (status) {
        ApprovalTaskStatus.PENDING -> Triple(
            "Pending",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ApprovalTaskStatus.APPROVED -> Triple(
            "Approved",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        ApprovalTaskStatus.REJECTED -> Triple(
            "Rejected",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        ApprovalTaskStatus.EXPIRED -> Triple(
            "Expired",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ApprovalTaskStatus.SKIPPED -> Triple(
            "Skipped",
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

@Composable
private fun CancelRequestDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Request") },
        text = {
            Text("Are you sure you want to cancel this enrollment request? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Cancel Request",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Request")
            }
        }
    )
}

@Composable
private fun WithdrawEnrollmentDialog(
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw Enrollment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to withdraw from this class? This action cannot be undone.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text(
                    text = "Withdraw",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Enrollment")
            }
        }
    )
}
