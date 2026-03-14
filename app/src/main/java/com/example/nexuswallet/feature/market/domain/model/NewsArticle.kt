package com.example.nexuswallet.feature.market.domain.model

data class NewsArticle(
    val title: String,
    val summary: String?,
    val publishedAt: String,
    val source: String = "CryptoPanic", // Default source
    val url: String = "", // No URL in free plan
    val image: String? = null
)