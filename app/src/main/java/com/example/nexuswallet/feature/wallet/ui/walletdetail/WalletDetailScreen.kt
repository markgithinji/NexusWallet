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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.AssetType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAllTransactions: (String) -> Unit,
    onNavigateToTransactionDetail: (String, String) -> Unit,
    onReceiveClick: (String, Network) -> Unit,
    onSendClick: (String, Network) -> Unit,
    onAssetClick: (String, Network) -> Unit,
    walletId: String,
    walletViewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(walletId) {
        walletViewModel.loadWallet(walletId)
    }

    // Show full screen loading only on initial load with no wallet
    if (uiState.isLoading && uiState.wallet == null) {
        FullScreenLoading(message = "Loading wallet...")
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
                            contentDescription = "Back",
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
                    uiState.isRefreshingBalance -> "Updating balances..."
                    uiState.isLoadingBalance -> "Loading balances..."
                    else -> ""
                },
                transactionsLoadingMessage = when {
                    uiState.isRefreshingTransactions -> "Updating transactions..."
                    uiState.isLoadingTransactions -> "Loading transactions..."
                    else -> ""
                },
                onAssetClick = { network -> onAssetClick(walletId, network) },
                onReceiveClick = { network -> onReceiveClick(walletId, network) },
                onSendClick = { network -> onSendClick(walletId, network) },
                onViewAllTransactionsClick = { onNavigateToAllTransactions(walletId) },
                onTransactionClick = { transaction ->
                    onNavigateToTransactionDetail(walletId, transaction.id)
                },
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
    onAssetClick: (Network) -> Unit,
    onReceiveClick: (Network) -> Unit,
    onSendClick: (Network) -> Unit,
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
                onReceive = { onReceiveClick(getDefaultNetwork(wallet)) },
                onSend = { onSendClick(getDefaultNetwork(wallet)) }
            )
        }

        // All assets from unified list
        items(
            items = assets,
            key = { it.id }
        ) { asset ->
            AssetCard(
                asset = asset,
                onClick = { onAssetClick(asset.network) }
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

private fun getDefaultNetwork(wallet: Wallet): Network = when {
    wallet.evmTokens.isNotEmpty() -> EthereumNetwork.Mainnet
    wallet.bitcoinCoins.isNotEmpty() -> BitcoinNetwork.Mainnet
    wallet.solanaCoins.isNotEmpty() -> SolanaNetwork.Mainnet
    else -> BitcoinNetwork.Mainnet
}

@Composable
fun AssetCard(
    asset: AssetDisplayInfo,
    onClick: () -> Unit
) {
    val (iconRes, iconColor, iconSize) = when (asset.assetType) {
        AssetType.BITCOIN -> Triple(R.drawable.bitcoin, bitcoinLight, 20.dp)
        AssetType.SOLANA -> Triple(R.drawable.solana, solanaLight, 20.dp)
        AssetType.ETHEREUM -> Triple(R.drawable.ethereum, ethereumLight, 24.dp)
        AssetType.USDC -> Triple(R.drawable.usdc, usdcLight, 20.dp)
        AssetType.USDT -> Triple(R.drawable.tether, usdtLight, 20.dp)
        AssetType.ERC20, AssetType.SPL -> Triple(null, MaterialTheme.colorScheme.primary, 20.dp)
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
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = asset.symbol,
                        modifier = Modifier.size(iconSize),
                        tint = Color.Unspecified
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Token,
                            contentDescription = asset.symbol,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (asset.isTestnet) {
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
                    text = "${asset.balanceFormatted} ${asset.symbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (asset.tokenCount > 0) {
                    Text(
                        text = "+${asset.tokenCount} tokens",
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
    onSend: () -> Unit
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
                text = "Total Balance",
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
                    text = NumberFormat.getCurrencyInstance(Locale.US).format(animatedValue.value),
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
                    text = "$assetCount assets",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionItem(
                    icon = Icons.Outlined.ArrowDownward,
                    label = "Receive",
                    onClick = onReceive,
                    color = MaterialTheme.colorScheme.success,
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    icon = Icons.Outlined.ArrowUpward,
                    label = "Send",
                    onClick = onSend,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TransactionsContainer(
    transactions: List<TransactionDisplayInfo>,
    isLoading: Boolean = false,
    loadingMessage: String = "Loading transactions...",
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
                    text = "Recent Transactions",
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
                        text = "See All",
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
                        Divider(
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
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
                Icons.Outlined.TrendingUp
            else
                Icons.Outlined.TrendingDown,
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