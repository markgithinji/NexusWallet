package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.nexuswallet.feature.wallet.domain.ChainType
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
    val context = LocalContext.current

    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep is TransactionReviewViewModel.TransactionStep.SUCCESS) {
            delay(4000)
            transaction?.let {
                navController.navigate("walletDetail/${it.walletId}") {
                    popUpTo("walletDetail/${it.walletId}") { inclusive = false }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (uiState.chainType) {
                                ChainType.BITCOIN -> Icons.Default.CurrencyBitcoin
                                ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> Icons.Default.CurrencyExchange
                                ChainType.SOLANA -> Icons.Default.Star
                                else -> Icons.Default.AccountBalanceWallet
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review ${uiState.chainType.displayName} Transaction")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            when (uiState.currentStep) {
                is TransactionReviewViewModel.TransactionStep.REVIEWING -> {
                    ApprovalBottomBar(
                        chainType = uiState.chainType,
                        onApprove = { viewModel.onEvent(TransactionReviewViewModel.ReviewEvent.ApproveTransaction) }
                    )
                }
                is TransactionReviewViewModel.TransactionStep.SUCCESS -> {
                    DoneBottomBar(
                        chainType = uiState.chainType,
                        txHash = uiState.broadcastResult?.hash,
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
            TransactionStepStatus(
                currentStep = uiState.currentStep,
                chainType = uiState.chainType
            )

            uiState.error?.let { error ->
                ErrorMessage(error = error) {
                    viewModel.onEvent(TransactionReviewViewModel.ReviewEvent.ClearError)
                }
            }

            if (uiState.isLoading || uiState.currentStep is TransactionReviewViewModel.TransactionStep.LOADING) {
                LoadingScreen()
                return@Scaffold
            }

            transaction?.let { tx ->
                TransactionReviewSummary(
                    transaction = tx,
                    chainType = uiState.chainType
                )

                Spacer(modifier = Modifier.height(16.dp))

                AddressesSection(
                    transaction = tx,
                    chainType = uiState.chainType
                )

                Spacer(modifier = Modifier.height(16.dp))

                FeeDetailsSection(
                    transaction = tx,
                    chainType = uiState.chainType
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!tx.note.isNullOrEmpty()) {
                    NoteSection(note = tx.note)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                uiState.broadcastResult?.let { result ->
                    BroadcastResultSection(
                        broadcastResult = result,
                        chainType = uiState.chainType
                    )

                    if (uiState.currentStep is TransactionReviewViewModel.TransactionStep.CHECKING_STATUS) {
                        CheckingStatusSection(chainType = uiState.chainType)
                    }

                    if (uiState.currentStep is TransactionReviewViewModel.TransactionStep.SUCCESS) {
                        TransactionFinalStatusSection(
                            confirmed = uiState.transactionConfirmed,
                            txHash = result.hash,
                            chainType = uiState.chainType
                        )
                    }
                }
            } ?: run {
                EmptyTransactionView()
            }
        }
    }
}

@Composable
fun TransactionReviewSummary(
    transaction: SendTransaction,
    chainType: ChainType
) {
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
            Text(
                text = transaction.amountDecimal,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = getTokenSymbol(transaction, chainType),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            TransactionStatusChip(status = transaction.status)

            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

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
                    text = "${transaction.totalDecimal} ${getTokenSymbol(transaction, chainType)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddressesSection(
    transaction: SendTransaction,
    chainType: ChainType
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
            AddressRow(
                label = "From",
                address = transaction.fromAddress,
                chainType = chainType,
                isSender = true
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            AddressRow(
                label = "To",
                address = transaction.toAddress,
                chainType = chainType,
                isSender = false
            )
        }
    }
}

@Composable
fun AddressRow(
    label: String,
    address: String,
    chainType: ChainType,
    isSender: Boolean
) {
    val context = LocalContext.current

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
                    imageVector = when {
                        isSender -> Icons.Default.AccountBalanceWallet
                        chainType == ChainType.SOLANA -> Icons.Default.Star
                        chainType == ChainType.BITCOIN -> Icons.Default.CurrencyBitcoin
                        else -> Icons.Default.Person
                    },
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
                        when (chainType) {
                            ChainType.SOLANA -> "${address.take(8)}...${address.takeLast(8)}"
                            else -> "${address.take(8)}...${address.takeLast(8)}"
                        }
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

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Address", address)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }
        }
    }
}

@Composable
fun FeeDetailsSection(
    transaction: SendTransaction,
    chainType: ChainType
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
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Network Fee
            DetailRow(
                label = "Network Fee",
                value = "${transaction.feeDecimal} ${getTokenSymbol(transaction, chainType)}"
            )

            when (chainType) {
                ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> {
                    transaction.gasPrice?.let { gasPrice ->
                        DetailRow(
                            label = "Gas Price",
                            value = "$gasPrice Gwei"
                        )
                    }
                    transaction.gasLimit?.let { gasLimit ->
                        DetailRow(
                            label = "Gas Limit",
                            value = gasLimit
                        )
                    }
                    transaction.nonce?.let { nonce ->
                        DetailRow(
                            label = "Nonce",
                            value = nonce.toString()
                        )
                    }
                }
                ChainType.SOLANA -> {
                    transaction.metadata?.get("blockhash")?.let { blockhash ->
                        DetailRow(
                            label = "Blockhash",
                            value = "${blockhash.take(8)}..."
                        )
                    }
                }
                ChainType.BITCOIN -> {
                    transaction.metadata?.get("feePerByte")?.let { feePerByte ->
                        DetailRow(
                            label = "Fee Rate",
                            value = "$feePerByte sat/byte"
                        )
                    }
                    transaction.metadata?.get("estimatedSize")?.let { size ->
                        DetailRow(
                            label = "Size",
                            value = "$size bytes"
                        )
                    }
                }
                else -> {}
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
fun TransactionFinalStatusSection(
    confirmed: Boolean,
    txHash: String?,
    chainType: ChainType
) {
    val context = LocalContext.current

    val (color, text, icon) = if (confirmed) {
        Triple(Color.Green, "Confirmed on chain", Icons.Default.CheckCircle)
    } else {
        Triple(Color.Yellow, "Sent (pending confirmation)", Icons.Default.Schedule)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }

            if (txHash != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View on ${getExplorerName(chainType)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val url = getExplorerUrl(chainType, txHash)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.OpenInBrowser, "View")
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionStepStatus(
    currentStep: TransactionReviewViewModel.TransactionStep,
    chainType: ChainType
) {
    val (icon, text, color) = when (currentStep) {
        is TransactionReviewViewModel.TransactionStep.LOADING ->
            Triple(null, "Loading transaction...", MaterialTheme.colorScheme.primary)
        is TransactionReviewViewModel.TransactionStep.REVIEWING ->
            Triple(Icons.Default.Visibility, "Review transaction details", Color.Blue)
        is TransactionReviewViewModel.TransactionStep.APPROVING ->
            Triple(null, "Approving transaction...", Color.Cyan)
        is TransactionReviewViewModel.TransactionStep.SIGNING ->
            Triple(null, "Signing transaction...", Color.Yellow)
        is TransactionReviewViewModel.TransactionStep.BROADCASTING ->
            Triple(null, "Broadcasting to ${chainType.displayName} network...", Color.Magenta)
        is TransactionReviewViewModel.TransactionStep.CHECKING_STATUS ->
            Triple(null, "Confirming on blockchain...", Color.DarkGray)
        is TransactionReviewViewModel.TransactionStep.SUCCESS ->
            Triple(Icons.Default.CheckCircle, "Transaction successful!", Color.Green)
        is TransactionReviewViewModel.TransactionStep.ERROR ->
            Triple(Icons.Default.Error, "Error: ${currentStep.message}", Color.Red)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, "Step", tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = color
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
fun ApprovalBottomBar(
    chainType: ChainType,
    onApprove: () -> Unit
) {
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
                    imageVector = when (chainType) {
                        ChainType.BITCOIN -> Icons.Default.CurrencyBitcoin
                        ChainType.SOLANA -> Icons.Default.Star
                        else -> Icons.Default.Send
                    },
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send ${chainType.displayName} Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DoneBottomBar(
    chainType: ChainType,
    txHash: String? = null,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (txHash != null) {
                Button(
                    onClick = {
                        val url = getExplorerUrl(chainType, txHash)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "View"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View on ${getExplorerName(chainType)}")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
fun BroadcastResultSection(
    broadcastResult: BroadcastResult,
    chainType: ChainType
) {
    val (color, icon, title) = if (broadcastResult.success) {
        Triple(Color.Green, Icons.Default.CheckCircle, "Transaction Broadcasted!")
    } else {
        Triple(Color.Red, Icons.Default.Error, "Broadcast Failed")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Broadcast Result",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
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
fun CheckingStatusSection(
    chainType: ChainType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
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
            Text("Confirming on ${chainType.displayName}...")
        }
    }
}

// ===== HELPER FUNCTIONS =====

private fun getTokenSymbol(transaction: SendTransaction, chainType: ChainType): String {
    return when {
        transaction.metadata?.get("token") == "USDC" -> "USDC"
        chainType == ChainType.BITCOIN -> "BTC"
        chainType == ChainType.SOLANA -> "SOL"
        chainType == ChainType.ETHEREUM_SEPOLIA -> "ETH (Sepolia)"
        chainType == ChainType.ETHEREUM -> "ETH"
        else -> "TOK"
    }
}

private fun getExplorerName(chainType: ChainType): String {
    return when (chainType) {
        ChainType.BITCOIN -> "Blockstream"
        ChainType.ETHEREUM -> "Etherscan"
        ChainType.ETHEREUM_SEPOLIA -> "Sepolia Etherscan"
        ChainType.SOLANA -> "Solscan"
        else -> "Explorer"
    }
}

private fun getExplorerUrl(chainType: ChainType, txHash: String): String {
    return when (chainType) {
        ChainType.BITCOIN -> "https://blockstream.info/tx/$txHash"
        ChainType.ETHEREUM -> "https://etherscan.io/tx/$txHash"
        ChainType.ETHEREUM_SEPOLIA -> "https://sepolia.etherscan.io/tx/$txHash"
        ChainType.SOLANA -> "https://solscan.io/tx/$txHash"
        else -> "https://etherscan.io/tx/$txHash"
    }
}

val ChainType.displayName: String
    get() = when (this) {
        ChainType.BITCOIN -> "Bitcoin"
        ChainType.ETHEREUM -> "Ethereum"
        ChainType.ETHEREUM_SEPOLIA -> "Ethereum (Sepolia)"
        ChainType.SOLANA -> "Solana"
        else -> "Unknown"
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