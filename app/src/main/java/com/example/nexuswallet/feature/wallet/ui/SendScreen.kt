package com.example.nexuswallet.feature.wallet.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.TransactionState
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    walletId: String,
    viewModel: SendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showMaxDialog by remember { mutableStateOf(false) }

    // Initialize once
    LaunchedEffect(Unit) {
        viewModel.initialize(walletId)
    }

    // Handle transaction state changes
    LaunchedEffect(uiState.transactionState) {
        when (val state = uiState.transactionState) {
            is TransactionState.Created -> {
                // Navigate to review screen with transaction
                navController.navigate("review/${state.transaction.id}")
                // Reset state after navigation
                viewModel.onEvent(SendViewModel.SendEvent.ResetTransactionState)
            }

            is TransactionState.Success -> {
                // Show success message
                Toast.makeText(context, "Transaction successful: ${state.hash.take(10)}...", Toast.LENGTH_LONG).show()
                // Navigate back or to transaction details
                navController.navigate("transaction/${state.hash}")
            }

            is TransactionState.Error -> {
                // Show error message
                if (uiState.error == null) {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
            }

            TransactionState.Loading -> {
                // Loading is already shown in UI
            }

            TransactionState.Idle -> {
                // Do nothing
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Show different icons based on transaction state
                    when (uiState.transactionState) {
                        TransactionState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        is TransactionState.Created -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                "Created",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        is TransactionState.Error -> {
                            Icon(
                                Icons.Default.Error,
                                "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            )
        },
        bottomBar = {
            SendBottomBar(
                uiState = uiState,
                onSend = {
                    viewModel.onEvent(SendViewModel.SendEvent.CreateTransaction)
                },
                onMaxClick = { showMaxDialog = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Transaction State Banner
            TransactionStateBanner(uiState.transactionState)

            // Error Message
            uiState.error?.let { error ->
                ErrorMessage(error = error) {
                    viewModel.onEvent(SendViewModel.SendEvent.ClearError)
                }
            }

            BalanceCard(
                balance = uiState.balance,
                walletType = uiState.walletType,
                address = uiState.fromAddress
            )
            Spacer(modifier = Modifier.height(16.dp))


            // Recipient Address
            AddressInputSection(
                toAddress = uiState.toAddress,
                onAddressChange = { viewModel.onEvent(SendViewModel.SendEvent.ToAddressChanged(it)) },
                walletType = uiState.walletType
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input
            AmountInputSection(
                amount = uiState.amount,
                onAmountChange = { viewModel.onEvent(SendViewModel.SendEvent.AmountChanged(it)) },
                balance = uiState.balance,
                walletType = uiState.walletType,
                onMaxClick = { showMaxDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fee Selection
            FeeSelectionSection(
                feeLevel = uiState.feeLevel,
                onFeeLevelChange = { viewModel.onEvent(SendViewModel.SendEvent.FeeLevelChanged(it)) },
                feeEstimate = uiState.feeEstimate,
                walletType = uiState.walletType
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Note Input
            NoteInputSection(
                note = uiState.note,
                onNoteChange = { viewModel.onEvent(SendViewModel.SendEvent.NoteChanged(it)) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Transaction Summary
            TransactionSummarySection(
                amount = uiState.amount,
                feeEstimate = uiState.feeEstimate,
                walletType = uiState.walletType
            )
        }

        // Max Amount Dialog
        if (showMaxDialog) {
            MaxAmountDialog(
                balance = uiState.balance,
                feeEstimate = uiState.feeEstimate,
                onDismiss = { showMaxDialog = false },
                onConfirm = { maxAmount ->
                    viewModel.onEvent(SendViewModel.SendEvent.AmountChanged(maxAmount))
                    showMaxDialog = false
                }
            )
        }

        // Navigate to review when transaction is created
        LaunchedEffect(uiState.isLoading) {
            if (!uiState.isLoading && uiState.error == null && viewModel.getCreatedTransaction() != null) {
                // This will be handled by navigation
                // For now, show success
                Toast.makeText(context, "Transaction created successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun TransactionStateBanner(state: TransactionState) {
    when (state) {
        TransactionState.Loading -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Creating transaction...")
                }
            }
        }

        is TransactionState.Created -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Created",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transaction created successfully!")
                }
            }
        }

        is TransactionState.Error -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(state.message, maxLines = 2)
                }
            }
        }

        else -> {
            // Don't show anything for Idle or Success (handled elsewhere)
        }
    }
}
// ===== COMPOSABLE COMPONENTS =====

@Composable
fun BalanceCard(
    balance: BigDecimal,
    walletType: WalletType,
    address: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "$${String.format("%.2f", balance.toDouble())}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${balance.toPlainString()} ${walletType.name.take(3).uppercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (walletType) {
                            WalletType.BITCOIN -> Icons.Default.CurrencyBitcoin
                            WalletType.ETHEREUM -> Icons.Default.CurrencyExchange
                            else -> Icons.Default.AccountBalanceWallet
                        },
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "From: ${address.take(12)}...${address.takeLast(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddressInputSection(
    toAddress: String,
    onAddressChange: (String) -> Unit,
    walletType: WalletType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Recipient",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Recipient Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = toAddress,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = when (walletType) {
                            WalletType.BITCOIN -> "Enter Bitcoin address (bc1..., 1..., or 3...)"
                            WalletType.ETHEREUM -> "Enter Ethereum address (0x...)"
                            else -> "Enter wallet address"
                        }
                    )
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (toAddress.isNotEmpty()) {
                        IconButton(onClick = { onAddressChange("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { /* Scan QR code */ },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Scan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan QR Code")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = { /* Paste from clipboard */ },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste")
                }
            }
        }
    }
}

@Composable
fun AmountInputSection(
    amount: String,
    onAmountChange: (String) -> Unit,
    balance: BigDecimal,
    walletType: WalletType,
    onMaxClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = "Amount",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Max: ${balance.toPlainString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        // Allow only numbers and decimal point
                        if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                            onAmountChange(newValue)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("0.00") },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Decimal
                    ),
                    trailingIcon = {
                        Text(
                            text = when (walletType) {
                                WalletType.BITCOIN -> "BTC"
                                WalletType.ETHEREUM -> "ETH"
                                else -> "TOK"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onMaxClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("MAX")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount in USD (demo)
            if (amount.isNotEmpty()) {
                val amountValue = try {
                    BigDecimal(amount)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }

                val usdRate = when (walletType) {
                    WalletType.BITCOIN -> 45000.0
                    WalletType.ETHEREUM -> 3000.0
                    else -> 1.0
                }

                val usdAmount = amountValue.toDouble() * usdRate

                Text(
                    text = "≈ $${String.format("%.2f", usdAmount)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun FeeSelectionSection(
    feeLevel: FeeLevel,
    onFeeLevelChange: (FeeLevel) -> Unit,
    feeEstimate: FeeEstimate?,
    walletType: WalletType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Fee",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Transaction Fee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fee Level Selection
            FeeLevelSelection(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fee Details
            feeEstimate?.let { fee ->
                FeeDetailsCard(
                    feeEstimate = fee,
                    walletType = walletType
                )
            }
        }
    }
}

@Composable
fun FeeLevelSelection(
    selectedLevel: FeeLevel,
    onLevelSelected: (FeeLevel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FeeLevelButton(
            level = FeeLevel.SLOW,
            selected = selectedLevel == FeeLevel.SLOW,
            onClick = { onLevelSelected(FeeLevel.SLOW) }
        )

        FeeLevelButton(
            level = FeeLevel.NORMAL,
            selected = selectedLevel == FeeLevel.NORMAL,
            onClick = { onLevelSelected(FeeLevel.NORMAL) }
        )

        FeeLevelButton(
            level = FeeLevel.FAST,
            selected = selectedLevel == FeeLevel.FAST,
            onClick = { onLevelSelected(FeeLevel.FAST) }
        )
    }
}

@Composable
fun FeeLevelButton(
    level: FeeLevel,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (text, icon, color) = when (level) {
        FeeLevel.SLOW -> Triple("Slow", Icons.Default.Timer, Color(0xFF4CAF50))
        FeeLevel.NORMAL -> Triple("Normal", Icons.Default.CheckCircle, Color(0xFF2196F3))
        FeeLevel.FAST -> Triple("Fast", Icons.Default.FlashOn, Color(0xFFFF9800))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() }
    ) {
        Card(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) color.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (selected) BorderStroke(2.dp, color) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeeDetailsCard(
    feeEstimate: FeeEstimate,
    walletType: WalletType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fee Amount:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${feeEstimate.totalFeeDecimal} ${walletType.name.take(3).uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "≈ USD:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val usdRate = when (walletType) {
                    WalletType.BITCOIN -> 45000.0
                    WalletType.ETHEREUM -> 3000.0
                    else -> 1.0
                }

                val feeUsd = feeEstimate.totalFeeDecimal.toDoubleOrNull() ?: 0.0 * usdRate

                Text(
                    text = "$${String.format("%.2f", feeUsd)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Estimated Time:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatTime(feeEstimate.estimatedTime),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (feeEstimate.gasPrice != null || feeEstimate.feePerByte != null) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (feeEstimate.gasPrice != null) "Gas Price:" else "Fee Rate:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (feeEstimate.gasPrice != null)
                            "${feeEstimate.gasPrice} Gwei"
                        else
                            "${feeEstimate.feePerByte} sat/byte",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NoteInputSection(
    note: String,
    onNoteChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Note,
                    contentDescription = "Note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Note (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note for this transaction") },
                shape = RoundedCornerShape(8.dp),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                )
            )
        }
    }
}

@Composable
fun TransactionSummarySection(
    amount: String,
    feeEstimate: FeeEstimate?,
    walletType: WalletType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Transaction Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider()

            Spacer(modifier = Modifier.height(12.dp))

            // Amount
            SummaryRow(
                label = "Amount",
                value = if (amount.isNotEmpty()) "$amount ${walletType.name.take(3).uppercase()}" else "0.00",
                isBold = false
            )

            // Fee
            feeEstimate?.let { fee ->
                SummaryRow(
                    label = "Network Fee",
                    value = "${fee.totalFeeDecimal} ${walletType.name.take(3).uppercase()}",
                    isBold = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            // Total
            val totalAmount = try {
                val amountValue = BigDecimal(amount)
                val feeValue = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                amountValue + feeValue
            } catch (e: Exception) {
                BigDecimal.ZERO
            }

            SummaryRow(
                label = "Total",
                value = "${totalAmount.toPlainString()} ${walletType.name.take(3).uppercase()}",
                isBold = true
            )
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    isBold: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SendBottomBar(
    uiState: SendViewModel.SendUiState,
    onSend: () -> Unit,
    onMaxClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Show transaction state message if present
            val transactionMessage = when (uiState.transactionState) {
                TransactionState.Loading -> "Creating transaction..."
                is TransactionState.Created -> "✅ Transaction created"
                is TransactionState.Error -> "❌ ${uiState.transactionState.message}"
                else -> uiState.validationError
            }

            transactionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (uiState.transactionState) {
                        is TransactionState.Error -> MaterialTheme.colorScheme.error
                        is TransactionState.Created -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState.isValid && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                when (uiState.transactionState) {
                    TransactionState.Loading -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating...")
                    }
                    is TransactionState.Created -> {
                        Icon(Icons.Default.CheckCircle, "Created")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Created!")
                    }
                    else -> {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MaxAmountDialog(
    balance: BigDecimal,
    feeEstimate: FeeEstimate?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Send Maximum Amount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                val fee = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal("0.001")
                val maxAmount = balance - fee

                if (maxAmount > BigDecimal.ZERO) {
                    Text(
                        text = "Available balance: ${balance.toPlainString()}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Network fee: ${fee.toPlainString()}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Maximum sendable: ${maxAmount.toPlainString()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will send all available funds minus the network fee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Insufficient balance to cover network fee.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val fee = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal("0.001")
            val maxAmount = balance - fee

            if (maxAmount > BigDecimal.ZERO) {
                Button(
                    onClick = { onConfirm(maxAmount.toPlainString()) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Send Maximum")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

// Helper function
private fun formatTime(seconds: Int): String {
    return when {
        seconds < 60 -> "$seconds sec"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} hr"
    }
}