package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.logging.Logger
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
    private val cryptoPanicApi: CryptoPanicApi,
    private val logger: Logger
) : MarketRepository {

    private val tag = "MarketRepo"
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
        logger.d(tag, "Fetching news for coin: $coinName")
        requestCount++
        if (requestCount > maxRequests) {
            logger.w(tag, "News request limit exceeded: $requestCount/$maxRequests")
            return Result.Error("Monthly request limit exceeded (100/month)")
        }

        val currencyCode = when (val name = coinName.lowercase()) {
            "bitcoin" -> "BTC"
            "ethereum" -> "ETH"
            "solana" -> "SOL"
            "cardano" -> "ADA"
            "binance coin", "binancecoin" -> "BNB"
            "ripple", "xrp" -> "XRP"
            "dogecoin" -> "DOGE"
            "polkadot" -> "DOT"
            "polygon" -> "MATIC"
            "avalanche" -> "AVAX"
            else -> {
                logger.w(tag, "No currency code found for name: $name")
                null
            }
        }

        logger.d(tag, "Resolved currency code: $currencyCode")

        val result = SafeApiCall.make {
            val response = cryptoPanicApi.getNews(
                public = true,
                currencies = currencyCode,
                kind = "news",
                regions = "en"
            )
            
            logger.d(tag, "CryptoPanic response received with ${response.results.size} items")
            
            response.results
                .map { it.toNewsArticle() }
                .take(5)
        }

        if (result is Result.Error) {
            logger.e(tag, "Error fetching news from API: ${result.message}", result.throwable)
        }

        return result
    }
}
