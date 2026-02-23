package com.example.nexuswallet.feature.market.domain

// Make sure your Token model has all fields
data class Token(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val marketCap: Double,
    val marketCapRank: Int,
    val priceChange24h: Double,        // For 24h price change in dollars
    val priceChangePercentage24h: Double, // For 24h percentage change
    val image: String,
    val sparklineIn7d: SparklineData? = null
)

data class SparklineData(
    val price: List<Double>
)