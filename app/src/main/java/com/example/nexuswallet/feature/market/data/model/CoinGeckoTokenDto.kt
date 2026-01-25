package com.example.nexuswallet.feature.market.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoinGeckoTokenDto(
    val id: String,
    val symbol: String,
    val name: String,
    @SerialName("current_price")
    val currentPrice: Double,
    @SerialName("market_cap")
    val marketCap: Double,
    @SerialName("market_cap_rank")
    val marketCapRank: Int,
    @SerialName("price_change_24h")
    val priceChange24h: Double,
    @SerialName("price_change_percentage_24h")
    val priceChangePercentage24h: Double,
    val image: String,
    @SerialName("sparkline_in_7d")
    val sparklineIn7d: SparklineDto? = null
)

@Serializable
data class SparklineDto(
    val price: List<Double>
)