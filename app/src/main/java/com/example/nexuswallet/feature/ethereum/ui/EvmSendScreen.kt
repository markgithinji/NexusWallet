package com.example.nexuswallet.feature.ethereum.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.ui.ErrorMessage
import com.example.nexuswallet.feature.core.ui.MaxAmountDialog
import com.example.nexuswallet.feature.core.ui.NetworkSelectorCard
import com.example.nexuswallet.feature.core.ui.NetworkSelectorDialog
import com.example.nexuswallet.feature.core.ui.SendAddressInput
import com.example.nexuswallet.feature.core.ui.SendAmountInput
import com.example.nexuswallet.feature.core.ui.SendBalanceCard
import com.example.nexuswallet.feature.core.ui.SendBottomBar
import com.example.nexuswallet.feature.core.ui.SendFeeSelection
import com.example.nexuswallet.feature.core.ui.SendTopBar
import com.example.nexuswallet.feature.core.ui.TokenSelectorCard
import com.example.nexuswallet.feature.core.ui.TokenSelectorDialog
import com.example.nexuswallet.feature.core.ui.rememberSendErrorState
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.usdcLight
import com.example.nexuswallet.ui.theme.usdtLight
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EthereumSendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, String, String, FeeLevel?, Coin) -> Unit,
    walletId: String,
    coin: Coin,
    viewModel: EVMSendViewModel = hiltViewModel()
) {
    var showMaxDialog by remember { mutableStateOf(false) }
    var showNetworkSelector by remember { mutableStateOf(false) }
    var showTokenSelector by remember { mutableStateOf(false) }
    var showAddressBook by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }

    var addressTouched by remember { mutableStateOf(false) }
    var amountTouched by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    var amountFocused by remember { mutableStateOf(false) }

    var addressHasBeenFocused by remember { mutableStateOf(false) }
    var amountHasBeenFocused by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.onEvent(EVMSendEvent.ToAddressChanged(result.contents))
        }
    }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(walletId, coin as EVMToken)

        // Auto-select the appropriate token if needed
        snapshotFlow { state.isInitialized }
            .filter { it }
            .firstOrNull()

        // Select the specific token that matches the passed coin
        val targetToken = state.availableTokens.firstOrNull {
            it.network == coin.network &&
                    it.evmTokenType == coin.evmTokenType &&
                    it.contractAddress == coin.contractAddress
        }
        targetToken?.let { viewModel.selectToken(it) }
    }

    val selectedToken = state.selectedToken
    val (iconRes, coinColor) = when (selectedToken) {
        is NativeETH -> Pair(R.drawable.ethereum, ethereumLight)
        is USDCToken -> Pair(R.drawable.usdc, usdcLight)
        is USDTToken -> Pair(R.drawable.tether, usdtLight)
        else -> when (coin) {
            is NativeETH -> Pair(R.drawable.ethereum, ethereumLight)
            is USDCToken -> Pair(R.drawable.usdc, usdcLight)
            is USDTToken -> Pair(R.drawable.tether, usdtLight)
            else -> Pair(R.drawable.ethereum, ethereumLight)
        }
    }

    val displayName = selectedToken?.name ?: coin.name

    val availableNetworks = listOf(
        EthereumNetwork.Mainnet,
        EthereumNetwork.Sepolia
    )

    val errorState = rememberSendErrorState(
        validationResult = state.validationResult,
        addressTouched = addressTouched,
        amountTouched = amountTouched,
        addressFocused = addressFocused,
        amountFocused = amountFocused
    )

    Scaffold(
        topBar = {
            SendTopBar(
                title = "Send $displayName",
                iconRes = iconRes,
                coinColor = coinColor,
                isLoading = state.isLoading,
                onNavigateUp = onNavigateUp
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Network Selector Dialog
            if (showNetworkSelector) {
                NetworkSelectorDialog(
                    availableNetworks = availableNetworks,
                    currentNetwork = state.network,
                    onNetworkSelected = { selectedNetwork ->
                        viewModel.switchNetwork(selectedNetwork as EthereumNetwork)
                        showNetworkSelector = false
                    },
                    onDismiss = { showNetworkSelector = false }
                )
            }

            // Token Selector Dialog
            if (showTokenSelector && state.availableTokens.size > 1) {
                TokenSelectorDialog(
                    availableTokens = state.availableTokens,
                    selectedToken = selectedToken,
                    onTokenSelected = { token ->
                        viewModel.selectToken(token)
                        showTokenSelector = false
                    },
                    onDismiss = { showTokenSelector = false }
                )
            }

            // Address Book Dialog
            if (showAddressBook) {
                com.example.nexuswallet.feature.core.ui.AddressBookSelectorDialog(
                    entries = state.addressBookEntries.filter { it.chain == "Ethereum" },
                    onEntrySelected = { entry ->
                        viewModel.onEvent(EVMSendEvent.ToAddressChanged(entry.address))
                        showAddressBook = false
                    },
                    onDismiss = { showAddressBook = false }
                )
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
                    .padding(vertical = 16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Network Selector Card
                NetworkSelectorCard(
                    currentNetwork = state.network,
                    onClick = { showNetworkSelector = true }
                )

                // Token Selector (if multiple tokens available)
                AnimatedVisibility(
                    visible = state.availableTokens.size > 1,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TokenSelectorCard(
                        selectedToken = selectedToken,
                        onClick = { showTokenSelector = true }
                    )
                }

                // Balance Card
                SendBalanceCard(
                    balance = if (selectedToken is NativeETH) state.ethBalance else state.tokenBalance,
                    balanceFormatted = when (selectedToken) {
                        is NativeETH -> "${state.ethBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ETH"
                        is USDCToken -> "${state.tokenBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} USDC"
                        is USDTToken -> "${state.tokenBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} USDT"
                        else -> "${state.tokenBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${selectedToken?.symbol ?: "ETH"}"
                    },
                    fiatRate = state.fiatRate,
                    coinColor = coinColor,
                    iconRes = iconRes,
                    address = state.fromAddress,
                    network = state.network
                )

                // Show ETH balance for gas if this is a token
                AnimatedVisibility(
                    visible = selectedToken !is NativeETH,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = "ETH for gas: ${
                            state.ethBalance.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                                .toPlainString()
                        } ETH",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Error Banner
                if (errorState.activeError != null) {
                    ErrorMessage(
                        error = errorState.activeError,
                        onDismiss = { viewModel.clearError() }
                    )
                }

                // Address Input
                SendAddressInput(
                    toAddress = state.toAddress,
                    onAddressChange = {
                        viewModel.onEvent(EVMSendEvent.ToAddressChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        addressFocused = isFocused
                        if (isFocused) addressHasBeenFocused = true
                        if (!isFocused && addressHasBeenFocused) {
                            addressTouched = true
                        }
                    },
                    placeholder = "Enter Ethereum address (0x...)",
                    isValid = !errorState.showAddressError && !errorState.showSelfSendError,
                    errorMessage = errorState.addressErrorMessage,
                    onPaste = { pastedText ->
                        addressTouched = true
                        viewModel.onEvent(EVMSendEvent.ToAddressChanged(pastedText))
                    },
                    onScanClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan Ethereum Address")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                            setCaptureActivity(com.example.nexuswallet.feature.core.ui.ScannerActivity::class.java)
                        }
                        scanLauncher.launch(options)
                    },
                    onAddressBookClick = { showAddressBook = true },
                    focusRequester = addressFocusRequester
                )

                // Amount Input
                SendAmountInput(
                    amount = state.amount,
                    coin = selectedToken ?: coin,
                    fiatRate = state.fiatRate,
                    onAmountChange = {
                        viewModel.onEvent(EVMSendEvent.AmountChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
                        if (isFocused) amountHasBeenFocused = true
                        if (!isFocused && amountHasBeenFocused) {
                            amountTouched = true
                        }
                    },
                    balance = if (selectedToken is NativeETH) state.ethBalance else state.tokenBalance,
                    symbol = selectedToken?.symbol ?: "ETH",
                    coinColor = coinColor,
                    onMaxClick = {
                        amountTouched = true
                        showMaxDialog = true
                    },
                    errorMessage = errorState.amountErrorMessage,
                    focusRequester = amountFocusRequester,
                    isFiatMode = state.isFiatMode,
                    onModeToggle = {
                        viewModel.onEvent(EVMSendEvent.ToggleFiatMode(it))
                    }
                )

                // Fee Selection
                SendFeeSelection(
                    feeLevel = state.feeLevel,
                    onFeeLevelChange = { viewModel.onEvent(EVMSendEvent.FeeLevelChanged(it)) },
                    feeEstimate = state.feeEstimate,
                    coin = selectedToken ?: coin
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Bar
            SendBottomBar(
                isValid = state.validationResult.isValid,
                isLoading = state.isLoading,
                error = errorState.activeError,
                onSend = {
                    focusManager.clearFocus()
                    onNavigateToReview(
                        walletId,
                        state.toAddress,
                        state.amount,
                        state.feeLevel,
                        state.selectedToken ?: coin
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Max Amount Dialog
    if (showMaxDialog) {
        MaxAmountDialog(
            balance = if (selectedToken is NativeETH) state.ethBalance else state.tokenBalance,
            feeEstimate = state.feeEstimate,
            fiatRate = state.fiatRate,
            tokenSymbol = selectedToken?.symbol ?: "ETH",
            coin = selectedToken ?: coin,
            onDismiss = { showMaxDialog = false },
            onConfirm = { maxAmount ->
                amountTouched = true
                viewModel.onEvent(EVMSendEvent.AmountChanged(maxAmount))
                showMaxDialog = false
            }
        )
    }
}