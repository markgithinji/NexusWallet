package com.example.nexuswallet.feature.market.data.remote.model.coingecko

import com.example.nexuswallet.feature.market.data.remote.model.coingecko.Sparkline7dResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoinGeckoTokenDto(
    val id: String,
    val symbol: String,
    val name: String,
    @SerialName("current_price")
    val currentPrice: Double? = null,
    @SerialName("market_cap")
    val marketCap: Double? = null,
    @SerialName("market_cap_rank")
    val marketCapRank: Int? = null,
    @SerialName("price_change_24h")
    val priceChange24h: Double? = null,
    @SerialName("price_change_percentage_24h")
    val priceChangePercentage24h: Double? = null,
    val image: String? = null,
    @SerialName("sparkline_in_7d")
    val sparklineIn7d: Sparkline7dResponse? = null
)