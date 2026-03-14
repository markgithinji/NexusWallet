package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.market.data.model.toChartData
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toTokenDetail
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
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
        return SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 100,
                page = 1,
                sparkline = false
            ).let { response ->
                if (response.isEmpty()) {
                    emptyMap()
                } else {
                    response.associate { coin ->
                        coin.id to (coin.priceChangePercentage24h ?: 0.0)
                    }
                }
            }
        }
    }

    override suspend fun getTokenDetails(tokenId: String): Result<TokenDetail> {
        return SafeApiCall.make {
            coinGeckoApi.getCoinDetails(id = tokenId).toTokenDetail()
        }
    }

    override suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration
    ): Result<ChartData> {
        return SafeApiCall.make {
            coinGeckoApi.getMarketChart(
                id = tokenId,
                days = duration.days
            ).toChartData()
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

        return SafeApiCall.make {
            cryptoPanicApi.getNews(
                authToken = BuildConfig.CRYPTOPANIC_API_KEY,
                public = true,
                currencies = currencyCode,
                kind = "news",
                regions = "en"
            ).results
                .map { it.toNewsArticle() }
                .take(5)
        }
    }
}