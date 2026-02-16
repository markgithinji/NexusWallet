package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.data.model.BitcoinOutput
import kotlinx.serialization.Serializable
import org.bitcoinj.core.Coin
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.script.Script
import java.math.BigDecimal

data class UTXO(
    val outPoint: TransactionOutPoint,
    val value: Coin,
    val script: Script
)

enum class FeeLevel {
    SLOW, NORMAL, FAST
}

enum class BitcoinNetwork(val displayName: String) {
    MAINNET("Mainnet"),
    TESTNET("Testnet")
}