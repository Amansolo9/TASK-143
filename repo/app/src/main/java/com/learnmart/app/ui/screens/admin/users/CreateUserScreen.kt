package com.learnmart.app.ui.screens.admin.users

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.learnmart.app.domain.model.CredentialType
import com.learnmart.app.domain.model.RoleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserScreen(
    onUserCreated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateUserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) {
            onUserCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create User") },
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

                // Username field
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("username"),
                    supportingText = uiState.fieldErrors["username"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading
                )

                // Display name field
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("displayName"),
                    supportingText = uiState.fieldErrors["displayName"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading
                )

                // Credential type dropdown
                CredentialTypeDropdown(
                    selectedType = uiState.selectedCredentialType,
                    onTypeSelected = viewModel::onCredentialTypeChanged,
                    enabled = !uiState.isLoading
                )

                // Credential field
                OutlinedTextField(
                    value = uiState.credential,
                    onValueChange = viewModel::onCredentialChanged,
                    label = {
                        Text(
                            when (uiState.selectedCredentialType) {
                                CredentialType.PIN -> "PIN"
                                CredentialType.PASSWORD -> "Password"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("credential"),
                    supportingText = uiState.fieldErrors["credential"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !uiState.isLoading
                )

                // Confirm credential field
                OutlinedTextField(
                    value = uiState.confirmCredential,
                    onValueChange = viewModel::onConfirmCredentialChanged,
                    label = {
                        Text(
                            when (uiState.selectedCredentialType) {
                                CredentialType.PIN -> "Confirm PIN"
                                CredentialType.PASSWORD -> "Confirm Password"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.fieldErrors.containsKey("confirmCredential"),
                    supportingText = uiState.fieldErrors["confirmCredential"]?.let { error ->
                        { Text(text = error, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !uiState.isLoading
                )

                // Role dropdown
                RoleDropdown(
                    selectedRole = uiState.selectedRole,
                    onRoleSelected = viewModel::onRoleChanged,
                    enabled = !uiState.isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = viewModel::createUser,
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
                        Text("Create User")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialTypeDropdown(
    selectedType: CredentialType,
    onTypeSelected: (CredentialType) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = when (selectedType) {
                CredentialType.PIN -> "PIN"
                CredentialType.PASSWORD -> "Password"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Credential Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CredentialType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (type) {
                                CredentialType.PIN -> "PIN"
                                CredentialType.PASSWORD -> "Password"
                            }
                        )
                    },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(
    selectedRole: RoleType,
    onRoleSelected: (RoleType) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = formatRoleLabel(selectedRole),
            onValueChange = {},
            readOnly = true,
            label = { Text("Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RoleType.entries.forEach { role ->
                DropdownMenuItem(
                    text = { Text(formatRoleLabel(role)) },
                    onClick = {
                        onRoleSelected(role)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatRoleLabel(role: RoleType): String = when (role) {
    RoleType.ADMINISTRATOR -> "Administrator"
    RoleType.REGISTRAR -> "Registrar"
    RoleType.INSTRUCTOR -> "Instructor"
    RoleType.TEACHING_ASSISTANT -> "Teaching Assistant"
    RoleType.LEARNER -> "Learner"
    RoleType.FINANCE_CLERK -> "Finance Clerk"
}
