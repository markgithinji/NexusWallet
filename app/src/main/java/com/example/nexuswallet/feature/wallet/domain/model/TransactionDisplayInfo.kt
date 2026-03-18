package com.example.nexuswallet.feature.wallet.domain.model

data class TransactionDisplayInfo(
    val id: String,
    val isIncoming: Boolean,
    val amount: String,
    val formattedAmount: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val formattedTime: String,
    val hash: String?,
    val coin: Coin,
    val symbol: String
)