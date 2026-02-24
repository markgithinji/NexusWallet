package com.example.nexuswallet.feature.market.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

data class ChartData(
    val prices: List<PricePoint>,
    val marketCaps: List<MarketCapPoint>,
    val volumes: List<VolumePoint>
)

data class PricePoint(
    val timestamp: Long,
    val price: Double,
    val date: Date = Date(timestamp)
)

data class MarketCapPoint(
    val timestamp: Long,
    val marketCap: Double
)

data class VolumePoint(
    val timestamp: Long,
    val volume: Double
)

data class TokenDetail(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    val currentPrice: Double,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double,
    val marketCap: Double,
    val marketCapRank: Int,
    val fullyDilutedValuation: Double?,
    val totalVolume: Double,
    val high24h: Double,
    val low24h: Double,
    val circulatingSupply: Double,
    val totalSupply: Double?,
    val maxSupply: Double?,
    val ath: Double,
    val athChangePercentage: Double,
    val athDate: String,
    val atl: Double,
    val atlChangePercentage: Double,
    val atlDate: String,
    val sparklineIn7d: List<Double>?,
    val description: String?
)

enum class ChartDuration(val days: String, val label: String) {
    ONE_DAY("1", "24H"),
    ONE_WEEK("7", "7D"),
    ONE_MONTH("30", "30D"),
    THREE_MONTHS("90", "90D"),
    ONE_YEAR("365", "1Y"),
    MAX("max", "All")
}

@Serializable
data class ImageUrls(
    val thumb: String,
    val small: String,
    val large: String
)

@Serializable
data class MarketData(
    @SerialName("current_price")
    val currentPrice: Map<String, Double>,
    @SerialName("market_cap")
    val marketCap: Map<String, Double>,
    @SerialName("market_cap_rank")
    val marketCapRank: Int?,
    @SerialName("total_volume")
    val totalVolume: Map<String, Double>,
    @SerialName("high_24h")
    val high24h: Map<String, Double>,
    @SerialName("low_24h")
    val low24h: Map<String, Double>,
    @SerialName("price_change_24h")
    val priceChange24h: Double,
    @SerialName("price_change_percentage_24h")
    val priceChangePercentage24h: Double,
    @SerialName("circulating_supply")
    val circulatingSupply: Double,
    @SerialName("total_supply")
    val totalSupply: Double?,
    @SerialName("max_supply")
    val maxSupply: Double?,
    @SerialName("ath")
    val ath: Map<String, Double>,
    @SerialName("ath_change_percentage")
    val athChangePercentage: Map<String, Double>,
    @SerialName("ath_date")
    val athDate: Map<String, String>,
    @SerialName("atl")
    val atl: Map<String, Double>,
    @SerialName("atl_change_percentage")
    val atlChangePercentage: Map<String, Double>,
    @SerialName("atl_date")
    val atlDate: Map<String, String>,
    @SerialName("sparkline_7d")
    val sparkline7d: Sparkline7d? = null
)

@Serializable
data class Sparkline7d(
    val price: List<Double>
)

@Serializable
data class TokenPriceUpdate(
    val tokenId: String,
    val price: Double,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double
)

enum class ConnectionState {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}