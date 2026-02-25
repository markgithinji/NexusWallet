package com.example.nexuswallet.feature.wallet.data.repository

import android.util.Log
import com.example.nexuswallet.BuildConfig
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
        return try {
            Log.d("MarketRepo", "========== FETCHING PERCENTAGES ==========")
            Log.d("MarketRepo", "Calling CoinGecko API...")

            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 100,
                page = 1,
                sparkline = false
            )

            Log.d("MarketRepo", "API Response received")
            Log.d("MarketRepo", "Total coins in response: ${response.size}")

            if (response.isEmpty()) {
                Log.e("MarketRepo", "Response is empty!")
                return emptyMap()
            }

            // Log first 5 coins to see structure
            Log.d("MarketRepo", "First 5 coins from API:")
            response.take(5).forEachIndexed { index, coin ->
                Log.d("MarketRepo", "  [$index] ID: ${coin.id}, Symbol: ${coin.symbol}, 24h%: ${coin.priceChangePercentage24h}")
            }

            // Specifically check for Bitcoin
            val bitcoin = response.find { it.id == "bitcoin" }
            if (bitcoin != null) {
                Log.d("MarketRepo", "Bitcoin found! 24h%: ${bitcoin.priceChangePercentage24h}")
            } else {
                Log.e("MarketRepo", "Bitcoin not found in response!")
            }

            val percentages = response.associate { coin ->
                coin.id to (coin.priceChangePercentage24h ?: 0.0)
            }

            Log.d("MarketRepo", "Created percentages map with ${percentages.size} entries")
            Log.d("MarketRepo", "Map keys (first 10): ${percentages.keys.take(10).joinToString()}")
            Log.d("MarketRepo", "========== FETCH COMPLETE ==========")

            percentages
        } catch (e: Exception) {
            Log.e("MarketRepo", "Error fetching percentages: ${e.message}")
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun getTokenDetails(tokenId: String): TokenDetail? {
        return try {
            Log.d("MarketRepo", "Fetching details for tokenId: '$tokenId'")
            val response = coinGeckoApi.getCoinDetails(id = tokenId)

            response.toTokenDetail()

        } catch (e: Exception) {
            Log.e("MarketRepo", "Error fetching token details: ${e.message}")
            null
        }
    }

    suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration
    ): ChartData? {
        return try {
            Log.d("MarketRepo", "Fetching chart for $tokenId, duration: ${duration.days} days")

            val response = coinGeckoApi.getMarketChart(
                id = tokenId,
                days = duration.days
            )

            response.toChartData()

        } catch (e: Exception) {
            Log.e("MarketRepo", "Error fetching chart: ${e.message}")
            null
        }
    }

    suspend fun getCoinNews(coinName: String): List<NewsArticle> {
        // Check rate limit
        requestCount++
        if (requestCount > maxRequests) {
            Log.e("MarketRepo", "Monthly request limit exceeded (100/month)")
            return emptyList()
        }

        return try {
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
                else -> null // Don't filter if no currency code
            }

            val response = cryptoPanicApi.getNews(
                authToken = BuildConfig.CRYPTOPANIC_API_KEY,
                public = true,
                currencies = currencyCode,
                kind = "news",
                regions = "en"
            )

            val articles = response.results
                .map { it.toNewsArticle() }
                .take(5)

            Log.d("MarketRepo", "Got ${articles.size} articles")
            articles

        } catch (e: retrofit2.HttpException) {
            Log.e("MarketRepo", "HTTP ${e.code()}: ${e.message()}")
            handleHttpError(e.code())
            emptyList()
        } catch (e: Exception) {
            Log.e("MarketRepo", "Error fetching news: ${e.message}")
            emptyList()
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