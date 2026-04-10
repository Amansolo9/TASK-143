package com.learnmart.app.ui.screens.operations

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.BackupArchive
import com.learnmart.app.domain.model.BackupStatus
import com.learnmart.app.domain.model.RestoreRun
import com.learnmart.app.domain.usecase.operations.BackupRestoreUseCase
import com.learnmart.app.util.AppResult
import com.learnmart.app.worker.WorkScheduler
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

data class BackupRestoreUiState(
    val backupArchives: List<BackupArchive> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingBackup: Boolean = false,
    val isExporting: Boolean = false,
    val restoringArchiveId: String? = null,
    val lastRestoreRun: RestoreRun? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val archivesResult = backupRestoreUseCase.getAllBackups()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, backupArchives = archivesResult.data) }
                }
                is AppResult.PermissionError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Permission denied: ${archivesResult.code}") }
                }
                is AppResult.ValidationError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = archivesResult.globalErrors.firstOrNull() ?: "Validation error") }
                }
                is AppResult.SystemError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "System error: ${archivesResult.message}") }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Not found: ${archivesResult.code}") }
                }
                is AppResult.ConflictError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = archivesResult.message) }
                }
            }
        }
    }

    /**
     * Enqueue backup creation via WorkManager (idle+charging constrained).
     * The heavy crypto/IO runs in BackupWorker, not inline.
     */
    fun enqueueBackupCreation() {
        _uiState.update { it.copy(isCreatingBackup = true, errorMessage = null, successMessage = null) }
        workScheduler.enqueueBackupJob()
        _uiState.update {
            it.copy(
                isCreatingBackup = false,
                successMessage = "Backup job enqueued. It will run when the device is idle and charging."
            )
        }
    }

    /**
     * Export a backup archive to a SAF-provided OutputStream (scoped storage).
     */
    fun exportBackupToUri(archiveId: String, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null, successMessage = null) }
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _uiState.update { it.copy(isExporting = false, errorMessage = "Could not open output location") }
                    return@launch
                }
                outputStream.use { os ->
                    when (val result = backupRestoreUseCase.exportBackupToStream(archiveId, os)) {
                        is AppResult.Success -> {
                            _uiState.update { it.copy(isExporting = false, successMessage = "Backup exported successfully to selected location") }
                        }
                        is AppResult.PermissionError -> {
                            _uiState.update { it.copy(isExporting = false, errorMessage = "Permission denied: ${result.code}") }
                        }
                        is AppResult.ValidationError -> {
                            _uiState.update { it.copy(isExporting = false, errorMessage = result.globalErrors.firstOrNull() ?: "Export validation error") }
                        }
                        is AppResult.NotFoundError -> {
                            _uiState.update { it.copy(isExporting = false, errorMessage = "Archive not found") }
                        }
                        is AppResult.SystemError -> {
                            _uiState.update { it.copy(isExporting = false, errorMessage = "Export failed: ${result.message}") }
                        }
                        is AppResult.ConflictError -> {
                            _uiState.update { it.copy(isExporting = false, errorMessage = result.message) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    /**
     * Restore from a SAF-provided InputStream (scoped storage import).
     */
    fun restoreFromUri(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(restoringArchiveId = "saf_import", errorMessage = null, successMessage = null) }
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { it.copy(restoringArchiveId = null, errorMessage = "Could not read the selected file") }
                    return@launch
                }
                inputStream.use { ins ->
                    when (val result = backupRestoreUseCase.restoreFromStream(ins)) {
                        is AppResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    restoringArchiveId = null,
                                    lastRestoreRun = result.data,
                                    successMessage = "Restore completed. Restart app to apply."
                                )
                            }
                        }
                        is AppResult.ValidationError -> {
                            _uiState.update {
                                it.copy(
                                    restoringArchiveId = null,
                                    errorMessage = result.globalErrors.firstOrNull() ?: "Restore validation error"
                                )
                            }
                        }
                        is AppResult.PermissionError -> {
                            _uiState.update { it.copy(restoringArchiveId = null, errorMessage = "Permission denied: ${result.code}") }
                        }
                        is AppResult.SystemError -> {
                            _uiState.update { it.copy(restoringArchiveId = null, errorMessage = "Restore failed: ${result.message}") }
                        }
                        is AppResult.NotFoundError -> {
                            _uiState.update { it.copy(restoringArchiveId = null, errorMessage = "Not found: ${result.code}") }
                        }
                        is AppResult.ConflictError -> {
                            _uiState.update { it.copy(restoringArchiveId = null, errorMessage = result.message) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(restoringArchiveId = null, errorMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    /**
     * Restore from an existing on-device backup archive (internal path).
     */
    fun restoreBackup(archiveId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(restoringArchiveId = archiveId, errorMessage = null, successMessage = null) }
            when (val result = backupRestoreUseCase.restoreFromBackup(archiveId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            restoringArchiveId = null,
                            lastRestoreRun = result.data,
                            successMessage = "Restore completed successfully"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(restoringArchiveId = null, errorMessage = "Permission denied: ${result.code}")
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            restoringArchiveId = null,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(restoringArchiveId = null, errorMessage = result.message)
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(restoringArchiveId = null, errorMessage = "Archive not found")
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(restoringArchiveId = null, errorMessage = "Restore failed: ${result.message}")
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

private val backupDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF launcher for importing/restoring a backup archive from scoped storage
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreFromUri(it, context) }
    }

    // Export launcher state: which archive to export
    val exportArchiveIdState = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val archiveId = exportArchiveIdState.value
        if (uri != null && archiveId != null) {
            viewModel.exportBackupToUri(archiveId, uri, context)
        }
        exportArchiveIdState.value = null
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Create Backup button (WorkManager-driven)
            Button(
                onClick = viewModel::enqueueBackupCreation,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCreatingBackup
            ) {
                if (state.isCreatingBackup) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create Backup")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SAF-based restore from external file (scoped storage)
            OutlinedButton(
                onClick = {
                    restoreFileLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.restoringArchiveId == null
            ) {
                Text("Import & Restore from File")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Last restore info
            if (state.lastRestoreRun != null) {
                val restore = state.lastRestoreRun!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Last Restore",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: ${restore.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Started: ${backupDateFormatter.format(restore.startedAt)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (restore.completedAt != null) {
                            Text(
                                text = "Completed: ${backupDateFormatter.format(restore.completedAt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (restore.errorMessage != null) {
                            Text(
                                text = "Error: ${restore.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Backup list header
            Text(
                text = "Backup Archives",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.backupArchives.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No backup archives found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.backupArchives, key = { it.id }) { archive ->
                        BackupArchiveDetailCard(
                            archive = archive,
                            isRestoring = state.restoringArchiveId == archive.id,
                            isExporting = state.isExporting,
                            onRestore = { viewModel.restoreBackup(archive.id) },
                            onExport = {
                                exportArchiveIdState.value = archive.id
                                exportFileLauncher.launch("learnmart_backup_${archive.id.take(8)}.enc")
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BackupArchiveDetailCard(
    archive: BackupArchive,
    isRestoring: Boolean,
    isExporting: Boolean,
    onRestore: () -> Unit,
    onExport: () -> Unit
) {
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
                    text = "Backup ${archive.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BackupArchiveStatusChip(status = archive.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            BackupInfoRow(label = "App Version", value = archive.appVersion)
            BackupInfoRow(label = "Schema Version", value = archive.schemaVersion.toString())
            BackupInfoRow(label = "Encryption", value = archive.encryptionMethod)
            BackupInfoRow(label = "Created", value = backupDateFormatter.format(archive.createdAt))
            BackupInfoRow(label = "Created By", value = archive.createdBy)

            if (archive.fileSizeBytes != null && archive.fileSizeBytes > 0) {
                BackupInfoRow(label = "Size", value = "${archive.fileSizeBytes} bytes")
            }

            if (archive.status == BackupStatus.SUCCEEDED || archive.status == BackupStatus.VERIFIED) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // SAF-based export to scoped storage
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting
                ) {
                    Text("Export to File")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Restore from this Backup")
                }
            }
        }
    }
}

@Composable
private fun BackupInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BackupArchiveStatusChip(status: BackupStatus) {
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
