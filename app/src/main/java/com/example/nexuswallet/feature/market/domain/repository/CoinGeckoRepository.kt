package com.example.nexuswallet.feature.market.domain.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.model.Token
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency

interface CoinGeckoRepository {
    suspend fun getTopCryptocurrencies(
        perPage: Int,
        page: Int,
        currency: SupportedCurrency = SupportedCurrency.USD
    ): Result<List<Token>>
}
