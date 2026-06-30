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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.nexuswallet.feature.core.ui.NexusTextField
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.InlineLoading
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.usdcLight
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun WalletDashboardScreen(
    onNavigateToWalletDetail: (String) -> Unit,
    onNavigateToCreateWallet: () -> Unit,
    onNavigateToImportWallet: () -> Unit,
    padding: PaddingValues,
    viewModel: WalletDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val totalPortfolio by viewModel.totalPortfolioValue.collectAsStateWithLifecycle()
    val isOperationLoading by viewModel.isOperationLoading.collectAsStateWithLifecycle()
    val isRefreshingState by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val isPrivacyModeEnabled by viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf<Wallet?>(null) }

    LaunchedEffect(operationError) {
        operationError?.let {
            viewModel.clearOperationError()
        }
    }

    // Refresh when screen comes to foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    if (showRenameDialog != null) {
        RenameWalletDialog(
            currentName = showRenameDialog!!.name,
            onConfirm = { newName ->
                viewModel.renameWallet(showRenameDialog!!.id, newName)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    Scaffold(
        topBar = {
            Column {
                DashboardTopBar(
                    onCreateWallet = onNavigateToCreateWallet,
                    onImportWallet = onNavigateToImportWallet
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                ) {
                    if (isRefreshingState) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is Result.Loading -> {
                    FullScreenLoading(message = stringResource(R.string.loading_wallets))
                }

                is Result.Error -> {
                    EmptyWalletsContent(
                        onCreateWallet = onNavigateToCreateWallet,
                        onImportWallet = onNavigateToImportWallet,
                        isError = true,
                        errorMessage = state.message,
                        onRetry = {
                            viewModel.refresh()
                        }
                    )
                }

                is Result.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyWalletsContent(
                            onCreateWallet = onNavigateToCreateWallet,
                            onImportWallet = onNavigateToImportWallet
                        )
                    } else {
                        DashboardContent(
                            wallets = state.data,
                            totalPortfolio = totalPortfolio,
                            balances = balances,
                            isPrivacyModeEnabled = isPrivacyModeEnabled,
                            selectedCurrency = selectedCurrency,
                            onWalletClick = { wallet ->
                                onNavigateToWalletDetail(wallet.id)
                            },
                            onDeleteWallet = { walletId ->
                                viewModel.deleteWallet(walletId)
                            },
                            onRenameWallet = { wallet ->
                                showRenameDialog = wallet
                            },
                            contentPadding = PaddingValues(
                                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                                bottom = scaffoldPadding.calculateBottomPadding() + padding.calculateBottomPadding() + 16.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
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
                    InlineLoading(message = stringResource(R.string.processing))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.wallets_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            Box {
                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.create_wallet),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_new_wallet)) },
                        onClick = {
                            showMenu = false
                            onCreateWallet()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_existing_wallet)) },
                        onClick = {
                            showMenu = false
                            onImportWallet()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Shield, contentDescription = null)
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    wallets: List<Wallet>,
    totalPortfolio: BigDecimal,
    balances: Map<String, WalletBalance>,
    isPrivacyModeEnabled: Boolean,
    selectedCurrency: String,
    onWalletClick: (Wallet) -> Unit,
    onDeleteWallet: (String) -> Unit,
    onRenameWallet: (Wallet) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AnimatedPortfolioHeader(
                totalPortfolio = totalPortfolio,
                walletCount = wallets.size,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                isTablet = isTablet,
                selectedCurrency = selectedCurrency
            )
        }

        item {
            Text(
                text = stringResource(R.string.your_wallets),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (isTablet) {
            items(
                items = wallets.chunked(2),
                key = { pair -> pair.joinToString("-") { it.id } }
            ) { walletPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    walletPair.forEach { wallet ->
                        WalletCard(
                            wallet = wallet,
                            balance = balances[wallet.id],
                            isPrivacyModeEnabled = isPrivacyModeEnabled,
                            selectedCurrency = selectedCurrency,
                            onWalletClick = { onWalletClick(wallet) },
                            onDelete = { onDeleteWallet(wallet.id) },
                            onRename = { onRenameWallet(wallet) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            items(
                items = wallets,
                key = { it.id }
            ) { wallet ->
                WalletCard(
                    wallet = wallet,
                    balance = balances[wallet.id],
                    isPrivacyModeEnabled = isPrivacyModeEnabled,
                    selectedCurrency = selectedCurrency,
                    onWalletClick = { onWalletClick(wallet) },
                    onDelete = { onDeleteWallet(wallet.id) },
                    onRename = { onRenameWallet(wallet) }
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
    isPrivacyModeEnabled: Boolean,
    selectedCurrency: String,
    onWalletClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
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
                        contentDescription = stringResource(R.string.wallet_icon),
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
                            text = if (isPrivacyModeEnabled) "****" else totalUsdValue.formatCurrency(
                                selectedCurrency
                            ),
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
                                text = if (coin.network.isTestnet) "${coin.symbol} ${
                                    stringResource(
                                        R.string.test_suffix
                                    )
                                }" else coin.symbol,
                                color = bitcoinLight,
                            )
                        }

                        wallet.solanaCoins.forEach { coin ->
                            CoinBadge(
                                text = if (coin.network.isTestnet) "${coin.symbol} ${
                                    stringResource(
                                        R.string.dev_suffix
                                    )
                                }" else coin.symbol,
                                color = solanaLight,
                            )
                        }

                        val visibleTokens = wallet.evmTokens.take(5)
                        visibleTokens.forEach { token ->
                            CoinBadge(
                                text = token.symbol,
                                color = when (token.evmTokenType) {
                                    EVMTokenType.NATIVE -> ethereumLight
                                    EVMTokenType.USDC -> usdcLight
                                    EVMTokenType.USDT -> Color(0xFF26A17B)
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
                        contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(
                            R.string.expand
                        ),
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
                    isPrivacyModeEnabled = isPrivacyModeEnabled,
                    selectedCurrency = selectedCurrency,
                    onDelete = { showDeleteDialog = true },
                    onRename = onRename
                )
            }
        }
    }
}

@Composable
fun WalletExpandedContent(
    wallet: Wallet,
    balance: WalletBalance?,
    isPrivacyModeEnabled: Boolean,
    selectedCurrency: String,
    onDelete: () -> Unit,
    onRename: () -> Unit
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
                symbol = "${coin.name}${if (coin.network.isTestnet) " ${stringResource(R.string.testnet_suffix)}" else ""}",
                amount = if (btcBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(btcBalance.btc.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = btcBalance?.usdValue ?: 0.0,
                color = bitcoinLight,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency,
                showZeroBalance = true
            )
        }

        // Solana coins
        wallet.solanaCoins.forEach { coin ->
            val solBalance = balance?.solanaBalances?.get(coin.network)

            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.solana),
                symbol = "${coin.name}${if (coin.network.isTestnet) " ${stringResource(R.string.devnet_suffix)}" else ""}",
                amount = if (solBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(solBalance.sol.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = solBalance?.usdValue ?: 0.0,
                color = solanaLight,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency,
                showZeroBalance = true
            )
        }

        // Group EVM tokens by tokenType
        val nativeTokens = wallet.evmTokens.filter { it.evmTokenType == EVMTokenType.NATIVE }
        val usdcTokens = wallet.evmTokens.filter { it.evmTokenType == EVMTokenType.USDC }
        val usdtTokens = wallet.evmTokens.filter { it.evmTokenType == EVMTokenType.USDT }

        // Native ETH tokens
        nativeTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.evmTokenType == EVMTokenType.NATIVE
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
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency,
                showZeroBalance = true
            )
        }

        // USDC tokens
        usdcTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.evmTokenType == EVMTokenType.USDC
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
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency,
                showZeroBalance = true
            )
        }

        // USDT tokens
        usdtTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.evmTokenType == EVMTokenType.USDT
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
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency,
                showZeroBalance = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onRename
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rename_wallet),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.rename_wallet),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.delete_wallet),
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
    isPrivacyModeEnabled: Boolean,
    selectedCurrency: String,
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
                    text = if (isPrivacyModeEnabled) "****" else amount,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (usdValue > 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Text(
            text = if (isPrivacyModeEnabled) "****" else usdValue.formatCurrency(selectedCurrency),
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
    isPrivacyModeEnabled: Boolean,
    isTablet: Boolean,
    selectedCurrency: String
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

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Text(
                text = stringResource(R.string.total_portfolio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Value
            Text(
                text = if (isPrivacyModeEnabled) "****" else animatedValue.value.toDouble()
                    .formatCurrency(selectedCurrency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = if (isTablet) 36.sp else 28.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // Wallet count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = walletCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Growth (static for now)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "+2.4%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Security
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = stringResource(R.string.secure),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyWalletsContent(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
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
                    contentDescription = if (isError) stringResource(R.string.error) else stringResource(
                        R.string.no_wallets_yet
                    ),
                    modifier = Modifier.size(56.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isError) stringResource(R.string.something_went_wrong) else stringResource(
                        R.string.no_wallets_yet
                    ),
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
                        stringResource(R.string.failed_to_load_wallets)
                    else
                        stringResource(R.string.create_first_wallet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isError) {
                    Button(
                        onClick = onRetry ?: {},
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            stringResource(R.string.try_again),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onCreateWallet,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.create_new_wallet))
                        }

                        OutlinedButton(
                            onClick = onImportWallet,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.import_existing_wallet))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameWalletDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.rename_wallet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                NexusTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.new_wallet_name),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && name != currentName
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
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
                text = stringResource(R.string.delete_wallet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_wallet_confirmation, walletName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onError
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
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
