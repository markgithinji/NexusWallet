package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.market.ui.formatTwoDecimals
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToCoinDetail: (String, CoinType, String) -> Unit,
    onNavigateToReceive: (String, CoinType) -> Unit,
    onNavigateToSend: (String, CoinType, String) -> Unit,
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
    onNavigateToCoinDetail: (String, CoinType, String) -> Unit,
    onNavigateToReceive: (String, CoinType) -> Unit,
    onNavigateToSend: (String, CoinType, String) -> Unit,
    onNavigateToAllTransactions: (String) -> Unit,
    onSwap: () -> Unit,
    onMore: () -> Unit,
    padding: PaddingValues
) {
    val totalFormatted = NumberFormat.getCurrencyInstance(Locale.US).format(totalUsdValue)

    // Create a map of externalTokenId -> EVMBalance for quick lookup
    val balanceMap = balance?.evmBalances?.associateBy { it.externalTokenId } ?: emptyMap()

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
                totalBalance = totalFormatted
            )
        }

        // Quick Actions
        item {
            QuickActionsRow(
                onReceive = {
                    // Default to first available coin for receive
                    val defaultCoin = when {
                        wallet.evmTokens.isNotEmpty() -> CoinType.ETHEREUM
                        wallet.bitcoinCoins.isNotEmpty() -> CoinType.BITCOIN
                        wallet.solanaCoins.isNotEmpty() -> CoinType.SOLANA
                        else -> CoinType.BITCOIN
                    }
                    onNavigateToReceive(wallet.id, defaultCoin)
                },
                onSend = {
                    // Default to first available coin for send
                    val defaultCoin = when {
                        wallet.evmTokens.isNotEmpty() -> CoinType.ETHEREUM
                        wallet.bitcoinCoins.isNotEmpty() -> CoinType.BITCOIN
                        wallet.solanaCoins.isNotEmpty() -> CoinType.SOLANA
                        else -> CoinType.BITCOIN
                    }
                    // For the default quick action, we need a network
                    val defaultNetwork = when (defaultCoin) {
                        CoinType.BITCOIN -> "mainnet"
                        CoinType.ETHEREUM -> "mainnet"
                        CoinType.SOLANA -> "mainnet"
                        CoinType.USDC -> "mainnet"
                    }
                    onNavigateToSend(wallet.id, defaultCoin, defaultNetwork)
                },
                onSwap = onSwap,
                onMore = onMore
            )
        }

        // Bitcoin Coins
        wallet.bitcoinCoins.forEachIndexed { index, coin ->
            val percentage = pricePercentages["bitcoin"]
            val networkKey = when (coin.network) {
                BitcoinNetwork.Mainnet -> "mainnet"
                BitcoinNetwork.Testnet -> "testnet"
            }
            val coinBalance = balance?.bitcoinBalances?.get(networkKey)
            val networkSuffix = if (coin.network != BitcoinNetwork.Mainnet) " (Testnet)" else ""

            item(key = "btc_${coin.network}_${coin.address.take(8)}_$index") {
                BitcoinCoinCard(
                    coin = coin,
                    balance = coinBalance,
                    onClick = {
                        onNavigateToCoinDetail(wallet.id, CoinType.BITCOIN, networkKey)
                    },
                    priceChangePercentage = percentage,
                    networkSuffix = networkSuffix
                )
            }
        }

        // Solana Coins
        wallet.solanaCoins.forEachIndexed { index, coin ->
            val percentage = pricePercentages["solana"]
            val networkKey = when (coin.network) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
            }
            val coinBalance = balance?.solanaBalances?.get(networkKey)
            val networkSuffix = if (coin.network != SolanaNetwork.Mainnet) " (Devnet)" else ""

            item(key = "sol_${coin.network}_${coin.address.take(8)}_$index") {
                SolanaCoinCard(
                    coin = coin,
                    balance = coinBalance,
                    onClick = {
                        onNavigateToCoinDetail(wallet.id, CoinType.SOLANA, networkKey)
                    },
                    priceChangePercentage = percentage,
                    networkSuffix = networkSuffix
                )
            }
        }

        // ============ EVM TOKENS ============
// Group EVM tokens by network for better organization
        val mainnetTokens = wallet.evmTokens.filter { it.network == EthereumNetwork.Mainnet }
        val sepoliaTokens = wallet.evmTokens.filter { it.network == EthereumNetwork.Sepolia }

// Mainnet tokens
        mainnetTokens.forEach { token ->
            val percentage = when (token) {
                is NativeETH -> pricePercentages["ethereum"]
                is USDCToken -> pricePercentages["usd-coin"]
                is USDTToken -> pricePercentages["tether"]
                else -> null
            }

            val networkParam = when (token.network) {
                EthereumNetwork.Mainnet -> "mainnet"
                EthereumNetwork.Sepolia -> "sepolia"
            }

            val tokenBalance = balanceMap[token.externalId]

            item(key = "${token.externalId}_mainnet") {
                EVMTokenCard(
                    token = token,
                    balance = tokenBalance,
                    onClick = {
                        when (token) {
                            is NativeETH -> onNavigateToCoinDetail(wallet.id, CoinType.ETHEREUM, networkParam)
                            is USDCToken -> onNavigateToCoinDetail(wallet.id, CoinType.USDC, networkParam)
                            else -> { /* Navigate to token detail */ }
                        }
                    },
                    priceChangePercentage = percentage
                )
            }
        }

// Sepolia tokens (Testnet)
        if (sepoliaTokens.isNotEmpty()) {
            sepoliaTokens.forEach { token ->
                val percentage = when (token) {
                    is NativeETH -> pricePercentages["ethereum"]
                    is USDCToken -> pricePercentages["usd-coin"]
                    is USDTToken -> pricePercentages["tether"]
                    else -> null
                }

                val networkParam = when (token.network) {
                    EthereumNetwork.Mainnet -> "mainnet"
                    EthereumNetwork.Sepolia -> "sepolia"
                }

                val tokenBalance = balanceMap[token.externalId]

                item(key = "${token.externalId}_sepolia") {
                    EVMTokenCard(
                        token = token,
                        balance = tokenBalance,
                        onClick = {
                            when (token) {
                                is NativeETH -> onNavigateToCoinDetail(wallet.id, CoinType.ETHEREUM, networkParam)
                                is USDCToken -> onNavigateToCoinDetail(wallet.id, CoinType.USDC, networkParam)
                                else -> { /* Navigate to token detail */ }
                            }
                        },
                        priceChangePercentage = percentage
                    )
                }
            }
        }

        // ============ SPL TOKENS ============
        val allSplTokens = wallet.solanaCoins.flatMap { it.splTokens }
        if (allSplTokens.isNotEmpty()) {
            allSplTokens.forEach { splToken ->
                item(key = "spl_${splToken.mintAddress}") {
                    SPLTokenCard(
                        token = splToken,
                        // TODO: get SPL balances from somewhere
                    )
                }
            }
        }

        // ============ TRANSACTIONS ============
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
fun SPLTokenCard(
    token: SPLToken,
    balance: SPLBalance? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = token.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = balance?.balanceDecimal ?: "0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$${NumberFormat.getNumberInstance(Locale.US).format(balance?.usdValue ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Serializable
data class SPLBalance(
    val mintAddress: String,
    val balanceDecimal: String,
    val usdValue: Double
)
@Composable
fun WalletHeaderCard(
    wallet: Wallet,
    totalBalance: String
) {
    val assetCount = wallet.bitcoinCoins.size +
            wallet.solanaCoins.size +
            wallet.evmTokens.size

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

            Divider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

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
fun BitcoinCoinCard(
    coin: BitcoinCoin,
    balance: BitcoinBalance?,
    onClick: () -> Unit,
    priceChangePercentage: Double? = null,
    networkSuffix: String = ""
) {
    val balanceAmount = balance?.btc ?: "0"
    val usdValue = balance?.usdValue ?: 0.0

    val formattedBalance = formatCryptoAmount(balanceAmount)
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
                    .background(bitcoinLight.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CurrencyBitcoin,
                    contentDescription = "BTC",
                    tint = bitcoinLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Coin info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "BTC$networkSuffix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$formattedBalance BTC",
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

                if (priceChangePercentage != null) {
                    PriceChangeIndicator(priceChangePercentage)
                }
            }
        }
    }
}

@Composable
fun SolanaCoinCard(
    coin: SolanaCoin,
    balance: SolanaBalance?,
    onClick: () -> Unit,
    priceChangePercentage: Double? = null,
    networkSuffix: String = ""
) {
    val balanceAmount = balance?.sol ?: "0"
    val usdValue = balance?.usdValue ?: 0.0

    val formattedBalance = formatCryptoAmount(balanceAmount)
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
                    .background(solanaLight.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FlashOn,
                    contentDescription = "SOL",
                    tint = solanaLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Coin info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "SOL$networkSuffix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$formattedBalance SOL",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show SPL token count if any
                if (coin.splTokens.isNotEmpty()) {
                    Text(
                        text = "+${coin.splTokens.size} tokens",
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
                    text = formattedUsd,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                if (priceChangePercentage != null) {
                    PriceChangeIndicator(priceChangePercentage)
                }
            }
        }
    }
}

@Composable
fun EVMTokenCard(
    token: EVMToken,
    balance: EVMBalance?,
    onClick: () -> Unit,
    priceChangePercentage: Double? = null
) {
    val (color, icon) = when (token) {
        is NativeETH -> Pair(ethereumLight, Icons.Outlined.Diamond)
        is USDCToken -> Pair(usdcLight, Icons.Outlined.AttachMoney)
        is USDTToken -> Pair(Color(0xFF26A17B), Icons.Outlined.AttachMoney)
        is ERC20Token -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.Token)
    }

    val balanceAmount = balance?.balanceDecimal ?: "0"
    val usdValue = balance?.usdValue ?: 0.0

    val formattedBalance = formatCryptoAmount(balanceAmount)
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
            // Token icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = token.symbol,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Token info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = token.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$formattedBalance ${token.symbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (token.network != EthereumNetwork.Mainnet) {
                    Text(
                        text = token.network.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

                if (priceChangePercentage != null) {
                    PriceChangeIndicator(priceChangePercentage)
                }
            }
        }
    }
}

@Composable
fun PriceChangeIndicator(percentage: Double) {
    val changeColor = if (percentage >= 0)
        MaterialTheme.colorScheme.success
    else
        MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (percentage >= 0)
                Icons.Outlined.TrendingUp
            else
                Icons.Outlined.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = changeColor
        )
        Text(
            text = "${if (percentage >= 0) "+" else ""}${percentage.formatTwoDecimals()}%",
            style = MaterialTheme.typography.labelSmall,
            color = changeColor
        )
    }
}

// Helper function to format crypto amounts
fun formatCryptoAmount(amount: String): String {
    return try {
        val amountDecimal = amount.toBigDecimal()
        when {
            amountDecimal < BigDecimal("0.000001") -> amountDecimal.setScale(8, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
            amountDecimal < BigDecimal("0.001") -> amountDecimal.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
            amountDecimal < BigDecimal("1") -> amountDecimal.setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
            else -> amountDecimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    } catch (e: Exception) {
        amount
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
fun TransactionItem(
    transaction: Any,
    wallet: Wallet
) {
    val (isIncoming, amount, symbol, status, timestamp) = when (transaction) {
        is BitcoinTransaction -> {
            // Find the Bitcoin coin that matches this transaction
            val bitcoinCoin = wallet.bitcoinCoins.find { it.address == transaction.toAddress || it.address == transaction.fromAddress }
            val isIncoming = transaction.toAddress == bitcoinCoin?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountBtc,
                symbol = "BTC",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is SolanaTransaction -> {
            // Find the Solana coin that matches this transaction
            val solanaCoin = wallet.solanaCoins.find { it.address == transaction.toAddress || it.address == transaction.fromAddress }
            val isIncoming = transaction.toAddress == solanaCoin?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountSol,
                symbol = if (transaction.tokenSymbol != null) transaction.tokenSymbol else "SOL",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is NativeETHTransaction -> {
            // Find any EVM token with the same address (Native ETH uses same address)
            val evmToken = wallet.evmTokens.firstOrNull { it.address == transaction.toAddress || it.address == transaction.fromAddress }
            val isIncoming = transaction.toAddress == evmToken?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountEth,
                symbol = "ETH",
                status = transaction.status,
                timestamp = transaction.timestamp
            )
        }

        is TokenTransaction -> {
            // Find the specific token that matches this transaction
            val evmToken = wallet.evmTokens.find {
                it.contractAddress == transaction.tokenContract &&
                        (it.address == transaction.toAddress || it.address == transaction.fromAddress)
            }
            val isIncoming = transaction.toAddress == evmToken?.address
            TransactionDisplayData(
                isIncoming = isIncoming,
                amount = transaction.amountDecimal,
                symbol = transaction.tokenSymbol,
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
                    text = if (isIncoming) "Received $symbol" else "Sent $symbol",
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