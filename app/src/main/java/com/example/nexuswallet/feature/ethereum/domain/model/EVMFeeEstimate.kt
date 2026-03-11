package com.example.nexuswallet.feature.ethereum.domain.model

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import kotlinx.serialization.Serializable

@Serializable
data class EVMFeeEstimate(
    val gasPriceGwei: String,           // Gas price in Gwei
    val gasPriceWei: String,             // Gas price in Wei
    val gasLimit: Long,                   // Gas limit (21000 for ETH, higher for tokens)
    val totalFeeWei: String,              // Total fee in Wei
    val totalFeeEth: String,               // Total fee in ETH (human readable)
    val estimatedTime: Int,                // Estimated time in seconds
    val priority: FeeLevel,
    val baseFee: String? = null,           // Base fee for EIP-1559 (optional)
    val isEIP1559: Boolean = false         // Whether this is an EIP-1559 fee estimate
)