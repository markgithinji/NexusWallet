package com.example.nexuswallet.feature.coin.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
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
class BitcoinSendViewModel @Inject constructor(
    private val getBitcoinWalletUseCase: GetBitcoinWalletUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val sendBitcoinUseCase: SendBitcoinUseCase,
    private val validateBitcoinAddressUseCase: ValidateBitcoinAddressUseCase,
    private val validateBitcoinTransactionUseCase: ValidateBitcoinTransactionUseCase,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BtcSendUiState())
    val state: StateFlow<BtcSendUiState> = _state.asStateFlow()

    private var wallet: Wallet? = null
    private var bitcoinCoin: BitcoinCoin? = null

    fun init(walletId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Load full wallet from repository
            wallet = walletRepository.getWallet(walletId)
            bitcoinCoin = wallet?.bitcoin

            when (val result = getBitcoinWalletUseCase(walletId)) {
                is Result.Success -> {
                    val walletInfo = result.data
                    _state.update {
                        it.copy(
                            walletId = walletInfo.walletId,
                            walletName = walletInfo.walletName,
                            walletAddress = walletInfo.walletAddress,
                            network = walletInfo.network,
                            isLoading = false
                        )
                    }
                    // Load balance after we have the address
                    loadBalance(walletInfo.walletAddress, walletInfo.network)
                    // Load initial fee estimate
                    updateFeeLevel(FeeLevel.NORMAL)
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork) {
        when (val balanceResult = getBitcoinBalanceUseCase(address, network)) {
            is Result.Success -> {
                val balance = balanceResult.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(8, RoundingMode.HALF_UP)} BTC"
                    )
                }
            }

            is Result.Error -> {
                _state.update {
                    it.copy(
                        error = "Failed to load balance: ${balanceResult.message}"
                    )
                }
            }

            Result.Loading -> {}
        }
    }

    fun updateFeeLevel(feeLevel: FeeLevel) {
        _state.update { it.copy(feeLevel = feeLevel) }
        viewModelScope.launch {
            when (val feeResult = getBitcoinFeeEstimateUseCase(feeLevel)) {
                is Result.Success -> {
                    _state.update { it.copy(feeEstimate = feeResult.data) }
                }

                is Result.Error -> {
                    _state.update { it.copy(error = "Failed to load fee: ${feeResult.message}") }
                }

                Result.Loading -> {}
            }
        }
    }

    fun updateAddress(address: String) {
        _state.update { it.copy(toAddress = address) }
        validateAddress(address)
    }

    private fun validateAddress(address: String) {
        if (address.isNotEmpty()) {
            val isValid = validateBitcoinAddressUseCase(address, _state.value.network)
            _state.update {
                it.copy(
                    isAddressValid = isValid,
                    addressError = if (!isValid) "Invalid Bitcoin address for ${_state.value.network.name.lowercase()}" else null
                )
            }
        } else {
            _state.update {
                it.copy(
                    isAddressValid = false,
                    addressError = null
                )
            }
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = try {
            amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        _state.update {
            it.copy(
                amount = amount,
                amountValue = amountValue
            )
        }
    }

    fun send(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            val walletId = state.walletId
            val currentWallet = wallet
            val currentBitcoinCoin = bitcoinCoin

            if (walletId.isEmpty() || currentWallet == null || currentBitcoinCoin == null) {
                _state.update { it.copy(error = "Wallet not properly loaded") }
                return@launch
            }

            val toAddress = state.toAddress
            val amount = state.amountValue
            val balance = state.balance
            val feeEstimate = state.feeEstimate

            val validationResult = validateBitcoinTransactionUseCase(
                walletId = walletId,
                wallet = currentWallet,
                toAddress = toAddress,
                amount = amount,
                network = state.network,
                balance = balance,
                feeEstimate = feeEstimate
            )

            if (!validationResult.isValid) {
                _state.update { it.copy(error = validationResult.errorMessage) }
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
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = sendResult.error ?: "Send failed"
                            )
                        }
                    }
                }

                is Result.Error -> {
                    _state.update {
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

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }
}