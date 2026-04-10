package com.learnmart.app.ui.screens.assessment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.SimilarityFlag
import com.learnmart.app.domain.model.SimilarityMatchResult
import com.learnmart.app.domain.repository.AssessmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SimilarityReviewUiState(
    val matches: List<SimilarityMatchResult> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SimilarityReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {
    private val assessmentId: String = savedStateHandle["assessmentId"] ?: ""
    private val _uiState = MutableStateFlow(SimilarityReviewUiState())
    val uiState: StateFlow<SimilarityReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val flagged = assessmentRepository.getFlaggedMatchesByAssessment(assessmentId)
            _uiState.value = SimilarityReviewUiState(matches = flagged, isLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarityReviewScreen(
    assessmentId: String,
    onNavigateBack: () -> Unit,
    viewModel: SimilarityReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Similarity Review") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (state.matches.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No flagged similarity matches", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("${state.matches.size} flagged match(es)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            items(state.matches, key = { it.id }) { match ->
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${"%.1f".format(match.similarityScore * 100)}% Similar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Surface(
                                color = when (match.flag) {
                                    SimilarityFlag.HIGH_SIMILARITY -> Color(0xFFD32F2F)
                                    SimilarityFlag.REVIEW_NEEDED -> Color(0xFFF57C00)
                                    SimilarityFlag.CLEAR -> Color(0xFF388E3C)
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(match.flag.name.replace("_", " "),
                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Submission 1: ...${match.submissionId1.takeLast(8)}", style = MaterialTheme.typography.bodySmall)
                        Text("Submission 2: ...${match.submissionId2.takeLast(8)}", style = MaterialTheme.typography.bodySmall)
                        Text("Detected: ${formatter.format(match.detectedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        match.reviewNotes?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
