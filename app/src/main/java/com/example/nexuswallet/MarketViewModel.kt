package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.data.repository.CoinGeckoRepository
import com.example.nexuswallet.domain.Token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketViewModel(
    private val repository: CoinGeckoRepository = CoinGeckoRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    init {
        loadMarketData()
    }

    fun loadMarketData() {
        viewModelScope.launch {
            _uiState.value = MarketUiState.Loading
            try {
                val tokens = repository.getTopCryptocurrencies()
                _uiState.value = MarketUiState.Success(tokens)
            } catch (e: Exception) {
                _uiState.value = MarketUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshData() {
        loadMarketData()
    }
}

sealed class MarketUiState {
    object Loading : MarketUiState()
    data class Success(val tokens: List<Token>) : MarketUiState()
    data class Error(val message: String) : MarketUiState()
}