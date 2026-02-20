package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
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

data class ParsedTransaction(
    val fromAddress: String,
    val toAddress: String,
    val amount: Long,
    val isIncoming: Boolean
)

data class BitcoinTransactionResponse(
    val txid: String,
    val fromAddress: String,
    val toAddress: String,
    val amount: Long,
    val fee: Long,
    val status: TransactionStatus,
    val timestamp: Long,
    val confirmations: Int,
    val blockHash: String?,
    val blockHeight: Int?,
    val isIncoming: Boolean
)