package com.example.nexuswallet.feature.market.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import javax.inject.Inject

class GetSimplePricesUseCase @Inject constructor(
    private val marketRepository: MarketRepository
) {
    // Map wallet symbols to CoinGecko IDs (Business logic for asset identification)
    private val symbolToId = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "USDC" to "usd-coin",
        "USDT" to "tether"
    )

    suspend operator fun invoke(
        symbols: List<String>,
        vsCurrency: String = "usd"
    ): Result<Map<String, Double>> {
        val ids = symbols.mapNotNull { symbolToId[it.uppercase()] }.distinct()
        if (ids.isEmpty()) return Result.Success(emptyMap())

        return when (val marketResult = marketRepository.getSimplePrices(ids, vsCurrency)) {
            is Result.Success -> {
                val pricesBySymbol = symbols.associateWith { symbol ->
                    val id = symbolToId[symbol.uppercase()]
                    marketResult.data[id] ?: 0.0
                }
                Result.Success(pricesBySymbol)
            }
            is Result.Error -> marketResult
            Result.Loading -> Result.Loading
        }
    }
}
