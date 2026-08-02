package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.market.data.model.toToken
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.domain.model.Token
import com.example.nexuswallet.feature.market.domain.repository.CoinGeckoRepository
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoinGeckoRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinGeckoApi
) : CoinGeckoRepository {

    override suspend fun getTopCryptocurrencies(
        perPage: Int,
        page: Int,
        currency: SupportedCurrency
    ): Result<List<Token>> {
        return SafeApiCall.make {
            coinGeckoApi.getMarkets(
                vsCurrency = currency.code.lowercase(),
                order = "market_cap_desc",
                perPage = perPage,
                page = page,
                sparkline = true
            ).map { it.toToken() }
        }
    }
}
