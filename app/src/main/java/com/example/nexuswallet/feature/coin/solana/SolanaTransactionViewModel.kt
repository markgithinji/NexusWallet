package com.example.nexuswallet.feature.coin.solana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val validateSolanaAddressUseCase: ValidateSolanaAddressUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    data class SendState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 SOL",
        val toAddress: String = "",
        val isAddressValid: Boolean = false,
        val addressError: String? = null,
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: SolanaFeeEstimate? = null,
        val isLoading: Boolean = false,
        val step: String = "",
        val error: String? = null
    )

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object ClearError : SendEvent()
    }

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = getSolanaWalletUseCase(walletId)) {
                is Result.Success -> {
                    val walletInfo = result.data
                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            walletAddress = walletInfo.walletAddress
                        )
                    }
                    loadBalance(walletInfo.walletAddress)
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private suspend fun loadBalance(address: String) {
        when (val balanceResult = getSolanaBalanceUseCase(address)) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(4, RoundingMode.HALF_UP)} SOL",
                        isLoading = false
                    )
                }
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

    private suspend fun loadFeeEstimate() {
        when (val feeResult = getSolanaFeeEstimateUseCase(_state.value.feeLevel)) {
            is Result.Success -> {
                _state.update { it.copy(feeEstimate = feeResult.data) }
            }

            is Result.Error -> {
                _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
            }

            Result.Loading -> {}
        }
    }

    fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                validateAddress(event.address)
                validateInputs()
            }

            is SendEvent.AmountChanged -> {
                val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update {
                    it.copy(
                        amount = event.amount,
                        amountValue = amountValue
                    )
                }
                validateInputs()
            }

            is SendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                viewModelScope.launch {
                    loadFeeEstimate()
                    validateInputs()
                }
            }

            SendEvent.Validate -> validateInputs()
            SendEvent.ClearError -> clearError()
        }
    }

    private fun validateInputs(): Boolean {
        val currentState = _state.value
        val toAddress = currentState.toAddress
        val amount = currentState.amountValue

        var errorMessage: String? = null
        var isValid = true

        if (toAddress.isBlank()) {
            errorMessage = "Please enter a recipient address"
            isValid = false
        } else if (!currentState.isAddressValid) {
            errorMessage = "Invalid Solana address format"
            isValid = false
        } else if (amount <= BigDecimal.ZERO) {
            errorMessage = "Amount must be greater than 0"
            isValid = false
        } else if (toAddress == currentState.walletAddress) {
            errorMessage = "Cannot send to yourself"
            isValid = false
        } else {
            val fee =
                currentState.feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
            val totalRequired = amount + fee
            if (totalRequired > currentState.balance) {
                errorMessage = "Insufficient balance (including fees)"
                isValid = false
            }
        }

        _state.update { it.copy(error = errorMessage) }
        return isValid
    }

    private fun validateAddress(address: String) {
        if (address.isNotEmpty()) {
            when (val validationResult = validateSolanaAddressUseCase(address)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            isAddressValid = validationResult.data,
                            addressError = if (!validationResult.data) "Invalid Solana address" else null
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isAddressValid = false,
                            addressError = "Address validation failed"
                        )
                    }
                }

                Result.Loading -> {}
            }
        } else {
            _state.update {
                it.copy(
                    isAddressValid = false,
                    addressError = null
                )
            }
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update {
            it.copy(
                amount = amount,
                amountValue = amountValue
            )
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            if (state.walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            val toAddress = state.toAddress
            val amount = state.amountValue

            if (!validateInputs()) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendSolanaUseCase(
                walletId = state.walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = state.feeLevel,
                note = null
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update { it.copy(isLoading = false, step = "Sent!") }
                        onSuccess(sendResult.txHash)
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed",
                                step = ""
                            )
                        }
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
            SendState(
                walletId = _state.value.walletId,
                walletName = _state.value.walletName,
                walletAddress = _state.value.walletAddress,
                balance = _state.value.balance,
                balanceFormatted = _state.value.balanceFormatted
            )
        }
    }
}