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
import androidx.compose.foundation.BorderStroke
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
import com.example.nexuswallet.feature.core.ui.clickableSingle
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.ui.common.AssetChip
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
                    FullScreenLoading(
                        modifier = Modifier
                            .padding(top = scaffoldPadding.calculateTopPadding())
                            .padding(bottom = padding.calculateBottomPadding()),
                        message = stringResource(R.string.loading_wallets)
                    )
                }

                is Result.Error -> {
                    EmptyWalletsContent(
                        modifier = Modifier
                            .padding(top = scaffoldPadding.calculateTopPadding())
                            .padding(bottom = padding.calculateBottomPadding()),
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
                            modifier = Modifier
                                .padding(top = scaffoldPadding.calculateTopPadding())
                                .padding(bottom = padding.calculateBottomPadding()),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.wallets_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
            }
        },
        actions = {
            Box {
                Surface(
                    onClick = { showMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.create_wallet),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .width(200.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    offset = androidx.compose.ui.unit.DpOffset(x = (-16).dp, y = 8.dp)
                ) {
                    DropdownMenuItem(
                        text = { 
                            Text(
                                stringResource(R.string.create_new_wallet),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            ) 
                        },
                        onClick = {
                            showMenu = false
                            onCreateWallet()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Add, 
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { 
                            Text(
                                stringResource(R.string.import_existing_wallet),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            ) 
                        },
                        onClick = {
                            showMenu = false
                            onImportWallet()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Shield, 
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickableSingle { onWalletClick() },
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = if (isPrivacyModeEnabled) "****" else totalUsdValue.formatCurrency(
                                selectedCurrency
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wallet.bitcoinCoins.forEach { coin ->
                            AssetChip(
                                text = coin.symbol + if (coin.network.isTestnet) " (Test)" else "",
                                color = bitcoinLight
                            )
                        }

                        wallet.solanaCoins.forEach { coin ->
                            AssetChip(
                                text = coin.symbol + if (coin.network.isTestnet) " (Dev)" else "",
                                color = solanaLight
                            )
                        }

                        val visibleTokens = wallet.evmTokens.take(3)
                        visibleTokens.forEach { token ->
                            AssetChip(
                                text = token.symbol,
                                color = when (token.evmTokenType) {
                                    EVMTokenType.NATIVE -> ethereumLight
                                    EVMTokenType.USDC -> usdcLight
                                    EVMTokenType.USDT -> Color(0xFF26A17B)
                                }
                            )
                        }

                        val remainingCount = wallet.evmTokens.size - 3
                        if (remainingCount > 0) {
                            AssetChip(
                                text = "+$remainingCount",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        isExpanded = !isExpanded
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                        .background(
                            if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Bitcoin coins
        wallet.bitcoinCoins.forEach { coin ->
            val btcBalance = balance?.bitcoinBalances?.get(coin.network)

            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.bitcoin),
                symbol = coin.name,
                networkName = if (coin.network.isTestnet) stringResource(R.string.testnet_suffix) else null,
                amount = if (btcBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(btcBalance.btc.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = btcBalance?.usdValue ?: 0.0,
                color = bitcoinLight,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency
            )
        }

        // Solana coins
        wallet.solanaCoins.forEach { coin ->
            val solBalance = balance?.solanaBalances?.get(coin.network)

            SimpleBalanceRow(
                icon = painterResource(id = R.drawable.solana),
                symbol = coin.name,
                networkName = if (coin.network.isTestnet) stringResource(R.string.devnet_suffix) else null,
                amount = if (solBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(solBalance.sol.toDoubleOrNull() ?: 0.0)
                    } ${coin.symbol}"
                else "0 ${coin.symbol}",
                usdValue = solBalance?.usdValue ?: 0.0,
                color = solanaLight,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency
            )
        }

        // Group EVM tokens
        wallet.evmTokens.forEach { token ->
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.evmTokenType == token.evmTokenType
            }
            val (color, iconRes) = when (token.evmTokenType) {
                EVMTokenType.NATIVE -> ethereumLight to R.drawable.ethereum
                EVMTokenType.USDC -> usdcLight to R.drawable.usdc
                EVMTokenType.USDT -> Color(0xFF26A17B) to R.drawable.tether
            }

            SimpleBalanceRow(
                icon = painterResource(id = iconRes),
                symbol = token.name,
                networkName = if (token.network.isTestnet) "(${token.network.name})" else null,
                amount = if (tokenBalance != null)
                    "${
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(tokenBalance.balanceDecimal.toDoubleOrNull() ?: 0.0)
                    } ${token.symbol}"
                else "0 ${token.symbol}",
                usdValue = tokenBalance?.usdValue ?: 0.0,
                color = color,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
                selectedCurrency = selectedCurrency
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.rename_wallet))
            }

            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                elevation = null
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.delete_wallet))
            }
        }
    }
}

@Composable
fun SimpleBalanceRow(
    icon: Any,
    symbol: String,
    networkName: String?,
    amount: String,
    usdValue: Double,
    color: Color,
    isPrivacyModeEnabled: Boolean,
    selectedCurrency: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                    is Painter -> Icon(icon, null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (networkName != null) {
                    Text(
                        text = " $networkName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = if (isPrivacyModeEnabled) "****" else amount,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (isPrivacyModeEnabled) "****" else usdValue.formatCurrency(selectedCurrency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (usdValue > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.total_portfolio),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "+2.4%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isPrivacyModeEnabled) "****" else animatedValue.value.toDouble()
                        .formatCurrency(selectedCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PortfolioStatItem(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        label = "$walletCount Wallets"
                    )
                    PortfolioStatItem(
                        icon = Icons.Outlined.Shield,
                        label = stringResource(R.string.secure)
                    )
                }
            }
        }
    }
}

@Composable
fun PortfolioStatItem(
    icon: ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun EmptyWalletsContent(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isError) Icons.Outlined.Error else Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isError) stringResource(R.string.something_went_wrong) else stringResource(
                        R.string.no_wallets_yet
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isError && errorMessage != null)
                        errorMessage
                    else if (isError)
                        stringResource(R.string.failed_to_load_wallets)
                    else
                        stringResource(R.string.create_first_wallet_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (isError) {
                    Button(
                        onClick = onRetry ?: {},
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            stringResource(R.string.try_again),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onCreateWallet,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.create_new_wallet),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onImportWallet,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                stringResource(R.string.import_existing_wallet),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
