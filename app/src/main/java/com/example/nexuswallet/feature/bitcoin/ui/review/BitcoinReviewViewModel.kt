package com.example.nexuswallet.feature.bitcoin.ui.review

import com.example.nexuswallet.feature.bitcoin.ui.review.BitcoinReviewEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinBalanceUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinFeeEstimateUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinWalletUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SelectBitcoinUtxosUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.PrepareBitcoinTransactionUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SendBitcoinUseCase
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_INPUT_COUNT
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_OUTPUT_COUNT
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.TransactionResult
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.toSatoshis
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
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
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class BitcoinReviewViewModel @Inject constructor(
    private val prepareBitcoinTransactionUseCase: PrepareBitcoinTransactionUseCase,
    private val sendBitcoinUseCase: SendBitcoinUseCase,
    private val getBitcoinWalletUseCase: GetBitcoinWalletUseCase,
    private val getBitcoinBalanceUseCase: GetBitcoinBalanceUseCase,
    private val getBitcoinFeeEstimateUseCase: GetBitcoinFeeEstimateUseCase,
    private val selectBitcoinUtxosUseCase: SelectBitcoinUtxosUseCase,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BitcoinReviewUiState())
    val state: StateFlow<BitcoinReviewUiState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<BitcoinReviewEffect>()
    val effect: SharedFlow<BitcoinReviewEffect> = _effect.asSharedFlow()

    private val _cryptoObject = MutableStateFlow<Cipher?>(null)
    val cryptoObject: StateFlow<Cipher?> = _cryptoObject.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

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

                    // Load balance - aggregated across Legacy and SegWit
                    val currentWallet = walletRepository.getWallet(walletId)
                    val xpub = currentWallet?.bitcoinCoins?.find { it.network == network }?.xpub
                    loadBalance(walletInfo.walletAddress, network, xpub)
                }

                is Result.Error -> {
                    _state.update { it.copy(error = walletResult.message, isLoading = false) }
                }

                else -> {}
            }
        }
    }

    private suspend fun loadBalance(address: String, network: BitcoinNetwork, xpub: String? = null) {
        when (val result = getBitcoinBalanceUseCase(address, network, xpub)) {
            is Result.Success -> {
                val balance = result.data
                _state.update {
                    it.copy(
                        balance = balance,
                        balanceFormatted = "${
                            balance.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                                .toPlainString()
                        } BTC"
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
        _state.update { it.copy(isFeeLoading = true) }

        val state = _state.value
        
        // Detect SegWit from address
        val isSegwitAddress = state.fromAddress.startsWith("bc1", ignoreCase = true) || 
                             state.fromAddress.startsWith("tb1", ignoreCase = true)

        // Step 1: Get base fee rate (using 1 input as placeholder)
        val baseFeeResult = getBitcoinFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            inputCount = DEFAULT_INPUT_COUNT,
            outputCount = DEFAULT_OUTPUT_COUNT,
            network = state.network,
            isSegwit = isSegwitAddress
        )
        
        val feePerByte = if (baseFeeResult is Result.Success) baseFeeResult.data.feePerByte else 10.0

        // Step 2: Fetch UTXOs
        val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(state.fromAddress, state.network)

        // Step 3: Determine accurate input count and script types
        val utxos = if (utxosResult is Result.Success) utxosResult.data else emptyList()
        val hasSegwitUtxo = utxos.any { org.bitcoinj.script.ScriptPattern.isP2WPKH(it.script) } || isSegwitAddress

        val inputCount = if (utxos.isNotEmpty()) {
            val selected = selectBitcoinUtxosUseCase(
                utxos = utxos,
                targetSatoshis = state.amountValue.toSatoshis(),
                feePerByte = feePerByte
            )
            // If selection fails, use total count to trigger correct insufficient balance logic
            if (selected.isNotEmpty()) selected.size else utxos.size.coerceAtLeast(DEFAULT_INPUT_COUNT)
        } else {
            DEFAULT_INPUT_COUNT
        }

        // Step 4: Get final accurate fee estimate
        when (val result = getBitcoinFeeEstimateUseCase(
            feeLevel = state.feeLevel,
            inputCount = inputCount,
            outputCount = DEFAULT_OUTPUT_COUNT,
            network = state.network,
            isSegwit = hasSegwitUtxo
        )) {
            is Result.Success -> {
                val feeEstimate = result.data
                val totalRequired = state.amountValue + BigDecimal(feeEstimate.totalFeeBtc)
                
                if (totalRequired > state.balance) {
                    _state.update {
                        it.copy(
                            error = "Insufficient funds for fees.",
                            isFeeLoading = false,
                            isLoading = false
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            feeEstimate = feeEstimate,
                            isFeeLoading = false,
                            isLoading = false
                        )
                    }
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
                    _effect.emit(BitcoinReviewEffect.TransactionResultEffect(TransactionResult.Error(result.message)))
                }

                else -> {}
            }
        }
    }

    fun sendTransaction(cipher: Cipher? = null, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            val state = _state.value
            val preparedTx = state.preparedTransaction

            if (preparedTx == null || !state.transactionPrepared) {
                _effect.emit(BitcoinReviewEffect.TransactionResultEffect(TransactionResult.Error("Transaction not prepared")))
                return@launch
            }

            _state.update { it.copy(isLoading = true, error = null, step = "Broadcasting...") }

            val result = sendBitcoinUseCase(
                preparedTransaction = preparedTx,
                walletId = state.walletId,
                network = state.network,
                cipher = cipher
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
                            BitcoinReviewEffect.TransactionResultEffect(
                                TransactionResult.Success(
                                    sendResult.txHash,
                                    explorerUrl
                                )
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
                        _effect.emit(BitcoinReviewEffect.TransactionResultEffect(TransactionResult.Error(sendResult.error ?: "Send failed")))
                    }
                }

                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject?.cipher
                        _authRequest.value = System.currentTimeMillis()
                        _state.update { it.copy(isLoading = false) }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                        _effect.emit(BitcoinReviewEffect.TransactionResultEffect(TransactionResult.Error(result.message)))
                    }
                }

                else -> {}
            }
        }
    }

    fun completeSendAfterBiometric(cipher: Cipher? = null, onSuccess: (String) -> Unit = {}) {
        _cryptoObject.value = null
        _authRequest.value = null
        sendTransaction(cipher = cipher, onSuccess = onSuccess)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearAuthRequest() {
        _authRequest.value = null
    }
}
