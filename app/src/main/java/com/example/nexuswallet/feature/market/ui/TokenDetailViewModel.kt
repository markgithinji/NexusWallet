package com.example.nexuswallet.feature.market.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.market.domain.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TokenDetailViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tokenId: String = checkNotNull(savedStateHandle["tokenId"])

    // Map token IDs to display names for news search
    private val tokenDisplayNames = mapOf(
        "bitcoin" to "Bitcoin",
        "ethereum" to "Ethereum",
        "solana" to "Solana",
        "cardano" to "Cardano",
        "binancecoin" to "Binance Coin",
        "ripple" to "XRP",
        "dogecoin" to "Dogecoin",
        "polkadot" to "Polkadot",
        "matic-network" to "Polygon",
        "avalanche-2" to "Avalanche"
    )

    // Token details state
    private val _uiState = MutableStateFlow<Result<TokenDetail>>(Result.Loading)
    val uiState: StateFlow<Result<TokenDetail>> = _uiState.asStateFlow()

    // Chart data state
    private val _chartState = MutableStateFlow<Result<ChartData>>(Result.Loading)
    val chartState: StateFlow<Result<ChartData>> = _chartState.asStateFlow()

    private val _selectedDuration = MutableStateFlow(ChartDuration.ONE_WEEK)
    val selectedDuration: StateFlow<ChartDuration> = _selectedDuration.asStateFlow()

    // News state
    private val _newsState = MutableStateFlow<Result<List<NewsArticle>>>(Result.Loading)
    val newsState: StateFlow<Result<List<NewsArticle>>> = _newsState.asStateFlow()

    // Track if we've already loaded news to avoid duplicate calls
    private var hasLoadedNews = false

    init {
        loadTokenDetails()
        loadChartData(_selectedDuration.value)
    }

    private fun loadTokenDetails() {
        viewModelScope.launch {
            _uiState.value = Result.Loading

            when (val result = marketRepository.getTokenDetails(tokenId)) {
                is Result.Success -> {
                    _uiState.value = Result.Success(result.data)
                    // Load news only after we have token details and haven't loaded yet
                    if (!hasLoadedNews) {
                        loadNews()
                    }
                }

                is Result.Error -> {
                    _uiState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {} // Already handled
            }
        }
    }

    fun loadChartData(duration: ChartDuration) {
        viewModelScope.launch {
            _chartState.value = Result.Loading
            _selectedDuration.value = duration

            when (val result = marketRepository.getMarketChart(tokenId, duration)) {
                is Result.Success -> {
                    _chartState.value = Result.Success(result.data)
                }

                is Result.Error -> {
                    _chartState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {} // Already handled
            }
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            _newsState.value = Result.Loading

            // Get display name for news search from the loaded token details if available
            val searchQuery = when (val currentState = _uiState.value) {
                is Result.Success -> currentState.data.name
                else -> tokenDisplayNames[tokenId] ?: tokenId.replaceFirstChar { it.uppercase() }
            }

            when (val result = marketRepository.getCoinNews(searchQuery)) {
                is Result.Success -> {
                    _newsState.value = Result.Success(result.data)
                    hasLoadedNews = true
                }

                is Result.Error -> {
                    _newsState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {} // Already handled
            }
        }
    }

    fun selectDuration(duration: ChartDuration) {
        if (duration != _selectedDuration.value) {
            loadChartData(duration)
        }
    }

    fun retryLoading() {
        when {
            _uiState.value is Result.Error -> loadTokenDetails()
            _chartState.value is Result.Error -> loadChartData(_selectedDuration.value)
            _newsState.value is Result.Error -> loadNews()
        }
    }

    fun refresh() {
        hasLoadedNews = false // Reset to allow news to load again
        loadTokenDetails()
        loadChartData(_selectedDuration.value)
    }

    fun clearErrors() {
        if (_uiState.value is Result.Error) {
            _uiState.value = Result.Loading
        }
        if (_chartState.value is Result.Error) {
            _chartState.value = Result.Loading
        }
        if (_newsState.value is Result.Error) {
            _newsState.value = Result.Loading
        }
    }
}