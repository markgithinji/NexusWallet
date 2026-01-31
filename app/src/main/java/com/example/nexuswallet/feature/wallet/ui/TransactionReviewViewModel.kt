package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.model.SigningMode
import com.example.nexuswallet.feature.wallet.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    data class ReviewUiState(
        val transaction: SendTransaction? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val isSigning: Boolean = false,
        val isBroadcasting: Boolean = false,
        val signedTransaction: SignedTransaction? = null,
        val broadcastResult: BroadcastResult? = null
    )

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    sealed class ReviewEvent {
        object SignTransaction : ReviewEvent()
        object BroadcastTransaction : ReviewEvent()
        object ClearError : ReviewEvent()
    }

    private suspend fun signTransaction() {
        val transaction = _uiState.value.transaction ?: return

        _uiState.update { it.copy(isSigning = true, error = null) }

        try {
            val result = transactionRepository.signTransaction(transaction.id)

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSigning = false,
                        signedTransaction = result.getOrNull(),
                        error = null
                    )
                }

                Log.d("TransactionReview", "Transaction signed successfully")

            } else {
                _uiState.update {
                    it.copy(
                        isSigning = false,
                        error = "Failed to sign: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSigning = false,
                    error = "Signing error: ${e.message}"
                )
            }
        }
    }

    fun initialize(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val transaction = transactionRepository.getSendTransaction(transactionId)
                if (transaction != null) {
                    _uiState.update {
                        it.copy(
                            transaction = transaction,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Transaction not found",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load transaction: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onEvent(event: ReviewEvent) {
        viewModelScope.launch {
            when (event) {
                ReviewEvent.SignTransaction -> signTransaction()
                ReviewEvent.BroadcastTransaction -> broadcastTransaction()
                ReviewEvent.ClearError -> {
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }

    private suspend fun broadcastTransaction() {
        val transaction = _uiState.value.transaction ?: return

        _uiState.update { it.copy(isBroadcasting = true, error = null) }

        try {
            val result = transactionRepository.broadcastTransaction(transaction.id)

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isBroadcasting = false,
                        broadcastResult = result.getOrNull()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isBroadcasting = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to broadcast transaction"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isBroadcasting = false,
                    error = "Broadcast error: ${e.message}"
                )
            }
        }
    }
}