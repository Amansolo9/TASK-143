package com.learnmart.app.ui.screens.commerce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.CartLineItem
import com.learnmart.app.domain.usecase.commerce.AddToCartRequest
import com.learnmart.app.domain.usecase.commerce.CheckoutUseCase
import com.learnmart.app.domain.usecase.commerce.ManageCartUseCase
import com.learnmart.app.domain.usecase.commerce.PricingResult
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class CartUiState(
    val cartItems: List<CartLineItem> = emptyList(),
    val pricing: PricingResult? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isCheckoutComplete: Boolean = false,
    val orderId: String? = null
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val manageCartUseCase: ManageCartUseCase,
    private val checkoutUseCase: CheckoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    init {
        loadCart()
    }

    private fun loadCart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val itemsResult = manageCartUseCase.getCartItems()) {
                is AppResult.Success -> {
                    val items = itemsResult.data
                    when (val pricingResult = manageCartUseCase.getCartPricing()) {
                        is AppResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    cartItems = items,
                                    pricing = pricingResult.data,
                                    isLoading = false
                                )
                            }
                        }
                        is AppResult.ValidationError -> {
                            _uiState.update {
                                it.copy(
                                    cartItems = items,
                                    isLoading = false,
                                    errorMessage = pricingResult.globalErrors.firstOrNull()
                                        ?: pricingResult.fieldErrors.values.firstOrNull()
                                        ?: "Failed to load pricing"
                                )
                            }
                        }
                        is AppResult.PermissionError -> {
                            _uiState.update {
                                it.copy(cartItems = items, isLoading = false, errorMessage = "Permission denied")
                            }
                        }
                        is AppResult.NotFoundError -> {
                            _uiState.update {
                                it.copy(cartItems = items, isLoading = false, errorMessage = "Cart not found")
                            }
                        }
                        is AppResult.ConflictError -> {
                            _uiState.update {
                                it.copy(cartItems = items, isLoading = false, errorMessage = pricingResult.message)
                            }
                        }
                        is AppResult.SystemError -> {
                            _uiState.update {
                                it.copy(cartItems = items, isLoading = false, errorMessage = pricingResult.message)
                            }
                        }
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = itemsResult.globalErrors.firstOrNull()
                                ?: itemsResult.fieldErrors.values.firstOrNull()
                                ?: "Failed to load cart"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied. Please log in.")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Cart not found")
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = itemsResult.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = itemsResult.message)
                    }
                }
            }
        }
    }

    fun addItem(request: AddToCartRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            when (val result = manageCartUseCase.addItem(request)) {
                is AppResult.Success -> loadCart()
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Failed to add item"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update { it.copy(errorMessage = "Permission denied") }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update { it.copy(errorMessage = "Item not found") }
                }
                is AppResult.ConflictError -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is AppResult.SystemError -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun removeItem(lineItemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            when (val result = manageCartUseCase.removeItem(lineItemId)) {
                is AppResult.Success -> loadCart()
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Failed to remove item"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update { it.copy(errorMessage = "Permission denied") }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update { it.copy(errorMessage = "Item not found") }
                }
                is AppResult.ConflictError -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is AppResult.SystemError -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun checkout(idempotencyToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val token = idempotencyToken ?: UUID.randomUUID().toString()
            val cartItems = _uiState.value.cartItems
            if (cartItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Cart is empty") }
                return@launch
            }
            val cartId = cartItems.first().cartId
            when (val result = checkoutUseCase.submitOrder(cartId, token)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCheckoutComplete = true,
                            orderId = result.data.id
                        )
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Checkout failed"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied. Please log in.")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Cart not found")
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
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.isCheckoutComplete, state.orderId) {
        if (state.isCheckoutComplete && state.orderId != null) {
            onNavigateToOrderDetail(state.orderId!!)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping Cart") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.cartItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Your cart is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Browse courses and materials to add items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(state.cartItems, key = { it.id }) { cartItem ->
                        CartItemCard(
                            item = cartItem,
                            onRemove = { viewModel.removeItem(cartItem.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    state.pricing?.let { pricing ->
                        item {
                            PricingSummaryCard(pricing = pricing)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.checkout() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        ) {
                            Text("Checkout")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartLineItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Qty: ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Unit Price: $${item.unitPrice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$${item.lineTotal}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingSummaryCard(pricing: PricingResult) {
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
                text = "Order Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            PricingRow(label = "Subtotal", amount = pricing.subtotal)

            if (pricing.taxAmount.compareTo(BigDecimal.ZERO) != 0) {
                PricingRow(label = "Tax", amount = pricing.taxAmount)
            }

            if (pricing.serviceFee.compareTo(BigDecimal.ZERO) != 0) {
                PricingRow(label = "Service Fee", amount = pricing.serviceFee)
            }

            if (pricing.packagingFee.compareTo(BigDecimal.ZERO) != 0) {
                PricingRow(label = "Packaging Fee", amount = pricing.packagingFee)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Grand Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$${pricing.grandTotal}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PricingRow(label: String, amount: BigDecimal) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$${amount}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
