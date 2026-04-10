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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnmart.app.domain.model.DeliveryConfirmation
import com.learnmart.app.domain.model.DeliveryType
import com.learnmart.app.domain.model.FulfillmentRecord
import com.learnmart.app.domain.model.Order
import com.learnmart.app.domain.model.OrderLineItem
import com.learnmart.app.domain.model.OrderPriceComponent
import com.learnmart.app.domain.model.OrderStatus
import com.learnmart.app.domain.model.PaymentRecord
import com.learnmart.app.domain.model.PaymentStatus
import com.learnmart.app.domain.model.PriceComponentType
import com.learnmart.app.domain.model.TenderType
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.usecase.commerce.ManageOrderUseCase
import com.learnmart.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

data class OrderDetailUiState(
    val order: Order? = null,
    val lineItems: List<OrderLineItem> = emptyList(),
    val priceComponents: List<OrderPriceComponent> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val fulfillment: FulfillmentRecord? = null,
    val delivery: DeliveryConfirmation? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manageOrderUseCase: ManageOrderUseCase,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    init {
        loadOrderDetail()
    }

    private fun loadOrderDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val orderResult = manageOrderUseCase.getOrderById(orderId)) {
                is AppResult.Success -> {
                    val order = orderResult.data
                    val lineItems = (manageOrderUseCase.getOrderLineItems(orderId) as? AppResult.Success)?.data ?: emptyList()
                    val priceComponents = (manageOrderUseCase.getOrderPriceComponents(orderId) as? AppResult.Success)?.data ?: emptyList()
                    val payments = paymentRepository.getPaymentsForOrder(orderId)
                    val fulfillment = paymentRepository.getFulfillmentForOrder(orderId)
                    val deliveryConfirmation = paymentRepository.getDeliveryConfirmation(orderId)

                    _uiState.update {
                        it.copy(
                            order = order,
                            lineItems = lineItems,
                            priceComponents = priceComponents,
                            payments = payments,
                            fulfillment = fulfillment,
                            delivery = deliveryConfirmation,
                            isLoading = false
                        )
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Order not found")
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied")
                    }
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = orderResult.globalErrors.firstOrNull()
                                ?: orderResult.fieldErrors.values.firstOrNull()
                                ?: "Validation error"
                        )
                    }
                }
                is AppResult.ConflictError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = orderResult.message)
                    }
                }
                is AppResult.SystemError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = orderResult.message)
                    }
                }
            }
        }
    }

    fun startFulfillment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageOrderUseCase.startFulfillment(orderId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(actionMessage = "Fulfillment started") }
                    loadOrderDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot start fulfillment"
                        )
                    }
                }
                is AppResult.PermissionError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Permission denied: requires order.fulfill")
                    }
                }
                is AppResult.NotFoundError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Order not found")
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

    fun confirmDelivery(deliveryType: DeliveryType, notes: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageOrderUseCase.confirmDelivery(orderId, deliveryType, notes)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(actionMessage = "Delivery confirmed") }
                    loadOrderDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot confirm delivery"
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
                        it.copy(isLoading = false, errorMessage = "Order not found")
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

    fun cancelOrder(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageOrderUseCase.cancelOrder(orderId, reason)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(actionMessage = "Order cancelled") }
                    loadOrderDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot cancel order"
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
                        it.copy(isLoading = false, errorMessage = "Order not found")
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

    fun closeOrder() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = manageOrderUseCase.closeOrder(orderId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(actionMessage = "Order closed") }
                    loadOrderDetail()
                }
                is AppResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.globalErrors.firstOrNull()
                                ?: result.fieldErrors.values.firstOrNull()
                                ?: "Cannot close order"
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
                        it.copy(isLoading = false, errorMessage = "Order not found")
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

private val detailDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    onNavigateToRecordPayment: (String) -> Unit,
    onNavigateToIssueRefund: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeliveryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionMessage()
        }
    }

    if (showCancelDialog) {
        CancelOrderDialog(
            onConfirm = { reason ->
                showCancelDialog = false
                viewModel.cancelOrder(reason)
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    if (showDeliveryDialog) {
        ConfirmDeliveryDialog(
            onConfirm = { deliveryType, notes ->
                showDeliveryDialog = false
                viewModel.confirmDelivery(deliveryType, notes)
            },
            onDismiss = { showDeliveryDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Details") },
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

            state.order == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Order not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                val order = state.order!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Order Header Card
                    OrderInfoCard(order = order)

                    // Line Items
                    if (state.lineItems.isNotEmpty()) {
                        LineItemsSection(lineItems = state.lineItems)
                    }

                    // Price Breakdown
                    if (state.priceComponents.isNotEmpty()) {
                        PriceBreakdownSection(components = state.priceComponents)
                    }

                    // Payment History
                    if (state.payments.isNotEmpty()) {
                        PaymentHistorySection(
                            payments = state.payments,
                            onIssueRefund = { paymentId ->
                                onNavigateToIssueRefund(orderId, paymentId)
                            }
                        )
                    }

                    // Fulfillment / Delivery Info
                    FulfillmentSection(
                        fulfillment = state.fulfillment,
                        delivery = state.delivery
                    )

                    // Action Buttons
                    OrderActionsSection(
                        order = order,
                        onRecordPayment = { onNavigateToRecordPayment(orderId) },
                        onStartFulfillment = { viewModel.startFulfillment() },
                        onConfirmDelivery = { showDeliveryDialog = true },
                        onCancelOrder = { showCancelDialog = true },
                        onCloseOrder = { viewModel.closeOrder() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderInfoCard(order: Order) {
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
                text = "Order Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            OrderInfoRow(label = "Order ID", value = "#${order.id.takeLast(8)}")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DetailOrderStatusChip(status = order.status)
            }

            OrderInfoRow(
                label = "Grand Total",
                value = "$${order.grandTotal}"
            )
            OrderInfoRow(
                label = "Paid Amount",
                value = "$${order.paidAmount}"
            )
            order.placedAt?.let {
                OrderInfoRow(
                    label = "Placed At",
                    value = detailDateFormatter.format(it)
                )
            }
            order.cancelledAt?.let {
                OrderInfoRow(
                    label = "Cancelled At",
                    value = detailDateFormatter.format(it)
                )
            }
            order.cancelReason?.let {
                OrderInfoRow(
                    label = "Cancel Reason",
                    value = it
                )
            }
        }
    }
}

@Composable
private fun OrderInfoRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LineItemsSection(lineItems: List<OrderLineItem>) {
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
                text = "Line Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            lineItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Qty: ${item.quantity} x $${item.unitPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$${item.lineTotal}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceBreakdownSection(components: List<OrderPriceComponent>) {
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
                text = "Price Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            components.forEach { component ->
                val isGrandTotal = component.componentType == PriceComponentType.GRAND_TOTAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = component.description,
                        style = if (isGrandTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isGrandTotal) FontWeight.Bold else FontWeight.Normal,
                        color = if (isGrandTotal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${component.amount}",
                        style = if (isGrandTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isGrandTotal) FontWeight.Bold else FontWeight.Normal,
                        color = if (isGrandTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isGrandTotal && component != components.last()) {
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun PaymentHistorySection(
    payments: List<PaymentRecord>,
    onIssueRefund: (String) -> Unit
) {
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
                text = "Payment History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            payments.forEach { payment ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$${payment.amount} via ${payment.tenderType.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        PaymentStatusChip(status = payment.status)
                    }

                    Text(
                        text = "Received: ${detailDateFormatter.format(payment.receivedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    payment.externalReference?.let { ref ->
                        Text(
                            text = "Ref: $ref",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    payment.notes?.let { notes ->
                        Text(
                            text = "Notes: $notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val canRefund = payment.status in listOf(
                        PaymentStatus.RECORDED,
                        PaymentStatus.ALLOCATED,
                        PaymentStatus.CLEARED,
                        PaymentStatus.PARTIALLY_REFUNDED
                    )
                    if (canRefund) {
                        OutlinedButton(
                            onClick = { onIssueRefund(payment.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Issue Refund")
                        }
                    }

                    if (payment != payments.last()) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FulfillmentSection(
    fulfillment: FulfillmentRecord?,
    delivery: DeliveryConfirmation?
) {
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
                text = "Fulfillment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            if (fulfillment != null) {
                OrderInfoRow(
                    label = "Fulfilled By",
                    value = fulfillment.fulfilledBy
                )
                OrderInfoRow(
                    label = "Fulfilled At",
                    value = detailDateFormatter.format(fulfillment.fulfilledAt)
                )
                fulfillment.notes?.let { notes ->
                    OrderInfoRow(label = "Notes", value = notes)
                }
            } else {
                Text(
                    text = "Not yet fulfilled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (delivery != null) {
                Divider()
                OrderInfoRow(
                    label = "Delivery Type",
                    value = delivery.deliveryType.name
                )
                OrderInfoRow(
                    label = "Confirmed By",
                    value = delivery.confirmedBy
                )
                OrderInfoRow(
                    label = "Confirmed At",
                    value = detailDateFormatter.format(delivery.confirmedAt)
                )
                delivery.notes?.let { notes ->
                    OrderInfoRow(label = "Notes", value = notes)
                }
            }
        }
    }
}

@Composable
private fun OrderActionsSection(
    order: Order,
    onRecordPayment: () -> Unit,
    onStartFulfillment: () -> Unit,
    onConfirmDelivery: () -> Unit,
    onCancelOrder: () -> Unit,
    onCloseOrder: () -> Unit
) {
    val canRecordPayment = order.status in listOf(
        OrderStatus.PLACED_UNPAID,
        OrderStatus.PARTIALLY_PAID
    )
    val canStartFulfillment = order.status == OrderStatus.PAID
    val canConfirmDelivery = order.status in listOf(
        OrderStatus.FULFILLMENT_IN_PROGRESS,
        OrderStatus.AWAITING_PICKUP
    )
    val canCancel = order.status.canTransitionTo(OrderStatus.MANUAL_CANCELLED)
    val canClose = order.status.canTransitionTo(OrderStatus.CLOSED)

    val hasActions = canRecordPayment || canStartFulfillment || canConfirmDelivery || canCancel || canClose

    if (hasActions) {
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
                    text = "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                if (canRecordPayment) {
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Record Payment")
                    }
                }

                if (canStartFulfillment) {
                    Button(
                        onClick = onStartFulfillment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Fulfillment")
                    }
                }

                if (canConfirmDelivery) {
                    Button(
                        onClick = onConfirmDelivery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirm Delivery")
                    }
                }

                if (canClose) {
                    OutlinedButton(
                        onClick = onCloseOrder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Order")
                    }
                }

                if (canCancel) {
                    OutlinedButton(
                        onClick = onCancelOrder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel Order",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailOrderStatusChip(status: OrderStatus) {
    val (label, containerColor, contentColor) = when (status) {
        OrderStatus.CART -> Triple(
            "Cart",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        OrderStatus.QUOTED -> Triple(
            "Quoted",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        OrderStatus.PENDING_SUBMISSION -> Triple(
            "Pending",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        OrderStatus.PLACED_UNPAID -> Triple(
            "Unpaid",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        OrderStatus.PARTIALLY_PAID -> Triple(
            "Partial Pay",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        OrderStatus.PAID -> Triple(
            "Paid",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        OrderStatus.FULFILLMENT_IN_PROGRESS -> Triple(
            "Fulfilling",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        OrderStatus.AWAITING_PICKUP -> Triple(
            "Awaiting Pickup",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        OrderStatus.DELIVERED -> Triple(
            "Delivered",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        OrderStatus.CLOSED -> Triple(
            "Closed",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        OrderStatus.AUTO_CANCELLED -> Triple(
            "Auto-Cancelled",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        OrderStatus.MANUAL_CANCELLED -> Triple(
            "Cancelled",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        OrderStatus.REFUND_IN_PROGRESS -> Triple(
            "Refunding",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        OrderStatus.RETURN_REQUESTED -> Triple(
            "Return Req.",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        OrderStatus.RETURNED -> Triple(
            "Returned",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        OrderStatus.EXCHANGE_IN_PROGRESS -> Triple(
            "Exchanging",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        OrderStatus.EXCHANGED -> Triple(
            "Exchanged",
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
private fun PaymentStatusChip(status: PaymentStatus) {
    val (label, containerColor, contentColor) = when (status) {
        PaymentStatus.RECORDED -> Triple(
            "Recorded",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        PaymentStatus.ALLOCATED -> Triple(
            "Allocated",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PaymentStatus.CLEARED -> Triple(
            "Cleared",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        PaymentStatus.DISCREPANCY_FLAGGED -> Triple(
            "Discrepancy",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        PaymentStatus.RESOLVED -> Triple(
            "Resolved",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PaymentStatus.VOIDED -> Triple(
            "Voided",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        PaymentStatus.PARTIALLY_REFUNDED -> Triple(
            "Partial Refund",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        PaymentStatus.REFUNDED -> Triple(
            "Refunded",
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
private fun CancelOrderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to cancel this order? This action cannot be undone.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Cancellation Reason") },
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
                    text = "Cancel Order",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Order")
            }
        }
    )
}

@Composable
private fun ConfirmDeliveryDialog(
    onConfirm: (DeliveryType, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delivery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select how the order was delivered:")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConfirm(DeliveryType.PICKUP, notes.ifBlank { null }) }) {
                    Text("Pickup")
                }
                TextButton(onClick = { onConfirm(DeliveryType.DELIVERY, notes.ifBlank { null }) }) {
                    Text("Delivery")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
