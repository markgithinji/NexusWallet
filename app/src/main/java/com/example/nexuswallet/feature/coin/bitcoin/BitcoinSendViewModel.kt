package com.example.nexuswallet.feature.coin.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class BitcoinSendViewModel @Inject constructor(
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BitcoinSendState())
    val state: StateFlow<BitcoinSendState> = _state

    fun init(walletId: String) {
        viewModelScope.launch {
            val wallet = walletRepository.getWallet(walletId) as? BitcoinWallet
            wallet?.let {
                _state.value = BitcoinSendState(
                    wallet = it,
                    walletAddress = it.address,
                    network = it.network
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

    fun getTestnetCoins() {
        viewModelScope.launch {
            // This would open a browser to a testnet faucet
            val wallet = _state.value.wallet ?: return@launch
            _state.value = _state.value.copy(
                info = "Get testnet BTC from: https://bitcoinfaucet.uo1.net"
            )
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
                val transaction = bitcoinTransactionRepository.createSendTransaction(
                    walletId = wallet.id,
                    toAddress = toAddress,
                    amount = amount
                ).getOrThrow()

                // 2. Sign transaction
                bitcoinTransactionRepository.signTransaction(transaction.id).getOrThrow()

                // 3. Broadcast transaction
                val result = bitcoinTransactionRepository.broadcastTransaction(transaction.id).getOrThrow()

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
    fun debug(){
        viewModelScope.launch {
            bitcoinBlockchainRepository.debugCheckUTXOsDirect(
                address = "my5mRT8cb5q9ScTyJDz46LtG7RNTfi1xF5",
                network = BitcoinNetwork.TESTNET
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearInfo() {
        _state.value = _state.value.copy(info = null)
    }
}

data class BitcoinSendState(
    val wallet: BitcoinWallet? = null,
    val walletAddress: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val network: BitcoinNetwork = BitcoinNetwork.TESTNET,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)