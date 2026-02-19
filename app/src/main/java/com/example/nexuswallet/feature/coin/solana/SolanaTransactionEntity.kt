package com.example.nexuswallet.feature.coin.solana

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus

@Entity(tableName = "SolanaTransaction")
data class SolanaTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
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
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime
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
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime
    )
}