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
import androidx.navigation.NavController
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
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    walletId: String,
    coinType: String,
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
            "ETH" -> ethereumViewModel.initialize(walletId)
            "USDC" -> usdcViewModel.init(walletId)
            "SOL" -> solanaViewModel.init(walletId)
            "BTC" -> bitcoinViewModel.init(walletId)
        }
    }

    // Determine loading state
    val isLoading = when (coinType) {
        "ETH" -> ethereumUiState.value.isLoading
        "USDC" -> usdcState.value.isLoading
        "SOL" -> solanaState.value.isLoading
        "BTC" -> bitcoinState.value.isLoading
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (coinType) {
                                "BTC" -> Icons.Outlined.CurrencyBitcoin
                                "ETH" -> Icons.Outlined.Diamond
                                "SOL" -> Icons.Outlined.FlashOn
                                "USDC" -> Icons.Outlined.AttachMoney
                                else -> Icons.Outlined.AccountBalanceWallet
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when (coinType) {
                                "BTC" -> Color(0xFFF7931A)
                                "ETH" -> Color(0xFF627EEA)
                                "SOL" -> Color(0xFF00FFA3)
                                "USDC" -> Color(0xFF2775CA)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send ${getDisplayName(coinType)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.Black
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
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F7)
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
                        "ETH" -> {
                            SendBalanceCard(
                                balance = ethereumUiState.value.balance,
                                balanceFormatted = "${ethereumUiState.value.balance.setScale(6, RoundingMode.HALF_UP)} ETH",
                                coinType = coinType,
                                address = ethereumUiState.value.fromAddress,
                                network = ethereumUiState.value.network
                            )
                        }
                        "USDC" -> {
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
                        "SOL" -> {
                            SendBalanceCard(
                                balance = solanaState.value.balance,
                                balanceFormatted = solanaState.value.balanceFormatted,
                                coinType = coinType,
                                address = solanaState.value.walletAddress
                            )
                        }
                        "BTC" -> {
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
                    "ETH" -> {
                        ethereumUiState.value.error?.let { error ->
                            item {
                                ErrorMessage(error = error) {
                                    ethereumViewModel.onEvent(EthereumSendEvent.ClearError)
                                }
                            }
                        }
                    }
                    "USDC" -> {
                        usdcState.value.error?.let { error ->
                            item {
                                ErrorMessage(error = error) {
                                    usdcViewModel.onEvent(USDCSendEvent.ClearError)
                                }
                            }
                        }
                        usdcState.value.info?.let { info ->
                            item {
                                InfoMessage(info = info) {
                                    usdcViewModel.onEvent(USDCSendEvent.ClearInfo)
                                }
                            }
                        }
                    }
                    "SOL" -> {
                        // Error is shown inline via validation result
                    }
                    "BTC" -> {
                        bitcoinState.value.error?.let { error ->
                            item {
                                ErrorMessage(error = error) {
                                    bitcoinViewModel.clearError()
                                }
                            }
                        }
                        bitcoinState.value.info?.let { info ->
                            item {
                                InfoMessage(info = info) {
                                    bitcoinViewModel.clearInfo()
                                }
                            }
                        }
                    }
                }

                // Address Input
                item {
                    when (coinType) {
                        "ETH" -> {
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
                        "USDC" -> {
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
                        "SOL" -> {
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
                        "BTC" -> {
                            SendAddressInput(
                                toAddress = bitcoinState.value.toAddress,
                                onAddressChange = { bitcoinViewModel.updateAddress(it) },
                                coinType = coinType,
                                isValid = bitcoinState.value.isAddressValid,
                                errorMessage = bitcoinState.value.addressError,
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
                        "ETH" -> {
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
                        "USDC" -> {
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
                        "SOL" -> {
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
                        "BTC" -> {
                            SendAmountInput(
                                amount = bitcoinState.value.amount,
                                onAmountChange = { bitcoinViewModel.updateAmount(it) },
                                balance = bitcoinState.value.balance,
                                coinType = coinType,
                                onMaxClick = { showMaxDialog = true }
                            )
                        }
                    }
                }

                // Fee Selection
                item {
                    when (coinType) {
                        "ETH" -> {
                            SendFeeSelection(
                                feeLevel = ethereumUiState.value.feeLevel,
                                onFeeLevelChange = { ethereumViewModel.onEvent(EthereumSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = ethereumUiState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        "USDC" -> {
                            SendFeeSelection(
                                feeLevel = usdcState.value.feeLevel,
                                onFeeLevelChange = { usdcViewModel.onEvent(USDCSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = usdcState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        "SOL" -> {
                            SendFeeSelection(
                                feeLevel = solanaState.value.feeLevel,
                                onFeeLevelChange = { solanaViewModel.onEvent(SolanaSendEvent.FeeLevelChanged(it)) },
                                feeEstimate = solanaState.value.feeEstimate,
                                coinType = coinType
                            )
                        }
                        "BTC" -> {
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
                    "ETH" -> ethereumUiState.value.validationResult.isValid
                    "USDC" -> usdcState.value.validationResult.isValid
                    "SOL" -> solanaState.value.validationResult.isValid
                    "BTC" -> bitcoinState.value.isAddressValid && bitcoinState.value.amountValue > BigDecimal.ZERO
                    else -> false
                },
                isLoading = isLoading,
                validationError = when (coinType) {
                    "ETH" -> null // Errors are shown inline via validationResult
                    "USDC" -> null // Errors are shown inline
                    "SOL" -> null // Errors are shown inline
                    "BTC" -> null
                    else -> null
                },
                error = when (coinType) {
                    "ETH" -> ethereumUiState.value.error
                    "USDC" -> usdcState.value.error
                    "SOL" -> solanaState.value.error
                    "BTC" -> bitcoinState.value.error
                    else -> null
                },
                onSend = {
                    when (coinType) {
                        "ETH" -> {
                            ethereumViewModel.send { txHash ->
                                navController.navigate("review/$walletId/ETH?toAddress=${ethereumUiState.value.toAddress}&amount=${ethereumUiState.value.amount}&feeLevel=${ethereumUiState.value.feeLevel.name}")
                            }
                        }
                        "USDC" -> {
                            usdcViewModel.send { txHash ->
                                navController.navigate("review/$walletId/USDC?toAddress=${usdcState.value.toAddress}&amount=${usdcState.value.amount}&feeLevel=${usdcState.value.feeLevel.name}")
                            }
                        }
                        "SOL" -> {
                            solanaViewModel.send { txHash ->
                                navController.navigate("review/$walletId/SOL?toAddress=${solanaState.value.toAddress}&amount=${solanaState.value.amount}&feeLevel=${solanaState.value.feeLevel.name}")
                            }
                        }
                        "BTC" -> {
                            navController.navigate("review/$walletId/BTC?toAddress=${bitcoinState.value.toAddress}&amount=${bitcoinState.value.amount}&feeLevel=${bitcoinState.value.feeLevel.name}")
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
            "ETH" -> {
                MaxAmountDialog(
                    balance = ethereumUiState.value.balance,
                    feeEstimate = ethereumUiState.value.feeEstimate,
                    tokenSymbol = "ETH",
                    coinType = "ETH",
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        ethereumViewModel.onEvent(EthereumSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            "USDC" -> {
                MaxAmountDialog(
                    balance = usdcState.value.usdcBalanceDecimal,
                    feeEstimate = usdcState.value.feeEstimate,
                    tokenSymbol = "USDC",
                    coinType = "USDC",
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        usdcViewModel.onEvent(USDCSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            "SOL" -> {
                MaxAmountDialog(
                    balance = solanaState.value.balance,
                    feeEstimate = solanaState.value.feeEstimate,
                    tokenSymbol = "SOL",
                    coinType = "SOL",
                    onDismiss = { showMaxDialog = false },
                    onConfirm = { maxAmount ->
                        solanaViewModel.onEvent(SolanaSendEvent.AmountChanged(maxAmount))
                        showMaxDialog = false
                    }
                )
            }
            "BTC" -> {
                MaxAmountDialog(
                    balance = bitcoinState.value.balance,
                    feeEstimate = bitcoinState.value.feeEstimate,
                    tokenSymbol = "BTC",
                    coinType = "BTC",
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
    coinType: String,
    address: String,
    secondaryBalance: BigDecimal? = null,
    secondaryBalanceFormatted: String? = null,
    network: String? = null
) {
    val (coinColor, icon) = when (coinType) {
        "BTC" -> Pair(Color(0xFFF7931A), Icons.Outlined.CurrencyBitcoin)
        "ETH" -> Pair(Color(0xFF627EEA), Icons.Outlined.Diamond)
        "SOL" -> Pair(Color(0xFF00FFA3), Icons.Outlined.FlashOn)
        "USDC" -> Pair(Color(0xFF2775CA), Icons.Outlined.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.AccountBalanceWallet)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                        color = Color(0xFF6B7280)
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
                        color = Color.Black
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
                            color = Color(0xFF6B7280)
                        )
                    }

                    if (network != null && network != "MAINNET" && network != "Mainnet") {
                        Text(
                            text = "Network: $network",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280)
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
                        contentDescription = coinType,
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
                    tint = Color(0xFF6B7280)
                )
                Text(
                    text = "From: ${address.take(6)}...${address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
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
    coinType: String,
    isValid: Boolean = true,
    errorMessage: String? = null,
    network: BitcoinNetwork? = null,
    onPaste: (String) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = toAddress,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = when (coinType) {
                            "BTC" -> {
                                val networkHint = if (network == BitcoinNetwork.TESTNET)
                                    " (testnet)" else ""
                                "Enter Bitcoin address$networkHint"
                            }
                            "ETH", "USDC" -> "Enter Ethereum address (0x...)"
                            "SOL" -> "Enter Solana address"
                            else -> "Enter wallet address"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9CA3AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = toAddress.isNotEmpty() && !isValid,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage, color = Color(0xFFEF4444), maxLines = 1) }
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
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isValid) Color(0xFF3B82F6) else Color(0xFFEF4444),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    cursorColor = Color(0xFF3B82F6)
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
                        contentColor = Color(0xFF3B82F6)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan", maxLines = 1)
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
                        contentColor = Color(0xFF3B82F6)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste", maxLines = 1)
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
    coinType: String,
    tokenSymbol: String? = null,
    onMaxClick: () -> Unit,
    errorMessage: String? = null
) {
    val symbol = tokenSymbol ?: coinType
    val (coinColor, icon) = when (coinType) {
        "BTC" -> Pair(Color(0xFFF7931A), Icons.Outlined.CurrencyBitcoin)
        "ETH" -> Pair(Color(0xFF627EEA), Icons.Outlined.Diamond)
        "SOL" -> Pair(Color(0xFF00FFA3), Icons.Outlined.FlashOn)
        "USDC" -> Pair(Color(0xFF2775CA), Icons.Outlined.AttachMoney)
        else -> Pair(MaterialTheme.colorScheme.primary, Icons.Outlined.AccountBalanceWallet)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                    color = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Max: ${
                        balance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                            .toPlainString()
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
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
                                color = Color(0xFF9CA3AF),
                                maxLines = 1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(errorMessage, color = Color(0xFFEF4444), maxLines = 1) }
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
                                            tint = Color(0xFF6B7280),
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
                            focusedBorderColor = if (errorMessage == null) Color(0xFF3B82F6) else Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                            cursorColor = Color(0xFF3B82F6),
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
                        containerColor = Color(0xFF3B82F6)
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        "MAX",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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

                val usdAmount = amountValue.toDouble() * getUsdRate(coinType, symbol)

                Text(
                    text = "≈ $${String.format("%.2f", usdAmount)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
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
    coinType: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeeLevelButtons(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (coinType) {
                "ETH", "USDC" -> {
                    (feeEstimate as? EthereumFeeEstimate)?.let { fee ->
                        FeeDetailsRow(
                            label = "Network Fee",
                            value = "${fee.totalFeeEth} ETH"
                        )
                    }
                }

                "BTC" -> {
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

                "SOL" -> {
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
            color = Color(0xFF6B7280)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.Black
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
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.NORMAL,
            selected = selectedLevel == FeeLevel.NORMAL,
            onClick = { onLevelSelected(FeeLevel.NORMAL) },
            color = Color(0xFF3B82F6),
            modifier = Modifier.weight(1f)
        )

        FeeLevelButton(
            level = FeeLevel.FAST,
            selected = selectedLevel == FeeLevel.FAST,
            onClick = { onLevelSelected(FeeLevel.FAST) },
            color = Color(0xFFF59E0B),
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
            containerColor = if (selected) color.copy(alpha = 0.1f) else Color(0xFFF3F4F6)
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
                tint = if (selected) color else Color(0xFF6B7280),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else Color(0xFF6B7280)
            )
        }
    }
}

@Composable
fun FeeLevelButton(
    level: FeeLevel,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    val (text, icon) = when (level) {
        FeeLevel.SLOW -> Pair("Slow", Icons.Outlined.Schedule)
        FeeLevel.NORMAL -> Pair("Normal", Icons.Outlined.Speed)
        FeeLevel.FAST -> Pair("Fast", Icons.Outlined.FlashOn)
    }

    Card(
        modifier = Modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else Color(0xFFF3F4F6)
        ),
        border = if (selected) BorderStroke(1.dp, color) else null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (selected) color else Color(0xFF6B7280),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else Color(0xFF6B7280)
            )
        }
    }
}

@Composable
fun SendBottomBar(
    isValid: Boolean,
    isLoading: Boolean,
    validationError: String? = null,
    error: String? = null,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val message = error ?: validationError
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444),
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
                    containerColor = Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
    coinType: String,
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
            "BTC" -> BigDecimal("0.00001")
            "ETH", "USDC" -> BigDecimal("0.001")
            "SOL" -> BigDecimal("0.000005")
            else -> BigDecimal("0.001")
        }
    }

    val maxAmount = balance - fee

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Send Maximum",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
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
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "${
                                balance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
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
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "- ${
                                fee.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFFE5E7EB),
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
                            color = Color.Black
                        )
                        Text(
                            text = "${
                                maxAmount.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                    .toPlainString()
                            } $tokenSymbol",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This will send all available funds minus the network fee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                } else {
                    Text(
                        text = "Insufficient balance to cover network fee.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF4444)
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
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text("Use Maximum")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF6B7280)
                )
            ) {
                Text("Cancel")
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
            containerColor = Color(0xFFEFF6FF)
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
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1E40AF),
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
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helper functions
private fun getDisplayName(coinType: String): String {
    return when (coinType) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "SOL" -> "Solana"
        "USDC" -> "USDC"
        else -> coinType
    }
}

private fun getUsdRate(coinType: String, tokenSymbol: String? = null): Double {
    val symbol = tokenSymbol ?: coinType
    return when {
        symbol == "BTC" -> 45000.0
        symbol == "ETH" -> 3000.0
        symbol == "SOL" -> 30.0
        symbol == "USDC" -> 1.0
        else -> 1.0
    }
}