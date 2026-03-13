package com.example.nexuswallet.feature.ethereum.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SendEthereumResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)