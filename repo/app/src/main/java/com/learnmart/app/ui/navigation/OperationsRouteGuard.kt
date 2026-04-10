package com.learnmart.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Route-level guard state.
 */
data class RouteGuardState(
    val isChecking: Boolean = true,
    val isAuthorized: Boolean = false,
    val denialMessage: String? = null
)

/**
 * ViewModel that checks whether the current user has any of the required
 * permissions to access a guarded route. This runs before the guarded
 * composable renders, preventing unauthorized access even with crafted
 * navigation state.
 */
@HiltViewModel
class OperationsRouteGuardViewModel @Inject constructor(
    private val checkPermissionUseCase: CheckPermissionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RouteGuardState())
    val state: StateFlow<RouteGuardState> = _state.asStateFlow()

    /**
     * Check if user has at least one of the given permissions.
     */
    fun checkAccess(vararg requiredPermissions: Permission) {
        viewModelScope.launch {
            _state.value = RouteGuardState(isChecking = true)
            val hasAccess = requiredPermissions.any { checkPermissionUseCase.hasPermission(it) }
            _state.value = RouteGuardState(
                isChecking = false,
                isAuthorized = hasAccess,
                denialMessage = if (!hasAccess) "Access denied. You do not have the required permissions." else null
            )
        }
    }
}

/**
 * Reusable composable guard that checks permissions before rendering content.
 * Unauthorized users see an access-denied message and a back button.
 * Used for operations routes.
 */
@Composable
fun RequireOperationsAccess(
    requiredPermissions: Array<Permission>,
    onNavigateBack: () -> Unit,
    viewModel: OperationsRouteGuardViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val guardState by viewModel.state.collectAsState()

    LaunchedEffect(requiredPermissions.toList()) {
        viewModel.checkAccess(*requiredPermissions)
    }

    RouteGuardContent(
        guardState = guardState,
        onNavigateBack = onNavigateBack,
        content = content
    )
}

/**
 * Generic reusable composable guard for any protected route.
 * Checks that the current user has at least one of the required permissions.
 * This is used for admin, audit, enrollment-review, and other sensitive routes.
 */
@Composable
fun RequirePermission(
    requiredPermissions: Array<Permission>,
    onNavigateBack: () -> Unit,
    viewModel: OperationsRouteGuardViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val guardState by viewModel.state.collectAsState()

    LaunchedEffect(requiredPermissions.toList()) {
        viewModel.checkAccess(*requiredPermissions)
    }

    RouteGuardContent(
        guardState = guardState,
        onNavigateBack = onNavigateBack,
        content = content
    )
}

@Composable
private fun RouteGuardContent(
    guardState: RouteGuardState,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        guardState.isChecking -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        guardState.isAuthorized -> {
            content()
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = guardState.denialMessage ?: "Access denied",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
