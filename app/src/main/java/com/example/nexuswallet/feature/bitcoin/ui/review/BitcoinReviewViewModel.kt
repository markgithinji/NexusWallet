package com.example.nexuswallet.feature.bitcoin.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinBalanceUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinFeeEstimateUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinWalletUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.PrepareBitcoinTransactionUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SendBitcoinUseCase
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_INPUT_COUNT
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_OUTPUT_COUNT
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.toSatoshis
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.util.ExplorerUrlHelper
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
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) : ViewModel() {

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
                    isLoading = true,
                    isFeeLoading = true
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
                _state.update {
                    it.copy(
                        error = "Failed to load balance: ${result.message}",
                        isLoading = false
                    )
                }
            }

            else -> {}
        }
    }

    private suspend fun loadFeeEstimate() {
        val state = _state.value

        _state.update { it.copy(isFeeLoading = true) }

        // Fetch UTXOs to determine input count dynamically
        val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(state.fromAddress, state.network)
        val inputCount = if (utxosResult is Result.Success) {
            val selected = bitcoinBlockchainRepository.selectUtxos(
                utxosResult.data,
                state.amountValue.toSatoshis()
            )
            if (selected.isNotEmpty()) selected.size else DEFAULT_INPUT_COUNT
        } else {
            DEFAULT_INPUT_COUNT
        }

        when (val result = getBitcoinFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            inputCount = inputCount,
            outputCount = DEFAULT_OUTPUT_COUNT,
            network = state.network
        )) {
            is Result.Success -> {
                _state.update {
                    it.copy(
                        feeEstimate = result.data,
                        isFeeLoading = false,
                        isLoading = false
                    )
                }
            }

            is Result.Error -> {
                _state.update {
                    it.copy(
                        error = "Failed to load fee: ${result.message}",
                        isFeeLoading = false,
                        isLoading = false
                    )
                }
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
                        val explorerUrl =
                            ExplorerUrlHelper.getExplorerUrl(sendResult.txHash, state.network)
                        _effect.emit(
                            BitcoinReviewEffect.TransactionSent(
                                sendResult.txHash,
                                explorerUrl
                            )
                        )
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