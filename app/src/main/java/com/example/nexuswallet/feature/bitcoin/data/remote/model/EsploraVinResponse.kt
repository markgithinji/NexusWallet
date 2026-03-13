package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsploraVinResponse(
    val txid: String,
    val vout: Int,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    val scriptsig: String?,
    @SerialName("scriptsig_asm") val scriptsigAsm: String?,
    val sequence: Long,
    val witness: List<String>? = null,
    val prevout: EsploraVoutResponse?
)