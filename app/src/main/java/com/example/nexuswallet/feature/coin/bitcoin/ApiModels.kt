package com.example.nexuswallet.feature.coin.bitcoin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode


@Serializable
data class AddressResponse(
    val address: String,
    @SerialName("chain_stats") val chainStats: ChainStats,
    @SerialName("mempool_stats") val mempoolStats: MempoolStats
)

@Serializable
data class ChainStats(
    @SerialName("funded_txo_sum") val fundedTxoSum: Long,
    @SerialName("spent_txo_sum") val spentTxoSum: Long,
    @SerialName("tx_count") val txCount: Int
)

@Serializable
data class MempoolStats(
    @SerialName("funded_txo_sum") val fundedTxoSum: Long,
    @SerialName("spent_txo_sum") val spentTxoSum: Long,
    @SerialName("tx_count") val txCount: Int
)

@Serializable
data class UtxoResponse(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: StatusResponse
)

@Serializable
data class StatusResponse(
    val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null,
    @SerialName("block_hash") val blockHash: String? = null,
    @SerialName("block_time") val blockTime: Long? = null
)

@Serializable
data class TransactionResponse(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val vin: List<VinResponse>,
    val vout: List<VoutResponse>,
    val status: TransactionStatusResponse? = null
)

@Serializable
data class VinResponse(
    val txid: String,
    val vout: Int,
    @SerialName("scriptsig") val scriptSig: String? = null,
    @SerialName("scriptsig_asm") val scriptSigAsm: String? = null,
    val witness: List<String>? = null,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    val sequence: Long,
    val prevout: VoutResponse? = null  // Added for getting input details
)

@Serializable
data class VoutResponse(
    @SerialName("scriptpubkey") val scriptPubKey: String? = null,
    @SerialName("scriptpubkey_asm") val scriptPubKeyAsm: String? = null,
    @SerialName("scriptpubkey_type") val scriptPubKeyType: String? = null,
    @SerialName("scriptpubkey_address") val scriptpubkey_address: String? = null,
    val value: Long
)

@Serializable
data class TransactionStatusResponse(
    val confirmed: Boolean,
    @SerialName("block_height") val block_height: Int? = null,
    @SerialName("block_hash") val block_hash: String? = null,
    @SerialName("block_time") val block_time: Long? = null
)

fun BitcoinTransactionResponse.toDomain(
    walletId: String,
    isIncoming: Boolean
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
        feeLevel = FeeLevel.NORMAL, // Default, would need to calculate from fee rate
        amountSatoshis = amount,
        amountBtc = btcAmount.toPlainString(),
        feeSatoshis = fee,
        feeBtc = feeBtc,
        feePerByte = 0.0, // Would need to calculate
        estimatedSize = 0,
        signedHex = null,
        txHash = txid,
        network = "", // Set based on context
        isIncoming = isIncoming
    )
}

@Serializable
data class EsploraTransaction(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val size: Int,
    val weight: Int,
    val fee: Long,
    val vin: List<EsploraVin>,
    val vout: List<EsploraVout>,
    val status: EsploraStatus
)

@Serializable
data class EsploraVin(
    val txid: String,
    val vout: Int,
    val is_coinbase: Boolean,
    val scriptsig: String?,
    val scriptsig_asm: String?,
    val sequence: Long,
    val witness: List<String>?,
    val prevout: EsploraVout?  // Previous output details
)

@Serializable
data class EsploraVout(
    val scriptpubkey: String?,
    val scriptpubkey_asm: String?,
    val scriptpubkey_type: String?,
    val scriptpubkey_address: String?,
    val value: Long
)

@Serializable
data class EsploraStatus(
    val confirmed: Boolean,
    val block_height: Int? = null,
    val block_hash: String? = null,
    val block_time: Long? = null
)

@Serializable
data class EsploraChainStats(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)

@Serializable
data class EsploraMempoolStats(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)

