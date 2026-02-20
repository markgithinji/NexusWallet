package com.example.nexuswallet.feature.coin.bitcoin

import org.bitcoinj.core.Coin
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.script.Script
import java.math.BigDecimal
import java.math.RoundingMode

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
fun BitcoinTransactionResponse.toDomain(
    walletId: String,
    isIncoming: Boolean,
    network: BitcoinNetwork
): BitcoinTransaction {
    val btcAmount = BigDecimal(amount).divide(
        BigDecimal(100_000_000),
        8,
        RoundingMode.HALF_UP
    )

    val feeBtc = if (fee > 0) {
        BigDecimal(fee).divide(
            BigDecimal(100_000_000),
            8,
            RoundingMode.HALF_UP
        ).toPlainString()
    } else "0"

    return BitcoinTransaction(
        id = "btc_tx_${System.currentTimeMillis()}_${txid.take(8)}",
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status,
        timestamp = timestamp * 1000, // Convert to milliseconds
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountSatoshis = amount,
        amountBtc = btcAmount.toPlainString(),
        feeSatoshis = fee,
        feeBtc = feeBtc,
        feePerByte = 0.0,
        estimatedSize = 0,
        signedHex = null,
        txHash = txid,
        network = network,
        isIncoming = isIncoming
    )
}