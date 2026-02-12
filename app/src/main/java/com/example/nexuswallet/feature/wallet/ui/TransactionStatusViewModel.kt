package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result

@HiltViewModel
class TransactionStatusViewModel @Inject constructor(
    private val getTransactionUseCase: GetTransactionUseCase
) : ViewModel() {

    data class StatusUiState(
        val transaction: SendTransaction? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    fun loadTransaction(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = getTransactionUseCase(transactionId)

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            transaction = result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            error = "Failed to load transaction: ${result.message}",
                            isLoading = false,
                            transaction = null
                        )
                    }
                }
                Result.Loading -> {
                    // Already loading, no update needed
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}