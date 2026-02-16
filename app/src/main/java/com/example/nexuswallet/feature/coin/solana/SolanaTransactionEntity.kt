package com.example.nexuswallet.feature.coin.solana

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val network: String
)