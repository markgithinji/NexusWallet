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
    private val validateSolanaSendUseCase: ValidateSolanaSendUseCase
) : ViewModel() {

    data class SolanaSendUIState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val network: SolanaNetwork = SolanaNetwork.DEVNET,
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 SOL",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: SolanaFeeEstimate? = null,
        val validationResult: ValidateSolanaSendUseCase.ValidationResult = ValidateSolanaSendUseCase.ValidationResult(
            isValid = false
        ),
        val isLoading: Boolean = false,
        val error: String? = null,
        val step: String = ""
    )

    private val _state = MutableStateFlow(SolanaSendUIState())
    val state: StateFlow<SolanaSendUIState> = _state.asStateFlow()

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = getSolanaWalletUseCase(walletId)) {
                is Result.Success -> {
                    val walletInfo = result.data
                    val network = SolanaNetwork.DEVNET

                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            walletAddress = walletInfo.walletAddress,
                            network = network
                        )
                    }
                    loadBalance(walletInfo.walletAddress, network)
                    loadFeeEstimate(network)
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
                validateInputs() // Re-validate after balance loads
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
                    validateInputs() // Re-validate after fee loads
                }

                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
                }

                Result.Loading -> {}
            }
        }
    }

    fun onEvent(event: SolanaSendEvent) {
        when (event) {
            is SolanaSendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                viewModelScope.launch {
                    validateInputs()
                }
            }

            is SolanaSendEvent.AmountChanged -> {
                val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update {
                    it.copy(
                        amount = event.amount,
                        amountValue = amountValue
                    )
                }
                viewModelScope.launch {
                    validateInputs()
                }
            }

            is SolanaSendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                viewModelScope.launch {
                    loadFeeEstimate(_state.value.network)
                }
            }

            SolanaSendEvent.Validate -> {
                viewModelScope.launch {
                    validateInputs()
                }
            }
            SolanaSendEvent.ClearError -> clearError()
        }
    }

    private fun validateInputs(): Boolean {
        val currentState = _state.value
        val validationResult = validateSolanaSendUseCase(
            toAddress = currentState.toAddress,
            amountValue = currentState.amountValue,
            walletAddress = currentState.walletAddress,
            balance = currentState.balance,
            feeEstimate = currentState.feeEstimate
        )

        _state.update { it.copy(validationResult = validationResult) }

        // Update error field for backward compatibility
        val firstError = validationResult.addressError
            ?: validationResult.amountError
            ?: validationResult.balanceError
            ?: validationResult.selfSendError

        if (firstError != null) {
            _state.update { it.copy(error = firstError) }
        } else if (validationResult.isValid) {
            _state.update { it.copy(error = null) }
        }

        return validationResult.isValid
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
        viewModelScope.launch {
            validateInputs()
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            if (state.walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
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
}