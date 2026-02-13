package com.example.nexuswallet.feature.wallet.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinSendViewModel
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.TransactionState
import com.example.nexuswallet.feature.coin.solana.SolanaSendViewModel
import com.example.nexuswallet.feature.coin.usdc.USDCSendViewModel
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.WalletType
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    walletId: String,
    walletType: WalletType,
    ethereumViewModel: EthereumSendViewModel = hiltViewModel(),
    usdcViewModel: USDCSendViewModel = hiltViewModel(),
    solanaViewModel: SolanaSendViewModel = hiltViewModel(),
    bitcoinViewModel: BitcoinSendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showMaxDialog by remember { mutableStateOf(false) }

    val ethereumUiState = ethereumViewModel.uiState.collectAsState()
    val usdcState = usdcViewModel.state.collectAsState()
    val solanaState = solanaViewModel.state.collectAsState()
    val bitcoinState = bitcoinViewModel.state.collectAsState()

    // Initialize ViewModels
    LaunchedEffect(Unit) {
        when (walletType) {
            WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> ethereumViewModel.initialize(walletId)
            WalletType.USDC -> usdcViewModel.init(walletId)
            WalletType.SOLANA -> solanaViewModel.init(walletId)
            WalletType.BITCOIN -> bitcoinViewModel.init(walletId)
            else -> {}
        }
    }

    // Transaction state handling
    LaunchedEffect(walletType, ethereumUiState.value.transactionState) {
        when (walletType) {
            WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                when (val state = ethereumUiState.value.transactionState) {
                    is TransactionState.Created -> {
                        navController.navigate("review/${state.transaction.id}")
                        ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.ResetTransactionState)
                    }
                    is TransactionState.Error -> {
                        if (ethereumUiState.value.error == null) {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }

    // Determine loading state
    val isLoading = when (walletType) {
        WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> ethereumUiState.value.isLoading
        WalletType.USDC -> usdcState.value.isLoading
        WalletType.SOLANA -> solanaState.value.isLoading
        WalletType.BITCOIN -> bitcoinState.value.isLoading
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (walletType) {
                                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> Icons.Default.CurrencyExchange
                                WalletType.USDC -> Icons.Default.AttachMoney
                                WalletType.SOLANA -> Icons.Default.Star
                                WalletType.BITCOIN -> Icons.Default.CurrencyBitcoin
                                else -> Icons.Default.AccountBalanceWallet
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send ${walletType.displayName}")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            )
        },
        bottomBar = {
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    SendBottomBar(
                        isValid = ethereumUiState.value.isValid,
                        isLoading = ethereumUiState.value.isLoading,
                        validationError = ethereumUiState.value.validationError,
                        transactionState = ethereumUiState.value.transactionState,
                        onSend = { ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.CreateTransaction) }
                    )
                }
                WalletType.USDC -> {
                    SendBottomBar(
                        isValid = usdcState.value.isValidAddress && usdcState.value.amountValue > BigDecimal.ZERO,
                        isLoading = usdcState.value.isLoading,
                        validationError = if (!usdcState.value.isValidAddress && usdcState.value.toAddress.isNotEmpty())
                            "Invalid address" else null,
                        error = usdcState.value.error,
                        onSend = {
                            usdcViewModel.send { hash ->
                                navController.navigate("transaction/$hash")
                            }
                        }
                    )
                }
                WalletType.SOLANA -> {
                    SendBottomBar(
                        isValid = solanaState.value.isAddressValid && solanaState.value.amountValue > BigDecimal.ZERO,
                        isLoading = solanaState.value.isLoading,
                        validationError = solanaState.value.addressError ?:
                        if (solanaState.value.amountValue <= BigDecimal.ZERO) "Enter amount" else null,
                        error = solanaState.value.error,
                        onSend = {
                            solanaViewModel.send { hash ->
                                navController.navigate("transaction/$hash")
                            }
                        }
                    )
                }
                WalletType.BITCOIN -> {
                    SendBottomBar(
                        isValid = bitcoinState.value.isAddressValid && bitcoinState.value.amountValue > BigDecimal.ZERO,
                        isLoading = bitcoinState.value.isLoading,
                        validationError = bitcoinState.value.addressError ?:
                        if (bitcoinState.value.amountValue <= BigDecimal.ZERO) "Enter amount" else null,
                        error = bitcoinState.value.error,
                        onSend = {
                            bitcoinViewModel.send { hash ->
                                navController.navigate("transaction/$hash")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Balance Card
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    BalanceCard(
                        balance = ethereumUiState.value.balance,
                        balanceFormatted = "${ethereumUiState.value.balance.setScale(6, RoundingMode.HALF_UP)} ETH",
                        walletType = walletType,
                        address = ethereumUiState.value.fromAddress
                    )
                }
                WalletType.USDC -> {
                    BalanceCard(
                        balance = usdcState.value.usdcBalanceDecimal,
                        balanceFormatted = "${usdcState.value.usdcBalanceDecimal.setScale(2, RoundingMode.HALF_UP)} USDC",
                        walletType = walletType,
                        address = usdcState.value.wallet?.address ?: "",
                        secondaryBalance = usdcState.value.ethBalanceDecimal,
                        secondaryBalanceFormatted = "${usdcState.value.ethBalanceDecimal.setScale(4, RoundingMode.HALF_UP)} ETH"
                    )
                }
                WalletType.SOLANA -> {
                    BalanceCard(
                        balance = solanaState.value.balance,
                        balanceFormatted = solanaState.value.balanceFormatted,
                        walletType = walletType,
                        address = solanaState.value.walletAddress
                    )
                }
                WalletType.BITCOIN -> {
                    BalanceCard(
                        balance = bitcoinState.value.balance,
                        balanceFormatted = bitcoinState.value.balanceFormatted,
                        walletType = walletType,
                        address = bitcoinState.value.walletAddress
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Message
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    ethereumUiState.value.error?.let { error ->
                        ErrorMessage(error = error) {
                            ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.ClearError)
                        }
                    }
                }
                WalletType.USDC -> {
                    usdcState.value.error?.let { error ->
                        ErrorMessage(error = error) {
                            usdcViewModel.clearError()
                        }
                    }
                    usdcState.value.info?.let { info ->
                        InfoMessage(info = info) {
                            usdcViewModel.clearInfo()
                        }
                    }
                }
                WalletType.SOLANA -> {
                    solanaState.value.error?.let { error ->
                        ErrorMessage(error = error) {
                            solanaViewModel.clearError()
                        }
                    }
                    solanaState.value.airdropMessage?.let { message ->
                        InfoMessage(info = message) {
                            solanaViewModel.clearAirdropMessage()
                        }
                    }
                }
                WalletType.BITCOIN -> {
                    bitcoinState.value.error?.let { error ->
                        ErrorMessage(error = error) {
                            bitcoinViewModel.clearError()
                        }
                    }
                    bitcoinState.value.info?.let { info ->
                        InfoMessage(info = info) {
                            bitcoinViewModel.clearInfo()
                        }
                    }
                }
                else -> {}
            }

            // Address Input
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    AddressInputSection(
                        toAddress = ethereumUiState.value.toAddress,
                        onAddressChange = { ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.ToAddressChanged(it)) },
                        walletType = walletType,
                        isValid = ethereumUiState.value.validationError?.contains("address") == false,
                        errorMessage = if (ethereumUiState.value.validationError?.contains("address") == true)
                            ethereumUiState.value.validationError else null
                    )
                }
                WalletType.USDC -> {
                    AddressInputSection(
                        toAddress = usdcState.value.toAddress,
                        onAddressChange = { usdcViewModel.updateAddress(it) },
                        walletType = walletType,
                        isValid = usdcState.value.isValidAddress,
                        errorMessage = if (!usdcState.value.isValidAddress && usdcState.value.toAddress.isNotEmpty())
                            "Invalid Ethereum address" else null
                    )
                }
                WalletType.SOLANA -> {
                    AddressInputSection(
                        toAddress = solanaState.value.toAddress,
                        onAddressChange = { solanaViewModel.updateAddress(it) },
                        walletType = walletType,
                        isValid = solanaState.value.isAddressValid,
                        errorMessage = solanaState.value.addressError
                    )
                }
                WalletType.BITCOIN -> {
                    AddressInputSection(
                        toAddress = bitcoinState.value.toAddress,
                        onAddressChange = { bitcoinViewModel.updateAddress(it) },
                        walletType = walletType,
                        isValid = bitcoinState.value.isAddressValid,
                        errorMessage = bitcoinState.value.addressError,
                        network = bitcoinState.value.network
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    AmountInputSection(
                        amount = ethereumUiState.value.amount,
                        onAmountChange = { ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.AmountChanged(it)) },
                        balance = ethereumUiState.value.balance,
                        walletType = walletType,
                        onMaxClick = { showMaxDialog = true },
                        errorMessage = if (ethereumUiState.value.validationError?.contains("balance") == true)
                            ethereumUiState.value.validationError else null
                    )
                }
                WalletType.USDC -> {
                    AmountInputSection(
                        amount = usdcState.value.amount,
                        onAmountChange = { usdcViewModel.updateAmount(it) },
                        balance = usdcState.value.usdcBalanceDecimal,
                        walletType = walletType,
                        tokenSymbol = "USDC",
                        onMaxClick = { usdcViewModel.updateAmount(usdcState.value.usdcBalanceDecimal.toPlainString()) }
                    )
                }
                WalletType.SOLANA -> {
                    AmountInputSection(
                        amount = solanaState.value.amount,
                        onAmountChange = { solanaViewModel.updateAmount(it) },
                        balance = solanaState.value.balance,
                        walletType = walletType,
                        onMaxClick = { showMaxDialog = true }
                    )
                }
                WalletType.BITCOIN -> {
                    AmountInputSection(
                        amount = bitcoinState.value.amount,
                        onAmountChange = { bitcoinViewModel.updateAmount(it) },
                        balance = bitcoinState.value.balance,
                        walletType = walletType,
                        onMaxClick = { showMaxDialog = true }
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fee Selection (for ETH and BTC)
            if (walletType == WalletType.ETHEREUM || walletType == WalletType.ETHEREUM_SEPOLIA ||
                walletType == WalletType.BITCOIN) {

                when (walletType) {
                    WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                        FeeSelectionSection(
                            feeLevel = ethereumUiState.value.feeLevel,
                            onFeeLevelChange = { ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.FeeLevelChanged(it)) },
                            feeEstimate = ethereumUiState.value.feeEstimate,
                            walletType = walletType
                        )
                    }
                    WalletType.BITCOIN -> {
                        FeeSelectionSection(
                            feeLevel = bitcoinState.value.feeLevel,
                            onFeeLevelChange = { bitcoinViewModel.updateFeeLevel(it) },
                            feeEstimate = bitcoinState.value.feeEstimate,
                            walletType = walletType
                        )
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Transaction Summary
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    TransactionSummarySection(
                        amount = ethereumUiState.value.amount,
                        feeEstimate = ethereumUiState.value.feeEstimate,
                        walletType = walletType,
                        tokenSymbol = "ETH"
                    )
                }
                WalletType.USDC -> {
                    TransactionSummarySection(
                        amount = usdcState.value.amount,
                        feeEstimate = FeeEstimate(
                            totalFee = "420000000000000",
                            totalFeeDecimal = usdcState.value.estimatedGas,
                            priority = FeeLevel.NORMAL,
                            estimatedTime = 60,
                            feePerByte = null,
                            gasPrice = "30"
                        ),
                        walletType = walletType,
                        tokenSymbol = "USDC"
                    )
                }
                WalletType.SOLANA -> {
                    TransactionSummarySection(
                        amount = solanaState.value.amount,
                        feeEstimate = FeeEstimate(
                            totalFee = "5000",
                            totalFeeDecimal = "0.000005",
                            priority = FeeLevel.NORMAL,
                            estimatedTime = 1,
                            feePerByte = null,
                            gasPrice = null
                        ),
                        walletType = walletType,
                        tokenSymbol = "SOL"
                    )
                }
                WalletType.BITCOIN -> {
                    TransactionSummarySection(
                        amount = bitcoinState.value.amount,
                        feeEstimate = bitcoinState.value.feeEstimate,
                        walletType = walletType,
                        tokenSymbol = "BTC"
                    )
                }
                else -> {}
            }
        }

        // Max Amount Dialog
        if (showMaxDialog) {
            when (walletType) {
                WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                    MaxAmountDialog(
                        balance = ethereumUiState.value.balance,
                        feeEstimate = ethereumUiState.value.feeEstimate ?: FeeEstimate(
                            totalFee = "630000000000000",
                            totalFeeDecimal = "0.00063",
                            priority = FeeLevel.NORMAL,
                            estimatedTime = 300,
                            feePerByte = null,
                            gasPrice = "30"
                        ),
                        tokenSymbol = "ETH",
                        onDismiss = { showMaxDialog = false },
                        onConfirm = { maxAmount ->
                            ethereumViewModel.onEvent(EthereumSendViewModel.SendEvent.AmountChanged(maxAmount))
                            showMaxDialog = false
                        }
                    )
                }
                WalletType.SOLANA -> {
                    MaxAmountDialog(
                        balance = solanaState.value.balance,
                        feeEstimate = FeeEstimate(
                            totalFee = "5000",
                            totalFeeDecimal = "0.000005",
                            priority = FeeLevel.NORMAL,
                            estimatedTime = 1,
                            feePerByte = null,
                            gasPrice = null
                        ),
                        tokenSymbol = "SOL",
                        onDismiss = { showMaxDialog = false },
                        onConfirm = { maxAmount ->
                            solanaViewModel.updateAmount(maxAmount)
                            showMaxDialog = false
                        }
                    )
                }
                WalletType.BITCOIN -> {
                    MaxAmountDialog(
                        balance = bitcoinState.value.balance,
                        feeEstimate = bitcoinState.value.feeEstimate ?: FeeEstimate(
                            totalFee = "10000",
                            totalFeeDecimal = "0.0001",
                            priority = FeeLevel.NORMAL,
                            estimatedTime = 6,
                            feePerByte = "10",
                            gasPrice = null
                        ),
                        tokenSymbol = "BTC",
                        onDismiss = { showMaxDialog = false },
                        onConfirm = { maxAmount ->
                            bitcoinViewModel.updateAmount(maxAmount)
                            showMaxDialog = false
                        }
                    )
                }
                else -> showMaxDialog = false
            }
        }
    }
}

@Composable
fun BalanceCard(
    balance: BigDecimal,
    balanceFormatted: String,
    walletType: WalletType,
    address: String,
    secondaryBalance: BigDecimal? = null,
    secondaryBalanceFormatted: String? = null
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
                        text = "$${String.format("%.2f", balance.toDouble() * getUsdRate(walletType))}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = balanceFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (secondaryBalance != null && secondaryBalanceFormatted != null) {
                        Text(
                            text = secondaryBalanceFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (walletType) {
                            WalletType.BITCOIN -> Icons.Default.CurrencyBitcoin
                            WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> Icons.Default.CurrencyExchange
                            WalletType.SOLANA -> Icons.Default.Star
                            WalletType.USDC -> Icons.Default.AttachMoney
                            else -> Icons.Default.AccountBalanceWallet
                        },
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "From: ${address.take(12)}...${address.takeLast(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddressInputSection(
    toAddress: String,
    onAddressChange: (String) -> Unit,
    walletType: WalletType,
    isValid: Boolean = true,
    errorMessage: String? = null,
    network: BitcoinNetwork? = null
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Recipient",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Recipient Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = toAddress,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = when (walletType) {
                            WalletType.BITCOIN -> {
                                val networkHint = if (network == BitcoinNetwork.TESTNET)
                                    " (testnet: m/n, 2, tb1)" else ""
                                "Enter Bitcoin address$networkHint"
                            }
                            WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> "Enter Ethereum address (0x...)"
                            WalletType.SOLANA -> "Enter Solana address"
                            WalletType.USDC -> "Enter Ethereum address (0x...)"
                            else -> "Enter wallet address"
                        }
                    )
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                isError = toAddress.isNotEmpty() && !isValid,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage) }
                } else null,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (toAddress.isNotEmpty()) {
                        IconButton(onClick = { onAddressChange("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    errorBorderColor = MaterialTheme.colorScheme.error
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
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Scan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan QR Code")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        val pastedText = clip?.getItemAt(0)?.text?.toString()
                        if (!pastedText.isNullOrBlank()) {
                            onAddressChange(pastedText)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste")
                }
            }
        }
    }
}

@Composable
fun AmountInputSection(
    amount: String,
    onAmountChange: (String) -> Unit,
    balance: BigDecimal,
    walletType: WalletType,
    tokenSymbol: String? = null,
    onMaxClick: () -> Unit,
    errorMessage: String? = null
) {
    val symbol = tokenSymbol ?: when (walletType) {
        WalletType.BITCOIN -> "BTC"
        WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> "ETH"
        WalletType.SOLANA -> "SOL"
        WalletType.USDC -> "USDC"
        else -> "TOK"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = "Amount",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Max: ${balance.toPlainString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                            onAmountChange(newValue)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("0.00") },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage) }
                    } else null,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Decimal
                    ),
                    trailingIcon = {
                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onMaxClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("MAX")
                }
            }

            if (amount.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                val amountValue = try {
                    BigDecimal(amount)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }

                val usdAmount = amountValue.toDouble() * getUsdRate(walletType, symbol)

                Text(
                    text = "≈ $${String.format("%.2f", usdAmount)} USD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun FeeSelectionSection(
    feeLevel: FeeLevel,
    onFeeLevelChange: (FeeLevel) -> Unit,
    feeEstimate: FeeEstimate?,
    walletType: WalletType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Fee",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Transaction Fee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FeeLevelSelection(
                selectedLevel = feeLevel,
                onLevelSelected = onFeeLevelChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            feeEstimate?.let { fee ->
                FeeDetailsCard(
                    feeEstimate = fee,
                    walletType = walletType
                )
            }
        }
    }
}

@Composable
fun FeeLevelSelection(
    selectedLevel: FeeLevel,
    onLevelSelected: (FeeLevel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FeeLevelButton(
            level = FeeLevel.SLOW,
            selected = selectedLevel == FeeLevel.SLOW,
            onClick = { onLevelSelected(FeeLevel.SLOW) }
        )

        FeeLevelButton(
            level = FeeLevel.NORMAL,
            selected = selectedLevel == FeeLevel.NORMAL,
            onClick = { onLevelSelected(FeeLevel.NORMAL) }
        )

        FeeLevelButton(
            level = FeeLevel.FAST,
            selected = selectedLevel == FeeLevel.FAST,
            onClick = { onLevelSelected(FeeLevel.FAST) }
        )
    }
}

@Composable
fun FeeLevelButton(
    level: FeeLevel,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (text, icon, color) = when (level) {
        FeeLevel.SLOW -> Triple("Slow", Icons.Default.Timer, Color(0xFF4CAF50))
        FeeLevel.NORMAL -> Triple("Normal", Icons.Default.CheckCircle, Color(0xFF2196F3))
        FeeLevel.FAST -> Triple("Fast", Icons.Default.FlashOn, Color(0xFFFF9800))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() }
    ) {
        Card(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) color.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (selected) BorderStroke(2.dp, color) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeeDetailsCard(
    feeEstimate: FeeEstimate,
    walletType: WalletType
) {
    val symbol = when (walletType) {
        WalletType.BITCOIN -> "BTC"
        WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> "ETH"
        WalletType.SOLANA -> "SOL"
        WalletType.USDC -> "ETH"
        else -> "TOK"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fee Amount:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${feeEstimate.totalFeeDecimal} $symbol",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "≈ USD:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val feeUsd = feeEstimate.totalFeeDecimal.toDoubleOrNull() ?: 0.0
                val usdValue = feeUsd * getUsdRate(walletType, symbol)

                Text(
                    text = "$${String.format("%.2f", usdValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Estimated Time:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatTime(feeEstimate.estimatedTime),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (feeEstimate.gasPrice != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gas Price:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${feeEstimate.gasPrice} Gwei",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (feeEstimate.feePerByte != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fee Rate:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${feeEstimate.feePerByte} sat/byte",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NoteInputSection(
    note: String,
    onNoteChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Note,
                    contentDescription = "Note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Note (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note for this transaction") },
                shape = RoundedCornerShape(8.dp),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                )
            )
        }
    }
}

@Composable
fun TransactionSummarySection(
    amount: String,
    feeEstimate: FeeEstimate?,
    walletType: WalletType,
    tokenSymbol: String
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
                text = "Transaction Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider()

            Spacer(modifier = Modifier.height(12.dp))

            // Amount
            SummaryRow(
                label = "Amount",
                value = if (amount.isNotEmpty()) "$amount $tokenSymbol" else "0.00 $tokenSymbol",
                isBold = false
            )

            // Fee
            feeEstimate?.let { fee ->
                SummaryRow(
                    label = "Network Fee",
                    value = "${fee.totalFeeDecimal} $tokenSymbol",
                    isBold = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            // Total
            val totalAmount = try {
                val amountValue = BigDecimal(amount)
                val feeValue = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                amountValue + feeValue
            } catch (e: Exception) {
                BigDecimal.ZERO
            }

            SummaryRow(
                label = "Total",
                value = "${totalAmount.toPlainString()} $tokenSymbol",
                isBold = true
            )
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    isBold: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SendBottomBar(
    isValid: Boolean,
    isLoading: Boolean,
    validationError: String? = null,
    error: String? = null,
    transactionState: Any? = null,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val message = error ?: validationError
            message?.let {
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
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
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
    feeEstimate: FeeEstimate?,
    tokenSymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Send Maximum Amount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                val fee = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal("0.001")
                val maxAmount = balance - fee

                if (maxAmount > BigDecimal.ZERO) {
                    Text(
                        text = "Available balance: ${balance.toPlainString()} $tokenSymbol",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Network fee: ${fee.toPlainString()} $tokenSymbol",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Maximum sendable: ${maxAmount.toPlainString()} $tokenSymbol",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

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
            val fee = feeEstimate?.totalFeeDecimal?.toBigDecimalOrNull() ?: BigDecimal("0.001")
            val maxAmount = balance - fee

            if (maxAmount > BigDecimal.ZERO) {
                Button(
                    onClick = { onConfirm(maxAmount.toPlainString()) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Send Maximum")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    "Info",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    "Dismiss",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helper functions
private fun formatTime(seconds: Int): String {
    return when {
        seconds < 60 -> "$seconds sec"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} hr"
    }
}

private fun getUsdRate(walletType: WalletType, tokenSymbol: String? = null): Double {
    return when {
        walletType == WalletType.BITCOIN || tokenSymbol == "BTC" -> 45000.0
        walletType == WalletType.ETHEREUM || walletType == WalletType.ETHEREUM_SEPOLIA || tokenSymbol == "ETH" -> 3000.0
        walletType == WalletType.SOLANA || tokenSymbol == "SOL" -> 30.0
        walletType == WalletType.USDC || tokenSymbol == "USDC" -> 1.0
        else -> 1.0
    }
}

val WalletType.displayName: String
    get() = when (this) {
        WalletType.BITCOIN -> "Bitcoin"
        WalletType.ETHEREUM -> "Ethereum"
        WalletType.ETHEREUM_SEPOLIA -> "Ethereum (Sepolia)"
        WalletType.SOLANA -> "Solana"
        WalletType.USDC -> "USDC"
        WalletType.MULTICHAIN -> "Multi-Chain"
        else -> "Unknown"
    }