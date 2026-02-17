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
data class TransactionResponse(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val vin: List<VinResponse>,
    val vout: List<VoutResponse>
)

@Serializable
data class VinResponse(
    val txid: String,
    val vout: Int,
    @SerialName("scriptsig") val scriptSig: String? = null,
    @SerialName("scriptsig_asm") val scriptSigAsm: String? = null,
    val witness: List<String>? = null,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    val sequence: Long
)

@Serializable
data class VoutResponse(
    @SerialName("scriptpubkey") val scriptPubKey: String? = null,
    @SerialName("scriptpubkey_asm") val scriptPubKeyAsm: String? = null,
    @SerialName("scriptpubkey_type") val scriptPubKeyType: String? = null,
    @SerialName("scriptpubkey_address") val scriptPubKeyAddress: String? = null,
    val value: Long
)