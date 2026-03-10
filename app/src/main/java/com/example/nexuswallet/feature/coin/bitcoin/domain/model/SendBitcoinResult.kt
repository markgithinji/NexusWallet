package com.example.nexuswallet.feature.coin.bitcoin.domain.model

data class SendBitcoinResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)