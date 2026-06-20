package com.example.nexuswallet.feature.market.domain.model

data class NewsArticle(
    val title: String,
    val summary: String?,
    val publishedAt: String,
    val source: String = "CoinStats", // Default source
    val url: String = "",
    val image: String? = null
)