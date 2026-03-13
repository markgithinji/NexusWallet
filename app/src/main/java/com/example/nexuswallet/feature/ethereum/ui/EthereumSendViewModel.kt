package com.example.nexuswallet.feature.ethereum.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.usecase.GetFeeEstimateUseCase
import com.example.nexuswallet.feature.ethereum.domain.usecase.SendEVMAssetUseCase
import com.example.nexuswallet.feature.ethereum.domain.usecase.ValidateEVMSendUseCase
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val sendEVMAssetUseCase: SendEVMAssetUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val validateEVMSendUseCase: ValidateEVMSendUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EthSendUiState())
    val uiState: StateFlow<EthSendUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EthereumSendEffect>()
    val effect: SharedFlow<EthereumSendEffect> = _effect.asSharedFlow()

    private var wallet: Wallet? = null
    private var evmTokensByNetwork: Map<EthereumNetwork, List<EVMToken>> = emptyMap()

    fun initialize(walletId: String, network: EthereumNetwork? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isFeeLoading = true,
                    error = null,
                    isInitialized = false,
                    balancesLoaded = false
                )
            }

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

    fun switchNetwork(network: EthereumNetwork) {
        viewModelScope.launch {

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
                    validationResult = SendValidationResult(isValid = false),
                    balancesLoaded = false,
                    tokenBalance = BigDecimal.ZERO
                )
            }

            loadBalances()
            loadFeeEstimate()
        }
    }

    fun selectToken(token: EVMToken) {
        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    selectedToken = token,
                    fromAddress = token.address,
                    balancesLoaded = false,
                    tokenBalance = BigDecimal.ZERO,
                    validationResult = SendValidationResult(isValid = false)
                )
            }
            loadBalances()
        }
    }

    private suspend fun loadBalances() {
        val state = _uiState.value
        val token = state.selectedToken ?: return
        val currentTokenId = token.externalId

        // Load ETH balance (for gas)
        val ethBalanceResult = evmBlockchainRepository.getNativeBalance(
            address = token.address,
            network = state.network
        )

        when (ethBalanceResult) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentTokenId) {
                        currentState.copy(ethBalance = ethBalanceResult.data)
                    } else {
                        currentState
                    }
                }
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
                Result.Success(state.ethBalance)
            }

            else -> {
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
                            balancesLoaded = true
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

        // Set fee loading state
        _uiState.update { currentState ->
            if (currentState.selectedToken?.externalId == currentToken?.externalId) {
                currentState.copy(isFeeLoading = true)
            } else {
                currentState
            }
        }

        val feeEstimateResult = getFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            network = state.network,
            isToken = state.selectedToken !is NativeETH
        )

        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentToken?.externalId) {
                        currentState.copy(
                            feeEstimate = feeEstimateResult.data,
                            isFeeLoading = false
                        )
                    } else {
                        currentState
                    }
                }
                validateInputs()
            }

            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken?.externalId == currentToken?.externalId) {
                        currentState.copy(
                            error = "Failed to load fee: ${feeEstimateResult.message}",
                            isFeeLoading = false
                        )
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
                error = when {
                    !validationResult.isValid -> {
                        validationResult.addressError
                            ?: validationResult.selfSendError
                            ?: validationResult.gasError
                            ?: validationResult.amountError
                            ?: validationResult.balanceError
                            ?: validationResult.networkError
                            ?: "Invalid transaction"
                    }

                    else -> null
                }
            )
        }

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
                        _effect.emit(EthereumSendEffect.TransactionSent(sendResult.txHash))
                        onSuccess(sendResult.txHash)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed"
                            )
                        }
                        _effect.emit(
                            EthereumSendEffect.ShowError(
                                sendResult.error ?: "Send failed"
                            )
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                    _effect.emit(EthereumSendEffect.ShowError(result.message))
                }

                Result.Loading -> {}
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}