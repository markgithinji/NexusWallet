package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.coin.Result

interface MarketRepository {
    suspend fun getLatestPricePercentages(): Result<Map<String, Double>>
    suspend fun getTokenDetails(tokenId: String): Result<TokenDetail>
    suspend fun getMarketChart(tokenId: String, duration: ChartDuration): Result<ChartData>
    suspend fun getCoinNews(coinName: String): Result<List<NewsArticle>>
}