package com.example.nexuswallet.data.mapper

import com.example.nexuswallet.data.model.CoinGeckoTokenDto
import com.example.nexuswallet.data.model.SparklineDto
import com.example.nexuswallet.domain.SparklineData
import com.example.nexuswallet.domain.Token

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