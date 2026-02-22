package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.navigation.NavController
import com.example.nexuswallet.NavigationViewModel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun WalletDashboardScreen(
    navController: NavController,
    navigationViewModel: NavigationViewModel,
    padding: PaddingValues,
) {
    val wallets by navigationViewModel.wallets.collectAsState()
    val viewModel: WalletDashboardViewModel = hiltViewModel()
    val totalPortfolio by viewModel.totalPortfolioValue.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = Triple(isLoading, error, wallets.isEmpty()),
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(300))
        }
    ) { (loading, errorMsg, emptyWallets) ->
        when {
            loading -> LoadingScreen()
            errorMsg != null -> ErrorScreen(
                message = errorMsg,
                onRetry = { viewModel.refresh() }
            )
            emptyWallets -> EmptyWalletsScreen(
                onCreateWallet = { navController.navigate("createWallet") }
            )
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F2))
                    )

                    // Main content
                    DashboardContent(
                        wallets = wallets,
                        totalPortfolio = totalPortfolio,
                        balances = viewModel.balances.collectAsState().value,
                        onWalletClick = { wallet ->
                            navigationViewModel.navigateToWalletDetail(wallet.id)
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
                isRefreshing = isRefreshing
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
                    color = Color.Black,
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
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = { /* Open drawer */ }) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Menu"
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            scrolledContainerColor = Color.White
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
            containerColor = Color.White
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
                    color = Color(0xFF6B7280)
                )

                Box(
                    modifier = Modifier
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF374151)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US).format(animatedValue.value),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
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
                        tint = Color(0xFF6B7280)
                    )
                    Text(
                        text = walletCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
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
                        tint = Color(0xFF10B981)
                    )
                    Text(
                        text = "+2.4%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
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
                        tint = Color(0xFF6B7280)
                    )
                    Text(
                        text = "Secure",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wallet icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
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
                    // Wallet name
                    Text(
                        text = wallet.name.take(15) + if (wallet.name.length > 15) "..." else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Balance summary
                    balance?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val totalBalance = (it.bitcoin?.usdValue ?: 0.0) +
                                    (it.ethereum?.usdValue ?: 0.0) +
                                    (it.solana?.usdValue ?: 0.0) +
                                    (it.usdc?.usdValue ?: 0.0)

                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.US).format(totalBalance),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Show coin badges in a separate row
                    CoinBadgesRow(wallet = wallet)
                }

                // Expand/collapse indicator
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF6B7280)
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
fun CoinBadgesRow(wallet: Wallet) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        wallet.bitcoin?.let {
            CoinBadge(text = "BTC", color = Color(0xFFF7931A))
        }
        wallet.ethereum?.let {
            CoinBadge(text = "ETH", color = Color(0xFF627EEA))
        }
        wallet.solana?.let {
            CoinBadge(text = "SOL", color = Color(0xFF00FFA3))
        }
        wallet.usdc?.let {
            CoinBadge(text = "USDC", color = Color(0xFF2775CA))
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
            color = Color(0xFFE5E7EB),
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
                        color = Color(0xFFF7931A),
                        change = "+3.35%",
                        isPositive = true
                    )
                }
                it.ethereum?.let { eth ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.Diamond,
                        symbol = "Ethereum",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(eth.eth.toDoubleOrNull() ?: 0.0)} ETH",
                        usdValue = eth.usdValue,
                        color = Color(0xFF627EEA),
                        change = "+1.06%",
                        isPositive = true
                    )
                }
                it.solana?.let { sol ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.FlashOn,
                        symbol = "Solana",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(sol.sol.toDoubleOrNull() ?: 0.0)} SOL",
                        usdValue = sol.usdValue,
                        color = Color(0xFF00FFA3),
                        change = "+2.35%",
                        isPositive = true
                    )
                }
                it.usdc?.let { usdc ->
                    CoinBalanceRow(
                        icon = Icons.Outlined.AttachMoney,
                        symbol = "USDC",
                        amount = "${NumberFormat.getNumberInstance(Locale.US).format(usdc.amountDecimal.toDoubleOrNull() ?: 0.0)} USDC",
                        usdValue = usdc.usdValue,
                        color = Color(0xFF2775CA),
                        change = "-0.95%",
                        isPositive = false
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
                    contentColor = Color(0xFFEF4444)
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
    color: Color,
    change: String,
    isPositive: Boolean
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
                    color = Color.Black
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B7280)
                )
            }
        }

        // Right side - USD value and change
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                )
                Text(
                    text = change,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
        }
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
                containerColor = Color.White
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
                    tint = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "No Wallets Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Create your first wallet to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCreateWallet,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text(
                        "Create Wallet",
                        style = MaterialTheme.typography.labelLarge
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
            color = Color(0xFF3B82F6)
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
                containerColor = Color.White
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
                    tint = Color(0xFFEF4444)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text(
                        "Try Again",
                        style = MaterialTheme.typography.labelLarge
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
        containerColor = Color.White,
        title = {
            Text(
                text = "Delete Wallet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$walletName\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFEF4444)
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
                    contentColor = Color(0xFF6B7280)
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