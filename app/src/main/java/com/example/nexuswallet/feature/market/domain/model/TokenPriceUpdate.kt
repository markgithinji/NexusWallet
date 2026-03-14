package com.example.nexuswallet.feature.market.domain.model

data class TokenPriceUpdate(
    val tokenId: String,
    val price: Double,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double
)