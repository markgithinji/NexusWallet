package com.example.nexuswallet.feature.wallet.ui.coindetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.LocalCurrency
import com.example.nexuswallet.feature.core.ui.clickableSingle
import com.example.nexuswallet.feature.core.util.formatAsCurrency
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.ui.common.ErrorScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.SyncPulseIndicator
import com.example.nexuswallet.feature.wallet.ui.common.TransactionItem
import com.example.nexuswallet.feature.wallet.ui.walletdetail.EmptyTransactionsView
import com.example.nexuswallet.feature.wallet.ui.walletdetail.QuickActionItem
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight
import com.example.nexuswallet.ui.theme.warning
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReceive: (String, Coin) -> Unit,
    onNavigateToSend: (String, Coin) -> Unit,
    onNavigateToAllTransactions: (String, Coin) -> Unit,
    onNavigateToTransactionDetail: (String, String, Coin) -> Unit,
    walletId: String,
    coin: Coin,
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, coin)
    }

    // Show loading only on initial load
    if (state.isLoading && state.address.isEmpty()) {
        FullScreenLoading(message = stringResource(R.string.loading_coin_details))
        return
    }

    // Show error if present
    state.error?.let { errorMessage ->
        ErrorScreen(
            message = errorMessage,
            onRetry = {
                viewModel.loadCoinDetails(walletId, coin)
            }
        )
        return
    }

    // Get display configuration from the coin
    val currentCoin = state.coin ?: coin
    val (coinColor, iconRes) = getCoinDetailConfig(currentCoin)
    val displayName = currentCoin.name

    val isAnyLoading = state.isLoading || state.isRefreshing

    Scaffold(
        topBar = {
            Column {
                CoinDetailTopBar(
                    iconRes = iconRes,
                    displayName = displayName,
                    isLoading = isAnyLoading,
                    onNavigateUp = onNavigateUp,
                    onRefresh = {
                        viewModel.refresh()
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                ) {
                    if (isAnyLoading && state.address.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = coinColor,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        CoinDetailContent(
            state = state,
            coinColor = coinColor,
            iconRes = iconRes,
            displayName = displayName,
            coin = currentCoin,
            onCopyAddress = { address ->
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(context.getString(R.string.address_label), address)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    context.getString(R.string.address_copied_toast),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onReceive = { onNavigateToReceive(walletId, currentCoin) },
            onSend = { onNavigateToSend(walletId, currentCoin) },
            onViewAllTransactions = { onNavigateToAllTransactions(walletId, currentCoin) },
            onTransactionClick = { transaction ->
                onNavigateToTransactionDetail(walletId, transaction.id, transaction.coin)
            },
            onSPLTokenClick = { /* Handle SPL token click */ },
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
                    text = stringResource(R.string.coin_wallet, displayName),
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
                    stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    stringResource(R.string.refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    coinColor: Color,
    iconRes: Int,
    displayName: String,
    coin: Coin,
    onCopyAddress: (String) -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (TransactionDisplayInfo) -> Unit,
    onSPLTokenClick: (SPLToken) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTestnet = coin.network.isTestnet

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
                coin = coin,
                usdValue = state.usdValue,
                isSyncing = state.isRefreshing,
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

        // Show ETH gas balance for EVM tokens
        if (coin is EVMToken && coin !is NativeETH) {
            item {
                CoinDetailEthGasBalanceCard(
                    formattedEthBalance = state.ethGasBalance
                )
            }
        }

        // SPL Tokens for Solana
        if (coin is SolanaCoin && state.splTokens.isNotEmpty()) {
            item {
                CoinDetailSPLTokensCard(
                    splTokens = state.splTokens,
                    isTestnet = isTestnet,
                    onTokenClick = onSPLTokenClick
                )
            }
        }

        // Transactions
        item {
            CoinDetailTransactionsContainer(
                transactions = state.transactions,
                coin = coin,
                onViewAll = onViewAllTransactions,
                onTransactionClick = onTransactionClick
            )
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
    coin: Coin,
    usdValue: BigDecimal?,
    isSyncing: Boolean = false,
    onCopyAddress: (String) -> Unit
) {
    val currencyState = LocalCurrency.current

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
                        if (coin.network.isTestnet) {
                            Text(
                                text = coin.network.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Address with copy icon (only show if address is not empty)
                if (address.isNotEmpty()) {
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
                                contentDescription = stringResource(R.string.copy_address),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (usdValue != null && usdValue > BigDecimal.ZERO) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = usdValue.formatAsCurrency(
                            currencyState.usdToRate,
                            currencyState.currency
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isSyncing) {
                        SyncPulseIndicator()
                    }
                }
            }

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
                label = stringResource(R.string.receive),
                onClick = onReceive,
                color = MaterialTheme.colorScheme.success
            )

            QuickActionItem(
                icon = Icons.Outlined.ArrowUpward,
                label = stringResource(R.string.send),
                onClick = onSend,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CoinDetailEthGasBalanceCard(
    formattedEthBalance: String
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
                    contentDescription = stringResource(R.string.gas),
                    tint = ethereumLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.eth_for_gas),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$formattedEthBalance ETH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoinDetailSPLTokensCard(
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
                text = stringResource(R.string.spl_tokens),
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
            .clickableSingle { onClick() }
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
private fun CoinDetailTransactionsContainer(
    transactions: List<TransactionDisplayInfo>,
    coin: Coin,
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
                    text = stringResource(R.string.recent_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.view_all),
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
                            .clickableSingle { onTransactionClick(transaction) }
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

private fun getCoinDetailConfig(coin: Coin): Pair<Color, Int> {
    return when (coin) {
        is BitcoinCoin -> Pair(bitcoinLight, R.drawable.bitcoin)
        is NativeETH -> Pair(ethereumLight, R.drawable.ethereum)
        is USDCToken -> Pair(usdcLight, R.drawable.usdc)
        is USDTToken -> Pair(usdtLight, R.drawable.tether)
        is SolanaCoin -> Pair(solanaLight, R.drawable.solana)
    }
}