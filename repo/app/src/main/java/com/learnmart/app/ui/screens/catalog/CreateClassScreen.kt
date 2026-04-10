package com.learnmart.app.ui.screens.catalog

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.ClassOffering
import com.learnmart.app.domain.model.ClassSession
import com.learnmart.app.domain.model.ClassStaffAssignment
import com.learnmart.app.domain.model.StaffRole
import com.learnmart.app.domain.usecase.course.CreateClassOfferingRequest
import com.learnmart.app.domain.usecase.course.CreateClassSessionRequest
import com.learnmart.app.domain.usecase.course.ManageClassUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// Session and Staff form entries
// ──────────────────────────────────────────────────────────────────────────────

data class SessionFormEntry(
    val sessionOrder: String = "",
    val title: String = "",
    val sessionTime: String = "",
    val durationMinutes: String = "",
    val location: String = "",
    val notes: String = ""
)

data class StaffFormEntry(
    val userId: String = "",
    val staffRole: StaffRole = StaffRole.INSTRUCTOR
)

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class CreateClassUiState(
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val hardCapacity: String = "",
    val scheduleStart: String = "",
    val scheduleEnd: String = "",
    val location: String = "",
    val waitlistEnabled: Boolean = false,
    val sessionEntries: List<SessionFormEntry> = emptyList(),
    val staffEntries: List<StaffFormEntry> = emptyList(),
    val createdOffering: ClassOffering? = null,
    val createdSessions: List<ClassSession> = emptyList(),
    val createdStaff: List<ClassStaffAssignment> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isOfferingCreated: Boolean = false,
    val successMessage: String? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class CreateClassViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageClassUseCase: ManageClassUseCase
) : ViewModel() {

    private val courseId: String = checkNotNull(savedStateHandle["courseId"])

    private val _uiState = MutableStateFlow(CreateClassUiState(courseId = courseId))
    val uiState: StateFlow<CreateClassUiState> = _uiState.asStateFlow()

    fun onTitleChanged(value: String) {
        _uiState.update {
            it.copy(title = value, fieldErrors = it.fieldErrors - "title")
        }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onHardCapacityChanged(value: String) {
        _uiState.update {
            it.copy(hardCapacity = value, fieldErrors = it.fieldErrors - "hardCapacity")
        }
    }

    fun onScheduleStartChanged(value: String) {
        _uiState.update {
            it.copy(scheduleStart = value, fieldErrors = it.fieldErrors - "scheduleStart")
        }
    }

    fun onScheduleEndChanged(value: String) {
        _uiState.update {
            it.copy(scheduleEnd = value, fieldErrors = it.fieldErrors - "scheduleEnd")
        }
    }

    fun onLocationChanged(value: String) {
        _uiState.update { it.copy(location = value) }
    }

    fun onWaitlistEnabledChanged(value: Boolean) {
        _uiState.update { it.copy(waitlistEnabled = value) }
    }

    // Session management
    fun addSessionEntry() {
        _uiState.update {
            val nextOrder = (it.sessionEntries.size + 1).toString()
            it.copy(
                sessionEntries = it.sessionEntries + SessionFormEntry(sessionOrder = nextOrder)
            )
        }
    }

    fun removeSessionEntry(index: Int) {
        _uiState.update {
            it.copy(sessionEntries = it.sessionEntries.toMutableList().apply { removeAt(index) })
        }
    }

    fun updateSessionEntry(index: Int, entry: SessionFormEntry) {
        _uiState.update {
            it.copy(
                sessionEntries = it.sessionEntries.toMutableList().apply { set(index, entry) }
            )
        }
    }

    // Staff management
    fun addStaffEntry() {
        _uiState.update {
            it.copy(staffEntries = it.staffEntries + StaffFormEntry())
        }
    }

    fun removeStaffEntry(index: Int) {
        _uiState.update {
            it.copy(staffEntries = it.staffEntries.toMutableList().apply { removeAt(index) })
        }
    }

    fun updateStaffEntry(index: Int, entry: StaffFormEntry) {
        _uiState.update {
            it.copy(
                staffEntries = it.staffEntries.toMutableList().apply { set(index, entry) }
            )
        }
    }

    fun createClassOffering() {
        val state = _uiState.value

        // Local validation
        val localErrors = mutableMapOf<String, String>()
        if (state.title.isBlank()) localErrors["title"] = "Title is required"
        val capacity = state.hardCapacity.toIntOrNull()
        if (capacity == null || capacity < 1) localErrors["hardCapacity"] = "Capacity must be a number >= 1"
        val startInstant = parseInstantSafe(state.scheduleStart)
        if (startInstant == null) localErrors["scheduleStart"] = "Invalid ISO datetime (e.g., 2026-06-01T09:00:00Z)"
        val endInstant = parseInstantSafe(state.scheduleEnd)
        if (endInstant == null) localErrors["scheduleEnd"] = "Invalid ISO datetime (e.g., 2026-06-30T17:00:00Z)"
        if (startInstant != null && endInstant != null && endInstant.isBefore(startInstant)) {
            localErrors["scheduleEnd"] = "End must be after start"
        }

        if (localErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = localErrors) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, fieldErrors = emptyMap())
            }

            val request = CreateClassOfferingRequest(
                courseId = courseId,
                title = state.title.trim(),
                description = state.description.trim(),
                hardCapacity = capacity!!,
                waitlistEnabled = state.waitlistEnabled,
                scheduleStart = startInstant!!,
                scheduleEnd = endInstant!!,
                location = state.location.trim()
            )

            when (val result = manageClassUseCase.createClassOffering(request)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            createdOffering = result.data,
                            isOfferingCreated = true,
                            successMessage = "Class offering created. You can now add sessions and staff."
                        )
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
                            errorMessage = "You do not have permission to create classes"
                        )
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Course not found")
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

    fun addAllSessionsAndStaff() {
        val state = _uiState.value
        val offeringId = state.createdOffering?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Add sessions
            val createdSessions = mutableListOf<ClassSession>()
            for (entry in state.sessionEntries) {
                val order = entry.sessionOrder.toIntOrNull() ?: continue
                val time = parseInstantSafe(entry.sessionTime) ?: continue
                val duration = entry.durationMinutes.toIntOrNull() ?: continue
                if (entry.title.isBlank()) continue

                val request = CreateClassSessionRequest(
                    classOfferingId = offeringId,
                    sessionOrder = order,
                    title = entry.title.trim(),
                    sessionTime = time,
                    durationMinutes = duration,
                    location = entry.location.ifBlank { null },
                    notes = entry.notes.ifBlank { null }
                )

                when (val sessionResult = manageClassUseCase.addSession(request)) {
                    is AppResult.Success -> {
                        createdSessions.add(sessionResult.data)
                    }
                    is AppResult.ValidationError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Session '${entry.title}': ${
                                    sessionResult.fieldErrors.values.firstOrNull()
                                        ?: sessionResult.globalErrors.firstOrNull()
                                        ?: "Validation error"
                                }"
                            )
                        }
                        return@launch
                    }
                    is AppResult.ConflictError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Session '${entry.title}': ${sessionResult.message}"
                            )
                        }
                        return@launch
                    }
                    is AppResult.PermissionError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Permission denied")
                        }
                        return@launch
                    }
                    is AppResult.NotFoundError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Class not found")
                        }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = sessionResult.message)
                        }
                        return@launch
                    }
                }
            }

            // Add staff
            val createdStaffList = mutableListOf<ClassStaffAssignment>()
            for (entry in state.staffEntries) {
                if (entry.userId.isBlank()) continue

                when (val staffResult = manageClassUseCase.assignStaff(
                    classOfferingId = offeringId,
                    userId = entry.userId.trim(),
                    staffRole = entry.staffRole
                )) {
                    is AppResult.Success -> {
                        createdStaffList.add(staffResult.data)
                    }
                    is AppResult.ValidationError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Staff '${entry.userId}': ${
                                    staffResult.fieldErrors.values.firstOrNull()
                                        ?: staffResult.globalErrors.firstOrNull()
                                        ?: "Validation error"
                                }"
                            )
                        }
                        return@launch
                    }
                    is AppResult.ConflictError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Staff '${entry.userId}': ${staffResult.message}"
                            )
                        }
                        return@launch
                    }
                    is AppResult.PermissionError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Permission denied for staff assignment")
                        }
                        return@launch
                    }
                    is AppResult.NotFoundError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Class not found for staff assignment")
                        }
                        return@launch
                    }
                    is AppResult.SystemError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = staffResult.message)
                        }
                        return@launch
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    createdSessions = createdSessions,
                    createdStaff = createdStaffList,
                    isComplete = true,
                    successMessage = "Class offering setup complete with ${createdSessions.size} session(s) and ${createdStaffList.size} staff member(s)."
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun parseInstantSafe(value: String): Instant? {
        return try {
            Instant.parse(value.trim())
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassScreen(
    courseId: String,
    onNavigateBack: () -> Unit,
    viewModel: CreateClassViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Class Offering") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

                if (!uiState.isOfferingCreated) {
                    // ── Class Offering Form ──
                    Text(
                        text = "Class Offering Details",
                        style = MaterialTheme.typography.titleMedium
                    )

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

                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChanged,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        enabled = !uiState.isLoading
                    )

                    OutlinedTextField(
                        value = uiState.hardCapacity,
                        onValueChange = viewModel::onHardCapacityChanged,
                        label = { Text("Hard Capacity") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.fieldErrors.containsKey("hardCapacity"),
                        supportingText = uiState.fieldErrors["hardCapacity"]?.let { error ->
                            { Text(text = error, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !uiState.isLoading
                    )

                    OutlinedTextField(
                        value = uiState.scheduleStart,
                        onValueChange = viewModel::onScheduleStartChanged,
                        label = { Text("Schedule Start (ISO datetime)") },
                        placeholder = { Text("2026-06-01T09:00:00Z") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.fieldErrors.containsKey("scheduleStart"),
                        supportingText = uiState.fieldErrors["scheduleStart"]?.let { error ->
                            { Text(text = error, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )

                    OutlinedTextField(
                        value = uiState.scheduleEnd,
                        onValueChange = viewModel::onScheduleEndChanged,
                        label = { Text("Schedule End (ISO datetime)") },
                        placeholder = { Text("2026-06-30T17:00:00Z") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.fieldErrors.containsKey("scheduleEnd"),
                        supportingText = uiState.fieldErrors["scheduleEnd"]?.let { error ->
                            { Text(text = error, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )

                    OutlinedTextField(
                        value = uiState.location,
                        onValueChange = viewModel::onLocationChanged,
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Waitlist Enabled",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = uiState.waitlistEnabled,
                            onCheckedChange = viewModel::onWaitlistEnabledChanged,
                            enabled = !uiState.isLoading
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = viewModel::createClassOffering,
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
                            Text("Create Class Offering")
                        }
                    }
                } else {
                    // ── Post-creation: Add Sessions & Staff ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Class offering created: ${uiState.createdOffering?.title ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "ID: ${uiState.createdOffering?.id ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Divider()

                    // Sessions section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sessions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = viewModel::addSessionEntry) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Session"
                            )
                        }
                    }

                    uiState.sessionEntries.forEachIndexed { index, entry ->
                        SessionFormCard(
                            index = index,
                            entry = entry,
                            onUpdate = { viewModel.updateSessionEntry(index, it) },
                            onRemove = { viewModel.removeSessionEntry(index) },
                            enabled = !uiState.isLoading
                        )
                    }

                    if (uiState.sessionEntries.isEmpty()) {
                        Text(
                            text = "No sessions added yet. Tap + to add a session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Divider()

                    // Staff section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Staff Assignments",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = viewModel::addStaffEntry) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Staff"
                            )
                        }
                    }

                    uiState.staffEntries.forEachIndexed { index, entry ->
                        StaffFormCard(
                            index = index,
                            entry = entry,
                            onUpdate = { viewModel.updateStaffEntry(index, it) },
                            onRemove = { viewModel.removeStaffEntry(index) },
                            enabled = !uiState.isLoading
                        )
                    }

                    if (uiState.staffEntries.isEmpty()) {
                        Text(
                            text = "No staff assigned yet. Tap + to add a staff member.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = viewModel::addAllSessionsAndStaff,
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
                            Text("Save Sessions & Staff")
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Skip & Finish")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionFormCard(
    index: Int,
    entry: SessionFormEntry,
    onUpdate: (SessionFormEntry) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session ${index + 1}",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove Session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = entry.sessionOrder,
                    onValueChange = { onUpdate(entry.copy(sessionOrder = it)) },
                    label = { Text("Order") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled
                )
                OutlinedTextField(
                    value = entry.title,
                    onValueChange = { onUpdate(entry.copy(title = it)) },
                    label = { Text("Title") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled
                )
            }

            OutlinedTextField(
                value = entry.sessionTime,
                onValueChange = { onUpdate(entry.copy(sessionTime = it)) },
                label = { Text("Session Time (ISO datetime)") },
                placeholder = { Text("2026-06-01T09:00:00Z") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = entry.durationMinutes,
                    onValueChange = { onUpdate(entry.copy(durationMinutes = it)) },
                    label = { Text("Duration (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled
                )
                OutlinedTextField(
                    value = entry.location,
                    onValueChange = { onUpdate(entry.copy(location = it)) },
                    label = { Text("Location") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled
                )
            }

            OutlinedTextField(
                value = entry.notes,
                onValueChange = { onUpdate(entry.copy(notes = it)) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun StaffFormCard(
    index: Int,
    entry: StaffFormEntry,
    onUpdate: (StaffFormEntry) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Staff ${index + 1}",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove Staff",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = entry.userId,
                onValueChange = { onUpdate(entry.copy(userId = it)) },
                label = { Text("User ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StaffRole.entries.forEach { role ->
                    OutlinedButton(
                        onClick = { onUpdate(entry.copy(staffRole = role)) },
                        enabled = enabled,
                        colors = if (entry.staffRole == role) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(
                            text = when (role) {
                                StaffRole.INSTRUCTOR -> "Instructor"
                                StaffRole.TEACHING_ASSISTANT -> "TA"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
