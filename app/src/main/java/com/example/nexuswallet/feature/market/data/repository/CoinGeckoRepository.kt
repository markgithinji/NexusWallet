package com.example.nexuswallet.feature.market.data.repository

import android.util.Log
import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.domain.Token
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result

@Singleton
class CoinGeckoRepository @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi
) {
    /**
     * Get top cryptocurrencies with pagination support
     * @param perPage Number of results per page (max 250)
     * @param page Page number to fetch
     */
    suspend fun getTopCryptocurrencies(
        perPage: Int = 100,
        page: Int = 1
    ): Result<List<Token>> {
        return try {
            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = perPage,
                page = page,
                sparkline = true
            )
            Log.d("CoinGeckoRepo", "API Response (page $page): ${response.size} tokens")
            Result.Success(response.map { it.toToken() })
        } catch (e: Exception) {
            Log.e("CoinGeckoRepo", "Error fetching page $page: ${e.message}", e)
            Result.Error("Failed to load page $page: ${e.message}", e)
        }
    }
}