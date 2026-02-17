package com.example.nexuswallet.feature.coin.usdc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCase
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class USDCSendViewModel @Inject constructor(
    private val sendUSDCUseCase: SendUSDCUseCase,
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val getUSDCFeeEstimateUseCase: GetUSDCFeeEstimateUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class USDCSendState(
        val walletId: String = "",
        val walletName: String = "",
        val fromAddress: String = "",
        val network: EthereumNetwork = EthereumNetwork.Sepolia,
        val contractAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: USDCFeeEstimate? = null,
        val usdcBalance: String = "0",
        val usdcBalanceDecimal: BigDecimal = BigDecimal.ZERO,
        val ethBalance: String = "0",
        val ethBalanceDecimal: BigDecimal = BigDecimal.ZERO,
        val isValidAddress: Boolean = false,
        val hasSufficientBalance: Boolean = false,
        val hasSufficientGas: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val step: String = ""
    )

    private val _state = MutableStateFlow(USDCSendState())
    val state: StateFlow<USDCSendState> = _state.asStateFlow()

    sealed class SendEvent {
        data class ToAddressChanged(val address: String) : SendEvent()
        data class AmountChanged(val amount: String) : SendEvent()
        data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
        object Validate : SendEvent()
        object ClearError : SendEvent()
        object ClearInfo : SendEvent()
    }

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _state.update { it.copy(error = "Wallet not found", isLoading = false) }
                return@launch
            }

            val usdcCoin = wallet.usdc
            if (usdcCoin == null) {
                _state.update { it.copy(error = "USDC not enabled for this wallet", isLoading = false) }
                return@launch
            }

            // Get USDC balance
            val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
            val usdcBalance = when (usdcBalanceResult) {
                is Result.Success -> usdcBalanceResult.data
                is Result.Error -> {
                    _state.update { it.copy(error = usdcBalanceResult.message, isLoading = false) }
                    return@launch
                }
                Result.Loading -> return@launch
            }

            // Get ETH balance for gas
            val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
            val ethBalance = when (ethBalanceResult) {
                is Result.Success -> ethBalanceResult.data
                is Result.Error -> {
                    _state.update { it.copy(error = ethBalanceResult.message, isLoading = false) }
                    return@launch
                }
                Result.Loading -> return@launch
            }

            // Get initial fee estimate
            val feeResult = getUSDCFeeEstimateUseCase(FeeLevel.NORMAL, usdcCoin.network)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                else -> null
            }

            _state.update {
                it.copy(
                    walletId = wallet.id,
                    walletName = wallet.name,
                    fromAddress = usdcCoin.address,
                    network = usdcCoin.network,
                    contractAddress = usdcCoin.contractAddress,
                    usdcBalance = usdcBalance.amountDecimal,
                    usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    ethBalance = ethBalance.toPlainString(),
                    ethBalanceDecimal = ethBalance,
                    feeEstimate = feeEstimate,
                    isLoading = false
                )
            }

            validateForm()
        }
    }

    fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.ToAddressChanged -> {
                _state.update { it.copy(toAddress = event.address) }
                validateForm()
            }
            is SendEvent.AmountChanged -> {
                val amountValue = event.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _state.update {
                    it.copy(
                        amount = event.amount,
                        amountValue = amountValue
                    )
                }
                validateForm()
            }
            is SendEvent.FeeLevelChanged -> {
                _state.update { it.copy(feeLevel = event.feeLevel) }
                updateFeeEstimate()
            }
            SendEvent.Validate -> validateForm()
            SendEvent.ClearError -> clearError()
            SendEvent.ClearInfo -> clearInfo()
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            if (state.walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            if (!validateForm()) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending USDC...") }

            val result = sendUSDCUseCase(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel
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
                    usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
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
    }

    private fun validateForm(): Boolean {
        val currentState = _state.value

        // Validate address format (Ethereum address)
        val isValidAddress = currentState.toAddress.startsWith("0x") &&
                currentState.toAddress.length == 42

        // Check amount > 0
        val isAmountValid = currentState.amountValue > BigDecimal.ZERO

        // Check sufficient USDC balance
        val hasSufficientBalance = currentState.amountValue <= currentState.usdcBalanceDecimal &&
                isAmountValid

        // Check sufficient ETH for gas (using fee estimate if available)
        val requiredEth = if (currentState.feeEstimate != null) {
            BigDecimal(currentState.feeEstimate.totalFeeEth)
        } else {
            BigDecimal("0.0005") // fallback
        }
        val hasSufficientGas = currentState.ethBalanceDecimal >= requiredEth

        _state.update {
            it.copy(
                isValidAddress = isValidAddress,
                hasSufficientBalance = hasSufficientBalance,
                hasSufficientGas = hasSufficientGas
            )
        }

        return isValidAddress && hasSufficientBalance && hasSufficientGas
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

    fun debug() {
        _state.update {
            it.copy(info = "Debug: USDC at ${state.value.contractAddress} on ${state.value.network.displayName}")
        }
    }

    fun getTestnetUSDC() {
        _state.update { it.copy(info = "Get test USDC from: https://faucet.circle.com/") }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}