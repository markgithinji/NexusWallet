package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val getEthereumWalletUseCase: GetEthereumWalletUseCase,
    private val sendEVMAssetUseCase: SendEVMAssetUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val validateEVMSendUseCase: ValidateEVMSendUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class EthSendUiState(
        val walletId: String = "",
        val walletName: String = "",
        val fromAddress: String = "",
        val network: EthereumNetwork = EthereumNetwork.Sepolia,
        val availableNetworks: List<EthereumNetwork> = emptyList(),
        val availableTokens: List<EVMToken> = emptyList(),
        val selectedToken: EVMToken? = null,
        val ethBalance: BigDecimal = BigDecimal.ZERO,
        val tokenBalance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 ETH",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: EVMFeeEstimate? = null,
        val validationResult: ValidateEVMSendUseCase.ValidationResult = ValidateEVMSendUseCase.ValidationResult(
            isValid = false
        ),
        val isLoading: Boolean = false,
        val error: String? = null,
        val step: String = "",
        val isInitialized: Boolean = false,
        val balancesLoaded: Boolean = false
    )

    private val _uiState = MutableStateFlow(EthSendUiState())
    val uiState: StateFlow<EthSendUiState> = _uiState.asStateFlow()

    private var wallet: Wallet? = null
    private var evmTokensByNetwork: Map<EthereumNetwork, List<EVMToken>> = emptyMap()

    fun initialize(walletId: String, network: EthereumNetwork? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isInitialized = false,
                    balancesLoaded = false
                )
            }

            Log.d("EthereumVM", "initialize() called with walletId: $walletId, network: $network")

            // Load wallet
            wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            // Group EVM tokens by network
            evmTokensByNetwork = wallet!!.evmTokens.groupBy { it.network }
            val availableNetworks = evmTokensByNetwork.keys.toList()

            if (availableNetworks.isEmpty()) {
                _uiState.update { it.copy(error = "No EVM tokens found", isLoading = false) }
                return@launch
            }

            // Determine target network
            val targetNetwork = network ?: availableNetworks.firstOrNull()
            if (targetNetwork == null) {
                _uiState.update { it.copy(error = "No network available", isLoading = false) }
                return@launch
            }

            Log.d("EthereumVM", "Target network selected: $targetNetwork")

            val networkTokens = evmTokensByNetwork[targetNetwork] ?: emptyList()
            val nativeEth = networkTokens.filterIsInstance<NativeETH>().firstOrNull()
            val initialToken = nativeEth ?: networkTokens.firstOrNull()

            _uiState.update {
                it.copy(
                    walletId = walletId,
                    walletName = wallet!!.name,
                    fromAddress = initialToken?.address ?: "",
                    network = targetNetwork,
                    availableNetworks = availableNetworks,
                    availableTokens = networkTokens,
                    selectedToken = initialToken,
                    isInitialized = true
                )
            }

            // Load balance and fee estimate for the initial token
            loadBalances()
            loadFeeEstimate()
        }
    }

    fun setTransactionData(
        toAddress: String,
        amount: String,
        feeLevel: FeeLevel
    ) {
        viewModelScope.launch {
            Log.d("EthereumVM", "setTransactionData() called - to: $toAddress, amount: $amount, feeLevel: $feeLevel")

            val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO

            _uiState.update {
                it.copy(
                    toAddress = toAddress,
                    amount = amount,
                    amountValue = amountValue,
                    feeLevel = feeLevel
                )
            }

            // Wait for balances to load
            if (!_uiState.value.balancesLoaded) {
                snapshotFlow { _uiState.value.balancesLoaded }
                    .filter { it }
                    .firstOrNull()
            }

            validateInputs()
        }
    }

    fun switchNetwork(network: EthereumNetwork) {
        viewModelScope.launch {
            Log.d("EthereumVM", "switchNetwork() called to: $network")

            val networkTokens = evmTokensByNetwork[network] ?: emptyList()
            val nativeEth = networkTokens.filterIsInstance<NativeETH>().firstOrNull()
            val newToken = nativeEth ?: networkTokens.firstOrNull()

            _uiState.update {
                it.copy(
                    network = network,
                    availableTokens = networkTokens,
                    selectedToken = newToken,
                    fromAddress = newToken?.address ?: "",
                    toAddress = "",
                    amount = "",
                    amountValue = BigDecimal.ZERO,
                    feeEstimate = null,
                    error = null,
                    validationResult = ValidateEVMSendUseCase.ValidationResult(isValid = false),
                    balancesLoaded = false,
                    tokenBalance = BigDecimal.ZERO // Reset token balance
                )
            }

            loadBalances()
            loadFeeEstimate()
        }
    }

    fun selectToken(token: EVMToken) {
        viewModelScope.launch {
            Log.d("EthereumVM", "selectToken() called: ${token.symbol}")

            _uiState.update {
                it.copy(
                    selectedToken = token,
                    fromAddress = token.address,
                    balancesLoaded = false,
                    tokenBalance = BigDecimal.ZERO, // Reset token balance
                    validationResult = ValidateEVMSendUseCase.ValidationResult(isValid = false)
                )
            }
            loadBalances()
        }
    }

    private suspend fun loadBalances() {
        val state = _uiState.value
        val token = state.selectedToken ?: return
        val currentTokenId = token.externalId

        Log.d("EthereumVM", "loadBalances() for network: ${state.network}, token: ${token.symbol}")

        // Load ETH balance (for gas)
        val ethBalanceResult = evmBlockchainRepository.getNativeBalance(
            address = token.address,
            network = state.network
        )

        when (ethBalanceResult) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    // Only update if token hasn't changed
                    if (currentState.selectedToken?.externalId == currentTokenId) {
                        currentState.copy(ethBalance = ethBalanceResult.data)
                    } else {
                        currentState
                    }
                }
                Log.d("EthereumVM", "ETH balance loaded: ${ethBalanceResult.data}")
            }
            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentTokenId) {
                        currentState.copy(error = "Failed to load ETH balance: ${ethBalanceResult.message}")
                    } else {
                        currentState
                    }
                }
            }
            Result.Loading -> {}
        }

        // Load token balance (for the selected token - could be ETH, USDC, etc.)
        val tokenBalanceResult = when (token) {
            is NativeETH -> {
                // For ETH, we don't need to make another API call
                // Just use the ETH balance we already have
                Result.Success(state.ethBalance)
            }
            else -> {
                // For tokens like USDC, get the token balance
                evmBlockchainRepository.getTokenBalance(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    tokenDecimals = token.decimals,
                    network = state.network
                )
            }
        }

        when (tokenBalanceResult) {
            is Result.Success -> {
                val balance = tokenBalanceResult.data
                _uiState.update { currentState ->
                    // Only update if this is still the selected token
                    if (currentState.selectedToken?.externalId == currentTokenId) {
                        currentState.copy(
                            tokenBalance = balance,
                            balanceFormatted = when (token) {
                                is USDCToken, is USDTToken ->
                                    "$${balance.setScale(2, RoundingMode.HALF_UP)} ${token.symbol}"
                                else ->
                                    "${balance.setScale(4, RoundingMode.HALF_UP)} ${token.symbol}"
                            },
                            isLoading = false,
                            balancesLoaded = true
                        )
                    } else {
                        currentState
                    }
                }
                Log.d("EthereumVM", "Token balance loaded: $balance for ${token.symbol}")

                // Only validate if this is still the selected token
                if (_uiState.value.selectedToken?.externalId == currentTokenId) {
                    validateInputs()
                }
            }
            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentTokenId) {
                        currentState.copy(
                            error = "Failed to load balance: ${tokenBalanceResult.message}",
                            isLoading = false,
                            balancesLoaded = true // Mark as loaded even on error to prevent infinite waiting
                        )
                    } else {
                        currentState
                    }
                }
            }
            Result.Loading -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val state = _uiState.value
        val currentToken = state.selectedToken

        Log.d("EthereumVM", "loadFeeEstimate() for network: ${state.network}, isToken: ${state.selectedToken !is NativeETH}")

        val feeEstimateResult = getFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            network = state.network,
            isToken = state.selectedToken !is NativeETH
        )

        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentToken?.externalId) {
                        currentState.copy(feeEstimate = feeEstimateResult.data)
                    } else {
                        currentState
                    }
                }
                validateInputs()
            }
            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentToken?.externalId) {
                        currentState.copy(error = "Failed to load fee: ${feeEstimateResult.message}")
                    } else {
                        currentState
                    }
                }
            }
            Result.Loading -> {}
        }
    }

    fun onEvent(event: EthereumSendEvent) {
        viewModelScope.launch {
            when (event) {
                is EthereumSendEvent.ToAddressChanged -> {
                    _uiState.update { it.copy(toAddress = event.address) }
                    validateInputs()
                }
                is EthereumSendEvent.AmountChanged -> {
                    val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    _uiState.update {
                        it.copy(
                            amount = event.amount,
                            amountValue = amountValue
                        )
                    }
                    validateInputs()
                }
                is EthereumSendEvent.NoteChanged -> _uiState.update { it.copy(note = event.note) }
                is EthereumSendEvent.FeeLevelChanged -> {
                    _uiState.update { it.copy(feeLevel = event.feeLevel) }
                    loadFeeEstimate()
                }
                EthereumSendEvent.Validate -> validateInputs()
                EthereumSendEvent.ClearError -> clearError()
            }
        }
    }

    private suspend fun validateInputs(): Boolean {
        val state = _uiState.value
        val token = state.selectedToken ?: return false

        Log.d("EthereumVM", "validateInputs() - network: ${state.network}, token: ${token.symbol}, to: ${state.toAddress}, amount: ${state.amountValue}")
        Log.d("EthereumVM", "Balances - ETH: ${state.ethBalance}, Token: ${state.tokenBalance}")

        val validationResult = validateEVMSendUseCase(
            toAddress = state.toAddress,
            amountValue = state.amountValue,
            fromAddress = state.fromAddress,
            tokenBalance = state.tokenBalance,
            ethBalance = state.ethBalance,
            feeLevel = state.feeLevel,
            token = token
        )

        _uiState.update {
            it.copy(
                validationResult = validationResult,
                feeEstimate = validationResult.feeEstimate ?: it.feeEstimate,
                error = validationResult.addressError
                    ?: validationResult.amountError
                    ?: validationResult.balanceError
                    ?: validationResult.selfSendError
                    ?: validationResult.gasError
            )
        }

        Log.d("EthereumVM", "Validation result: isValid=${validationResult.isValid}, balanceError=${validationResult.balanceError}, gasError=${validationResult.gasError}")

        return validationResult.isValid
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val token = state.selectedToken

            if (state.walletId.isEmpty() || token == null) {
                _uiState.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            if (!validateInputs()) return@launch

            _uiState.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendEVMAssetUseCase(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel,
                token = token,
                note = state.note.takeIf { it.isNotEmpty() }
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _uiState.update { it.copy(isLoading = false, step = "Sent!") }
                        onSuccess(sendResult.txHash)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed"
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}