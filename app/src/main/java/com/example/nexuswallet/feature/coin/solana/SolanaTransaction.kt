package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable

@Serializable
data class SolanaTransaction(
    val id: String,
    val walletId: String,
    val coinType: CoinType = CoinType.SOLANA,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountLamports: Long,
    val amountSol: String,
    val feeLamports: Long,
    val feeSol: String,
    val blockhash: String,
    val signedData: String? = null,
    val signature: String? = null,
    val network: String,
    val isIncoming: Boolean = false,
    val slot: Long? = null,
    val blockTime: Long? = null
)

enum class SolanaNetwork {
    MAINNET,
    DEVNET
}

data class SolanaFeeEstimate(
    val feeLamports: Long,              // Fee in lamports
    val feeSol: String,                  // Fee in SOL (human readable)
    val estimatedTime: Int,               // Estimated time in seconds (usually instant for Solana)
    val priority: FeeLevel,
    val computeUnits: Int,                 // Compute units for the transaction
    val blockhash: String? = null          // Recent blockhash (if available)
)

data class SolanaSignedTransaction(
    val signature: String,
    val serialize: () -> ByteArray
)

data class SendSolanaResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

data class SolanaWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String
)