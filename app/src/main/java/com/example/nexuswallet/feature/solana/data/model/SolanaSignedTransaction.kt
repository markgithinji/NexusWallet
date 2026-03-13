package com.example.nexuswallet.feature.solana.data.model

data class SolanaSignedTransaction(
    val signature: String,
    val serialize: () -> ByteArray
)