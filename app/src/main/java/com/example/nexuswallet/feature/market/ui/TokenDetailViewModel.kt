package com.example.nexuswallet.feature.market.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
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

            // ALWAYS fetch in USD
            when (val result = marketRepository.getTokenDetails(tokenId, SupportedCurrency.USD)) {
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

                Result.Loading -> {}
            }
        }
    }

    fun loadChartData(duration: ChartDuration) {
        viewModelScope.launch {
            _chartState.value = Result.Loading
            _selectedDuration.value = duration

            // Always fetch chart in USD
            when (val result = marketRepository.getMarketChart(tokenId, duration, SupportedCurrency.USD)) {
                is Result.Success -> {
                    _chartState.value = Result.Success(result.data)
                }

                is Result.Error -> {
                    _chartState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {}
            }
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            _newsState.value = Result.Loading

            // Use tokenId (slug) for news search which works best with CoinStats
            val searchQuery = tokenId

            when (val result = marketRepository.getCoinNews(searchQuery)) {
                is Result.Success -> {
                    _newsState.value = Result.Success(result.data)
                    hasLoadedNews = true
                }

                is Result.Error -> {
                    _newsState.value = Result.Error(result.message, result.throwable)
                }

                Result.Loading -> {}
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
        hasLoadedNews = false 
        loadTokenDetails()
        loadChartData(_selectedDuration.value)
    }
}