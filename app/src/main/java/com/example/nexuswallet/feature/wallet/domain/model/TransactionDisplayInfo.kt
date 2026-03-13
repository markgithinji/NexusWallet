package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.core.domain.model.CoinType

data class TransactionDisplayInfo(
    val id: String,
    val coinType: CoinType,
    val isIncoming: Boolean,
    val amount: String,
    val formattedAmount: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val formattedTime: String,
    val hash: String?
)