package com.example.nexuswallet.feature.solana.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class HeliusTransactionRequest(
    val transactions: List<String>
)