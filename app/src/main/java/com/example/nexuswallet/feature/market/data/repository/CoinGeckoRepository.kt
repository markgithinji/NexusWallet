package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.RetrofitClient
import com.example.nexuswallet.feature.market.domain.Token

class CoinGeckoRepository(
    private val coinGeckoApi: CoinGeckoApi = RetrofitClient.coinGeckoApi
) {
    suspend fun getTopCryptocurrencies(): List<Token> {
        return try {
            val response = coinGeckoApi.getMarkets(
                vsCurrency = "usd",
                order = "market_cap_desc",
                perPage = 50,
                page = 1,
                sparkline = true
            )
            response.map { it.toToken() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}