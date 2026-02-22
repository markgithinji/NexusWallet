package com.example.nexuswallet.feature.coin

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null
)