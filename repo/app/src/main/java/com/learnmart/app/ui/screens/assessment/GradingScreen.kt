package com.learnmart.app.ui.screens.assessment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.usecase.assessment.ManageAssessmentUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradingUiState(
    val queueItem: SubjectiveGradeQueueItem? = null,
    val question: Question? = null,
    val answer: SubmissionAnswer? = null,
    val score: String = "",
    val feedback: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class GradingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageAssessmentUseCase: ManageAssessmentUseCase,
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {
    private val queueItemId: String = savedStateHandle["queueItemId"] ?: ""
    private val _uiState = MutableStateFlow(GradingUiState())
    val uiState: StateFlow<GradingUiState> = _uiState.asStateFlow()

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val item = assessmentRepository.getGradeQueueItemById(queueItemId)
            if (item != null) {
                val question = assessmentRepository.getQuestionById(item.questionId)
                val answers = assessmentRepository.getAnswersForSubmission(item.submissionId)
                val answer = answers.find { it.questionId == item.questionId }
                _uiState.value = GradingUiState(queueItem = item, question = question, answer = answer, isLoading = false)
            } else {
                _uiState.value = GradingUiState(isLoading = false, errorMessage = "Queue item not found")
            }
        }
    }

    fun onScoreChanged(v: String) { _uiState.value = _uiState.value.copy(score = v, errorMessage = null) }
    fun onFeedbackChanged(v: String) { _uiState.value = _uiState.value.copy(feedback = v) }

    fun submitGrade() {
        val score = _uiState.value.score.toIntOrNull()
        if (score == null || score < 0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Score must be a non-negative integer")
            return
        }
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = manageAssessmentUseCase.gradeSubjectiveAnswer(
                queueItemId, score, _uiState.value.feedback.ifBlank { null }
            )) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(isSubmitting = false, isComplete = true)
                is AppResult.ValidationError -> _uiState.value = _uiState.value.copy(isSubmitting = false,
                    errorMessage = result.globalErrors.firstOrNull() ?: result.fieldErrors.values.firstOrNull() ?: "Error")
                is AppResult.PermissionError -> _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = "Permission denied")
                else -> _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = "Grading failed")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradingScreen(
    queueItemId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GradingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.isComplete) { if (state.isComplete) onComplete() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grade Answer") },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.errorMessage?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            state.question?.let { q ->
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(q.questionText, style = MaterialTheme.typography.bodyLarge)
                        Text("Max Points: ${q.points} | Difficulty: ${q.difficulty}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            state.answer?.let { a ->
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Learner's Answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(a.answerText ?: "(No text answer)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Divider()
            Text("Grade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(value = state.score, onValueChange = viewModel::onScoreChanged,
                label = { Text("Score (0-${state.question?.points ?: "?"})") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.feedback, onValueChange = viewModel::onFeedbackChanged,
                label = { Text("Feedback (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

            Button(onClick = viewModel::submitGrade, Modifier.fillMaxWidth(), enabled = !state.isSubmitting) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp))
                else Text("Submit Grade")
            }
        }
    }
}
