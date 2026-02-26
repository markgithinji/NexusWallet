package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendViewModel
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendEvent
import com.example.nexuswallet.feature.coin.ethereum.EthereumSendViewModel
import com.example.nexuswallet.feature.coin.solana.SolanaFeeEstimate
import com.example.nexuswallet.feature.coin.solana.SolanaSendEvent
import com.example.nexuswallet.feature.coin.solana.SolanaSendViewModel
import com.example.nexuswallet.feature.coin.usdc.USDCSendEvent
import com.example.nexuswallet.feature.coin.usdc.USDCSendViewModel
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import com.example.nexuswallet.ui.theme.bitcoinLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.info
import com.example.nexuswallet.ui.theme.infoContainer
import com.example.nexuswallet.ui.theme.solanaLight
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.warning
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, CoinType, String, String, FeeLevel?) -> Unit,
    walletId: String,
    coinType: CoinType,
    ethereumViewModel: EthereumSendViewModel = hiltViewModel(),
    usdcViewModel: USDCSendViewModel = hiltViewModel(),
    solanaViewModel: SolanaSendViewModel = hiltViewModel(),
    bitcoinViewModel: BitcoinSendViewModel = hiltViewModel()
) {
    var showMaxDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val ethereumUiState = ethereumViewModel.uiState.collectAsState()
    val usdcState = usdcViewModel.state.collectAsState()
    val solanaState = solanaViewModel.state.collectAsState()
    val bitcoinState = bitcoinViewModel.state.collectAsState()

    // Initialize ViewModels
    LaunchedEffect(Unit) {
        when (coinType) {
            CoinType.ETHEREUM -> ethereumViewModel.initialize(walletId)
            CoinType.USDC -> usdcViewModel.init(walletId)
            CoinType.SOLANA -> solanaViewModel.init(walletId)
            CoinType.BITCOIN -> bitcoinViewModel.init(walletId)
        }
    }

    // Determine loading state
    val isLoading = when (coinType) {
        CoinType.ETHEREUM -> ethereumUiState.value.isLoading
        CoinType.USDC -> usdcState.value.isLoading
        CoinType.SOLANA -> solanaState.value.isLoading
        CoinType.BITCOIN -> bitcoinState.value.isLoading
    }

    // Get coin display config using your coin-specific colors
    val (coinColor, icon, displayName) = when (coinType) {
        CoinType.BITCOIN -> Triple(bitcoinLight, Icons.Outlined.CurrencyBitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(ethereumLight, Icons.Outlined.Diamond, "Ethereum")
        CoinType.SOLANA -> Triple(solanaLight, Icons.Outlined.FlashOn, "Solana")
        CoinType.USDC -> Triple(usdcLight, Icons.Outlined.AttachMoney, "USDC")
    }

    Scaffold(
        topBar = {
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
                            text = "Send $displayName",
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollable content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Balance Card
                item {
                    when (coinType) {
                        CoinType.ETHEREUM -> {
                            SendBalanceCard(
                                balance = ethereumUiState.value.balance,
                                balanceFormatted = "${ethereumUiState.value.balance.setScale(6, RoundingMode.HALF_UP)} ETH",
                                coinType = coinType,
                                address = ethereumUiState.value.fromAddress,
                                network = ethereumUiState.value.network
                            )
                        }
                        CoinType.USDC -> {
                            SendBalanceCard(
                                balance = usdcState.value.usdcBalanceDecimal,
                                balanceFormatted = "${usdcState.value.usdcBalanceDecimal.setScale(2, RoundingMode.HALF_UP)} USDC",
                                coinType = coinType,
                                address = usdcState.value.fromAddress,
                                secondaryBalance = usdcState.value.ethBalanceDecimal,
                                secondaryBalanceFormatted = "${usdcState.value.ethBalanceDecimal.setScale(4, RoundingMode.HALF_UP)} ETH",
                                network = usdcState.value.network.displayName
                            )
                        }
                        CoinType.SOLANA -> {
                            SendBalanceCard(
                                balance = solanaState.value.balance,
                                balanceFormatted = solanaState.value.balanceFormatted,
                                coinType = coinType,
                                address = solanaState.value.walletAddress
                            )
                        }
                        CoinType.BITCOIN -> {
                            SendBalanceCard(
                                balance = bitcoinState.value.balance,
                                balanceFormatted = bitcoinState.value.balanceFormatted,
                                coinType = coinType,
                                address = bitcoinState.value.walletAddress,
                                network = bitcoinState.value.network.name
                            )
                        }
                    }
                }

                // Error/Info Messages
                when (coinType) {
                    CoinType.ETHEREUM -> {
                        ethereumUiState.value.error?.let { error ->
                            item {
                                ErrorMessage(
                                    error = error,
                                    onDismiss = { ethereumViewModel.onEvent(EthereumSendEvent.ClearError) }
                                )
                            }
                        }
                    }
                    CoinType.USDC -> {
                        usdcState.value.error?.let { error ->
                            item {
                                ErrorMessage(
                                    error = error,
                                    onDismiss = { usdcViewModel.onEvent(USDCSendEvent.ClearError) }
                                )
                            }
                        }
                    }
                    CoinType.BITCOIN -> {
                        bitcoinState.value.error?.let { error ->
                            item {
                                ErrorMessage(
                                    error = error,
                                    onDismiss = { bitcoinViewModel.clearError() }
                                )
                            }
                        }
                    }
                    // SOLANA errors are shown inline via validation
                    else -> {}
                }

                // Address Input
                item {
                    when (coinType) {
                        CoinType.ETHEREUM -> {
                            SendAddressInput(
                                toAddress = ethereumUiState.value.toAddress,
                                onAddressChange = { ethereumViewModel.onEvent(EthereumSendEvent.ToAddressChanged(it)) },
                                coinType = coinType,
                                isValid = ethereumUiState.value.validationResult.isValid &&
                                        ethereumUiState.value.validationResult.addressError == null,
                                errorMessage = ethereumUiState.value.validationResult.addressError
                                    ?: ethereumUiState.value.validationResult.selfSendError,
                                onPaste = { pastedText ->
                                    ethereumViewModel.onEvent(EthereumSendEvent.ToAddressChanged(pastedText))
                                }
                            )
                        }
                        CoinType.USDC -> {
                            SendAddressInput(
                                toAddress = usdcState.value.toAddress,
                                onAddressChange = { usdcViewModel.onEvent(USDCSendEvent.ToAddressChanged(it)) },
                                coinType = coinType,
                                isValid = usdcState.value.validationResult.isValidAddress,
                                errorMessage = usdcState.value.validationResult.addressError,
                                onPaste = { pastedText ->
                                    usdcViewModel.onEvent(USDCSendEvent.ToAddressChanged(pastedText))
                                }
                            )
                        }
                        CoinType.SOLANA -> {
                            SendAddressInput(
                                toAddress = solanaState.value.toAddress,
                                onAddressChange = { solanaViewModel.onEvent(SolanaSendEvent.ToAddressChanged(it)) },
                                coinType = coinType,
                                isValid = solanaState.value.validationResult.isValid &&
                                        solanaState.value.validationResult.addressError == null,
                                errorMessage = solanaState.value.validationResult.addressError
                                    ?: solanaState.value.validationResult.selfSendError,
                                onPaste = { pastedText ->
                                    solanaViewModel.onEvent(SolanaSendEvent.ToAddressChanged(pastedText))
                                }
                            )
                        }
                        CoinType.BITCOIN -> {
                            SendAddressInput(
                                toAddress = bitcoinState.value.toAddress,
                                onAddressChange = { bitcoinViewModel.updateAddress(it) },
                                coinType = coinType,
                                isValid = bitcoinState.value.validationResult.isValid &&
                                        bitcoinState.value.validationResult.addressError == null,
                                errorMessage = bitcoinState.value.validationResult.addressError
                                    ?: bitcoinState.value.validationResult.selfSendError,
                                network = bitcoinState.value.network,
                                onPaste = { pastedText ->
                                    bitcoinViewModel.updateAddress(pastedText)
                                }
                            )
                        }
                    }
                }

                // Amount Input
                item {
                    when (coinType) {
                        CoinType.ETHEREUM -> {
                            SendAmountInput(
                                amount = ethereumUiState.value.amount,
                                onAmountChange = { ethereumViewModel.onEvent(EthereumSendEvent.AmountChanged(it)) },
                                balance = ethereumUiState.value.balance,
                                coinType = coinType,
                                onMaxClick = { showMaxDialog = true },
                                errorMessage = ethereumUiState.value.validationResult.amountError
                                    ?: ethereumUiState.value.validationResult.balanceError
                            )
                        }
                        CoinType.USDC -> {
                            SendAmountInput(
                                amount = usdcState.value.amount,
                                onAmountChange = { usdcViewModel.onEvent(USDCSendEvent.AmountChanged(it)) },
                                balance = usdcState.value.usdcBalanceDecimal,
                                coinType = coinType,
                                tokenSymbol = "USDC",
                                onMaxClick = { showMaxDialog = true },
                                errorMessage = usdcState.value.validationResult.amountError
                                    ?: usdcState.value.validationResult.balanceError
                            )
                        }
                        CoinType.SOLANA -> {
                            SendAmountInput(
                                amount = solanaState.value.amount,
                                onAmountChange = { solanaViewModel.onEvent(SolanaSendEvent.AmountChanged(it)) },
                                balance = solanaState.value.balance,
                                coinType = coinType,
                                onMaxClick = { showMaxDialog = true },
                                errorMessage = solanaState.value.validationResult.amountError
                                    ?: solanaState.value.validationResult.balanceError
                            )
                        }
                        CoinType.BITCOIN -> {
                            SendAmountInput(
                                amount = bitcoinState.value.amount,
                                onAmountChange = { bitcoinViewModel.updateAmount(it) },
                                balance = bitcoinState.value.balance,
                                coinType = coinType,
                                onMaxClick = { showMaxDialog = true },
                                errorMessage = bitcoinState.value.validationResult.amountError
                                    ?: bitcoinState.value.validationResult.balanceError
                            )
                        }
                    }
                }

                // Fee Selection
                item {
                    when (coinType) {
                        CoinType.ETHEREUM -> {
                            SendFeeSelection(
                                feeLevel = ethereumUiState.value.feeLevel,
                                onFeeLevelChange = { ethereumViewModel.onEvent(EthereumSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = ethereumUiState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        CoinType.USDC -> {
                            SendFeeSelection(
                                feeLevel = usdcState.value.feeLevel,
                                onFeeLevelChange = { usdcViewModel.onEvent(USDCSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = usdcState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        CoinType.SOLANA -> {
                            SendFeeSelection(
                                feeLevel = solanaState.value.feeLevel,
                                onFeeLevelChange = { solanaViewModel.onEvent(SolanaSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = solanaState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        CoinType.BITCOIN -> {
                            SendFeeSelection(
                                feeLevel = bitcoinState.value.feeLevel,
                                onFeeLevelChange = { bitcoinViewModel.updateFeeLevel(it) },
                                feeEstimate = bitcoinState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Bottom Bar
            SendBottomBar(
                isValid = when (coinType) {
                    CoinType.ETHEREUM -> ethereumUiState.value.validationResult.isValid
                    CoinType.USDC -> usdcState.value.validationResult.isValid
                    CoinType.SOLANA -> solanaState.value.validationResult.isValid
                    CoinType.BITCOIN -> bitcoinState.value.validationResult.isValid
                },
                isLoading = isLoading,
                error = when (coinType) {
                    CoinType.ETHEREUM -> ethereumUiState.value.error
                    CoinType.USDC -> usdcState.value.error
                    CoinType.SOLANA -> solanaState.value.error
                    CoinType.BITCOIN -> bitcoinState.value.error
                },
                onSend = {
                    when (coinType) {
                        CoinType.ETHEREUM -> {
                            ethereumViewModel.send { txHash ->
                                onNavigateToReview(
                                    walletId,
                                    CoinType.ETHEREUM,
                                    ethereumUiState.value.toAddress,
                                    ethereumUiState.value.amount,
                                    ethereumUiState.value.feeLevel
                                )
                            }
                        }
                        CoinType.USDC -> {
                            usdcViewModel.send { txHash ->
                                onNavigateToReview(
                                    walletId,
                                    CoinType.USDC,
                                    usdcState.value.toAddress,
                                    usdcState.value.amount,
                                    usdcState.value.feeLevel
                                )
                            }
                        }
                        CoinType.SOLANA -> {
                            solanaViewModel.send { txHash ->
                                onNavigateToReview(
                                    walletId,
                                    CoinType.SOLANA,
                                    solanaState.value.toAddress,
                                    solanaState.value.amount,
                                    solanaState.value.feeLevel
                                )
                            }
                        }
                        CoinType.BITCOIN -> {
                            onNavigateToReview(
                                walletId,
                                CoinType.BITCOIN,
                                bitcoinState.value.toAddress,
                                bitcoinState.value.amount,
                                bitcoinState.value.feeLevel
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Max Amount Dialog
    if (showMaxDialog) {
        when (coinType) {
            CoinType.ETHEREUM -> {
                MaxAmountDialog(
                    balance = ethereumUiState.value.balance,
                    feeEstimate = ethereumUiState.value.feeEstimate,
                    tokenSymbol = "ETH",
                    coinType = coinType,
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        ethereumViewModel.onEvent(EthereumSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            CoinType.USDC -> {
                MaxAmountDialog(
                    balance = usdcState.value.usdcBalanceDecimal,
                    feeEstimate = usdcState.value.feeEstimate,
                    tokenSymbol = "USDC",
                    coinType = coinType,
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        usdcViewModel.onEvent(USDCSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            CoinType.SOLANA -> {
                MaxAmountDialog(
                    balance = solanaState.value.balance,
                    feeEstimate = solanaState.value.feeEstimate,
                    tokenSymbol = "SOL",
                    coinType = coinType,
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        solanaViewModel.onEvent(SolanaSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            CoinType.BITCOIN -> {
                MaxAmountDialog(
                    balance = bitcoinState.value.balance,
                    feeEstimate = bitcoinState.value.feeEstimate,
                    tokenSymbol = "BTC",
                    coinType = coinType,
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        bitcoinViewModel.updateAmount(maxAmount)
                        showMaxDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun SendBalanceCard(
    balance: BigDecimal,
    balanceFormatted: String,
    coinType: CoinType,
    address: String,
    secondaryBalance: BigDecimal? = null,
    secondaryBalanceFormatted: String? = null,
    network: String? = null
) {
    val (coinColor, icon, displayName) = when (coinType) {
        CoinType.BITCOIN -> Triple(bitcoinLight, Icons.Outlined.CurrencyBitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(ethereumLight, Icons.Outlined.Diamond, "Ethereum")
        CoinType.SOLANA -> Triple(solanaLight, Icons.Outlined.FlashOn, "Solana")
        CoinType.USDC -> Triple(usdcLight, Icons.Outlined.AttachMoney, "USDC")
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
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "$${
                            String.format(
                                "%.2f",
                                balance.toDouble() * getUsdRate(coinType)
                            )
                        }",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = balanceFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = coinColor
                    )

                    if (secondaryBalance != null && secondaryBalanceFormatted != null) {
                        Text(
                            text = secondaryBalanceFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (network != null && network != "MAINNET" && network != "Mainnet") {
                        Text(
                            text = "Network: $network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "From: ${address.take(6)}...${address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SendAddressInput(
    toAddress: String,
    onAddressChange: (String) -> Unit,
    coinType: CoinType,
    isValid: Boolean = true,
    errorMessage: String? = null,
    network: BitcoinNetwork? = null,
    onPaste: (String) -> Unit
) {
    val context = LocalContext.current
    val (coinColor, _, _) = when (coinType) {
        CoinType.BITCOIN -> Triple(bitcoinLight, Icons.Outlined.CurrencyBitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(ethereumLight, Icons.Outlined.Diamond, "Ethereum")
        CoinType.SOLANA -> Triple(solanaLight, Icons.Outlined.FlashOn, "Solana")
        CoinType.USDC -> Triple(usdcLight, Icons.Outlined.AttachMoney, "USDC")
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recipient Address",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = toAddress,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = when (coinType) {
                            CoinType.BITCOIN -> {
                                val networkHint = if (network == BitcoinNetwork.TESTNET)
                                    " (testnet)" else ""
                                "Enter Bitcoin address$networkHint"
                            }
                            CoinType.ETHEREUM, CoinType.USDC -> "Enter Ethereum address (0x...)"
                            CoinType.SOLANA -> "Enter Solana address"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = toAddress.isNotEmpty() && !isValid,
                supportingText = if (errorMessage != null) {
                    {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else null,
                trailingIcon = {
                    if (toAddress.isNotEmpty()) {
                        IconButton(
                            onClick = { onAddressChange("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { /* Scan QR code */ },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Scan",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        val pastedText = clip?.getItemAt(0)?.text?.toString()
                        if (!pastedText.isNullOrBlank()) {
                            onPaste(pastedText)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Paste",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun SendAmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    balance: BigDecimal,
    coinType: CoinType,
    tokenSymbol: String? = null,
    onMaxClick: () -> Unit,
    errorMessage: String? = null
) {
    val symbol = tokenSymbol ?: when (coinType) {
        CoinType.BITCOIN -> "BTC"
        CoinType.ETHEREUM -> "ETH"
        CoinType.SOLANA -> "SOL"
        CoinType.USDC -> "USDC"
    }
    val (coinColor, _, _) = when (coinType) {
        CoinType.BITCOIN -> Triple(bitcoinLight, Icons.Outlined.CurrencyBitcoin, "Bitcoin")
        CoinType.ETHEREUM -> Triple(ethereumLight, Icons.Outlined.Diamond, "Ethereum")
        CoinType.SOLANA -> Triple(solanaLight, Icons.Outlined.FlashOn, "Solana")
        CoinType.USDC -> Triple(usdcLight, Icons.Outlined.AttachMoney, "USDC")
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
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Max: ${
                        balance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                            .toPlainString()
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Amount TextField
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                                onAmountChange(newValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            {
                                Text(
                                    errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else null,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (amount.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onAmountChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = symbol,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = coinColor,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (errorMessage == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTrailingIconColor = coinColor,
                            unfocusedTrailingIconColor = coinColor
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onMaxClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        "MAX",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (amount.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                val amountValue = try {
                    BigDecimal(amount)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }

                val usdAmount = amountValue.toDouble() * getUsdRate(coinType)

                Text(
                    text = "≈ $${String.format("%.2f", usdAmount)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SendFeeSelection(
    feeLevel: FeeLevel,
    onFeeLevelChange: (FeeLevel) -> Unit,
    feeEstimate: Any?,
    coinType: CoinType
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
                text = "Transaction Fee",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeeLevelButtons(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (coinType) {
                CoinType.ETHEREUM, CoinType.USDC -> {
                    (feeEstimate as? EthereumFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeEth} ETH"
                        )
                    }
                }

                CoinType.BITCOIN -> {
                    (feeEstimate as? BitcoinFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeBtc} BTC"
                        )
                        FeeDetailsRow(
                            label = "Fee Rate",
                            value = "${fee.feePerByte} sat/byte"
                        )
                    }
                }

                CoinType.SOLANA -> {
                    (feeEstimate as? SolanaFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.feeSol} SOL"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeeDetailsRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeeLevelButtons(
    selectedLevel: FeeLevel,
    onLevelSelected: (FeeLevel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FeeLevelButton(
            level = FeeLevel.SLOW,
            selected = selectedLevel == FeeLevel.SLOW,
            onClick = { onLevelSelected(FeeLevel.SLOW) },
            color = MaterialTheme.colorScheme.success,
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.NORMAL,
            selected = selectedLevel == FeeLevel.NORMAL,
            onClick = { onLevelSelected(FeeLevel.NORMAL) },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.FAST,
            selected = selectedLevel == FeeLevel.FAST,
            onClick = { onLevelSelected(FeeLevel.FAST) },
            color = MaterialTheme.colorScheme.warning,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FeeLevelButton(
    level: FeeLevel,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val (text, icon) = when (level) {
        FeeLevel.SLOW -> Pair("Slow", Icons.Outlined.Schedule)
        FeeLevel.NORMAL -> Pair("Normal", Icons.Outlined.Speed)
        FeeLevel.FAST -> Pair("Fast", Icons.Outlined.FlashOn)
    }

    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) BorderStroke(1.dp, color) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SendBottomBar(
    isValid: Boolean,
    isLoading: Boolean,
    error: String? = null,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = isValid && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Processing...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun MaxAmountDialog(
    balance: BigDecimal,
    feeEstimate: Any?,
    tokenSymbol: String,
    coinType: CoinType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Extract fee based on coin type
    val fee = when (feeEstimate) {
        is BitcoinFeeEstimate -> feeEstimate.totalFeeBtc.toBigDecimalOrNull()
            ?: BigDecimal("0.00001")

        is EthereumFeeEstimate -> feeEstimate.totalFeeEth.toBigDecimalOrNull()
            ?: BigDecimal("0.001")

        is USDCFeeEstimate -> feeEstimate.totalFeeEth.toBigDecimalOrNull() ?: BigDecimal("0.001")
        is SolanaFeeEstimate -> feeEstimate.feeSol.toBigDecimalOrNull() ?: BigDecimal("0.000005")
        else -> when (coinType) {
            CoinType.BITCOIN -> BigDecimal("0.00001")
            CoinType.ETHEREUM, CoinType.USDC -> BigDecimal("0.001")
            CoinType.SOLANA -> BigDecimal("0.000005")
        }
    }

    val maxAmount = balance - fee

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Send Maximum",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                if (maxAmount > BigDecimal.ZERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${
                                balance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Network Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "- ${
                                fee.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum Send:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${
                                maxAmount.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will send all available funds minus the network fee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Insufficient balance to cover network fee.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (maxAmount > BigDecimal.ZERO) {
                Button(
                    onClick = { onConfirm(maxAmount.toPlainString()) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Use Maximum",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
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

@Composable
fun InfoMessage(
    info: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.infoContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    "Info",
                    tint = MaterialTheme.colorScheme.info,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.info,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "Dismiss",
                    tint = MaterialTheme.colorScheme.info,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorMessage(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helper function for USD rate (simplified version)
private fun getUsdRate(coinType: CoinType): Double {
    return when (coinType) {
        CoinType.BITCOIN -> 45000.0
        CoinType.ETHEREUM -> 3000.0
        CoinType.SOLANA -> 30.0
        CoinType.USDC -> 1.0
    }
}