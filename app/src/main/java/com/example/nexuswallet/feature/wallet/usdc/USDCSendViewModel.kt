package com.example.nexuswallet.feature.wallet.usdc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.ethereum.EthereumBlockchainRepository
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

@HiltViewModel
class USDCSendViewModel @Inject constructor(
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(USDCSendState())
    val state: StateFlow<USDCSendState> = _state

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val wallet = walletRepository.getWallet(walletId) as? USDCWallet
                if (wallet == null) {
                    _state.update { it.copy(
                        error = "Not a USDC wallet",
                        isLoading = false
                    ) }
                    return@launch
                }

                _state.update { it.copy(
                    wallet = wallet,
                    network = wallet.network,
                    contractAddress = wallet.contractAddress,
                    isLoading = false,
                    hasSufficientBalance = true,
                    hasSufficientGas = true
                ) }

            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to load wallet: ${e.message}",
                    isLoading = false
                ) }
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

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val wallet = state.value.wallet ?: throw IllegalStateException("Wallet not loaded")
                val toAddress = state.value.toAddress
                val amount = state.value.amountValue

                // Validate only address format
                if (!state.value.isValidAddress) {
                    throw IllegalArgumentException("Invalid Ethereum address")
                }

                // Send USDC
                val result = usdcTransactionRepository.completeUSDCTransfer(
                    walletId = wallet.id,
                    toAddress = toAddress,
                    amount = amount
                )

                if (result.isSuccess) {
                    val broadcastResult = result.getOrThrow()
                    if (broadcastResult.success) {
                        _state.update { it.copy(
                            isLoading = false,
                            info = "Transaction sent! Hash: ${broadcastResult.hash?.take(10)}..."
                        ) }
                        onSuccess(broadcastResult.hash ?: "")
                    } else {
                        throw Exception(broadcastResult.error ?: "Transaction failed")
                    }
                } else {
                    throw result.exceptionOrNull() ?: Exception("Transaction failed")
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Failed to send: ${e.message}"
                ) }
            }
        }
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