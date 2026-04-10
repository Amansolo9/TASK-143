package com.learnmart.app.ui.screens.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.RoleType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToUsers: () -> Unit,
    onNavigateToPolicies: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToEnrollments: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToCart: () -> Unit = {},
    onNavigateToAssessments: () -> Unit = {},
    onNavigateToOperations: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LearnMart Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    WelcomeSection(
                        displayName = uiState.displayName,
                        roles = uiState.roles
                    )
                }

                item {
                    NavigationCardsSection(
                        roles = uiState.roles,
                        onNavigateToUsers = onNavigateToUsers,
                        onNavigateToPolicies = onNavigateToPolicies,
                        onNavigateToAuditLog = onNavigateToAuditLog,
                        onNavigateToCatalog = onNavigateToCatalog,
                        onNavigateToEnrollments = onNavigateToEnrollments,
                        onNavigateToOrders = onNavigateToOrders,
                        onNavigateToCart = onNavigateToCart,
                        onNavigateToAssessments = onNavigateToAssessments,
                        onNavigateToOperations = onNavigateToOperations
                    )
                }

                if (uiState.hasAuditPermission) {
                    item {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.recentAuditEvents.isEmpty()) {
                        item {
                            Text(
                                text = "No recent activity to display.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(
                            items = uiState.recentAuditEvents,
                            key = { it.id }
                        ) { event ->
                            AuditEventItem(event = event)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeSection(
    displayName: String,
    roles: List<RoleType>
) {
    Column {
        Text(
            text = "Welcome, $displayName",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            roles.forEach { role ->
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = role.name.replace("_", " ")
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NavigationCardsSection(
    roles: List<RoleType>,
    onNavigateToUsers: () -> Unit,
    onNavigateToPolicies: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToEnrollments: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToAssessments: () -> Unit,
    onNavigateToOperations: () -> Unit
) {
    val isAdministrator = roles.contains(RoleType.ADMINISTRATOR)
    val isRegistrar = roles.contains(RoleType.REGISTRAR)
    val isFinanceClerk = roles.contains(RoleType.FINANCE_CLERK)
    val isInstructor = roles.contains(RoleType.INSTRUCTOR)
    val isTA = roles.contains(RoleType.TEACHING_ASSISTANT)
    val isLearner = roles.contains(RoleType.LEARNER)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Catalog - visible to all roles (browse) but staff can manage
        NavigationCard(
            title = "Course Catalog",
            description = "Browse courses and class offerings",
            icon = Icons.Filled.MenuBook,
            onClick = onNavigateToCatalog
        )

        // Enrollments - visible to learners, registrars, and administrators
        if (isLearner || isRegistrar || isAdministrator) {
            NavigationCard(
                title = "Enrollments",
                description = if (isRegistrar || isAdministrator) "Manage enrollment requests and approvals"
                             else "View your enrollments and request new ones",
                icon = Icons.Filled.School,
                onClick = onNavigateToEnrollments
            )
        }

        // Cart - visible to learners
        if (isLearner) {
            NavigationCard(
                title = "Shopping Cart",
                description = "View your cart and checkout",
                icon = Icons.Filled.ShoppingCart,
                onClick = onNavigateToCart
            )
        }

        // Orders - visible to learners, finance clerks, and administrators
        if (isLearner || isFinanceClerk || isAdministrator) {
            NavigationCard(
                title = "Orders",
                description = if (isFinanceClerk || isAdministrator) "View and manage orders and payments"
                             else "View your order history",
                icon = Icons.Filled.Receipt,
                onClick = onNavigateToOrders
            )
        }

        // Assessments - visible to learners, instructors, TAs, and administrators
        if (isLearner || isInstructor || isTA || isAdministrator) {
            NavigationCard(
                title = "Assessments",
                description = when {
                    isInstructor || isTA -> "Manage assessments and grade submissions"
                    isLearner -> "View and take assessments"
                    else -> "View all assessments"
                },
                icon = Icons.Filled.Assignment,
                onClick = onNavigateToAssessments
            )
        }

        // Admin: User Management
        if (isAdministrator) {
            NavigationCard(
                title = "User Management",
                description = "Create, update, and manage user accounts and role assignments",
                icon = Icons.Filled.People,
                onClick = onNavigateToUsers
            )
        }

        // Admin: Policies
        if (isAdministrator) {
            NavigationCard(
                title = "Policies",
                description = "Configure system policies and business rules",
                icon = Icons.Filled.Description,
                onClick = onNavigateToPolicies
            )
        }

        // Audit Log
        if (isAdministrator || isRegistrar) {
            NavigationCard(
                title = "Audit Log",
                description = "View system audit trail and activity history",
                icon = Icons.Filled.Security,
                onClick = onNavigateToAuditLog
            )
        }

        // Operations
        if (isAdministrator || isFinanceClerk) {
            NavigationCard(
                title = "Operations",
                description = "Imports, reconciliation, backup/restore, and exports",
                icon = Icons.Filled.Description,
                onClick = onNavigateToOperations
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AuditEventItem(event: AuditEvent) {
    val formatter = DateTimeFormatter
        .ofPattern("MMM dd, yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    val formattedTimestamp = formatter.format(event.timestamp)

    val outcomeColor = when (event.outcome) {
        AuditOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
        AuditOutcome.FAILURE -> MaterialTheme.colorScheme.error
        AuditOutcome.DENIED -> MaterialTheme.colorScheme.error
        AuditOutcome.ERROR -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.actionType.name.replace("_", " "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = event.outcome.name,
                style = MaterialTheme.typography.labelMedium,
                color = outcomeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = formattedTimestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}
