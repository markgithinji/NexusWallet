package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.model.Token

interface CoinGeckoRepository {
    suspend fun getTopCryptocurrencies(
        perPage: Int,
        page: Int
    ): Result<List<Token>>
}