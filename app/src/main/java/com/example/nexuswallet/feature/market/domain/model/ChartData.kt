package com.example.nexuswallet.feature.market.domain.model

import java.util.Date

data class ChartData(
    val prices: List<PricePoint>,
    val marketCaps: List<MarketCapPoint>,
    val volumes: List<VolumePoint>
)

data class PricePoint(
    val timestamp: Long,
    val price: Double,
    val date: Date = Date(timestamp)
)

data class MarketCapPoint(
    val timestamp: Long,
    val marketCap: Double
)

data class VolumePoint(
    val timestamp: Long,
    val volume: Double
)


