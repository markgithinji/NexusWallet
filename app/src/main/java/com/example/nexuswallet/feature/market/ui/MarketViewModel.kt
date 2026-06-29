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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val coinGeckoRepository: CoinGeckoRepository,
    private val webSocketRepository: WebSocketRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<Result<List<Token>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Token>>> = _uiState.asStateFlow()

    // Filtered tokens (with search applied)
    private val _filteredTokens = MutableStateFlow<List<Token>>(emptyList())
    val filteredTokens: StateFlow<List<Token>> = _filteredTokens.asStateFlow()

    // WebSocket connection state
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    
    val isWebSocketConnected: StateFlow<Boolean> = _connectionState.map { state ->
        // We consider it "Connected" (or at least don't show the error banner) 
        // while it's in the process of connecting.
        state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var webSocketCollectorJob: Job? = null
    private var connectionStateJob: Job? = null

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

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
            _searchQuery
                .debounce(searchDebounceTime)
                .collect { query ->
                    applySearchFilter(query)
                }
        }
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
                    isInitialDataLoaded = true
                    // Update filtered tokens before setting success state to avoid empty state flash
                    applySearchFilter(_searchQuery.value)
                    _uiState.value = Result.Success(firstPage)
                    currentPage = 2

                    // Load next pages in background
                    loadRemainingPages()
                }

                is Result.Error -> {
                    _uiState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {} // Already handled
            }
        }
    }

    private fun loadRemainingPages() {
        viewModelScope.launch {
            _isLoadingMore.value = true

            // Load pages 2 and 3
            val remainingPagesJobs = (currentPage..3).map { page ->
                async {
                    loadPage(page)
                }
            }

            // Wait for all pages to complete
            remainingPagesJobs.awaitAll()

            _isLoadingMore.value = false
        }
    }

    private suspend fun loadPage(page: Int) {
        when (val result = coinGeckoRepository.getTopCryptocurrencies(
            perPage = perPage,
            page = page
        )) {
            is Result.Success -> {
                val tokens = result.data
                if (tokens.isNotEmpty()) {
                    allTokensCache = allTokensCache + tokens
                    // Update filtered tokens before setting success state
                    applySearchFilter(_searchQuery.value)
                    _uiState.value = Result.Success(allTokensCache)
                    currentPage = page + 1
                }
            }

            is Result.Error -> {
                // Error silently handled - UI will show error state if needed
            }

            Result.Loading -> {}
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || currentPage > 10) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            loadPage(currentPage)
            _isLoadingMore.value = false
        }
    }

    private fun setupWebSocketObservers() {
        // Cancel existing collectors
        webSocketCollectorJob?.cancel()
        connectionStateJob?.cancel()

        // Collect full token updates (price + percentage)
        webSocketCollectorJob = viewModelScope.launch {
            webSocketRepository.getTokenUpdates().collect { updatesMap ->
                if (isInitialDataLoaded) {
                    updateTokensWithLiveData(updatesMap)
                }
            }
        }

        // Collect connection state
        connectionStateJob = viewModelScope.launch {
            webSocketRepository.getConnectionState().collect { state ->
                _connectionState.value = state
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
        // Update filtered tokens before setting success state
        applySearchFilter(_searchQuery.value)
        _uiState.value = Result.Success(updatedTokens)
    }

    private fun applySearchFilter(query: String) {
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
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun refreshData() {
        viewModelScope.launch {
            // Reset pagination but keep WebSocket connections
            currentPage = 1
            allTokensCache = emptyList()
            isInitialDataLoaded = false

            // Load fresh data
            _uiState.value = Result.Loading

            when (val result = coinGeckoRepository.getTopCryptocurrencies(
                perPage = perPage,
                page = 1
            )) {
                is Result.Success -> {
                    val firstPage = result.data
                    allTokensCache = firstPage
                    isInitialDataLoaded = true
                    // Update filtered tokens before setting success state
                    applySearchFilter(_searchQuery.value)
                    _uiState.value = Result.Success(firstPage)
                    currentPage = 2

                    // Load remaining pages in background
                    loadRemainingPages()
                }

                is Result.Error -> {
                    _uiState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {}
            }
        }
    }

    fun retryWebSocket() {
        // Only reconnect if disconnected
        val currentState = _connectionState.value
        if (currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.ERROR) {
            viewModelScope.launch {
                // Cancel existing collectors
                webSocketCollectorJob?.cancel()
                connectionStateJob?.cancel()

                // Disconnect and reconnect
                webSocketRepository.disconnect()
                webSocketRepository.reconnect()

                // Re-setup observers
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