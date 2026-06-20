package com.example.nexuswallet.feature.ethereum.domain.model

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import kotlinx.serialization.Serializable

@Serializable
data class EVMFeeEstimate(
    val gasPriceGwei: String,           // Legacy gas price or Max Fee in Gwei
    val gasPriceWei: String,             // Legacy gas price or Max Fee in Wei
    val gasLimit: Long,                   // Gas limit
    val totalFeeWei: String,              // Total fee in Wei (Max)
    val totalFeeEth: String,               // Total fee in ETH (Max)
    val estimatedTime: Int,                // Estimated time in seconds
    val priority: FeeLevel,
    val baseFee: String? = null,           // Base fee for EIP-1559 in Gwei
    val maxPriorityFeeGwei: String? = null, // Max priority fee in Gwei
    val isEIP1559: Boolean = false         // Whether this is an EIP-1559 fee estimate
)