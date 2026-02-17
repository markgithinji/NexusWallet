package com.example.nexuswallet.feature.coin.bitcoin

import org.bitcoinj.core.Coin
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.script.Script

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


data class BitcoinFeeEstimate(
    val feePerByte: Double,           // Satoshis per byte
    val totalFeeSatoshis: Long,       // Total fee in satoshis
    val totalFeeBtc: String,          // Total fee in BTC (human readable)
    val estimatedTime: Int,           // Estimated time in seconds
    val priority: FeeLevel,
    val estimatedSize: Long,           // Estimated transaction size in bytes
    val blockTarget: Int               // Block target (2, 6, 144 blocks)
)

data class BitcoinWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: BitcoinNetwork
)

data class SendBitcoinResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)
