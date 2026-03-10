package com.example.nexuswallet.feature.coin.bitcoin.domain.model

import com.example.nexuswallet.feature.coin.FeeLevel

data class BitcoinFeeEstimate(
    val feePerByte: Double,           // Satoshis per byte
    val totalFeeSatoshis: Long,       // Total fee in satoshis
    val totalFeeBtc: String,          // Total fee in BTC (human readable)
    val estimatedTime: Int,           // Estimated time in seconds
    val priority: FeeLevel,
    val estimatedSize: Long,           // Estimated transaction size in bytes
    val blockTarget: Int               // Block target (2, 6, 144 blocks)
)