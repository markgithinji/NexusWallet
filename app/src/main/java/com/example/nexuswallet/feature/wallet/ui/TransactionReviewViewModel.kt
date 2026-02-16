package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.SendBitcoinUseCase
import com.example.nexuswallet.feature.coin.ethereum.BroadcastTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.SignEthereumTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.BroadcastSolanaTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.SignSolanaTransactionUseCase
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val getTransactionUseCase: GetTransactionUseCase,
    // Ethereum
    private val signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    private val broadcastTransactionUseCase: BroadcastTransactionUseCase,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    // Solana
    private val signSolanaTransactionUseCase: SignSolanaTransactionUseCase,
    private val broadcastSolanaTransactionUseCase: BroadcastSolanaTransactionUseCase,
    // Bitcoin
    private val sendBitcoinUseCase: SendBitcoinUseCase
) : ViewModel() {

    sealed class TransactionType {
        data class Ethereum(val transaction: EthereumTransaction) : TransactionType()
        data class Bitcoin(val transaction: BitcoinTransaction) : TransactionType()
        data class Solana(val transaction: SolanaTransaction) : TransactionType()
    }

    data class ReviewUiState(
        val transaction: TransactionType? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentStep: TransactionStep = TransactionStep.LOADING,
        val broadcastResult: BroadcastResult? = null,
        val isApproved: Boolean = false,
        val transactionConfirmed: Boolean = false,
        val walletId: String = "",
        val coinType: String = "",
        val toAddress: String = "",
        val amount: String = "",
        val feeLevel: FeeLevel = FeeLevel.NORMAL
    )

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    sealed class TransactionStep {
        data object LOADING : TransactionStep()
        data object REVIEWING : TransactionStep()
        data object APPROVING : TransactionStep()
        data object SIGNING : TransactionStep()
        data object BROADCASTING : TransactionStep()
        data object SUCCESS : TransactionStep()
        data class ERROR(val message: String) : TransactionStep()
        data object CHECKING_STATUS : TransactionStep()
    }

    sealed class ReviewEvent {
        object ApproveTransaction : ReviewEvent()
        object ClearError : ReviewEvent()
    }

    fun initialize(
        walletId: String,
        coinType: String,
        toAddress: String,
        amount: String,
        feeLevel: String?
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentStep = TransactionStep.LOADING,
                    walletId = walletId,
                    coinType = coinType,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel?.let { FeeLevel.valueOf(it) } ?: FeeLevel.NORMAL
                )
            }

            // For Bitcoin, we don't need to load an existing transaction
            // since SendBitcoinUseCase will create it
            if (coinType == "BTC") {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = TransactionStep.REVIEWING
                    )
                }
                return@launch
            }

            // load the existing transaction
            val transactionResult = getTransactionUseCase(walletId)

            when (transactionResult) {
                is Result.Success -> {
                    val transaction = transactionResult.data
                    val transactionType = when (transaction) {
                        is EthereumTransaction -> TransactionType.Ethereum(transaction)
                        else -> null
                    }

                    if (transactionType != null) {
                        _uiState.update {
                            it.copy(
                                transaction = transactionType,
                                isLoading = false,
                                currentStep = TransactionStep.REVIEWING
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                error = "Unsupported transaction type",
                                isLoading = false,
                                currentStep = TransactionStep.ERROR("Unsupported transaction type")
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            error = "Failed to load transaction: ${transactionResult.message}",
                            isLoading = false,
                            currentStep = TransactionStep.ERROR("Failed to load transaction")
                        )
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    private suspend fun approveTransaction() {
        val state = _uiState.value

        _uiState.update {
            it.copy(
                currentStep = TransactionStep.APPROVING,
                isApproved = true,
                error = null
            )
        }

        when (state.coinType) {
            "BTC" -> {
                sendBitcoin()
            }
            "ETH" -> {
                val transaction = state.transaction as? TransactionType.Ethereum
                if (transaction != null) {
                    autoSignAndBroadcastEthereum(transaction.transaction.id)
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Ethereum transaction not found",
                            currentStep = TransactionStep.ERROR("Transaction not found")
                        )
                    }
                }
            }
            "SOL" -> {
                val transaction = state.transaction as? TransactionType.Solana
                if (transaction != null) {
                    autoSignAndBroadcastSolana(transaction.transaction.id)
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Solana transaction not found",
                            currentStep = TransactionStep.ERROR("Transaction not found")
                        )
                    }
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        error = "Unsupported coin type: ${state.coinType}",
                        currentStep = TransactionStep.ERROR("Unsupported coin type")
                    )
                }
            }
        }
    }

    // ===== BITCOIN (UPDATED) =====
    private suspend fun sendBitcoin() {
        val state = _uiState.value
        val walletId = state.walletId
        val toAddress = state.toAddress
        val amount = state.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val feeLevel = state.feeLevel

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val result = sendBitcoinUseCase(
            walletId = walletId,
            toAddress = toAddress,
            amount = amount,
            feeLevel = feeLevel,
            note = null
        )

        when (result) {
            is Result.Success -> {
                val sendResult = result.data
                _uiState.update {
                    it.copy(
                        broadcastResult = BroadcastResult(
                            success = sendResult.success,
                            hash = sendResult.txHash,
                            error = sendResult.error
                        ),
                        currentStep = if (sendResult.success) TransactionStep.SUCCESS else TransactionStep.ERROR(sendResult.error ?: "Send failed")
                    )
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = result.message,
                        currentStep = TransactionStep.ERROR(result.message ?: "Send failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    // ===== ETHEREUM (KEEP EXISTING) =====
    private suspend fun autoSignAndBroadcastEthereum(transactionId: String) {
        Log.d("TransactionReview", "START Ethereum autoSignAndBroadcast for: $transactionId")

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val signResult = signEthereumTransactionUseCase(transactionId)

        when (signResult) {
            is Result.Success -> {
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        val updatedTransaction = updatedTransactionResult.data as? EthereumTransaction
                        if (updatedTransaction != null) {
                            _uiState.update {
                                it.copy(
                                    transaction = TransactionType.Ethereum(updatedTransaction),
                                    currentStep = TransactionStep.BROADCASTING
                                )
                            }

                            val broadcastResult = broadcastTransactionUseCase(transactionId)

                            when (broadcastResult) {
                                is Result.Success -> {
                                    val result = broadcastResult.data
                                    _uiState.update {
                                        it.copy(
                                            broadcastResult = result,
                                            currentStep = TransactionStep.CHECKING_STATUS
                                        )
                                    }
                                    checkEthereumTransactionStatus(result.hash, updatedTransaction)
                                }
                                is Result.Error -> {
                                    _uiState.update {
                                        it.copy(
                                            error = "Broadcast failed: ${broadcastResult.message}",
                                            currentStep = TransactionStep.ERROR("Broadcast failed")
                                        )
                                    }
                                }
                                Result.Loading -> {}
                            }
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: ${updatedTransactionResult.message}",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Signing failed: ${signResult.message}",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    private fun checkEthereumTransactionStatus(txHash: String?, transaction: EthereumTransaction) {
        viewModelScope.launch {
            if (txHash == null) {
                _uiState.update {
                    it.copy(
                        currentStep = TransactionStep.SUCCESS,
                        transactionConfirmed = false
                    )
                }
                return@launch
            }

            val network = when (transaction.network) {
                "sepolia" -> EthereumNetwork.SEPOLIA
                else -> EthereumNetwork.MAINNET
            }

            for (attempt in 1..5) {
                delay(3000)
                val statusResult = ethereumBlockchainRepository.checkTransactionStatus(txHash, network)

                when (statusResult) {
                    is Result.Success -> {
                        when (statusResult.data) {
                            TransactionStatus.SUCCESS -> {
                                _uiState.update {
                                    it.copy(
                                        currentStep = TransactionStep.SUCCESS,
                                        transactionConfirmed = true
                                    )
                                }
                                return@launch
                            }
                            TransactionStatus.FAILED -> {
                                _uiState.update {
                                    it.copy(
                                        currentStep = TransactionStep.ERROR("Transaction failed"),
                                        error = "Transaction failed on chain"
                                    )
                                }
                                return@launch
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }

            _uiState.update {
                it.copy(
                    currentStep = TransactionStep.SUCCESS,
                    transactionConfirmed = false
                )
            }
        }
    }

    // ===== SOLANA (KEEP EXISTING) =====
    private suspend fun autoSignAndBroadcastSolana(transactionId: String) {
        Log.d("TransactionReview", "START Solana autoSignAndBroadcast for: $transactionId")

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val signResult = signSolanaTransactionUseCase(transactionId)

        when (signResult) {
            is Result.Success -> {
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        val updatedTransaction = updatedTransactionResult.data as? SolanaTransaction
                        if (updatedTransaction != null) {
                            _uiState.update {
                                it.copy(
                                    transaction = TransactionType.Solana(updatedTransaction),
                                    currentStep = TransactionStep.BROADCASTING
                                )
                            }

                            val broadcastResult = broadcastSolanaTransactionUseCase(transactionId)

                            when (broadcastResult) {
                                is Result.Success -> {
                                    val result = broadcastResult.data
                                    _uiState.update {
                                        it.copy(
                                            broadcastResult = result,
                                            currentStep = TransactionStep.SUCCESS,
                                            transactionConfirmed = result.success
                                        )
                                    }
                                }
                                is Result.Error -> {
                                    _uiState.update {
                                        it.copy(
                                            error = "Broadcast failed: ${broadcastResult.message}",
                                            currentStep = TransactionStep.ERROR("Broadcast failed")
                                        )
                                    }
                                }
                                Result.Loading -> {}
                            }
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: ${updatedTransactionResult.message}",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        error = "Signing failed: ${signResult.message}",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {}
        }
    }

    fun onEvent(event: ReviewEvent) {
        viewModelScope.launch {
            when (event) {
                ReviewEvent.ApproveTransaction -> approveTransaction()
                ReviewEvent.ClearError -> {
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }
}