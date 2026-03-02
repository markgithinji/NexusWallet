package com.example.nexuswallet.feature.coin.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class BitcoinReviewViewModel @Inject constructor(
    private val prepareBitcoinTransactionUseCase: PrepareBitcoinTransactionUseCase,
    private val sendBitcoinUseCase: SendBitcoinUseCase,
    private val getBitcoinWalletUseCase: GetBitcoinWalletUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val logger: Logger
) : ViewModel() {

    data class BitcoinReviewUiState(
        val walletId: String = "",
        val walletName: String = "",
        val fromAddress: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val amountValue: BigDecimal = BigDecimal.ZERO,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val network: BitcoinNetwork = BitcoinNetwork.Testnet,
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceFormatted: String = "0 BTC",
        val feeEstimate: BitcoinFeeEstimate? = null,
        val preparedTransaction: PreparedBitcoinTransaction? = null,
        val transactionPrepared: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val step: String = ""
    )

    private val _state = MutableStateFlow(BitcoinReviewUiState())
    val state: StateFlow<BitcoinReviewUiState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<BitcoinReviewEffect>()
    val effect: SharedFlow<BitcoinReviewEffect> = _effect.asSharedFlow()

    fun initialize(
        walletId: String,
        toAddress: String,
        amount: String,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ) {
        viewModelScope.launch {
            val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO

            _state.update {
                it.copy(
                    walletId = walletId,
                    toAddress = toAddress,
                    amount = amount,
                    amountValue = amountValue,
                    feeLevel = feeLevel,
                    network = network,
                    isLoading = true
                )
            }

            // Load wallet info
            when (val walletResult = getBitcoinWalletUseCase(walletId, network)) {
                is Result.Success -> {
                    val walletInfo = walletResult.data
                    _state.update {
                        it.copy(
                            fromAddress = walletInfo.walletAddress,
                            walletName = walletInfo.walletName
                        )
                    }

                    // Load balance
                    loadBalance(walletInfo.walletAddress, network)
                }
                is Result.Error -> {
                    _state.update { it.copy(error = walletResult.message, isLoading = false) }
                }
                else -> {}
            }
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork) {
        when (val result = getBitcoinBalanceUseCase(address, network)) {
            is Result.Success -> {
                val balance = result.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${balance.setScale(8, RoundingMode.HALF_UP)} BTC"
                    )
                }
                loadFeeEstimate()
            }
            is Result.Error -> {
                _state.update { it.copy(error = "Failed to load balance: ${result.message}", isLoading = false) }
            }
            else -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val state = _state.value
        when (val result = getBitcoinFeeEstimateUseCase(state.feeLevel)) {
            is Result.Success -> {
                _state.update {
                    it.copy(
                        feeEstimate = result.data,
                        isLoading = false
                    )
                }
                logger.d("BitcoinReviewVM", "Fee estimate loaded: ${result.data.totalFeeBtc} BTC")
            }
            is Result.Error -> {
                _state.update { it.copy(error = "Failed to load fee: ${result.message}", isLoading = false) }
            }
            else -> {}
        }
    }

    fun prepareTransaction() {
        viewModelScope.launch {
            val state = _state.value

            _state.update { it.copy(isLoading = true, step = "Preparing...") }

            val result = prepareBitcoinTransactionUseCase(
                walletId = state.walletId,
                toAddress = state.toAddress,
                amount = state.amountValue,
                feeLevel = state.feeLevel,
                network = state.network
            )

            when (result) {
                is Result.Success -> {
                    val preparedTx = result.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            transactionPrepared = true,
                            preparedTransaction = preparedTx,
                            step = "Ready"
                        )
                    }
                    _effect.emit(BitcoinReviewEffect.TransactionPrepared(preparedTx.transactionId))
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                    _effect.emit(BitcoinReviewEffect.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun sendTransaction(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            val preparedTx = state.preparedTransaction

            if (preparedTx == null || !state.transactionPrepared) {
                _effect.emit(BitcoinReviewEffect.ShowError("Transaction not prepared"))
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Broadcasting...") }

            val result = sendBitcoinUseCase(
                preparedTransaction = preparedTx,
                walletId = state.walletId,
                network = state.network
            )

            when (result) {
                is Result.Success -> {
                    val sendResult = result.data
                    if (sendResult.success) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                step = "Sent!",
                                transactionPrepared = false,
                                preparedTransaction = null
                            )
                        }
                        _effect.emit(BitcoinReviewEffect.TransactionSent(sendResult.txHash))
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
                else -> {}
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}