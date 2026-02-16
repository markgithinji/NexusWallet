package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.plus

@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val sendEthereumUseCase: SendEthereumUseCase,
    private val validateAddressUseCase: ValidateAddressUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) : ViewModel() {

    data class SendUiState(
        val walletId: String = "",
        val fromAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val feeEstimate: FeeEstimate? = null,
        val balance: BigDecimal = BigDecimal.ZERO,
        val isValid: Boolean = false,
        val validationError: String? = null,
        val network: String = "",
        val step: String = ""
    )

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class NoteChanged(val note: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object ClearError : SendEvent()
    }

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            val ethereumCoin = wallet.ethereum
            if (ethereumCoin == null) {
                _uiState.update { it.copy(error = "Ethereum not enabled", isLoading = false) }
                return@launch
            }

            val walletBalance = walletRepository.getWalletBalance(walletId)

            val ethBalance = if (walletBalance?.ethereum != null) {
                walletBalance.ethereum.eth.toBigDecimalOrNull() ?: BigDecimal.ZERO
            } else {
                val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
                    ethereumCoin.address,
                    ethereumCoin.network
                )
                when (balanceResult) {
                    is Result.Success -> balanceResult.data
                    else -> BigDecimal.ZERO
                }
            }

            val feeEstimateResult = getFeeEstimateUseCase(FeeLevel.NORMAL)
            val feeEstimate = when (feeEstimateResult) {
                is Result.Success -> feeEstimateResult.data
                else -> null
            }

            _uiState.update {
                it.copy(
                    walletId = walletId,
                    fromAddress = ethereumCoin.address,
                    balance = ethBalance,
                    feeEstimate = feeEstimate,
                    network = ethereumCoin.network.name,
                    isLoading = false
                )
            }

            validateInputs()
        }
    }

    fun onEvent(event: SendEvent) {
        viewModelScope.launch {
            when (event) {
                is SendEvent.ToAddressChanged -> {
                    _uiState.update { it.copy(toAddress = event.address) }
                    validateInputs()
                }
                is SendEvent.AmountChanged -> {
                    val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    _uiState.update { it.copy(amount = event.amount, amountValue = amountValue) }
                    validateInputs()
                }
                is SendEvent.NoteChanged -> _uiState.update { it.copy(note = event.note) }
                is SendEvent.FeeLevelChanged -> {
                    _uiState.update { it.copy(feeLevel = event.feeLevel) }
                    updateFeeEstimate()
                }
                SendEvent.Validate -> validateInputs()
                SendEvent.ClearError -> _uiState.update { it.copy(error = null, validationError = null) }
            }
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.walletId.isEmpty()) {
                _uiState.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            if (!validateInputs()) return@launch

            _uiState.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendEthereumUseCase(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel,
                note = state.note.takeIf { it.isNotEmpty() }
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _uiState.update { it.copy(isLoading = false, step = "Sent!") }
                        onSuccess(sendResult.txHash)
                    } else {
                        _uiState.update { it.copy(
                            isLoading = false,
                            error = sendResult.error ?: "Send failed"
                        ) }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = result.message
                    ) }
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun validateInputs(): Boolean {
        val state = _uiState.value
        val toAddress = state.toAddress
        val amount = state.amountValue

        if (toAddress.isEmpty() || amount == BigDecimal.ZERO) {
            _uiState.update {
                it.copy(
                    isValid = false,
                    validationError = if (toAddress.isEmpty()) "Enter address" else "Enter amount"
                )
            }
            return false
        }

        if (!validateAddressUseCase(toAddress)) {
            _uiState.update { it.copy(isValid = false, validationError = "Invalid address") }
            return false
        }

        if (toAddress == state.fromAddress) {
            _uiState.update { it.copy(isValid = false, validationError = "Cannot send to yourself") }
            return false
        }

        val feeEstimateResult = getFeeEstimateUseCase(state.feeLevel)
        if (feeEstimateResult is Result.Success) {
            val feeEstimate = feeEstimateResult.data
            val totalRequired = amount + BigDecimal(feeEstimate.totalFeeDecimal)

            if (totalRequired > state.balance) {
                _uiState.update {
                    it.copy(
                        isValid = false,
                        validationError = "Insufficient balance (including fees)",
                        feeEstimate = feeEstimate
                    )
                }
                return false
            }

            _uiState.update { it.copy(isValid = true, validationError = null, feeEstimate = feeEstimate) }
            return true
        }

        return false
    }

    private suspend fun updateFeeEstimate() {
        val feeEstimateResult = getFeeEstimateUseCase(_uiState.value.feeLevel)
        if (feeEstimateResult is Result.Success) {
            _uiState.update { it.copy(feeEstimate = feeEstimateResult.data) }
            validateInputs()
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null, validationError = null) }
}