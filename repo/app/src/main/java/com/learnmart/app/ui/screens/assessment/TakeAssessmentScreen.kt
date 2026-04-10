package com.learnmart.app.ui.screens.assessment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.learnmart.app.domain.usecase.assessment.ManageAssessmentUseCase
import com.learnmart.app.domain.usecase.assessment.SubmitAnswerData
import com.learnmart.app.domain.usecase.assessment.SubmitAssessmentUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TakeAssessmentUiState(
    val assignment: Assignment? = null,
    val questions: List<Question> = emptyList(),
    val choicesMap: Map<String, List<QuestionChoice>> = emptyMap(),
    val answers: MutableMap<String, SubmitAnswerData> = mutableMapOf(),
    val submission: Submission? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
    val resultMessage: String? = null
)

@HiltViewModel
class TakeAssessmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val submitUseCase: SubmitAssessmentUseCase,
    private val manageUseCase: ManageAssessmentUseCase
) : ViewModel() {
    private val assessmentId: String = savedStateHandle["assessmentId"] ?: ""
    private val _uiState = MutableStateFlow(TakeAssessmentUiState())
    val uiState: StateFlow<TakeAssessmentUiState> = _uiState.asStateFlow()

    init { loadAssessment() }

    private fun loadAssessment() {
        viewModelScope.launch(Dispatchers.IO) {
            val assignmentResult = manageUseCase.getAssignmentById(assessmentId)
            if (assignmentResult is AppResult.Success) {
                val assignment = assignmentResult.data
                val questions = assignment.questionIds.mapNotNull { qId ->
                    (manageUseCase.getQuestionsForBank(assignment.questionBankId ?: "")).find { it.id == qId }
                        ?: run { val repo = manageUseCase; null } // fallback
                }
                val choicesMap = mutableMapOf<String, List<QuestionChoice>>()
                questions.forEach { q -> choicesMap[q.id] = manageUseCase.getChoicesForQuestion(q.id) }

                // Start submission
                val subResult = submitUseCase.startSubmission(assessmentId)
                val submission = (subResult as? AppResult.Success)?.data

                _uiState.value = _uiState.value.copy(
                    assignment = assignment, questions = questions,
                    choicesMap = choicesMap, submission = submission, isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to load assessment")
            }
        }
    }

    fun updateAnswer(questionId: String, answerText: String?, selectedChoiceIds: List<String>) {
        _uiState.value.answers[questionId] = SubmitAnswerData(questionId, answerText, selectedChoiceIds)
    }

    fun submit() {
        val submission = _uiState.value.submission ?: return
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val answers = _uiState.value.answers.values.toList()
            when (val result = submitUseCase.submitAnswers(submission.id, answers)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false, isSubmitted = true,
                        resultMessage = "Submitted! Status: ${result.data.status}, Score: ${result.data.totalScore ?: "pending"}/${result.data.maxScore ?: "?"}"
                    )
                }
                is AppResult.ValidationError -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false,
                        errorMessage = result.globalErrors.firstOrNull() ?: "Validation error")
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = "Submission failed")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeAssessmentScreen(
    assessmentId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TakeAssessmentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSubmitted) { if (state.isSubmitted) { /* Stay to show result */ } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(state.assignment?.title ?: "Assessment") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (state.isSubmitted) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Assessment Submitted!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                state.resultMessage?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onComplete) { Text("Done") }
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.errorMessage?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            state.assignment?.let { a ->
                item {
                    Text(a.description, style = MaterialTheme.typography.bodyMedium)
                    Text("Total Points: ${a.totalPoints}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }

            itemsIndexed(state.questions) { index, question ->
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Q${index + 1}. ${question.questionText}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${question.points} pts | ${question.difficulty}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))

                        val choices = state.choicesMap[question.id] ?: emptyList()
                        val currentAnswer = state.answers[question.id]

                        if (question.questionType == QuestionType.OBJECTIVE && choices.isNotEmpty()) {
                            choices.forEach { choice ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = currentAnswer?.selectedChoiceIds?.contains(choice.id) == true,
                                        onClick = { viewModel.updateAnswer(question.id, null, listOf(choice.id)) }
                                    )
                                    Text(choice.choiceText, Modifier.padding(start = 4.dp))
                                }
                            }
                        } else {
                            var text by remember { mutableStateOf(currentAnswer?.answerText ?: "") }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it; viewModel.updateAnswer(question.id, it, emptyList()) },
                                label = { Text("Your Answer") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::submit, Modifier.fillMaxWidth(), enabled = !state.isSubmitting) {
                    if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp))
                    else Text("Submit Assessment")
                }
            }
        }
    }
}
