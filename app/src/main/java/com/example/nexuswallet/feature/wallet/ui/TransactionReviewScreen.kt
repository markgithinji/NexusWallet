package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    navController: NavController,
    transactionId: String,
    viewModel: TransactionReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val transaction = uiState.transaction

    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep is TransactionReviewViewModel.TransactionStep.SUCCESS) {
            delay(2000) // Show success for 2 seconds
            transaction?.let {
                navController.navigate("walletDetail/${it.walletId}") {
                    popUpTo("walletDetail/${it.walletId}") { inclusive = false }
                }
            }
        }
    }

    // Initialize on load
    LaunchedEffect(Unit) {
        viewModel.initialize(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            // Show approval button only when in REVIEWING state
            when (uiState.currentStep) {
                is TransactionReviewViewModel.TransactionStep.REVIEWING -> {
                    ApprovalBottomBar(
                        onApprove = { viewModel.onEvent(TransactionReviewViewModel.ReviewEvent.ApproveTransaction) }
                    )
                }
                is TransactionReviewViewModel.TransactionStep.SUCCESS -> {
                    DoneBottomBar(
                        onDone = {
                            transaction?.let {
                                navController.navigate("walletDetail/${it.walletId}") {
                                    popUpTo("walletDetail/${it.walletId}") { inclusive = false }
                                }
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Show current step
            TransactionStepStatus(currentStep = uiState.currentStep)

            // Error Message
            uiState.error?.let { error ->
                ErrorMessage(error = error) {
                    viewModel.onEvent(TransactionReviewViewModel.ReviewEvent.ClearError)
                }
            }

            // Loading State
            if (uiState.isLoading || uiState.currentStep is TransactionReviewViewModel.TransactionStep.LOADING) {
                LoadingScreen()
                return@Scaffold
            }

            transaction?.let { tx ->
                // Transaction Summary
                TransactionReviewSummary(transaction = tx)

                Spacer(modifier = Modifier.height(16.dp))

                // From/To Addresses
                AddressesSection(transaction = tx)

                Spacer(modifier = Modifier.height(16.dp))

                // Fee Details
                FeeDetailsSection(transaction = tx)

                Spacer(modifier = Modifier.height(16.dp))

                // Note
                if (!tx.note.isNullOrEmpty()) {
                    NoteSection(note = tx.note)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Show broadcast result
                uiState.broadcastResult?.let { result ->
                    BroadcastResultSection(broadcastResult = result)
                }
            } ?: run {
                // No transaction found
                EmptyTransactionView()
            }
        }
    }
}

@Composable
fun TransactionStepStatus(currentStep: TransactionReviewViewModel.TransactionStep) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (currentStep) {
                is TransactionReviewViewModel.TransactionStep.LOADING ->
                    MaterialTheme.colorScheme.primaryContainer
                is TransactionReviewViewModel.TransactionStep.REVIEWING ->
                    Color.Blue.copy(alpha = 0.1f)
                is TransactionReviewViewModel.TransactionStep.APPROVING ->
                    Color.Cyan .copy(alpha = 0.1f)
                is TransactionReviewViewModel.TransactionStep.SIGNING ->
                    Color.Yellow.copy(alpha = 0.1f)
                is TransactionReviewViewModel.TransactionStep.BROADCASTING ->
                    Color.Magenta.copy(alpha = 0.1f)
                is TransactionReviewViewModel.TransactionStep.SUCCESS ->
                    Color.Green.copy(alpha = 0.1f)
                is TransactionReviewViewModel.TransactionStep.ERROR ->
                    Color.Red.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (currentStep) {
                is TransactionReviewViewModel.TransactionStep.LOADING -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading transaction...")
                }
                is TransactionReviewViewModel.TransactionStep.REVIEWING -> {
                    Icon(Icons.Default.Visibility, "Reviewing", tint = Color.Blue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Review transaction details")
                }
                is TransactionReviewViewModel.TransactionStep.APPROVING -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = Color.Cyan
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Approving transaction...")
                }
                is TransactionReviewViewModel.TransactionStep.SIGNING -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = Color.Yellow
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Signing transaction...")
                }
                is TransactionReviewViewModel.TransactionStep.BROADCASTING -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = Color.Magenta
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Broadcasting to network...")
                }
                is TransactionReviewViewModel.TransactionStep.SUCCESS -> {
                    Icon(Icons.Default.CheckCircle, "Success", tint = Color.Green)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transaction successful!", color = Color.Green)
                }
                is TransactionReviewViewModel.TransactionStep.ERROR -> {
                    Icon(Icons.Default.Error, "Error", tint = Color.Red)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Error: ${currentStep.message}", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun ApprovalBottomBar(onApprove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DoneBottomBar(onDone: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TransactionReviewSummary(transaction: SendTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Amount
            Text(
                text = transaction.amountDecimal,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${transaction.walletType.name.take(3).uppercase()}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status
            TransactionStatusChip(status = transaction.status)

            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "${transaction.totalDecimal} ${transaction.walletType.name.take(3).uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddressesSection(transaction: SendTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // From Address
            AddressRow(
                label = "From",
                address = transaction.fromAddress,
                isSender = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Arrow
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "To",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // To Address
            AddressRow(
                label = "To",
                address = transaction.toAddress,
                isSender = false
            )
        }
    }
}

@Composable
fun AddressRow(
    label: String,
    address: String,
    isSender: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSender) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSender) Icons.Default.AccountBalanceWallet
                    else Icons.Default.Person,
                    contentDescription = label,
                    tint = if (isSender) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (address.length > 16) {
                        "${address.take(8)}...${address.takeLast(8)}"
                    } else {
                        address
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { /* Copy address */ }) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }
        }
    }
}

@Composable
fun FeeDetailsSection(transaction: SendTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Network Fee
            DetailRow(
                label = "Network Fee",
                value = "${transaction.feeDecimal} ${transaction.walletType.name.take(3).uppercase()}"
            )

            // Gas Price (if Ethereum)
            transaction.gasPrice?.let { gasPrice ->
                DetailRow(
                    label = "Gas Price",
                    value = "$gasPrice Gwei"
                )
            }

            // Gas Limit (if Ethereum)
            transaction.gasLimit?.let { gasLimit ->
                DetailRow(
                    label = "Gas Limit",
                    value = gasLimit
                )
            }

            // Nonce (if Ethereum)
            transaction.nonce?.let { nonce ->
                DetailRow(
                    label = "Nonce",
                    value = nonce.toString()
                )
            }

            // Date
            DetailRow(
                label = "Date",
                value = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    .format(Date(transaction.timestamp))
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String
) {
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
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NoteSection(note: String) {
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Note,
                    contentDescription = "Note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SigningStatus() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Signing transaction...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SignedTransactionSection(signedTransaction: SignedTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Signed",
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Transaction Signed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Green
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Hash: ${signedTransaction.hash.take(16)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BroadcastingStatus() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Broadcasting to network...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun BroadcastResultSection(broadcastResult: BroadcastResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (broadcastResult.success)
                Color.Green.copy(alpha = 0.1f)
            else
                Color.Red.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (broadcastResult.success)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.Error,
                    contentDescription = "Broadcast Result",
                    tint = if (broadcastResult.success) Color.Green else Color.Red,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (broadcastResult.success)
                        "Transaction Broadcasted!"
                    else
                        "Broadcast Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (broadcastResult.success) Color.Green else Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            broadcastResult.hash?.let { hash ->
                Text(
                    text = "TX Hash: ${hash.take(16)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            broadcastResult.error?.let { error ->
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
fun EmptyTransactionView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "No Transaction",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Transaction Not Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The transaction you're looking for doesn't exist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TransactionStatusChip(status: TransactionStatus) {
    val (text, color) = when (status) {
        TransactionStatus.PENDING -> Pair("Pending", Color.Yellow)
        TransactionStatus.SUCCESS -> Pair("Success", Color.Green)
        TransactionStatus.FAILED -> Pair("Failed", Color.Red)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ErrorMessage(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}