package com.example.nexuswallet.feature.market.data.model

import com.example.nexuswallet.feature.market.domain.SparklineData
import com.example.nexuswallet.feature.market.domain.Token

fun CoinGeckoTokenDto.toToken(): Token {
    return Token(
        id = id,
        symbol = symbol,
        name = name,
        currentPrice = currentPrice,
        marketCap = marketCap,
        marketCapRank = marketCapRank,
        priceChange24h = priceChange24h,
        priceChangePercentage24h = priceChangePercentage24h,
        image = image,
        sparklineIn7d = sparklineIn7d?.toSparklineData()
    )
}

fun SparklineDto.toSparklineData(): SparklineData {
    return SparklineData(price = price)
}