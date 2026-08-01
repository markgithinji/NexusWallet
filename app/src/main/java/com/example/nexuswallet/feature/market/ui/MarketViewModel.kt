package com.example.nexuswallet.feature.market.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.repository.CoinGeckoRepository
import com.example.nexuswallet.feature.market.domain.repository.WebSocketRepository
import com.example.nexuswallet.feature.market.domain.model.Token
import com.example.nexuswallet.feature.market.domain.model.TokenPriceUpdate
import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val coinGeckoRepository: CoinGeckoRepository,
    private val webSocketRepository: WebSocketRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MarketUiState(isLoading = true))
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private var webSocketCollectorJob: Job? = null
    private var connectionStateJob: Job? = null

    private var currentPage = 1
    private val perPage = 100
    private var allTokensCache = emptyList<Token>()

    // Flag to track if initial data is loaded
    private var isInitialDataLoaded = false

    // Debounce time for search
    private val searchDebounceTime = 300L

    init {
        loadInitialData()
        setupWebSocketObservers()
        setupSearchDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _uiState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce<String>(searchDebounceTime)
                .collect { query ->
                    applySearchFilter(query)
                }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            isInitialDataLoaded = false
            allTokensCache = emptyList()

            when (val result = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = 1
            )) {
                is Result.Success -> {
                    val firstPage = result.data
                    allTokensCache = firstPage.distinctBy { it.id }
                    isInitialDataLoaded = true
                    
                    currentPage = 2
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            tokens = allTokensCache
                        )
                    }
                    applySearchFilter(_uiState.value.searchQuery)

                    // Load next pages in background
                    loadRemainingPages()
                }

                is Result.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = result.message
                        ) 
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private fun loadRemainingPages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            // Load pages 2 and 3
            val remainingPagesJobs = (currentPage..3).map { page ->
                async {
                    loadPage(page)
                }
            }

            // Wait for all pages to complete
            remainingPagesJobs.awaitAll()

            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun loadPage(page: Int) {
        if (page > 3) return

        when (val result = coinGeckoRepository.getTopCryptocurrencies(
            perPage = perPage,
            page = page
        )) {
            is Result.Success -> {
                val tokens = result.data
                if (tokens.isNotEmpty()) {
                    val existingIds = allTokensCache.map { it.id }.toSet()
                    val newTokens = tokens.filter { it.id !in existingIds }

                    if (newTokens.isNotEmpty()) {
                        allTokensCache = (allTokensCache + newTokens).distinctBy { it.id }
                        _uiState.update { it.copy(tokens = allTokensCache) }
                        applySearchFilter(_uiState.value.searchQuery)
                    }

                    if (page >= currentPage) {
                        currentPage = page + 1
                    }
                }
            }

            is Result.Error -> {}
            Result.Loading -> {}
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoadingMore || currentPage > 3) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            loadPage(currentPage)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private fun setupWebSocketObservers() {
        webSocketCollectorJob?.cancel()
        connectionStateJob?.cancel()

        webSocketCollectorJob = viewModelScope.launch {
            webSocketRepository.getTokenUpdates().collect { updatesMap ->
                if (isInitialDataLoaded) {
                    updateTokensWithLiveData(updatesMap)
                }
            }
        }

        connectionStateJob = viewModelScope.launch {
            webSocketRepository.getConnectionState().collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun updateTokensWithLiveData(updatesMap: Map<String, TokenPriceUpdate>) {
        val updatedTokens = allTokensCache.map { token ->
            val update = updatesMap[token.id]
            if (update != null && token.currentPrice > 0) {
                val priceRatio = update.price / token.currentPrice
                val newMarketCap = token.marketCap * priceRatio
                
                token.copy(
                    currentPrice = update.price,
                    marketCap = newMarketCap,
                    priceChange24h = update.priceChange24h,
                    priceChangePercentage24h = update.priceChangePercentage24h
                )
            } else {
                token
            }
        }

        allTokensCache = updatedTokens
        _uiState.update { it.copy(tokens = updatedTokens) }
        applySearchFilter(_uiState.value.searchQuery)
    }

    private fun applySearchFilter(query: String) {
        // Ensure tokens are always ordered by market cap (real-time value)
        val tokens = allTokensCache.sortedByDescending { it.marketCap }

        val filtered = if (query.isBlank()) {
            tokens
        } else {
            tokens.filter { token ->
                token.name.contains(query, ignoreCase = true) ||
                        token.symbol.contains(query, ignoreCase = true)
            }
        }
        
        _uiState.update { it.copy(filteredTokens = filtered) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun refreshData() {
        loadInitialData()
    }

    fun retryWebSocket() {
        val state = _uiState.value.connectionState
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            viewModelScope.launch {
                webSocketCollectorJob?.cancel()
                connectionStateJob?.cancel()

                webSocketRepository.disconnect()
                webSocketRepository.reconnect()

                setupWebSocketObservers()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketCollectorJob?.cancel()
        connectionStateJob?.cancel()
        webSocketRepository.disconnect()
    }
}
