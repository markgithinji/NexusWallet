package com.example.nexuswallet.feature.solana.domain.model

data class SendSolanaResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)