package com.example.nexuswallet.feature.solana.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import com.example.nexuswallet.feature.core.ui.rememberSendErrorState
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.ui.theme.solanaLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolanaSendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, String, String, FeeLevel?, Coin) -> Unit,
    walletId: String,
    coin: Coin,
    viewModel: SolanaSendViewModel = hiltViewModel()
) {
    var showMaxDialog by remember { mutableStateOf(false) }
    var showNetworkSelector by remember { mutableStateOf(false) }
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

    val state by viewModel.state.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.onEvent(SolanaSendEvent.ToAddressChanged(result.contents))
        }
    }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.init(walletId, coin as SolanaCoin)
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
                title = "Send ${coin.symbol}",
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

            // Address Book Dialog
            if (showAddressBook) {
                com.example.nexuswallet.feature.core.ui.AddressBookSelectorDialog(
                    entries = state.addressBookEntries.filter { it.chain == "Solana" },
                    onEntrySelected = { entry ->
                        viewModel.onEvent(SolanaSendEvent.ToAddressChanged(entry.address))
                        showAddressBook = false
                    },
                    onDismiss = { showAddressBook = false }
                )
            }

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

                // Balance Card
                SendBalanceCard(
                    balance = state.balance,
                    balanceFormatted = state.balanceFormatted,
                    fiatRate = state.fiatRate,
                    coinColor = solanaLight,
                    iconRes = R.drawable.solana,
                    address = state.walletAddress,
                    network = state.network
                )

                // Error Banner
                if (state.error != null) {
                    ErrorMessage(
                        error = state.error!!,
                        onDismiss = { viewModel.onEvent(SolanaSendEvent.ClearError) }
                    )
                }

                // Address Input
                SendAddressInput(
                    toAddress = state.toAddress,
                    onAddressChange = {
                        viewModel.onEvent(SolanaSendEvent.ToAddressChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        addressFocused = isFocused
                        if (isFocused) addressHasBeenFocused = true
                        if (!isFocused && addressHasBeenFocused) {
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
                    onScanClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan Solana Address")
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
                    coin = coin,
                    fiatRate = state.fiatRate,
                    onAmountChange = {
                        viewModel.onEvent(SolanaSendEvent.AmountChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
                        if (isFocused) amountHasBeenFocused = true
                        if (!isFocused && amountHasBeenFocused) {
                            amountTouched = true
                        }
                    },
                    balance = state.balance,
                    symbol = coin.symbol,
                    coinColor = solanaLight,
                    onMaxClick = {
                        amountTouched = true
                        showMaxDialog = true
                    },
                    errorMessage = errorState.amountErrorMessage,
                    focusRequester = amountFocusRequester,
                    isFiatMode = state.isFiatMode,
                    onModeToggle = {
                        viewModel.onEvent(SolanaSendEvent.ToggleFiatMode(it))
                    }
                )

                // Fee Selection
                SendFeeSelection(
                    feeLevel = state.feeLevel,
                    onFeeLevelChange = { viewModel.onEvent(SolanaSendEvent.FeeLevelChanged(it)) },
                    feeEstimate = state.feeEstimate,
                    coin = coin
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Bar
            SendBottomBar(
                isValid = state.validationResult.isValid,
                isLoading = state.isLoading || state.isFeeLoading,
                error = errorState.activeError,
                onSend = {
                    focusManager.clearFocus()
                    onNavigateToReview(
                        walletId,
                        state.toAddress,
                        state.amount,
                        state.feeLevel,
                        state.coin ?: coin
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
            fiatRate = state.fiatRate,
            tokenSymbol = coin.symbol,
            coin = coin,
            onDismiss = { showMaxDialog = false },
            onConfirm = { maxAmount ->
                amountTouched = true
                viewModel.onEvent(SolanaSendEvent.AmountChanged(maxAmount))
                showMaxDialog = false
            }
        )
    }
}
