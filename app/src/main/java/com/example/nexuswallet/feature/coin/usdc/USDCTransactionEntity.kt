package com.example.nexuswallet.feature.coin.usdc

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "USDCSendTransaction")
data class USDCTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
    val amount: String,
    val amountDecimal: String,
    val contractAddress: String,
    val network: String,
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val ethereumTransactionId: String? = null
)