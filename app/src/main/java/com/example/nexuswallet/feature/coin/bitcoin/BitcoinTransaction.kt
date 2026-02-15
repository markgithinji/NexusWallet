package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class BitcoinTransaction(
    val id: String,
    val walletId: String,
    val coinType: CoinType = CoinType.BITCOIN,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountSatoshis: Long,
    val amountBtc: String,
    val feeSatoshis: Long,
    val feeBtc: String,
    val feePerByte: Double,
    val estimatedSize: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String
)