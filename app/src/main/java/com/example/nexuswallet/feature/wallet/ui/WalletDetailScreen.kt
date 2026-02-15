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
fun WalletDetailScreen(
    navController: NavController,
    walletId: String,
    walletViewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val wallet by walletViewModel.wallet.collectAsState()
    val balance by walletViewModel.balance.collectAsState()
    val isLoading by walletViewModel.isLoading.collectAsState()
    val error by walletViewModel.error.collectAsState()

    // Load wallet when screen is first composed
    LaunchedEffect(walletId) {
        walletViewModel.loadWallet(walletId)
    }

    if (isLoading) {
        LoadingScreen()
        return
    }

    error?.let {
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            walletViewModel.refresh()
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        wallet?.let { currentWallet ->
            WalletDetailContent(
                wallet = currentWallet,
                balance = balance,
                navController = navController,
                viewModel = walletViewModel,
                padding = padding
            )
        } ?: run {
            EmptyWalletView(
                onBack = { navController.navigateUp() }
            )
        }
    }
}

@Composable
fun DebugButton(walletId: String, navController: NavController) {

    Button(
        onClick = { navController.navigate("debug/$walletId") },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(Icons.Default.BugReport, "Debug")
        Spacer(modifier = Modifier.width(8.dp))
        Text("API Debug")
    }
}
@Composable
fun WalletDetailContent(
    wallet: Wallet,
    balance: WalletBalance?,
    navController: NavController,
    viewModel: WalletDetailViewModel,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // Wallet Header
        WalletHeaderCard(
            wallet = wallet,
            balance = balance,
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Coins Section
        Text(
            text = "Assets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // List of coins in this wallet
        wallet.bitcoin?.let { coin ->
            CoinOverviewCard(
                coinType = "BTC",
                coin = coin,
                balance = balance?.bitcoin,
                onClick = {
                    navController.navigate("coin/${wallet.id}/BTC")
                }
            )
        }

        wallet.ethereum?.let { coin ->
            CoinOverviewCard(
                coinType = "ETH",
                coin = coin,
                balance = balance?.ethereum,
                onClick = {
                    navController.navigate("coin/${wallet.id}/ETH")
                }
            )
        }

        wallet.solana?.let { coin ->
            CoinOverviewCard(
                coinType = "SOL",
                coin = coin,
                balance = balance?.solana,
                onClick = {
                    navController.navigate("coin/${wallet.id}/SOL")
                }
            )
        }

        wallet.usdc?.let { coin ->
            CoinOverviewCard(
                coinType = "USDC",
                coin = coin,
                balance = balance?.usdc,
                onClick = {
                    navController.navigate("coin/${wallet.id}/USDC")
                }
            )
        }
    }
}
@Composable
fun CoinOverviewCard(
    coinType: String,
    coin: Any, // Could be BitcoinCoin, EthereumCoin, etc.
    balance: Any?, // Could be BitcoinBalance, EthereumBalance, etc.
    onClick: () -> Unit
) {
    val coinInfo = when (coinType) {
        "BTC" -> {
            val btcBalance = balance as? BitcoinBalance
            CoinInfo(
                color = Color(0xFFF7931A),
                icon = Icons.Default.CurrencyBitcoin,
                balanceAmount = btcBalance?.btc ?: "0",
                usdValue = btcBalance?.usdValue ?: 0.0
            )
        }
        "ETH" -> {
            val ethBalance = balance as? EthereumBalance
            CoinInfo(
                color = Color(0xFF627EEA),
                icon = Icons.Default.CurrencyExchange,
                balanceAmount = ethBalance?.eth ?: "0",
                usdValue = ethBalance?.usdValue ?: 0.0
            )
        }
        "SOL" -> {
            val solBalance = balance as? SolanaBalance
            CoinInfo(
                color = Color(0xFF00FFA3),
                icon = Icons.Default.FlashOn,
                balanceAmount = solBalance?.sol ?: "0",
                usdValue = solBalance?.usdValue ?: 0.0
            )
        }
        "USDC" -> {
            val usdcBalance = balance as? USDCBalance
            CoinInfo(
                color = Color(0xFF2775CA),
                icon = Icons.Default.AttachMoney,
                balanceAmount = usdcBalance?.amountDecimal ?: "0",
                usdValue = usdcBalance?.usdValue ?: 0.0
            )
        }
        else -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(coinInfo.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = coinInfo.icon,
                    contentDescription = coinType,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = coinType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = coinInfo.balanceAmount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$${String.format("%.2f", coinInfo.usdValue)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper data class
data class CoinInfo(
    val color: Color,
    val icon: ImageVector,
    val balanceAmount: String,
    val usdValue: Double
)
@Composable
fun WalletHeaderCard(
    wallet: Wallet,
    balance: WalletBalance?,
    viewModel: WalletDetailViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$${String.format("%.2f", viewModel.getTotalUsdValue())}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalGasStation,
                contentDescription = "Gas",
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ETH for Gas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${ethBalance?.setScale(6, RoundingMode.HALF_UP) ?: "0"} ETH",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WalletActionsCard(
    wallet: Wallet,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onBackup: () -> Unit
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
            // Main actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                ActionButton(
                    icon = Icons.Default.Backup,
                    label = "Backup",
                    onClick = onBackup
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Key,
                    label = "Private Key",
                    onClick = {
                        // Navigate to private key view with auth
                    }
                )

                ActionButton(
                    icon = Icons.Default.Security,
                    label = "Security",
                    onClick = {
                        // Navigate to security settings
                    }
                )
            }
        }
    }
}

@Composable
fun TokenBalanceItem(
    tokenBalance: TokenBalance,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (tokenBalance.symbol) {
                            "USDC" -> Color(0xFF2775CA)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tokenBalance.symbol.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tokenBalance.symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tokenBalance.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = tokenBalance.balanceDecimal,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$${String.format("%.2f", tokenBalance.usdValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
@Composable
fun TransactionsSection(
    transactions: List<Any>,
    wallet: Wallet,
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
                    TransactionItem(
                        transaction = transaction,
                        wallet = wallet
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
fun TransactionItem(
    transaction: Any,
    wallet: Wallet
) {
    val (isIncoming, amount, symbol, status, timestamp, hash) = when (transaction) {
        is BitcoinTransaction -> {
            val isIncoming = transaction.toAddress == wallet.bitcoin?.address
            TransactionData(
                isIncoming = isIncoming,
                amount = transaction.amountBtc,
                symbol = "BTC",
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is EthereumTransaction -> {
            val isIncoming = transaction.toAddress == wallet.ethereum?.address
            TransactionData(
                isIncoming = isIncoming,
                amount = transaction.amountEth,
                symbol = "ETH",
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.txHash
            )
        }
        is SolanaTransaction -> {
            val isIncoming = transaction.toAddress == wallet.solana?.address
            TransactionData(
                isIncoming = isIncoming,
                amount = transaction.amountSol,
                symbol = "SOL",
                status = transaction.status,
                timestamp = transaction.timestamp,
                hash = transaction.signature?.let { it.toHexString() }
            )
        }
        is USDCSendTransaction -> {
            val isIncoming = transaction.toAddress == wallet.usdc?.address
            TransactionData(
                isIncoming = isIncoming,
                amount = transaction.amountDecimal,
                symbol = "USDC",
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
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                        TransactionStatus.PENDING -> Color(0xFFFFA500).copy(alpha = 0.1f)
                        TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = if (isIncoming) "Received" else "Sent",
                tint = when (status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color(0xFFFFA500)
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (isIncoming) "Received $symbol" else "Sent $symbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTimestamp(timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hash != null) {
                Text(
                    text = "Hash: ${hash.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Amount and Status
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${if (isIncoming) "+" else "-"}$amount $symbol",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncoming) Color.Green else MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (status) {
                            TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                            TransactionStatus.PENDING -> Color(0xFFFFA500).copy(alpha = 0.1f)
                            TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        TransactionStatus.SUCCESS -> Color.Green
                        TransactionStatus.PENDING -> Color(0xFFFFA500)
                        TransactionStatus.FAILED -> Color.Red
                    }
                )
            }
        }
    }
}

// Helper data class
data class TransactionData(
    val isIncoming: Boolean,
    val amount: String,
    val symbol: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val hash: String?
)


@Composable
fun WalletIconDetailScreen(wallet: CryptoWallet) {
    val iconSize = 48.dp
    val (backgroundColor, icon) = when (wallet) {
        is BitcoinWallet -> Pair(Color(0xFFF7931A), Icons.Default.CurrencyBitcoin)
        is EthereumWallet -> Pair(Color(0xFF627EEA), Icons.Default.Diamond)
        is SolanaWallet -> Pair(Color(0xFF00FFA3), Icons.Default.FlashOn)
        is USDCWallet -> Pair(Color(0xFF2775CA), Icons.Default.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.AccountBalanceWallet)
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Wallet Type",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

fun getWalletTypeDisplay(wallet: CryptoWallet): String {
    return when (wallet) {
        is BitcoinWallet -> "Bitcoin"
        is EthereumWallet -> when (wallet.network) {
            EthereumNetwork.SEPOLIA -> "Ethereum Sepolia"
            else -> "Ethereum"
        }
        is SolanaWallet -> "Solana"
        is USDCWallet -> "USDC ${getNetworkDisplay(wallet.network)}"
        is MultiChainWallet -> "Multi-Chain"
        else -> "Crypto Wallet"
    }
}

fun getNativeSymbol(wallet: CryptoWallet): String {
    return when (wallet) {
        is BitcoinWallet -> "BTC"
        is EthereumWallet -> when (wallet.network) {
            EthereumNetwork.SEPOLIA -> "ETH (Sepolia)"
            else -> "ETH"
        }
        is SolanaWallet -> "SOL"
        is USDCWallet -> "USDC"
        is MultiChainWallet -> "MULTI"
        else -> "CRYPTO"
    }
}

@Composable
fun WalletActionsCard(
    wallet: CryptoWallet,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onBackup: () -> Unit,
    onAddSampleTransaction: () -> Unit,
    onViewPrivateKey: () -> Unit
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
            // Main actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                ActionButton(
                    icon = Icons.Default.Backup,
                    label = "Backup",
                    onClick = onBackup
                )

                ActionButton(
                    icon = Icons.Default.Add,
                    label = "Sample",
                    onClick = onAddSampleTransaction
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Key,
                    label = "Private Key",
                    onClick = onViewPrivateKey
                )

                ActionButton(
                    icon = Icons.Default.Security,
                    label = "Security",
                    onClick = {
                        // Navigate to security settings
                    }
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun AddressCardWithQR(
    address: String,
    fullAddress: String,
    onCopy: (String) -> Unit,
    onShowFullQR: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wallet Address",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR Code Preview
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clickable { onShowFullQR() }
                    .clip(RoundedCornerShape(8.dp))
            ) {
                QrCodeDisplay(
                    address = fullAddress,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay for click hint
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "View Full QR",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onCopy(fullAddress) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onShowFullQR,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.QrCode2, "QR Code", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("QR Code")
                }
            }
        }
    }
}

@Composable
fun AddressCard(
    address: String,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Wallet Address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onCopy(address) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Address")
            }
        }
    }
}

@Composable
fun TransactionsSection(
    transactions: List<Transaction>,
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

                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (transactions.isEmpty()) {
                EmptyTransactionsView()
            } else {
                transactions.take(3).forEach { transaction ->
                    TransactionItem(transaction = transaction)
                    if (transactions.indexOf(transaction) < 2) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    color = when (transaction.status) {
                        TransactionStatus.SUCCESS -> Color.Green.copy(alpha = 0.1f)
                        TransactionStatus.PENDING -> Color.Yellow.copy(alpha = 0.1f)
                        TransactionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if ((transaction.value.toDoubleOrNull() ?: 0.0) > 0) {
                    Icons.Default.ArrowDownward
                } else {
                    Icons.Default.ArrowUpward
                },
                contentDescription = "Transaction",
                tint = when (transaction.status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color.Yellow
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (transaction.value.toDoubleOrNull() ?: 0.0 > 0) "Received" else "Sent",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDate(transaction.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${transaction.valueDecimal} ${transaction.chain.name.take(3)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = transaction.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = when (transaction.status) {
                    TransactionStatus.SUCCESS -> Color.Green
                    TransactionStatus.PENDING -> Color.Yellow
                    TransactionStatus.FAILED -> Color.Red
                }
            )
        }
    }
}

@Composable
fun EmptyWalletView(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Wallet Not Found",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wallet Not Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The wallet you're looking for doesn't exist or has been deleted",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back to Wallets")
        }
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
            imageVector = Icons.Default.Receipt,
            contentDescription = "No Transactions",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No Transactions Yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Your transaction history will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getNetworkDisplay(network: EthereumNetwork): String {
    return when (network) {
        EthereumNetwork.SEPOLIA -> "(Sepolia)"
        EthereumNetwork.MAINNET -> ""
        else -> "(${network.name})"
    }
}

private fun getSymbolForWallet(wallet: CryptoWallet): String {
    return when (wallet) {
        is BitcoinWallet -> "BTC"
        is EthereumWallet -> "ETH"
        is SolanaWallet -> "SOL"
        is USDCWallet -> "USDC"
        else -> ""
    }
}

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