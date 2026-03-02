package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val isInitialized: Boolean = false
    )

    private val _uiState = MutableStateFlow(EthSendUiState())
    val uiState: StateFlow<EthSendUiState> = _uiState.asStateFlow()

    private var wallet: Wallet? = null
    private var evmTokensByNetwork: Map<EthereumNetwork, List<EVMToken>> = emptyMap()

    fun initialize(walletId: String, network: EthereumNetwork? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isInitialized = false) }

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

            _uiState.update {
                it.copy(
                    walletId = walletId,
                    walletName = wallet!!.name,
                    fromAddress = nativeEth?.address ?: networkTokens.firstOrNull()?.address ?: "",
                    network = targetNetwork,
                    availableNetworks = availableNetworks,
                    availableTokens = networkTokens,
                    selectedToken = nativeEth ?: networkTokens.firstOrNull(),
                    isInitialized = true
                )
            }

            // Load balance and fee estimate
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

            // Wait for balances to load if they're still loading
            var attempts = 0
            while (_uiState.value.isLoading && attempts < 10) {
                delay(100)
                attempts++
            }

            validateInputs()
        }
    }

    fun switchNetwork(network: EthereumNetwork) {
        viewModelScope.launch {
            Log.d("EthereumVM", "switchNetwork() called to: $network")

            val networkTokens = evmTokensByNetwork[network] ?: emptyList()
            val nativeEth = networkTokens.filterIsInstance<NativeETH>().firstOrNull()

            _uiState.update {
                it.copy(
                    network = network,
                    availableTokens = networkTokens,
                    selectedToken = nativeEth ?: networkTokens.firstOrNull(),
                    fromAddress = nativeEth?.address ?: networkTokens.firstOrNull()?.address ?: "",
                    toAddress = "",
                    amount = "",
                    amountValue = BigDecimal.ZERO,
                    feeEstimate = null,
                    error = null,
                    validationResult = ValidateEVMSendUseCase.ValidationResult(isValid = false)
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
                    fromAddress = token.address
                )
            }
            loadBalances()
        }
    }

    private suspend fun loadBalances() {
        val state = _uiState.value
        val token = state.selectedToken ?: return

        Log.d("EthereumVM", "loadBalances() for network: ${state.network}, token: ${token.symbol}")

        // Load ETH balance (for gas)
        val ethBalanceResult = evmBlockchainRepository.getNativeBalance(
            address = token.address,
            network = state.network
        )

        when (ethBalanceResult) {
            is Result.Success -> {
                _uiState.update { it.copy(ethBalance = ethBalanceResult.data) }
                Log.d("EthereumVM", "ETH balance loaded: ${ethBalanceResult.data}")
            }
            is Result.Error -> {
                _uiState.update { it.copy(error = "Failed to load ETH balance: ${ethBalanceResult.message}") }
            }
            Result.Loading -> {}
        }

        // Load token balance
        val tokenBalanceResult = when (token) {
            is NativeETH -> evmBlockchainRepository.getNativeBalance(
                address = token.address,
                network = state.network
            )
            else -> evmBlockchainRepository.getTokenBalance(
                address = token.address,
                tokenContract = token.contractAddress,
                tokenDecimals = token.decimals,
                network = state.network
            )
        }

        when (tokenBalanceResult) {
            is Result.Success -> {
                val balance = tokenBalanceResult.data
                _uiState.update {
                    it.copy(
                        tokenBalance = balance,
                        balanceFormatted = "${balance.setScale(4, RoundingMode.HALF_UP)} ${token.symbol}",
                        isLoading = false
                    )
                }
                Log.d("EthereumVM", "Token balance loaded: $balance for ${token.symbol}")
                validateInputs()
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Failed to load balance: ${tokenBalanceResult.message}",
                        isLoading = false
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val state = _uiState.value
        Log.d("EthereumVM", "loadFeeEstimate() for network: ${state.network}, isToken: ${state.selectedToken !is NativeETH}")

        val feeEstimateResult = getFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            network = state.network,
            isToken = state.selectedToken !is NativeETH
        )

        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { it.copy(feeEstimate = feeEstimateResult.data) }
                validateInputs()
            }
            is Result.Error -> {
                _uiState.update { it.copy(error = "Failed to load fee: ${feeEstimateResult.message}") }
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

        Log.d("EthereumVM", "validateInputs() - network: ${state.network}, to: ${state.toAddress}, amount: ${state.amountValue}")

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
                feeEstimate = validationResult.feeEstimate ?: it.feeEstimate
            )
        }

        // Update error field for backward compatibility
        val firstError = validationResult.addressError
            ?: validationResult.amountError
            ?: validationResult.balanceError
            ?: validationResult.selfSendError
            ?: validationResult.gasError

        if (firstError != null) {
            _uiState.update { it.copy(error = firstError) }
        } else if (validationResult.isValid) {
            _uiState.update { it.copy(error = null) }
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