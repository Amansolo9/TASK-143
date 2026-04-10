package com.learnmart.app.ui.screens.admin.users

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.User
import com.learnmart.app.domain.model.UserStatus
import com.learnmart.app.domain.usecase.user.ManageUserUseCase
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
import androidx.hilt.navigation.compose.hiltViewModel

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class UserDetailUiState(
    val user: User? = null,
    val roles: List<RoleType> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class UserDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageUserUseCase: ManageUserUseCase
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"])

    private val _uiState = MutableStateFlow(UserDetailUiState())
    val uiState: StateFlow<UserDetailUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageUserUseCase.getUserById(userId)) {
                is AppResult.Success -> {
                    val roles = (manageUserUseCase.getRolesForUser(userId) as? AppResult.Success)?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            roles = roles,
                            isLoading = false
                        )
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "User not found")
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Validation error"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
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

    fun unlockUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageUserUseCase.unlockUser(userId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            isLoading = false,
                            actionMessage = "User unlocked successfully"
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot unlock user"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "User not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
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

    fun disableUser(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageUserUseCase.disableUser(userId, reason)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            isLoading = false,
                            actionMessage = "User disabled successfully"
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull() ?: "Cannot disable user"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "User not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
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

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserDetailScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    viewModel: UserDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisableDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionMessage()
        }
    }

    if (showDisableDialog) {
        DisableUserDialog(
            onConfirm = { reason ->
                showDisableDialog = false
                viewModel.disableUser(reason)
            },
            onDismiss = { showDisableDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Details") },
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
        when {
            uiState.isLoading && uiState.user == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.user == null && uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load user",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            uiState.user != null -> {
                val user = uiState.user!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // User info card
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
                                text = user.displayName,
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Divider()

                            DetailRow(label = "Username", value = user.username)
                            DetailRow(label = "Status", value = null) {
                                UserDetailStatusChip(status = user.status)
                            }
                            DetailRow(
                                label = "Credential Type",
                                value = user.credentialType.name
                            )
                            DetailRow(
                                label = "Last Login",
                                value = user.lastLoginAt?.let { dateTimeFormatter.format(it) }
                                    ?: "Never"
                            )
                            DetailRow(
                                label = "Created At",
                                value = dateTimeFormatter.format(user.createdAt)
                            )
                        }
                    }

                    // Roles card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Roles",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (uiState.roles.isEmpty()) {
                                Text(
                                    text = "No roles assigned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.roles.forEach { role ->
                                        RoleChip(roleType = role)
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
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

                            if (user.status == UserStatus.LOCKED) {
                                Button(
                                    onClick = { viewModel.unlockUser() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isLoading
                                ) {
                                    Text("Unlock User")
                                }
                            }

                            if (user.status != UserStatus.DISABLED) {
                                OutlinedButton(
                                    onClick = { showDisableDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isLoading,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Disable User")
                                }
                            }

                            if (user.status == UserStatus.DISABLED) {
                                Text(
                                    text = "This user account is disabled.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
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
private fun UserDetailStatusChip(status: UserStatus) {
    val (label, containerColor, contentColor) = when (status) {
        UserStatus.ACTIVE -> Triple(
            "Active",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        UserStatus.LOCKED -> Triple(
            "Locked",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        UserStatus.DISABLED -> Triple(
            "Disabled",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        UserStatus.ARCHIVED -> Triple(
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
private fun RoleChip(roleType: RoleType) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = formatRoleTypeLabel(roleType),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun formatRoleTypeLabel(role: RoleType): String = when (role) {
    RoleType.ADMINISTRATOR -> "Administrator"
    RoleType.REGISTRAR -> "Registrar"
    RoleType.INSTRUCTOR -> "Instructor"
    RoleType.TEACHING_ASSISTANT -> "Teaching Assistant"
    RoleType.LEARNER -> "Learner"
    RoleType.FINANCE_CLERK -> "Finance Clerk"
}

@Composable
private fun DisableUserDialog(
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to disable this user? They will no longer be able to log in.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text(
                    text = "Disable",
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
