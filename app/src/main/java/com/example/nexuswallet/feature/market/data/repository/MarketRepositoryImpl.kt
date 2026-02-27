package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.model.toChartData
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toTokenDetail
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.market.domain.MarketRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi,
    private val cryptoPanicApi: CryptoPanicApi
) : MarketRepository {

    private var requestCount = 0
    private val maxRequests = 100

    override suspend fun getLatestPricePercentages(): Result<Map<String, Double>> {
        val apiResult = SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 100,
                page = 1,
                sparkline = false
            )
        }

        return when (apiResult) {
            is Result.Success -> {
                val response = apiResult.data
                val percentages = if (response.isEmpty()) {
                    emptyMap()
                } else {
                    response.associate { coin ->
                        coin.id to (coin.priceChangePercentage24h ?: 0.0)
                    }
                }
                Result.Success(percentages)
            }

            is Result.Error -> Result.Error(apiResult.message, apiResult.throwable)
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getTokenDetails(tokenId: String): Result<TokenDetail> {
        val apiResult = SafeApiCall.make {
            coinGeckoApi.getCoinDetails(id = tokenId)
        }

        return when (apiResult) {
            is Result.Success -> Result.Success(apiResult.data.toTokenDetail())
            is Result.Error -> Result.Error(apiResult.message, apiResult.throwable)
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration
    ): Result<ChartData> {
        val apiResult = SafeApiCall.make {
            coinGeckoApi.getMarketChart(
                id = tokenId,
                days = duration.days
            )
        }

        return when (apiResult) {
            is Result.Success -> Result.Success(apiResult.data.toChartData())
            is Result.Error -> Result.Error(apiResult.message, apiResult.throwable)
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun getCoinNews(coinName: String): Result<List<NewsArticle>> {
        requestCount++
        if (requestCount > maxRequests) {
            return Result.Error("Monthly request limit exceeded (100/month)")
        }

        val currencyCode = when (coinName.lowercase()) {
            "bitcoin" -> "BTC"
            "ethereum" -> "ETH"
            "solana" -> "SOL"
            "cardano" -> "ADA"
            "binance coin" -> "BNB"
            "ripple", "xrp" -> "XRP"
            "dogecoin" -> "DOGE"
            "polkadot" -> "DOT"
            "polygon" -> "MATIC"
            "avalanche" -> "AVAX"
            else -> null
        }

        val apiResult = SafeApiCall.make {
            cryptoPanicApi.getNews(
                authToken = BuildConfig.CRYPTOPANIC_API_KEY,
                public = true,
                currencies = currencyCode,
                kind = "news",
                regions = "en"
            )
        }

        return when (apiResult) {
            is Result.Success -> {
                val articles = apiResult.data.results
                    .map { it.toNewsArticle() }
                    .take(5)
                Result.Success(articles)
            }

            is Result.Error -> Result.Error(apiResult.message, apiResult.throwable)
            Result.Loading -> Result.Loading
        }
    }
}