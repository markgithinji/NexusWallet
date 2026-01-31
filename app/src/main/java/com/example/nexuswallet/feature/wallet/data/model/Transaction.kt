package com.example.nexuswallet.feature.wallet.data.model

import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.serialization.Serializable
@Serializable
data class SendTransaction(
    val id: String,
    val walletId: String,
    val walletType: WalletType,
    val fromAddress: String,
    val toAddress: String,
    val amount: String, // In smallest unit (satoshis, wei)
    val amountDecimal: String, // Human readable (BTC, ETH)
    val fee: String,
    val feeDecimal: String,
    val total: String,
    val totalDecimal: String,
    val chain: ChainType,
    val status: TransactionStatus,
    val hash: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val gasPrice: String? = null, // For Ethereum
    val gasLimit: String? = null, // For Ethereum
    val nonce: Int? = null, // For Ethereum
    val signedHex: String? = null,
    val feeLevel: FeeLevel = FeeLevel.NORMAL
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
    val priority: FeeLevel
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
    val nonce: String,           // Hex string "0x1"
    val gasPrice: String,        // Hex string "0x..."
    val gasLimit: String,        // Hex string "0x5208" (21000)
    val to: String,             // Address "0x..."
    val value: String,          // Hex string "0x..."
    val data: String = "0x",    // Hex string
    val chainId: Long = 1L      // 1=Mainnet, 5=Goerli
)

@Serializable
data class SigningResult(
    val success: Boolean,
    val signedTransaction: SignedTransaction? = null,
    val error: String? = null
)

enum class SigningMode {
    MOCK,           // For demo/portfolio
    REAL_ETHEREUM,  // Real Ethereum signing
    REAL_BITCOIN    // Real Bitcoin signing
}
