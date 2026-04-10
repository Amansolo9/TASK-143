package com.learnmart.app.ui.screens.operations

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.BackupArchive
import com.learnmart.app.domain.model.BackupStatus
import com.learnmart.app.domain.model.DiscrepancyCase
import com.learnmart.app.domain.model.DiscrepancyCaseStatus
import com.learnmart.app.domain.model.ExportJob
import com.learnmart.app.domain.model.ImportJob
import com.learnmart.app.domain.model.ImportJobStatus
import com.learnmart.app.domain.usecase.operations.ManageOperationsUseCase
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

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Error types surfaced to the operator, distinguishing permission denial,
 * empty data, validation issues, and unexpected failures.
 */
enum class OperationsErrorType {
    PERMISSION_DENIED,
    VALIDATION_ERROR,
    SYSTEM_FAILURE,
    NONE
}

data class OperationsUiState(
    val importJobs: List<ImportJob> = emptyList(),
    val discrepancyCases: List<DiscrepancyCase> = emptyList(),
    val backupArchives: List<BackupArchive> = emptyList(),
    val exportJobs: List<ExportJob> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val errorType: OperationsErrorType = OperationsErrorType.NONE
)

@HiltViewModel
class OperationsViewModel @Inject constructor(
    private val manageOperationsUseCase: ManageOperationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, errorType = OperationsErrorType.NONE) }
            try {
                val importsResult = manageOperationsUseCase.getImportJobs()
                val discrepanciesResult = manageOperationsUseCase.getDiscrepancyCases(limit = 100, offset = 0)
                val backupsResult = manageOperationsUseCase.getBackupArchives()
                val exportsResult = manageOperationsUseCase.getExportJobs(limit = 100, offset = 0)

                val imports = when (importsResult) {
                    is AppResult.Success -> importsResult.data
                    is AppResult.PermissionError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Permission denied: ${importsResult.code}", errorType = OperationsErrorType.PERMISSION_DENIED) }
                        return@launch
                    }
                    is AppResult.ValidationError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = importsResult.globalErrors.firstOrNull() ?: "Validation error loading imports", errorType = OperationsErrorType.VALIDATION_ERROR) }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "System error: ${importsResult.message}", errorType = OperationsErrorType.SYSTEM_FAILURE) }
                        return@launch
                    }
                    else -> emptyList()
                }
                val discrepancies = when (discrepanciesResult) {
                    is AppResult.Success -> discrepanciesResult.data
                    is AppResult.PermissionError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Permission denied: ${discrepanciesResult.code}", errorType = OperationsErrorType.PERMISSION_DENIED) }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "System error: ${discrepanciesResult.message}", errorType = OperationsErrorType.SYSTEM_FAILURE) }
                        return@launch
                    }
                    else -> emptyList()
                }
                val backups = when (backupsResult) {
                    is AppResult.Success -> backupsResult.data
                    is AppResult.PermissionError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Permission denied: ${backupsResult.code}", errorType = OperationsErrorType.PERMISSION_DENIED) }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "System error: ${backupsResult.message}", errorType = OperationsErrorType.SYSTEM_FAILURE) }
                        return@launch
                    }
                    else -> emptyList()
                }
                val exports = when (exportsResult) {
                    is AppResult.Success -> exportsResult.data
                    is AppResult.PermissionError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Permission denied: ${exportsResult.code}", errorType = OperationsErrorType.PERMISSION_DENIED) }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "System error: ${exportsResult.message}", errorType = OperationsErrorType.SYSTEM_FAILURE) }
                        return@launch
                    }
                    else -> emptyList()
                }

                _uiState.update {
                    it.copy(
                        importJobs = imports,
                        discrepancyCases = discrepancies,
                        backupArchives = backups,
                        exportJobs = exports,
                        isLoading = false,
                        errorType = OperationsErrorType.NONE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unexpected system failure loading operations data",
                        errorType = OperationsErrorType.SYSTEM_FAILURE
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

private val operationsDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

private val tabTitles = listOf("Imports", "Discrepancies", "Backups", "Exports")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToReconciliation: (String) -> Unit,
    onNavigateToBackupDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: OperationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operations") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
            } else if (state.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                when (selectedTabIndex) {
                    0 -> ImportsTab(
                        importJobs = state.importJobs,
                        onNavigateToImport = onNavigateToImport,
                        onNavigateToReconciliation = onNavigateToReconciliation
                    )
                    1 -> DiscrepanciesTab(discrepancyCases = state.discrepancyCases)
                    2 -> BackupsTab(
                        backupArchives = state.backupArchives,
                        onNavigateToBackupDetail = onNavigateToBackupDetail
                    )
                    3 -> ExportsTab(exportJobs = state.exportJobs)
                }
            }
        }
    }
}

@Composable
private fun ImportsTab(
    importJobs: List<ImportJob>,
    onNavigateToImport: () -> Unit,
    onNavigateToReconciliation: (String) -> Unit
) {
    if (importJobs.isEmpty()) {
        EmptyStateMessage(message = "No import jobs found")
    } else {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(importJobs, key = { it.id }) { job ->
                ImportJobCard(
                    job = job,
                    onClick = { onNavigateToImport() }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ImportJobCard(
    job: ImportJob,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = job.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                ImportStatusChip(status = job.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type: ${job.fileType.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = operationsDateFormatter.format(job.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rows: ${job.totalRows} total, ${job.validRows} valid, ${job.errorRows} errors",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportStatusChip(status: ImportJobStatus) {
    val (label, containerColor, contentColor) = when (status) {
        ImportJobStatus.CREATED -> Triple(
            "Created",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ImportJobStatus.VALIDATING -> Triple(
            "Validating",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ImportJobStatus.REJECTED -> Triple(
            "Rejected",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        ImportJobStatus.READY_TO_APPLY -> Triple(
            "Ready",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ImportJobStatus.APPLYING -> Triple(
            "Applying",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        ImportJobStatus.APPLIED -> Triple(
            "Applied",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        ImportJobStatus.FAILED -> Triple(
            "Failed",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        ImportJobStatus.RETRYABLE -> Triple(
            "Retryable",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
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
private fun DiscrepanciesTab(discrepancyCases: List<DiscrepancyCase>) {
    if (discrepancyCases.isEmpty()) {
        EmptyStateMessage(message = "No discrepancy cases found")
    } else {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(discrepancyCases, key = { it.id }) { case_ ->
                DiscrepancyCaseCard(case_ = case_)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DiscrepancyCaseCard(case_: DiscrepancyCase) {
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
                DiscrepancyStatusChip(status = case_.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = case_.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Created: ${operationsDateFormatter.format(case_.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscrepancyStatusChip(status: DiscrepancyCaseStatus) {
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

@Composable
private fun BackupsTab(
    backupArchives: List<BackupArchive>,
    onNavigateToBackupDetail: (String) -> Unit
) {
    if (backupArchives.isEmpty()) {
        EmptyStateMessage(message = "No backup archives found")
    } else {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(backupArchives, key = { it.id }) { archive ->
                BackupArchiveCard(
                    archive = archive,
                    onClick = { onNavigateToBackupDetail(archive.id) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun BackupArchiveCard(
    archive: BackupArchive,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = "Backup ${archive.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BackupStatusChip(status = archive.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "v${archive.appVersion} | Schema ${archive.schemaVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = operationsDateFormatter.format(archive.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Encryption: ${archive.encryptionMethod}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BackupStatusChip(status: BackupStatus) {
    val (label, containerColor, contentColor) = when (status) {
        BackupStatus.REQUESTED -> Triple(
            "Requested",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        BackupStatus.RUNNING -> Triple(
            "Running",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        BackupStatus.SUCCEEDED -> Triple(
            "Succeeded",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        BackupStatus.FAILED -> Triple(
            "Failed",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        BackupStatus.RETRYABLE -> Triple(
            "Retryable",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        BackupStatus.VERIFIED -> Triple(
            "Verified",
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
private fun ExportsTab(exportJobs: List<ExportJob>) {
    if (exportJobs.isEmpty()) {
        EmptyStateMessage(message = "No export jobs found")
    } else {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(exportJobs, key = { it.id }) { job ->
                ExportJobCard(job = job)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ExportJobCard(job: ExportJob) {
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
                    text = job.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) {
                    Text(
                        text = job.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type: ${job.exportType} | Format: ${job.format.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = operationsDateFormatter.format(job.exportedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Records: ${job.recordCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
