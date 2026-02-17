package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable
import java.math.BigDecimal


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
    val network: String
)

fun SolanaTransactionEntity.toDomain(): SolanaTransaction {
    return SolanaTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        amountLamports = amountLamports,
        amountSol = amountSol,
        feeLamports = feeLamports,
        feeSol = feeSol,
        blockhash = blockhash,
        signedData = signedData,
        signature = signature,
        network = network
    )
}

fun SolanaTransaction.toEntity(): SolanaTransactionEntity {
    return SolanaTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amountLamports = amountLamports,
        amountSol = amountSol,
        feeLamports = feeLamports,
        feeSol = feeSol,
        blockhash = blockhash,
        signedData = signedData,
        signature = signature,
        network = network
    )
}
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
