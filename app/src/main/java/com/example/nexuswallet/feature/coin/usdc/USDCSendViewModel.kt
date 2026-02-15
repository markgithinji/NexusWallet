package com.example.nexuswallet.feature.coin.usdc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCase
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class USDCSendViewModel @Inject constructor(
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val sendUSDCUseCase: SendUSDCUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class USDCSendState(
        val walletId: String = "",
        val walletName: String = "",
        val fromAddress: String = "",
        val network: EthereumNetwork = EthereumNetwork.SEPOLIA,
        val contractAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val usdcBalance: String = "0",
        val usdcBalanceDecimal: BigDecimal = BigDecimal.ZERO,
        val ethBalance: String = "0",
        val ethBalanceDecimal: BigDecimal = BigDecimal.ZERO,
        val estimatedGas: String = "0.0005",
        val isValidAddress: Boolean = false,
        val hasSufficientBalance: Boolean = false,
        val hasSufficientGas: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val info: String? = null
    )

    private val _state = MutableStateFlow(USDCSendState())
    val state: StateFlow<USDCSendState> = _state.asStateFlow()

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                _state.update { it.copy(
                    error = "Wallet not found",
                    isLoading = false
                ) }
                return@launch
            }

            // Check if USDC is enabled for this wallet
            val usdcCoin = wallet.usdc
            if (usdcCoin == null) {
                _state.update { it.copy(
                    error = "USDC not enabled for this wallet",
                    isLoading = false
                ) }
                return@launch
            }

            // Get USDC balance
            val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
            val usdcBalance = when (usdcBalanceResult) {
                is Result.Success -> usdcBalanceResult.data
                is Result.Error -> {
                    _state.update { it.copy(
                        error = usdcBalanceResult.message,
                        isLoading = false
                    ) }
                    return@launch
                }
                Result.Loading -> return@launch
            }

            // Get ETH balance for gas
            val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
            val ethBalance = when (ethBalanceResult) {
                is Result.Success -> ethBalanceResult.data
                is Result.Error -> {
                    _state.update { it.copy(
                        error = ethBalanceResult.message,
                        isLoading = false
                    ) }
                    return@launch
                }
                Result.Loading -> return@launch
            }

            _state.update { it.copy(
                walletId = wallet.id,
                walletName = wallet.name,
                fromAddress = usdcCoin.address,
                network = usdcCoin.network,
                contractAddress = usdcCoin.contractAddress,
                usdcBalance = usdcBalance.amountDecimal,
                usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                ethBalance = ethBalance.toPlainString(),
                ethBalanceDecimal = ethBalance,
                isLoading = false
            ) }

            validateForm()
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val toAddress = state.value.toAddress
            val amount = state.value.amountValue

            // Validate address format
            if (!state.value.isValidAddress) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Invalid Ethereum address"
                ) }
                return@launch
            }

            // Validate amount
            if (amount <= BigDecimal.ZERO) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Amount must be greater than 0"
                ) }
                return@launch
            }

            // Check sufficient balance
            if (amount > state.value.usdcBalanceDecimal) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Insufficient USDC balance"
                ) }
                return@launch
            }

            // Check sufficient ETH for gas (assuming ~0.0005 ETH) TODO: Get real values
            val estimatedGasEth = BigDecimal("0.0005")
            if (state.value.ethBalanceDecimal < estimatedGasEth) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Insufficient ETH for gas. Need at least 0.0005 ETH"
                ) }
                return@launch
            }

            val result = sendUSDCUseCase(
                walletId = state.value.walletId,
                toAddress = toAddress,
                amount = amount
            )

            when (result) {
                is Result.Success -> {
                    val broadcastResult = result.data
                    if (broadcastResult.success) {
                        _state.update { it.copy(
                            isLoading = false,
                            info = "Transaction sent! Hash: ${broadcastResult.hash?.take(10)}..."
                        ) }
                        onSuccess(broadcastResult.hash ?: "")

                        // Refresh balances after successful send
                        refreshBalances()
                    } else {
                        _state.update { it.copy(
                            isLoading = false,
                            error = broadcastResult.error ?: "Transaction failed"
                        ) }
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(
                        isLoading = false,
                        error = result.message
                    ) }
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun refreshBalances() {
        // Refresh USDC balance
        val usdcBalanceResult = getUSDCBalanceUseCase(state.value.walletId)
        if (usdcBalanceResult is Result.Success) {
            val usdcBalance = usdcBalanceResult.data
            _state.update { it.copy(
                usdcBalance = usdcBalance.amountDecimal,
                usdcBalanceDecimal = usdcBalance.amountDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
            ) }
        }

        // Refresh ETH balance
        val ethBalanceResult = getETHBalanceForGasUseCase(state.value.walletId)
        if (ethBalanceResult is Result.Success) {
            val ethBalance = ethBalanceResult.data
            _state.update { it.copy(
                ethBalance = ethBalance.toPlainString(),
                ethBalanceDecimal = ethBalance
            ) }
        }
    }

    private fun validateForm() {
        val currentState = _state.value

        // Validate address format (Ethereum address)
        val isValidAddress = currentState.toAddress.startsWith("0x") &&
                currentState.toAddress.length == 42

        // Check sufficient balance
        val hasSufficientBalance = currentState.amountValue <= currentState.usdcBalanceDecimal &&
                currentState.amountValue > BigDecimal.ZERO

        // Check sufficient ETH for gas (assuming ~0.0005 ETH)
        val estimatedGasEth = BigDecimal("0.0005")
        val hasSufficientGas = currentState.ethBalanceDecimal >= estimatedGasEth

        _state.update { it.copy(
            isValidAddress = isValidAddress,
            hasSufficientBalance = hasSufficientBalance,
            hasSufficientGas = hasSufficientGas,
            estimatedGas = estimatedGasEth.toPlainString()
        ) }
    }

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateForm()
    }

    fun updateAmount(amount: String) {
        _state.update { it.copy(amount = amount) }

        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        _state.update { it.copy(amountValue = amountValue) }
        validateForm()
    }

    fun debug() {
        _state.update { it.copy(info = "Debug: USDC at ${state.value.contractAddress} on ${state.value.network}") }
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