package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.data.model.toChartData
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toTokenDetail
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CoinStatsApi
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
    private val coinStatsApi: CoinStatsApi,
    private val logger: Logger
) : MarketRepository {

    private val tag = "MarketRepo"

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

    override suspend fun getCoinNews(coinNameOrSymbol: String): Result<List<NewsArticle>> {
        val coinId = resolveCoinId(coinNameOrSymbol)

        val result = SafeApiCall.make {
            val response = coinStatsApi.getNews(
                coinId = coinId,
                type = "latest",
                limit = 10
            )
            
            response.news
                .filter { article ->
                    // Strict client-side filtering by coinId (slug)
                    coinId == null || article.relatedCoins?.contains(coinId) == true
                }
                .map { it.toNewsArticle() }
        }

        if (result is Result.Error) {
            logger.e(tag, "Error fetching news from API: ${result.message}", result.throwable)
        }

        return result
    }

    override suspend fun getSimplePrices(ids: List<String>): Result<Map<String, Double>> {
        return SafeApiCall.make {
            val response = coinGeckoApi.getSimplePrice(
                ids = ids.joinToString(","),
                vsCurrencies = "usd"
            )
            response.mapValues { it.value["usd"] ?: 0.0 }
        }
    }

    private fun resolveCoinId(input: String): String? {
        val normalized = input.trim().lowercase()
        
        // Map common names and symbols to CoinStats slugs
        return when (normalized) {
            "bitcoin", "btc" -> "bitcoin"
            "ethereum", "eth" -> "ethereum"
            "solana", "sol" -> "solana"
            "cardano", "ada" -> "cardano"
            "binance coin", "binancecoin", "bnb" -> "binance-coin"
            "ripple", "xrp" -> "ripple"
            "dogecoin", "doge" -> "dogecoin"
            "polkadot", "dot" -> "polkadot"
            "polygon", "matic" -> "polygon"
            "avalanche", "avax" -> "avalanche"
            "chainlink", "link" -> "chainlink"
            "uniswap", "uni" -> "uniswap"
            "litecoin", "ltc" -> "litecoin"
            else -> {
                // Return as is, might be a valid slug
                normalized
            }
        }
    }
}
