package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun WalletDashboardScreen(
    onNavigateToWalletDetail: (String) -> Unit,
    onNavigateToCreateWallet: () -> Unit,
    padding: PaddingValues,
    viewModel: WalletDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val totalPortfolio by viewModel.totalPortfolioValue.collectAsState()
    val isOperationLoading by viewModel.isOperationLoading.collectAsState()
    val operationError by viewModel.operationError.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    // Show error snackbar if operation fails
    LaunchedEffect(operationError) {
        operationError?.let {
            // Handle error (show snackbar, etc.)
            viewModel.clearOperationError()
        }
    }

    when (val state = uiState) {
        is Result.Loading -> LoadingScreen()

        is Result.Error -> ErrorScreen(
            message = state.message,
            onRetry = { viewModel.refresh() }
        )

        is Result.Success -> {
            if (state.data.isEmpty()) {
                EmptyWalletsScreen(
                    onCreateWallet = onNavigateToCreateWallet
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )

                    // Main content
                    DashboardContent(
                        wallets = state.data,
                        totalPortfolio = totalPortfolio,
                        balances = balances,
                        isOperationLoading = isOperationLoading,
                        onWalletClick = { wallet ->
                            onNavigateToWalletDetail(wallet.id)
                        },
                        onDeleteWallet = { walletId ->
                            viewModel.deleteWallet(walletId)
                        },
                        onRefresh = {
                            isRefreshing = true
                            viewModel.refresh()
                            isRefreshing = false
                        },
                        isRefreshing = isRefreshing
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    wallets: List<Wallet>,
    totalPortfolio: BigDecimal,
    balances: Map<String, WalletBalance>,
    isOperationLoading: Boolean,
    onWalletClick: (Wallet) -> Unit,
    onDeleteWallet: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DashboardTopBar(
                scrollBehavior = scrollBehavior,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing || isOperationLoading
            )
        },
        floatingActionButton = {},
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Portfolio Summary Header
            item {
                AnimatedPortfolioHeader(
                    totalPortfolio = totalPortfolio,
                    walletCount = wallets.size,
                    isTablet = isTablet
                )
            }

            // Wallets Section Header
            item {
                Text(
                    text = "Your Wallets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Wallets Grid/List
            if (isTablet) {
                // Grid layout for tablets
                items(wallets.chunked(2)) { walletPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        walletPair.forEach { wallet ->
                            WalletCard(
                                wallet = wallet,
                                balance = balances[wallet.id],
                                onClick = { onWalletClick(wallet) },
                                onDelete = { onDeleteWallet(wallet.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // List layout for phones
                items(wallets) { wallet ->
                    WalletCard(
                        wallet = wallet,
                        balance = balances[wallet.id],
                        onClick = { onWalletClick(wallet) },
                        onDelete = { onDeleteWallet(wallet.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { /* Open drawer */ }) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
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

                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US).format(animatedValue.value),
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
                // Wallets stat
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

                // 24h change stat
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingUp,
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

                // Secure stat
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WalletCard(
    wallet: Wallet,
    balance: WalletBalance?,
    onClick: () -> Unit,
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
                .clickable { onClick() }
        ) {
            // Main card content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wallet icon
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

                // Wallet info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    balance?.let {
                        val totalBalance = (it.bitcoin?.usdValue ?: 0.0) +
                                (it.ethereum?.usdValue ?: 0.0) +
                                (it.solana?.usdValue ?: 0.0) +
                                (it.usdc?.usdValue ?: 0.0)

                        Text(
                            text = NumberFormat.getCurrencyInstance(Locale.US).format(totalBalance),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Show coin badges as small indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        wallet.bitcoin?.let {
                            CoinBadge(text = "BTC", color = bitcoinLight)
                        }
                        wallet.ethereum?.let {
                            CoinBadge(text = "ETH", color = ethereumLight)
                        }
                        wallet.solana?.let {
                            CoinBadge(text = "SOL", color = solanaLight)
                        }
                        wallet.usdc?.let {
                            CoinBadge(text = "USDC", color = usdcLight)
                        }
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
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

            // Expanded content with coin details
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
        Divider(
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Coin balances with icons
        balance?.let {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                it.bitcoin?.let { btc ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.CurrencyBitcoin,
                        symbol = "Bitcoin",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(btc.btc.toDoubleOrNull() ?: 0.0)} BTC",
                        usdValue = btc.usdValue,
                        color = bitcoinLight
                    )
                }
                it.ethereum?.let { eth ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.Diamond,
                        symbol = "Ethereum",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(eth.eth.toDoubleOrNull() ?: 0.0)} ETH",
                        usdValue = eth.usdValue,
                        color = ethereumLight
                    )
                }
                it.solana?.let { sol ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.FlashOn,
                        symbol = "Solana",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(sol.sol.toDoubleOrNull() ?: 0.0)} SOL",
                        usdValue = sol.usdValue,
                        color = solanaLight
                    )
                }
                it.usdc?.let { usdc ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.AttachMoney,
                        symbol = "USDC",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(usdc.amountDecimal.toDoubleOrNull() ?: 0.0)} USDC",
                        usdValue = usdc.usdValue,
                        color = usdcLight
                    )
                }
            }
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
fun CoinBalanceRow(
    icon: ImageVector,
    symbol: String,
    amount: String,
    usdValue: Double,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side - icon and details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Right side - USD value
        Text(
            text = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun EmptyWalletsScreen(
    onCreateWallet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "No Wallets",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "No Wallets Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Create your first wallet to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCreateWallet,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Create Wallet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Try Again",
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