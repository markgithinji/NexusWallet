package com.example.nexuswallet.feature.market.data.model

import com.example.nexuswallet.feature.market.data.remote.ImageUrls
import com.example.nexuswallet.feature.market.data.remote.MarketData
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
    val sparklineIn7d: SparklineDto? = null
)

@Serializable
data class SparklineDto(
    val price: List<Double>
)

@Serializable
data class CryptoPanicResponse(
    val next: String? = null,
    val previous: String? = null,
    val results: List<CryptoPanicPost>
)

@Serializable
data class CryptoPanicPost(
    val title: String,
    val description: String? = null,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("created_at")
    val createdAt: String,
    val kind: String
)

@Serializable
data class NewsArticle(
    val title: String,
    val summary: String?,
    val publishedAt: String,
    val source: String = "CryptoPanic", // Default source
    val url: String = "", // No URL in free plan
    val image: String? = null
)

@Serializable
data class MarketChartResponse(
    val prices: List<List<Double>>,
    val market_caps: List<List<Double>>,
    val total_volumes: List<List<Double>>
)

@Serializable
data class CoinDetailResponse(
    val id: String,
    val symbol: String,
    val name: String,
    val image: ImageUrls,
    val market_data: MarketData,
    val description: Map<String, String>? = null
)
