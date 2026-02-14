package com.example.nexuswallet.feature.coin.bitcoin


import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal


data class BitcoinTransaction(
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountSatoshis: Long,
    val amountBtc: BigDecimal,
    val feeSatoshis: Long,
    val feeBtc: BigDecimal,
    val feePerByte: Double,
    val estimatedSize: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String // "mainnet", "testnet", "regtest"
)