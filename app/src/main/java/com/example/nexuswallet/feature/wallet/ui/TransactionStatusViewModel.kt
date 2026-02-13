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
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    fun initialize(txHash: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}