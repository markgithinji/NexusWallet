package com.example.nexuswallet.feature.market.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.market.domain.Token
import com.example.nexuswallet.feature.market.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result

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

    // Using Result for UI state
    private val _uiState = MutableStateFlow<Result<TokenDetail>>(Result.Loading)
    val uiState: StateFlow<Result<TokenDetail>> = _uiState.asStateFlow()

    private val _chartData = MutableStateFlow<ChartData?>(null)
    val chartData: StateFlow<ChartData?> = _chartData.asStateFlow()

    private val _selectedDuration = MutableStateFlow<ChartDuration>(ChartDuration.ONE_WEEK)
    val selectedDuration: StateFlow<ChartDuration> = _selectedDuration.asStateFlow()

    private val _isLoadingChart = MutableStateFlow(false)
    val isLoadingChart: StateFlow<Boolean> = _isLoadingChart.asStateFlow()

    // News
    private val _newsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsArticles: StateFlow<List<NewsArticle>> = _newsArticles.asStateFlow()

    private val _isLoadingNews = MutableStateFlow(false)
    val isLoadingNews: StateFlow<Boolean> = _isLoadingNews.asStateFlow()

    init {
        loadTokenDetails()
        loadChartData(_selectedDuration.value)
        loadNews()
    }

    private fun loadTokenDetails() {
        viewModelScope.launch {
            _uiState.value = Result.Loading

            val token = marketRepository.getTokenDetails(tokenId)
            if (token != null) {
                _uiState.value = Result.Success(token)
            } else {
                _uiState.value = Result.Error("Token not found")
            }
        }
    }

    fun loadChartData(duration: ChartDuration) {
        viewModelScope.launch {
            _isLoadingChart.value = true
            _selectedDuration.value = duration

            val chart = marketRepository.getMarketChart(tokenId, duration)
            _chartData.value = chart
            _isLoadingChart.value = false
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            _isLoadingNews.value = true

            // Get display name for news search
            val searchQuery = tokenDisplayNames[tokenId] ?: tokenId.replaceFirstChar { it.uppercase() }

            val news = marketRepository.getCoinNews(searchQuery)
            _newsArticles.value = news
            _isLoadingNews.value = false
        }
    }

    fun selectDuration(duration: ChartDuration) {
        if (duration != _selectedDuration.value) {
            loadChartData(duration)
        }
    }

    fun refresh() {
        loadTokenDetails()
        loadChartData(_selectedDuration.value)
        loadNews()
    }
}