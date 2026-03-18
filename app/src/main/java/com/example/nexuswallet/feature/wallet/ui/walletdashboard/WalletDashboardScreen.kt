package com.example.nexuswallet.feature.wallet.ui.walletdashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.InlineLoading
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun WalletDashboardScreen(
    onNavigateToWalletDetail: (String) -> Unit,
    onNavigateToCreateWallet: () -> Unit,
    padding: PaddingValues,
    viewModel: WalletDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val totalPortfolio by viewModel.totalPortfolioValue.collectAsStateWithLifecycle()
    val isOperationLoading by viewModel.isOperationLoading.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(operationError) {
        operationError?.let {
            viewModel.clearOperationError()
        }
    }

    // Refresh when screen comes to foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                onCreateWallet = onNavigateToCreateWallet,
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(padding)
        ) {
            when (val state = uiState) {
                is Result.Loading -> {
                    FullScreenLoading(message = "Loading wallets...")
                }

                is Result.Error -> {
                    EmptyWalletsContent(
                        onCreateWallet = onNavigateToCreateWallet,
                        isError = true,
                        errorMessage = state.message,
                        onRetry = {
                            isRefreshing = true
                            viewModel.refresh()
                        }
                    )
                }

                is Result.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyWalletsContent(
                            onCreateWallet = onNavigateToCreateWallet
                        )
                    } else {
                        DashboardContent(
                            wallets = state.data,
                            totalPortfolio = totalPortfolio,
                            balances = balances,
                            onWalletClick = { wallet ->
                                onNavigateToWalletDetail(wallet.id)
                            },
                            onDeleteWallet = { walletId ->
                                viewModel.deleteWallet(walletId)
                            }
                        )
                    }
                }
            }

            if (isOperationLoading && uiState is Result.Success && (uiState as Result.Success).data.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    InlineLoading(message = "Processing...")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    onCreateWallet: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Wallets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(
                onClick = onCreateWallet
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create Wallet",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    wallets: List<Wallet>,
    totalPortfolio: BigDecimal,
    balances: Map<String, WalletBalance>,
    onWalletClick: (Wallet) -> Unit,
    onDeleteWallet: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AnimatedPortfolioHeader(
                totalPortfolio = totalPortfolio,
                walletCount = wallets.size,
                isTablet = isTablet
            )
        }

        item {
            Text(
                text = "Your Wallets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (isTablet) {
            items(wallets.chunked(2)) { walletPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    walletPair.forEach { wallet ->
                        WalletCard(
                            wallet = wallet,
                            balance = balances[wallet.id],
                            onWalletClick = { onWalletClick(wallet) },
                            onDelete = { onDeleteWallet(wallet.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            items(wallets) { wallet ->
                WalletCard(
                    wallet = wallet,
                    balance = balances[wallet.id],
                    onWalletClick = { onWalletClick(wallet) },
                    onDelete = { onDeleteWallet(wallet.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WalletCard(
    wallet: Wallet,
    balance: WalletBalance?,
    onWalletClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteWalletDialog(
            walletName = wallet.name,
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val totalUsdValue = balance?.let {
        var total = 0.0
        it.bitcoinBalances.values.forEach { btc -> total += btc.usdValue }
        it.solanaBalances.values.forEach { sol -> total += sol.usdValue }
        it.evmBalances.forEach { evm -> total += evm.usdValue }
        total
    } ?: 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onWalletClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = wallet.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = totalUsdValue.formatAsCurrency(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = Int.MAX_VALUE
                    ) {
                        wallet.bitcoinCoins.forEach { coin ->
                            CoinBadge(
                                text = if (coin.network.isTestnet) "${coin.symbol} (Test)" else coin.symbol,
                                color = bitcoinLight,
                            )
                        }

                        wallet.solanaCoins.forEach { coin ->
                            CoinBadge(
                                text = if (coin.network.isTestnet) "${coin.symbol} (Dev)" else coin.symbol,
                                color = solanaLight,
                            )
                        }

                        val visibleTokens = wallet.evmTokens.take(5)
                        visibleTokens.forEach { token ->
                            CoinBadge(
                                text = token.symbol,
                                color = when (token.tokenType) {
                                    TokenType.NATIVE -> ethereumLight
                                    TokenType.USDC -> usdcLight
                                    TokenType.USDT -> Color(0xFF26A17B)
                                },
                            )
                        }

                        val remainingCount = wallet.evmTokens.size - 5
                        if (remainingCount > 0) {
                            CoinBadge(
                                text = "+$remainingCount",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        isExpanded = !isExpanded
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                WalletExpandedContent(
                    wallet = wallet,
                    balance = balance,
                    onDelete = { showDeleteDialog = true }
                )
            }
        }
    }
}

@Composable
fun WalletExpandedContent(
    wallet: Wallet,
    balance: WalletBalance?,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 12.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )

        // Bitcoin coins
        wallet.bitcoinCoins.forEach { coin ->
            val btcBalance = balance?.bitcoinBalances?.get(coin.network)

            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.bitcoin),
                symbol = "${coin.name}${if (coin.network.isTestnet) " (Testnet)" else ""}",
                amount = if (btcBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(btcBalance.btc.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = btcBalance?.usdValue ?: 0.0,
                color = bitcoinLight,
                showZeroBalance = true
            )
        }

        // Solana coins
        wallet.solanaCoins.forEach { coin ->
            val solBalance = balance?.solanaBalances?.get(coin.network)

            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.solana),
                symbol = "${coin.name}${if (coin.network.isTestnet) " (Devnet)" else ""}",
                amount = if (solBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(solBalance.sol.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = solBalance?.usdValue ?: 0.0,
                color = solanaLight,
                showZeroBalance = true
            )
        }

        // Group EVM tokens by tokenType
        val nativeTokens = wallet.evmTokens.filter { it.tokenType == TokenType.NATIVE }
        val usdcTokens = wallet.evmTokens.filter { it.tokenType == TokenType.USDC }
        val usdtTokens = wallet.evmTokens.filter { it.tokenType == TokenType.USDT }

        // Native ETH tokens
        nativeTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.tokenType == TokenType.NATIVE
            }
            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.ethereum),
                symbol = "${token.name}${if (token.network.isTestnet) " (${token.network.name})" else ""}",
                amount = if (tokenBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(tokenBalance.balanceDecimal.toDoubleOrNull() ?: 0.0)
                    } ${token.symbol}"
                else "0 ${token.symbol}",
                usdValue = tokenBalance?.usdValue ?: 0.0,
                color = ethereumLight,
                showZeroBalance = true
            )
        }

        // USDC tokens
        usdcTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.tokenType == TokenType.USDC
            }
            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.usdc),
                symbol = "${token.name}${if (token.network.isTestnet) " (${token.network.name})" else ""}",
                amount = if (tokenBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(tokenBalance.balanceDecimal.toDoubleOrNull() ?: 0.0)
                    } ${token.symbol}"
                else "0 ${token.symbol}",
                usdValue = tokenBalance?.usdValue ?: 0.0,
                color = usdcLight,
                showZeroBalance = true
            )
        }

        // USDT tokens
        usdtTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.tokenType == TokenType.USDT
            }
            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.tether),
                symbol = "${token.name}${if (token.network.isTestnet) " (${token.network.name})" else ""}",
                amount = if (tokenBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(tokenBalance.balanceDecimal.toDoubleOrNull() ?: 0.0)
                    } ${token.symbol}"
                else "0 ${token.symbol}",
                usdValue = tokenBalance?.usdValue ?: 0.0,
                color = Color(0xFF26A17B),
                showZeroBalance = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Delete Wallet",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun SimpleBalanceRow(
    icon: Any,
    symbol: String,
    amount: String,
    usdValue: Double,
    color: Color,
    showZeroBalance: Boolean = false
) {
    if (!showZeroBalance && usdValue <= 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (icon) {
                    is ImageVector -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    is Painter -> {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (usdValue > 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Text(
            text = usdValue.formatAsCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (usdValue > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (usdValue > 0)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun CoinBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnimatedPortfolioHeader(
    totalPortfolio: BigDecimal,
    walletCount: Int,
    isTablet: Boolean
) {
    var previousValue by remember { mutableStateOf(totalPortfolio) }
    val animatedValue = remember { Animatable(previousValue.toFloat()) }

    LaunchedEffect(totalPortfolio) {
        if (previousValue != totalPortfolio) {
            animatedValue.animateTo(
                targetValue = totalPortfolio.toFloat(),
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
            previousValue = totalPortfolio
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Portfolio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = totalPortfolio.toDouble().formatAsCurrency(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (isTablet) 36.sp else 28.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
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
                        text = walletCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.success
                    )
                    Text(
                        text = "+2.4%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.success
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Secure",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyWalletsContent(
    onCreateWallet: () -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Icon(
                    imageVector = if (isError) Icons.Outlined.Error else Icons.Outlined.AccountBalanceWallet,
                    contentDescription = if (isError) "Error" else "No Wallets",
                    modifier = Modifier.size(56.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isError) "Something went wrong" else "No Wallets Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isError && errorMessage != null)
                        errorMessage
                    else if (isError)
                        "Failed to load wallets"
                    else
                        "Create your first wallet to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = if (isError && onRetry != null) onRetry else onCreateWallet,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isError) "Try Again" else "Create Wallet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteWalletDialog(
    walletName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Delete Wallet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$walletName\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

fun Double.formatAsCurrency(): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(this)