package com.example.nexuswallet.feature.market.data.repository

import android.util.Log
import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.domain.Token
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall

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
        val result = SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = perPage,
                page = page,
                sparkline = true
            )
        }

        return when (result) {
            is Result.Success -> {
                val tokens = result.data.map { it.toToken() }
                Result.Success(tokens)
            }
            is Result.Error -> Result.Error(result.message, result.throwable)
            Result.Loading -> Result.Loading
        }
    }
}