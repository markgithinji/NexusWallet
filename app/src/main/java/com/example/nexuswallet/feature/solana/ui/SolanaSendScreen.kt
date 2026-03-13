package com.example.nexuswallet.feature.solana.ui

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
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
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
import com.example.nexuswallet.ui.theme.solanaLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolanaSendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, String, String, FeeLevel?, Network) -> Unit,
    walletId: String,
    network: Network,
    viewModel: SolanaSendViewModel = hiltViewModel()
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

    // Initialize ViewModel with the network directly
    LaunchedEffect(Unit) {
        viewModel.init(walletId, network as SolanaNetwork)
    }

    val availableNetworks = listOf(
        SolanaNetwork.Mainnet,
        SolanaNetwork.Devnet
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
                title = "Send Solana",
                iconRes = R.drawable.solana,
                coinColor = solanaLight,
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
                        viewModel.switchNetwork(selectedNetwork as SolanaNetwork)
                        showNetworkSelector = false
                    },
                    onDismiss = { showNetworkSelector = false }
                )
            }

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

                // Balance Card
                SendBalanceCard(
                    balance = state.balance,
                    balanceFormatted = state.balanceFormatted,
                    coinColor = solanaLight,
                    iconRes = R.drawable.solana,
                    address = state.walletAddress,
                    network = state.network
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
                        viewModel.onEvent(SolanaSendEvent.ToAddressChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        addressFocused = isFocused
                        if (!isFocused && state.toAddress.isNotEmpty()) {
                            addressTouched = true
                        }
                    },
                    placeholder = "Enter Solana address",
                    isValid = !errorState.showAddressError && !errorState.showSelfSendError,
                    errorMessage = errorState.addressErrorMessage,
                    onPaste = { pastedText ->
                        addressTouched = true
                        viewModel.onEvent(SolanaSendEvent.ToAddressChanged(pastedText))
                    },
                    focusRequester = addressFocusRequester
                )

                // Amount Input
                SendAmountInput(
                    amount = state.amount,
                    coinType = CoinType.SOLANA,
                    onAmountChange = {
                        amountTouched = true
                        viewModel.onEvent(SolanaSendEvent.AmountChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
                        if (!isFocused && state.amount.isNotEmpty()) {
                            amountTouched = true
                        }
                    },
                    balance = state.balance,
                    symbol = "SOL",
                    coinColor = solanaLight,
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
                    onFeeLevelChange = { viewModel.onEvent(SolanaSendEvent.FeeLevelChanged(it)) },
                    feeEstimate = state.feeEstimate,
                    coinType = CoinType.SOLANA
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
            balance = state.balance,
            feeEstimate = state.feeEstimate,
            tokenSymbol = "SOL",
            coinType = CoinType.SOLANA,
            onDismiss = { showMaxDialog = false },
            onConfirm = { maxAmount ->
                amountTouched = true
                viewModel.onEvent(SolanaSendEvent.AmountChanged(maxAmount))
                showMaxDialog = false
            }
        )
    }
}