package com.example.nexuswallet.feature.market.domain.model

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