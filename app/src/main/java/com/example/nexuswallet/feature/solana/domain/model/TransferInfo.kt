package com.example.nexuswallet.feature.solana.domain.model

data class TransferInfo(
    val from: String,
    val to: String,
    val amount: Long,
    val isIncoming: Boolean,
    val fee: Long
)