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

@HiltViewModel
class USDCSendViewModel @Inject constructor(
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val sendUSDCUseCase: SendUSDCUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(USDCSendState())
    val state: StateFlow<USDCSendState> = _state

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val wallet = walletRepository.getWallet(walletId) as? USDCWallet
            if (wallet == null) {
                _state.update { it.copy(
                    error = "Not a USDC wallet",
                    isLoading = false
                ) }
                return@launch
            }

            // Get USDC balance
            val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
            if (usdcBalanceResult !is Result.Success) {
                _state.update { it.copy(
                    error = (usdcBalanceResult as Result.Error).message,
                    isLoading = false
                ) }
                return@launch
            }
            val usdcBalance = usdcBalanceResult.data

            // Get ETH balance
            val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
            if (ethBalanceResult !is Result.Success) {
                _state.update { it.copy(
                    error = (ethBalanceResult as Result.Error).message,
                    isLoading = false
                ) }
                return@launch
            }
            val ethBalance = ethBalanceResult.data

            _state.update { it.copy(
                wallet = wallet,
                network = wallet.network,
                contractAddress = wallet.contractAddress,
                usdcBalance = usdcBalance.balanceDecimal,
                usdcBalanceDecimal = usdcBalance.balanceDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO,
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

            // Validate only address format
            if (!state.value.isValidAddress) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Invalid Ethereum address"
                ) }
                return@launch
            }

            val wallet = state.value.wallet ?: run {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Wallet not loaded"
                ) }
                return@launch
            }

            val result = sendUSDCUseCase(
                walletId = wallet.id,
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
                Result.Loading -> {
                    // Already loading
                }
            }
        }
    }

    private fun validateForm() {
        val currentState = _state.value

        // Only validate address format
        val isValidAddress = currentState.toAddress.startsWith("0x") &&
                currentState.toAddress.length == 42

        _state.update { it.copy(
            isValidAddress = isValidAddress,
            hasSufficientBalance = true,
            hasSufficientGas = true,
            estimatedGas = "0.0005"
        ) }
    }

    fun updateAddress(address: String) {
        viewModelScope.launch {
            _state.update { it.copy(toAddress = address) }
            validateForm()
        }
    }

    fun updateAmount(amount: String) {
        viewModelScope.launch {
            _state.update { it.copy(amount = amount) }

            val amountValue = try {
                amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            } catch (e: Exception) {
                BigDecimal.ZERO
            }

            _state.update { it.copy(amountValue = amountValue) }
            validateForm()
        }
    }

    fun debug() {
        _state.update { it.copy(info = "Debug info: USDC contract at ${state.value.contractAddress}") }
    }

    fun getTestnetUSDC() {
        _state.update { it.copy(info = "Use a Sepolia USDC faucet to get test tokens") }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

data class USDCSendState(
    val wallet: USDCWallet? = null,
    val network: EthereumNetwork = EthereumNetwork.SEPOLIA,
    val contractAddress: String? = null,
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val usdcBalance: String = "0",
    val usdcBalanceDecimal: BigDecimal = BigDecimal.ZERO,
    val ethBalance: String = "0",
    val ethBalanceDecimal: BigDecimal = BigDecimal.ZERO,
    val estimatedGas: String = "0",
    val isValidAddress: Boolean = false,
    val hasSufficientBalance: Boolean = false,
    val hasSufficientGas: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)