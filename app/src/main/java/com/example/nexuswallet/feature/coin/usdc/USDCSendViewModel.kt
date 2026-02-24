package com.example.nexuswallet.feature.coin.usdc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCWalletUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import com.example.nexuswallet.feature.coin.usdc.domain.ValidateUSDCFormUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class USDCSendViewModel @Inject constructor(
    private val getUSDCWalletUseCase: GetUSDCWalletUseCase,
    private val sendUSDCUseCase: SendUSDCUseCase,
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val getUSDCFeeEstimateUseCase: GetUSDCFeeEstimateUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val validateUSDCFormUseCase: ValidateUSDCFormUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(USDCSendState())
    val state: StateFlow<USDCSendState> = _state.asStateFlow()

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val walletResult = getUSDCWalletUseCase(walletId)) {
                is Result.Success -> {
                    val walletInfo = walletResult.data
                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            fromAddress = walletInfo.walletAddress,
                            network = walletInfo.network,
                            contractAddress = walletInfo.contractAddress
                        )
                    }

                    loadBalances(walletId)
                    updateFeeEstimate()
                }

                is Result.Error -> {
                    _state.update {
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

    private suspend fun loadBalances(walletId: String) {
        // Load USDC balance
        val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
        when (usdcBalanceResult) {
            is Result.Success -> {
                val usdcBalance = usdcBalanceResult.data
                _state.update {
                    it.copy(
                        usdcBalance = usdcBalance.amountDecimal,
                        usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull()
                            ?: BigDecimal.ZERO
                    )
                }
            }

            is Result.Error -> {
                _state.update { it.copy(error = usdcBalanceResult.message) }
                return
            }

            Result.Loading -> {}
        }

        // Load ETH balance for gas
        val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
        when (ethBalanceResult) {
            is Result.Success -> {
                val ethBalance = ethBalanceResult.data
                _state.update {
                    it.copy(
                        ethBalance = ethBalance.toPlainString(),
                        ethBalanceDecimal = ethBalance
                    )
                }
                validateForm()
                _state.update { it.copy(isLoading = false) }
            }

            is Result.Error -> {
                _state.update {
                    it.copy(
                        error = ethBalanceResult.message,
                        isLoading = false
                    )
                }
            }

            Result.Loading -> {}
        }
    }

    fun onEvent(event: USDCSendEvent) {
        when (event) {
            is USDCSendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                validateForm()
            }

            is USDCSendEvent.AmountChanged -> {
                val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update {
                    it.copy(
                        amount = event.amount,
                        amountValue = amountValue
                    )
                }
                validateForm()
            }

            is USDCSendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                updateFeeEstimate()
            }

            USDCSendEvent.Validate -> validateForm()
            USDCSendEvent.ClearError -> clearError()
            USDCSendEvent.ClearInfo -> clearInfo()
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value

            if (currentState.walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            if (!currentState.validationResult.isValid) {
                validateForm() // Re-validate to show errors
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending USDC...") }

            val result = sendUSDCUseCase(
                walletId = currentState.walletId,
                toAddress = currentState.toAddress,
                amount = currentState.amountValue,
                feeLevel = currentState.feeLevel
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                step = "Sent!",
                                info = "Transaction sent! Hash: ${sendResult.txHash.take(10)}..."
                            )
                        }
                        onSuccess(sendResult.txHash)
                        refreshBalances()
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

    private suspend fun refreshBalances() {
        // Refresh USDC balance
        val usdcBalanceResult = getUSDCBalanceUseCase(_state.value.walletId)
        if (usdcBalanceResult is Result.Success) {
            val usdcBalance = usdcBalanceResult.data
            _state.update {
                it.copy(
                    usdcBalance = usdcBalance.amountDecimal,
                    usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull()
                        ?: BigDecimal.ZERO
                )
            }
        }

        // Refresh ETH balance
        val ethBalanceResult = getETHBalanceForGasUseCase(_state.value.walletId)
        if (ethBalanceResult is Result.Success) {
            val ethBalance = ethBalanceResult.data
            _state.update {
                it.copy(
                    ethBalance = ethBalance.toPlainString(),
                    ethBalanceDecimal = ethBalance
                )
            }
        }
        validateForm()
    }

    private fun validateForm() {
        val currentState = _state.value
        val validationResult = validateUSDCFormUseCase(
            toAddress = currentState.toAddress,
            amountValue = currentState.amountValue,
            usdcBalanceDecimal = currentState.usdcBalanceDecimal,
            ethBalanceDecimal = currentState.ethBalanceDecimal,
            feeEstimate = currentState.feeEstimate
        )

        _state.update { it.copy(validationResult = validationResult) }
    }

    private fun updateFeeEstimate() {
        viewModelScope.launch {
            val currentState = _state.value
            val feeResult = getUSDCFeeEstimateUseCase(currentState.feeLevel, currentState.network)
            if (feeResult is Result.Success) {
                _state.update { it.copy(feeEstimate = feeResult.data) }
                validateForm()
            }
        }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}