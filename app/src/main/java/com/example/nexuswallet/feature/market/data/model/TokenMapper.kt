package com.example.nexuswallet.feature.market.data.model

import com.example.nexuswallet.feature.market.data.remote.model.coingecko.TokenDetailDto
import com.example.nexuswallet.feature.market.data.remote.model.coingecko.CoinGeckoTokenDto
import com.example.nexuswallet.feature.market.data.remote.model.coinstats.CoinStatsNewsDto
import com.example.nexuswallet.feature.market.data.remote.model.coingecko.MarketChartDto
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.data.remote.model.coingecko.Sparkline7dDto
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.MarketCapPoint
import com.example.nexuswallet.feature.market.domain.model.PricePoint
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.market.domain.model.VolumePoint
import com.example.nexuswallet.feature.market.domain.model.SparklineData
import com.example.nexuswallet.feature.market.domain.model.Token

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

fun Sparkline7dDto.toSparklineData(): SparklineData {
    return SparklineData(price = price)
}

fun CoinStatsNewsDto.toNewsArticle(): NewsArticle {
    return NewsArticle(
        title = title,
        summary = description,
        publishedAt = java.time.Instant.ofEpochMilli(feedDate).toString(),
        source = source,
        url = link ?: ""
    )
}
fun MarketChartDto.toChartData(): ChartData {
    return ChartData(
        prices = prices.map { (timestamp, price) ->
            PricePoint(timestamp = timestamp.toLong(), price = price)
        },
        marketCaps = market_caps.map { (timestamp, cap) ->
            MarketCapPoint(timestamp = timestamp.toLong(), marketCap = cap)
        },
        volumes = total_volumes.map { (timestamp, volume) ->
            VolumePoint(timestamp = timestamp.toLong(), volume = volume)
        }
    )
}


fun TokenDetailDto.toTokenDetail(): TokenDetail {
    return TokenDetail(
        id = id,
        symbol = symbol,
        name = name,
        image = image.large,
        currentPrice = market_data.currentPrice["usd"] ?: 0.0,
        priceChange24h = market_data.priceChange24h,
        priceChangePercentage24h = market_data.priceChangePercentage24h,
        marketCap = market_data.marketCap["usd"] ?: 0.0,
        marketCapRank = market_data.marketCapRank ?: 0,
        fullyDilutedValuation = null,
        totalVolume = market_data.totalVolume["usd"] ?: 0.0,
        high24h = market_data.high24h["usd"] ?: 0.0,
        low24h = market_data.low24h["usd"] ?: 0.0,
        circulatingSupply = market_data.circulatingSupply,
        totalSupply = market_data.totalSupply,
        maxSupply = market_data.maxSupply,
        ath = market_data.ath["usd"] ?: 0.0,
        athChangePercentage = market_data.athChangePercentage["usd"] ?: 0.0,
        athDate = market_data.athDate["usd"] ?: "",
        atl = market_data.atl["usd"] ?: 0.0,
        atlChangePercentage = market_data.atlChangePercentage["usd"] ?: 0.0,
        atlDate = market_data.atlDate["usd"] ?: "",
        sparklineIn7d = market_data.sparkline7DDto?.price,
        description = description?.get("en")
    )
}