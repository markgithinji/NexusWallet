package com.example.nexuswallet.feature.coin.bitcoin

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus

@Entity(tableName = "BitcoinTransaction")
data class BitcoinTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
    val amountSatoshis: Long,
    val amountBtc: String,
    val feeSatoshis: Long,
    val feeBtc: String,
    val feePerByte: Double,
    val estimatedSize: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String,
    val isIncoming: Boolean = false
)

fun BitcoinTransactionEntity.toDomain(): BitcoinTransaction {
    return BitcoinTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        amountSatoshis = amountSatoshis,
        amountBtc = amountBtc,
        feeSatoshis = feeSatoshis,
        feeBtc = feeBtc,
        feePerByte = feePerByte,
        estimatedSize = estimatedSize,
        signedHex = signedHex,
        txHash = txHash,
        network = BitcoinNetwork.valueOf(network),
        isIncoming = isIncoming
    )
}

fun BitcoinTransaction.toEntity(): BitcoinTransactionEntity {
    return BitcoinTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amountSatoshis = amountSatoshis,
        amountBtc = amountBtc,
        feeSatoshis = feeSatoshis,
        feeBtc = feeBtc,
        feePerByte = feePerByte,
        estimatedSize = estimatedSize,
        signedHex = signedHex,
        txHash = txHash,
        network = network.name,
        isIncoming = isIncoming
    )
}