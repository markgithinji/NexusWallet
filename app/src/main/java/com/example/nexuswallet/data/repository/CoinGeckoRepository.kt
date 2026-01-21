package com.example.nexuswallet.data.repository

import com.example.nexuswallet.data.mapper.toToken
import com.example.nexuswallet.data.remote.CoinGeckoApi
import com.example.nexuswallet.data.remote.RetrofitClient
import com.example.nexuswallet.domain.Token

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