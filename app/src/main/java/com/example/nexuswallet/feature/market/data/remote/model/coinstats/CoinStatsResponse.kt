package com.example.nexuswallet.feature.market.data.remote.model.coinstats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoinStatsResponse(
    @SerialName("result")
    val news: List<CoinStatsNewsDto>
)

@Serializable
data class CoinStatsNewsDto(
    val id: String,
    val feedDate: Long,
    val source: String,
    val title: String,
    val description: String? = null,
    val imgUrl: String? = null,
    val link: String? = null,
    val relatedCoins: List<String>? = emptyList()
)
