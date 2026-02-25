package com.example.nexuswallet.feature.market.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
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

    // Using Result for UI state
    private val _uiState = MutableStateFlow<Result<List<Token>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Token>>> = _uiState.asStateFlow()

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
    private var allTokensCache = emptyList<Token>() // Private cache for internal use

    init {
        loadInitialData()
        setupWebSocketObservers()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = Result.Loading

            when (val result = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = 1
            )) {
                is Result.Success -> {
                    val firstPage = result.data
                    allTokensCache = firstPage
                    _uiState.value = Result.Success(firstPage)
                    applySearchFilter() // Apply search to update filtered list
                    currentPage = 2

                    // Load next pages in background
                    loadMorePages()
                }

                is Result.Error -> {
                    _uiState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {} // Already handled
            }
        }
    }

    private fun loadMorePages() {
        viewModelScope.launch {
            _isLoadingMore.value = true

            // Load pages 2 and 3
            for (page in currentPage..3) {
                when (val result = coinGeckoRepository.getTopCryptocurrencies(
                    perPage = perPage,
                    page = page
                )) {
                    is Result.Success -> {
                        val tokens = result.data
                        if (tokens.isNotEmpty()) {
                            allTokensCache = allTokensCache + tokens
                            // Update UI state with new combined list
                            _uiState.value = Result.Success(allTokensCache)
                            applySearchFilter()
                        }
                    }

                    is Result.Error -> {
                        Log.e("MarketVM", "Error loading page $page: ${result.message}")
                    }

                    Result.Loading -> {} // Not used here
                }

                delay(1000) // Rate limit protection
            }

            currentPage = 4
            _isLoadingMore.value = false
            Log.d("MarketVM", "Total tokens loaded: ${allTokensCache.size}")
        }
    }

    // Load more on demand (for infinite scrolling)
    fun loadNextPage() {
        if (_isLoadingMore.value || currentPage > 10) return

        viewModelScope.launch {
            _isLoadingMore.value = true

            when (val result = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = currentPage
            )) {
                is Result.Success -> {
                    val tokens = result.data
                    if (tokens.isNotEmpty()) {
                        allTokensCache = allTokensCache + tokens
                        _uiState.value = Result.Success(allTokensCache)
                        applySearchFilter()
                        currentPage++
                    }
                }

                is Result.Error -> {
                    Log.e("MarketVM", "Error loading page $currentPage: ${result.message}")
                }

                Result.Loading -> {}
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
        val updatedTokens = allTokensCache.map { token ->
            val update = updatesMap[token.id]
            if (update != null) {
                token.copy(
                    currentPrice = update.price,
                    priceChange24h = update.priceChange24h,
                    priceChangePercentage24h = update.priceChangePercentage24h
                )
            } else {
                token
            }
        }

        allTokensCache = updatedTokens
        _uiState.value = Result.Success(updatedTokens)
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val query = _searchQuery.value
        val tokens = allTokensCache

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
        // Reset pagination
        currentPage = 1
        allTokensCache = emptyList()
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