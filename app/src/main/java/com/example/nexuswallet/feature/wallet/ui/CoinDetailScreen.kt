package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReceive: (String, CoinType) -> Unit,
    onNavigateToSend: (String, CoinType) -> Unit,
    onNavigateToAllTransactions: (String, CoinType) -> Unit,
    walletId: String,
    coinType: CoinType,
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, coinType)
    }

    // Show loading only on initial load
    if (state.isLoading && state.address.isEmpty()) {
        LoadingScreen()
        return
    }

    // Show error if present
    state.error?.let { errorMessage ->
        ErrorScreen(
            message = errorMessage,
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
                                CoinType.BITCOIN -> Icons.Outlined.CurrencyBitcoin
                                CoinType.ETHEREUM -> Icons.Outlined.Diamond
                                CoinType.SOLANA -> Icons.Outlined.FlashOn
                                CoinType.USDC -> Icons.Outlined.AttachMoney
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when (coinType) {
                                CoinType.BITCOIN -> bitcoinLight
                                CoinType.ETHEREUM -> ethereumLight
                                CoinType.SOLANA -> solanaLight
                                CoinType.USDC -> usdcLight
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (coinType) {
                                CoinType.BITCOIN -> "Bitcoin Wallet"
                                CoinType.ETHEREUM -> "Ethereum Wallet"
                                CoinType.SOLANA -> "Solana Wallet"
                                CoinType.USDC -> "USDC Wallet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
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
                        onNavigateToReceive(state.walletId, coinType)
                    },
                    onSend = {
                        onNavigateToSend(state.walletId, coinType)
                    }
                )
            }

            // ETH Gas Balance for USDC
            if (coinType == CoinType.USDC && state.ethGasBalance != null) {
                item {
                    EthGasBalanceCard(ethBalance = state.ethGasBalance)
                }
            }

            // Recent Transactions
            item {
                TransactionsContainer(
                    transactions = state.transactions,
                    coinType = coinType,
                    onViewAll = {
                        onNavigateToAllTransactions(state.walletId, coinType)
                    }
                )
            }
        }
    }
}

@Composable
fun CoinBalanceCard(
    coinType: CoinType,
    balance: String,
    balanceFormatted: String,
    address: String,
    network: String,
    usdValue: Double,
    onCopyAddress: (String) -> Unit
) {
    val (coinColor, icon) = when (coinType) {
        CoinType.BITCOIN -> Pair(bitcoinLight, Icons.Outlined.CurrencyBitcoin)
        CoinType.ETHEREUM -> Pair(ethereumLight, Icons.Outlined.Diamond)
        CoinType.SOLANA -> Pair(solanaLight, Icons.Outlined.FlashOn)
        CoinType.USDC -> Pair(usdcLight, Icons.Outlined.AttachMoney)
    }

    val displayName = when (coinType) {
        CoinType.BITCOIN -> "Bitcoin"
        CoinType.ETHEREUM -> "Ethereum"
        CoinType.SOLANA -> "Solana"
        CoinType.USDC -> "USD Coin"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                            contentDescription = displayName,
                            tint = coinColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (network != "MAINNET" && network != "Mainnet") {
                            Text(
                                text = network,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = { onCopyAddress(address) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
    coinType: CoinType,
    onReceive: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                color = MaterialTheme.colorScheme.success
            )

            QuickActionItem(
                icon = Icons.Outlined.ArrowUpward,
                label = "Send",
                onClick = onSend,
                color = MaterialTheme.colorScheme.primary
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
            containerColor = MaterialTheme.colorScheme.surface
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
                    .background(ethereumLight.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalGasStation,
                    contentDescription = "Gas",
                    tint = ethereumLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ETH for Gas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${ethBalance?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: "0"} ETH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionsContainer(
    transactions: List<TransactionDisplayInfo>,
    coinType: CoinType,
    onViewAll: () -> Unit
) {
    if (transactions.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
            containerColor = MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
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
                        coinType = coinType
                    )

                    if (index < 2) {
                        Divider(
                            color = MaterialTheme.colorScheme.outline,
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
    transaction: TransactionDisplayInfo,
    coinType: CoinType
) {
    val (symbol, displayName) = when (coinType) {
        CoinType.BITCOIN -> Pair("BTC", "Bitcoin")
        CoinType.ETHEREUM -> Pair("ETH", "Ethereum")
        CoinType.SOLANA -> Pair("SOL", "Solana")
        CoinType.USDC -> Pair("USDC", "USD Coin")
    }

    val (statusColor, statusBgColor) = when (transaction.status) {
        TransactionStatus.SUCCESS -> Pair(
            MaterialTheme.colorScheme.success,
            MaterialTheme.colorScheme.success.copy(alpha = 0.1f)
        )
        TransactionStatus.PENDING -> Pair(
            MaterialTheme.colorScheme.warning,
            MaterialTheme.colorScheme.warning.copy(alpha = 0.1f)
        )
        TransactionStatus.FAILED -> Pair(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        )
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
                imageVector = if (transaction.isIncoming)
                    Icons.Outlined.ArrowDownward
                else
                    Icons.Outlined.ArrowUpward,
                contentDescription = if (transaction.isIncoming) "Received" else "Sent",
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
                text = if (transaction.isIncoming) "Received $displayName" else "Sent $displayName",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = transaction.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Amount and status
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(min = 80.dp, max = 120.dp)
        ) {
            Text(
                text = "${if (transaction.isIncoming) "+" else "-"}${transaction.formattedAmount} $symbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.isIncoming) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurface,
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
                    text = transaction.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1
                )
            }
        }
    }
}