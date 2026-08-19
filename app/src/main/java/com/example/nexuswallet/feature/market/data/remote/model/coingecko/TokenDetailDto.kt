package com.example.nexuswallet.feature.market.data.remote.model.coingecko

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenDetailDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: ImageUrlsResponse,
    @SerialName("market_data")
    val market_data: MarketDataDto,
    val description: Map<String, String>? = null,
    val categories: List<String>? = null,
    @SerialName("sentiment_votes_up_percentage")
    val sentimentVotesUpPercentage: Double? = null,
    @SerialName("sentiment_votes_down_percentage")
    val sentimentVotesDownPercentage: Double? = null,
    val links: LinksDto? = null
)

@Serializable
data class LinksDto(
    val homepage: List<String>? = null,
    @SerialName("blockchain_site")
    val blockchainSite: List<String>? = null,
    @SerialName("twitter_screen_name")
    val twitterScreenName: String? = null,
    @SerialName("telegram_channel_identifier")
    val telegramChannelIdentifier: String? = null,
    @SerialName("subreddit_url")
    val subredditUrl: String? = null,
    @SerialName("repos_url")
    val reposUrl: Map<String, List<String>>? = null
)

@Serializable
data class MarketDataDto(
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
    val sparkline7DDto: Sparkline7dDto? = null
)

@Serializable
data class ImageUrlsResponse(
    val thumb: String,
    val small: String,
    val large: String
)
