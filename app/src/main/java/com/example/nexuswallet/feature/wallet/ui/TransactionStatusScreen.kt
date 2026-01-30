package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.NavigationViewModel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletType
import com.example.nexuswallet.formatDate
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionStatusScreen(
    navController: NavController,
    transactionId: String,
    viewModel: TransactionStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTransaction(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Status") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingScreen()
        } else {
            uiState.transaction?.let { transaction ->
                TransactionStatusContent(
                    transaction = transaction,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } ?: run {
                EmptyTransactionView()
            }
        }
    }
}

@Composable
fun TransactionStatusContent(
    transaction: SendTransaction,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Status Icon
        when (transaction.status) {
            TransactionStatus.SUCCESS -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.Green,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Transaction Successful!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            }

            TransactionStatus.PENDING -> {
                CircularProgressIndicator(
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Transaction Pending",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            TransactionStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = Color.Red,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Transaction Failed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transaction Details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Amount
                Text(
                    text = transaction.amountDecimal,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${transaction.walletType.name.take(3).uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Divider()

                Spacer(modifier = Modifier.height(16.dp))

                // Details
                StatusDetailRow(
                    label = "Transaction Hash",
                    value = transaction.hash ?: "Pending",
                    monospace = true
                )

                StatusDetailRow(
                    label = "From",
                    value = transaction.fromAddress,
                    monospace = true
                )

                StatusDetailRow(
                    label = "To",
                    value = transaction.toAddress,
                    monospace = true
                )

                StatusDetailRow(
                    label = "Fee",
                    value = "${transaction.feeDecimal} ${transaction.walletType.name.take(3).uppercase()}"
                )

                StatusDetailRow(
                    label = "Date",
                    value = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
                        .format(Date(transaction.timestamp))
                )

                transaction.note?.let { note ->
                    StatusDetailRow(
                        label = "Note",
                        value = note
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (transaction.hash != null) {
                Button(
                    onClick = { /* View on explorer */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Visibility, "View")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View on Explorer")
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { /* Copy details */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.ContentCopy, "Copy")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Details")
            }
        }
    }
}

@Composable
fun StatusDetailRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            style = if (monospace) MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ) else MaterialTheme.typography.bodyMedium,
            fontWeight = if (monospace) FontWeight.Normal else FontWeight.Medium,
            maxLines = if (monospace) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}