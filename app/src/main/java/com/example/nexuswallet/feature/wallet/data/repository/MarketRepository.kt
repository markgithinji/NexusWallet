package com.example.nexuswallet.feature.wallet.data.repository

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.model.toChartData
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toTokenDetail
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.data.remote.MarketCapPoint
import com.example.nexuswallet.feature.market.data.remote.PricePoint
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.market.data.remote.VolumePoint
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import retrofit2.HttpException
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi,
    private val cryptoPanicApi: CryptoPanicApi
) {

    private var requestCount = 0
    private val maxRequests = 100

    /**
     * Get latest price percentages for coins
     */
    suspend fun getLatestPricePercentages(): Map<String, Double> {
        Log.d("MarketRepo", "========== FETCHING PERCENTAGES ==========")

        val result = SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 100,
                page = 1,
                sparkline = false
            )
        }

        return when (result) {
            is Result.Success -> {
                val response = result.data
                Log.d("MarketRepo", "API Response received: ${response.size} coins")

                if (response.isEmpty()) {
                    Log.e("MarketRepo", "Response is empty!")
                    emptyMap()
                } else {
                    // Log first 5 coins for debugging
                    response.take(5).forEachIndexed { index, coin ->
                        Log.d("MarketRepo", "  [$index] ID: ${coin.id}, Symbol: ${coin.symbol}, 24h%: ${coin.priceChangePercentage24h}")
                    }

                    // Check for Bitcoin specifically
                    response.find { it.id == "bitcoin" }?.let {
                        Log.d("MarketRepo", "Bitcoin found! 24h%: ${it.priceChangePercentage24h}")
                    }

                    val percentages = response.associate { coin ->
                        coin.id to (coin.priceChangePercentage24h ?: 0.0)
                    }

                    Log.d("MarketRepo", "Created percentages map with ${percentages.size} entries")
                    percentages
                }
            }
            is Result.Error -> {
                Log.e("MarketRepo", "Error fetching percentages: ${result.message}")
                emptyMap()
            }
            Result.Loading -> emptyMap() // Won't happen here
        }
    }

    suspend fun getTokenDetails(tokenId: String): TokenDetail? {
        Log.d("MarketRepo", "Fetching details for tokenId: '$tokenId'")

        val result = SafeApiCall.make {
            coinGeckoApi.getCoinDetails(id = tokenId)
        }

        return when (result) {
            is Result.Success -> {
                result.data.toTokenDetail()
            }
            is Result.Error -> {
                Log.e("MarketRepo", "Error fetching token details: ${result.message}")
                null
            }
            Result.Loading -> null
        }
    }

    suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration
    ): ChartData? {
        Log.d("MarketRepo", "Fetching chart for $tokenId, duration: ${duration.days} days")

        val result = SafeApiCall.make {
            coinGeckoApi.getMarketChart(
                id = tokenId,
                days = duration.days
            )
        }

        return when (result) {
            is Result.Success -> {
                result.data.toChartData()
            }
            is Result.Error -> {
                Log.e("MarketRepo", "Error fetching chart: ${result.message}")
                null
            }
            Result.Loading -> null
        }
    }

    suspend fun getCoinNews(coinName: String): List<NewsArticle> {
        // Check rate limit
        requestCount++
        if (requestCount > maxRequests) {
            Log.e("MarketRepo", "Monthly request limit exceeded (100/month)")
            return emptyList()
        }

        Log.d("MarketRepo", "Fetching news for: $coinName (Request #$requestCount)")

        // Map coin names to their currency codes
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

        val result = SafeApiCall.make {
            cryptoPanicApi.getNews(
                authToken = BuildConfig.CRYPTOPANIC_API_KEY,
                public = true,
                currencies = currencyCode,
                kind = "news",
                regions = "en"
            )
        }

        return when (result) {
            is Result.Success -> {
                val articles = result.data.results
                    .map { it.toNewsArticle() }
                    .take(5)

                Log.d("MarketRepo", "Got ${articles.size} articles")
                articles
            }
            is Result.Error -> {
                Log.e("MarketRepo", "Error fetching news: ${result.message}")

                // Check for specific HTTP errors
                if (result.throwable is HttpException) {
                    handleHttpError(result.throwable.code())
                }

                emptyList()
            }
            Result.Loading -> emptyList()
        }
    }

    private fun handleHttpError(code: Int) {
        when (code) {
            401 -> Log.e("MarketRepo", "Invalid API key")
            403 -> Log.e("MarketRepo", "Rate limit exceeded")
            429 -> Log.e("MarketRepo", "Too many requests")
            500 -> Log.e("MarketRepo", "Server error - free plan limitations")
        }
    }
}