package com.example.nexuswallet.feature.coin.solana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SolanaSendViewModel @Inject constructor(
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state

    fun init(walletId: String) {
        viewModelScope.launch {
            val wallet = walletRepository.getWallet(walletId) as? SolanaWallet
            wallet?.let {
                _state.value = SendState(
                    wallet = it,
                    walletAddress = it.address
                )
            }
        }
    }

    fun updateAddress(address: String) {
        _state.value = _state.value.copy(toAddress = address)
    }

    fun updateAmount(amount: String) {
        val amountValue = try { BigDecimal(amount) } catch (e: Exception) { BigDecimal.ZERO }
        _state.value = _state.value.copy(
            amount = amount,
            amountValue = amountValue
        )
    }

    fun requestAirdrop() {
        viewModelScope.launch {
            val wallet = _state.value.wallet ?: return@launch
            solanaBlockchainRepository.requestAirdrop(wallet.address)
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val wallet = _state.value.wallet ?: return@launch
            val toAddress = _state.value.toAddress
            val amount = _state.value.amountValue

            if (toAddress.isBlank() || amount <= BigDecimal.ZERO) {
                _state.value = _state.value.copy(error = "Please enter valid address and amount")
                return@launch
            }

            try {
                _state.value = _state.value.copy(isLoading = true)

                // 1. Create transaction
                val transaction = solanaTransactionRepository.createSendTransaction(
                    walletId = wallet.id,
                    toAddress = toAddress,
                    amount = amount
                ).getOrThrow()

                // 2. Sign transaction
                solanaTransactionRepository.signTransaction(transaction.id).getOrThrow()

                // 3. Broadcast transaction
                val result = solanaTransactionRepository.broadcastTransaction(transaction.id).getOrThrow()

                if (result.success) {
                    onSuccess(result.hash ?: "unknown")
                } else {
                    _state.value = _state.value.copy(error = result.error)
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

data class SendState(
    val wallet: SolanaWallet? = null,
    val walletAddress: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = false,
    val error: String? = null
)