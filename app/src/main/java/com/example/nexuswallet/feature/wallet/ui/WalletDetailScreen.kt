package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.market.ui.formatTwoDecimals
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToCoinDetail: (String, CoinType) -> Unit,
    onNavigateToReceive: (String, CoinType) -> Unit,
    onNavigateToSend: (String, CoinType) -> Unit,
    onNavigateToAllTransactions: (String) -> Unit,
    walletId: String,
    walletViewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsState()

    // Load wallet when screen is first composed
    LaunchedEffect(walletId) {
        walletViewModel.loadWallet(walletId)
    }

    if (uiState.isLoading && uiState.wallet == null) {
        LoadingScreen()
        return
    }

    uiState.error?.let {
        ErrorScreen(
            message = it,
            onRetry = { walletViewModel.loadWallet(walletId) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = walletViewModel.getWalletName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { walletViewModel.refresh() },
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
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
        uiState.wallet?.let { currentWallet ->
            WalletDetailContent(
                wallet = currentWallet,
                balance = uiState.balance,
                transactions = uiState.transactions,
                pricePercentages = uiState.pricePercentages,
                totalUsdValue = walletViewModel.getTotalUsdValue(),
                onRefresh = { walletViewModel.refresh() },
                onNavigateToCoinDetail = onNavigateToCoinDetail,
                onNavigateToReceive = onNavigateToReceive,
                onNavigateToSend = onNavigateToSend,
                onNavigateToAllTransactions = onNavigateToAllTransactions,
                onSwap = { /* Handle swap action */ },
                onMore = { /* Handle more actions */ },
                padding = padding
            )
        } ?: run {
            EmptyWalletView(
                onBack = onNavigateUp
            )
        }
    }
}

@Composable
fun WalletDetailContent(
    wallet: Wallet,
    balance: WalletBalance?,
    transactions: List<Any>,
    pricePercentages: Map<String, Double>,
    totalUsdValue: Double,
    onRefresh: () -> Unit,
    onNavigateToCoinDetail: (String, CoinType) -> Unit,
    onNavigateToReceive: (String, CoinType) -> Unit,
    onNavigateToSend: (String, CoinType) -> Unit,
    onNavigateToAllTransactions: (String) -> Unit,
    onSwap: () -> Unit,
    onMore: () -> Unit,
    padding: PaddingValues
) {
    val totalFormatted = NumberFormat.getCurrencyInstance(Locale.US).format(totalUsdValue)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Wallet Header Card
        item {
            WalletHeaderCard(
                wallet = wallet,
                totalBalance = totalFormatted,
                balance = balance
            )
        }

        // Quick Actions
        item {
            QuickActionsRow(
                onReceive = { onNavigateToReceive(wallet.id, CoinType.BITCOIN) },
                onSend = { onNavigateToSend(wallet.id, CoinType.BITCOIN) },
                onSwap = onSwap,
                onMore = onMore
            )
        }

        // Assets Section
        item {
            SectionHeader(title = "Assets")
        }

        wallet.bitcoin?.let { coin ->
            val percentage = pricePercentages["bitcoin"]
            item(key = "btc_${pricePercentages.hashCode()}") {
                CoinDetailCard(
                    coinType = "BTC",
                    coin = coin,
                    balance = balance?.bitcoin,
                    color = bitcoinLight,
                    icon = Icons.Outlined.CurrencyBitcoin,
                    onClick = { onNavigateToCoinDetail(wallet.id, CoinType.BITCOIN) },
                    priceChangePercentage = percentage
                )
            }
        }

        wallet.ethereum?.let { coin ->
            val percentage = pricePercentages["ethereum"]
            item(key = "eth_${pricePercentages.hashCode()}") {
                CoinDetailCard(
                    coinType = "ETH",
                    coin = coin,
                    balance = balance?.ethereum,
                    color = ethereumLight,
                    icon = Icons.Outlined.Diamond,
                    onClick = { onNavigateToCoinDetail(wallet.id, CoinType.ETHEREUM) },
                    priceChangePercentage = percentage
                )
            }
        }

        wallet.solana?.let { coin ->
            val percentage = pricePercentages["solana"]
            item(key = "sol_${pricePercentages.hashCode()}") {
                CoinDetailCard(
                    coinType = "SOL",
                    coin = coin,
                    balance = balance?.solana,
                    color = solanaLight,
                    icon = Icons.Outlined.FlashOn,
                    onClick = { onNavigateToCoinDetail(wallet.id, CoinType.SOLANA) },
                    priceChangePercentage = percentage
                )
            }
        }

        wallet.usdc?.let { coin ->
            val percentage = pricePercentages["usd-coin"]
            item(key = "usdc_${pricePercentages.hashCode()}") {
                CoinDetailCard(
                    coinType = "USDC",
                    coin = coin,
                    balance = balance?.usdc,
                    color = usdcLight,
                    icon = Icons.Outlined.AttachMoney,
                    onClick = { onNavigateToCoinDetail(wallet.id, CoinType.USDC) },
                    priceChangePercentage = percentage
                )
            }
        }

        // Transactions Section
        if (transactions.isNotEmpty()) {
            item {
                TransactionsContainer(
                    transactions = transactions,
                    wallet = wallet,
                    onViewAll = { onNavigateToAllTransactions(wallet.id) }
                )
            }
        } else {
            item {
                EmptyTransactionsView()
            }
        }
    }
}

@Composable
fun WalletHeaderCard(
    wallet: Wallet,
    totalBalance: String,
    balance: WalletBalance?
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Wallet icon and name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Multi-currency wallet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider
            Divider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Balance
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = totalBalance,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Assets count only
            val assetCount = listOfNotNull(
                wallet.bitcoin,
                wallet.ethereum,
                wallet.solana,
                wallet.usdc
            ).size

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$assetCount assets",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun TransactionsContainer(
    transactions: List<Any>,
    wallet: Wallet,
    onViewAll: () -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with "Recent Transactions" and "See All" button
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
                        text = "See All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction list
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                transactions.take(3).forEachIndexed { index, transaction ->
                    TransactionItem(
                        transaction = transaction,
                        wallet = wallet
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
fun StatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsRow(
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onSwap: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionItem(
            icon = Icons.Outlined.ArrowDownward,
            label = "Receive",
            onClick = onReceive,
            color = MaterialTheme.colorScheme.primary
        )
        QuickActionItem(
            icon = Icons.Outlined.ArrowUpward,
            label = "Send",
            onClick = onSend,
            color = MaterialTheme.colorScheme.success
        )
        QuickActionItem(
            icon = Icons.Outlined.SwapHoriz,
            label = "Swap",
            onClick = onSwap,
            color = MaterialTheme.colorScheme.tertiary
        )
        QuickActionItem(
            icon = Icons.Outlined.MoreHoriz,
            label = "More",
            onClick = onMore,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CoinDetailCard(
    coinType: String,
    coin: Any,
    balance: Any?,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    priceChangePercentage: Double? = null
) {
    val (balanceAmount, usdValue) = when (balance) {
        is BitcoinBalance -> Pair(balance.btc, balance.usdValue)
        is EthereumBalance -> Pair(balance.eth, balance.usdValue)
        is SolanaBalance -> Pair(balance.sol, balance.usdValue)
        is USDCBalance -> Pair(balance.amountDecimal, balance.usdValue)
        else -> Pair("0", 0.0)
    }

    val formattedBalance = try {
        val amountDecimal = balanceAmount.toBigDecimal()
        when {
            amountDecimal < BigDecimal("0.000001") -> amountDecimal.setScale(
                8,
                RoundingMode.HALF_UP
            ).stripTrailingZeros().toPlainString()

            amountDecimal < BigDecimal("0.001") -> amountDecimal.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()

            amountDecimal < BigDecimal("1") -> amountDecimal.setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()

            else -> amountDecimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString()
        }
    } catch (e: Exception) {
        balanceAmount
    }

    val formattedUsd = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coin icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = coinType,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Coin info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = coinType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$formattedBalance $coinType",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // USD Value and percentage
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formattedUsd,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // Show percentage if available
                if (priceChangePercentage != null) {
                    val changeColor = if (priceChangePercentage >= 0)
                        MaterialTheme.colorScheme.success
                    else
                        MaterialTheme.colorScheme.error

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (priceChangePercentage >= 0)
                                Icons.Outlined.TrendingUp
                            else
                                Icons.Outlined.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = changeColor
                        )
                        Text(
                            text = "${if (priceChangePercentage >= 0) "+" else ""}${priceChangePercentage.formatTwoDecimals()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = changeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Any,
    wallet: Wallet
) {
    val (isIncoming, amount, symbol, status, timestamp) = when (transaction) {
        is BitcoinTransaction -> {
            val isIncoming = transaction.toAddress == wallet.bitcoin?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountBtc,
                symbol = "BTC",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is EthereumTransaction -> {
            val isIncoming = transaction.toAddress == wallet.ethereum?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountEth,
                symbol = "ETH",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is SolanaTransaction -> {
            val isIncoming = transaction.toAddress == wallet.solana?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountSol,
                symbol = "SOL",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is USDCTransaction -> {
            val isIncoming = transaction.toAddress == wallet.usdc?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountDecimal,
                symbol = "USDC",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        else -> return
    }

    // Format the amount to remove unnecessary zeros
    val formattedAmount = try {
        val amountDecimal = amount.toBigDecimal()

        // For very small amounts, show up to 6 decimal places
        val maxDecimals = when {
            amountDecimal < BigDecimal("0.000001") -> 8
            amountDecimal < BigDecimal("0.001") -> 6
            amountDecimal < BigDecimal("1") -> 4
            else -> 2
        }

        amountDecimal.stripTrailingZeros().let {
            if (it.scale() > maxDecimals) {
                it.setScale(maxDecimals, RoundingMode.HALF_UP)
            } else it
        }.toPlainString()
    } catch (e: Exception) {
        amount
    }

    val (statusColor, statusBgColor) = when (status) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
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
                    imageVector = if (isIncoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                    contentDescription = if (isIncoming) "Received" else "Sent",
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
                    text = if (isIncoming) "Received" else "Sent",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = formatTimestamp(timestamp),
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
                    text = "${if (isIncoming) "+" else "-"}$formattedAmount $symbol",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncoming) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurface,
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
                        text = status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Helper function to format timestamp
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
        else -> {
            val date = Date(timestamp)
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}

// Helper data class for transaction display
data class TransactionDisplayData(
    val isIncoming: Boolean,
    val amount: String,
    val symbol: String,
    val status: TransactionStatus,
    val timestamp: Long
)

@Composable
fun EmptyTransactionsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Receipt,
            contentDescription = "No Transactions",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No Transactions Yet",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Your transactions will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyWalletView(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Wallet Not Found",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Wallet Not Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The wallet you're looking for doesn't exist or has been deleted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Back to Wallets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}