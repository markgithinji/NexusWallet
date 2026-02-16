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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
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
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.formatDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    navController: NavController,
    walletId: String,
    coinType: String, // "BTC", "ETH", "SOL", "USDC"
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val coinDetailState by viewModel.coinDetailState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, coinType)
    }

    if (isLoading) {
        LoadingScreen()
        return
    }

    error?.let {
        ErrorScreen(
            message = it,
            onRetry = { viewModel.loadCoinDetails(walletId, coinType) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (coinType) {
                                "BTC" -> Icons.Default.CurrencyBitcoin
                                "ETH" -> Icons.Default.CurrencyExchange
                                "SOL" -> Icons.Default.FlashOn
                                "USDC" -> Icons.Default.AttachMoney
                                else -> Icons.Default.AccountBalanceWallet
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when (coinType) {
                                "BTC" -> Color(0xFFF7931A)
                                "ETH" -> Color(0xFF627EEA)
                                "SOL" -> Color(0xFF00FFA3)
                                "USDC" -> Color(0xFF2775CA)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${coinType} Wallet")
                    }
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
        coinDetailState?.let { state ->
            CoinDetailContent(
                state = state,
                coinType = coinType,
                navController = navController,
                viewModel = viewModel,
                padding = padding
            )
        }
    }
}

@Composable
fun CoinDetailContent(
    state: CoinDetailViewModel.CoinDetailState,
    coinType: String,
    navController: NavController,
    viewModel: CoinDetailViewModel,
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
        // Balance Card
        CoinBalanceCard(
            coinType = coinType,
            balance = state.balance,
            balanceFormatted = state.balanceFormatted,
            address = state.address,
            network = state.network,
            usdValue = state.usdValue
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        CoinActionsCard(
            coinType = coinType,
            onReceive = {
                navController.navigate("receive/${state.walletId}?coinType=$coinType")
            },
            onSend = {
                navController.navigate("send/${state.walletId}/$coinType")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Address Card
        AddressCardWithQR(
            address = state.address.take(12) + "..." + state.address.takeLast(8),
            fullAddress = state.address,
            onCopy = { address ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Address", address)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
            },
            onShowFullQR = {
                navController.navigate("qrCode/${state.walletId}?coinType=$coinType")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ETH Gas Balance for USDC
        if (coinType == "USDC" && state.ethGasBalance != null) {
            EthGasBalanceCard(ethBalance = state.ethGasBalance)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recent Transactions
        CoinTransactionsSection(
            transactions = transactions,
            coinType = coinType,
            onViewAll = {
                navController.navigate("transactions/${state.walletId}?coinType=$coinType")
            }
        )
    }
}

@Composable
fun CoinBalanceCard(
    coinType: String,
    balance: String,
    balanceFormatted: String,
    address: String,
    network: String,
    usdValue: Double
) {
    val (coinColor, icon) = when (coinType) {
        "BTC" -> Pair(Color(0xFFF7931A), Icons.Default.CurrencyBitcoin)
        "ETH" -> Pair(Color(0xFF627EEA), Icons.Default.CurrencyExchange)
        "SOL" -> Pair(Color(0xFF00FFA3), Icons.Default.FlashOn)
        "USDC" -> Pair(Color(0xFF2775CA), Icons.Default.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.AccountBalanceWallet)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = coinColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(coinColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = coinType,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = coinType,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (network != "MAINNET" && network != "Mainnet") {
                        Text(
                            text = network,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$${String.format("%.2f", usdValue)}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = balanceFormatted,
                style = MaterialTheme.typography.titleMedium,
                color = coinColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address.take(12) + "..." + address.takeLast(8),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CoinActionsCard(
    coinType: String,
    onReceive: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
        }
    }
}

@Composable
fun CoinTransactionsSection(
    transactions: List<Any>,
    coinType: String,
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

                if (transactions.isNotEmpty()) {
                    TextButton(onClick = onViewAll) {
                        Text("View All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                EmptyTransactionsView()
            } else {
                transactions.take(3).forEachIndexed { index, transaction ->
                    CoinTransactionItem(
                        transaction = transaction,
                        coinType = coinType
                    )
                    if (index < 2) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun CoinTransactionItem(
    transaction: Any,
    coinType: String
) {
    val transactionInfo = when (transaction) {
        is BitcoinTransaction -> {
            TransactionInfo(
                isIncoming = transaction.toAddress == transaction.fromAddress,
                amount = transaction.amountBtc,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is EthereumTransaction -> {
            TransactionInfo(
                isIncoming = transaction.toAddress == transaction.fromAddress,
                amount = transaction.amountEth,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is SolanaTransaction -> {
            TransactionInfo(
                isIncoming = transaction.toAddress == transaction.fromAddress,
                amount = transaction.amountSol,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.signature
            )
        }
        is USDCSendTransaction -> {
            TransactionInfo(
                isIncoming = transaction.toAddress == transaction.fromAddress,
                amount = transaction.amountDecimal,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        else -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (transactionInfo.status) {
                        TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                        TransactionStatus.PENDING -> Color(0xFFFFA500).copy(alpha = 0.1f)
                        TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (transactionInfo.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = if (transactionInfo.isIncoming) "Received" else "Sent",
                tint = when (transactionInfo.status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color(0xFFFFA500)
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (transactionInfo.isIncoming) "Received $coinType" else "Sent $coinType",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTimestamp(transactionInfo.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${if (transactionInfo.isIncoming) "+" else "-"}${transactionInfo.amount} $coinType",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (transactionInfo.isIncoming) Color.Green else MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (transactionInfo.status) {
                            TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                            TransactionStatus.PENDING -> Color(0xFFFFA500).copy(alpha = 0.1f)
                            TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = transactionInfo.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (transactionInfo.status) {
                        TransactionStatus.SUCCESS -> Color.Green
                        TransactionStatus.PENDING -> Color(0xFFFFA500)
                        TransactionStatus.FAILED -> Color.Red
                    }
                )
            }
        }
    }
}

data class TransactionInfo(
    val isIncoming: Boolean,
    val amount: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val hash: String?
)
