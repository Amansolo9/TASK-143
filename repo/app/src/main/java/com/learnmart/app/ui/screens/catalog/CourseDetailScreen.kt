package com.learnmart.app.ui.screens.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.learnmart.app.domain.model.ClassOffering
import com.learnmart.app.domain.model.ClassOfferingStatus
import com.learnmart.app.domain.model.ClassSession
import com.learnmart.app.domain.model.ClassStaffAssignment
import com.learnmart.app.domain.model.CourseStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onNavigateToCreateClass: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CourseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnpublishDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

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

    if (showUnpublishDialog) {
        ReasonDialog(
            title = "Unpublish Course",
            description = "Provide a reason for unpublishing this course.",
            confirmLabel = "Unpublish",
            onConfirm = { reason ->
                showUnpublishDialog = false
                viewModel.unpublishCourse(reason.ifBlank { null })
            },
            onDismiss = { showUnpublishDialog = false }
        )
    }

    if (showArchiveDialog) {
        ReasonDialog(
            title = "Archive Course",
            description = "Provide a reason for archiving this course. This action cannot be undone.",
            confirmLabel = "Archive",
            onConfirm = { reason ->
                showArchiveDialog = false
                viewModel.archiveCourse(reason.ifBlank { null })
            },
            onDismiss = { showArchiveDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.course != null) {
                FloatingActionButton(onClick = { onNavigateToCreateClass(courseId) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Class"
                    )
                }
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading && state.course == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.course == null && state.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load course",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.course != null -> {
                val course = state.course!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(0.dp))
                    }

                    // Course info card
                    item {
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
                                    text = course.title,
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                Divider()

                                CourseDetailRow(label = "Code", value = course.code)
                                CourseDetailRow(label = "Status", value = null) {
                                    CourseStatusChip(status = course.status)
                                }
                                if (course.description.isNotBlank()) {
                                    CourseDetailRow(label = "Description", value = course.description)
                                }
                                CourseDetailRow(
                                    label = "Version",
                                    value = "v${course.version}"
                                )
                                CourseDetailRow(
                                    label = "Created",
                                    value = dateTimeFormatter.format(course.createdAt)
                                )
                            }
                        }
                    }

                    // Action buttons
                    item {
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
                                    text = "Actions",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                val allowedTransitions = course.status.allowedTransitions()

                                if (CourseStatus.PUBLISHED in allowedTransitions) {
                                    Button(
                                        onClick = { viewModel.publishCourse() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !state.isLoading
                                    ) {
                                        Text("Publish Course")
                                    }
                                }

                                if (CourseStatus.UNPUBLISHED in allowedTransitions) {
                                    OutlinedButton(
                                        onClick = { showUnpublishDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !state.isLoading
                                    ) {
                                        Text("Unpublish Course")
                                    }
                                }

                                if (CourseStatus.ARCHIVED in allowedTransitions) {
                                    OutlinedButton(
                                        onClick = { showArchiveDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !state.isLoading,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Archive Course")
                                    }
                                }

                                if (allowedTransitions.isEmpty()) {
                                    Text(
                                        text = "No actions available for this course status.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Class Offerings section
                    item {
                        Text(
                            text = "Class Offerings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (state.classOfferings.isEmpty()) {
                        item {
                            Text(
                                text = "No class offerings yet. Tap + to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.classOfferings, key = { it.id }) { offering ->
                            ClassOfferingCard(
                                offering = offering,
                                sessions = state.sessions[offering.id] ?: emptyList(),
                                staffAssignments = state.staff[offering.id] ?: emptyList(),
                                isLoading = state.isLoading,
                                onOpenForEnrollment = { viewModel.openClassForEnrollment(offering.id) },
                                onTransitionStatus = { targetStatus ->
                                    viewModel.transitionClassStatus(offering.id, targetStatus)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassOfferingCard(
    offering: ClassOffering,
    sessions: List<ClassSession>,
    staffAssignments: List<ClassStaffAssignment>,
    isLoading: Boolean,
    onOpenForEnrollment: () -> Unit,
    onTransitionStatus: (ClassOfferingStatus) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row - clickable to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offering.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClassOfferingStatusChip(status = offering.status)
                        Text(
                            text = "${offering.enrolledCount}/${offering.hardCapacity}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary info always visible
            Text(
                text = "Schedule: ${dateTimeFormatter.format(offering.scheduleStart)} - ${dateTimeFormatter.format(offering.scheduleEnd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (offering.location.isNotBlank()) {
                Text(
                    text = "Location: ${offering.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Staff: ${staffAssignments.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Open for enrollment button (if PLANNED)
            if (offering.status == ClassOfferingStatus.PLANNED) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenForEnrollment,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Open for Enrollment")
                }
            }

            // Status transition buttons
            val allowedTransitions = offering.status.allowedTransitions()
                .filter { it != ClassOfferingStatus.OPEN }
            if (allowedTransitions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allowedTransitions.forEach { targetStatus ->
                        OutlinedButton(
                            onClick = { onTransitionStatus(targetStatus) },
                            enabled = !isLoading
                        ) {
                            Text(formatTransitionLabel(targetStatus))
                        }
                    }
                }
            }

            // Expanded content: sessions and staff
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider()

                    // Sessions section
                    Text(
                        text = "Sessions (${sessions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (sessions.isEmpty()) {
                        Text(
                            text = "No sessions defined",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sessions.sortedBy { it.sessionOrder }.forEach { session ->
                            SessionRow(session = session)
                        }
                    }

                    Divider()

                    // Staff section
                    Text(
                        text = "Staff (${staffAssignments.size})",
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (staffAssignments.isEmpty()) {
                        Text(
                            text = "No staff assigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        staffAssignments.forEach { assignment ->
                            StaffRow(assignment = assignment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ClassSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${session.sessionOrder} - ${session.title}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${dateTimeFormatter.format(session.sessionTime)} (${session.durationMinutes} min)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.location?.let { loc ->
                if (loc.isNotBlank()) {
                    Text(
                        text = loc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffRow(assignment: ClassStaffAssignment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = assignment.userId,
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = assignment.staffRole.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CourseDetailRow(
    label: String,
    value: String?,
    valueContent: @Composable (() -> Unit)? = null
) {
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
        if (valueContent != null) {
            valueContent()
        } else {
            Text(
                text = value ?: "",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ClassOfferingStatusChip(status: ClassOfferingStatus) {
    val (label, containerColor, contentColor) = when (status) {
        ClassOfferingStatus.PLANNED -> Triple(
            "Planned",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ClassOfferingStatus.OPEN -> Triple(
            "Open",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        ClassOfferingStatus.CLOSED -> Triple(
            "Closed",
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
        ClassOfferingStatus.IN_PROGRESS -> Triple(
            "In Progress",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        ClassOfferingStatus.COMPLETED -> Triple(
            "Completed",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        ClassOfferingStatus.CANCELLED -> Triple(
            "Cancelled",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        ClassOfferingStatus.ARCHIVED -> Triple(
            "Archived",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surface
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
private fun ReasonDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTransitionLabel(status: ClassOfferingStatus): String = when (status) {
    ClassOfferingStatus.PLANNED -> "Set Planned"
    ClassOfferingStatus.OPEN -> "Open"
    ClassOfferingStatus.CLOSED -> "Close"
    ClassOfferingStatus.IN_PROGRESS -> "Start"
    ClassOfferingStatus.COMPLETED -> "Complete"
    ClassOfferingStatus.CANCELLED -> "Cancel"
    ClassOfferingStatus.ARCHIVED -> "Archive"
}
