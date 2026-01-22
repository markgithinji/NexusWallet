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

    // All tokens from REST API (original, unfiltered)
    private val _allTokens = MutableStateFlow<List<Token>>(emptyList())
    val allTokens: StateFlow<List<Token>> = _allTokens.asStateFlow()

    // Filtered tokens (with search applied)
    private val _filteredTokens = MutableStateFlow<List<Token>>(emptyList())
    val filteredTokens: StateFlow<List<Token>> = _filteredTokens.asStateFlow()

    // Refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // WebSocket connection state
    private val _isWebSocketConnected = MutableStateFlow(false)
    val isWebSocketConnected: StateFlow<Boolean> = _isWebSocketConnected.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
                _allTokens.value = tokens
                _uiState.value = MarketUiState.Success(tokens)
                applySearchFilter() // Apply current search to new data
                updateTokensWithLivePrices(tokens, _livePrices.value)
            } catch (e: Exception) {
                _uiState.value = MarketUiState.Error(e.message ?: "Failed to load data")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun setupWebSocketObservers() {
        webSocketCollectorJob = viewModelScope.launch {
            webSocketRepository.getLivePrices().collect { priceUpdate ->
                _livePrices.value = priceUpdate
                updateAllTokensWithLivePrices(priceUpdate)
            }
        }

        viewModelScope.launch {
            webSocketRepository.getConnectionState().collect { isConnected ->
                _isWebSocketConnected.value = isConnected
            }
        }
    }

    private fun updateAllTokensWithLivePrices(livePrices: Map<String, Double>) {
        val currentTokens = _allTokens.value
        updateTokensWithLivePrices(currentTokens, livePrices)
    }

    private fun updateTokensWithLivePrices(
        restTokens: List<Token>,
        livePrices: Map<String, Double>
    ) {
        val updatedTokens = restTokens.map { token ->
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

        _allTokens.value = updatedTokens
        applySearchFilter() // Re-apply search with updated prices
    }

    // Search functionality
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val query = _searchQuery.value
        val tokens = _allTokens.value

        val filtered = if (query.isBlank()) {
            tokens
        } else {
            tokens.filter { token ->
                token.name.contains(query, ignoreCase = true) ||
                        token.symbol.contains(query, ignoreCase = true)
            }
        }

        _filteredTokens.value = filtered
    }

    fun refreshData() {
        _isRefreshing.value = true
        loadMarketData()
    }

    fun retryWebSocket() {
        webSocketCollectorJob?.cancel()
        webSocketRepository.reconnect()
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