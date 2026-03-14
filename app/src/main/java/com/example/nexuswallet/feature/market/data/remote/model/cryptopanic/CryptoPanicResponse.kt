package com.example.nexuswallet.feature.market.data.remote.model.cryptopanic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CryptoPanicResponse(
    val next: String? = null,
    val previous: String? = null,
    val results: List<CryptoPanicPostDto>
)

@Serializable
data class CryptoPanicPostDto(
    val title: String,
    val description: String? = null,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("created_at")
    val createdAt: String,
    val kind: String
)