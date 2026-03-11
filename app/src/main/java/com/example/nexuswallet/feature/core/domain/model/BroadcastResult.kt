package com.example.nexuswallet.feature.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null
)