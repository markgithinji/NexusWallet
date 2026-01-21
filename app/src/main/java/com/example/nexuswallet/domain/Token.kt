package com.example.nexuswallet.domain

data class Token(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val marketCap: Double,
    val marketCapRank: Int,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double,
    val image: String,
    val sparklineIn7d: SparklineData? = null
)

data class SparklineData(
    val price: List<Double>
)