package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.data.model.BitcoinOutput
import kotlinx.serialization.Serializable
import org.bitcoinj.core.Coin
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.script.Script
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
data class UTXO(
    val outPoint: TransactionOutPoint,
    val value: Coin,
    val script: Script
)

enum class FeeLevel {
    SLOW, NORMAL, FAST
}

enum class BitcoinNetwork {
    MAINNET, TESTNET
}

sealed class ValidationResult {
    data class Valid(val balance: BigDecimal, val estimatedFee: BigDecimal) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}