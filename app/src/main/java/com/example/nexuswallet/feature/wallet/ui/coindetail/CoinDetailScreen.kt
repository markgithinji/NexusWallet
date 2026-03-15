package com.example.nexuswallet.feature.wallet.ui.coindetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.ui.EmptyTransactionsView
import com.example.nexuswallet.feature.wallet.ui.ErrorScreen
import com.example.nexuswallet.feature.wallet.ui.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.QuickActionItem
import com.example.nexuswallet.feature.wallet.ui.TransactionItem
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReceive: (String, Network) -> Unit,
    onNavigateToSend: (String, Network) -> Unit,
    onNavigateToAllTransactions: (String, Network) -> Unit,
    onNavigateToTransactionDetail: (String, String) -> Unit,
    onCopyAddress: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSPLTokenClick: (SPLToken) -> Unit,
    onEVMTokenClick: (EVMToken) -> Unit,
    walletId: String,
    network: Network,
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, network)
    }

    // Create handlers that use the hoisted callbacks
    val handleCopyAddress: (String) -> Unit = { address ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Address", address)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
        onCopyAddress(address)
    }

    val handleReceive: () -> Unit = {
        onNavigateToReceive(walletId, network)
    }

    val handleSend: () -> Unit = {
        onNavigateToSend(walletId, network)
    }

    val handleViewAllTransactions: () -> Unit = {
        onNavigateToAllTransactions(walletId, network)
    }

    val handleTransactionClick: (TransactionDisplayInfo) -> Unit = { transaction ->
        onNavigateToTransactionDetail(walletId, transaction.id)
    }

    val handleRefresh: () -> Unit = {
        onRefresh()
        viewModel.refresh()
    }

    val handleRetry: () -> Unit = {
        onRetry()
        viewModel.loadCoinDetails(walletId, network)
    }

    // Show loading only on initial load
    if (state.isLoading && state.address.isEmpty()) {
        FullScreenLoading(message = "Loading coin details...")
        return
    }

    // Show error if present
    state.error?.let { errorMessage ->
        ErrorScreen(
            message = errorMessage,
            onRetry = handleRetry
        )
        return
    }

    val coinType = state.coinType ?: network.coinType
    val (coinColor, iconRes) = getCoinDetailConfig(coinType)
    val displayName = coinType.displayName

    Scaffold(
        topBar = {
            CoinDetailTopBar(
                iconRes = iconRes,
                displayName = displayName,
                isLoading = state.isLoading,
                onNavigateUp = onNavigateUp,
                onRefresh = handleRefresh
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        CoinDetailContent(
            state = state,
            coinType = coinType,
            coinColor = coinColor,
            iconRes = iconRes,
            displayName = displayName,
            network = network,
            onCopyAddress = handleCopyAddress,
            onReceive = handleReceive,
            onSend = handleSend,
            onViewAllTransactions = handleViewAllTransactions,
            onTransactionClick = handleTransactionClick,
            onSPLTokenClick = onSPLTokenClick,
            onEVMTokenClick = onEVMTokenClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinDetailTopBar(
    iconRes: Int,
    displayName: String,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$displayName Wallet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
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
}

@Composable
private fun CoinDetailContent(
    state: CoinDetailState,
    coinType: CoinType,
    coinColor: Color,
    iconRes: Int,
    displayName: String,
    network: Network,
    onCopyAddress: (String) -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (TransactionDisplayInfo) -> Unit,
    onSPLTokenClick: (SPLToken) -> Unit,
    onEVMTokenClick: (EVMToken) -> Unit,
    modifier: Modifier = Modifier
) {
    val networkDisplayName = network.displayName
    val isTestnet = network.isTestnet

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CoinDetailBalanceCard(
                coinColor = coinColor,
                iconRes = iconRes,
                displayName = displayName,
                balanceFormatted = state.balanceFormatted,
                address = state.address,
                network = network,
                usdValue = state.usdValue,
                onCopyAddress = onCopyAddress
            )
        }

        // Actions
        item {
            CoinDetailActionsCard(
                onReceive = onReceive,
                onSend = onSend
            )
        }

        if (coinType == CoinType.USDC && state.ethGasBalance != null) {
            item {
                CoinDetailEthGasBalanceCard(ethBalance = state.ethGasBalance)
            }
        }

        // SPL Tokens for Solana
        if (coinType == CoinType.SOLANA && state.splTokens.isNotEmpty()) {
            item {
                CoinDetailSPLTokensCard(
                    splTokens = state.splTokens,
                    isTestnet = isTestnet,
                    onTokenClick = onSPLTokenClick
                )
            }
        }

        if (coinType == CoinType.ETHEREUM && state.evmTokens.size > 1) {
            item {
                CoinDetailOtherTokensCard(
                    tokens = state.evmTokens.filter { it !is NativeETH },
                    isTestnet = isTestnet,
                    onTokenClick = onEVMTokenClick
                )
            }
        }

        item {
            CoinDetailTransactionsContainer(
                transactions = state.transactions,
                coinType = coinType,
                onViewAll = onViewAllTransactions,
                onTransactionClick = onTransactionClick
            )
        }
    }
}

@Composable
fun CoinDetailSPLTokensCard(
    splTokens: List<SPLToken>,
    isTestnet: Boolean,
    onTokenClick: (SPLToken) -> Unit
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "SPL Tokens",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            splTokens.forEach { token ->
                SPLTokenRow(
                    token = token,
                    isTestnet = isTestnet,
                    onClick = { onTokenClick(token) }
                )
            }
        }
    }
}

@Composable
fun SPLTokenRow(
    token: SPLToken,
    isTestnet: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(solanaLight.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Token,
                contentDescription = token.symbol,
                tint = solanaLight,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = token.symbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = token.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isTestnet) {
                Text(
                    text = "Devnet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.warning,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun CoinDetailOtherTokensCard(
    tokens: List<EVMToken>,
    isTestnet: Boolean,
    onTokenClick: (EVMToken) -> Unit
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Other Tokens",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            tokens.forEach { token ->
                OtherTokenRow(
                    token = token,
                    isTestnet = isTestnet,
                    onClick = { onTokenClick(token) }
                )
            }
        }
    }
}

@Composable
fun OtherTokenRow(
    token: EVMToken,
    isTestnet: Boolean,
    onClick: () -> Unit
) {
    val (color, iconRes) = when (token) {
        is USDCToken -> Pair(usdcLight, R.drawable.usdc)
        is USDTToken -> Pair(Color(0xFF26A17B), R.drawable.tether)
        else -> Pair(MaterialTheme.colorScheme.primary, null)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = token.symbol,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Token,
                    contentDescription = token.symbol,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = token.symbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = token.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isTestnet) {
                Text(
                    text = "Sepolia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.warning,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CoinDetailBalanceCard(
    coinColor: Color,
    iconRes: Int,
    displayName: String,
    balanceFormatted: String,
    address: String,
    network: Network,
    usdValue: Double,
    onCopyAddress: (String) -> Unit
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
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with coin icon, name, and address with copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            painter = painterResource(id = iconRes),
                            contentDescription = displayName,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
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
                        if (network.isTestnet) {
                            Text(
                                text = network.displayName,
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

            HorizontalDivider(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

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
private fun CoinDetailActionsCard(
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
private fun CoinDetailEthGasBalanceCard(ethBalance: BigDecimal?) {
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
                    text = "${
                        ethBalance?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()
                            ?.toPlainString() ?: "0"
                    } ETH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoinDetailTransactionsContainer(
    transactions: List<TransactionDisplayInfo>,
    coinType: CoinType,
    onViewAll: () -> Unit,
    onTransactionClick: (TransactionDisplayInfo) -> Unit
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
                    TransactionItem(
                        transaction = transaction,
                        modifier = Modifier
                            .clickable { onTransactionClick(transaction) }
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
        }
    }
}

private fun getCoinDetailConfig(coinType: CoinType): Pair<Color, Int> {
    return when (coinType) {
        CoinType.BITCOIN -> Pair(bitcoinLight, R.drawable.bitcoin)
        CoinType.ETHEREUM -> Pair(ethereumLight, R.drawable.ethereum)
        CoinType.SOLANA -> Pair(solanaLight, R.drawable.solana)
        CoinType.USDC -> Pair(usdcLight, R.drawable.usdc)
    }
}