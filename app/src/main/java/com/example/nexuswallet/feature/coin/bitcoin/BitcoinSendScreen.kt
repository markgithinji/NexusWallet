package com.example.nexuswallet.feature.coin.bitcoin

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.ui.ErrorMessage
import com.example.nexuswallet.feature.wallet.ui.MaxAmountDialog
import com.example.nexuswallet.feature.wallet.ui.NetworkSelectorCard
import com.example.nexuswallet.feature.wallet.ui.NetworkSelectorDialog
import com.example.nexuswallet.feature.wallet.ui.SendAddressInput
import com.example.nexuswallet.feature.wallet.ui.SendAmountInput
import com.example.nexuswallet.feature.wallet.ui.SendBalanceCard
import com.example.nexuswallet.feature.wallet.ui.SendBottomBar
import com.example.nexuswallet.feature.wallet.ui.SendFeeSelection
import com.example.nexuswallet.feature.wallet.ui.SendTopBar
import com.example.nexuswallet.feature.wallet.ui.rememberSendErrorState
import com.example.nexuswallet.ui.theme.bitcoinLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitcoinSendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, CoinType, String, String, FeeLevel?, NetworkType?) -> Unit,
    walletId: String,
    network: NetworkType? = null,
    viewModel: BitcoinSendViewModel = hiltViewModel()
) {
    var showMaxDialog by remember { mutableStateOf(false) }
    var showNetworkSelector by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val addressFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }

    var addressTouched by remember { mutableStateOf(false) }
    var amountTouched by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    var amountFocused by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()

    // Extract Bitcoin network from NetworkType
    val bitcoinNetwork = when (network) {
        NetworkType.BITCOIN_MAINNET -> BitcoinNetwork.Mainnet
        NetworkType.BITCOIN_TESTNET -> BitcoinNetwork.Testnet
        else -> null
    }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.handleEvent(BitcoinSendEvent.Initialize(walletId, bitcoinNetwork))
    }

    val currentNetworkName = when (state.network) {
        BitcoinNetwork.Mainnet -> "Bitcoin Mainnet"
        BitcoinNetwork.Testnet -> "Bitcoin Testnet"
    }

    val availableNetworks = listOf(
        NetworkType.BITCOIN_MAINNET,
        NetworkType.BITCOIN_TESTNET
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
                title = "Send Bitcoin",
                iconRes = R.drawable.bitcoin,
                coinColor = bitcoinLight,
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
                    currentNetwork = currentNetworkName,
                    onNetworkSelected = { selectedNetwork ->
                        when (selectedNetwork) {
                            NetworkType.BITCOIN_MAINNET -> {
                                viewModel.handleEvent(BitcoinSendEvent.SwitchNetwork(BitcoinNetwork.Mainnet))
                            }
                            NetworkType.BITCOIN_TESTNET -> {
                                viewModel.handleEvent(BitcoinSendEvent.SwitchNetwork(BitcoinNetwork.Testnet))
                            }
                            else -> {}
                        }
                        showNetworkSelector = false
                    },
                    onDismiss = { showNetworkSelector = false }
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
                    currentNetwork = currentNetworkName,
                    onClick = { showNetworkSelector = true }
                )

                // Balance Card
                SendBalanceCard(
                    balance = state.balance,
                    balanceFormatted = state.balanceFormatted,
                    coinColor = bitcoinLight,
                    iconRes = R.drawable.bitcoin,
                    address = state.walletAddress,
                    network = currentNetworkName
                )

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
                        viewModel.handleEvent(BitcoinSendEvent.UpdateAddress(it))
                    },
                    onFocusChange = { isFocused ->
                        addressFocused = isFocused
                        // Mark as touched when focus leaves and field has content
                        if (!isFocused && state.toAddress.isNotEmpty()) {
                            addressTouched = true
                        }
                    },
                    placeholder = if (state.network == BitcoinNetwork.Testnet)
                        "Enter Bitcoin testnet address"
                    else
                        "Enter Bitcoin address",
                    isValid = !errorState.showAddressError && !errorState.showSelfSendError,
                    errorMessage = errorState.addressErrorMessage,
                    onPaste = { pastedText ->
                        addressTouched = true
                        viewModel.handleEvent(BitcoinSendEvent.UpdateAddress(pastedText))
                    },
                    focusRequester = addressFocusRequester
                )

                // Amount Input
                SendAmountInput(
                    amount = state.amount,
                    coinType = CoinType.BITCOIN,
                    onAmountChange = {
                        amountTouched = true
                        viewModel.handleEvent(BitcoinSendEvent.UpdateAmount(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
                        // Mark as touched when focus leaves and field has content
                        if (!isFocused && state.amount.isNotEmpty()) {
                            amountTouched = true
                        }
                    },
                    balance = state.balance,
                    symbol = "BTC",
                    coinColor = bitcoinLight,
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
                    onFeeLevelChange = {
                        viewModel.handleEvent(BitcoinSendEvent.UpdateFeeLevel(it))
                    },
                    feeEstimate = state.feeEstimate,
                    coinType = CoinType.BITCOIN
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Bar
            SendBottomBar(
                isValid = state.isValid,
                isLoading = state.isLoading,
                error = errorState.activeError,
                onSend = {
                    focusManager.clearFocus()
                    val currentNetwork = when (state.network) {
                        BitcoinNetwork.Mainnet -> NetworkType.BITCOIN_MAINNET
                        BitcoinNetwork.Testnet -> NetworkType.BITCOIN_TESTNET
                    }
                    onNavigateToReview(
                        walletId,
                        CoinType.BITCOIN,
                        state.toAddress,
                        state.amount,
                        state.feeLevel,
                        currentNetwork
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Max Amount Dialog
    if (showMaxDialog) {
        MaxAmountDialog(
            balance = state.balance,
            feeEstimate = state.feeEstimate,
            tokenSymbol = "BTC",
            coinType = CoinType.BITCOIN,
            onDismiss = { showMaxDialog = false },
            onConfirm = { maxAmount ->
                amountTouched = true
                viewModel.handleEvent(BitcoinSendEvent.UpdateAmount(maxAmount))
                showMaxDialog = false
            }
        )
    }
}