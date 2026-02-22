package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    navController: NavController,
    walletId: String,
    coinType: String, // "BTC", "ETH", "SOL", "USDC"
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val coinDetailState by viewModel.coinDetailState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, coinType)
    }

    if (isLoading && coinDetailState == null) {
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
                                "BTC" -> Icons.Outlined.CurrencyBitcoin
                                "ETH" -> Icons.Outlined.Diamond
                                "SOL" -> Icons.Outlined.FlashOn
                                "USDC" -> Icons.Outlined.AttachMoney
                                else -> Icons.Outlined.AccountBalanceWallet
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
                        Text(
                            text = "$coinType Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.Black
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                "Refresh",
                                tint = Color.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F7)
    ) { padding ->
        coinDetailState?.let { state ->
            val context = LocalContext.current

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Balance Card
                item {
                    CoinBalanceCard(
                        coinType = coinType,
                        balance = state.balance,
                        balanceFormatted = state.balanceFormatted,
                        address = state.address,
                        network = state.network,
                        usdValue = state.usdValue,
                        onCopyAddress = { address ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Address", address)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Actions
                item {
                    CoinActionsCard(
                        coinType = coinType,
                        onReceive = {
                            navController.navigate("receive/${state.walletId}?coinType=$coinType")
                        },
                        onSend = {
                            navController.navigate("send/${state.walletId}/$coinType")
                        }
                    )
                }

                // ETH Gas Balance for USDC
                if (coinType == "USDC" && state.ethGasBalance != null) {
                    item {
                        EthGasBalanceCard(ethBalance = state.ethGasBalance)
                    }
                }

                // Recent Transactions
                item {
                    TransactionsContainer(
                        transactions = transactions,
                        coinType = coinType,
                        walletAddress = state.address,
                        onViewAll = {
                            navController.navigate("transactions/${state.walletId}?coinType=$coinType")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CoinBalanceCard(
    coinType: String,
    balance: String,
    balanceFormatted: String,
    address: String,
    network: String,
    usdValue: Double,
    onCopyAddress: (String) -> Unit
) {
    val (coinColor, icon) = when (coinType) {
        "BTC" -> Pair(Color(0xFFF7931A), Icons.Outlined.CurrencyBitcoin)
        "ETH" -> Pair(Color(0xFF627EEA), Icons.Outlined.Diamond)
        "SOL" -> Pair(Color(0xFF00FFA3), Icons.Outlined.FlashOn)
        "USDC" -> Pair(Color(0xFF2775CA), Icons.Outlined.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.AccountBalanceWallet)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with coin icon, name, and address with copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Coin icon and name
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(coinColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = coinType,
                            tint = coinColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = coinType,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        if (network != "MAINNET" && network != "Mainnet") {
                            Text(
                                text = network,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                // Address with copy icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = address.take(6) + "..." + address.takeLast(4),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = { onCopyAddress(address) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(
                color = Color(0xFFE5E7EB),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )

            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = balanceFormatted,
                style = MaterialTheme.typography.bodyLarge,
                color = coinColor
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionItem(
                icon = Icons.Outlined.ArrowDownward,
                label = "Receive",
                onClick = onReceive,
                color = Color(0xFF10B981)
            )

            QuickActionItem(
                icon = Icons.Outlined.ArrowUpward,
                label = "Send",
                onClick = onSend,
                color = Color(0xFF3B82F6)
            )
        }
    }
}

@Composable
fun EthGasBalanceCard(ethBalance: BigDecimal?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF627EEA).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalGasStation,
                    contentDescription = "Gas",
                    tint = Color(0xFF627EEA),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ETH for Gas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = "${ethBalance?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: "0"} ETH",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
fun TransactionsContainer(
    transactions: List<Any>,
    coinType: String,
    walletAddress: String,
    onViewAll: () -> Unit
) {
    if (transactions.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            EmptyTransactionsView()
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with "Recent Transactions" and "View All" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction list
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                transactions.take(3).forEachIndexed { index, transaction ->
                    CoinTransactionItem(
                        transaction = transaction,
                        coinType = coinType,
                        walletAddress = walletAddress
                    )

                    if (index < 2) {
                        Divider(
                            color = Color(0xFFE5E7EB),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoinTransactionItem(
    transaction: Any,
    coinType: String,
    walletAddress: String
) {
    val transactionInfo = when (transaction) {
        is BitcoinTransaction -> {
            TransactionInfo(
                isIncoming = transaction.isIncoming,
                amount = transaction.amountBtc,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is EthereumTransaction -> {
            TransactionInfo(
                isIncoming = transaction.isIncoming,
                amount = transaction.amountEth,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is SolanaTransaction -> {
            TransactionInfo(
                isIncoming = transaction.isIncoming,
                amount = transaction.amountSol,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.signature
            )
        }
        is USDCSendTransaction -> {
            TransactionInfo(
                isIncoming = transaction.isIncoming,
                amount = transaction.amountDecimal,
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        else -> return
    }

    // Format amount to remove unnecessary zeros
    val formattedAmount = try {
        val amountDecimal = transactionInfo.amount.toBigDecimal()
        when {
            amountDecimal < BigDecimal("0.000001") -> amountDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            amountDecimal < BigDecimal("0.001") -> amountDecimal.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            amountDecimal < BigDecimal("1") -> amountDecimal.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            else -> amountDecimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    } catch (e: Exception) {
        transactionInfo.amount
    }

    val (statusColor, statusBgColor) = when (transactionInfo.status) {
        TransactionStatus.SUCCESS -> Pair(Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.1f))
        TransactionStatus.PENDING -> Pair(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.1f))
        TransactionStatus.FAILED -> Pair(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.1f))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(statusBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (transactionInfo.isIncoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = if (transactionInfo.isIncoming) "Received" else "Sent",
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Transaction details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (transactionInfo.isIncoming) "Received $coinType" else "Sent $coinType",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1
            )
            Text(
                text = formatTimestamp(transactionInfo.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7280),
                maxLines = 1
            )
        }

        // Amount and status
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(min = 80.dp, max = 120.dp)
        ) {
            Text(
                text = "${if (transactionInfo.isIncoming) "+" else "-"}$formattedAmount $coinType",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transactionInfo.isIncoming) Color(0xFF10B981) else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusBgColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = transactionInfo.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1
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
