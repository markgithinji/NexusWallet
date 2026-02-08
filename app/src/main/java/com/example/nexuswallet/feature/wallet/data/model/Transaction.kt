package com.example.nexuswallet.feature.wallet.data.model

import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class SendTransaction(
    val id: String,
    val walletId: String,
    val walletType: WalletType,
    val fromAddress: String,
    val toAddress: String,
    val amount: String, // In smallest unit (wei, satoshis, lamports)
    val amountDecimal: String, // Human readable (ETH, BTC, SOL)
    val fee: String, // Fee in smallest unit
    val feeDecimal: String, // Fee human readable
    val total: String, // Total in smallest unit (amount + fee)
    val totalDecimal: String, // Total human readable
    val chain: ChainType,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val gasPrice: String? = null, // For Ethereum (Gwei)
    val gasLimit: String? = null, // For Ethereum
    val computeUnits: String? = null, // For Solana
    val signedHex: String? = null, // Signed transaction hex
    val nonce: Int? = null, // For Ethereum, null for Bitcoin/Solana
    val hash: String? = null, // Transaction hash
    val note: String? = null, // User note
    val timestamp: Long = System.currentTimeMillis(),
    val feeLevel: FeeLevel? = FeeLevel.NORMAL,
    val metadata: Map<String, String> = emptyMap() // Chain-specific metadata
)


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
    val error: String? = null,
    val chain: ChainType
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

@Serializable
data class EthereumTransactionParams(
    val nonce: String,
    val gasPrice: String,
    val gasLimit: String,
    val to: String,
    val value: String,
    val data: String = "0x",
    val network: EthereumNetwork = EthereumNetwork.MAINNET
) {
    fun getChainId(): Long {
        return when (network) {
            EthereumNetwork.MAINNET -> 1L
            EthereumNetwork.GOERLI -> 5L
            EthereumNetwork.SEPOLIA -> 11155111L
            EthereumNetwork.POLYGON -> 137L
            EthereumNetwork.BSC -> 56L
            else -> 1L
        }
    }
}