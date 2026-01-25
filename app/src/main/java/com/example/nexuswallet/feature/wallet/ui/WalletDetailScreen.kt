package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.data.model.BitcoinWallet
import com.example.nexuswallet.data.model.CryptoWallet
import com.example.nexuswallet.data.model.EthereumWallet
import com.example.nexuswallet.data.model.MultiChainWallet
import com.example.nexuswallet.data.model.SolanaWallet
import com.example.nexuswallet.data.model.Transaction
import com.example.nexuswallet.data.model.TransactionStatus
import com.example.nexuswallet.data.model.WalletBalance
import com.example.nexuswallet.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    navController: NavController,
    viewModel: WalletDetailViewModel
) {
    val wallet by viewModel.wallet.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Track if we've checked authentication
    var hasCheckedAuth by remember { mutableStateOf(false) }
    val securityManager = NexusWalletApplication.Companion.instance.securityManager

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.getWalletName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LaunchedEffect(wallet?.id) {
            if (wallet?.id != null && !hasCheckedAuth) {
                Log.d("WalletDetail", " Checking auth for wallet ${wallet!!.id}")

                val isAuthRequired = securityManager.isAuthenticationRequired(AuthAction.VIEW_WALLET)
                Log.d("WalletDetail", " Auth required: $isAuthRequired")

                if (isAuthRequired) {
                    Log.d("WalletDetail", " Navigating to auth screen")
                    navController.navigate("authenticate/walletDetail/${wallet!!.id}")
                } else {
                    Log.d("WalletDetail", " No auth required, showing wallet")
                }
                hasCheckedAuth = true
            }
        }

        if (isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        error?.let {
            ErrorScreen(
                message = it,
                onRetry = { viewModel.refresh() }
            )
            return@Scaffold
        }

        wallet?.let { currentWallet ->
            WalletDetailContent(
                wallet = currentWallet,
                balance = balance,
                navController = navController,
                viewModel = viewModel,
                padding = padding
            )
        } ?: run {
            EmptyWalletView(
                onBack = { navController.navigateUp() }
            )
        }
    }
}

@Composable
fun WalletDetailContent(
    wallet: CryptoWallet,
    balance: WalletBalance?,
    navController: NavController,
    viewModel: WalletDetailViewModel,
    padding: PaddingValues
) {
    val transactions by viewModel.transactions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Wallet Header
        WalletHeaderCard(wallet = wallet, balance = balance)

        Spacer(modifier = Modifier.height(16.dp))

        WalletActionsCard(
            wallet = wallet,
            onReceive = {
                // Show receive screen (no auth needed)
                navController.navigate("receive/${wallet.id}")
            },
            onSend = {
                // Navigate directly to authentication for send
                navController.navigate("authenticate/send/${wallet.id}?action=SEND_TRANSACTION")
            },
            onBackup = {
                // Navigate directly to authentication for backup
                navController.navigate("authenticate/backup/${wallet.id}?action=BACKUP_WALLET")
            },
            onAddSampleTransaction = {
                viewModel.addSampleTransaction()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Address Card
        AddressCard(address = viewModel.getDisplayAddress())

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Transactions
        TransactionsSection(
            transactions = transactions,
            onViewAll = { /* TODO */ }
        )
    }
}

@Composable
fun WalletHeaderCard(wallet: CryptoWallet, balance: WalletBalance?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                WalletIcon(wallet = wallet)

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getWalletTypeDisplay(wallet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$${balance?.usdValue?.let { String.format("%.2f", it) } ?: "0.00"}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            balance?.let {
                Text(
                    text = "${it.nativeBalanceDecimal} ${getNativeSymbol(wallet)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun WalletActionsCard(
    wallet: CryptoWallet,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onBackup:  () -> Unit,
    onAddSampleTransaction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = Icons.Default.ArrowDownward,
                label = "Receive",
                onClick = onReceive
            )

            ActionButton(
                icon = Icons.Default.ArrowUpward,
                label = "Send",
                onClick = onSend
            )

            ActionButton(
                icon = Icons.Default.Backup,
                label = "Backup",
                onClick = onBackup
            )

            ActionButton(
                icon = Icons.Default.Add,
                label = "Sample",
                onClick = onAddSampleTransaction
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun AddressCard(address: String) {
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
                text = "Wallet Address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { /* Copy to clipboard */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Copy Address")
            }
        }
    }
}

@Composable
fun TransactionsSection(
    transactions: List<Transaction>,
    onViewAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                EmptyTransactionsView()
            } else {
                transactions.take(3).forEach { transaction ->
                    TransactionItem(transaction = transaction)
                    if (transactions.indexOf(transaction) < 2) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    color = when (transaction.status) {
                        TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                        TransactionStatus.PENDING -> Color.Yellow.copy(alpha = 0.1f)
                        TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if ((transaction.value.toDoubleOrNull() ?: 0.0) > 0) {
                    Icons.Default.ArrowDownward
                } else {
                    Icons.Default.ArrowUpward
                },
                contentDescription = "Transaction",
                tint = when (transaction.status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color.Yellow
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (transaction.value.toDoubleOrNull() ?: 0.0 > 0) "Received" else "Sent",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDate(transaction.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${transaction.valueDecimal} ${transaction.chain.name.take(3)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = transaction.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = when (transaction.status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color.Yellow
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }
    }
}

@Composable
fun EmptyWalletView(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Wallet Not Found",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wallet Not Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The wallet you're looking for doesn't exist or has been deleted",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back to Wallets")
        }
    }
}

@Composable
fun EmptyTransactionsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = "No Transactions",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No Transactions Yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Your transaction history will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helper functions
fun getWalletTypeDisplay(wallet: CryptoWallet): String {
    return when (wallet) {
        is BitcoinWallet -> "Bitcoin Wallet"
        is EthereumWallet -> "Ethereum Wallet"
        is MultiChainWallet -> "Multi-Chain Wallet"
        is SolanaWallet -> "Solana Wallet"
        else -> "Crypto Wallet"
    }
}

fun getNativeSymbol(wallet: CryptoWallet): String {
    return when (wallet) {
        is BitcoinWallet -> "BTC"
        is EthereumWallet -> "ETH"
        is MultiChainWallet -> "MULTI"
        is SolanaWallet -> "SOL"
        else -> "CRYPTO"
    }
}