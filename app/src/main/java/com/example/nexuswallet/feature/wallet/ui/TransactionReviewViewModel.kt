package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.SendBitcoinUseCase
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.SendEthereumUseCase
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
    private val sendEthereumUseCase: SendEthereumUseCase,
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
                sendEthereum()
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

    // ===== BITCOIN =====
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

    // ===== ETHEREUM (UPDATED) =====
    private suspend fun sendEthereum() {
        val state = _uiState.value
        val walletId = state.walletId
        val toAddress = state.toAddress
        val amount = state.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val feeLevel = state.feeLevel

        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

        val result = sendEthereumUseCase(
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

                // Check transaction status if successful
                if (sendResult.success) {
                    checkEthereumTransactionStatus(sendResult.txHash)
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

    private fun checkEthereumTransactionStatus(txHash: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentStep = TransactionStep.CHECKING_STATUS) }

            // You might want to get the network from somewhere
            val network = EthereumNetwork.SEPOLIA // Default to Sepolia for now

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