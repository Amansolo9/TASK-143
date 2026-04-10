package com.learnmart.app.ui.screens.operations

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.learnmart.app.domain.model.ImportJob
import com.learnmart.app.domain.model.ImportJobStatus
import com.learnmart.app.domain.usecase.operations.ImportSettlementUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class ImportUiState(
    val selectedFileName: String = "",
    val fileType: String = "csv",
    val fileContentBytes: ByteArray? = null,
    val signatureHex: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val resultJob: ImportJob? = null,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importSettlementUseCase: ImportSettlementUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri, context: Context) {
        val contentBytes = context.contentResolver.openInputStream(uri)?.readBytes()
        if (contentBytes == null) {
            _uiState.update { it.copy(errorMessage = "Could not read the selected file") }
            return
        }

        val fileName = resolveFileName(uri, context)
        val fileType = when {
            fileName.endsWith(".csv", ignoreCase = true) -> "csv"
            fileName.endsWith(".json", ignoreCase = true) -> "json"
            else -> "csv"
        }

        _uiState.update {
            it.copy(
                selectedFileName = fileName,
                fileType = fileType,
                fileContentBytes = contentBytes,
                errorMessage = null
            )
        }
    }

    fun updateSignatureHex(value: String) {
        _uiState.update { it.copy(signatureHex = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startImport() {
        val current = _uiState.value
        if (current.selectedFileName.isBlank() || current.fileContentBytes == null) {
            _uiState.update { it.copy(errorMessage = "Please select a file first") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val contentString = current.fileContentBytes.decodeToString()
            val rawRows = parseContent(contentString, current.fileType)
            if (rawRows == null) {
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = "Could not parse file content. Ensure the file is valid ${current.fileType.uppercase()}.")
                }
                return@launch
            }

            val fileSizeBytes = current.fileContentBytes.size.toLong()
            when (val result = importSettlementUseCase.importFile(
                fileName = current.selectedFileName,
                fileType = current.fileType,
                fileSizeBytes = fileSizeBytes,
                rawRows = rawRows,
                fileContent = current.fileContentBytes,
                signatureHex = current.signatureHex.ifBlank { null }
            )) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isSubmitted = true,
                            resultJob = result.data,
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
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Resource not found")
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

    fun resetForm() {
        _uiState.update { ImportUiState() }
    }

    private fun resolveFileName(uri: Uri, context: Context): String {
        var name = "unknown_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    private fun parseContent(content: String, fileType: String): List<Map<String, String>>? {
        return when (fileType) {
            "csv" -> parseCsv(content)
            "json" -> parseJson(content)
            else -> parseCsv(content)
        }
    }

    /**
     * RFC 4180-compliant CSV parser that handles:
     * - Quoted fields with embedded commas
     * - Escaped quotes (doubled "")
     * - Mixed quoted and unquoted fields
     * - Trimming of whitespace
     * - De-duplication by external_id
     */
    private fun parseCsv(csv: String): List<Map<String, String>>? {
        return try {
            val lines = csv.lines().filter { it.isNotBlank() }
            if (lines.size < 2) return null

            val headers = parseCsvLine(lines.first()).map { it.trim() }
            if (headers.isEmpty()) return null

            val rows = mutableListOf<Map<String, String>>()
            val seenIds = mutableSetOf<String>()

            for (line in lines.drop(1)) {
                val values = parseCsvLine(line)
                val map = mutableMapOf<String, String>()
                headers.forEachIndexed { index, header ->
                    if (index < values.size) {
                        map[header] = values[index].trim()
                    }
                }
                if (map.isEmpty()) continue

                // De-dup by external_id if present
                val externalId = map["external_id"]
                if (externalId != null && externalId.isNotBlank()) {
                    if (seenIds.contains(externalId)) continue
                    seenIds.add(externalId)
                }

                // Reject rows with invalid dates
                val dateStr = map["transaction_date"]
                if (dateStr != null && dateStr.isNotBlank()) {
                    if (!isValidDateFormat(dateStr)) {
                        continue // Skip row with invalid date
                    }
                }

                rows.add(map)
            }
            if (rows.isEmpty()) null else rows
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse a single CSV line respecting RFC 4180 quoting rules.
     * Handles: commas inside quotes, escaped quotes (""), mixed fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        // Check for escaped quote
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i += 2
                            continue
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(ch)
                    }
                }
                ch == '"' -> {
                    inQuotes = true
                }
                ch == ',' -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(ch)
                }
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    /**
     * Robust JSON parser supporting:
     * - JSON arrays of objects
     * - String, number, boolean, and null values
     * - Nested quotes/escapes
     * - De-duplication by external_id
     * - Invalid date rejection
     */
    private fun parseJson(json: String): List<Map<String, String>>? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[")) return null

            val result = mutableListOf<Map<String, String>>()
            val seenIds = mutableSetOf<String>()

            // Use a proper tokenized approach for JSON parsing
            val objects = extractJsonObjects(trimmed)

            for (obj in objects) {
                val map = parseJsonObject(obj)
                if (map.isEmpty()) continue

                // De-dup by external_id if present
                val externalId = map["external_id"]
                if (externalId != null && externalId.isNotBlank()) {
                    if (seenIds.contains(externalId)) continue
                    seenIds.add(externalId)
                }

                // Reject rows with invalid dates
                val dateStr = map["transaction_date"]
                if (dateStr != null && dateStr.isNotBlank()) {
                    if (!isValidDateFormat(dateStr)) {
                        continue
                    }
                }

                result.add(map)
            }
            if (result.isEmpty()) null else result
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonObjects(jsonArray: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var objStart = -1

        for (i in jsonArray.indices) {
            when (jsonArray[i]) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(jsonArray.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
        }
        return objects
    }

    private fun parseJsonObject(obj: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // Match key-value pairs: "key": "value" or "key": number or "key": true/false/null
        val kvPattern = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(-?\d+(?:\.\d+)?)|(\btrue\b|\bfalse\b|\bnull\b))""")
        for (match in kvPattern.findAll(obj)) {
            val key = unescapeJsonString(match.groupValues[1])
            val value = when {
                match.groupValues[2].isNotEmpty() -> unescapeJsonString(match.groupValues[2])
                match.groupValues[3].isNotEmpty() -> match.groupValues[3]
                match.groupValues[4].isNotEmpty() -> match.groupValues[4]
                else -> ""
            }
            if (value != "null") {
                map[key] = value
            }
        }
        return map
    }

    private fun unescapeJsonString(s: String): String {
        return s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }

    private fun isValidDateFormat(dateStr: String): Boolean {
        return try {
            java.time.Instant.parse(dateStr)
            true
        } catch (_: Exception) {
            try {
                java.time.LocalDate.parse(dateStr)
                true
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(dateStr)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFileSelected(it, context) }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Settlement File") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.isSubmitted && state.resultJob != null) {
                ImportResultContent(
                    job = state.resultJob!!,
                    warnings = state.warnings,
                    onReset = viewModel::resetForm,
                    onNavigateBack = onNavigateBack
                )
            } else {
                ImportFormContent(
                    state = state,
                    onSelectFile = {
                        filePickerLauncher.launch(arrayOf("text/csv", "application/json", "text/*"))
                    },
                    onSignatureHexChanged = viewModel::updateSignatureHex,
                    onSubmit = viewModel::startImport
                )
            }
        }
    }
}

@Composable
private fun ImportFormContent(
    state: ImportUiState,
    onSelectFile: () -> Unit,
    onSignatureHexChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Text(
        text = "Import Settlement Data",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onSelectFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Select File")
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (state.selectedFileName.isNotBlank()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Selected: ${state.selectedFileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Type: ${state.fileType.uppercase()} | Size: ${state.fileContentBytes?.size ?: 0} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = state.signatureHex,
        onValueChange = onSignatureHexChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Signature (Hex)") },
        placeholder = { Text("Optional HMAC-SHA256 signature hex") },
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Provide the HMAC-SHA256 signature hex if signature verification is enabled. Leave blank if not required.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isSubmitting && state.fileContentBytes != null
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("Import File")
        }
    }
}

@Composable
private fun ImportResultContent(
    job: ImportJob,
    warnings: List<String>,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
                text = "Import Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Divider()

            ResultRow(label = "File", value = job.fileName)
            ResultRow(label = "Type", value = job.fileType.uppercase())
            ResultRow(label = "Status", value = job.status.name)
            ResultRow(label = "Total Rows", value = job.totalRows.toString())
            ResultRow(label = "Valid Rows", value = job.validRows.toString())
            ResultRow(label = "Error Rows", value = job.errorRows.toString())

            if (job.errorDetails != null) {
                Divider()
                Text(
                    text = "Error Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = job.errorDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (warnings.isNotEmpty()) {
                Divider()
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

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onReset,
            modifier = Modifier.weight(1f)
        ) {
            Text("Import Another")
        }
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.weight(1f)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
