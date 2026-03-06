package com.example.nexuswallet.feature.coin.ethereum


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
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
import com.example.nexuswallet.feature.wallet.ui.TokenSelectorCard
import com.example.nexuswallet.feature.wallet.ui.TokenSelectorDialog
import com.example.nexuswallet.feature.wallet.ui.rememberSendErrorState
import com.example.nexuswallet.feature.wallet.ui.usdtLight
import com.example.nexuswallet.ui.theme.ethereumLight
import com.example.nexuswallet.ui.theme.usdcLight
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EthereumSendScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReview: (String, CoinType, String, String, FeeLevel?, NetworkType?) -> Unit,
    walletId: String,
    coinType: CoinType, // Can be CoinType.ETHEREUM or CoinType.USDC
    network: NetworkType? = null,
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

    val state by viewModel.uiState.collectAsState()

    // Extract Ethereum network from NetworkType
    val ethereumNetwork = when (network) {
        NetworkType.ETHEREUM_MAINNET -> EthereumNetwork.Mainnet
        NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
        else -> null
    }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(walletId, ethereumNetwork)

        // Auto-select USDC if needed
        if (coinType == CoinType.USDC) {
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
        else -> when (coinType) {
            CoinType.ETHEREUM -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
            CoinType.USDC -> Triple(R.drawable.usdc, usdcLight, "USDC")
            else -> Triple(R.drawable.ethereum, ethereumLight, "Ethereum")
        }
    }

    val currentNetworkName = when (state.network) {
        EthereumNetwork.Mainnet -> "Ethereum Mainnet"
        EthereumNetwork.Sepolia -> "Ethereum Sepolia"
    }

    val availableNetworks = listOf(
        NetworkType.ETHEREUM_MAINNET,
        NetworkType.ETHEREUM_SEPOLIA
    )

    // Use the common error state calculator
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
                    currentNetwork = currentNetworkName,
                    onNetworkSelected = { selectedNetwork ->
                        when (selectedNetwork) {
                            NetworkType.ETHEREUM_MAINNET -> {
                                viewModel.switchNetwork(EthereumNetwork.Mainnet)
                            }
                            NetworkType.ETHEREUM_SEPOLIA -> {
                                viewModel.switchNetwork(EthereumNetwork.Sepolia)
                            }
                            else -> {}
                        }
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
                    currentNetwork = currentNetworkName,
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
                        "$${state.tokenBalance.setScale(2, RoundingMode.HALF_UP)} ${selectedToken?.symbol}"
                    else
                        "${state.tokenBalance.setScale(6, RoundingMode.HALF_UP)} ${selectedToken?.symbol ?: "ETH"}",
                    coinColor = coinColor,
                    iconRes = iconRes,
                    address = state.fromAddress,
                    network = currentNetworkName
                )

                // Show ETH balance for gas if this is a token
                if (selectedToken !is NativeETH) {
                    Text(
                        text = "ETH for gas: ${state.ethBalance.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ETH",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Error Banner - only show if there's an active error and no field is focused
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
                    coinType = CoinType.BITCOIN,
                    onAmountChange = {
                        amountTouched = true
                        viewModel.onEvent(EthereumSendEvent.AmountChanged(it))
                    },
                    onFocusChange = { isFocused ->
                        amountFocused = isFocused
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
                    coinType = CoinType.ETHEREUM,
                    token = selectedToken
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
                    val currentNetwork = when (state.network) {
                        EthereumNetwork.Mainnet -> NetworkType.ETHEREUM_MAINNET
                        EthereumNetwork.Sepolia -> NetworkType.ETHEREUM_SEPOLIA
                    }
                    onNavigateToReview(
                        walletId,
                        coinType,
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
            balance = if (selectedToken is NativeETH) state.ethBalance else state.tokenBalance,
            feeEstimate = state.feeEstimate,
            tokenSymbol = selectedToken?.symbol ?: "ETH",
            coinType = coinType,
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