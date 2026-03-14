package com.example.nexuswallet.feature.market.data.remote.model.coingecko

import kotlinx.serialization.Serializable

@Serializable
data class Sparkline7dResponse(
    val price: List<Double>
)