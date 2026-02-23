package com.example.nexuswallet.feature.market.data.repository

import android.util.Log
import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.RetrofitClient
import com.example.nexuswallet.feature.market.domain.Token

class CoinGeckoRepository(
    private val coinGeckoApi: CoinGeckoApi = RetrofitClient.coinGeckoApi
) {

    /**
     * Get top cryptocurrencies with pagination support
     * @param perPage Number of results per page (max 250)
     * @param page Page number to fetch
     */
    suspend fun getTopCryptocurrencies(
        perPage: Int = 100,
        page: Int = 1
    ): List<Token> {
        return try {
            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = perPage,
                page = page,
                sparkline = true
            )
            Log.d("CoinGeckoRepo", "API Response (page $page): ${response.size} tokens")
            response.map { it.toToken() }
        } catch (e: Exception) {
            Log.e("CoinGeckoRepo", "Error fetching page $page: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get all top cryptocurrencies (up to 300 coins)
     */
    suspend fun getAllTopCryptocurrencies(): List<Token> {
        val allTokens = mutableListOf<Token>()

        // Fetch first 3 pages (100 coins each = 300 coins)
        for (page in 1..3) {
            val tokens = getTopCryptocurrencies(perPage = 100, page = page)
            if (tokens.isNotEmpty()) {
                allTokens.addAll(tokens)
                Log.d("CoinGeckoRepo", "Loaded page $page: ${tokens.size} tokens, total: ${allTokens.size}")
            } else {
                break // Stop if no more tokens
            }
            // Small delay to avoid rate limiting
            if (page < 3) {
                kotlinx.coroutines.delay(1000)
            }
        }

        Log.d("CoinGeckoRepo", "Total tokens loaded: ${allTokens.size}")
        return allTokens
    }

    /**
     * Get specific range of cryptocurrencies
     * @param startPage Starting page number
     * @param endPage Ending page number (inclusive)
     * @param perPage Number of results per page
     */
    suspend fun getCryptocurrencyRange(
        startPage: Int = 1,
        endPage: Int = 3,
        perPage: Int = 100
    ): List<Token> {
        val allTokens = mutableListOf<Token>()

        for (page in startPage..endPage) {
            val tokens = getTopCryptocurrencies(perPage = perPage, page = page)
            if (tokens.isNotEmpty()) {
                allTokens.addAll(tokens)
            } else {
                break
            }
            // Add delay between requests to respect rate limits
            if (page < endPage) {
                kotlinx.coroutines.delay(1000)
            }
        }

        return allTokens
    }
}