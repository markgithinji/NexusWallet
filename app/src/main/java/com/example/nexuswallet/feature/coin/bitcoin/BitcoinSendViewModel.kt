package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class BitcoinSendViewModel @Inject constructor(
    private val sendBitcoinUseCase: SendBitcoinUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val validateBitcoinAddressUseCase: ValidateBitcoinAddressUseCase,
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BitcoinSendState())
    val state: StateFlow<BitcoinSendState> = _state.asStateFlow()

    data class BitcoinSendState(
        val walletId: String = "",
        val walletName: String = "",
        val walletAddress: String = "",
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 BTC",
        val toAddress: String = "",
        val isAddressValid: Boolean = false,
        val addressError: String? = null,
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val network: BitcoinNetwork = BitcoinNetwork.TESTNET,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val feeEstimate: FeeEstimate? = null,
        val isLoading: Boolean = false,
        val step: String = "",
        val error: String? = null,
        val info: String? = null
    )

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Get wallet from repository
            val wallet = walletRepository.getWallet(walletId)

            // Check if Bitcoin is enabled for this wallet
            val bitcoinCoin = wallet?.bitcoin
            if (bitcoinCoin == null) {
                _state.update {
                    it.copy(
                        error = "Bitcoin not enabled for this wallet",
                        isLoading = false
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    walletId = wallet.id,
                    walletName = wallet.name,
                    walletAddress = bitcoinCoin.address,
                    network = bitcoinCoin.network
                )
            }

            // Load balance and fee estimate
            loadBalance(bitcoinCoin.address, bitcoinCoin.network)
            loadFeeEstimate()
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork) {
        val balanceResult = getBitcoinBalanceUseCase(address, network)
        when (balanceResult) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(8, RoundingMode.HALF_UP)} BTC",
                        isLoading = false
                    )
                }
                Log.d("BitcoinSendVM", "Balance loaded: $balance BTC")
            }
            is Result.Error -> {
                Log.e("BitcoinSendVM", "Error loading balance: ${balanceResult.message}")
                _state.update {
                    it.copy(
                        balance = BigDecimal.ZERO,
                        balanceFormatted = "0 BTC",
                        error = "Failed to load balance: ${balanceResult.message}",
                        isLoading = false
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val feeResult = getBitcoinFeeEstimateUseCase(_state.value.feeLevel)
        when (feeResult) {
            is Result.Success -> {
                _state.update { it.copy(feeEstimate = feeResult.data) }
            }
            is Result.Error -> {
                Log.e("BitcoinSendVM", "Error loading fee: ${feeResult.message}")
            }
            Result.Loading -> {}
        }
    }

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateAddress(address)
    }

    private fun validateAddress(address: String) {
        if (address.isNotEmpty()) {
            val isValid = validateBitcoinAddressUseCase(address, _state.value.network)
            _state.update { it.copy(
                isAddressValid = isValid,
                addressError = if (!isValid) "Invalid Bitcoin address for ${_state.value.network.name.lowercase()}" else null
            )}
        } else {
            _state.update { it.copy(
                isAddressValid = false,
                addressError = null
            )}
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update { it.copy(
            amount = amount,
            amountValue = amountValue
        )}
    }

    fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        viewModelScope.launch {
            loadFeeEstimate()
        }
    }

    fun getTestnetCoins() {
        _state.update { it.copy(
            info = "Get testnet BTC from: https://bitcoinfaucet.uo1.net"
        )}
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            val walletId = state.walletId
            if (walletId.isEmpty()) {
                _state.update { it.copy(error = "Wallet not loaded") }
                return@launch
            }

            val toAddress = state.toAddress
            val amount = state.amountValue

            if (!validateInputs(toAddress, amount)) {
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Sending...") }

            val result = sendBitcoinUseCase(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
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
                        _state.update { it.copy(
                            isLoading = false,
                            error = sendResult.error ?: "Send failed"
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

    private fun validateInputs(toAddress: String, amount: BigDecimal): Boolean {
        if (toAddress.isBlank()) {
            _state.update { it.copy(error = "Please enter a recipient address") }
            return false
        }

        val isValid = validateBitcoinAddressUseCase(toAddress, _state.value.network)
        if (!isValid) {
            _state.update { it.copy(error = "Invalid Bitcoin address for ${_state.value.network.name.lowercase()}") }
            return false
        }

        if (amount <= BigDecimal.ZERO) {
            _state.update { it.copy(error = "Amount must be greater than 0") }
            return false
        }

        if (toAddress == _state.value.walletAddress) {
            _state.update { it.copy(error = "Cannot send to yourself") }
            return false
        }

        val feeEstimate = _state.value.feeEstimate
        val feeBtc = if (feeEstimate != null) {
            BigDecimal(feeEstimate.totalFeeDecimal)
        } else {
            BigDecimal("0.00001")
        }

        val totalRequired = amount + feeBtc
        if (totalRequired > _state.value.balance) {
            _state.update { it.copy(error = "Insufficient balance (including fees)") }
            return false
        }

        return true
    }

    fun debug() {
        viewModelScope.launch {
            bitcoinBlockchainRepository.debugCheckUTXOsDirect(
                address = "my5mRT8cb5q9ScTyJDz46LtG7RNTfi1xF5",
                network = BitcoinNetwork.TESTNET
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    fun resetState() {
        _state.update {
            BitcoinSendState(
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