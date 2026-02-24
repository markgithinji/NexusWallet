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
import com.example.nexuswallet.feature.wallet.data.repository.MarketRepository
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

    private val _uiState = MutableStateFlow<TokenDetailUiState>(TokenDetailUiState.Loading)
    val uiState: StateFlow<TokenDetailUiState> = _uiState.asStateFlow()

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
            _uiState.value = TokenDetailUiState.Loading
            try {
                val token = marketRepository.getTokenDetails(tokenId)
                if (token != null) {
                    _uiState.value = TokenDetailUiState.Success(token)
                } else {
                    _uiState.value = TokenDetailUiState.Error("Token not found")
                }
            } catch (e: Exception) {
                _uiState.value = TokenDetailUiState.Error(e.message ?: "Failed to load token details")
            }
        }
    }

    fun loadChartData(duration: ChartDuration) {
        viewModelScope.launch {
            _isLoadingChart.value = true
            _selectedDuration.value = duration

            try {
                val chart = marketRepository.getMarketChart(tokenId, duration)
                _chartData.value = chart
            } catch (e: Exception) {
                Log.e("TokenDetailVM", "Error loading chart: ${e.message}")
            } finally {
                _isLoadingChart.value = false
            }
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            _isLoadingNews.value = true

            // Get display name for news search
            val searchQuery = tokenDisplayNames[tokenId] ?: tokenId.replaceFirstChar { it.uppercase() }

            try {
                val news = marketRepository.getCoinNews(searchQuery)
                _newsArticles.value = news
            } catch (e: Exception) {
                Log.e("TokenDetailVM", "Error loading news: ${e.message}")
            } finally {
                _isLoadingNews.value = false
            }
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

sealed class TokenDetailUiState {
    object Loading : TokenDetailUiState()
    data class Success(val token: TokenDetail) : TokenDetailUiState()
    data class Error(val message: String) : TokenDetailUiState()
}