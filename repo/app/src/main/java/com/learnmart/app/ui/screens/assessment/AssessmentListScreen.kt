package com.learnmart.app.ui.screens.assessment

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.Assignment
import com.learnmart.app.domain.model.GradeQueueStatus
import com.learnmart.app.domain.model.Submission
import com.learnmart.app.domain.model.SubmissionStatus
import com.learnmart.app.domain.model.SubjectiveGradeQueueItem
import com.learnmart.app.domain.usecase.assessment.ManageAssessmentUseCase
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

data class AssessmentListUiState(
    val assignments: List<Assignment> = emptyList(),
    val mySubmissions: List<Submission> = emptyList(),
    val gradingQueue: List<SubjectiveGradeQueueItem> = emptyList(),
    val selectedTab: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class AssessmentListViewModel @Inject constructor(
    private val manageAssessmentUseCase: ManageAssessmentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssessmentListUiState())
    val uiState: StateFlow<AssessmentListUiState> = _uiState.asStateFlow()

    init {
        loadAssignments()
        loadMySubmissions()
        loadGradingQueue()
    }

    private fun loadAssignments() {
        viewModelScope.launch {
            val assignments = manageAssessmentUseCase.getAssignmentsForClass("")
            _uiState.update { it.copy(assignments = assignments, isLoading = false) }
        }
    }

    private fun loadMySubmissions() {
        viewModelScope.launch {
            val submissions = manageAssessmentUseCase.getMySubmissions()
            _uiState.update { it.copy(mySubmissions = submissions) }
        }
    }

    private fun loadGradingQueue() {
        viewModelScope.launch {
            val queue = manageAssessmentUseCase.getMyGradingQueue()
            _uiState.update { it.copy(gradingQueue = queue) }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentListScreen(
    onNavigateToAssessmentDetail: (String) -> Unit,
    onNavigateToSubmission: (String) -> Unit,
    onNavigateToGradeItem: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AssessmentListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val tabTitles = listOf("Assignments", "My Submissions", "Grading Queue")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assessments") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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
                        onClick = {
                            selectedTabIndex = index
                            viewModel.selectTab(index)
                        },
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
                    0 -> AssignmentsTab(
                        assignments = state.assignments,
                        onAssignmentClick = { onNavigateToAssessmentDetail(it.id) }
                    )
                    1 -> MySubmissionsTab(
                        submissions = state.mySubmissions,
                        onSubmissionClick = { onNavigateToSubmission(it.id) }
                    )
                    2 -> GradingQueueTab(
                        queueItems = state.gradingQueue,
                        onQueueItemClick = { onNavigateToGradeItem(it.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentsTab(
    assignments: List<Assignment>,
    onAssignmentClick: (Assignment) -> Unit
) {
    if (assignments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No assignments available",
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
            items(assignments, key = { it.id }) { assignment ->
                AssignmentCard(
                    assignment = assignment,
                    onClick = { onAssignmentClick(assignment) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AssignmentCard(
    assignment: Assignment,
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
                    text = assignment.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AssessmentTypeChip(type = assignment.assessmentType.name)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = assignment.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Points: ${assignment.totalPoints}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Due: ${dateTimeFormatter.format(assignment.releaseEnd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (assignment.timeLimitMinutes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Time Limit: ${assignment.timeLimitMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun MySubmissionsTab(
    submissions: List<Submission>,
    onSubmissionClick: (Submission) -> Unit
) {
    if (submissions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No submissions yet",
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
            items(submissions, key = { it.id }) { submission ->
                SubmissionCard(
                    submission = submission,
                    onClick = { onSubmissionClick(submission) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SubmissionCard(
    submission: Submission,
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
                    text = "Assessment: ${submission.assessmentId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SubmissionStatusChip(status = submission.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (submission.submittedAt != null)
                        "Submitted: ${dateTimeFormatter.format(submission.submittedAt)}"
                    else
                        "Started: ${submission.startedAt?.let { dateTimeFormatter.format(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (submission.totalScore != null && submission.maxScore != null) {
                    Text(
                        text = "Score: ${submission.totalScore}/${submission.maxScore}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (submission.gradePercentage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grade: ${"%.1f".format(submission.gradePercentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GradingQueueTab(
    queueItems: List<SubjectiveGradeQueueItem>,
    onQueueItemClick: (SubjectiveGradeQueueItem) -> Unit
) {
    if (queueItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No items to grade",
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
            items(queueItems, key = { it.id }) { item ->
                GradingQueueCard(
                    item = item,
                    onClick = { onQueueItemClick(item) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GradingQueueCard(
    item: SubjectiveGradeQueueItem,
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
                    text = "Question: ${item.questionId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                GradeQueueStatusChip(status = item.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Submission: ${item.submissionId.take(8)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Created: ${dateTimeFormatter.format(item.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.assignedRoleType != null) {
                Text(
                    text = "Assigned Role: ${item.assignedRoleType.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun AssessmentTypeChip(type: String) {
    val (containerColor, contentColor) = when (type) {
        "QUIZ" -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = type,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SubmissionStatusChip(status: SubmissionStatus) {
    val (label, containerColor, contentColor) = when (status) {
        SubmissionStatus.NOT_RELEASED -> Triple(
            "Not Released",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SubmissionStatus.AVAILABLE -> Triple(
            "Available",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SubmissionStatus.IN_PROGRESS -> Triple(
            "In Progress",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SubmissionStatus.SUBMITTED -> Triple(
            "Submitted",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SubmissionStatus.LATE_SUBMITTED -> Triple(
            "Late",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        SubmissionStatus.AUTO_GRADED -> Triple(
            "Auto Graded",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SubmissionStatus.QUEUED_FOR_MANUAL_REVIEW -> Triple(
            "Pending Review",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SubmissionStatus.GRADED -> Triple(
            "Graded",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        SubmissionStatus.FINALIZED -> Triple(
            "Finalized",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        SubmissionStatus.REOPENED_BY_INSTRUCTOR -> Triple(
            "Reopened",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SubmissionStatus.MISSED -> Triple(
            "Missed",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        SubmissionStatus.ABANDONED -> Triple(
            "Abandoned",
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
private fun GradeQueueStatusChip(status: GradeQueueStatus) {
    val (label, containerColor, contentColor) = when (status) {
        GradeQueueStatus.PENDING -> Triple(
            "Pending",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        GradeQueueStatus.IN_REVIEW -> Triple(
            "In Review",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        GradeQueueStatus.GRADED -> Triple(
            "Graded",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        GradeQueueStatus.SKIPPED -> Triple(
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
