package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.ui.common.ErrorScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.TransactionItem
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight
import com.example.nexuswallet.ui.theme.warning
import java.text.NumberFormat
import java.util.Locale
import com.example.nexuswallet.feature.core.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAllTransactions: (String) -> Unit,
    onNavigateToTransactionDetail: (String, String, Coin) -> Unit,
    onReceiveClick: (String, Coin) -> Unit,
    onSendClick: (String, Coin) -> Unit,
    onAssetClick: (String, Coin) -> Unit,
    onSwapClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    walletId: String,
    walletViewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsStateWithLifecycle()

    var showAssetSelector by remember { mutableStateOf(false) }
    var selectorPurpose by remember { mutableStateOf(AssetSelectorPurpose.SEND) }

    LaunchedEffect(walletId) {
        walletViewModel.loadWallet(walletId)
    }

    // Show full screen loading only on initial load with no wallet
    if (uiState.isLoading && uiState.wallet == null) {
        FullScreenLoading(message = stringResource(R.string.loading_wallet))
        return
    }

    // Show error if present and no wallet
    uiState.error?.let {
        if (uiState.wallet == null) {
            ErrorScreen(
                message = it,
                onRetry = { walletViewModel.loadWallet(walletId) }
            )
            return
        }
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Show warning icon if there's a sync error
                    if (uiState.hasSyncError) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = "Sync error",
                                tint = MaterialTheme.colorScheme.warning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Refresh button
                    IconButton(
                        onClick = { walletViewModel.refresh() },
                        enabled = !uiState.isRefreshingBalance && !uiState.isRefreshingTransactions
                    ) {
                        if (uiState.isRefreshingBalance || uiState.isRefreshingTransactions) {
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
                assets = uiState.assets,
                transactions = uiState.transactions,
                totalBalanceFormatted = uiState.totalBalanceFormatted,
                hasSyncError = uiState.hasSyncError,
                isLoadingBalance = uiState.isLoadingBalance,
                isLoadingTransactions = uiState.isLoadingTransactions,
                isRefreshingBalance = uiState.isRefreshingBalance,
                isRefreshingTransactions = uiState.isRefreshingTransactions,
                balanceLoadingMessage = when {
                    uiState.isRefreshingBalance -> stringResource(R.string.updating_balances)
                    uiState.isLoadingBalance -> stringResource(R.string.loading_balances)
                    else -> ""
                },
                transactionsLoadingMessage = when {
                    uiState.isRefreshingTransactions -> stringResource(R.string.updating_transactions)
                    uiState.isLoadingTransactions -> stringResource(R.string.loading_transactions)
                    else -> ""
                },
                onAssetClick = { coin -> onAssetClick(walletId, coin) },
                onReceiveClick = { coin -> onReceiveClick(walletId, coin) },
                onSendClick = { coin -> onSendClick(walletId, coin) },
                onShowAssetSelector = { purpose ->
                    selectorPurpose = purpose
                    showAssetSelector = true
                },
                onSwapClick = onSwapClick,
                onMoreClick = onMoreClick,
                onViewAllTransactionsClick = { onNavigateToAllTransactions(walletId) },
                onTransactionClick = { transaction ->
                    onNavigateToTransactionDetail(
                        walletId,
                        transaction.id,
                        transaction.coin
                    )
                },
                padding = padding
            )

            if (showAssetSelector) {
                AssetSelectionDialog(
                    assets = uiState.assets,
                    purpose = selectorPurpose,
                    onAssetSelected = { coin ->
                        showAssetSelector = false
                        if (selectorPurpose == AssetSelectorPurpose.SEND) {
                            onSendClick(walletId, coin)
                        } else {
                            onReceiveClick(walletId, coin)
                        }
                    },
                    onDismiss = { showAssetSelector = false }
                )
            }
        } ?: run {
            EmptyWalletView(
                onBack = onNavigateUp
            )
        }
    }
}

@Composable
private fun WalletDetailContent(
    wallet: Wallet,
    assets: List<AssetDisplayInfo>,
    transactions: List<TransactionDisplayInfo>,
    totalBalanceFormatted: String,
    hasSyncError: Boolean,
    isLoadingBalance: Boolean,
    isLoadingTransactions: Boolean,
    isRefreshingBalance: Boolean,
    isRefreshingTransactions: Boolean,
    balanceLoadingMessage: String,
    transactionsLoadingMessage: String,
    onAssetClick: (Coin) -> Unit,
    onReceiveClick: (Coin) -> Unit,
    onSendClick: (Coin) -> Unit,
    onShowAssetSelector: (AssetSelectorPurpose) -> Unit,
    onSwapClick: () -> Unit,
    onMoreClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onTransactionClick: (TransactionDisplayInfo) -> Unit,
    padding: PaddingValues
) {
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
                totalBalanceFormatted = totalBalanceFormatted,
                hasSyncError = hasSyncError,
                isLoadingBalance = isLoadingBalance || isRefreshingBalance,
                balanceLoadingMessage = balanceLoadingMessage,
                onReceive = {
                    if (assets.size > 1) {
                        onShowAssetSelector(AssetSelectorPurpose.RECEIVE)
                    } else {
                        assets.firstOrNull()?.let { onReceiveClick(it.coin) }
                            ?: getDefaultCoin(wallet)?.let { onReceiveClick(it) }
                    }
                },
                onSend = {
                    if (assets.size > 1) {
                        onShowAssetSelector(AssetSelectorPurpose.SEND)
                    } else {
                        assets.firstOrNull()?.let { onSendClick(it.coin) }
                            ?: getDefaultCoin(wallet)?.let { onSendClick(it) }
                    }
                },
                onSwap = onSwapClick,
                onMore = onMoreClick
            )
        }

        // All assets from unified list
        items(
            items = assets,
            key = { it.id }
        ) { asset ->
            AssetCard(
                asset = asset,
                onClick = { onAssetClick(asset.coin) }
            )
        }

        // Transactions Section
        item {
            TransactionsContainer(
                transactions = transactions,
                isLoading = isLoadingTransactions || isRefreshingTransactions,
                loadingMessage = transactionsLoadingMessage,
                onViewAll = onViewAllTransactionsClick,
                onTransactionClick = onTransactionClick
            )
        }
    }
}

private fun getDefaultCoin(wallet: Wallet): Coin? = when {
    wallet.evmTokens.isNotEmpty() -> wallet.evmTokens.first()
    wallet.bitcoinCoins.isNotEmpty() -> wallet.bitcoinCoins.first()
    wallet.solanaCoins.isNotEmpty() -> wallet.solanaCoins.first()
    else -> null
}

@Composable
fun AssetCard(
    asset: AssetDisplayInfo,
    onClick: () -> Unit
) {
    val (iconRes, iconColor, iconSize) = when (asset.coin) {
        is BitcoinCoin -> Triple(R.drawable.bitcoin, bitcoinLight, 20.dp)
        is SolanaCoin -> Triple(R.drawable.solana, solanaLight, 20.dp)
        is NativeETH -> Triple(R.drawable.ethereum, ethereumLight, 24.dp)
        is USDCToken -> Triple(R.drawable.usdc, usdcLight, 20.dp)
        is USDTToken -> Triple(R.drawable.tether, usdtLight, 20.dp)
    }

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
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = asset.coin.symbol,
                    modifier = Modifier.size(iconSize),
                    tint = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Asset info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = asset.coin.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (asset.coin.network.isTestnet) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.warning.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Testnet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.warning,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${asset.balanceFormatted} ${asset.coin.symbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = asset.coin.network.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    fontSize = 11.sp
                )

                if (asset.tokenCount > 0) {
                    Text(
                        text = stringResource(R.string.plus_x_tokens, asset.tokenCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // USD Value and percentage
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = asset.usdValueFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                if (asset.priceChangeFormatted != null) {
                    PriceChangeIndicator(asset.priceChangeFormatted)
                }
            }
        }
    }
}

@Composable
fun WalletHeaderCard(
    wallet: Wallet,
    totalBalanceFormatted: String,
    hasSyncError: Boolean = false,
    isLoadingBalance: Boolean = false,
    balanceLoadingMessage: String = "",
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onSwap: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null
) {
    val assetCount = wallet.bitcoinCoins.size +
            wallet.solanaCoins.size +
            wallet.evmTokens.size

    // Extract numeric value from formatted string
    val numericBalance = remember(totalBalanceFormatted) {
        totalBalanceFormatted.replace("[$,]".toRegex(), "").toDoubleOrNull() ?: 0.0
    }

    var previousValue by remember { mutableStateOf(numericBalance) }
    val animatedValue = remember { Animatable(previousValue.toFloat()) }

    LaunchedEffect(numericBalance) {
        if (previousValue != numericBalance) {
            animatedValue.animateTo(
                targetValue = numericBalance.toFloat(),
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
            previousValue = numericBalance
        }
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
                .padding(20.dp)
        ) {
            // Total Balance Label
            Text(
                text = stringResource(R.string.total_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Animated Balance with optional warning icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = animatedValue.value.toDouble().formatCurrency(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Show warning icon if there's a sync error
                if (hasSyncError) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Sync error",
                        tint = MaterialTheme.colorScheme.warning,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Show loading message if balance is loading
            if (isLoadingBalance && balanceLoadingMessage.isNotEmpty()) {
                Text(
                    text = balanceLoadingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Asset count
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
                    text = stringResource(R.string.asset_count_plural, assetCount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receive button
                QuickActionItem(
                    icon = Icons.Outlined.ArrowDownward,
                    label = stringResource(R.string.receive),
                    onClick = onReceive,
                    color = MaterialTheme.colorScheme.success,
                    modifier = Modifier.weight(1f)
                )

                // Send button
                QuickActionItem(
                    icon = Icons.Outlined.ArrowUpward,
                    label = stringResource(R.string.send),
                    onClick = onSend,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // Swap button
                if (onSwap != null) {
                    QuickActionItem(
                        icon = Icons.Outlined.SwapHoriz,
                        label = stringResource(R.string.swap),
                        onClick = onSwap,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // More button
                if (onMore != null) {
                    QuickActionItem(
                        icon = Icons.Outlined.MoreHoriz,
                        label = stringResource(R.string.more),
                        onClick = onMore,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionsContainer(
    transactions: List<TransactionDisplayInfo>,
    isLoading: Boolean = false,
    loadingMessage: String = stringResource(R.string.loading_transactions),
    onViewAll: () -> Unit,
    onTransactionClick: (TransactionDisplayInfo) -> Unit
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
                    text = stringResource(R.string.recent_transactions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.see_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLoading)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isLoading)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction list or loading state
            if (isLoading && transactions.isEmpty()) {
                // Show loading skeletons
                repeat(3) { index ->
                    TransactionLoadingSkeleton()
                    if (index < 2) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Show loading message
                Text(
                    text = loadingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else if (transactions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    transactions.take(3).forEachIndexed { index, displayInfo ->
                        TransactionItem(
                            transaction = displayInfo,
                            modifier = Modifier
                                .clickable {
                                    onTransactionClick(displayInfo)
                                }
                        )

                        if (index < 2) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                EmptyTransactionsView()
            }
        }
    }
}

@Composable
fun PriceChangeIndicator(formatted: String) {
    val isPositive = formatted.startsWith("+")
    val changeColor = if (isPositive)
        MaterialTheme.colorScheme.success
    else
        MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (isPositive)
                Icons.AutoMirrored.Outlined.TrendingUp
            else
                Icons.AutoMirrored.Outlined.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = changeColor
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelSmall,
            color = changeColor
        )
    }
}

@Composable
fun TransactionLoadingSkeleton() {
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
                .shimmer()
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .shimmer()
            )
        }

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(32.dp)
                .shimmer()
        )
    }
}

@Composable
fun Modifier.shimmer(): Modifier = this.then(
    Modifier.background(
        brush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.surfaceVariant
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    )
)

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onClick() }
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
            text = stringResource(R.string.no_transactions_yet),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.transactions_appear_here),
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
                    text = stringResource(R.string.wallet_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.wallet_not_found_desc),
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
                        stringResource(R.string.back_to_wallets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private enum class AssetSelectorPurpose {
    SEND, RECEIVE
}

@Composable
private fun AssetSelectionDialog(
    assets: List<AssetDisplayInfo>,
    purpose: AssetSelectorPurpose,
    onAssetSelected: (Coin) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (purpose == AssetSelectorPurpose.SEND)
                    stringResource(R.string.select_asset_to_send)
                else
                    stringResource(R.string.select_asset_to_receive),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assets) { asset ->
                        AssetSelectionRow(
                            asset = asset,
                            onClick = { onAssetSelected(asset.coin) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AssetSelectionRow(
    asset: AssetDisplayInfo,
    onClick: () -> Unit
) {
    val (iconRes, _, iconSize) = when (asset.coin) {
        is BitcoinCoin -> Triple(R.drawable.bitcoin, bitcoinLight, 20.dp)
        is SolanaCoin -> Triple(R.drawable.solana, solanaLight, 20.dp)
        is NativeETH -> Triple(R.drawable.ethereum, ethereumLight, 24.dp)
        is USDCToken -> Triple(R.drawable.usdc, usdcLight, 20.dp)
        is USDTToken -> Triple(R.drawable.tether, usdtLight, 20.dp)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = Color.Unspecified
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.coin.symbol,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = asset.coin.network.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = asset.balanceFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = asset.usdValueFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
