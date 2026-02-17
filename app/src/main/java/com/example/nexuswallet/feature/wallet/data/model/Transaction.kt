package com.example.nexuswallet.feature.wallet.data.model

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.serialization.Serializable

@Serializable
data class TransactionFee(
    val chain: ChainType,
    val slow: FeeEstimate,
    val normal: FeeEstimate,
    val fast: FeeEstimate,
    val custom: FeeEstimate? = null
)

@Serializable
data class FeeEstimate(
    val feePerByte: String? = null, // For Bitcoin (sat/byte)
    val gasPrice: String? = null, // For Ethereum (Gwei)
    val totalFee: String, // Total fee in smallest unit
    val totalFeeDecimal: String, // Human readable
    val estimatedTime: Int, // Seconds
    val priority: FeeLevel,
    val metadata: Map<String, String> = emptyMap() // Chain-specific metadata
)

@Serializable
data class SignedTransaction(
    val rawHex: String,
    val hash: String,
    val chain: ChainType
)

@Serializable
data class BroadcastResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null
)

@Serializable
data class BitcoinOutput(
    val address: String,
    val amount: Long // satoshis
)

@Serializable
data class EthereumTransactionData(
    val nonce: String,
    val gasPrice: String, // in wei
    val gasLimit: String,
    val to: String,
    val value: String, // in wei
    val data: String = "0x",
    val chainId: Long
)