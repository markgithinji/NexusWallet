package com.example.nexuswallet.feature.wallet.data.repository

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.model.toNewsArticle
import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocket
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.data.remote.MarketCapPoint
import com.example.nexuswallet.feature.market.data.remote.PricePoint
import com.example.nexuswallet.feature.market.data.remote.RetrofitClient
import com.example.nexuswallet.feature.market.data.remote.TokenDetail
import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import com.example.nexuswallet.feature.market.data.remote.VolumePoint
import com.example.nexuswallet.feature.market.domain.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi = RetrofitClient.coinGeckoApi,
    private val cryptoPanicApi: CryptoPanicApi = RetrofitClient.cryptoPanicApi
) {

    private var requestCount = 0
    private val maxRequests = 100
    /**
     * Get latest price percentages for coins
     */
    suspend fun getLatestPricePercentages(): Map<String, Double> {
        return try {
            Log.d(" MarketRepo", "========== FETCHING PERCENTAGES ==========")
            Log.d(" MarketRepo", "Calling CoinGecko API...")

            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 100,
                page = 1,
                sparkline = false
            )

            Log.d(" MarketRepo", " API Response received")
            Log.d(" MarketRepo", "Total coins in response: ${response.size}")

            if (response.isEmpty()) {
                Log.e(" MarketRepo", " Response is empty!")
                return emptyMap()
            }

            // Log first 5 coins to see structure
            Log.d(" MarketRepo", "First 5 coins from API:")
            response.take(5).forEachIndexed { index, coin ->
                Log.d(" MarketRepo", "  [$index] ID: ${coin.id}, Symbol: ${coin.symbol}, 24h%: ${coin.priceChangePercentage24h}")
            }

            // Specifically check for Bitcoin
            val bitcoin = response.find { it.id == "bitcoin" }
            if (bitcoin != null) {
                Log.d(" MarketRepo", " Bitcoin found! 24h%: ${bitcoin.priceChangePercentage24h}")
            } else {
                Log.e(" MarketRepo", " Bitcoin not found in response!")
            }

            val percentages = response.associate { coin ->
                coin.id to (coin.priceChangePercentage24h ?: 0.0)
            }

            Log.d(" MarketRepo", "Created percentages map with ${percentages.size} entries")
            Log.d(" MarketRepo", "Map keys (first 10): ${percentages.keys.take(10).joinToString()}")
            Log.d(" MarketRepo", "========== FETCH COMPLETE ==========")

            percentages
        } catch (e: Exception) {
            Log.e(" MarketRepo", " Error fetching percentages: ${e.message}")
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Get percentage for specific coin ID
     */
    suspend fun getPricePercentageForCoin(coinId: String): Double? {
        return try {
            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                perPage = 1,
                page = 1,
                sparkline = false
            ).firstOrNull { it.id == coinId }
            response?.priceChangePercentage24h
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTokenDetails(tokenId: String): TokenDetail? {
        return try {
            Log.d(" MarketRepo", "Fetching details for tokenId: '$tokenId'")
            val response = coinGeckoApi.getCoinDetails(id = tokenId)

            TokenDetail(
                id = response.id,
                symbol = response.symbol,
                name = response.name,
                image = response.image.large,
                currentPrice = response.market_data.currentPrice["usd"] ?: 0.0,
                priceChange24h = response.market_data.priceChange24h,
                priceChangePercentage24h = response.market_data.priceChangePercentage24h,
                marketCap = response.market_data.marketCap["usd"] ?: 0.0,
                marketCapRank = response.market_data.marketCapRank ?: 0,
                fullyDilutedValuation = null,
                totalVolume = response.market_data.totalVolume["usd"] ?: 0.0,
                high24h = response.market_data.high24h["usd"] ?: 0.0,
                low24h = response.market_data.low24h["usd"] ?: 0.0,
                circulatingSupply = response.market_data.circulatingSupply,
                totalSupply = response.market_data.totalSupply,
                maxSupply = response.market_data.maxSupply,
                ath = response.market_data.ath["usd"] ?: 0.0,
                athChangePercentage = response.market_data.athChangePercentage["usd"] ?: 0.0,
                athDate = response.market_data.athDate["usd"] ?: "",
                atl = response.market_data.atl["usd"] ?: 0.0,
                atlChangePercentage = response.market_data.atlChangePercentage["usd"] ?: 0.0,
                atlDate = response.market_data.atlDate["usd"] ?: "",
                sparklineIn7d = response.market_data.sparkline7d?.price,
                description = response.description?.get("en")
            )
        } catch (e: Exception) {
            Log.e("🔍 MarketRepo", "Error fetching token details: ${e.message}")
            null
        }
    }

    suspend fun getMarketChart(
        tokenId: String,
        duration: ChartDuration
    ): ChartData? {
        return try {
            Log.d(" MarketRepo", "Fetching chart for $tokenId, duration: ${duration.days} days")

            val response = coinGeckoApi.getMarketChart(
                id = tokenId,
                days = duration.days
            )

            ChartData(
                prices = response.prices.map { (timestamp, price) ->
                    PricePoint(timestamp = timestamp.toLong(), price = price)
                },
                marketCaps = response.market_caps.map { (timestamp, cap) ->
                    MarketCapPoint(timestamp = timestamp.toLong(), marketCap = cap)
                },
                volumes = response.total_volumes.map { (timestamp, volume) ->
                    VolumePoint(timestamp = timestamp.toLong(), volume = volume)
                }
            )
        } catch (e: Exception) {
            Log.e(" MarketRepo", "Error fetching chart: ${e.message}")
            null
        }
    }

    suspend fun getCoinNews(coinName: String): List<NewsArticle> {
        // Check rate limit
        requestCount++
        if (requestCount > maxRequests) {
            Log.e("NewsRepo", "Monthly request limit exceeded (100/month)")
            return emptyList()
        }

        return try {
            Log.d(" NewsRepo", "Fetching news for: $coinName (Request #$requestCount)")

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
                currencies = currencyCode,  // Filter by coin if available
                kind = "news",
                regions = "en"
            )

            // Free plan returns 20 items max
            val articles = response.results
                .map { it.toNewsArticle() }
                .take(5) // Show only first 5 in UI

            Log.d(" NewsRepo", "Got ${articles.size} articles")
            articles

        } catch (e: retrofit2.HttpException) {
            Log.e(" NewsRepo", "HTTP ${e.code()}: ${e.message()}")
            handleHttpError(e.code())
            emptyList()
        } catch (e: Exception) {
            Log.e(" NewsRepo", "Error fetching news: ${e.message}")
            emptyList()
        }
    }

    private fun handleHttpError(code: Int) {
        when (code) {
            401 -> Log.e(" NewsRepo", "Invalid API key")
            403 -> Log.e(" NewsRepo", "Rate limit exceeded")
            429 -> Log.e(" NewsRepo", "Too many requests")
            500 -> Log.e(" NewsRepo", "Server error - free plan limitations")
        }
    }
}