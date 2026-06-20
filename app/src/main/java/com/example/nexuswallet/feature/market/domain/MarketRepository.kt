package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.core.util.Result

interface MarketRepository {
    suspend fun getLatestPricePercentages(): Result<Map<String, Double>>
    suspend fun getTokenDetails(tokenId: String): Result<TokenDetail>
    suspend fun getMarketChart(tokenId: String, duration: ChartDuration): Result<ChartData>
    suspend fun getCoinNews(coinNameOrSymbol: String): Result<List<NewsArticle>>
    suspend fun getSimplePrices(ids: List<String>, vsCurrency: String = "usd"): Result<Map<String, Double>>
}