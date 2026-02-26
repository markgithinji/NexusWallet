package com.example.nexuswallet.feature.coin.ethereum

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
class EthereumSendViewModel @Inject constructor(
    private val getEthereumWalletUseCase: GetEthereumWalletUseCase,
    private val sendEthereumUseCase: SendEthereumUseCase,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val validateEthereumSendUseCase: ValidateEthereumSendUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EthSendUiState())
    val uiState: StateFlow<EthSendUiState> = _uiState.asStateFlow()

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
                        balanceFormatted = "${balance.setScale(4, RoundingMode.HALF_UP)} ETH",
                        isLoading = false
                    )
                }
                validateInputs()
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
        val feeEstimateResult = getFeeEstimateUseCase(_uiState.value.feeLevel)
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
        val validationResult = validateEthereumSendUseCase(
            toAddress = state.toAddress,
            amountValue = state.amountValue,
            fromAddress = state.fromAddress,
            balance = state.balance,
            feeLevel = state.feeLevel
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