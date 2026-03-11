package com.example.nexuswallet.feature.bitcoin.domain.model

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import java.math.BigDecimal

data class PreparedBitcoinTransaction(
    val transactionId: String,
    val fromAddress: String,
    val toAddress: String,
    val amountBtc: BigDecimal,
    val amountSatoshis: Long,
    val feeBtc: BigDecimal,
    val feeSatoshis: Long,
    val feePerByte: Double,
    val feeLevel: FeeLevel,
    val network: BitcoinNetwork,
    val hasPrivateKey: Boolean,
    val estimatedSize: Int,
    val utxoCount: Int
)