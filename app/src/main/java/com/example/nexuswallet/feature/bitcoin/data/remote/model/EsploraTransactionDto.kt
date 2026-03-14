package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsploraTransactionDto(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val size: Int,
    val weight: Int,
    val fee: Long,
    val vin: List<EsploraVinDto>,
    val vout: List<EsploraVoutDto>,
    val status: StatusDto
)

@Serializable
data class EsploraVinDto(
    val txid: String,
    val vout: Int,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    val scriptsig: String?,
    @SerialName("scriptsig_asm") val scriptsigAsm: String?,
    val sequence: Long,
    val witness: List<String>? = null,
    val prevout: EsploraVoutDto?
)

@Serializable
data class EsploraVoutDto(
    val scriptpubkey: String?,
    @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String?,
    @SerialName("scriptpubkey_type") val scriptpubkeyType: String?,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String?,
    val value: Long
)

@Serializable
data class StatusDto(
    val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null,
    @SerialName("block_hash") val blockHash: String? = null,
    @SerialName("block_time") val blockTime: Long? = null
)