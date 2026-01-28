package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.NavigationViewModel
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.formatDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    navController: NavController,
    walletViewModel: WalletDetailViewModel = hiltViewModel(),
    blockchainViewModel: BlockchainViewModel = hiltViewModel()
) {
    val wallet by walletViewModel.wallet.collectAsState()
    val balance by walletViewModel.balance.collectAsState()
    val ethBalance by blockchainViewModel.ethBalance.collectAsState()
    val btcBalance by blockchainViewModel.btcBalance.collectAsState()
    val isLoading by blockchainViewModel.isLoading.collectAsState()
    val error by blockchainViewModel.error.collectAsState()

    LaunchedEffect(wallet) {
        wallet?.let { blockchainViewModel.fetchWalletData(it) }
    }

    // Show loading state while fetching blockchain data
    if (isLoading) {
        LoadingScreen()
        return
    }

    error?.let {
        ErrorScreen(
            message = it,
            onRetry = { wallet?.let { w -> blockchainViewModel.refresh(w) } }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = walletViewModel.getWalletName(),
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
                        onClick = {
                            wallet?.let { blockchainViewModel.refresh(it) }
                            walletViewModel.refresh()
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        wallet?.let { currentWallet ->
            WalletDetailContent(
                wallet = currentWallet,
                balance = balance,
                navController = navController,
                viewModel = walletViewModel,
                blockchainViewModel = blockchainViewModel,
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
fun DebugButton(walletId: String, navController: NavController) {

    Button(
        onClick = { navController.navigate("debug/$walletId") },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(Icons.Default.BugReport, "Debug")
        Spacer(modifier = Modifier.width(8.dp))
        Text("API Debug")
    }
}
@Composable
fun WalletDetailContent(
    wallet: CryptoWallet,
    balance: WalletBalance?,
    navController: NavController,
    viewModel: WalletDetailViewModel,
    blockchainViewModel: BlockchainViewModel,
    padding: PaddingValues
) {
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Wallet Header
        WalletHeaderCard(wallet = wallet, balance = balance)

        DebugButton(walletId = wallet.id, navController = navController)

        Spacer(modifier = Modifier.height(16.dp))

        BlockchainDataCard(
            wallet = wallet,
            blockchainViewModel = blockchainViewModel,
//            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        WalletActionsCard(
            wallet = wallet,
            onReceive = {
                navController.navigate("receive/${wallet.id}")
            },
            onSend = {
                navController.navigate("authenticate/send/${wallet.id}")
            },
            onBackup = {
                navController.navigate("authenticate/backup/${wallet.id}")
            },
            onAddSampleTransaction = {
                viewModel.addSampleTransaction()
            },
            onViewPrivateKey = {
                Toast.makeText(context, "Private key viewing requires authentication", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Address Card with QR Code
        AddressCardWithQR(
            address = viewModel.getDisplayAddress(),
            fullAddress = viewModel.getFullAddress(),
            onCopy = { address ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Wallet Address", address)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onShowFullQR = {
                // Navigate to full screen QR code
                navController.navigate("qrCode/${wallet.id}")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Transactions
        TransactionsSection(
            transactions = transactions,
            onViewAll = {
                // TODO: Navigate to full transactions screen
                Toast.makeText(context, "View all transactions", Toast.LENGTH_SHORT).show()
            }
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
                WalletIconDetailScreen(wallet = wallet)

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
    onBackup: () -> Unit,
    onAddSampleTransaction: () -> Unit,
    onViewPrivateKey: () -> Unit
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
            // Main actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Security actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Key,
                    label = "Private Key",
                    onClick = onViewPrivateKey
                )

                ActionButton(
                    icon = Icons.Default.Security,
                    label = "Security",
                    onClick = {
                        // Navigate to security settings
                    }
                )
            }
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
fun AddressCardWithQR(
    address: String,
    fullAddress: String,
    onCopy: (String) -> Unit,
    onShowFullQR: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wallet Address",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR Code Preview
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clickable { onShowFullQR() }
                    .clip(RoundedCornerShape(8.dp))
            ) {
                QrCodeDisplay(
                    address = fullAddress,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay for click hint
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "View Full QR",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onCopy(fullAddress) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onShowFullQR,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.QrCode2, "QR Code", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("QR Code")
                }
            }
        }
    }
}

@Composable
fun AddressCard(
    address: String,
    onCopy: (String) -> Unit
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
                onClick = { onCopy(address) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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

@Composable
fun WalletIconDetailScreen(wallet: CryptoWallet) {
    val iconSize = 48.dp
    val backgroundColor = when (wallet) {
        is BitcoinWallet -> Color(0xFFF7931A) // Bitcoin orange
        is EthereumWallet -> Color(0xFF627EEA) // Ethereum blue
        is SolanaWallet -> Color(0xFF00FFA3) // Solana green
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = wallet.name.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return format.format(date)
}