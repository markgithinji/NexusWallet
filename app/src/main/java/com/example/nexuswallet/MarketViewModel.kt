package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.data.repository.CoinGeckoRepository
import com.example.nexuswallet.data.repository.WebSocketRepository
import com.example.nexuswallet.domain.Token
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MarketViewModel(
    private val coinGeckoRepository: CoinGeckoRepository = CoinGeckoRepository(),
    private val webSocketRepository: WebSocketRepository = WebSocketRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    // WebSocket Live Prices
    private val _livePrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    val livePrices: StateFlow<Map<String, Double>> = _livePrices.asStateFlow()

    // Combined tokens with live updates
    private val _combinedTokens = MutableStateFlow<List<Token>>(emptyList())
    val combinedTokens: StateFlow<List<Token>> = _combinedTokens.asStateFlow()

    // Refresh state for pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // WebSocket connection state
    private val _isWebSocketConnected = MutableStateFlow(false)
    val isWebSocketConnected: StateFlow<Boolean> = _isWebSocketConnected.asStateFlow()

    private var webSocketCollectorJob: Job? = null

    init {
        loadMarketData()
        setupWebSocketObservers()
    }

    private fun loadMarketData() {
        viewModelScope.launch {
            try {
                if (!_isRefreshing.value) {
                    _uiState.value = MarketUiState.Loading
                }
                val tokens = coinGeckoRepository.getTopCryptocurrencies()
                _uiState.value = MarketUiState.Success(tokens)
                updateCombinedTokens(tokens, _livePrices.value)
            } catch (e: Exception) {
                _uiState.value = MarketUiState.Error(e.message ?: "Failed to load data")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun setupWebSocketObservers() {
        // Start collecting price updates from repository
        webSocketCollectorJob = viewModelScope.launch {
            webSocketRepository.getLivePrices().collect { priceUpdate ->
                _livePrices.value = priceUpdate
                _isWebSocketConnected.value = true

                // Update tokens with live prices
                val currentState = _uiState.value
                if (currentState is MarketUiState.Success) {
                    updateCombinedTokens(currentState.tokens, priceUpdate)
                }
            }
        }

        // Monitor connection state from repository
        viewModelScope.launch {
            webSocketRepository.getConnectionState().collect { isConnected ->
                _isWebSocketConnected.value = isConnected
            }
        }
    }

    private fun updateCombinedTokens(
        restTokens: List<Token>,
        livePrices: Map<String, Double>
    ) {
        val updatedTokens = restTokens.map { token ->
            // Try different symbol formats to match WebSocket data
            val symbolVariants = listOf(
                token.symbol.lowercase(),
                token.symbol.uppercase(),
                token.id.lowercase()
            )

            val livePrice = symbolVariants.firstOrNull { livePrices.containsKey(it) }
                ?.let { livePrices[it] }
                ?: token.currentPrice

            token.copy(currentPrice = livePrice)
        }
        _combinedTokens.value = updatedTokens
    }

    fun refreshData() {
        _isRefreshing.value = true
        loadMarketData()
    }

    fun retryWebSocket() {
        // Cancel current collection
        webSocketCollectorJob?.cancel()

        // Use repository's reconnect method
        webSocketRepository.reconnect()

        // Restart observers
        setupWebSocketObservers()
    }

    override fun onCleared() {
        super.onCleared()
        webSocketCollectorJob?.cancel()
        webSocketRepository.disconnect()
    }
}

sealed class MarketUiState {
    object Loading : MarketUiState()
    data class Success(val tokens: List<Token>) : MarketUiState()
    data class Error(val message: String) : MarketUiState()
}