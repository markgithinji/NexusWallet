package com.example.nexuswallet.feature.coin.solana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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
class SolanaSendViewModel @Inject constructor(
    private val getSolanaWalletUseCase: GetSolanaWalletUseCase,
    private val sendSolanaUseCase: SendSolanaUseCase,
    private val getSolanaBalanceUseCase: GetSolanaBalanceUseCase,
    private val getSolanaFeeEstimateUseCase: GetSolanaFeeEstimateUseCase,
    private val validateSolanaSendUseCase: ValidateSolanaSendUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class SolanaSendUIState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val network: SolanaNetwork = SolanaNetwork.Devnet,
        val availableNetworks: List<SolanaNetwork> = emptyList(),
        val availableSplTokens: List<SPLToken> = emptyList(),
        val selectedSplToken: SPLToken? = null,
        val isNativeSol: Boolean = true,
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 SOL",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: SolanaFeeEstimate? = null,
        val validationResult: SendValidationResult = SendValidationResult(isValid = false),
        val isLoading: Boolean = false,
        val error: String? = null,
        val step: String = "",
        val isValid: Boolean = false
    )

    private val _state = MutableStateFlow(SolanaSendUIState())
    val state: StateFlow<SolanaSendUIState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SolanaSendEffect>()
    val effect: SharedFlow<SolanaSendEffect> = _effect.asSharedFlow()

    private var wallet: Wallet? = null
    private var solanaCoins: Map<SolanaNetwork, SolanaCoin> = emptyMap()

    fun init(walletId: String, network: SolanaNetwork? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Load wallet
            wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _state.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            // Get all Solana coins
            solanaCoins = wallet!!.solanaCoins.associateBy { it.network }
            val availableNetworks = solanaCoins.keys.toList()

            if (availableNetworks.isEmpty()) {
                _state.update { it.copy(error = "Solana not enabled for this wallet", isLoading = false) }
                return@launch
            }

            // Determine target network
            val targetNetwork = network ?: availableNetworks.firstOrNull() ?: SolanaNetwork.Devnet
            val solanaCoin = solanaCoins[targetNetwork]

            if (solanaCoin == null) {
                _state.update {
                    it.copy(
                        error = "Solana not enabled for network $targetNetwork",
                        isLoading = false,
                        availableNetworks = availableNetworks
                    )
                }
                return@launch
            }

            when (val result = getSolanaWalletUseCase(walletId, targetNetwork)) {
                is Result.Success -> {
                    val walletInfo = result.data
                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            walletAddress = walletInfo.walletAddress,
                            network = targetNetwork,
                            availableNetworks = availableNetworks,
                            availableSplTokens = solanaCoin.splTokens,
                            isNativeSol = true,
                            isLoading = false
                        )
                    }
                    loadBalance(walletInfo.walletAddress, targetNetwork)
                    loadFeeEstimate(targetNetwork)
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false,
                            availableNetworks = availableNetworks
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    fun setTransactionData(
        toAddress: String,
        amount: String,
        feeLevel: FeeLevel
    ) {
        _state.update {
            it.copy(
                toAddress = toAddress,
                amount = amount,
                amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                feeLevel = feeLevel
            )
        }

        viewModelScope.launch {
            loadFeeEstimate(_state.value.network)
            validateInputs()
        }
    }

    fun switchNetwork(network: SolanaNetwork) {
        val solanaCoin = solanaCoins[network]
        if (solanaCoin == null) {
            _state.update { it.copy(error = "Solana not available on $network") }
            return
        }

        _state.update {
            it.copy(
                network = network,
                walletAddress = solanaCoin.address,
                availableSplTokens = solanaCoin.splTokens,
                isNativeSol = true,
                selectedSplToken = null,
                toAddress = "",
                amount = "",
                amountValue = BigDecimal.ZERO,
                feeEstimate = null,
                error = null
            )
        }

        viewModelScope.launch {
            loadBalance(solanaCoin.address, network)
            loadFeeEstimate(network)
        }
    }

    fun selectAsset(isNative: Boolean, splToken: SPLToken? = null) {
        _state.update {
            it.copy(
                isNativeSol = isNative,
                selectedSplToken = splToken,
                amount = "",
                amountValue = BigDecimal.ZERO
            )
        }
        viewModelScope.launch {
            validateInputs()
        }
    }

    private suspend fun loadBalance(address: String, network: SolanaNetwork) {
        when (val balanceResult = getSolanaBalanceUseCase(address, network)) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(4, RoundingMode.HALF_UP)} SOL",
                        isLoading = false
                    )
                }
                validateInputs()
            }

            is Result.Error -> {
                _state.update {
                    it.copy(
                        error = "Failed to load balance: ${balanceResult.message}",
                        isLoading = false
                    )
                }
            }

            Result.Loading -> {}
        }
    }

    private suspend fun loadFeeEstimate(network: SolanaNetwork) {
        val currentState = _state.value
        if (currentState.walletId.isNotEmpty()) {
            when (val feeResult = getSolanaFeeEstimateUseCase(
                feeLevel = currentState.feeLevel,
                network = network
            )) {
                is Result.Success -> {
                    _state.update { it.copy(feeEstimate = feeResult.data) }
                    validateInputs()
                }

                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
                }

                Result.Loading -> {}
            }
        }
    }

    fun onEvent(event: SolanaSendEvent) {
        viewModelScope.launch {
            when (event) {
                is SolanaSendEvent.ToAddressChanged -> {
                    _state.update { it.copy(toAddress = event.address) }
                    validateInputs()
                }

                is SolanaSendEvent.AmountChanged -> {
                    val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    _state.update {
                        it.copy(
                            amount = event.amount,
                            amountValue = amountValue
                        )
                    }
                    validateInputs()
                }

                is SolanaSendEvent.FeeLevelChanged -> {
                    _state.update { it.copy(feeLevel = event.feeLevel) }
                    loadFeeEstimate(_state.value.network)
                }

                SolanaSendEvent.Validate -> validateInputs()
                SolanaSendEvent.ClearError -> clearError()
            }
        }
    }

    private suspend fun validateInputs(): Boolean {
        val currentState = _state.value

        val validationResult = validateSolanaSendUseCase(
            toAddress = currentState.toAddress,
            amountValue = currentState.amountValue,
            walletAddress = currentState.walletAddress,
            balance = currentState.balance,
            feeEstimate = currentState.feeEstimate
        )

        _state.update {
            it.copy(
                validationResult = validationResult,
                isValid = validationResult.isValid,
                error = when {
                    !validationResult.isValid -> {
                        validationResult.addressError
                            ?: validationResult.selfSendError
                            ?: validationResult.amountError
                            ?: validationResult.balanceError
                            ?: validationResult.gasError
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
            val state = _state.value
            val solanaCoin = solanaCoins[state.network]

            if (state.walletId.isEmpty() || solanaCoin == null) {
                _state.update { it.copy(error = "Wallet not properly loaded") }
                return@launch
            }

            if (!validateInputs()) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendSolanaUseCase(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel,
                network = state.network,
                note = null
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update { it.copy(isLoading = false, step = "Sent!") }
                        _effect.emit(SolanaSendEffect.TransactionSent(sendResult.txHash))
                        onSuccess(sendResult.txHash)
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed",
                                step = ""
                            )
                        }
                        _effect.emit(SolanaSendEffect.ShowError(sendResult.error ?: "Send failed"))
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                            step = ""
                        )
                    }
                    _effect.emit(SolanaSendEffect.ShowError(result.message))
                }

                Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun resetState() {
        _state.update {
            SolanaSendUIState(
                walletId = _state.value.walletId,
                walletName = _state.value.walletName,
                walletAddress = _state.value.walletAddress,
                network = _state.value.network,
                balance = _state.value.balance,
                balanceFormatted = _state.value.balanceFormatted
            )
        }
    }

    fun updateToAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        viewModelScope.launch {
            validateInputs()
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        _state.update {
            it.copy(
                amount = amount,
                amountValue = amountValue
            )
        }
        viewModelScope.launch {
            validateInputs()
        }
    }

    fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        viewModelScope.launch {
            loadFeeEstimate(_state.value.network)
        }
    }
}

sealed class SolanaSendEffect {
    data class ShowError(val message: String) : SolanaSendEffect()
    data class TransactionSent(val txHash: String) : SolanaSendEffect()
}