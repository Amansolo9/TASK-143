package com.learnmart.app.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.usecase.course.ManageCourseUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class CreateCourseUiState(
    val title: String = "",
    val description: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isCreated: Boolean = false
)

@HiltViewModel
class CreateCourseViewModel @Inject constructor(
    private val manageCourseUseCase: ManageCourseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCourseUiState())
    val uiState: StateFlow<CreateCourseUiState> = _uiState.asStateFlow()

    fun onTitleChanged(value: String) {
        _uiState.update {
            it.copy(
                title = value,
                fieldErrors = it.fieldErrors - "title"
            )
        }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                code = value,
                fieldErrors = it.fieldErrors - "code"
            )
        }
    }

    fun createCourse() {
        val state = _uiState.value

        // Local validation
        val localErrors = mutableMapOf<String, String>()
        if (state.title.isBlank()) {
            localErrors["title"] = "Title is required"
        }
        if (state.code.isBlank()) {
            localErrors["code"] = "Code is required"
        }

        if (localErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = localErrors) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, fieldErrors = emptyMap())
            }

            when (val result = manageCourseUseCase.createCourse(
                title = state.title.trim(),
                description = state.description.trim(),
                code = state.code.trim()
            )) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, isCreated = true)
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fieldErrors = result.fieldErrors,
                            errorMessage = result.globalErrors.firstOrNull()
                        )
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "You do not have permission to create courses"
                        )
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Required resource not found")
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
fun CreateCourseScreen(
    onCourseCreated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateCourseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) {
            onCourseCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Course") },
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Global error message
                if (uiState.errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Title field
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChanged,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("title"),
                    supportingText = uiState.fieldErrors["title"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading
                )

                // Code field
                OutlinedTextField(
                    value = uiState.code,
                    onValueChange = viewModel::onCodeChanged,
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("code"),
                    supportingText = uiState.fieldErrors["code"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading
                )

                // Description field
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    enabled = !uiState.isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = viewModel::createCourse,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Create Course")
                    }
                }
            }
        }
    }
}
