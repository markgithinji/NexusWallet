package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.ethereum.BroadcastTransactionUseCase
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.SignEthereumTransactionUseCase
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
import com.example.nexuswallet.feature.coin.Result

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val getTransactionUseCase: GetTransactionUseCase,
    private val signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    private val broadcastTransactionUseCase: BroadcastTransactionUseCase,
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
                    isApproved = false
                )
            }

            val transactionResult = getTransactionUseCase(transactionId)

            when (transactionResult) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            transaction = transactionResult.data,
                            isLoading = false,
                            currentStep = TransactionStep.REVIEWING
                        )
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
                Result.Loading -> {
                    // Already loading
                }
            }
        }
    }

    private suspend fun approveTransaction() {
        val transaction = _uiState.value.transaction ?: return

        _uiState.update {
            it.copy(
                currentStep = TransactionStep.APPROVING,
                isApproved = true,
                error = null
            )
        }

        autoSignAndBroadcast(transaction.id)
    }

    private suspend fun autoSignAndBroadcast(transactionId: String) {
        Log.d("TransactionReview", " START autoSignAndBroadcast for: $transactionId")

        // Step 1: Sign
        _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }
        Log.d("TransactionReview", "Step 1: Signing...")

        val signResult = signEthereumTransactionUseCase(transactionId)
        Log.d("TransactionReview", "Sign result: ${if (signResult is Result.Success) "SUCCESS" else "FAILED"}")

        when (signResult) {
            is Result.Success -> {
                // Get updated transaction
                val updatedTransactionResult = getTransactionUseCase(transactionId)

                when (updatedTransactionResult) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                transaction = updatedTransactionResult.data,
                                currentStep = TransactionStep.BROADCASTING
                            )
                        }

                        Log.d("TransactionReview", "Step 2: Broadcasting...")
                        Log.d("TransactionReview", "Transaction signed hex: ${updatedTransactionResult.data.signedHex?.take(50)}...")
                        Log.d("TransactionReview", "Transaction hash: ${updatedTransactionResult.data.hash}")

                        // Step 2: Broadcast
                        val broadcastResult = broadcastTransactionUseCase(transactionId)
                        Log.d("TransactionReview", "Broadcast result: $broadcastResult")

                        when (broadcastResult) {
                            is Result.Success -> {
                                val result = broadcastResult.data
                                Log.d("TransactionReview", " Broadcast successful! Hash: ${result.hash}")

                                _uiState.update {
                                    it.copy(
                                        broadcastResult = result,
                                        currentStep = TransactionStep.CHECKING_STATUS
                                    )
                                }

                                // Step 3: Check transaction status
                                checkTransactionStatus(result.hash, transactionId)
                            }
                            is Result.Error -> {
                                val error = broadcastResult.message
                                Log.e("TransactionReview", " Broadcast failed: $error")
                                _uiState.update {
                                    it.copy(
                                        error = "Broadcast failed: $error",
                                        currentStep = TransactionStep.ERROR("Broadcast failed")
                                    )
                                }
                            }
                            Result.Loading -> {
                                // Should not happen
                            }
                        }
                    }
                    is Result.Error -> {
                        val error = updatedTransactionResult.message
                        Log.e("TransactionReview", " Failed to get updated transaction: $error")
                        _uiState.update {
                            it.copy(
                                error = "Failed to get updated transaction: $error",
                                currentStep = TransactionStep.ERROR("Failed to update transaction")
                            )
                        }
                    }
                    Result.Loading -> {}
                }
            }
            is Result.Error -> {
                val error = signResult.message
                Log.e("TransactionReview", " Signing failed: $error")
                _uiState.update {
                    it.copy(
                        error = "Signing failed: $error",
                        currentStep = TransactionStep.ERROR("Signing failed")
                    )
                }
            }
            Result.Loading -> {
                // Should not happen
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
            val transactionResult = getTransactionUseCase(transactionId)
            val network = when (transactionResult) {
                is Result.Success -> when (transactionResult.data.chain) {
                    ChainType.ETHEREUM_SEPOLIA -> EthereumNetwork.SEPOLIA
                    else -> EthereumNetwork.MAINNET
                }
                else -> EthereumNetwork.SEPOLIA
            }

            // Try checking status 5 times with 3 second delays
            for (attempt in 1..5) {
                Log.d("TransactionReview", "Status check attempt $attempt/5")

                delay(3000)

                val statusResult = ethereumBlockchainRepository.checkTransactionStatus(txHash, network)

                when (statusResult) {
                    is Result.Success -> {
                        val status = statusResult.data
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
                    }
                    is Result.Error -> {
                        Log.e("TransactionReview", "Error checking status: ${statusResult.message}")
                    }
                    Result.Loading -> {}
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