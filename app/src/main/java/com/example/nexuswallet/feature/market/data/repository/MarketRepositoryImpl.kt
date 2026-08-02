package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.market.data.model.toChartData
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toTokenDetail
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CoinStatsApi
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi,
    private val coinStatsApi: CoinStatsApi
) : MarketRepository {

    override suspend fun getLatestPricePercentages(currency: SupportedCurrency): Result<Map<String, Double>> {
        return SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = currency.code.lowercase(),
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

    override suspend fun getTokenDetails(tokenId: String, currency: SupportedCurrency): Result<TokenDetail> {
        return SafeApiCall.make {
            coinGeckoApi.getCoinDetails(id = tokenId).toTokenDetail(currency)
        }
    }

    override suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration,
        currency: SupportedCurrency
    ): Result<ChartData> {
        return SafeApiCall.make {
            coinGeckoApi.getMarketChart(
                id = tokenId,
                vsCurrency = currency.code.lowercase(),
                days = duration.days
            ).toChartData()
        }
    }

    override suspend fun getCoinNews(coinNameOrSymbol: String): Result<List<NewsArticle>> {
        val coinId = resolveCoinId(coinNameOrSymbol)

        return SafeApiCall.make {
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
    }

    override suspend fun getSimplePrices(
        ids: List<String>,
        currency: SupportedCurrency
    ): Result<Map<String, Double>> {
        return SafeApiCall.make {
            val currencyCode = currency.code.lowercase()
            val response = coinGeckoApi.getSimplePrice(
                ids = ids.joinToString(","),
                vsCurrencies = currencyCode
            )
            response.mapValues { it.value[currencyCode] ?: 0.0 }
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
