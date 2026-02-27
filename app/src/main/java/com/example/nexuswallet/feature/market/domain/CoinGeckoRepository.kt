package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.coin.Result

interface CoinGeckoRepository {
    suspend fun getTopCryptocurrencies(
        perPage: Int,
        page: Int
    ): Result<List<Token>>
}