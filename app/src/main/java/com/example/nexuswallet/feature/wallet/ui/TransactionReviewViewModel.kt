package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) : ViewModel() {

    data class ReviewUiState(
        val transaction: SendTransaction? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentStep: TransactionStep = TransactionStep.LOADING,
        val broadcastResult: BroadcastResult? = null,
        val isApproved: Boolean = false,
        val transactionConfirmed: Boolean = false
    )

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    sealed class TransactionStep {
        data object LOADING : TransactionStep()
        data object REVIEWING : TransactionStep()      // User reviewing
        data object APPROVING : TransactionStep()      // Waiting for user approval
        data object SIGNING : TransactionStep()        // Signing in progress
        data object BROADCASTING : TransactionStep()   // Broadcasting in progress
        data object SUCCESS : TransactionStep()        // Transaction successful
        data class ERROR(val message: String) : TransactionStep()
        data object CHECKING_STATUS : TransactionStep()
    }

    sealed class ReviewEvent {
        object ApproveTransaction : ReviewEvent()  // User approves the transaction
        object ClearError : ReviewEvent()
    }

    fun initialize(transactionId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentStep = TransactionStep.LOADING,
                    isApproved = false  // Reset approval on initialization
                )
            }

            try {
                val transaction = ethereumTransactionRepository.getSendTransaction(transactionId)
                if (transaction != null) {
                    _uiState.update {
                        it.copy(
                            transaction = transaction,
                            isLoading = false,
                            currentStep = TransactionStep.REVIEWING  // Show for user review
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Transaction not found",
                            isLoading = false,
                            currentStep = TransactionStep.ERROR("Transaction not found")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load transaction: ${e.message}",
                        isLoading = false,
                        currentStep = TransactionStep.ERROR("Failed to load transaction")
                    )
                }
            }
        }
    }

    // User approves the transaction
    private suspend fun approveTransaction() {
        val transaction = _uiState.value.transaction ?: return

        _uiState.update {
            it.copy(
                currentStep = TransactionStep.APPROVING,
                isApproved = true,
                error = null
            )
        }

        // Start auto-sign and broadcast after approval
        autoSignAndBroadcast(transaction.id)
    }

    private suspend fun autoSignAndBroadcast(transactionId: String) {
        Log.d("TransactionReview", " START autoSignAndBroadcast for: $transactionId")

        // Step 1: Sign
        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }
        Log.d("TransactionReview", "Step 1: Signing...")

        try {
            val signResult = ethereumTransactionRepository.signTransaction(transactionId)
            Log.d("TransactionReview", "Sign result: ${if (signResult.isSuccess) "SUCCESS" else "FAILED"}")

            if (signResult.isSuccess) {
                // Update with signed transaction
                val updatedTransaction = ethereumTransactionRepository.getSendTransaction(transactionId)
                _uiState.update {
                    it.copy(
                        transaction = updatedTransaction,
                        currentStep = TransactionStep.BROADCASTING
                    )
                }

                Log.d("TransactionReview", "Step 2: Broadcasting...")
                Log.d("TransactionReview", "Transaction signed hex: ${updatedTransaction?.signedHex?.take(50)}...")
                Log.d("TransactionReview", "Transaction hash: ${updatedTransaction?.hash}")

                // Step 2: Broadcast
                val broadcastResult = ethereumTransactionRepository.broadcastTransaction(transactionId)
                Log.d("TransactionReview", "Broadcast result: $broadcastResult")

                if (broadcastResult.isSuccess) {
                    val result = broadcastResult.getOrThrow()
                    Log.d("TransactionReview", " Broadcast successful! Hash: ${result.hash}")

                    _uiState.update {
                        it.copy(
                            broadcastResult = result,
                            currentStep = TransactionStep.CHECKING_STATUS
                        )
                    }

                    // Step 3: Check transaction status
                    checkTransactionStatus(result.hash, transactionId)

                } else {
                    val error = broadcastResult.exceptionOrNull()?.message
                    Log.e("TransactionReview", " Broadcast failed: $error")
                    _uiState.update {
                        it.copy(
                            error = "Broadcast failed: $error",
                            currentStep = TransactionStep.ERROR("Broadcast failed")
                        )
                    }
                }
            } else {
                val error = signResult.exceptionOrNull()?.message
                Log.e("TransactionReview", " Signing failed: $error")
                _uiState.update {
                    it.copy(
                        error = "Signing failed: $error",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TransactionReview", "❌Error in autoSignAndBroadcast: ${e.message}", e)
            _uiState.update {
                it.copy(
                    error = "Error: ${e.message}",
                    currentStep = TransactionStep.ERROR("Processing error")
                )
            }
        }
    }

    private fun checkTransactionStatus(txHash: String?, transactionId: String) {
        viewModelScope.launch {
            if (txHash == null) {
                Log.e("TransactionReview", " No transaction hash available")
                _uiState.update {
                    it.copy(
                        currentStep = TransactionStep.SUCCESS,
                        transactionConfirmed = false
                    )
                }
                return@launch
            }

            Log.d("TransactionReview", " Checking transaction status for: $txHash")

            // Get network from transaction
            val transaction = ethereumTransactionRepository.getSendTransaction(transactionId)
            val network = when (transaction?.chain) {
                ChainType.ETHEREUM_SEPOLIA -> EthereumNetwork.SEPOLIA
                else -> EthereumNetwork.MAINNET
            }

            // Try checking status 5 times with 3 second delays
            for (attempt in 1..5) {
                Log.d("TransactionReview", "Status check attempt $attempt/5")

                delay(3000) // Wait 3 seconds

                try {
                    val status = ethereumBlockchainRepository.checkTransactionStatus(txHash, network)
                    Log.d("TransactionReview", "Transaction status: $status")

                    when (status) {
                        TransactionStatus.SUCCESS -> {
                            Log.d("TransactionReview", " Transaction confirmed on chain!")
                            _uiState.update {
                                it.copy(
                                    currentStep = TransactionStep.SUCCESS,
                                    transactionConfirmed = true
                                )
                            }
                            return@launch
                        }
                        TransactionStatus.FAILED -> {
                            Log.e("TransactionReview", " Transaction failed on chain")
                            _uiState.update {
                                it.copy(
                                    currentStep = TransactionStep.ERROR("Transaction failed"),
                                    error = "Transaction failed on chain"
                                )
                            }
                            return@launch
                        }
                        else -> {
                            Log.d("TransactionReview", " Transaction still pending...")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TransactionReview", "Error checking status: ${e.message}")
                }
            }

            // If we get here, still pending after all attempts
            Log.d("TransactionReview", "⚠ Transaction still pending after 15 seconds")
            _uiState.update {
                it.copy(
                    currentStep = TransactionStep.SUCCESS,
                    transactionConfirmed = false
                )
            }
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