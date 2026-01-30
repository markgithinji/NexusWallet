package com.example.nexuswallet.feature.wallet.data.model

import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.serialization.Serializable
import java.math.BigDecimal


@Serializable
data class BitcoinTransactionData(
    val inputs: List<BitcoinInput>,
    val outputs: List<BitcoinOutput>,
    val fee: Long,
    val changeAddress: String? = null
)

@Serializable
data class BitcoinInput(
    val txid: String,
    val vout: Int,
    val amount: Long,
    val scriptPubKey: String,
    val address: String
)

@Serializable
data class UTXO(
    val txid: String,
    val vout: Int,
    val amount: Long,
    val scriptPubKey: String,
    val confirmations: Int
)

enum class FeeLevel {
    SLOW, NORMAL, FAST
}

sealed class ValidationResult {
    data class Valid(val balance: BigDecimal, val estimatedFee: BigDecimal) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}