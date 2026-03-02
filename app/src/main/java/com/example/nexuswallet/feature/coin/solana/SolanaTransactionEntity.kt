package com.example.nexuswallet.feature.coin.solana

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "solana_transactions")
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
    val signature: String?,
    val network: String,
    val isIncoming: Boolean = false,
    val tokenMint: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    val slot: Long? = null,
    val blockTime: Long? = null
)