package com.example.nexuswallet.feature.bitcoin.data.model

data class ParsedTransaction(
    val fromAddress: String,
    val toAddress: String,
    val amount: Long,
    val isIncoming: Boolean
)