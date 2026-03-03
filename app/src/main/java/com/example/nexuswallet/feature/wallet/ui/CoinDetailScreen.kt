package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
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
    onNavigateToReceive: (String, CoinType, NetworkType?) -> Unit,
    onNavigateToSend: (String, CoinType, NetworkType?) -> Unit,
    onNavigateToAllTransactions: (String, CoinType, NetworkType?) -> Unit,
    walletId: String,
    coinType: CoinType,
    network: NetworkType? = null,
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val networkString = network?.apiValue ?: ""

    LaunchedEffect(Unit) {
        viewModel.loadCoinDetails(walletId, coinType, networkString)
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
            onRetry = { viewModel.loadCoinDetails(walletId, coinType, networkString) }
        )
        return
    }

    val (coinColor, icon) = getCoinDetailConfig(coinType)
    val displayName = getCoinDisplayName(coinType)

    Scaffold(
        topBar = {
            CoinDetailTopBar(
                coinColor = coinColor,
                icon = icon,
                displayName = displayName,
                isLoading = state.isLoading,
                onNavigateUp = onNavigateUp,
                onRefresh = { viewModel.refresh() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        CoinDetailContent(
            state = state,
            coinType = coinType,
            coinColor = coinColor,
            icon = icon,
            displayName = displayName,
            network = network,
            onCopyAddress = { address ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Address", address)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
            },
            onReceive = onNavigateToReceive,
            onSend = onNavigateToSend,
            onViewAllTransactions = onNavigateToAllTransactions,
            onNavigateToSend = onNavigateToSend,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinDetailTopBar(
    coinColor: Color,
    icon: ImageVector,
    displayName: String,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = coinColor
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
                    Icons.Default.ArrowBack,
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
    state: CoinDetailViewModel.CoinDetailState,
    coinType: CoinType,
    coinColor: Color,
    icon: ImageVector,
    displayName: String,
    network: NetworkType?,
    onCopyAddress: (String) -> Unit,
    onReceive: (String, CoinType, NetworkType?) -> Unit,
    onSend: (String, CoinType, NetworkType?) -> Unit,
    onViewAllTransactions: (String, CoinType, NetworkType?) -> Unit,
    onNavigateToSend: (String, CoinType, NetworkType?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get the network display name from the enum
    val networkDisplayName = network?.displayName ?: when (coinType) {
        CoinType.BITCOIN -> "Bitcoin"
        CoinType.ETHEREUM -> "Ethereum"
        CoinType.SOLANA -> "Solana"
        CoinType.USDC -> "USD Coin"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Balance Card
        item {
            CoinDetailBalanceCard(
                coinType = coinType,
                coinColor = coinColor,
                icon = icon,
                displayName = displayName,
                balance = state.balance,
                balanceFormatted = state.balanceFormatted,
                address = state.address,
                network = networkDisplayName,
                usdValue = state.usdValue,
                onCopyAddress = onCopyAddress
            )
        }

        // Actions
        item {
            CoinDetailActionsCard(
                onReceive = { onReceive(state.walletId, coinType, network) },
                onSend = { onSend(state.walletId, coinType, network) }
            )
        }

        // ETH Gas Balance for USDC
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
                    network = network,
                    onTokenClick = { token ->
                        // TODO: create a token detail screen
                        // onNavigateToTokenDetail(token.mintAddress, network)
                    }
                )
            }
        }

        // Other EVM Tokens (for ETH view)
        if (coinType == CoinType.ETHEREUM && state.evmTokens.size > 1) {
            item {
                CoinDetailOtherTokensCard(
                    tokens = state.evmTokens.filter { it !is NativeETH },
                    network = network,
                    onTokenClick = { token ->
                        when (token) {
                            is USDCToken -> onNavigateToSend(state.walletId, CoinType.USDC, network)
                            is USDTToken -> {
                                // For USDT, using USDC type for now
                                onNavigateToSend(state.walletId, CoinType.USDC, network)
                            }
                            else -> {
                                // TODO: Handle other ERC20 tokens
                            }
                        }
                    }
                )
            }
        }

        // Recent Transactions
        item {
            CoinDetailTransactionsContainer(
                transactions = state.transactions,
                coinType = coinType,
                onViewAll = { onViewAllTransactions(state.walletId, coinType, network) }
            )
        }
    }
}

@Composable
fun CoinDetailSPLTokensCard(
    splTokens: List<SPLToken>,
    network: NetworkType?,
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
                    network = network,
                    onClick = { onTokenClick(token) }
                )
            }
        }
    }
}

@Composable
fun SPLTokenRow(
    token: SPLToken,
    network: NetworkType?,
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
            if (network == NetworkType.SOLANA_DEVNET) {
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
    network: NetworkType?,
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
                    network = network,
                    onClick = { onTokenClick(token) }
                )
            }
        }
    }
}

@Composable
fun OtherTokenRow(
    token: EVMToken,
    network: NetworkType?,
    onClick: () -> Unit
) {
    val (color, icon) = when (token) {
        is USDCToken -> Pair(usdcLight, Icons.Outlined.AttachMoney)
        is USDTToken -> Pair(Color(0xFF26A17B), Icons.Outlined.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.Token)
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
            Icon(
                imageVector = icon,
                contentDescription = token.symbol,
                tint = color,
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
            if (network == NetworkType.ETHEREUM_SEPOLIA) {
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
fun CoinDetailOtherTokensCard(
    tokens: List<EVMToken>,
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
                    onClick = { onTokenClick(token) }
                )
            }
        }
    }
}

@Composable
fun OtherTokenRow(
    token: EVMToken,
    onClick: () -> Unit
) {
    val (color, icon) = when (token) {
        is USDCToken -> Pair(usdcLight, Icons.Outlined.AttachMoney)
        is USDTToken -> Pair(Color(0xFF26A17B), Icons.Outlined.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.Token)
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
            Icon(
                imageVector = icon,
                contentDescription = token.symbol,
                tint = color,
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
            if (token.network != EthereumNetwork.Mainnet) {
                Text(
                    text = token.network.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.warning,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CoinDetailSPLTokensCard(
    splTokens: List<SPLToken>,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            splTokens.take(3).forEach { token ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTokenClick(token) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(solanaLight.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Token,
                            contentDescription = null,
                            tint = solanaLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = token.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = token.symbol,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (splTokens.indexOf(token) < splTokens.size - 1 && splTokens.indexOf(token) < 2) {
                    Divider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )
                }
            }

            if (splTokens.size > 3) {
                TextButton(
                    onClick = { /* View all */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View All ${splTokens.size} Tokens")
                }
            }
        }
    }
}

@Composable
private fun CoinDetailBalanceCard(
    coinType: CoinType,
    coinColor: Color,
    icon: ImageVector,
    displayName: String,
    balance: String,
    balanceFormatted: String,
    address: String,
    network: String,
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
                    text = "${ethBalance?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: "0"} ETH",
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
                    CoinDetailTransactionItem(
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
private fun CoinDetailTransactionItem(
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

// Helper functions
private fun getCoinDetailConfig(coinType: CoinType): Pair<Color, ImageVector> {
    return when (coinType) {
        CoinType.BITCOIN -> Pair(bitcoinLight, Icons.Outlined.CurrencyBitcoin)
        CoinType.ETHEREUM -> Pair(ethereumLight, Icons.Outlined.Diamond)
        CoinType.SOLANA -> Pair(solanaLight, Icons.Outlined.FlashOn)
        CoinType.USDC -> Pair(usdcLight, Icons.Outlined.AttachMoney)
    }
}

private fun getCoinDisplayName(coinType: CoinType): String {
    return when (coinType) {
        CoinType.BITCOIN -> "Bitcoin"
        CoinType.ETHEREUM -> "Ethereum"
        CoinType.SOLANA -> "Solana"
        CoinType.USDC -> "USDC"
    }
}