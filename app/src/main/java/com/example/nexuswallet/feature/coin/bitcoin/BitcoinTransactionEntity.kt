package com.example.nexuswallet.feature.coin.bitcoin

import androidx.room.Entity
import androidx.room.PrimaryKey

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