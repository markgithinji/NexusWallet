package com.example.nexuswallet.feature.market.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import com.example.nexuswallet.feature.market.data.repository.CoinGeckoRepository
import com.example.nexuswallet.feature.market.data.repository.WebSocketRepository
import com.example.nexuswallet.feature.market.domain.Token
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val coinGeckoRepository: CoinGeckoRepository,
    private val webSocketRepository: WebSocketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    // All tokens from REST API (original, unfiltered)
    private val _allTokens = MutableStateFlow<List<Token>>(emptyList())
    val allTokens: StateFlow<List<Token>> = _allTokens.asStateFlow()

    // Filtered tokens (with search applied)
    private val _filteredTokens = MutableStateFlow<List<Token>>(emptyList())
    val filteredTokens: StateFlow<List<Token>> = _filteredTokens.asStateFlow()

    // WebSocket connection state
    private val _isWebSocketConnected = MutableStateFlow(false)
    val isWebSocketConnected: StateFlow<Boolean> = _isWebSocketConnected.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var webSocketCollectorJob: Job? = null

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private val perPage = 100

    init {
        loadInitialData()
        setupWebSocketObservers()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = MarketUiState.Loading

            // Load first page
            val firstPage = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = 1
            )

            if (firstPage.isNotEmpty()) {
                _allTokens.value = firstPage
                _uiState.value = MarketUiState.Success(firstPage)
                currentPage = 2

                // Load next pages in background
                loadMorePages()
            } else {
                _uiState.value = MarketUiState.Error("Failed to load market data")
            }
        }
    }

    private fun loadMorePages() {
        viewModelScope.launch {
            _isLoadingMore.value = true

            // Load pages 2 and 3
            val moreTokens = mutableListOf<Token>()

            for (page in currentPage..3) {
                val tokens = coinGeckoRepository.getTopCryptocurrencies(
                    perPage = perPage,
                    page = page
                )
                moreTokens.addAll(tokens)

                // Update UI incrementally
                if (tokens.isNotEmpty()) {
                    _allTokens.value = _allTokens.value + tokens
                }

                delay(1000) // Rate limit protection
            }

            currentPage = 4
            _isLoadingMore.value = false
            Log.d("MarketVM", "Total tokens loaded: ${_allTokens.value.size}")
        }
    }

    // Load more on demand (for infinite scrolling)
    fun loadNextPage() {
        if (_isLoadingMore.value || currentPage > 10) return

        viewModelScope.launch {
            _isLoadingMore.value = true

            val tokens = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = currentPage
            )

            if (tokens.isNotEmpty()) {
                _allTokens.value = _allTokens.value + tokens
                currentPage++
            }

            _isLoadingMore.value = false
        }
    }


    private fun setupWebSocketObservers() {
        // Collect full token updates (price + percentage)
        webSocketCollectorJob = viewModelScope.launch {
            webSocketRepository.getTokenUpdates().collect { updatesMap ->
                updateTokensWithLiveData(updatesMap)
            }
        }

        // Collect connection state
        viewModelScope.launch {
            webSocketRepository.getConnectionState().collect { isConnected ->
                _isWebSocketConnected.value = isConnected
            }
        }
    }

    private fun updateTokensWithLiveData(updatesMap: Map<String, TokenPriceUpdate>) {
        val currentTokens = _allTokens.value

        val updatedTokens = currentTokens.map { token ->
            val update = updatesMap[token.id]
            if (update != null) {
                // Update both price & percentage
                token.copy(
                    currentPrice = update.price,
                    priceChange24h = update.priceChange24h,
                    priceChangePercentage24h = update.priceChangePercentage24h
                )
            } else {
                token
            }
        }

        _allTokens.value = updatedTokens
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val query = _searchQuery.value
        val tokens = _allTokens.value

        _filteredTokens.value = if (query.isBlank()) {
            tokens
        } else {
            tokens.filter { token ->
                token.name.contains(query, ignoreCase = true) ||
                        token.symbol.contains(query, ignoreCase = true)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        applySearchFilter()
    }

    fun refreshData() {
        loadInitialData()
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