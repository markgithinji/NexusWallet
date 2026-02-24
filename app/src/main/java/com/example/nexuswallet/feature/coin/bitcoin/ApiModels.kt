package com.example.nexuswallet.feature.coin.bitcoin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class EsploraTransactionResponse(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val size: Int,
    val weight: Int,
    val fee: Long,
    val vin: List<EsploraVinResponse>,
    val vout: List<EsploraVoutResponse>,
    val status: EsploraStatusResponse
)

@Serializable
data class EsploraVinResponse(
    val txid: String,
    val vout: Int,
    val is_coinbase: Boolean,
    val scriptsig: String?,
    val scriptsig_asm: String?,
    val sequence: Long,
    val witness: List<String>?,
    val prevout: EsploraVoutResponse?
)

@Serializable
data class EsploraVoutResponse(
    val scriptpubkey: String?,
    val scriptpubkey_asm: String?,
    val scriptpubkey_type: String?,
    val scriptpubkey_address: String?,
    val value: Long
)

@Serializable
data class EsploraStatusResponse(
    val confirmed: Boolean,
    val block_height: Int? = null,
    val block_hash: String? = null,
    val block_time: Long? = null
)

