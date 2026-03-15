package com.example.nexuswallet.feature.ethereum.ui

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
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
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
    onNavigateToReview: (String, String, String, FeeLevel?, Network) -> Unit,
    walletId: String,
    network: Network,
    viewModel: EthereumSendViewModel = hiltViewModel()
) {
    var showMaxDialog by remember { mutableStateOf(false) }
    var showNetworkSelector by remember { mutableStateOf(false) }
    var showTokenSelector by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }

    var addressTouched by remember { mutableStateOf(false) }
    var amountTouched by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    var amountFocused by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Initialize ViewModel with the network directly
    LaunchedEffect(Unit) {
        viewModel.initialize(walletId, network as EthereumNetwork)

        // Auto-select USDC if needed (based on network's coinType)
        if (network.coinType == CoinType.USDC) {
            snapshotFlow { state.isInitialized }
                .filter { it }
                .firstOrNull()

            val usdcToken = state.availableTokens.firstOrNull { it is USDCToken }
            usdcToken?.let { viewModel.selectToken(it) }
        }
    }

    val selectedToken = state.selectedToken
    val (iconRes, coinColor, displayName) = when (selectedToken) {
        is NativeETH -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
        is USDCToken -> Triple(R.drawable.usdc, usdcLight, "USDC")
        is USDTToken -> Triple(R.drawable.usdc, usdtLight, "USDT")
        else -> when (network.coinType) {
            CoinType.ETHEREUM -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
            CoinType.USDC -> Triple(R.drawable.usdc, usdcLight, "USDC")
            else -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
        }
    }

    val availableNetworks = listOf(
        EthereumNetwork.Mainnet,
        EthereumNetwork.Sepolia
    )

    // Determine the current coin type for the amount input
    val currentCoinType = when {
        selectedToken is USDCToken -> CoinType.USDC
        selectedToken is USDTToken -> CoinType.USDC // Treat USDT as USDC for USD price
        else -> network.coinType
    }

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

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Network Selector Card
                NetworkSelectorCard(
                    currentNetwork = state.network,
                    onClick = { showNetworkSelector = true }
                )

                // Token Selector (if multiple tokens available)
                if (state.availableTokens.size > 1) {
                    TokenSelectorCard(
                        selectedToken = selectedToken,
                        onClick = { showTokenSelector = true }
                    )
                }

                // Balance Card
                SendBalanceCard(
                    balance = if (selectedToken is NativeETH) state.ethBalance else state.tokenBalance,
                    balanceFormatted = if (selectedToken is NativeETH)
                        "${state.ethBalance.setScale(6, RoundingMode.HALF_UP)} ETH"
                    else if (selectedToken is USDCToken || selectedToken is USDTToken)
                        "$${
                            state.tokenBalance.setScale(
                                2,
                                RoundingMode.HALF_UP
                            )
                        } ${selectedToken?.symbol}"
                    else
                        "${
                            state.tokenBalance.setScale(
                                6,
                                RoundingMode.HALF_UP
                            )
                        } ${selectedToken?.symbol ?: "ETH"}",
                    coinColor = coinColor,
                    iconRes = iconRes,
                    address = state.fromAddress,
                    network = state.network
                )

                // Show ETH balance for gas if this is a token
                if (selectedToken !is NativeETH) {
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
                        addressTouched = true
                        viewModel.onEvent(EthereumSendEvent.ToAddressChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        addressFocused = isFocused
                        if (!isFocused && state.toAddress.isNotEmpty()) {
                            addressTouched = true
                        }
                    },
                    placeholder = "Enter Ethereum address (0x...)",
                    isValid = !errorState.showAddressError && !errorState.showSelfSendError,
                    errorMessage = errorState.addressErrorMessage,
                    onPaste = { pastedText ->
                        addressTouched = true
                        viewModel.onEvent(EthereumSendEvent.ToAddressChanged(pastedText))
                    },
                    focusRequester = addressFocusRequester
                )

                // Amount Input
                SendAmountInput(
                    amount = state.amount,
                    coinType = currentCoinType,
                    onAmountChange = {
                        amountTouched = true
                        viewModel.onEvent(EthereumSendEvent.AmountChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
                        if (!isFocused && state.amount.isNotEmpty()) {
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
                    focusRequester = amountFocusRequester
                )

                // Fee Selection
                SendFeeSelection(
                    feeLevel = state.feeLevel,
                    onFeeLevelChange = { viewModel.onEvent(EthereumSendEvent.FeeLevelChanged(it)) },
                    feeEstimate = state.feeEstimate,
                    coinType = CoinType.ETHEREUM
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
                        state.network
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
            tokenSymbol = selectedToken?.symbol ?: "ETH",
            coinType = network.coinType,
            token = selectedToken,
            onDismiss = { showMaxDialog = false },
            onConfirm = { maxAmount ->
                amountTouched = true
                viewModel.onEvent(EthereumSendEvent.AmountChanged(maxAmount))
                showMaxDialog = false
            }
        )
    }
}