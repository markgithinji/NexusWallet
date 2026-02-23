package com.example.nexuswallet.feature.market.data.model

import com.example.nexuswallet.feature.market.domain.SparklineData
import com.example.nexuswallet.feature.market.domain.Token

fun CoinGeckoTokenDto.toToken(): Token {
    return Token(
        id = id,
        symbol = symbol,
        name = name,
        currentPrice = currentPrice ?: 0.0,
        marketCap = marketCap ?: 0.0,
        marketCapRank = marketCapRank ?: 0,
        priceChange24h = priceChange24h ?: 0.0,
        priceChangePercentage24h = priceChangePercentage24h ?: 0.0,
        image = image ?: "",
        sparklineIn7d = sparklineIn7d?.toSparklineData()
    )
}

fun SparklineDto.toSparklineData(): SparklineData {
    return SparklineData(price = price)
}