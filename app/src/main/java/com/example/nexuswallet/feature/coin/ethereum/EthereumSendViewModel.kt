package com.example.nexuswallet.feature.coin.ethereum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.plus
@HiltViewModel
class EthereumSendViewModel @Inject constructor(
    private val getEthereumWalletUseCase: GetEthereumWalletUseCase,
    private val sendEthereumUseCase: SendEthereumUseCase,
    private val validateAddressUseCase: ValidateAddressUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) : ViewModel() {

    data class SendUiState(
        val walletId: String = "",
        val walletName: String = "",
        val fromAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val note: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val feeEstimate: EthereumFeeEstimate? = null,
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

            when (val walletResult = getEthereumWalletUseCase(walletId)) {
                is Result.Success -> {
                    val walletInfo = walletResult.data
                    _uiState.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            fromAddress = walletInfo.walletAddress,
                            network = walletInfo.network.displayName
                        )
                    }
                    loadBalance(walletInfo.walletAddress, walletInfo.network)
                    loadFeeEstimate()
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            error = walletResult.message,
                            isLoading = false
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private suspend fun loadBalance(address: String, network: EthereumNetwork) {
        val balanceResult = ethereumBlockchainRepository.getEthereumBalance(address, network)
        when (balanceResult) {
            is Result.Success -> {
                val balance = balanceResult.data
                _uiState.update {
                    it.copy(
                        balance = balance,
                        isLoading = false
                    )
                }
            }
            is Result.Error -> {
                _uiState.update {
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
        val feeEstimateResult = getFeeEstimateUseCase(FeeLevel.NORMAL)
        when (feeEstimateResult) {
            is Result.Success -> {
                _uiState.update { it.copy(feeEstimate = feeEstimateResult.data) }
            }
            is Result.Error -> {
                _uiState.update { it.copy(error = "Failed to load fee: ${feeEstimateResult.message}") }
            }
            Result.Loading -> {}
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
            val totalRequired = amount + BigDecimal(feeEstimate.totalFeeEth)

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