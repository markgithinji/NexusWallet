package com.example.nexuswallet.feature.market.data.remote.model.coingecko

import kotlinx.serialization.Serializable

@Serializable
data class MarketChartResponse(
    val prices: List<List<Double>>,
    val market_caps: List<List<Double>>,
    val total_volumes: List<List<Double>>
)