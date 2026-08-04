package com.example.nexuswallet.feature.ethereum.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.usecase.GetEVMFeeEstimateUseCase
import com.example.nexuswallet.feature.ethereum.domain.usecase.SendEVMAssetUseCase
import com.example.nexuswallet.feature.ethereum.domain.usecase.ValidateEVMSendUseCase
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAddressBookEntriesUseCase
import com.example.nexuswallet.feature.wallet.util.ExplorerUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class EVMSendViewModel @Inject constructor(
    private val sendEVMAssetUseCase: SendEVMAssetUseCase,
    private val getEVMFeeEstimateUseCase: GetEVMFeeEstimateUseCase,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val validateEVMSendUseCase: ValidateEVMSendUseCase,
    private val walletRepository: WalletRepository,
    private val marketRepository: MarketRepository,
    private val getAddressBookEntriesUseCase: GetAddressBookEntriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EVMSendUiState())
    val uiState: StateFlow<EVMSendUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EVMSendEffect>()
    val effect: SharedFlow<EVMSendEffect> = _effect.asSharedFlow()

    private val _cryptoObject = MutableStateFlow<Cipher?>(null)
    val cryptoObject: StateFlow<Cipher?> = _cryptoObject.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    private var wallet: Wallet? = null
    private var evmTokensByNetwork: Map<EthereumNetwork, List<EVMToken>> = emptyMap()
    private var currentCoin: EVMToken? = null
    private var feeJob: Job? = null

    init {
        observeAddressBook()
    }

    private fun observeAddressBook() {
        viewModelScope.launch {
            getAddressBookEntriesUseCase().collect { entries ->
                _uiState.update { it.copy(addressBookEntries = entries) }
            }
        }
    }

    fun initialize(walletId: String, coin: EVMToken? = null) {
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
            val allTokens = wallet!!.evmTokens

            if (allTokens.isEmpty()) {
                _uiState.update { it.copy(error = "No EVM tokens found", isLoading = false) }
                return@launch
            }

            // Determine target coin
            val targetCoin =
                coin ?: allTokens.firstOrNull { it is NativeETH } ?: allTokens.firstOrNull()

            if (targetCoin == null) {
                _uiState.update { it.copy(error = "No token available", isLoading = false) }
                return@launch
            }

            currentCoin = targetCoin
            val networkTokens = evmTokensByNetwork[targetCoin.network] ?: emptyList()

            _uiState.update {
                it.copy(
                    walletId = walletId,
                    walletName = wallet!!.name,
                    fromAddress = targetCoin.address,
                    network = targetCoin.network,
                    coin = targetCoin,
                    availableNetworks = availableNetworks,
                    availableTokens = networkTokens,
                    selectedToken = targetCoin,
                    isInitialized = true
                )
            }

            // Load balance and fee estimate for the initial token
            loadBalances()
            loadFeeEstimate()
            loadFiatRate(targetCoin)
        }
    }

    fun switchNetwork(network: EthereumNetwork) {
        viewModelScope.launch {
            val networkTokens = evmTokensByNetwork[network] ?: emptyList()
            val nativeEth = networkTokens.filterIsInstance<NativeETH>().firstOrNull()
            val newToken = nativeEth ?: networkTokens.firstOrNull()

            if (newToken == null) {
                _uiState.update { it.copy(error = "No tokens available on ${network.name}") }
                return@launch
            }

            currentCoin = newToken

            _uiState.update {
                it.copy(
                    network = network,
                    coin = newToken,
                    availableTokens = networkTokens,
                    selectedToken = newToken,
                    fromAddress = newToken.address,
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
            loadFiatRate(newToken)
        }
    }

    fun selectToken(token: EVMToken) {
        viewModelScope.launch {
            currentCoin = token

            _uiState.update {
                it.copy(
                    selectedToken = token,
                    coin = token,
                    fromAddress = token.address,
                    balancesLoaded = false,
                    tokenBalance = BigDecimal.ZERO,
                    validationResult = SendValidationResult(isValid = false)
                )
            }
            loadBalances()
            loadFeeEstimate()
            loadFiatRate(token)
        }
    }

    private suspend fun loadFiatRate(token: EVMToken?) {
        val tokenId = when (token) {
            is NativeETH -> "ethereum"
            is USDCToken -> "usd-coin"
            is USDTToken -> "tether"
            else -> "ethereum"
        }

        when (val result = marketRepository.getTokenDetails(tokenId)) {
            is Result.Success -> {
                _uiState.update { it.copy(fiatRate = result.data.currentPrice) }
            }

            else -> {}
        }
    }

    private suspend fun loadBalances() {
        val state = _uiState.value
        val token = state.selectedToken ?: return

        // Load ETH balance (for gas)
        val ethBalanceResult = evmBlockchainRepository.getNativeBalance(
            address = token.address,
            network = state.network
        )

        when (ethBalanceResult) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken == token) {
                        currentState.copy(ethBalance = ethBalanceResult.data)
                    } else {
                        currentState
                    }
                }
            }

            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken == token) {
                        currentState.copy(error = "Failed to load ETH balance: ${ethBalanceResult.message}")
                    } else {
                        currentState
                    }
                }
            }

            Result.Loading -> {}
        }

        // Load token balance (for the selected token)
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
                    if (currentState.selectedToken == token) {
                        currentState.copy(
                            tokenBalance = balance,
                            balanceFormatted = when (token) {
                                is USDCToken, is USDTToken ->
                                    "$${balance.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${token.symbol}"

                                else ->
                                    "${balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${token.symbol}"
                            },
                            isLoading = false,
                            balancesLoaded = true
                        )
                    } else {
                        currentState
                    }
                }

                if (_uiState.value.selectedToken == token) {
                    validateInputs()
                }
            }

            is Result.Error -> {
                _uiState.update { currentState ->
                    if (currentState.selectedToken == token) {
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

    private fun loadFeeEstimate() {
        feeJob?.cancel()
        feeJob = viewModelScope.launch {
            delay(500) // Debounce fee estimation
            val state = _uiState.value
            val currentToken = state.selectedToken ?: return@launch

            // Set fee loading state
            _uiState.update { currentState ->
                if (currentState.selectedToken == currentToken) {
                    currentState.copy(isFeeLoading = true)
                } else {
                    currentState
                }
            }

            // Prepare parameters for dynamic estimation if available
            val amountInWei = if (state.amountValue > BigDecimal.ZERO) {
                state.amountValue.multiply(BigDecimal.TEN.pow(currentToken.decimals)).toBigInteger()
            } else {
                BigInteger.ONE // Use small amount for estimation if not entered
            }

            val feeEstimateResult = getEVMFeeEstimateUseCase(
                feeLevel = state.feeLevel,
                network = state.network,
                isToken = currentToken.evmTokenType != EVMTokenType.NATIVE,
                fromAddress = state.fromAddress,
                toAddress = state.toAddress.takeIf { it.length >= 40 }, // Basic check for address
                amount = amountInWei,
                tokenContract = if (currentToken.evmTokenType == EVMTokenType.NATIVE) null else currentToken.contractAddress
            )

            when (feeEstimateResult) {
                is Result.Success -> {
                    _uiState.update { currentState ->
                        if (currentState.selectedToken == currentToken) {
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
                        if (currentState.selectedToken == currentToken) {
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
    }

    fun onEvent(event: EVMSendEvent) {
        viewModelScope.launch {
            when (event) {
                is EVMSendEvent.ToAddressChanged -> {
                    _uiState.update { it.copy(toAddress = event.address) }
                    validateInputs()
                    if (event.address.length >= 40) {
                        loadFeeEstimate()
                    }
                }

                is EVMSendEvent.AmountChanged -> {
                    val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    _uiState.update {
                        it.copy(
                            amount = event.amount,
                            amountValue = amountValue
                        )
                    }
                    validateInputs()
                    if (amountValue > BigDecimal.ZERO) {
                        loadFeeEstimate()
                    }
                }

                is EVMSendEvent.NoteChanged -> _uiState.update { it.copy(note = event.note) }
                is EVMSendEvent.FeeLevelChanged -> {
                    _uiState.update { it.copy(feeLevel = event.feeLevel) }
                    loadFeeEstimate()
                }

                EVMSendEvent.Validate -> validateInputs()
                EVMSendEvent.ClearError -> clearError()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val state = _uiState.value
        val token = state.selectedToken ?: return false

        val validationResult = validateEVMSendUseCase(
            toAddress = state.toAddress,
            amountValue = state.amountValue,
            fromAddress = state.fromAddress,
            tokenBalance = state.tokenBalance,
            ethBalance = state.ethBalance,
            feeEstimate = state.feeEstimate,
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

    fun send(cipher: Cipher? = null, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val token = state.selectedToken ?: currentCoin

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
                note = state.note.takeIf { it.isNotEmpty() },
                cipher = cipher
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _uiState.update { it.copy(isLoading = false, step = "Sent!") }
                        val explorerUrl =
                            ExplorerUrlHelper.getExplorerUrl(sendResult.txHash, state.network)
                        _effect.emit(
                            EVMSendEffect.TransactionSent(
                                sendResult.txHash,
                                explorerUrl
                            )
                        )
                        onSuccess(sendResult.txHash)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed"
                            )
                        }
                        _effect.emit(
                            EVMSendEffect.ShowError(
                                sendResult.error ?: "Send failed"
                            )
                        )
                    }
                }

                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject?.cipher
                        _authRequest.value = System.currentTimeMillis()
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                        _effect.emit(EVMSendEffect.ShowError(result.message))
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    fun completeSendAfterBiometric(cipher: Cipher? = null, onSuccess: (String) -> Unit) {
        _cryptoObject.value = null
        _authRequest.value = null
        send(cipher = cipher, onSuccess = onSuccess)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearAuthRequest() {
        _authRequest.value = null
    }
}
