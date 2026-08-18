package com.example.nexuswallet.feature.market.domain.repository

import com.example.nexuswallet.feature.market.domain.model.AssetPriceData
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency

interface MarketRepository {
    suspend fun getLatestPricePercentages(currency: SupportedCurrency = SupportedCurrency.USD): Result<Map<String, Double>>
    suspend fun getTokenDetails(tokenId: String, currency: SupportedCurrency = SupportedCurrency.USD): Result<TokenDetail>
    suspend fun getMarketChart(tokenId: String, duration: ChartDuration, currency: SupportedCurrency = SupportedCurrency.USD): Result<ChartData>
    suspend fun getCoinNews(coinNameOrSymbol: String): Result<List<NewsArticle>>
    suspend fun getSimplePrices(ids: List<String>, currency: SupportedCurrency = SupportedCurrency.USD): Result<Map<String, AssetPriceData>>
}
