package com.example.nexuswallet.feature.coin.solana.data.model

data class SolanaSignedTransaction(
    val signature: String,
    val serialize: () -> ByteArray
)